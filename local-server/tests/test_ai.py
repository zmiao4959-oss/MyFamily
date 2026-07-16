import json

from sqlalchemy import select

from app.ai_provider import AICompletion, AIProviderError, ToolCall
from app.ai_service import process_job
from app.models import AIArtifact, Claim, ProcessingJob, Record


class FakeProvider:
    name = "fake"

    def __init__(self, responses):
        self.responses = list(responses)
        self.calls = []

    def complete(self, messages, model, *, json_mode=False, tools=None):
        self.calls.append({"messages": messages, "model": model, "json_mode": json_mode, "tools": tools})
        response = self.responses.pop(0)
        if isinstance(response, Exception):
            raise response
        return response


def enable_ai(settings):
    settings.feature_ai = True
    settings.deepseek_api_key = "fake-test-key"


def create_record(client, auth_headers):
    person = client.post("/persons", headers=auth_headers, json={"name": "李奶奶"}).json()
    record = client.post(
        "/records",
        headers=auth_headers,
        json={"title": "旧相册", "original_text": "1998年春节，全家在奶奶家合影。", "person_ids": [person["id"]]},
    ).json()
    return person, record


def organized_json(person_id):
    return json.dumps({
        "title": "1998年春节家庭合影",
        "summary": "一家人在奶奶家春节团聚。",
        "people_candidates": [{"name": "李奶奶", "matched_person_id": person_id, "confidence": 0.92}],
        "locations": [{"name": "奶奶家", "confidence": 0.7}],
        "event_date": None,
        "date_text": "1998年春节",
        "date_precision": "approximate",
        "tags": ["春节", "家庭聚会"],
        "claims": [{"subject_person_id": person_id, "predicate": "参加", "object_person_id": None, "object_text": "1998年春节团聚", "confidence": 0.75}],
        "questions_for_user": ["奶奶家位于哪里？"],
        "needs_confirmation": True,
    }, ensure_ascii=False)


def test_ai_status_and_disabled_guard(client, auth_headers):
    status = client.get("/ai/status", headers=auth_headers)
    assert status.status_code == 200
    assert status.json()["configured"] is False

    _, record = create_record(client, auth_headers)
    response = client.post("/ai/organize-record", headers=auth_headers, json={"record_id": record["id"]})
    assert response.status_code == 409


def test_organize_repairs_json_and_confirmation_preserves_original(client, auth_headers, test_settings, session_factory):
    enable_ai(test_settings)
    person, record = create_record(client, auth_headers)
    queued = client.post("/ai/organize-record", headers=auth_headers, json={"record_id": record["id"]})
    assert queued.status_code == 202
    provider = FakeProvider([AICompletion("not-json"), AICompletion(organized_json(person["id"]))])

    with session_factory() as db:
        job = db.get(ProcessingJob, queued.json()["id"])
        process_job(db, job, provider)

    completed = client.get(f"/ai/jobs/{queued.json()['id']}", headers=auth_headers).json()
    assert completed["status"] == "completed"
    artifact_id = completed["result_artifact_id"]
    artifact = client.get(f"/ai/artifacts/{artifact_id}", headers=auth_headers).json()
    assert artifact["output_json"]["summary"].startswith("一家人")
    assert artifact["user_confirmed"] is False
    assert len(provider.calls) == 2

    confirmed = client.post(f"/ai/artifacts/{artifact_id}/confirm", headers=auth_headers, json={"confirmed": True})
    assert confirmed.status_code == 200
    assert confirmed.json()["user_confirmed"] is True
    with session_factory() as db:
        saved_record = db.get(Record, record["id"])
        assert saved_record.original_text == "1998年春节，全家在奶奶家合影。"
        assert saved_record.ai_summary == "一家人在奶奶家春节团聚。"
        assert {tag.name for tag in saved_record.tags} == {"春节", "家庭聚会"}
        assert db.scalar(select(Claim).where(Claim.source_record_id == record["id"])) is not None


def test_failed_job_retries_then_stops(client, auth_headers, test_settings, session_factory):
    enable_ai(test_settings)
    _, record = create_record(client, auth_headers)
    queued = client.post("/ai/organize-record", headers=auth_headers, json={"record_id": record["id"]}).json()
    provider = FakeProvider([AIProviderError("provider unavailable")] * 3)

    with session_factory() as db:
        for expected in ["pending", "pending", "failed"]:
            job = db.get(ProcessingJob, queued["id"])
            result = process_job(db, job, provider)
            assert result.status == expected
        assert "provider unavailable" not in result.last_error

    response = client.post(f"/ai/jobs/{queued['id']}/retry", headers=auth_headers)
    assert response.status_code == 200
    assert response.json()["status"] == "pending"
    assert response.json()["attempt_count"] == 0


def test_ask_uses_validated_tool_result(client, auth_headers, test_settings, session_factory):
    enable_ai(test_settings)
    _, record = create_record(client, auth_headers)
    queued = client.post("/ai/ask", headers=auth_headers, json={"question": "春节的记录有哪些？"}).json()
    final = json.dumps({"answer": "找到一条春节记录。", "source_record_ids": [record["id"]], "unknown": False, "needs_confirmation": False}, ensure_ascii=False)
    provider = FakeProvider([
        AICompletion(None, [ToolCall("call-1", "search_records", json.dumps({"query": "春节", "limit": 5}, ensure_ascii=False))]),
        AICompletion(final),
    ])
    with session_factory() as db:
        job = db.get(ProcessingJob, queued["id"])
        result = process_job(db, job, provider)
        assert result.status == "completed"
        artifact = db.get(AIArtifact, result.result_artifact_id)
        assert artifact.output_json["source_record_ids"] == [record["id"]]
    assert provider.calls[0]["tools"] is not None


def test_ai_endpoints_require_authentication(client):
    assert client.get("/ai/status").status_code == 401
