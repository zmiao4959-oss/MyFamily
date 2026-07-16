from __future__ import annotations

import hashlib
import json
from datetime import timedelta
from typing import TypeVar

from pydantic import BaseModel, ValidationError
from sqlalchemy import select
from sqlalchemy.orm import Session

from app.ai_provider import AIProvider, AIProviderError
from app.ai_schemas import AskResult, BiographyResult, InterviewResult, OrganizeResult
from app.ai_tools import AI_TOOL_DEFINITIONS, AIToolExecutor
from app.models import AIArtifact, DEFAULT_FAMILY_ID, Person, ProcessingJob, Record, utc_now

PROMPT_VERSION = "phase5-v1"
StructuredT = TypeVar("StructuredT", bound=BaseModel)

SYSTEM_GUARDRAILS = """你是个人家族档案整理助手。只依据提供的私人资料回答，不得补写未知事实。
必须区分事实、个人回忆、他人描述和 AI 建议。AI 输出始终是待确认建议。
不得删除资料、修改人物关系、修改可见范围、确认事实或覆盖原始文字。
不知道时明确说明不知道。输出必须是符合给定结构的 JSON，不要输出 Markdown。"""


class AIProcessingError(RuntimeError):
    pass


def _json(value: object) -> str:
    return json.dumps(value, ensure_ascii=False, sort_keys=True, default=str)


def _hash(value: object) -> str:
    return hashlib.sha256(_json(value).encode("utf-8")).hexdigest()


def _structured_call(
    provider: AIProvider,
    model: str,
    messages: list[dict],
    schema: type[StructuredT],
) -> StructuredT:
    schema_text = _json(schema.model_json_schema())
    requested = messages + [{"role": "system", "content": f"严格返回 JSON，结构必须符合此 JSON Schema：{schema_text}"}]
    completion = provider.complete(requested, model, json_mode=True)
    try:
        return schema.model_validate_json(completion.content or "")
    except (ValidationError, ValueError):
        repair = requested + [
            {"role": "assistant", "content": completion.content or ""},
            {"role": "user", "content": "上一份 JSON 无效。只修复结构和字段类型，不增加新事实，再返回一次完整 JSON。"},
        ]
        repaired = provider.complete(repair, model, json_mode=True)
        try:
            return schema.model_validate_json(repaired.content or "")
        except (ValidationError, ValueError) as error:
            raise AIProcessingError("AI returned invalid structured output after repair") from error


def _record_input(db: Session, record_id: str) -> tuple[Record, dict]:
    record = db.scalar(select(Record).where(Record.id == record_id, Record.family_id == DEFAULT_FAMILY_ID, Record.deleted_at.is_(None)))
    if record is None:
        raise AIProcessingError("Record not found")
    names = [item.name for item in db.scalars(select(Person).where(Person.id.in_(record.person_ids), Person.deleted_at.is_(None))).all()] if record.person_ids else []
    return record, {
        "record_id": record.id, "title": record.title, "original_text": record.original_text,
        "edited_text": record.edited_text, "occurred_at": record.occurred_at_start,
        "date_precision": record.date_precision, "location": record.location_text,
        "related_people": names, "source_type": record.source_type, "verification_status": record.verification_status,
    }


def _person_input(db: Session, person_id: str) -> tuple[Person, dict]:
    person = db.scalar(select(Person).where(Person.id == person_id, Person.family_id == DEFAULT_FAMILY_ID, Person.deleted_at.is_(None)))
    if person is None:
        raise AIProcessingError("Person not found")
    records = db.scalars(select(Record).where(Record.family_id == DEFAULT_FAMILY_ID, Record.deleted_at.is_(None)).order_by(Record.updated_at.desc())).unique().all()
    sources = [
        {"id": record.id, "title": record.title, "text": record.original_text[:2500], "source_type": record.source_type, "verification_status": record.verification_status}
        for record in records if person.id in record.person_ids
    ][:30]
    return person, {
        "person": {"id": person.id, "name": person.name, "birth_year": person.birth_year, "death_year": person.death_year, "birth_place": person.birth_place, "ancestral_home": person.ancestral_home, "existing_biography": person.biography},
        "source_records": sources,
    }


def _run_ask(db: Session, provider: AIProvider, model: str, question: str, person_id: str | None) -> tuple[AskResult, dict]:
    context = {"question": question, "person_id": person_id}
    messages: list[dict] = [
        {"role": "system", "content": SYSTEM_GUARDRAILS + "\n回答前优先使用工具检索。答案必须列出实际使用的 source_record_ids。"},
        {"role": "user", "content": _json(context)},
    ]
    executor = AIToolExecutor(db)
    allowed_source_ids: set[str] = set()
    for _ in range(5):
        completion = provider.complete(messages, model, json_mode=True, tools=AI_TOOL_DEFINITIONS)
        if completion.tool_calls:
            messages.append({
                "role": "assistant", "content": completion.content,
                "tool_calls": [{"id": call.id, "type": "function", "function": {"name": call.name, "arguments": call.arguments}} for call in completion.tool_calls],
            })
            for call in completion.tool_calls:
                try:
                    arguments = json.loads(call.arguments)
                    if not isinstance(arguments, dict):
                        raise ValueError("arguments must be an object")
                    result = executor.execute(call.name, arguments)
                    candidates = result if isinstance(result, list) else [result]
                    for candidate in candidates:
                        if isinstance(candidate, dict) and isinstance(candidate.get("id"), str):
                            allowed_source_ids.add(candidate["id"])
                except (ValueError, TypeError) as error:
                    result = {"error": f"tool validation failed: {type(error).__name__}"}
                messages.append({"role": "tool", "tool_call_id": call.id, "content": _json(result)})
            continue
        try:
            parsed = AskResult.model_validate_json(completion.content or "")
            if any(source_id not in allowed_source_ids for source_id in parsed.source_record_ids):
                raise AIProcessingError("AI answer cited a record that was not returned by tools")
            if not parsed.unknown and not parsed.source_record_ids:
                raise AIProcessingError("AI answer claimed knowledge without a source record")
            return parsed, context
        except (ValidationError, ValueError):
            messages.extend([
                {"role": "assistant", "content": completion.content or ""},
                {"role": "user", "content": f"只修复为符合此 JSON Schema 的 JSON：{_json(AskResult.model_json_schema())}"},
            ])
    raise AIProcessingError("AI tool loop exceeded the safe limit")


def process_job(db: Session, job: ProcessingJob, provider: AIProvider) -> ProcessingJob:
    job.status = "running"
    job.attempt_count += 1
    job.last_error = None
    job.updated_at = utc_now()
    db.commit()
    try:
        if job.job_type == "organize_record":
            record, input_data = _record_input(db, job.record_id or "")
            result = _structured_call(
                provider, job.model,
                [{"role": "system", "content": SYSTEM_GUARDRAILS}, {"role": "user", "content": "整理这条记录：" + _json(input_data)}],
                OrganizeResult,
            )
            record.ai_processing_status = "suggestion_ready"
            artifact_type = "organized_record"
        elif job.job_type == "generate_biography":
            _, input_data = _person_input(db, job.person_id or "")
            result = _structured_call(
                provider, job.model,
                [{"role": "system", "content": SYSTEM_GUARDRAILS + "\n写人物小传草稿，逐条依据 source_records，不确定内容放入 uncertainties。"}, {"role": "user", "content": _json(input_data)}],
                BiographyResult,
            )
            valid_sources = {item["id"] for item in input_data["source_records"]}
            if any(source_id not in valid_sources for source_id in result.source_record_ids):
                raise AIProcessingError("Biography cited a record outside the provided sources")
            artifact_type = "biography_draft"
        elif job.job_type == "generate_interview_questions":
            person, input_data = _person_input(db, job.person_id or "")
            result = _structured_call(
                provider, job.model,
                [{"role": "system", "content": SYSTEM_GUARDRAILS + "\n生成温和、具体、一次只问一件事的口述采访问题。"}, {"role": "user", "content": _json(input_data)}],
                InterviewResult,
            )
            if result.person_id != person.id:
                raise AIProcessingError("Interview result refers to another person")
            artifact_type = "interview_questions"
        elif job.job_type == "ask":
            result, input_data = _run_ask(db, provider, job.model, str(job.payload_json.get("question", "")), job.person_id)
            artifact_type = "answer"
        else:
            raise AIProcessingError("Unsupported AI job type")
        artifact = AIArtifact(
            record_id=job.record_id, person_id=job.person_id, artifact_type=artifact_type,
            provider=provider.name, model=job.model, prompt_version=PROMPT_VERSION,
            input_hash=_hash(input_data), output_json=result.model_dump(mode="json"), status="suggestion",
        )
        db.add(artifact)
        db.flush()
        job.result_artifact_id = artifact.id
        job.status = "completed"
        job.updated_at = utc_now()
        db.commit()
        db.refresh(job)
        return job
    except Exception as error:
        db.rollback()
        current = db.get(ProcessingJob, job.id)
        if current is None:
            raise
        current.status = "failed" if current.attempt_count >= current.max_attempts else "pending"
        if isinstance(error, AIProcessingError):
            safe_message = str(error)[:360]
        elif isinstance(error, AIProviderError):
            safe_message = "AI provider request failed"
        else:
            safe_message = "Unexpected AI processing failure"
        current.last_error = f"{type(error).__name__}: {safe_message}"
        current.updated_at = utc_now()
        db.commit()
        return current


def process_next_job(db: Session, provider: AIProvider) -> ProcessingJob | None:
    stale_before = utc_now() - timedelta(minutes=10)
    stale = db.scalars(select(ProcessingJob).where(ProcessingJob.status == "running", ProcessingJob.updated_at < stale_before)).all()
    for item in stale:
        item.status = "pending" if item.attempt_count < item.max_attempts else "failed"
        item.last_error = "Worker interruption detected; task is safe to retry"
        item.updated_at = utc_now()
    if stale:
        db.commit()
    job = db.scalar(
        select(ProcessingJob).where(ProcessingJob.status == "pending").order_by(ProcessingJob.created_at).with_for_update(skip_locked=True).limit(1)
    )
    return process_job(db, job, provider) if job else None
