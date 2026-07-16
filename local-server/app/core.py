from __future__ import annotations

from datetime import datetime, timezone
from typing import Any, TypeVar

from fastapi import APIRouter, Depends, HTTPException, Query, status
from sqlalchemy import func, select
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session

from app.database import get_db
from app.models import (
    DEFAULT_FAMILY_ID,
    DEFAULT_OWNER_ID,
    Person,
    Record,
    Relationship,
    SyncChange,
    Tag,
    new_uuid,
)
from app.schemas import (
    PersonCreate,
    PersonRead,
    PersonUpdate,
    RecordCreate,
    RecordRead,
    RecordUpdate,
    RelationshipCreate,
    RelationshipRead,
    RelationshipUpdate,
    SyncPullResponse,
    SyncPushRequest,
    SyncPushResponse,
    SyncPushResult,
    TagCreate,
    TagRead,
)
from app.security import require_access_token

router = APIRouter(dependencies=[Depends(require_access_token)])
ModelT = TypeVar("ModelT", Person, Relationship, Record, Tag)


def now() -> datetime:
    return datetime.now(timezone.utc)


def active_query(model):
    return select(model).where(model.family_id == DEFAULT_FAMILY_ID, model.deleted_at.is_(None))


def get_active(db: Session, model: type[ModelT], entity_id: str) -> ModelT:
    entity = db.scalar(active_query(model).where(model.id == entity_id))
    if entity is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail=f"{model.__name__} not found")
    return entity


def log_change(db: Session, entity_type: str, entity_id: str, operation: str) -> None:
    db.add(SyncChange(entity_type=entity_type, entity_id=entity_id, operation=operation))


def commit(db: Session) -> None:
    try:
        db.commit()
    except IntegrityError as error:
        db.rollback()
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Related data is invalid") from error


def create_entity(db: Session, model: type[ModelT], data: dict[str, Any], entity_type: str) -> ModelT:
    values = dict(data)
    entity_id = values.pop("id", None) or new_uuid()
    client_uuid = values.pop("client_uuid", None) or new_uuid()
    entity = model(
        **values,
        owner_id=DEFAULT_OWNER_ID,
        family_id=DEFAULT_FAMILY_ID,
        id=entity_id,
        client_uuid=client_uuid,
    )
    db.add(entity)
    db.flush()
    log_change(db, entity_type, entity.id, "upsert")
    commit(db)
    db.refresh(entity)
    return entity


def update_entity(db: Session, entity: ModelT, data: dict[str, Any], entity_type: str) -> ModelT:
    for key, value in data.items():
        if key not in {"id", "client_uuid", "owner_id", "family_id", "created_at", "deleted_at"}:
            setattr(entity, key, value)
    entity.sync_version += 1
    entity.updated_at = now()
    log_change(db, entity_type, entity.id, "upsert")
    commit(db)
    db.refresh(entity)
    return entity


def delete_entity(db: Session, entity: ModelT, entity_type: str) -> None:
    entity.deleted_at = now()
    entity.updated_at = now()
    entity.sync_version += 1
    log_change(db, entity_type, entity.id, "delete")
    commit(db)


def person_read(person: Person) -> PersonRead:
    return PersonRead.model_validate(person)


def relationship_read(item: Relationship) -> RelationshipRead:
    return RelationshipRead.model_validate(item)


def tag_read(tag: Tag) -> TagRead:
    return TagRead.model_validate(tag)


def record_read(record: Record) -> RecordRead:
    values = {column.name: getattr(record, column.name) for column in Record.__table__.columns}
    values["tag_ids"] = [tag.id for tag in record.tags if tag.deleted_at is None]
    return RecordRead.model_validate(values)


@router.get("/persons", response_model=list[PersonRead])
def list_persons(search: str = "", db: Session = Depends(get_db)):
    query = active_query(Person).order_by(Person.is_self.desc(), Person.name)
    if search:
        query = query.where(Person.name.ilike(f"%{search.strip()}%"))
    return [person_read(item) for item in db.scalars(query).all()]


@router.post("/persons", response_model=PersonRead, status_code=201)
def create_person(payload: PersonCreate, db: Session = Depends(get_db)):
    return person_read(create_entity(db, Person, payload.model_dump(exclude_none=True), "person"))


@router.get("/persons/{entity_id}", response_model=PersonRead)
def get_person(entity_id: str, db: Session = Depends(get_db)):
    return person_read(get_active(db, Person, entity_id))


@router.patch("/persons/{entity_id}", response_model=PersonRead)
def update_person(entity_id: str, payload: PersonUpdate, db: Session = Depends(get_db)):
    return person_read(update_entity(db, get_active(db, Person, entity_id), payload.model_dump(exclude_unset=True, exclude_none=True), "person"))


@router.delete("/persons/{entity_id}", status_code=204)
def delete_person(entity_id: str, db: Session = Depends(get_db)):
    delete_entity(db, get_active(db, Person, entity_id), "person")


@router.get("/relationships", response_model=list[RelationshipRead])
def list_relationships(person_id: str | None = None, db: Session = Depends(get_db)):
    query = active_query(Relationship).order_by(Relationship.created_at)
    if person_id:
        query = query.where((Relationship.person_a_id == person_id) | (Relationship.person_b_id == person_id))
    return [relationship_read(item) for item in db.scalars(query).all()]


@router.post("/relationships", response_model=RelationshipRead, status_code=201)
def create_relationship(payload: RelationshipCreate, db: Session = Depends(get_db)):
    get_active(db, Person, payload.person_a_id)
    get_active(db, Person, payload.person_b_id)
    return relationship_read(create_entity(db, Relationship, payload.model_dump(exclude_none=True), "relationship"))


@router.patch("/relationships/{entity_id}", response_model=RelationshipRead)
def update_relationship(entity_id: str, payload: RelationshipUpdate, db: Session = Depends(get_db)):
    data = payload.model_dump(exclude_unset=True, exclude_none=True)
    for person_key in ("person_a_id", "person_b_id"):
        if person_key in data:
            get_active(db, Person, data[person_key])
    return relationship_read(update_entity(db, get_active(db, Relationship, entity_id), data, "relationship"))


@router.delete("/relationships/{entity_id}", status_code=204)
def delete_relationship(entity_id: str, db: Session = Depends(get_db)):
    delete_entity(db, get_active(db, Relationship, entity_id), "relationship")


@router.get("/tags", response_model=list[TagRead])
def list_tags(db: Session = Depends(get_db)):
    return [tag_read(item) for item in db.scalars(active_query(Tag).order_by(Tag.name)).all()]


@router.post("/tags", response_model=TagRead, status_code=201)
def create_tag(payload: TagCreate, db: Session = Depends(get_db)):
    existing = db.scalar(active_query(Tag).where(func.lower(Tag.name) == payload.name.strip().lower()))
    if existing:
        return tag_read(existing)
    return tag_read(create_entity(db, Tag, payload.model_dump(exclude_none=True), "tag"))


def record_data(db: Session, payload: RecordCreate | RecordUpdate, *, partial: bool) -> tuple[dict, list[Tag]]:
    data = payload.model_dump(exclude_unset=partial, exclude_none=True)
    tag_ids = data.pop("tag_ids", [])
    person_ids = data.get("person_ids", [])
    if data.get("author_person_id"):
        get_active(db, Person, data["author_person_id"])
    for person_id in person_ids:
        get_active(db, Person, person_id)
    tags = [get_active(db, Tag, tag_id) for tag_id in tag_ids]
    return data, tags


@router.get("/records", response_model=list[RecordRead])
def list_records(person_id: str | None = None, db: Session = Depends(get_db)):
    records = db.scalars(active_query(Record).order_by(Record.occurred_at_start.desc().nullslast(), Record.created_at.desc())).unique().all()
    if person_id:
        records = [item for item in records if person_id in item.person_ids or item.author_person_id == person_id]
    return [record_read(item) for item in records]


@router.post("/records", response_model=RecordRead, status_code=201)
def create_record(payload: RecordCreate, db: Session = Depends(get_db)):
    data, tags = record_data(db, payload, partial=False)
    record = create_entity(db, Record, data, "record")
    record.tags = tags
    commit(db)
    db.refresh(record)
    return record_read(record)


@router.get("/records/{entity_id}", response_model=RecordRead)
def get_record(entity_id: str, db: Session = Depends(get_db)):
    return record_read(get_active(db, Record, entity_id))


@router.patch("/records/{entity_id}", response_model=RecordRead)
def update_record(entity_id: str, payload: RecordUpdate, db: Session = Depends(get_db)):
    data, tags = record_data(db, payload, partial=True)
    record = update_entity(db, get_active(db, Record, entity_id), data, "record")
    if "tag_ids" in payload.model_fields_set:
        record.tags = tags
        commit(db)
    return record_read(record)


@router.delete("/records/{entity_id}", status_code=204)
def delete_record(entity_id: str, db: Session = Depends(get_db)):
    delete_entity(db, get_active(db, Record, entity_id), "record")


@router.get("/sync/changes", response_model=SyncPullResponse)
def sync_changes(after: int = Query(default=0, ge=0), limit: int = Query(default=200, ge=1, le=500), db: Session = Depends(get_db)):
    changes = db.scalars(select(SyncChange).where(SyncChange.version > after).order_by(SyncChange.version).limit(limit)).all()
    latest = db.scalar(select(func.max(SyncChange.version))) or 0
    return SyncPullResponse(changes=changes, latest_version=latest)


ENTITY_CONFIG = {
    "person": (Person, PersonCreate),
    "relationship": (Relationship, RelationshipCreate),
    "record": (Record, RecordCreate),
    "tag": (Tag, TagCreate),
}


@router.post("/sync/push", response_model=SyncPushResponse)
def sync_push(payload: SyncPushRequest, db: Session = Depends(get_db)):
    results: list[SyncPushResult] = []
    for change in payload.changes:
        model, schema = ENTITY_CONFIG[change.entity_type]
        existing = db.scalar(select(model).where(model.client_uuid == change.client_uuid))
        if change.operation == "delete":
            if existing and existing.deleted_at is None:
                delete_entity(db, existing, change.entity_type)
            results.append(SyncPushResult(client_uuid=change.client_uuid, entity_id=existing.id if existing else None, status="deleted"))
            continue
        try:
            parsed = schema.model_validate({**change.payload, "client_uuid": change.client_uuid})
            data = parsed.model_dump(exclude_none=True)
            if model is Record:
                tag_ids = data.pop("tag_ids", [])
                tags = [get_active(db, Tag, tag_id) for tag_id in tag_ids]
            if existing:
                entity = update_entity(db, existing, data, change.entity_type)
            else:
                entity = create_entity(db, model, data, change.entity_type)
            if model is Record:
                entity.tags = tags
                commit(db)
            results.append(SyncPushResult(client_uuid=change.client_uuid, entity_id=entity.id, status="synced"))
        except (HTTPException, ValueError):
            db.rollback()
            results.append(SyncPushResult(client_uuid=change.client_uuid, entity_id=None, status="rejected"))
    return SyncPushResponse(results=results)
