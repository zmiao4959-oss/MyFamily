from __future__ import annotations

from fastapi import APIRouter, Depends, HTTPException, Query, status
from sqlalchemy import func, select
from sqlalchemy.orm import Session

from app.ai_schemas import (
    AIArtifactRead,
    AIAskRequest,
    AIObjectRequest,
    AIStatusRead,
    ConfirmArtifactRequest,
    OrganizeResult,
    ProcessingJobRead,
)
from app.config import Settings, get_settings
from app.database import get_db
from app.models import AIArtifact, Claim, DEFAULT_FAMILY_ID, Person, ProcessingJob, Record, Tag, utc_now
from app.security import require_access_token
from app.embedding_service import queue_source

router = APIRouter(prefix="/ai", dependencies=[Depends(require_access_token)])


def _require_ai(settings: Settings) -> None:
    if not settings.feature_ai:
        raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail="AI features are disabled")
    if not settings.deepseek_api_key:
        raise HTTPException(status_code=status.HTTP_503_SERVICE_UNAVAILABLE, detail="DeepSeek API key is not configured")


def _active_record(db: Session, record_id: str) -> Record:
    item = db.scalar(select(Record).where(Record.id == record_id, Record.family_id == DEFAULT_FAMILY_ID, Record.deleted_at.is_(None)))
    if item is None:
        raise HTTPException(status_code=404, detail="Record not found")
    return item


def _active_person(db: Session, person_id: str) -> Person:
    item = db.scalar(select(Person).where(Person.id == person_id, Person.family_id == DEFAULT_FAMILY_ID, Person.deleted_at.is_(None)))
    if item is None:
        raise HTTPException(status_code=404, detail="Person not found")
    return item


def _queue(
    db: Session,
    settings: Settings,
    job_type: str,
    *,
    record_id: str | None = None,
    person_id: str | None = None,
    payload: dict | None = None,
    main_model: bool = False,
) -> ProcessingJob:
    _require_ai(settings)
    existing = None if job_type == "ask" else db.scalar(
        select(ProcessingJob).where(
            ProcessingJob.family_id == DEFAULT_FAMILY_ID,
            ProcessingJob.job_type == job_type,
            ProcessingJob.record_id == record_id,
            ProcessingJob.person_id == person_id,
            ProcessingJob.status.in_(["pending", "running"]),
        ).order_by(ProcessingJob.created_at.desc())
    )
    if existing:
        return existing
    job = ProcessingJob(
        job_type=job_type, record_id=record_id, person_id=person_id,
        model=settings.deepseek_main_model if main_model else settings.deepseek_fast_model,
        payload_json=payload or {},
    )
    db.add(job)
    if record_id:
        record = _active_record(db, record_id)
        record.ai_processing_status = "pending"
    db.commit()
    db.refresh(job)
    return job


@router.get("/status", response_model=AIStatusRead)
def ai_status(settings: Settings = Depends(get_settings)) -> AIStatusRead:
    return AIStatusRead(
        enabled=settings.feature_ai,
        configured=settings.feature_ai and bool(settings.deepseek_api_key),
        provider="deepseek", fast_model=settings.deepseek_fast_model, main_model=settings.deepseek_main_model,
    )


@router.post("/organize-record", response_model=ProcessingJobRead, status_code=202)
def organize_record(payload: AIObjectRequest, db: Session = Depends(get_db), settings: Settings = Depends(get_settings)):
    if not payload.record_id:
        raise HTTPException(status_code=422, detail="record_id is required")
    _active_record(db, payload.record_id)
    return _queue(db, settings, "organize_record", record_id=payload.record_id)


@router.post("/generate-biography", response_model=ProcessingJobRead, status_code=202)
def generate_biography(payload: AIObjectRequest, db: Session = Depends(get_db), settings: Settings = Depends(get_settings)):
    if not payload.person_id:
        raise HTTPException(status_code=422, detail="person_id is required")
    _active_person(db, payload.person_id)
    return _queue(db, settings, "generate_biography", person_id=payload.person_id, main_model=True)


@router.post("/generate-interview-questions", response_model=ProcessingJobRead, status_code=202)
def generate_interview_questions(payload: AIObjectRequest, db: Session = Depends(get_db), settings: Settings = Depends(get_settings)):
    if not payload.person_id:
        raise HTTPException(status_code=422, detail="person_id is required")
    _active_person(db, payload.person_id)
    return _queue(db, settings, "generate_interview_questions", person_id=payload.person_id)


@router.post("/ask", response_model=ProcessingJobRead, status_code=202)
def ask(payload: AIAskRequest, db: Session = Depends(get_db), settings: Settings = Depends(get_settings)):
    if payload.person_id:
        _active_person(db, payload.person_id)
    return _queue(db, settings, "ask", person_id=payload.person_id, payload={"question": payload.question}, main_model=True)


@router.get("/jobs/{job_id}", response_model=ProcessingJobRead)
def get_job(job_id: str, db: Session = Depends(get_db)):
    job = db.scalar(select(ProcessingJob).where(ProcessingJob.id == job_id, ProcessingJob.family_id == DEFAULT_FAMILY_ID))
    if job is None:
        raise HTTPException(status_code=404, detail="AI job not found")
    return job


@router.post("/jobs/{job_id}/retry", response_model=ProcessingJobRead)
def retry_job(job_id: str, db: Session = Depends(get_db), settings: Settings = Depends(get_settings)):
    _require_ai(settings)
    job = db.scalar(select(ProcessingJob).where(ProcessingJob.id == job_id, ProcessingJob.family_id == DEFAULT_FAMILY_ID))
    if job is None:
        raise HTTPException(status_code=404, detail="AI job not found")
    if job.status not in {"failed", "pending"}:
        raise HTTPException(status_code=409, detail="Only failed jobs can be retried")
    job.status = "pending"
    job.attempt_count = 0
    job.last_error = None
    job.updated_at = utc_now()
    db.commit()
    db.refresh(job)
    return job


@router.get("/artifacts", response_model=list[AIArtifactRead])
def list_artifacts(
    record_id: str | None = Query(default=None),
    person_id: str | None = Query(default=None),
    db: Session = Depends(get_db),
):
    query = select(AIArtifact).where(AIArtifact.family_id == DEFAULT_FAMILY_ID)
    if record_id:
        query = query.where(AIArtifact.record_id == record_id)
    if person_id:
        query = query.where(AIArtifact.person_id == person_id)
    return db.scalars(query.order_by(AIArtifact.created_at.desc()).limit(100)).all()


@router.get("/artifacts/{artifact_id}", response_model=AIArtifactRead)
def get_artifact(artifact_id: str, db: Session = Depends(get_db)):
    artifact = db.scalar(select(AIArtifact).where(AIArtifact.id == artifact_id, AIArtifact.family_id == DEFAULT_FAMILY_ID))
    if artifact is None:
        raise HTTPException(status_code=404, detail="AI artifact not found")
    return artifact


@router.post("/artifacts/{artifact_id}/confirm", response_model=AIArtifactRead)
def confirm_artifact(artifact_id: str, payload: ConfirmArtifactRequest, db: Session = Depends(get_db), settings: Settings = Depends(get_settings)):
    artifact = db.scalar(select(AIArtifact).where(AIArtifact.id == artifact_id, AIArtifact.family_id == DEFAULT_FAMILY_ID))
    if artifact is None:
        raise HTTPException(status_code=404, detail="AI artifact not found")
    if artifact.user_confirmed:
        return artifact
    if not payload.confirmed:
        artifact.status = "rejected"
        db.commit()
        db.refresh(artifact)
        return artifact
    if artifact.artifact_type == "organized_record" and artifact.record_id:
        result = OrganizeResult.model_validate(artifact.output_json)
        record = _active_record(db, artifact.record_id)
        record.ai_summary = result.summary
        record.ai_processing_status = "confirmed"
        for name in {item.strip() for item in result.tags if item.strip()}:
            tag = db.scalar(select(Tag).where(Tag.family_id == DEFAULT_FAMILY_ID, Tag.deleted_at.is_(None), func.lower(Tag.name) == name.lower()))
            if tag is None:
                tag = Tag(name=name)
                db.add(tag)
                db.flush()
            if tag not in record.tags:
                record.tags.append(tag)
        valid_people = {item.id for item in db.scalars(select(Person).where(Person.family_id == DEFAULT_FAMILY_ID, Person.deleted_at.is_(None))).all()}
        for suggestion in result.claims:
            db.add(Claim(
                subject_person_id=suggestion.subject_person_id if suggestion.subject_person_id in valid_people else None,
                predicate=suggestion.predicate,
                object_person_id=suggestion.object_person_id if suggestion.object_person_id in valid_people else None,
                object_text=suggestion.object_text,
                source_record_id=record.id,
                claim_type="ai_suggestion",
                verification_status="confirmed_fact",
                confidence=suggestion.confidence,
                confirmed_at=utc_now(),
            ))
    artifact.user_confirmed = True
    artifact.status = "confirmed"
    if artifact.record_id:
        record = _active_record(db, artifact.record_id)
        queue_source(db, record, settings)
    db.commit()
    db.refresh(artifact)
    return artifact
