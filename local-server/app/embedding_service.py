from __future__ import annotations

import hashlib
import logging
from datetime import datetime, timedelta, timezone
from pathlib import Path

from sqlalchemy import select
from sqlalchemy.orm import Session

from app.config import Settings
from app.embedding_provider import EmbeddingProviderError, VolcanoEmbeddingProvider
from app.models import (
    DEFAULT_FAMILY_ID,
    DEFAULT_OWNER_ID,
    Embedding,
    MediaAsset,
    Person,
    ProcessingJob,
    Record,
    new_uuid,
)

logger = logging.getLogger("family-memory-embedding")


def _hash(value: str | bytes) -> str:
    payload = value.encode("utf-8") if isinstance(value, str) else value
    return hashlib.sha256(payload).hexdigest()


def _record_text(record: Record) -> str:
    tags = " ".join(tag.name for tag in record.tags if tag.deleted_at is None)
    return "\n".join(part for part in [record.title, record.original_text, record.edited_text, record.ai_summary, record.location_text, tags] if part.strip())


def _person_text(person: Person) -> str:
    return "\n".join(part for part in [person.name, person.nickname, person.biography, person.notes, person.birth_place, person.ancestral_home, " ".join(person.former_residences)] if part.strip())


def _source(db: Session, item: Embedding, settings: Settings) -> tuple[str | Path, str, str, datetime] | None:
    if item.object_type == "record":
        record = db.scalar(select(Record).where(Record.id == item.object_id, Record.family_id == DEFAULT_FAMILY_ID, Record.deleted_at.is_(None)))
        if record is None:
            return None
        text = _record_text(record)
        return text, "text/plain", _hash(text), record.updated_at
    if item.object_type == "person":
        person = db.scalar(select(Person).where(Person.id == item.object_id, Person.family_id == DEFAULT_FAMILY_ID, Person.deleted_at.is_(None)))
        if person is None:
            return None
        text = _person_text(person)
        return text, "text/plain", _hash(text), person.updated_at
    if item.object_type == "media":
        media = db.scalar(select(MediaAsset).where(MediaAsset.id == item.object_id, MediaAsset.family_id == DEFAULT_FAMILY_ID, MediaAsset.deleted_at.is_(None)))
        if media is None or media.media_type != "image":
            return None
        relative = media.thumbnail_path or media.storage_path
        root = settings.media_root.resolve()
        path = (root / relative).resolve()
        try:
            path.relative_to(root)
        except ValueError:
            return None
        if not path.is_file():
            return None
        record = db.get(Record, media.record_id)
        caption = record.title if record and record.deleted_at is None else "家族图片"
        return path, "image/jpeg" if media.thumbnail_path else media.mime_type, media.sha256, media.created_at
    return None


def queue_source(db: Session, source: Record | Person | MediaAsset, settings: Settings) -> int:
    if not settings.feature_multimodal_search:
        return 0
    if isinstance(source, Record):
        object_type, modality, content, updated = "record", "text", _record_text(source), source.updated_at
        content_hash = _hash(content)
        record_id, person_id = source.id, None
    elif isinstance(source, Person):
        object_type, modality, content, updated = "person", "text", _person_text(source), source.updated_at
        content_hash = _hash(content)
        record_id, person_id = None, source.id
    elif isinstance(source, MediaAsset) and source.media_type == "image":
        object_type, modality, content_hash, updated = "media", "image", source.sha256, source.created_at
        record_id, person_id = source.record_id, None
    else:
        return 0
    model = settings.volcano_embedding_model
    endpoint = settings.volcano_embedding_endpoint_id
    item = db.scalar(select(Embedding).where(
        Embedding.object_type == object_type,
        Embedding.object_id == source.id,
        Embedding.modality == modality,
        Embedding.model == model,
        Embedding.endpoint_id == endpoint,
    ))
    if item and item.content_hash == content_hash and item.status in {"pending", "ready"}:
        return 0
    if item is None:
        item = Embedding(
            id=new_uuid(), owner_id=DEFAULT_OWNER_ID, family_id=DEFAULT_FAMILY_ID,
            object_type=object_type, object_id=source.id, modality=modality, model=model,
            endpoint_id=endpoint, content_hash=content_hash, source_updated_at=updated,
        )
        db.add(item)
        db.flush()
    else:
        item.content_hash = content_hash
        item.source_updated_at = updated
        item.vector = None
        item.status = "pending"
        item.last_error = None
    db.add(ProcessingJob(
        id=new_uuid(), owner_id=DEFAULT_OWNER_ID, family_id=DEFAULT_FAMILY_ID,
        job_type="embedding_generate", record_id=record_id, person_id=person_id,
        status="pending", provider="volcano_ark", model=endpoint or model,
        payload_json={"embedding_id": item.id}, max_attempts=3,
    ))
    return 1


def queue_all(db: Session, settings: Settings) -> dict[str, int]:
    counts = {"records": 0, "persons": 0, "images": 0}
    for record in db.scalars(select(Record).where(Record.family_id == DEFAULT_FAMILY_ID, Record.deleted_at.is_(None))).unique().all():
        counts["records"] += queue_source(db, record, settings)
    for person in db.scalars(select(Person).where(Person.family_id == DEFAULT_FAMILY_ID, Person.deleted_at.is_(None))).all():
        counts["persons"] += queue_source(db, person, settings)
    for media in db.scalars(select(MediaAsset).where(MediaAsset.family_id == DEFAULT_FAMILY_ID, MediaAsset.media_type == "image", MediaAsset.deleted_at.is_(None))).all():
        counts["images"] += queue_source(db, media, settings)
    db.commit()
    return counts


def process_next_embedding_job(db: Session, provider: VolcanoEmbeddingProvider, settings: Settings) -> ProcessingJob | None:
    stale_before = datetime.now(timezone.utc) - timedelta(minutes=10)
    for stale in db.scalars(select(ProcessingJob).where(
        ProcessingJob.job_type == "embedding_generate", ProcessingJob.status == "running", ProcessingJob.updated_at < stale_before,
    )).all():
        stale.status = "pending"
    job = db.scalar(select(ProcessingJob).where(
        ProcessingJob.job_type == "embedding_generate", ProcessingJob.status == "pending",
    ).order_by(ProcessingJob.created_at).with_for_update(skip_locked=True))
    if job is None:
        db.commit()
        return None
    job.status = "running"
    db.commit()
    item = db.get(Embedding, job.payload_json.get("embedding_id"))
    try:
        if item is None:
            raise EmbeddingProviderError("Embedding target is missing")
        source = _source(db, item, settings)
        if source is None:
            item.status = "obsolete"
            job.status = "completed"
            db.commit()
            return job
        value, mime_type, content_hash, updated = source
        item.content_hash = content_hash
        item.source_updated_at = updated
        if item.modality == "image":
            record = db.get(Record, job.record_id) if job.record_id else None
            vector = provider.embed_image(value, mime_type, record.title if record else "家族图片")
        else:
            vector = provider.embed_text(str(value))
        item.vector = vector
        item.status = "ready"
        item.last_error = None
        job.status = "completed"
        job.last_error = None
    except Exception as error:
        job.attempt_count += 1
        job.status = "pending" if job.attempt_count < job.max_attempts else "failed"
        job.last_error = "Embedding provider request failed"
        if item is not None:
            item.status = "pending" if job.status == "pending" else "failed"
            item.last_error = job.last_error
        logger.warning("Embedding job %s failed: %s", job.id, type(error).__name__)
    db.commit()
    return job
