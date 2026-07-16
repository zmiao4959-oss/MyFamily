"""Add media assets and resumable upload sessions.

Revision ID: 0003
Revises: 0002
"""
from alembic import op
import sqlalchemy as sa

revision = "0003"
down_revision = "0002"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.create_table(
        "media_assets",
        sa.Column("id", sa.String(36), primary_key=True),
        sa.Column("client_uuid", sa.String(36), nullable=False, unique=True),
        sa.Column("owner_id", sa.String(36), sa.ForeignKey("users.id"), nullable=False),
        sa.Column("family_id", sa.String(36), sa.ForeignKey("families.id"), nullable=False),
        sa.Column("record_id", sa.String(36), sa.ForeignKey("records.id"), nullable=False),
        sa.Column("media_type", sa.String(20), nullable=False),
        sa.Column("original_filename", sa.String(255), nullable=False),
        sa.Column("storage_path", sa.String(500), nullable=False),
        sa.Column("thumbnail_path", sa.String(500), nullable=True),
        sa.Column("mime_type", sa.String(100), nullable=False),
        sa.Column("size_bytes", sa.BigInteger(), nullable=False),
        sa.Column("sha256", sa.String(64), nullable=False),
        sa.Column("duration_ms", sa.BigInteger(), nullable=True),
        sa.Column("width", sa.Integer(), nullable=True),
        sa.Column("height", sa.Integer(), nullable=True),
        sa.Column("captured_at", sa.DateTime(timezone=True), nullable=True),
        sa.Column("metadata_json", sa.JSON(), nullable=False, server_default="{}"),
        sa.Column("duplicate_of_id", sa.String(36), sa.ForeignKey("media_assets.id"), nullable=True),
        sa.Column("created_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("deleted_at", sa.DateTime(timezone=True), nullable=True),
    )
    op.create_index("ix_media_assets_client_uuid", "media_assets", ["client_uuid"])
    op.create_index("ix_media_assets_family_id", "media_assets", ["family_id"])
    op.create_index("ix_media_assets_record_id", "media_assets", ["record_id"])
    op.create_index("ix_media_assets_sha256", "media_assets", ["sha256"])
    op.create_table(
        "upload_sessions",
        sa.Column("id", sa.String(36), primary_key=True),
        sa.Column("client_uuid", sa.String(36), nullable=False),
        sa.Column("record_id", sa.String(36), sa.ForeignKey("records.id"), nullable=False),
        sa.Column("original_filename", sa.String(255), nullable=False),
        sa.Column("mime_type", sa.String(100), nullable=False),
        sa.Column("media_type", sa.String(20), nullable=False),
        sa.Column("expected_size", sa.BigInteger(), nullable=False),
        sa.Column("expected_sha256", sa.String(64), nullable=True),
        sa.Column("bytes_received", sa.BigInteger(), nullable=False, server_default="0"),
        sa.Column("temp_path", sa.String(500), nullable=False),
        sa.Column("status", sa.String(20), nullable=False, server_default="uploading"),
        sa.Column("asset_id", sa.String(36), sa.ForeignKey("media_assets.id"), nullable=True),
        sa.Column("created_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("updated_at", sa.DateTime(timezone=True), nullable=False),
    )
    op.create_index("ix_upload_sessions_client_uuid", "upload_sessions", ["client_uuid"])


def downgrade() -> None:
    op.drop_table("upload_sessions")
    op.drop_table("media_assets")
