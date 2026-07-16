from __future__ import annotations

import math
from datetime import datetime

from fastapi import APIRouter, Depends, HTTPException, status
from pydantic import BaseModel, Field
from sqlalchemy import func, or_, select
from sqlalchemy.orm import Session

from app.config import Settings, get_settings
from app.database import get_db
from app.embedding_provider import EmbeddingProviderError, VolcanoEmbeddingProvider
from app.embedding_service import queue_all
from app.models import DEFAULT_FAMILY_ID, Embedding, MediaAsset, Person, ProcessingJob, Record, Tag
from app.security import require_access_token

router = APIRouter(prefix="/search", dependencies=[Depends(require_access_token)])


class SearchRequest(BaseModel):
    query: str = Field(min_length=1, max_length=500)
    person_id: str | None = None
    tag_id: str | None = None
    media_type: str | None = None
    year_from: int | None = Field(default=None, ge=1, le=9999)
    year_to: int | None = Field(default=None, ge=1, le=9999)
    limit: int = Field(default=20, ge=1, le=50)


class SearchResult(BaseModel):
    result_type: str
    object_id: str
    record_id: str | None = None
    person_id: str | None = None
    media_id: str | None = None
    media_type: str | None = None
    title: str
    snippet: str = ""
    score: float


class SearchResponse(BaseModel):
    mode: str
    results: list[SearchResult]
    message: str = ""


class SearchStatus(BaseModel):
    enabled: bool
    configured: bool
    provider: str
    model: str
    endpoint_id: str
    ready: int
    pending: int
    failed: int


def _configured(settings: Settings) -> bool:
    return bool(settings.volcano_ark_api_key and (settings.volcano_embedding_endpoint_id or settings.volcano_embedding_model))


@router.get("/status", response_model=SearchStatus)
def search_status(db: Session = Depends(get_db), settings: Settings = Depends(get_settings)):
    counts = dict(db.execute(
        select(Embedding.status, func.count()).where(
            Embedding.family_id == DEFAULT_FAMILY_ID,
            Embedding.model == settings.volcano_embedding_model,
            Embedding.endpoint_id == settings.volcano_embedding_endpoint_id,
        ).group_by(Embedding.status)
    ).all())
    return SearchStatus(
        enabled=settings.feature_multimodal_search,
        configured=_configured(settings),
        provider="volcano_ark",
        model=settings.volcano_embedding_model,
        endpoint_id=settings.volcano_embedding_endpoint_id,
        ready=counts.get("ready", 0), pending=counts.get("pending", 0), failed=counts.get("failed", 0),
    )


def _allowed_records(db: Session, payload: SearchRequest) -> list[Record]:
    query = select(Record).where(Record.family_id == DEFAULT_FAMILY_ID, Record.deleted_at.is_(None))
    if payload.year_from is not None:
        query = query.where(func.extract("year", Record.occurred_at_start) >= payload.year_from)
    if payload.year_to is not None:
        query = query.where(func.extract("year", Record.occurred_at_start) <= payload.year_to)
    records = db.scalars(query).unique().all()
    if payload.person_id:
        records = [item for item in records if payload.person_id in item.person_ids or payload.person_id == item.author_person_id]
    if payload.tag_id:
        records = [item for item in records if any(tag.id == payload.tag_id for tag in item.tags)]
    if payload.media_type:
        record_ids = set(db.scalars(select(MediaAsset.record_id).where(
            MediaAsset.family_id == DEFAULT_FAMILY_ID, MediaAsset.deleted_at.is_(None), MediaAsset.media_type == payload.media_type,
        )).all())
        records = [item for item in records if item.id in record_ids]
    return records


@router.post("/text", response_model=SearchResponse)
def text_search(payload: SearchRequest, db: Session = Depends(get_db)):
    term = payload.query.strip().lower()
    records = _allowed_records(db, payload)
    results: list[SearchResult] = []
    for record in records:
        tags = " ".join(tag.name for tag in record.tags if tag.deleted_at is None)
        searchable = "\n".join([record.title, record.original_text, record.edited_text, record.ai_summary, record.location_text, tags]).lower()
        if term in searchable:
            results.append(SearchResult(
                result_type="record", object_id=record.id, record_id=record.id, title=record.title,
                snippet=(record.ai_summary or record.edited_text or record.original_text)[:180], score=1.0,
            ))
    people_query = select(Person).where(
        Person.family_id == DEFAULT_FAMILY_ID, Person.deleted_at.is_(None),
        or_(Person.name.ilike(f"%{term}%"), Person.nickname.ilike(f"%{term}%"), Person.biography.ilike(f"%{term}%")),
    )
    if payload.person_id:
        people_query = people_query.where(Person.id == payload.person_id)
    people = [] if payload.media_type or payload.tag_id or payload.year_from or payload.year_to else db.scalars(people_query).all()
    results.extend(SearchResult(
        result_type="person", object_id=person.id, person_id=person.id, title=person.name,
        snippet=(person.biography or person.notes)[:180], score=1.0,
    ) for person in people)
    return SearchResponse(mode="text", results=results[:payload.limit], message="关键词搜索在未配置模型时也可使用")


def _cosine(left: list[float], right: list[float]) -> float:
    if len(left) != len(right) or not left:
        return -1.0
    dot = sum(a * b for a, b in zip(left, right))
    norm = math.sqrt(sum(a * a for a in left)) * math.sqrt(sum(b * b for b in right))
    return dot / norm if norm else -1.0


def _semantic(payload: SearchRequest, db: Session, settings: Settings, *, images_only: bool) -> SearchResponse:
    if not settings.feature_multimodal_search:
        raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail="Multimodal search is disabled")
    if not _configured(settings):
        raise HTTPException(status_code=status.HTTP_503_SERVICE_UNAVAILABLE, detail="Volcano embedding is not configured")
    try:
        query_vector = VolcanoEmbeddingProvider(settings).embed_text(payload.query.strip())
    except EmbeddingProviderError as error:
        raise HTTPException(status_code=503, detail="Embedding provider unavailable") from error
    allowed_records = {item.id: item for item in _allowed_records(db, payload)}
    people_query = select(Person).where(Person.family_id == DEFAULT_FAMILY_ID, Person.deleted_at.is_(None))
    if payload.person_id:
        people_query = people_query.where(Person.id == payload.person_id)
    allowed_people = {} if images_only or payload.media_type or payload.tag_id or payload.year_from or payload.year_to else {
        item.id: item for item in db.scalars(people_query).all()
    }
    allowed_media = {
        item.id: item for item in db.scalars(select(MediaAsset).where(
            MediaAsset.family_id == DEFAULT_FAMILY_ID, MediaAsset.deleted_at.is_(None), MediaAsset.media_type == "image",
        )).all() if item.record_id in allowed_records
    }
    modalities = ["image"] if images_only else ["text", "image"]
    embeddings = db.scalars(select(Embedding).where(
        Embedding.family_id == DEFAULT_FAMILY_ID,
        Embedding.model == settings.volcano_embedding_model,
        Embedding.endpoint_id == settings.volcano_embedding_endpoint_id,
        Embedding.status == "ready", Embedding.modality.in_(modalities), Embedding.vector.is_not(None),
    )).all()
    ranked: list[SearchResult] = []
    for item in embeddings:
        if item.object_type == "record" and not images_only and item.object_id in allowed_records:
            record = allowed_records[item.object_id]
            ranked.append(SearchResult(result_type="record", object_id=record.id, record_id=record.id, title=record.title, snippet=(record.ai_summary or record.original_text)[:180], score=_cosine(query_vector, item.vector)))
        elif item.object_type == "person" and not images_only and item.object_id in allowed_people:
            person = allowed_people[item.object_id]
            ranked.append(SearchResult(result_type="person", object_id=person.id, person_id=person.id, title=person.name, snippet=person.biography[:180], score=_cosine(query_vector, item.vector)))
        elif item.object_type == "media" and item.object_id in allowed_media:
            media = allowed_media[item.object_id]
            record = allowed_records[media.record_id]
            ranked.append(SearchResult(result_type="image", object_id=media.id, record_id=record.id, media_id=media.id, media_type=media.media_type, title=record.title, snippet="相关家族图片", score=_cosine(query_vector, item.vector)))
    ranked = [item for item in ranked if item.score >= -0.5]
    ranked.sort(key=lambda item: item.score, reverse=True)
    return SearchResponse(mode="multimodal" if images_only else "semantic", results=ranked[:payload.limit])


@router.post("/semantic", response_model=SearchResponse)
def semantic_search(payload: SearchRequest, db: Session = Depends(get_db), settings: Settings = Depends(get_settings)):
    return _semantic(payload, db, settings, images_only=False)


@router.post("/multimodal", response_model=SearchResponse)
def multimodal_search(payload: SearchRequest, db: Session = Depends(get_db), settings: Settings = Depends(get_settings)):
    return _semantic(payload, db, settings, images_only=True)


@router.post("/reindex")
def reindex(db: Session = Depends(get_db), settings: Settings = Depends(get_settings)):
    if not settings.feature_multimodal_search:
        raise HTTPException(status_code=409, detail="Multimodal search is disabled")
    counts = queue_all(db, settings)
    return {"queued": sum(counts.values()), **counts}


@router.post("/jobs/{job_id}/retry")
def retry_embedding(job_id: str, db: Session = Depends(get_db)):
    job = db.scalar(select(ProcessingJob).where(
        ProcessingJob.id == job_id, ProcessingJob.family_id == DEFAULT_FAMILY_ID, ProcessingJob.job_type == "embedding_generate",
    ))
    if job is None:
        raise HTTPException(status_code=404, detail="Embedding job not found")
    job.status = "pending"
    job.attempt_count = 0
    job.last_error = None
    job.updated_at = datetime.now().astimezone()
    db.commit()
    return {"id": job.id, "status": job.status}
