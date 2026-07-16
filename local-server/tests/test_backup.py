import hashlib


def test_encrypted_backup_can_be_inspected_downloaded_and_restored(client, auth_headers):
    person = client.post("/persons", headers=auth_headers, json={"name": "备份测试人物"})
    assert person.status_code == 201
    password = "correct-horse-battery"

    created = client.post("/backup/create", headers=auth_headers, json={"password": password})
    assert created.status_code == 201
    backup = created.json()
    assert backup["encrypted"] is True
    assert backup["persons_count"] == 1

    listed = client.get("/backup/list", headers=auth_headers)
    assert listed.status_code == 200
    assert listed.json()[0]["id"] == backup["id"]
    wrong = client.post(f"/backup/{backup['id']}/inspect", headers=auth_headers, json={"password": "wrong-password-123"})
    assert wrong.status_code == 400
    inspected = client.post(f"/backup/{backup['id']}/inspect", headers=auth_headers, json={"password": password})
    assert inspected.status_code == 200
    assert inspected.json()["counts"]["persons"] == 1

    downloaded = client.get(f"/backup/{backup['id']}/download", headers=auth_headers)
    assert downloaded.status_code == 200
    assert hashlib.sha256(downloaded.content).hexdigest() == backup["sha256"]

    assert client.delete(f"/persons/{person.json()['id']}", headers=auth_headers).status_code == 204
    missing_confirmation = client.post(
        f"/backup/{backup['id']}/restore", headers=auth_headers,
        json={"password": password, "confirmation": "NO"},
    )
    assert missing_confirmation.status_code == 400
    restored = client.post(
        f"/backup/{backup['id']}/restore", headers=auth_headers,
        json={"password": password, "confirmation": "RESTORE"},
    )
    assert restored.status_code == 200
    assert restored.json()["restored"] is True
    people = client.get("/persons", headers=auth_headers).json()
    assert any(item["name"] == "备份测试人物" for item in people)


def test_backup_requires_authentication(client):
    assert client.get("/backup/list").status_code == 401
