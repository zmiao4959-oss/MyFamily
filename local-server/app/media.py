from __future__ import annotations

import hashlib
import os
import re
import subprocess
from pathlib import Path

from fastapi import APIRouter, Depends, HTTPException, Query, Request, status
from fastapi.responses import FileResponse
from PIL import Image, ImageOps
from sqlalchemy import select
from sqlalchemy.orm import Session

from app.config import Settings, get_settings
from app.core import get_active
from app.database import get_db
from app.models import (
    DEFAULT_FAMILY_ID,
    DEFAULT_OWNER_ID,
    MediaAsset,
    Record,
    UploadSession,
    new_uuid,
)
from app.schemas import MediaChunkResponse, MediaInitRequest, MediaInitResponse, MediaRead
from app.security import require_access_token
from app.embedding_service import queue_source

router = APIRouter(prefix="/media", dependencies=[Depends(require_access_token)])
CHUNK_SIZE = 1024 * 1024
MIME_CONFIG = {
    "image/jpeg": ("image", ".jpg"),
    "image/png": ("image", ".png"),
    "image/webp": ("image", ".webp"),
    "image/heic": ("image", ".heic"),
    "image/heif": ("image", ".heif"),
    "audio/mp4": ("audio", ".m4a"),
    "audio/aac": ("audio", ".aac"),
    "audio/mpeg": ("audio", ".mp3"),
    "audio/wav": ("audio", ".wav"),
    "audio/ogg": ("audio", ".ogg"),
    "video/mp4": ("video", ".mp4"),
    "video/webm": ("video", ".webm"),
    "video/quicktime": ("video", ".mov"),
}


def media_read(asset: MediaAsset) -> MediaRead:
    return MediaRead.model_validate({
        **{column.name: getattr(asset, column.name) for column in MediaAsset.__table__.columns},
        "has_thumbnail": bool(asset.thumbnail_path),
    })


def root_path(settings: Settings) -> Path:
    root = settings.media_root.resolve()
    for folder in ("original", "thumbnails", ".uploads"):
        (root / folder).mkdir(parents=True, exist_ok=True)
    return root


def safe_path(root: Path, relative: str) -> Path:
    candidate = (root / relative).resolve()
    try:
        candidate.relative_to(root)
    except ValueError as error:
        raise HTTPException(status_code=400, detail="Unsafe media path") from error
    return candidate


def clean_filename(value: str) -> str:
    if Path(value).name != value or "/" in value or "\\" in value or any(ord(char) < 32 for char in value):
        raise HTTPException(status_code=400, detail="Invalid original filename")
    return value.strip()


def signature_matches(path: Path, mime_type: str) -> bool:
    with path.open("rb") as source:
        head = source.read(32)
    if mime_type == "image/jpeg":
        return head.startswith(b"\xff\xd8\xff")
    if mime_type == "image/png":
        return head.startswith(b"\x89PNG\r\n\x1a\n")
    if mime_type == "image/webp":
        return head.startswith(b"RIFF") and head[8:12] == b"WEBP"
    if mime_type in {"image/heic", "image/heif"}:
        return len(head) >= 12 and head[4:8] == b"ftyp" and head[8:12] in {b"heic", b"heix", b"hevc", b"hevx", b"mif1", b"msf1"}
    if mime_type in {"audio/mp4", "video/mp4", "video/quicktime"}:
        return len(head) >= 12 and head[4:8] == b"ftyp"
    if mime_type == "video/webm":
        return head.startswith(b"\x1aE\xdf\xa3")
    if mime_type == "audio/wav":
        return head.startswith(b"RIFF") and head[8:12] == b"WAVE"
    if mime_type == "audio/ogg":
        return head.startswith(b"OggS")
    if mime_type == "audio/mpeg":
        return head.startswith(b"ID3") or (len(head) > 1 and head[0] == 0xFF and head[1] & 0xE0 == 0xE0)
    if mime_type == "audio/aac":
        return len(head) > 1 and head[0] == 0xFF and head[1] & 0xF0 == 0xF0
    return False


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for block in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def probe_media(path: Path) -> dict:
    try:
        result = subprocess.run(
            [ffmpeg_executable(), "-hide_banner", "-i", str(path)],
            capture_output=True,
            text=True,
            timeout=20,
        )
        output = result.stderr
        duration_match = re.search(r"Duration:\s*(\d+):(\d+):([\d.]+)", output)
        size_match = re.search(r"Video:.*?\s(\d{2,5})x(\d{2,5})", output)
        duration = None
        if duration_match:
            hours, minutes, seconds = duration_match.groups()
            duration = (int(hours) * 3600 + int(minutes) * 60 + float(seconds)) * 1000
        return {
            "width": int(size_match.group(1)) if size_match else None,
            "height": int(size_match.group(2)) if size_match else None,
            "duration_ms": round(duration) if duration else None,
        }
    except (FileNotFoundError, subprocess.SubprocessError, ValueError):
        return {"width": None, "height": None, "duration_ms": None}


def ffmpeg_executable() -> str:
    try:
        import imageio_ffmpeg

        return imageio_ffmpeg.get_ffmpeg_exe()
    except (ImportError, RuntimeError):
        return "ffmpeg"


def create_thumbnail(source: Path, destination: Path, media_type: str) -> tuple[int | None, int | None]:
    destination.parent.mkdir(parents=True, exist_ok=True)
    if media_type == "image":
        try:
            with Image.open(source) as raw:
                image = ImageOps.exif_transpose(raw)
                width, height = image.size
                image.thumbnail((512, 512))
                if image.mode not in {"RGB", "L"}:
                    image = image.convert("RGB")
                image.save(destination, "JPEG", quality=85, optimize=True)
                return width, height
        except (OSError, ValueError):
            return None, None
    if media_type == "video":
        try:
            subprocess.run(
                [ffmpeg_executable(), "-y", "-ss", "0", "-i", str(source), "-frames:v", "1", "-vf", "scale=512:-2", str(destination)],
                capture_output=True,
                timeout=30,
                check=True,
            )
        except (FileNotFoundError, subprocess.SubprocessError):
            return None, None
    return None, None


@router.post("/init-upload", response_model=MediaInitResponse, status_code=201)
def init_upload(payload: MediaInitRequest, db: Session = Depends(get_db), settings: Settings = Depends(get_settings)):
    get_active(db, Record, payload.record_id)
    config = MIME_CONFIG.get(payload.mime_type.lower())
    if config is None:
        raise HTTPException(status_code=415, detail="Unsupported media type")
    maximum = settings.max_upload_size_mb * 1024 * 1024
    if payload.size_bytes > maximum:
        raise HTTPException(status_code=413, detail=f"File exceeds {settings.max_upload_size_mb} MB limit")
    filename = clean_filename(payload.original_filename)
    existing = db.scalar(select(UploadSession).where(UploadSession.client_uuid == payload.client_uuid, UploadSession.record_id == payload.record_id))
    if existing:
        if existing.expected_size != payload.size_bytes or existing.mime_type != payload.mime_type.lower():
            raise HTTPException(status_code=409, detail="Upload identifier already has different metadata")
        return MediaInitResponse(upload_id=existing.id, chunk_size=CHUNK_SIZE, bytes_received=existing.bytes_received)
    root = root_path(settings)
    upload_id = new_uuid()
    relative = f".uploads/{upload_id}.part"
    safe_path(root, relative).touch(exist_ok=False)
    session = UploadSession(
        id=upload_id,
        client_uuid=payload.client_uuid,
        record_id=payload.record_id,
        original_filename=filename,
        mime_type=payload.mime_type.lower(),
        media_type=config[0],
        expected_size=payload.size_bytes,
        expected_sha256=payload.sha256.lower() if payload.sha256 else None,
        temp_path=relative,
    )
    db.add(session)
    db.commit()
    return MediaInitResponse(upload_id=upload_id, chunk_size=CHUNK_SIZE, bytes_received=0)


@router.put("/{upload_id}/chunk", response_model=MediaChunkResponse)
async def upload_chunk(
    upload_id: str,
    request: Request,
    offset: int = Query(ge=0),
    db: Session = Depends(get_db),
    settings: Settings = Depends(get_settings),
):
    session = db.get(UploadSession, upload_id)
    if session is None:
        raise HTTPException(status_code=404, detail="Upload session not found")
    if session.status == "complete":
        return MediaChunkResponse(upload_id=upload_id, bytes_received=session.bytes_received, complete=True)
    if offset != session.bytes_received:
        raise HTTPException(status_code=409, detail={"expected_offset": session.bytes_received})
    body = await request.body()
    if not body or len(body) > CHUNK_SIZE:
        raise HTTPException(status_code=400, detail="Chunk must contain 1 byte to 1 MB")
    if session.bytes_received + len(body) > session.expected_size:
        raise HTTPException(status_code=413, detail="Chunk exceeds declared file size")
    path = safe_path(root_path(settings), session.temp_path)
    with path.open("ab") as target:
        target.write(body)
        target.flush()
        os.fsync(target.fileno())
    session.bytes_received += len(body)
    db.commit()
    return MediaChunkResponse(upload_id=upload_id, bytes_received=session.bytes_received, complete=session.bytes_received == session.expected_size)


@router.post("/{upload_id}/complete", response_model=MediaRead, status_code=201)
def complete_upload(upload_id: str, db: Session = Depends(get_db), settings: Settings = Depends(get_settings)):
    session = db.get(UploadSession, upload_id)
    if session is None:
        raise HTTPException(status_code=404, detail="Upload session not found")
    if session.status == "complete" and session.asset_id:
        return media_read(db.get(MediaAsset, session.asset_id))
    if session.bytes_received != session.expected_size:
        raise HTTPException(status_code=409, detail="Upload is incomplete")
    root = root_path(settings)
    temporary = safe_path(root, session.temp_path)
    if temporary.stat().st_size != session.expected_size or not signature_matches(temporary, session.mime_type):
        temporary.unlink(missing_ok=True)
        session.status = "rejected"
        db.commit()
        raise HTTPException(status_code=415, detail="File content does not match declared media type")
    digest = sha256_file(temporary)
    if session.expected_sha256 and digest != session.expected_sha256:
        raise HTTPException(status_code=422, detail="SHA-256 mismatch")
    duplicate = db.scalar(select(MediaAsset).where(MediaAsset.family_id == DEFAULT_FAMILY_ID, MediaAsset.sha256 == digest, MediaAsset.deleted_at.is_(None)))
    asset_id = new_uuid()
    extension = MIME_CONFIG[session.mime_type][1]
    if duplicate:
        storage_relative = duplicate.storage_path
        thumbnail_relative = duplicate.thumbnail_path
        temporary.unlink(missing_ok=True)
        details = {"width": duplicate.width, "height": duplicate.height, "duration_ms": duplicate.duration_ms}
    else:
        storage_relative = f"original/{asset_id}{extension}"
        stored = safe_path(root, storage_relative)
        os.replace(temporary, stored)
        details = probe_media(stored)
        thumbnail_relative = None
        if session.media_type in {"image", "video"}:
            thumbnail_relative = f"thumbnails/{asset_id}.jpg"
            width, height = create_thumbnail(stored, safe_path(root, thumbnail_relative), session.media_type)
            details["width"] = details.get("width") or width
            details["height"] = details.get("height") or height
            if not safe_path(root, thumbnail_relative).exists():
                thumbnail_relative = None
    asset = MediaAsset(
        id=asset_id,
        client_uuid=session.client_uuid,
        owner_id=DEFAULT_OWNER_ID,
        family_id=DEFAULT_FAMILY_ID,
        record_id=session.record_id,
        media_type=session.media_type,
        original_filename=session.original_filename,
        storage_path=storage_relative,
        thumbnail_path=thumbnail_relative,
        mime_type=session.mime_type,
        size_bytes=session.expected_size,
        sha256=digest,
        duration_ms=details.get("duration_ms"),
        width=details.get("width"),
        height=details.get("height"),
        metadata_json={"content_verified": True},
        duplicate_of_id=duplicate.id if duplicate else None,
    )
    db.add(asset)
    db.flush()
    session.status = "complete"
    session.asset_id = asset.id
    db.commit()
    db.refresh(asset)
    queue_source(db, asset, settings)
    db.commit()
    return media_read(asset)


@router.get("", response_model=list[MediaRead])
def list_media(record_id: str | None = None, db: Session = Depends(get_db)):
    query = select(MediaAsset).where(MediaAsset.family_id == DEFAULT_FAMILY_ID, MediaAsset.deleted_at.is_(None)).order_by(MediaAsset.created_at)
    if record_id:
        query = query.where(MediaAsset.record_id == record_id)
    return [media_read(item) for item in db.scalars(query).all()]


@router.get("/{asset_id}")
def download_media(asset_id: str, db: Session = Depends(get_db), settings: Settings = Depends(get_settings)):
    asset = db.scalar(select(MediaAsset).where(MediaAsset.id == asset_id, MediaAsset.family_id == DEFAULT_FAMILY_ID, MediaAsset.deleted_at.is_(None)))
    if asset is None:
        raise HTTPException(status_code=404, detail="Media not found")
    path = safe_path(root_path(settings), asset.storage_path)
    if not path.is_file():
        raise HTTPException(status_code=404, detail="Media file missing")
    return FileResponse(path, media_type=asset.mime_type, filename=asset.original_filename)


@router.get("/{asset_id}/thumbnail")
def download_thumbnail(asset_id: str, db: Session = Depends(get_db), settings: Settings = Depends(get_settings)):
    asset = db.scalar(select(MediaAsset).where(MediaAsset.id == asset_id, MediaAsset.family_id == DEFAULT_FAMILY_ID, MediaAsset.deleted_at.is_(None)))
    if asset is None or not asset.thumbnail_path:
        raise HTTPException(status_code=404, detail="Thumbnail not found")
    path = safe_path(root_path(settings), asset.thumbnail_path)
    if not path.is_file():
        raise HTTPException(status_code=404, detail="Thumbnail file missing")
    return FileResponse(path, media_type="image/jpeg")
