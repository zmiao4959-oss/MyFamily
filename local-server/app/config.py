from __future__ import annotations

import logging
import secrets
from functools import lru_cache

from pydantic import Field
from pydantic_settings import BaseSettings, SettingsConfigDict

logger = logging.getLogger(__name__)


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file="../.env", extra="ignore")

    app_env: str = "development"
    app_host: str = "0.0.0.0"
    app_port: int = 8080
    app_secret: str = Field(default="development-only-change-me", min_length=16)
    pairing_token: str = ""
    database_url: str = "postgresql+psycopg://family_memory:family_memory@localhost:5432/family_memory"
    app_version: str = "0.1.0"

    def effective_pairing_token(self) -> str:
        if not self.pairing_token:
            self.pairing_token = secrets.token_urlsafe(18)
        return self.pairing_token


@lru_cache
def get_settings() -> Settings:
    return Settings()
