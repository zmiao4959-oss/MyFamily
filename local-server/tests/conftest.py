from collections.abc import Generator

import pytest
from fastapi.testclient import TestClient
from sqlalchemy import create_engine
from sqlalchemy.orm import Session, sessionmaker
from sqlalchemy.pool import StaticPool

from app.config import Settings, get_settings
from app.database import Base, get_db
from app.main import create_app


@pytest.fixture
def session_factory():
    engine = create_engine(
        "sqlite://",
        connect_args={"check_same_thread": False},
        poolclass=StaticPool,
    )
    factory = sessionmaker(bind=engine, expire_on_commit=False)
    Base.metadata.create_all(engine)
    return factory


@pytest.fixture
def test_settings(tmp_path):
    return Settings(
        app_secret="test-secret-with-enough-length",
        pairing_token="test-pairing-token",
        database_url="sqlite://",
        media_root=tmp_path / "media",
        backup_root=tmp_path / "backups",
        max_upload_size_mb=2,
        feature_ai=False,
        deepseek_api_key="",
    )


@pytest.fixture
def client(session_factory, test_settings) -> Generator[TestClient, None, None]:

    def override_db() -> Generator[Session, None, None]:
        with session_factory() as session:
            yield session

    app = create_app()
    app.dependency_overrides[get_db] = override_db
    app.dependency_overrides[get_settings] = lambda: test_settings
    with TestClient(app) as test_client:
        yield test_client


@pytest.fixture
def auth_headers(client):
    response = client.post(
        "/pair",
        json={"pairing_token": "test-pairing-token", "device_name": "pytest"},
    )
    return {"Authorization": f"Bearer {response.json()['access_token']}"}
