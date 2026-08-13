"""
Endpoint-level tests via FastAPI's TestClient. Every test that touches
the item bank uses the isolated_bank fixture (see conftest.py) so these
never read or write the real item_bank.json.

A few of these (anything that uploads a real photo) pay the ~7.5s/image
segmentation cost for real - that's inherent to the endpoint, not a test
inefficiency, so the count of such tests is kept deliberately small.
"""
import json

from conftest import make_test_jpeg_bytes, make_test_zip


# --- auth --------------------------------------------------------------

def test_missing_api_key_is_rejected(client, isolated_bank):
    resp = client.get("/bank")
    assert resp.status_code == 401


def test_wrong_api_key_is_rejected(client, isolated_bank):
    resp = client.get("/bank", headers={"X-API-Key": "not-the-real-key"})
    assert resp.status_code == 401


def test_correct_api_key_is_accepted(client, auth_headers, isolated_bank):
    resp = client.get("/bank", headers=auth_headers)
    assert resp.status_code == 200


# --- /bank/upload-zip ----------------------------------------------------

def test_upload_zip_rejects_non_zip_extension(client, auth_headers, isolated_bank):
    resp = client.post(
        "/bank/upload-zip",
        headers=auth_headers,
        files={"zip_file": ("photo.jpg", make_test_jpeg_bytes(), "image/jpeg")},
    )
    assert resp.status_code == 400


def test_upload_zip_rejects_corrupt_zip_bytes(client, auth_headers, isolated_bank):
    resp = client.post(
        "/bank/upload-zip",
        headers=auth_headers,
        files={"zip_file": ("bad.zip", b"not actually a zip", "application/zip")},
    )
    assert resp.status_code == 400


def test_upload_zip_with_no_category_subfolder_is_rejected(client, auth_headers, isolated_bank):
    # Image sits at the zip root, not inside e.g. sneakers/ - nothing
    # should be added, and this should read as an error, not a silent no-op.
    zip_bytes = make_test_zip({"loose_photo.jpg": make_test_jpeg_bytes()})
    resp = client.post(
        "/bank/upload-zip",
        headers=auth_headers,
        files={"zip_file": ("upload.zip", zip_bytes, "application/zip")},
    )
    assert resp.status_code == 400
    assert "category subfolder" in resp.json()["detail"]


def test_upload_zip_happy_path_adds_item_under_normalized_category(client, auth_headers, isolated_bank):
    # "sneakers" folder should land in the canonical "shoes" category.
    zip_bytes = make_test_zip({"sneakers/red_one.jpg": make_test_jpeg_bytes(color=(200, 20, 20))})
    resp = client.post(
        "/bank/upload-zip",
        headers=auth_headers,
        files={"zip_file": ("upload.zip", zip_bytes, "application/zip")},
    )
    assert resp.status_code == 200
    body = resp.json()
    assert body["added"] == {"shoes": 1}
    assert body["duplicates_skipped"] == 0
    assert body["total_bank_size"] == 1

    bank = client.get("/bank", headers=auth_headers).json()
    assert bank["count"] == 1
    assert bank["items"][0]["category"] == "shoes"
    assert bank["items"][0]["label"] is None  # zip uploads have no rating context


def test_upload_zip_skips_duplicate_photo_on_second_upload(client, auth_headers, isolated_bank):
    zip_bytes = make_test_zip({"shoes/one.jpg": make_test_jpeg_bytes(color=(50, 50, 200))})
    client.post("/bank/upload-zip", headers=auth_headers, files={"zip_file": ("a.zip", zip_bytes, "application/zip")})

    resp = client.post("/bank/upload-zip", headers=auth_headers, files={"zip_file": ("b.zip", zip_bytes, "application/zip")})
    body = resp.json()
    assert body["added"] == {}
    assert body["duplicates_skipped"] == 1
    assert body["total_bank_size"] == 1  # still just the one item, not two


# --- PATCH /bank/{item_id} -----------------------------------------------

def _seed_one_item(isolated_bank):
    import main
    items = [{
        "id": 1, "category": "pants", "description": "pants", "image_base64": "",
        "embedding": [0.0] * 512, "palette": [{"color": [0, 0, 0], "weight": 1.0}],
        "source": "zip_upload", "label": None, "added_at": "2026-01-01T00:00:00",
    }]
    main.save_item_bank(items)


def test_patch_bank_item_updates_label_and_description(client, auth_headers, isolated_bank):
    _seed_one_item(isolated_bank)
    resp = client.patch("/bank/1", headers=auth_headers, data={"label": "good", "description": "my favorite jeans"})
    assert resp.status_code == 200
    assert resp.json() == {"id": 1, "category": "pants", "description": "my favorite jeans", "label": "good"}

    bank = client.get("/bank", headers=auth_headers).json()
    assert bank["items"][0]["label"] == "good"
    assert bank["items"][0]["description"] == "my favorite jeans"


def test_patch_bank_item_unrated_clears_label(client, auth_headers, isolated_bank):
    _seed_one_item(isolated_bank)
    client.patch("/bank/1", headers=auth_headers, data={"label": "bad"})
    resp = client.patch("/bank/1", headers=auth_headers, data={"label": "unrated"})
    assert resp.json()["label"] is None


def test_patch_bank_item_rejects_invalid_label(client, auth_headers, isolated_bank):
    _seed_one_item(isolated_bank)
    resp = client.patch("/bank/1", headers=auth_headers, data={"label": "maybe"})
    assert resp.status_code == 400


def test_patch_bank_item_404_for_unknown_id(client, auth_headers, isolated_bank):
    _seed_one_item(isolated_bank)
    resp = client.patch("/bank/999", headers=auth_headers, data={"label": "good"})
    assert resp.status_code == 404


# --- /generate-outfit-from-top --------------------------------------------

def test_generate_outfit_malformed_image_item_returns_400_not_500(client, auth_headers, isolated_bank):
    # Regression test: this endpoint used to catch HTTPException inside
    # its own try/except and re-raise everything as a 500, flattening
    # real 400 client errors (bad input) into misleading 500s.
    inventory = {"pants": [{"id": -1, "description": "broken", "image_base64": "not-valid-base64!!"}]}
    resp = client.post(
        "/generate-outfit-from-top",
        headers=auth_headers,
        data={"inventory_json": json.dumps(inventory)},
        files={"file": ("top.jpg", make_test_jpeg_bytes(), "image/jpeg")},
    )
    assert resp.status_code == 400


def test_generate_outfit_with_empty_inventory_returns_nulls(client, auth_headers, isolated_bank):
    resp = client.post(
        "/generate-outfit-from-top",
        headers=auth_headers,
        data={"inventory_json": json.dumps({})},
        files={"file": ("top.jpg", make_test_jpeg_bytes(), "image/jpeg")},
    )
    assert resp.status_code == 200
    rec = resp.json()["recommended_outfit"]
    assert rec["best_pants_match"] is None
    assert rec["best_shoes_match"] is None
