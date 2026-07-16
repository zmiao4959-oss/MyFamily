def test_pairing_issues_token_and_token_can_be_revoked(client):
    pair = client.post(
        "/pair",
        json={"pairing_token": "test-pairing-token", "device_name": "测试手机"},
    )
    assert pair.status_code == 201
    access_token = pair.json()["access_token"]
    assert access_token != "test-pairing-token"

    headers = {"Authorization": f"Bearer {access_token}"}
    assert client.get("/auth/check", headers=headers).json() == {"authenticated": True}
    assert client.post("/pair/revoke", headers=headers).json() == {"revoked": True}
    assert client.get("/auth/check", headers=headers).status_code == 401


def test_invalid_pairing_token_is_rejected(client):
    response = client.post(
        "/pair",
        json={"pairing_token": "wrong-token", "device_name": "测试手机"},
    )
    assert response.status_code == 401
