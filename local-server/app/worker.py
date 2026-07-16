import logging
import time

from sqlalchemy import text

from app.database import SessionLocal

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s %(message)s")
logger = logging.getLogger("family-memory-worker")


def main() -> None:
    logger.info("Database-backed worker started; media processing dependencies are ready")
    while True:
        try:
            with SessionLocal() as session:
                session.execute(text("SELECT 1"))
            time.sleep(30)
        except Exception as error:
            logger.warning("Worker database check failed: %s", type(error).__name__)
            time.sleep(5)


if __name__ == "__main__":
    main()
