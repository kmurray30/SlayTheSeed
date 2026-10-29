import builtins
import json
import logging
from pathlib import Path

import pytest
from workbench import bugreport_store as store


def _block_gcs_import(monkeypatch):
    """Make ``from google.cloud import storage`` raise, as in an image that
    forgot the dependency."""
    real_import = builtins.__import__

    def fake_import(name, globals=None, locals=None, fromlist=(), level=0):
        if name.startswith("google.cloud"):
            raise ImportError("No module named 'google.cloud.storage'")
        return real_import(name, globals, locals, fromlist, level)

    monkeypatch.setattr(builtins, "__import__", fake_import)


class _FakeBlob:
    """Records uploads / serves a canned body, or raises on demand."""

    def __init__(self, store_dict, key, *, fail=False, body=None):
        self._store = store_dict
        self._key = key
        self._fail = fail
        self._body = body

    def upload_from_string(self, body, content_type=None):
        if self._fail:
            raise RuntimeError("403 permission denied")
        self._store[self._key] = (body, content_type)

    def download_as_text(self):
        if self._fail:
            raise RuntimeError("404 not found")
        return self._body

    @property
    def public_url(self):
        return f"https://storage.googleapis.com/bkt/{self._key}"


def _fake_gcs(monkeypatch, *, fail=False, body=None, client_error=None):
    """Point ``storage.Client`` at an in-memory double; return its object dict."""
    objects: dict[str, object] = {}

    class _FakeBucket:
        def blob(self, key):
            return _FakeBlob(objects, key, fail=fail, body=body)

    class _FakeClient:
        def bucket(self, name):
            objects["_bucket_name"] = name
            return _FakeBucket()

    def _client(*a, **k):
        if client_error is not None:
            raise client_error
        return _FakeClient()

    import google.cloud.storage as gcs

    monkeypatch.setattr(gcs, "Client", _client)
    return objects


def test_new_id_shape():
    rid = store.new_id()
    assert rid.startswith("wb-")
    assert len(rid) == 19  # "wb-" + 16 hex chars
    assert rid[3:].isalnum() and rid[3:].islower()


def test_put_get_local_fallback(tmp_path: Path):
    logs = tmp_path / "logs"
    logs.mkdir()
    rid = "wb-0123456789abcdef"
    url = store.put(rid, {"hello": "world"}, logs_dir=logs, bucket=None)
    # No bucket -> local file, file:// URI pointing at the saved dump.
    assert url.startswith("file://")
    saved = logs / "bugreports" / f"{rid}.json"
    assert saved.is_file()
    got = store.get(rid, logs_dir=logs, bucket=None)
    assert got == {"hello": "world"}


def test_get_unknown_returns_none(tmp_path: Path):
    logs = tmp_path / "logs"
    logs.mkdir()
    assert store.get("wb-deadbeefdeadbeef", logs_dir=logs, bucket=None) is None


def test_get_rejects_malformed_id(tmp_path):
    logs = tmp_path / "logs"
    logs.mkdir()
    with pytest.raises(ValueError, match="invalid report id"):
        store.get("wb-../../etc/passwd", logs_dir=logs, bucket=None)


def test_put_rejects_malformed_id(tmp_path):
    logs = tmp_path / "logs"
    logs.mkdir()
    with pytest.raises(ValueError, match="invalid report id"):
        store.put("../evil", {"x": 1}, logs_dir=logs, bucket=None)


def test_new_id_passes_check():
    # a generated id must satisfy the guard
    store._check_id(store.new_id())  # no raise


def test_put_strips_gs_prefix(tmp_path, monkeypatch):
    captured = {}

    class _FakeBlob:
        def upload_from_string(self, body, content_type=None):
            captured["uploaded"] = True

        @property
        def public_url(self):
            return "https://storage.googleapis.com/mybucket/bugreports/x.json"

    class _FakeBucket:
        def blob(self, key):
            captured["key"] = key
            return _FakeBlob()

    class _FakeClient:
        def bucket(self, name):
            captured["bucket_name"] = name
            return _FakeBucket()

    import google.cloud.storage as gcs

    monkeypatch.setattr(gcs, "Client", lambda *a, **k: _FakeClient())

    rid = "wb-0123456789abcdef"
    url = store.put(rid, {"x": 1}, logs_dir=tmp_path, bucket="gs://mybucket")
    assert captured["bucket_name"] == "mybucket"  # gs:// stripped
    assert captured["key"] == "bugreports/wb-0123456789abcdef.json"
    assert url.startswith("https://storage.googleapis.com/")


# -- check_config ---------------------------------------------------------


def test_check_config_unconfigured_is_not_an_error():
    assert store.check_config(None) is None
    assert store.check_config("") is None


def test_check_config_flags_missing_dependency(monkeypatch):
    _block_gcs_import(monkeypatch)
    msg = store.check_config("gs://bkt")
    assert msg is not None
    assert "google-cloud-storage is not installed" in msg
    assert "gs://bkt" in msg


def test_check_config_flags_unresolvable_credentials(monkeypatch):
    _fake_gcs(monkeypatch, client_error=RuntimeError("could not determine default project"))
    msg = store.check_config("gs://bkt")
    assert msg is not None
    assert "credentials do not resolve" in msg
    assert "could not determine default project" in msg


def test_check_config_ok_when_client_builds(monkeypatch):
    _fake_gcs(monkeypatch)
    assert store.check_config("gs://bkt") is None


def test_check_config_does_not_probe_the_bucket(monkeypatch):
    """It must not call ``Bucket.exists()`` — that needs a permission the
    least-privilege grant deliberately withholds."""
    objects = _fake_gcs(monkeypatch)
    assert store.check_config("gs://bkt") is None
    assert "_bucket_name" not in objects  # no bucket() call at all


# -- put: GCS success + fallback -----------------------------------------


def test_put_uploads_pretty_sorted_json(monkeypatch, tmp_path):
    objects = _fake_gcs(monkeypatch)
    rid = "wb-0123456789abcdef"
    store.put(rid, {"b": 2, "a": 1}, logs_dir=tmp_path, bucket="bkt")
    body, content_type = objects[f"bugreports/{rid}.json"]
    assert content_type == "application/json"
    assert json.loads(body) == {"a": 1, "b": 2}
    assert body.splitlines()[1].startswith('  "a"')  # indent=2, sort_keys=True
    assert not (tmp_path / "bugreports").exists()  # no local copy on success


def test_put_falls_back_to_local_and_logs_error(monkeypatch, tmp_path, caplog):
    _fake_gcs(monkeypatch, fail=True)
    rid = "wb-0123456789abcdef"
    with caplog.at_level(logging.ERROR, logger="workbench.bugreport_store"):
        url = store.put(rid, {"x": 1}, logs_dir=tmp_path, bucket="gs://bkt")
    assert url.startswith("file://")
    saved = tmp_path / "bugreports" / f"{rid}.json"
    assert json.loads(saved.read_text()) == {"x": 1}
    # Must be findable at ERROR: a configured bucket means local is NOT intended.
    assert [r for r in caplog.records if r.levelno == logging.ERROR]
    assert "FAILED" in caplog.text


def test_put_falls_back_when_the_sdk_is_absent(monkeypatch, tmp_path):
    _block_gcs_import(monkeypatch)
    rid = "wb-0123456789abcdef"
    url = store.put(rid, {"x": 1}, logs_dir=tmp_path, bucket="gs://bkt")
    assert url.startswith("file://")
    assert (tmp_path / "bugreports" / f"{rid}.json").is_file()


def test_put_creates_missing_parent_dirs(tmp_path):
    logs = tmp_path / "does" / "not" / "exist"
    rid = "wb-0123456789abcdef"
    store.put(rid, {"x": 1}, logs_dir=logs, bucket=None)
    assert (logs / "bugreports" / f"{rid}.json").is_file()


# -- get: GCS first, then local ------------------------------------------


def test_get_reads_from_gcs_when_configured(monkeypatch, tmp_path):
    _fake_gcs(monkeypatch, body=json.dumps({"from": "gcs"}))
    rid = "wb-0123456789abcdef"
    # A stale local copy must lose to the bucket.
    store.put(rid, {"from": "local"}, logs_dir=tmp_path, bucket=None)
    assert store.get(rid, logs_dir=tmp_path, bucket="gs://bkt") == {"from": "gcs"}


def test_get_strips_gs_prefix_and_prefixes_the_key(monkeypatch, tmp_path):
    objects = _fake_gcs(monkeypatch, body="{}")
    rid = "wb-0123456789abcdef"
    store.get(rid, logs_dir=tmp_path, bucket="gs://mybucket")
    assert objects["_bucket_name"] == "mybucket"


def test_get_falls_back_to_local_on_gcs_miss(monkeypatch, tmp_path, caplog):
    rid = "wb-0123456789abcdef"
    store.put(rid, {"from": "local"}, logs_dir=tmp_path, bucket=None)
    _fake_gcs(monkeypatch, fail=True)
    with caplog.at_level(logging.INFO, logger="workbench.bugreport_store"):
        got = store.get(rid, logs_dir=tmp_path, bucket="gs://bkt")
    assert got == {"from": "local"}
    assert "GCS fetch miss" in caplog.text


def test_get_returns_none_when_neither_backend_has_it(monkeypatch, tmp_path):
    _fake_gcs(monkeypatch, fail=True)
    assert store.get("wb-deadbeefdeadbeef", logs_dir=tmp_path, bucket="gs://bkt") is None


def test_get_falls_back_when_the_sdk_is_absent(monkeypatch, tmp_path):
    rid = "wb-0123456789abcdef"
    store.put(rid, {"from": "local"}, logs_dir=tmp_path, bucket=None)
    _block_gcs_import(monkeypatch)
    assert store.get(rid, logs_dir=tmp_path, bucket="gs://bkt") == {"from": "local"}


def test_put_get_roundtrip_through_gcs(monkeypatch, tmp_path):
    """What ``put`` uploads is exactly what ``get`` can parse back."""
    objects = _fake_gcs(monkeypatch)
    rid = store.new_id()
    store.put(rid, {"nested": {"a": [1, 2]}, "z": None}, logs_dir=tmp_path, bucket="bkt")
    body, _ = objects[f"bugreports/{rid}.json"]
    _fake_gcs(monkeypatch, body=body)  # same bucket, now serving what we wrote
    assert store.get(rid, logs_dir=tmp_path, bucket="bkt") == {"nested": {"a": [1, 2]}, "z": None}
