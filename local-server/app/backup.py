from __future__ import annotations

import hashlib
import json
from datetime import date, datetime, timezone
from pathlib import Path

import pyzipper
from fastapi import APIRouter, Depends, HTTPException, status
from fastapi.responses import FileResponse
from pydantic import BaseModel, Field
from sqlalchemy import Date as SADate, DateTime as SADateTime, delete, insert, select
from sqlalchemy.orm import Session

from app.config import Settings, get_settings
from app.database import get_db
from app.models import (
    AIArtifact, BackupRecord, Claim, Embedding, Family, MediaAsset, Person,
    ProcessingJob, Record, Relationship, SyncChange, Tag, UploadSession, User, new_uuid, record_tags,
)
from app.security import require_access_token

router = APIRouter(prefix="/backup", dependencies=[Depends(require_access_token)])

EXPORT_TABLES = [User.__table__, Family.__table__, Person.__table__, Relationship.__table__, Tag.__table__, Record.__table__, record_tags, MediaAsset.__table__, Claim.__table__, AIArtifact.__table__, Embedding.__table__]
RESTORE_FORMAT = 1


class PasswordRequest(BaseModel):
    password: str = Field(min_length=10, max_length=256)


class RestoreRequest(PasswordRequest):
    confirmation: str


class BackupRead(BaseModel):
    id: str
    filename: str
    size_bytes: int
    sha256: str
    encrypted: bool
    persons_count: int
    records_count: int
    media_count: int
    status: str
    created_at: datetime


def _json_value(value):
    if isinstance(value, (datetime, date)):
        return value.isoformat()
    if hasattr(value, "tolist"):
        return value.tolist()
    return value


def _row_dict(row, table) -> dict:
    return {column.name: _json_value(getattr(row, column.name)) for column in table.columns}


def _safe_file(root: Path, relative: str) -> Path:
    candidate = (root / relative).resolve()
    try:
        candidate.relative_to(root.resolve())
    except ValueError as error:
        raise HTTPException(status_code=400, detail="Backup contains an unsafe media path") from error
    return candidate


def _archive_path(record: BackupRecord, settings: Settings) -> Path:
    root = settings.backup_root.resolve()
    candidate = Path(record.storage_path).resolve()
    try:
        candidate.relative_to(root)
    except ValueError as error:
        raise HTTPException(status_code=400, detail="Unsafe backup path") from error
    if not candidate.is_file():
        raise HTTPException(status_code=404, detail="Backup file missing")
    return candidate


def create_encrypted_backup(db: Session, settings: Settings, password: str, reason: str = "manual") -> BackupRecord:
    root = settings.backup_root.resolve()
    root.mkdir(parents=True, exist_ok=True)
    created = datetime.now(timezone.utc)
    backup_id = new_uuid()
    filename = f"family-memory-{created.strftime('%Y%m%d-%H%M%S')}-{backup_id[:8]}.fmbackup"
    path = root / filename

    exported: dict[str, list[dict]] = {}
    for table in EXPORT_TABLES:
        rows = db.execute(select(table)).mappings().all()
        exported[table.name] = [{column.name: _json_value(row[column.name]) for column in table.columns} for row in rows]
    database_bytes = json.dumps(exported, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
    manifest = {
        "format": RESTORE_FORMAT,
        "app_version": settings.app_version,
        "created_at": created.isoformat(),
        "reason": reason,
        "database_sha256": hashlib.sha256(database_bytes).hexdigest(),
        "counts": {
            "persons": len(exported["persons"]),
            "records": len(exported["records"]),
            "media": len(exported["media_assets"]),
        },
    }
    with pyzipper.AESZipFile(path, "w", compression=pyzipper.ZIP_DEFLATED, encryption=pyzipper.WZ_AES) as archive:
        archive.setpassword(password.encode("utf-8"))
        archive.setencryption(pyzipper.WZ_AES, nbits=256)
        archive.writestr("manifest.json", json.dumps(manifest, ensure_ascii=False).encode("utf-8"))
        archive.writestr("database.json", database_bytes)
        media_root = settings.media_root.resolve()
        for asset in exported["media_assets"]:
            for kind, key in (("original", "storage_path"), ("thumbnail", "thumbnail_path")):
                relative = asset.get(key)
                if relative:
                    source = _safe_file(media_root, relative)
                    if source.is_file():
                        archive.write(source, f"media/{asset['id']}/{kind}")
    digest = hashlib.sha256(path.read_bytes()).hexdigest()
    record = BackupRecord(
        id=backup_id, filename=filename, storage_path=str(path), size_bytes=path.stat().st_size,
        sha256=digest, encrypted=True, persons_count=manifest["counts"]["persons"],
        records_count=manifest["counts"]["records"], media_count=manifest["counts"]["media"], status="ready",
    )
    db.add(record)
    db.commit()
    db.refresh(record)
    return record


def _read_archive(path: Path, password: str) -> tuple[dict, dict, dict[str, bytes]]:
    try:
        with pyzipper.AESZipFile(path, "r") as archive:
            archive.setpassword(password.encode("utf-8"))
            names = set(archive.namelist())
            if "manifest.json" not in names or "database.json" not in names:
                raise HTTPException(status_code=400, detail="Invalid backup archive")
            manifest_bytes = archive.read("manifest.json")
            database_bytes = archive.read("database.json")
            media = {name: archive.read(name) for name in names if name.startswith("media/")}
    except (RuntimeError, ValueError, pyzipper.BadZipFile) as error:
        raise HTTPException(status_code=400, detail="Wrong password or damaged backup") from error
    manifest = json.loads(manifest_bytes)
    if manifest.get("format") != RESTORE_FORMAT:
        raise HTTPException(status_code=409, detail="Unsupported backup version")
    if hashlib.sha256(database_bytes).hexdigest() != manifest.get("database_sha256"):
        raise HTTPException(status_code=400, detail="Backup integrity check failed")
    data = json.loads(database_bytes)
    if set(data) != {table.name for table in EXPORT_TABLES}:
        raise HTTPException(status_code=400, detail="Backup table set is incomplete")
    return manifest, data, media


def _read(record: BackupRecord) -> BackupRead:
    return BackupRead.model_validate(record, from_attributes=True)


def _typed_row(table, row: dict) -> dict:
    result = dict(row)
    for column in table.columns:
        value = result.get(column.name)
        if not isinstance(value, str):
            continue
        if isinstance(column.type, SADateTime):
            result[column.name] = datetime.fromisoformat(value)
        elif isinstance(column.type, SADate):
            result[column.name] = date.fromisoformat(value)
    return result


@router.post("/create", response_model=BackupRead, status_code=status.HTTP_201_CREATED)
def create_backup(request: PasswordRequest, db: Session = Depends(get_db), settings: Settings = Depends(get_settings)) -> BackupRead:
    return _read(create_encrypted_backup(db, settings, request.password))


@router.get("/list", response_model=list[BackupRead])
def list_backups(db: Session = Depends(get_db)) -> list[BackupRead]:
    records = db.scalars(select(BackupRecord).order_by(BackupRecord.created_at.desc())).all()
    return [_read(record) for record in records]


@router.get("/{backup_id}/download")
def download_backup(backup_id: str, db: Session = Depends(get_db), settings: Settings = Depends(get_settings)):
    record = db.get(BackupRecord, backup_id)
    if record is None:
        raise HTTPException(status_code=404, detail="Backup not found")
    return FileResponse(_archive_path(record, settings), filename=record.filename, media_type="application/octet-stream")


@router.post("/{backup_id}/inspect")
def inspect_backup(backup_id: str, request: PasswordRequest, db: Session = Depends(get_db), settings: Settings = Depends(get_settings)) -> dict:
    record = db.get(BackupRecord, backup_id)
    if record is None:
        raise HTTPException(status_code=404, detail="Backup not found")
    manifest, _, _ = _read_archive(_archive_path(record, settings), request.password)
    return manifest


@router.post("/{backup_id}/restore")
def restore_backup(backup_id: str, request: RestoreRequest, db: Session = Depends(get_db), settings: Settings = Depends(get_settings)) -> dict:
    if request.confirmation != "RESTORE":
        raise HTTPException(status_code=400, detail="Restore confirmation is required")
    record = db.get(BackupRecord, backup_id)
    if record is None:
        raise HTTPException(status_code=404, detail="Backup not found")
    manifest, data, media = _read_archive(_archive_path(record, settings), request.password)
    safety = create_encrypted_backup(db, settings, request.password, reason="pre_restore")
    try:
        synced_tables = {
            "persons": "person", "relationships": "relationship", "records": "record", "tags": "tag",
        }
        previous_ids = {
            name: set(db.execute(select(next(table for table in EXPORT_TABLES if table.name == name).c.id)).scalars())
            for name in synced_tables
        }
        for transient in (UploadSession.__table__, ProcessingJob.__table__):
            db.execute(delete(transient))
        for table in reversed(EXPORT_TABLES):
            db.execute(delete(table))
        for table in EXPORT_TABLES:
            rows = [_typed_row(table, row) for row in data[table.name]]
            if rows:
                db.execute(insert(table), rows)
        media_root = settings.media_root.resolve()
        for asset in data["media_assets"]:
            for kind, key in (("original", "storage_path"), ("thumbnail", "thumbnail_path")):
                member = f"media/{asset['id']}/{kind}"
                relative = asset.get(key)
                if relative and member in media:
                    destination = _safe_file(media_root, relative)
                    destination.parent.mkdir(parents=True, exist_ok=True)
                    destination.write_bytes(media[member])
        for table_name, entity_type in synced_tables.items():
            restored_rows = data[table_name]
            restored_ids = {row["id"] for row in restored_rows}
            for row in restored_rows:
                operation = "delete" if row.get("deleted_at") else "upsert"
                db.add(SyncChange(entity_type=entity_type, entity_id=row["id"], operation=operation))
            for removed_id in previous_ids[table_name] - restored_ids:
                db.add(SyncChange(entity_type=entity_type, entity_id=removed_id, operation="delete"))
        db.commit()
    except Exception:
        db.rollback()
        raise
    return {"restored": True, "counts": manifest["counts"], "safety_backup_id": safety.id}
