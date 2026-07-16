from __future__ import annotations

import hashlib
import hmac
import secrets
from datetime import datetime, timezone

from fastapi import Depends, HTTPException, status
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer
from sqlalchemy import select
from sqlalchemy.orm import Session

from app.config import Settings, get_settings
from app.database import get_db
from app.models import AppSetting, PairingToken

bearer_scheme = HTTPBearer(auto_error=False)


def token_hash(token: str, secret: str) -> str:
    return hmac.new(secret.encode(), token.encode(), hashlib.sha256).hexdigest()


ADMIN_PAIRING_HASH = "admin_pairing_token_hash"


def verify_pairing_token(provided: str, settings: Settings, db: Session | None = None) -> bool:
    override = db.get(AppSetting, ADMIN_PAIRING_HASH) if db is not None else None
    if override is not None:
        return secrets.compare_digest(token_hash(provided, settings.app_secret), override.value)
    return secrets.compare_digest(provided, settings.effective_pairing_token())


def issue_access_token(db: Session, device_name: str, settings: Settings) -> str:
    raw_token = secrets.token_urlsafe(32)
    db.add(
        PairingToken(
            token_hash=token_hash(raw_token, settings.app_secret),
            device_name=device_name.strip(),
        )
    )
    db.commit()
    return raw_token


def require_access_token(
    credentials: HTTPAuthorizationCredentials | None = Depends(bearer_scheme),
    db: Session = Depends(get_db),
    settings: Settings = Depends(get_settings),
) -> PairingToken:
    if credentials is None or credentials.scheme.lower() != "bearer":
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Missing bearer token")
    stored = db.scalar(
        select(PairingToken).where(
            PairingToken.token_hash == token_hash(credentials.credentials, settings.app_secret),
            PairingToken.revoked_at.is_(None),
        )
    )
    if stored is None:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid bearer token")
    stored.last_used_at = datetime.now(timezone.utc)
    db.commit()
    return stored
