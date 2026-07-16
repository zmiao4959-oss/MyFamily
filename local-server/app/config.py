from __future__ import annotations

import logging
import secrets
from functools import lru_cache
from pathlib import Path

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
    app_version: str = "0.8.0"
    media_root: Path = Path("/data/media")
    backup_root: Path = Path("/data/backups")
    max_upload_size_mb: int = Field(default=500, ge=1, le=4096)
    deepseek_api_key: str = ""
    deepseek_base_url: str = "https://api.deepseek.com"
    deepseek_fast_model: str = "deepseek-v4-flash"
    deepseek_main_model: str = "deepseek-v4-pro"
    feature_ai: bool = False
    ai_request_timeout_seconds: int = Field(default=60, ge=10, le=300)
    volcano_ark_api_key: str = ""
    volcano_ark_base_url: str = "https://ark.cn-beijing.volces.com"
    volcano_embedding_model: str = "doubao-embedding-vision-251215"
    volcano_embedding_endpoint_id: str = ""
    feature_multimodal_search: bool = False
    embedding_request_timeout_seconds: int = Field(default=60, ge=10, le=300)

    def effective_pairing_token(self) -> str:
        if not self.pairing_token:
            self.pairing_token = secrets.token_urlsafe(18)
        return self.pairing_token


@lru_cache
def get_settings() -> Settings:
    return Settings()
