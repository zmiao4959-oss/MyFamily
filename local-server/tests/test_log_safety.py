from app.log_safety import redact


def test_log_redaction_masks_common_secrets():
    value = redact('Authorization: Bearer abc123 api_key=secret password=hunter2 "access_token":"xyz"')
    assert "abc123" not in value
    assert "secret" not in value
    assert "hunter2" not in value
    assert "xyz" not in value
    assert value.count("[REDACTED]") == 4
