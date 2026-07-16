import hashlib
from io import BytesIO

from PIL import Image


def png_bytes(color=(120, 80, 40)):
    output = BytesIO()
    Image.new("RGB", (80, 60), color).save(output, format="PNG")
    return output.getvalue()


def create_record(client, headers, title="媒体记录"):
    return client.post("/records", headers=headers, json={"title": title}).json()


def upload(client, headers, record_id, content, client_uuid="media-client-1", filename="photo.png"):
    initialized = client.post(
        "/media/init-upload",
        headers=headers,
        json={
            "record_id": record_id,
            "client_uuid": client_uuid,
            "original_filename": filename,
            "mime_type": "image/png",
            "size_bytes": len(content),
            "sha256": hashlib.sha256(content).hexdigest(),
        },
    )
    assert initialized.status_code == 201
    upload_id = initialized.json()["upload_id"]
    chunk = client.put(f"/media/{upload_id}/chunk?offset=0", headers=headers, content=content)
    assert chunk.status_code == 200
    return client.post(f"/media/{upload_id}/complete", headers=headers)


def test_image_upload_thumbnail_download_and_auth(client, auth_headers):
    record = create_record(client, auth_headers)
    content = png_bytes()
    completed = upload(client, auth_headers, record["id"], content)
    assert completed.status_code == 201
    asset = completed.json()
    assert asset["media_type"] == "image"
    assert asset["sha256"] == hashlib.sha256(content).hexdigest()
    assert asset["width"] == 80
    assert asset["height"] == 60
    assert asset["has_thumbnail"] is True

    assert client.get(f"/media/{asset['id']}").status_code == 401
    downloaded = client.get(f"/media/{asset['id']}", headers=auth_headers)
    assert downloaded.status_code == 200
    assert downloaded.content == content
    thumbnail = client.get(f"/media/{asset['id']}/thumbnail", headers=auth_headers)
    assert thumbnail.status_code == 200
    assert thumbnail.headers["content-type"].startswith("image/jpeg")


def test_upload_rejects_path_traversal_mime_spoof_and_bad_offset(client, auth_headers):
    record = create_record(client, auth_headers)
    bad_name = client.post(
        "/media/init-upload",
        headers=auth_headers,
        json={"record_id": record["id"], "client_uuid": "bad-name", "original_filename": "../secret.png", "mime_type": "image/png", "size_bytes": 4},
    )
    assert bad_name.status_code == 400

    initialized = client.post(
        "/media/init-upload",
        headers=auth_headers,
        json={"record_id": record["id"], "client_uuid": "bad-mime", "original_filename": "fake.png", "mime_type": "image/png", "size_bytes": 4},
    ).json()
    assert client.put(f"/media/{initialized['upload_id']}/chunk?offset=2", headers=auth_headers, content=b"nope").status_code == 409
    assert client.put(f"/media/{initialized['upload_id']}/chunk?offset=0", headers=auth_headers, content=b"nope").status_code == 200
    assert client.post(f"/media/{initialized['upload_id']}/complete", headers=auth_headers).status_code == 415


def test_duplicate_content_reuses_file_but_keeps_record_attachment(client, auth_headers):
    first_record = create_record(client, auth_headers, "第一条")
    second_record = create_record(client, auth_headers, "第二条")
    content = png_bytes((20, 100, 80))
    first = upload(client, auth_headers, first_record["id"], content, "duplicate-1").json()
    second = upload(client, auth_headers, second_record["id"], content, "duplicate-2").json()
    assert first["id"] != second["id"]
    assert second["duplicate_of_id"] == first["id"]
    assert len(client.get("/media", headers=auth_headers).json()) == 2


def test_declared_file_size_limit(client, auth_headers):
    record = create_record(client, auth_headers)
    response = client.post(
        "/media/init-upload",
        headers=auth_headers,
        json={"record_id": record["id"], "client_uuid": "too-large", "original_filename": "large.mp4", "mime_type": "video/mp4", "size_bytes": 3 * 1024 * 1024},
    )
    assert response.status_code == 413
