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


def test_device_management_and_pairing_token_rotation(client, auth_headers):
    second = client.post("/pair", json={"pairing_token": "test-pairing-token", "device_name": "第二台手机"})
    assert second.status_code == 201
    devices = client.get("/pair/tokens", headers=auth_headers).json()
    other = next(item for item in devices if item["device_name"] == "第二台手机")
    assert client.delete(f"/pair/tokens/{other['id']}", headers=auth_headers).json() == {"revoked": True}
    second_headers = {"Authorization": f"Bearer {second.json()['access_token']}"}
    assert client.get("/auth/check", headers=second_headers).status_code == 401

    rotated = client.post("/pair/admin-token/regenerate", headers=auth_headers)
    assert rotated.status_code == 200
    new_token = rotated.json()["pairing_token"]
    assert "test-pairing-token" not in rotated.text
    assert client.post("/pair", json={"pairing_token": "test-pairing-token", "device_name": "旧令牌"}).status_code == 401
    assert client.post("/pair", json={"pairing_token": new_token, "device_name": "新令牌"}).status_code == 201
