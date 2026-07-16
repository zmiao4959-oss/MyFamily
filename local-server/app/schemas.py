from datetime import date, datetime
from typing import Literal

from pydantic import BaseModel, ConfigDict, Field, model_validator


class HealthResponse(BaseModel):
    status: str
    version: str
    database: str


class PairRequest(BaseModel):
    pairing_token: str = Field(min_length=8, max_length=256)
    device_name: str = Field(min_length=1, max_length=120)


class PairResponse(BaseModel):
    access_token: str
    token_type: str = "bearer"
    server_version: str


class RevokeResponse(BaseModel):
    revoked: bool


class CoreModel(BaseModel):
    model_config = ConfigDict(from_attributes=True)


class PersonCreate(CoreModel):
    id: str | None = None
    client_uuid: str | None = None
    name: str = Field(min_length=1, max_length=120)
    surname: str = ""
    given_name: str = ""
    former_names: list[str] = Field(default_factory=list)
    nickname: str = ""
    gender: str | None = None
    birth_date: date | None = None
    birth_year: int | None = Field(default=None, ge=1000, le=9999)
    birth_date_precision: str = "unknown"
    death_date: date | None = None
    death_year: int | None = Field(default=None, ge=1000, le=9999)
    death_date_precision: str = "unknown"
    birth_place: str = ""
    ancestral_home: str = ""
    former_residences: list[str] = Field(default_factory=list)
    biography: str = ""
    portrait_media_id: str | None = None
    is_self: bool = False
    notes: str = ""
    source_text: str = ""
    verification_status: str = "unverified_information"
    visibility: str = "private"


class PersonUpdate(PersonCreate):
    name: str | None = Field(default=None, max_length=120)


class PersonRead(PersonCreate):
    id: str
    client_uuid: str
    owner_id: str
    family_id: str
    sync_version: int
    created_at: datetime
    updated_at: datetime
    deleted_at: datetime | None


class RelationshipCreate(CoreModel):
    id: str | None = None
    client_uuid: str | None = None
    person_a_id: str
    person_b_id: str
    relation_type: str = Field(min_length=1, max_length=50)
    start_date: date | None = None
    end_date: date | None = None
    notes: str = ""
    source_text: str = ""
    verification_status: str = "unverified_information"
    visibility: str = "private"

    @model_validator(mode="after")
    def different_people(self):
        if self.person_a_id is not None and self.person_b_id is not None and self.person_a_id == self.person_b_id:
            raise ValueError("A relationship requires two different people")
        return self


class RelationshipUpdate(RelationshipCreate):
    person_a_id: str | None = None
    person_b_id: str | None = None
    relation_type: str | None = None


class RelationshipRead(RelationshipCreate):
    id: str
    client_uuid: str
    owner_id: str
    family_id: str
    sync_version: int
    created_at: datetime
    updated_at: datetime
    deleted_at: datetime | None


class TagCreate(CoreModel):
    id: str | None = None
    client_uuid: str | None = None
    name: str = Field(min_length=1, max_length=80)
    color: str = ""


class TagRead(TagCreate):
    id: str
    client_uuid: str
    owner_id: str
    family_id: str
    sync_version: int
    created_at: datetime
    updated_at: datetime
    deleted_at: datetime | None


class RecordCreate(CoreModel):
    id: str | None = None
    client_uuid: str | None = None
    author_person_id: str | None = None
    title: str = Field(min_length=1, max_length=240)
    original_text: str = ""
    edited_text: str = ""
    record_type: str = "text"
    occurred_at_start: datetime | None = None
    occurred_at_end: datetime | None = None
    date_precision: str = "unknown"
    location_text: str = ""
    visibility: str = "private"
    source_type: str = "personal_memory"
    verification_status: str = "unverified_information"
    person_ids: list[str] = Field(default_factory=list)
    tag_ids: list[str] = Field(default_factory=list)


class RecordUpdate(RecordCreate):
    title: str | None = Field(default=None, max_length=240)


class RecordRead(RecordCreate):
    id: str
    client_uuid: str
    owner_id: str
    family_id: str
    ai_summary: str
    ai_processing_status: str
    sync_version: int
    created_at: datetime
    updated_at: datetime
    deleted_at: datetime | None
    tag_ids: list[str]


class SyncChangeRead(CoreModel):
    version: int
    entity_type: str
    entity_id: str
    operation: str
    changed_at: datetime


class SyncPullResponse(CoreModel):
    changes: list[SyncChangeRead]
    latest_version: int


class SyncPushItem(CoreModel):
    entity_type: Literal["person", "relationship", "record", "tag"]
    operation: Literal["upsert", "delete"]
    client_uuid: str
    payload: dict = Field(default_factory=dict)


class SyncPushRequest(CoreModel):
    changes: list[SyncPushItem] = Field(max_length=200)


class SyncPushResult(CoreModel):
    client_uuid: str
    entity_id: str | None
    status: str


class SyncPushResponse(CoreModel):
    results: list[SyncPushResult]


class MediaInitRequest(CoreModel):
    record_id: str
    client_uuid: str
    original_filename: str = Field(min_length=1, max_length=255)
    mime_type: str = Field(min_length=3, max_length=100)
    size_bytes: int = Field(gt=0)
    sha256: str | None = Field(default=None, pattern=r"^[0-9a-fA-F]{64}$")


class MediaInitResponse(CoreModel):
    upload_id: str
    chunk_size: int
    bytes_received: int


class MediaChunkResponse(CoreModel):
    upload_id: str
    bytes_received: int
    complete: bool


class MediaRead(CoreModel):
    id: str
    client_uuid: str
    record_id: str
    media_type: str
    original_filename: str
    mime_type: str
    size_bytes: int
    sha256: str
    duration_ms: int | None
    width: int | None
    height: int | None
    captured_at: datetime | None
    metadata_json: dict
    duplicate_of_id: str | None
    has_thumbnail: bool = False
    created_at: datetime
