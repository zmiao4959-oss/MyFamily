def test_person_relationship_record_and_soft_delete(client, auth_headers):
    father = client.post("/persons", headers=auth_headers, json={"name": "周父", "birth_year": 1960}).json()
    child = client.post("/persons", headers=auth_headers, json={"name": "周子", "birth_year": 1990, "is_self": True}).json()
    assert father["sync_version"] == 1

    relation = client.post(
        "/relationships",
        headers=auth_headers,
        json={"person_a_id": father["id"], "person_b_id": child["id"], "relation_type": "father"},
    )
    assert relation.status_code == 201

    tag = client.post("/tags", headers=auth_headers, json={"name": "春节", "color": "#884433"}).json()
    record = client.post(
        "/records",
        headers=auth_headers,
        json={
            "title": "一次团圆",
            "original_text": "原始文字必须保留",
            "edited_text": "整理后的文字",
            "person_ids": [father["id"], child["id"]],
            "tag_ids": [tag["id"]],
        },
    )
    assert record.status_code == 201
    assert record.json()["original_text"] == "原始文字必须保留"
    assert record.json()["tag_ids"] == [tag["id"]]

    updated = client.patch(f"/persons/{child['id']}", headers=auth_headers, json={"nickname": "小周"})
    assert updated.json()["nickname"] == "小周"
    assert updated.json()["sync_version"] == 2

    assert len(client.get("/persons", headers=auth_headers).json()) == 2
    assert len(client.get(f"/relationships?person_id={child['id']}", headers=auth_headers).json()) == 1
    assert len(client.get(f"/records?person_id={child['id']}", headers=auth_headers).json()) == 1

    assert client.delete(f"/records/{record.json()['id']}", headers=auth_headers).status_code == 204
    assert client.get("/records", headers=auth_headers).json() == []


def test_relationship_rejects_same_person(client, auth_headers):
    person = client.post("/persons", headers=auth_headers, json={"name": "独立人物"}).json()
    response = client.post(
        "/relationships",
        headers=auth_headers,
        json={"person_a_id": person["id"], "person_b_id": person["id"], "relation_type": "other"},
    )
    assert response.status_code == 422


def test_sync_push_is_idempotent_and_pull_reports_changes(client, auth_headers):
    client_uuid = "11111111-1111-1111-1111-111111111111"
    payload = {
        "changes": [{
            "entity_type": "person",
            "operation": "upsert",
            "client_uuid": client_uuid,
            "payload": {"name": "离线创建的人物"},
        }]
    }
    first = client.post("/sync/push", headers=auth_headers, json=payload).json()
    second = client.post("/sync/push", headers=auth_headers, json=payload).json()
    assert first["results"][0]["entity_id"] == second["results"][0]["entity_id"]
    assert len(client.get("/persons", headers=auth_headers).json()) == 1

    pulled = client.get("/sync/changes?after=0", headers=auth_headers).json()
    assert pulled["latest_version"] >= 2
    assert all(change["entity_type"] == "person" for change in pulled["changes"])


def test_demo_family_can_be_loaded_once_and_cleared(client, auth_headers):
    loaded = client.post("/demo/load", headers=auth_headers)
    assert loaded.status_code == 200
    assert loaded.json()["persons"] == 9
    assert loaded.json()["relationships"] == 12
    assert len(client.get("/records", headers=auth_headers).json()) == 5

    again = client.post("/demo/load", headers=auth_headers).json()
    assert again["already_loaded"] is True
    assert len(client.get("/persons", headers=auth_headers).json()) == 9

    assert client.delete("/demo", headers=auth_headers).json() == {"cleared": True}
    assert client.get("/persons", headers=auth_headers).json() == []
