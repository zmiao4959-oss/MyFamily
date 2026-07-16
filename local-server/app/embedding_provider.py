from __future__ import annotations

import base64
from pathlib import Path

import httpx

from app.config import Settings


class EmbeddingProviderError(RuntimeError):
    pass


class VolcanoEmbeddingProvider:
    name = "volcano_ark"

    def __init__(self, settings: Settings, transport: httpx.BaseTransport | None = None):
        self.settings = settings
        self.transport = transport

    @property
    def target(self) -> str:
        return self.settings.volcano_embedding_endpoint_id or self.settings.volcano_embedding_model

    def embed_text(self, text: str) -> list[float]:
        return self._request([{"type": "text", "text": text[:12000]}])

    def embed_image(self, path: Path, mime_type: str, caption: str = "") -> list[float]:
        encoded = base64.b64encode(path.read_bytes()).decode("ascii")
        items: list[dict] = []
        if caption.strip():
            items.append({"type": "text", "text": caption.strip()[:2000]})
        items.append({"type": "image_url", "image_url": {"url": f"data:{mime_type};base64,{encoded}"}})
        return self._request(items)

    def _request(self, input_items: list[dict]) -> list[float]:
        if not self.settings.volcano_ark_api_key or not self.target:
            raise EmbeddingProviderError("Embedding provider is not configured")
        url = f"{self.settings.volcano_ark_base_url.rstrip('/')}/api/v3/embeddings/multimodal"
        try:
            with httpx.Client(timeout=self.settings.embedding_request_timeout_seconds, transport=self.transport) as client:
                response = client.post(
                    url,
                    headers={"Authorization": f"Bearer {self.settings.volcano_ark_api_key}"},
                    json={"model": self.target, "encoding_format": "float", "input": input_items},
                )
                response.raise_for_status()
                payload = response.json()
            raw = payload.get("data")
            if isinstance(raw, list):
                raw = raw[0] if raw else None
            if isinstance(raw, dict):
                raw = raw.get("embedding")
            while isinstance(raw, list) and len(raw) == 1 and isinstance(raw[0], list):
                raw = raw[0]
            if not isinstance(raw, list) or not raw or not all(isinstance(value, (int, float)) for value in raw):
                raise EmbeddingProviderError("Embedding response has no numeric vector")
            return [float(value) for value in raw]
        except EmbeddingProviderError:
            raise
        except (httpx.HTTPError, ValueError, KeyError) as error:
            raise EmbeddingProviderError("Embedding provider request failed") from error
