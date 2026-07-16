"""Add pgvector embeddings for multimodal search.

Revision ID: 0005
Revises: 0004
"""
from alembic import op
import sqlalchemy as sa
from pgvector.sqlalchemy import Vector

revision = "0005"
down_revision = "0004"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.execute("CREATE EXTENSION IF NOT EXISTS vector")
    op.create_table(
        "embeddings",
        sa.Column("id", sa.String(36), primary_key=True),
        sa.Column("owner_id", sa.String(36), sa.ForeignKey("users.id"), nullable=False),
        sa.Column("family_id", sa.String(36), sa.ForeignKey("families.id"), nullable=False),
        sa.Column("object_type", sa.String(40), nullable=False),
        sa.Column("object_id", sa.String(36), nullable=False),
        sa.Column("modality", sa.String(20), nullable=False),
        sa.Column("model", sa.String(120), nullable=False),
        sa.Column("endpoint_id", sa.String(120), nullable=False, server_default=""),
        sa.Column("content_hash", sa.String(64), nullable=False),
        sa.Column("source_updated_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("vector", Vector(), nullable=True),
        sa.Column("status", sa.String(30), nullable=False, server_default="pending"),
        sa.Column("last_error", sa.String(500), nullable=True),
        sa.Column("created_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("updated_at", sa.DateTime(timezone=True), nullable=False),
        sa.UniqueConstraint("object_type", "object_id", "modality", "model", "endpoint_id", name="uq_embeddings_source_model"),
    )
    for name, columns in [
        ("ix_embeddings_family_id", ["family_id"]),
        ("ix_embeddings_object_type", ["object_type"]),
        ("ix_embeddings_object_id", ["object_id"]),
        ("ix_embeddings_modality", ["modality"]),
        ("ix_embeddings_content_hash", ["content_hash"]),
        ("ix_embeddings_status", ["status"]),
    ]:
        op.create_index(name, "embeddings", columns)


def downgrade() -> None:
    op.drop_table("embeddings")
