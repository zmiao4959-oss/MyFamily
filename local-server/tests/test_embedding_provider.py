import httpx

from app.config import Settings
from app.embedding_provider import VolcanoEmbeddingProvider


def test_volcano_provider_uses_multimodal_api_and_parses_vector(tmp_path):
    captured = {}

    def handler(request: httpx.Request):
        captured["path"] = request.url.path
        captured["authorization"] = request.headers["authorization"]
        captured["body"] = request.read().decode()
        return httpx.Response(200, json={"data": [{"embedding": [[0.25, -0.5, 1.0]]}]})

    settings = Settings(
        app_secret="test-secret-with-enough-length", database_url="sqlite://",
        volcano_ark_api_key="secret-test-key", volcano_embedding_endpoint_id="ep-test",
        media_root=tmp_path / "media", backup_root=tmp_path / "backups",
    )
    provider = VolcanoEmbeddingProvider(settings, transport=httpx.MockTransport(handler))
    vector = provider.embed_text("家庭春节记录")

    assert vector == [0.25, -0.5, 1.0]
    assert captured["path"] == "/api/v3/embeddings/multimodal"
    assert captured["authorization"] == "Bearer secret-test-key"
    assert "secret-test-key" not in captured["body"]
    assert '"model":"ep-test"' in captured["body"]
