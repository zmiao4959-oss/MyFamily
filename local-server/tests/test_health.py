def test_health_reports_database_and_version(client):
    response = client.get("/health")

    assert response.status_code == 200
    assert response.json() == {"status": "ok", "version": "0.5.0", "database": "ok"}


def test_version_is_available_without_authentication(client):
    response = client.get("/version")

    assert response.status_code == 200
    assert response.json() == {"version": "0.5.0"}
