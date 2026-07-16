import hashlib
from pathlib import Path

from PIL import Image
from sqlalchemy import select

from app.embedding_service import process_next_embedding_job
from app.models import DEFAULT_FAMILY_ID, DEFAULT_OWNER_ID, Embedding, MediaAsset, ProcessingJob, new_uuid, utc_now


class FakeEmbeddingProvider:
    def embed_text(self, text):
        return [1.0, 0.0, 0.0]

    def embed_image(self, path: Path, mime_type: str, caption: str = ""):
        assert path.is_file()
        return [1.0, 0.0, 0.0]


def test_keyword_search_works_when_multimodal_is_disabled(client, auth_headers):
    record = client.post("/records", headers=auth_headers, json={"title": "奶奶的春节", "original_text": "一家人在广州团聚"}).json()
    response = client.post("/search/text", headers=auth_headers, json={"query": "春节"})
    assert response.status_code == 200
    assert response.json()["results"][0]["record_id"] == record["id"]
    assert client.post("/search/semantic", headers=auth_headers, json={"query": "家庭团聚"}).status_code == 409


def test_reindex_processes_text_and_image_and_filters_deleted_records(client, auth_headers, test_settings, session_factory, monkeypatch):
    test_settings.feature_multimodal_search = True
    test_settings.volcano_ark_api_key = "fake-key"
    person = client.post("/persons", headers=auth_headers, json={"name": "李奶奶", "biography": "在广州生活"}).json()
    record = client.post("/records", headers=auth_headers, json={"title": "春节合影", "original_text": "全家团聚", "person_ids": [person["id"]]}).json()
    root = test_settings.media_root
    (root / "original").mkdir(parents=True, exist_ok=True)
    image_path = root / "original" / "test.jpg"
    Image.new("RGB", (32, 32), "red").save(image_path, "JPEG")
    digest = hashlib.sha256(image_path.read_bytes()).hexdigest()
    with session_factory() as db:
        media = MediaAsset(
            id=new_uuid(), client_uuid=new_uuid(), owner_id=DEFAULT_OWNER_ID, family_id=DEFAULT_FAMILY_ID,
            record_id=record["id"], media_type="image", original_filename="test.jpg", storage_path="original/test.jpg",
            mime_type="image/jpeg", size_bytes=image_path.stat().st_size, sha256=digest, metadata_json={}, created_at=utc_now(),
        )
        db.add(media)
        db.commit()

    queued = client.post("/search/reindex", headers=auth_headers)
    assert queued.status_code == 200
    assert queued.json()["queued"] >= 1
    provider = FakeEmbeddingProvider()
    with session_factory() as db:
        while process_next_embedding_job(db, provider, test_settings):
            pass
        assert db.scalar(select(Embedding).where(Embedding.object_type == "media", Embedding.status == "ready")) is not None
        assert db.scalar(select(ProcessingJob).where(ProcessingJob.job_type == "embedding_generate", ProcessingJob.status == "failed")) is None

    monkeypatch.setattr("app.search.VolcanoEmbeddingProvider", lambda settings: provider)
    semantic = client.post("/search/semantic", headers=auth_headers, json={"query": "过年团聚"})
    assert semantic.status_code == 200
    assert any(item["record_id"] == record["id"] for item in semantic.json()["results"])
    images = client.post("/search/multimodal", headers=auth_headers, json={"query": "红色家庭照片"})
    assert images.status_code == 200
    assert images.json()["results"][0]["result_type"] == "image"

    assert client.delete(f"/records/{record['id']}", headers=auth_headers).status_code == 204
    hidden = client.post("/search/semantic", headers=auth_headers, json={"query": "过年团聚"}).json()["results"]
    assert all(item.get("record_id") != record["id"] for item in hidden)
