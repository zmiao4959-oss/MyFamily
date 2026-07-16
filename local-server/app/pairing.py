from __future__ import annotations

import secrets
from datetime import datetime, timezone

from fastapi import APIRouter, Depends, HTTPException
from pydantic import BaseModel
from sqlalchemy import select
from sqlalchemy.orm import Session

from app.config import Settings, get_settings
from app.database import get_db
from app.models import AppSetting, PairingToken
from app.security import ADMIN_PAIRING_HASH, require_access_token, token_hash

router = APIRouter(prefix="/pair", dependencies=[Depends(require_access_token)])


class DeviceRead(BaseModel):
    id: str
    device_name: str
    created_at: datetime
    last_used_at: datetime | None
    revoked_at: datetime | None
    current: bool


@router.get("/tokens", response_model=list[DeviceRead])
def list_tokens(current: PairingToken = Depends(require_access_token), db: Session = Depends(get_db)) -> list[DeviceRead]:
    tokens = db.scalars(select(PairingToken).order_by(PairingToken.created_at.desc())).all()
    return [DeviceRead(
        id=item.id, device_name=item.device_name, created_at=item.created_at,
        last_used_at=item.last_used_at, revoked_at=item.revoked_at, current=item.id == current.id,
    ) for item in tokens]


@router.delete("/tokens/{token_id}")
def revoke_token(token_id: str, current: PairingToken = Depends(require_access_token), db: Session = Depends(get_db)) -> dict:
    token = db.get(PairingToken, token_id)
    if token is None:
        raise HTTPException(status_code=404, detail="Device token not found")
    if token.id == current.id:
        raise HTTPException(status_code=409, detail="Use disconnect to revoke the current device")
    token.revoked_at = datetime.now(timezone.utc)
    db.commit()
    return {"revoked": True}


@router.post("/admin-token/regenerate")
def regenerate_admin_token(db: Session = Depends(get_db), settings: Settings = Depends(get_settings)) -> dict:
    raw = secrets.token_urlsafe(18)
    setting = db.get(AppSetting, ADMIN_PAIRING_HASH)
    digest = token_hash(raw, settings.app_secret)
    if setting is None:
        db.add(AppSetting(key=ADMIN_PAIRING_HASH, value=digest))
    else:
        setting.value = digest
    db.commit()
    return {"pairing_token": raw, "shown_once": True}
