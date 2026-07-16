"""Add core family data and sync tables.

Revision ID: 0002
Revises: 0001
"""
from alembic import op
import sqlalchemy as sa

revision = "0002"
down_revision = "0001"
branch_labels = None
depends_on = None

OWNER_ID = "00000000-0000-0000-0000-000000000001"
FAMILY_ID = "00000000-0000-0000-0000-000000000002"


def sync_columns():
    return [
        sa.Column("id", sa.String(36), primary_key=True),
        sa.Column("client_uuid", sa.String(36), nullable=False, unique=True),
        sa.Column("sync_version", sa.Integer(), nullable=False, server_default="1"),
        sa.Column("created_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("updated_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("deleted_at", sa.DateTime(timezone=True), nullable=True),
        sa.Column("owner_id", sa.String(36), sa.ForeignKey("users.id"), nullable=False),
        sa.Column("family_id", sa.String(36), sa.ForeignKey("families.id"), nullable=False),
    ]


def upgrade() -> None:
    op.create_table(
        "users",
        sa.Column("id", sa.String(36), primary_key=True),
        sa.Column("display_name", sa.String(120), nullable=False),
        sa.Column("created_at", sa.DateTime(timezone=True), nullable=False),
    )
    op.create_table(
        "families",
        sa.Column("id", sa.String(36), primary_key=True),
        sa.Column("owner_id", sa.String(36), sa.ForeignKey("users.id"), nullable=False),
        sa.Column("name", sa.String(120), nullable=False),
        sa.Column("created_at", sa.DateTime(timezone=True), nullable=False),
    )
    op.execute(f"INSERT INTO users (id, display_name, created_at) VALUES ('{OWNER_ID}', '本机用户', CURRENT_TIMESTAMP)")
    op.execute(f"INSERT INTO families (id, owner_id, name, created_at) VALUES ('{FAMILY_ID}', '{OWNER_ID}', '我的家庭', CURRENT_TIMESTAMP)")

    op.create_table(
        "persons",
        *sync_columns(),
        sa.Column("name", sa.String(120), nullable=False),
        sa.Column("surname", sa.String(60), nullable=False, server_default=""),
        sa.Column("given_name", sa.String(60), nullable=False, server_default=""),
        sa.Column("former_names", sa.JSON(), nullable=False, server_default="[]"),
        sa.Column("nickname", sa.String(80), nullable=False, server_default=""),
        sa.Column("gender", sa.String(30), nullable=True),
        sa.Column("birth_date", sa.Date(), nullable=True),
        sa.Column("birth_year", sa.Integer(), nullable=True),
        sa.Column("birth_date_precision", sa.String(30), nullable=False, server_default="unknown"),
        sa.Column("death_date", sa.Date(), nullable=True),
        sa.Column("death_year", sa.Integer(), nullable=True),
        sa.Column("death_date_precision", sa.String(30), nullable=False, server_default="unknown"),
        sa.Column("birth_place", sa.String(240), nullable=False, server_default=""),
        sa.Column("ancestral_home", sa.String(240), nullable=False, server_default=""),
        sa.Column("former_residences", sa.JSON(), nullable=False, server_default="[]"),
        sa.Column("biography", sa.Text(), nullable=False, server_default=""),
        sa.Column("portrait_media_id", sa.String(36), nullable=True),
        sa.Column("is_self", sa.Boolean(), nullable=False, server_default=sa.false()),
        sa.Column("notes", sa.Text(), nullable=False, server_default=""),
        sa.Column("source_text", sa.Text(), nullable=False, server_default=""),
        sa.Column("verification_status", sa.String(40), nullable=False, server_default="unverified_information"),
        sa.Column("visibility", sa.String(30), nullable=False, server_default="private"),
    )
    op.create_index("ix_persons_family_id", "persons", ["family_id"])
    op.create_index("ix_persons_name", "persons", ["name"])

    op.create_table(
        "relationships",
        *sync_columns(),
        sa.Column("person_a_id", sa.String(36), sa.ForeignKey("persons.id"), nullable=False),
        sa.Column("person_b_id", sa.String(36), sa.ForeignKey("persons.id"), nullable=False),
        sa.Column("relation_type", sa.String(50), nullable=False),
        sa.Column("start_date", sa.Date(), nullable=True),
        sa.Column("end_date", sa.Date(), nullable=True),
        sa.Column("notes", sa.Text(), nullable=False, server_default=""),
        sa.Column("source_text", sa.Text(), nullable=False, server_default=""),
        sa.Column("verification_status", sa.String(40), nullable=False, server_default="unverified_information"),
        sa.Column("visibility", sa.String(30), nullable=False, server_default="private"),
    )
    op.create_index("ix_relationships_family_id", "relationships", ["family_id"])
    op.create_index("ix_relationships_person_a_id", "relationships", ["person_a_id"])
    op.create_index("ix_relationships_person_b_id", "relationships", ["person_b_id"])

    op.create_table(
        "tags",
        *sync_columns(),
        sa.Column("name", sa.String(80), nullable=False),
        sa.Column("color", sa.String(20), nullable=False, server_default=""),
    )
    op.create_index("ix_tags_family_id", "tags", ["family_id"])
    op.create_index("ix_tags_name", "tags", ["name"])

    op.create_table(
        "records",
        *sync_columns(),
        sa.Column("author_person_id", sa.String(36), sa.ForeignKey("persons.id"), nullable=True),
        sa.Column("title", sa.String(240), nullable=False),
        sa.Column("original_text", sa.Text(), nullable=False, server_default=""),
        sa.Column("edited_text", sa.Text(), nullable=False, server_default=""),
        sa.Column("ai_summary", sa.Text(), nullable=False, server_default=""),
        sa.Column("record_type", sa.String(40), nullable=False, server_default="text"),
        sa.Column("occurred_at_start", sa.DateTime(timezone=True), nullable=True),
        sa.Column("occurred_at_end", sa.DateTime(timezone=True), nullable=True),
        sa.Column("date_precision", sa.String(30), nullable=False, server_default="unknown"),
        sa.Column("location_text", sa.String(240), nullable=False, server_default=""),
        sa.Column("visibility", sa.String(30), nullable=False, server_default="private"),
        sa.Column("source_type", sa.String(40), nullable=False, server_default="personal_memory"),
        sa.Column("verification_status", sa.String(40), nullable=False, server_default="unverified_information"),
        sa.Column("ai_processing_status", sa.String(30), nullable=False, server_default="disabled"),
        sa.Column("person_ids", sa.JSON(), nullable=False, server_default="[]"),
    )
    op.create_index("ix_records_family_id", "records", ["family_id"])
    op.create_table(
        "record_tags",
        sa.Column("record_id", sa.String(36), sa.ForeignKey("records.id", ondelete="CASCADE"), primary_key=True),
        sa.Column("tag_id", sa.String(36), sa.ForeignKey("tags.id", ondelete="CASCADE"), primary_key=True),
    )
    op.create_table(
        "sync_changes",
        sa.Column("version", sa.Integer(), primary_key=True, autoincrement=True),
        sa.Column("entity_type", sa.String(40), nullable=False),
        sa.Column("entity_id", sa.String(36), nullable=False),
        sa.Column("operation", sa.String(20), nullable=False),
        sa.Column("changed_at", sa.DateTime(timezone=True), nullable=False),
    )
    op.create_index("ix_sync_changes_entity_type", "sync_changes", ["entity_type"])
    op.create_index("ix_sync_changes_entity_id", "sync_changes", ["entity_id"])


def downgrade() -> None:
    for table in ["sync_changes", "record_tags", "records", "tags", "relationships", "persons", "families", "users"]:
        op.drop_table(table)
