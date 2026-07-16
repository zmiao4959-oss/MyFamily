import json

import httpx
import pytest

from app.ai_provider import AIProviderError, DeepSeekProvider
from app.config import Settings


def test_deepseek_provider_uses_json_mode_and_parses_tool_calls():
    captured = {}

    def handler(request: httpx.Request):
        captured["url"] = str(request.url)
        captured["authorization"] = request.headers["Authorization"]
        captured["body"] = json.loads(request.content)
        return httpx.Response(200, json={
            "choices": [{"message": {"content": None, "tool_calls": [{
                "id": "call-1", "type": "function", "function": {"name": "search_records", "arguments": "{\"query\":\"春节\"}"},
            }]}}],
        })

    settings = Settings(app_secret="test-secret-with-enough-length", deepseek_api_key="secret-key", deepseek_base_url="https://api.deepseek.com")
    provider = DeepSeekProvider(settings, transport=httpx.MockTransport(handler))
    result = provider.complete(
        [{"role": "user", "content": "返回 JSON"}], settings.deepseek_fast_model,
        json_mode=True, tools=[{"type": "function", "function": {"name": "search_records"}}],
    )

    assert captured["url"] == "https://api.deepseek.com/chat/completions"
    assert captured["authorization"] == "Bearer secret-key"
    assert captured["body"]["response_format"] == {"type": "json_object"}
    assert result.tool_calls[0].name == "search_records"


def test_deepseek_provider_requires_server_side_key():
    settings = Settings(app_secret="test-secret-with-enough-length", deepseek_api_key="")
    with pytest.raises(AIProviderError, match="not configured"):
        DeepSeekProvider(settings).complete([{"role": "user", "content": "hi"}], settings.deepseek_fast_model)
