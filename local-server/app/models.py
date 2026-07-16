from __future__ import annotations

import uuid
from datetime import date, datetime, timezone

from sqlalchemy import JSON, Boolean, Date, DateTime, ForeignKey, Integer, String, Table, Text, Column
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.database import Base

DEFAULT_OWNER_ID = "00000000-0000-0000-0000-000000000001"
DEFAULT_FAMILY_ID = "00000000-0000-0000-0000-000000000002"


def new_uuid() -> str:
    return str(uuid.uuid4())


def utc_now() -> datetime:
    return datetime.now(timezone.utc)


class SyncMixin:
    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=new_uuid)
    client_uuid: Mapped[str] = mapped_column(String(36), unique=True, nullable=False, default=new_uuid, index=True)
    sync_version: Mapped[int] = mapped_column(Integer, nullable=False, default=1)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now, nullable=False)
    updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now, onupdate=utc_now, nullable=False)
    deleted_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)


class User(Base):
    __tablename__ = "users"
    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=new_uuid)
    display_name: Mapped[str] = mapped_column(String(120), nullable=False)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now, nullable=False)


class Family(Base):
    __tablename__ = "families"
    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=new_uuid)
    owner_id: Mapped[str] = mapped_column(ForeignKey("users.id"), nullable=False)
    name: Mapped[str] = mapped_column(String(120), nullable=False)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now, nullable=False)


class Person(SyncMixin, Base):
    __tablename__ = "persons"
    owner_id: Mapped[str] = mapped_column(ForeignKey("users.id"), nullable=False, default=DEFAULT_OWNER_ID)
    family_id: Mapped[str] = mapped_column(ForeignKey("families.id"), nullable=False, default=DEFAULT_FAMILY_ID, index=True)
    name: Mapped[str] = mapped_column(String(120), nullable=False, index=True)
    surname: Mapped[str] = mapped_column(String(60), default="", nullable=False)
    given_name: Mapped[str] = mapped_column(String(60), default="", nullable=False)
    former_names: Mapped[list[str]] = mapped_column(JSON, default=list, nullable=False)
    nickname: Mapped[str] = mapped_column(String(80), default="", nullable=False)
    gender: Mapped[str | None] = mapped_column(String(30), nullable=True)
    birth_date: Mapped[date | None] = mapped_column(Date, nullable=True)
    birth_year: Mapped[int | None] = mapped_column(Integer, nullable=True)
    birth_date_precision: Mapped[str] = mapped_column(String(30), default="unknown", nullable=False)
    death_date: Mapped[date | None] = mapped_column(Date, nullable=True)
    death_year: Mapped[int | None] = mapped_column(Integer, nullable=True)
    death_date_precision: Mapped[str] = mapped_column(String(30), default="unknown", nullable=False)
    birth_place: Mapped[str] = mapped_column(String(240), default="", nullable=False)
    ancestral_home: Mapped[str] = mapped_column(String(240), default="", nullable=False)
    former_residences: Mapped[list[str]] = mapped_column(JSON, default=list, nullable=False)
    biography: Mapped[str] = mapped_column(Text, default="", nullable=False)
    portrait_media_id: Mapped[str | None] = mapped_column(String(36), nullable=True)
    is_self: Mapped[bool] = mapped_column(Boolean, default=False, nullable=False)
    notes: Mapped[str] = mapped_column(Text, default="", nullable=False)
    source_text: Mapped[str] = mapped_column(Text, default="", nullable=False)
    verification_status: Mapped[str] = mapped_column(String(40), default="unverified_information", nullable=False)
    visibility: Mapped[str] = mapped_column(String(30), default="private", nullable=False)


class Relationship(SyncMixin, Base):
    __tablename__ = "relationships"
    owner_id: Mapped[str] = mapped_column(ForeignKey("users.id"), nullable=False, default=DEFAULT_OWNER_ID)
    family_id: Mapped[str] = mapped_column(ForeignKey("families.id"), nullable=False, default=DEFAULT_FAMILY_ID, index=True)
    person_a_id: Mapped[str] = mapped_column(ForeignKey("persons.id"), nullable=False, index=True)
    person_b_id: Mapped[str] = mapped_column(ForeignKey("persons.id"), nullable=False, index=True)
    relation_type: Mapped[str] = mapped_column(String(50), nullable=False)
    start_date: Mapped[date | None] = mapped_column(Date, nullable=True)
    end_date: Mapped[date | None] = mapped_column(Date, nullable=True)
    notes: Mapped[str] = mapped_column(Text, default="", nullable=False)
    source_text: Mapped[str] = mapped_column(Text, default="", nullable=False)
    verification_status: Mapped[str] = mapped_column(String(40), default="unverified_information", nullable=False)
    visibility: Mapped[str] = mapped_column(String(30), default="private", nullable=False)


record_tags = Table(
    "record_tags",
    Base.metadata,
    Column("record_id", String(36), ForeignKey("records.id", ondelete="CASCADE"), primary_key=True),
    Column("tag_id", String(36), ForeignKey("tags.id", ondelete="CASCADE"), primary_key=True),
)


class Tag(SyncMixin, Base):
    __tablename__ = "tags"
    owner_id: Mapped[str] = mapped_column(ForeignKey("users.id"), nullable=False, default=DEFAULT_OWNER_ID)
    family_id: Mapped[str] = mapped_column(ForeignKey("families.id"), nullable=False, default=DEFAULT_FAMILY_ID, index=True)
    name: Mapped[str] = mapped_column(String(80), nullable=False, index=True)
    color: Mapped[str] = mapped_column(String(20), default="", nullable=False)


class Record(SyncMixin, Base):
    __tablename__ = "records"
    owner_id: Mapped[str] = mapped_column(ForeignKey("users.id"), nullable=False, default=DEFAULT_OWNER_ID)
    family_id: Mapped[str] = mapped_column(ForeignKey("families.id"), nullable=False, default=DEFAULT_FAMILY_ID, index=True)
    author_person_id: Mapped[str | None] = mapped_column(ForeignKey("persons.id"), nullable=True)
    title: Mapped[str] = mapped_column(String(240), nullable=False)
    original_text: Mapped[str] = mapped_column(Text, default="", nullable=False)
    edited_text: Mapped[str] = mapped_column(Text, default="", nullable=False)
    ai_summary: Mapped[str] = mapped_column(Text, default="", nullable=False)
    record_type: Mapped[str] = mapped_column(String(40), default="text", nullable=False)
    occurred_at_start: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)
    occurred_at_end: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)
    date_precision: Mapped[str] = mapped_column(String(30), default="unknown", nullable=False)
    location_text: Mapped[str] = mapped_column(String(240), default="", nullable=False)
    visibility: Mapped[str] = mapped_column(String(30), default="private", nullable=False)
    source_type: Mapped[str] = mapped_column(String(40), default="personal_memory", nullable=False)
    verification_status: Mapped[str] = mapped_column(String(40), default="unverified_information", nullable=False)
    ai_processing_status: Mapped[str] = mapped_column(String(30), default="disabled", nullable=False)
    person_ids: Mapped[list[str]] = mapped_column(JSON, default=list, nullable=False)
    tags: Mapped[list[Tag]] = relationship(secondary=record_tags, lazy="selectin")


class SyncChange(Base):
    __tablename__ = "sync_changes"
    version: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    entity_type: Mapped[str] = mapped_column(String(40), nullable=False, index=True)
    entity_id: Mapped[str] = mapped_column(String(36), nullable=False, index=True)
    operation: Mapped[str] = mapped_column(String(20), nullable=False)
    changed_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now, nullable=False)


class PairingToken(Base):
    __tablename__ = "pairing_tokens"
    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=new_uuid)
    token_hash: Mapped[str] = mapped_column(String(64), unique=True, nullable=False, index=True)
    device_name: Mapped[str] = mapped_column(String(120), nullable=False)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now, nullable=False)
    last_used_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)
    revoked_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)
