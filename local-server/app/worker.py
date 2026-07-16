import logging
import time

from sqlalchemy import text

from app.database import SessionLocal
from app.ai_provider import DeepSeekProvider
from app.ai_service import process_next_job
from app.config import get_settings
from app.embedding_provider import VolcanoEmbeddingProvider
from app.embedding_service import process_next_embedding_job

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s %(message)s")
logger = logging.getLogger("family-memory-worker")


def main() -> None:
    settings = get_settings()
    provider = DeepSeekProvider(settings)
    embedding_provider = VolcanoEmbeddingProvider(settings)
    logger.info(
        "Database-backed worker started; AI enabled=%s configured=%s; multimodal search enabled=%s configured=%s",
        settings.feature_ai, bool(settings.deepseek_api_key), settings.feature_multimodal_search, bool(settings.volcano_ark_api_key),
    )
    while True:
        try:
            with SessionLocal() as session:
                session.execute(text("SELECT 1"))
                ai_job = process_next_job(session, provider) if settings.feature_ai and settings.deepseek_api_key else None
                embedding_job = process_next_embedding_job(session, embedding_provider, settings) if settings.feature_multimodal_search and settings.volcano_ark_api_key else None
                job = ai_job or embedding_job
            time.sleep(1 if job else 5)
        except Exception as error:
            logger.warning("Worker database check failed: %s", type(error).__name__)
            time.sleep(5)


if __name__ == "__main__":
    main()
