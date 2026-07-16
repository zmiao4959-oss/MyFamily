from __future__ import annotations

import logging
from contextlib import asynccontextmanager
from datetime import datetime, timezone

from fastapi import Depends, FastAPI, HTTPException, status
from sqlalchemy import text
from sqlalchemy.orm import Session

from app.config import Settings, get_settings
from app.database import get_db
from app.schemas import HealthResponse, PairRequest, PairResponse, RevokeResponse
from app.security import issue_access_token, require_access_token, verify_pairing_token
from app.models import PairingToken
from app.core import router as core_router
from app.demo import router as demo_router
from app.media import router as media_router
from app.ai import router as ai_router
from app.search import router as search_router

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s %(message)s")
logger = logging.getLogger("family-memory")


def create_app() -> FastAPI:
    @asynccontextmanager
    async def lifespan(_: FastAPI):
        settings = get_settings()
        logger.info("Family Memory local server %s starting", settings.app_version)
        logger.info("Administrator pairing token: %s", settings.effective_pairing_token())
        yield

    application = FastAPI(
        title="家忆本地服务",
        version=get_settings().app_version,
        lifespan=lifespan,
    )
    application.include_router(core_router)
    application.include_router(demo_router)
    application.include_router(media_router)
    application.include_router(ai_router)
    application.include_router(search_router)

    @application.get("/health", response_model=HealthResponse)
    def health(
        db: Session = Depends(get_db),
        settings: Settings = Depends(get_settings),
    ) -> HealthResponse:
        try:
            db.execute(text("SELECT 1"))
        except Exception as error:
            logger.warning("Database health check failed: %s", type(error).__name__)
            raise HTTPException(status_code=status.HTTP_503_SERVICE_UNAVAILABLE, detail="Database unavailable") from error
        return HealthResponse(status="ok", version=settings.app_version, database="ok")

    @application.get("/version")
    def version(settings: Settings = Depends(get_settings)) -> dict[str, str]:
        return {"version": settings.app_version}

    @application.post("/pair", response_model=PairResponse, status_code=status.HTTP_201_CREATED)
    def pair(
        request: PairRequest,
        db: Session = Depends(get_db),
        settings: Settings = Depends(get_settings),
    ) -> PairResponse:
        if not verify_pairing_token(request.pairing_token, settings):
            raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid pairing token")
        access_token = issue_access_token(db, request.device_name, settings)
        return PairResponse(access_token=access_token, server_version=settings.app_version)

    @application.post("/pair/revoke", response_model=RevokeResponse)
    def revoke(
        token: PairingToken = Depends(require_access_token),
        db: Session = Depends(get_db),
    ) -> RevokeResponse:
        token.revoked_at = datetime.now(timezone.utc)
        db.commit()
        return RevokeResponse(revoked=True)

    @application.get("/auth/check")
    def auth_check(_: PairingToken = Depends(require_access_token)) -> dict[str, bool]:
        return {"authenticated": True}

    return application


app = create_app()
