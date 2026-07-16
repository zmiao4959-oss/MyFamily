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
def client(tmp_path) -> Generator[TestClient, None, None]:
    engine = create_engine(
        "sqlite://",
        connect_args={"check_same_thread": False},
        poolclass=StaticPool,
    )
    testing_session = sessionmaker(bind=engine, expire_on_commit=False)
    Base.metadata.create_all(engine)
    settings = Settings(
        app_secret="test-secret-with-enough-length",
        pairing_token="test-pairing-token",
        database_url="sqlite://",
        media_root=tmp_path / "media",
        backup_root=tmp_path / "backups",
        max_upload_size_mb=2,
    )

    def override_db() -> Generator[Session, None, None]:
        with testing_session() as session:
            yield session

    app = create_app()
    app.dependency_overrides[get_db] = override_db
    app.dependency_overrides[get_settings] = lambda: settings
    with TestClient(app) as test_client:
        yield test_client


@pytest.fixture
def auth_headers(client):
    response = client.post(
        "/pair",
        json={"pairing_token": "test-pairing-token", "device_name": "pytest"},
    )
    return {"Authorization": f"Bearer {response.json()['access_token']}"}
