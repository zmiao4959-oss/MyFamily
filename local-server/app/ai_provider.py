from __future__ import annotations

from dataclasses import dataclass, field
from typing import Protocol

import httpx

from app.config import Settings


class AIProviderError(RuntimeError):
    pass


@dataclass
class ToolCall:
    id: str
    name: str
    arguments: str


@dataclass
class AICompletion:
    content: str | None
    tool_calls: list[ToolCall] = field(default_factory=list)


class AIProvider(Protocol):
    name: str

    def complete(
        self,
        messages: list[dict],
        model: str,
        *,
        json_mode: bool = False,
        tools: list[dict] | None = None,
    ) -> AICompletion: ...


class DeepSeekProvider:
    name = "deepseek"

    def __init__(self, settings: Settings, transport: httpx.BaseTransport | None = None):
        self.settings = settings
        self.transport = transport

    def complete(
        self,
        messages: list[dict],
        model: str,
        *,
        json_mode: bool = False,
        tools: list[dict] | None = None,
    ) -> AICompletion:
        if not self.settings.deepseek_api_key:
            raise AIProviderError("DeepSeek API key is not configured")
        body: dict = {"model": model, "messages": messages, "stream": False, "temperature": 0.2}
        if json_mode:
            body["response_format"] = {"type": "json_object"}
        if tools:
            body["tools"] = tools
            body["tool_choice"] = "auto"
        try:
            with httpx.Client(timeout=self.settings.ai_request_timeout_seconds, transport=self.transport) as client:
                response = client.post(
                    f"{self.settings.deepseek_base_url.rstrip('/')}/chat/completions",
                    headers={"Authorization": f"Bearer {self.settings.deepseek_api_key}", "Content-Type": "application/json"},
                    json=body,
                )
                response.raise_for_status()
                data = response.json()
            message = data["choices"][0]["message"]
            calls = [
                ToolCall(id=item["id"], name=item["function"]["name"], arguments=item["function"]["arguments"])
                for item in message.get("tool_calls", [])
            ]
            return AICompletion(content=message.get("content"), tool_calls=calls)
        except (httpx.HTTPError, KeyError, IndexError, TypeError, ValueError) as error:
            raise AIProviderError(f"DeepSeek request failed: {type(error).__name__}") from error
