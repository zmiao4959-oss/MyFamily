FROM python:3.12-slim

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PIP_NO_CACHE_DIR=1

WORKDIR /app

COPY requirements.txt ./
RUN pip install --no-cache-dir -r requirements.txt

COPY . .

RUN addgroup --system familymemory \
    && adduser --system --ingroup familymemory familymemory \
    && mkdir -p /data/media/original /data/media/thumbnails /data/media/processed /data/backups \
    && chown -R familymemory:familymemory /app /data

USER familymemory

EXPOSE 8080
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8080"]
