"""Add AI artifacts, claims, and processing jobs.

Revision ID: 0004
Revises: 0003
"""
from alembic import op
import sqlalchemy as sa

revision = "0004"
down_revision = "0003"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.create_table(
        "claims",
        sa.Column("id", sa.String(36), primary_key=True),
        sa.Column("owner_id", sa.String(36), sa.ForeignKey("users.id"), nullable=False),
        sa.Column("family_id", sa.String(36), sa.ForeignKey("families.id"), nullable=False),
        sa.Column("subject_person_id", sa.String(36), sa.ForeignKey("persons.id"), nullable=True),
        sa.Column("predicate", sa.String(120), nullable=False),
        sa.Column("object_person_id", sa.String(36), sa.ForeignKey("persons.id"), nullable=True),
        sa.Column("object_text", sa.Text(), nullable=False, server_default=""),
        sa.Column("source_record_id", sa.String(36), sa.ForeignKey("records.id"), nullable=True),
        sa.Column("claim_type", sa.String(40), nullable=False, server_default="ai_suggestion"),
        sa.Column("verification_status", sa.String(40), nullable=False, server_default="unverified_information"),
        sa.Column("confidence", sa.Float(), nullable=True),
        sa.Column("created_by_ai", sa.Boolean(), nullable=False, server_default=sa.true()),
        sa.Column("confirmed_at", sa.DateTime(timezone=True), nullable=True),
        sa.Column("created_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("updated_at", sa.DateTime(timezone=True), nullable=False),
    )
    op.create_index("ix_claims_family_id", "claims", ["family_id"])
    op.create_index("ix_claims_subject_person_id", "claims", ["subject_person_id"])
    op.create_index("ix_claims_source_record_id", "claims", ["source_record_id"])
    op.create_table(
        "ai_artifacts",
        sa.Column("id", sa.String(36), primary_key=True),
        sa.Column("owner_id", sa.String(36), sa.ForeignKey("users.id"), nullable=False),
        sa.Column("family_id", sa.String(36), sa.ForeignKey("families.id"), nullable=False),
        sa.Column("record_id", sa.String(36), sa.ForeignKey("records.id"), nullable=True),
        sa.Column("person_id", sa.String(36), sa.ForeignKey("persons.id"), nullable=True),
        sa.Column("artifact_type", sa.String(50), nullable=False),
        sa.Column("provider", sa.String(50), nullable=False),
        sa.Column("model", sa.String(120), nullable=False),
        sa.Column("prompt_version", sa.String(40), nullable=False),
        sa.Column("input_hash", sa.String(64), nullable=False),
        sa.Column("output_json", sa.JSON(), nullable=False),
        sa.Column("status", sa.String(30), nullable=False, server_default="suggestion"),
        sa.Column("user_confirmed", sa.Boolean(), nullable=False, server_default=sa.false()),
        sa.Column("created_at", sa.DateTime(timezone=True), nullable=False),
    )
    for name, columns in [
        ("ix_ai_artifacts_family_id", ["family_id"]), ("ix_ai_artifacts_record_id", ["record_id"]),
        ("ix_ai_artifacts_person_id", ["person_id"]), ("ix_ai_artifacts_artifact_type", ["artifact_type"]),
    ]:
        op.create_index(name, "ai_artifacts", columns)
    op.create_table(
        "processing_jobs",
        sa.Column("id", sa.String(36), primary_key=True),
        sa.Column("owner_id", sa.String(36), sa.ForeignKey("users.id"), nullable=False),
        sa.Column("family_id", sa.String(36), sa.ForeignKey("families.id"), nullable=False),
        sa.Column("job_type", sa.String(50), nullable=False),
        sa.Column("record_id", sa.String(36), sa.ForeignKey("records.id"), nullable=True),
        sa.Column("person_id", sa.String(36), sa.ForeignKey("persons.id"), nullable=True),
        sa.Column("status", sa.String(30), nullable=False, server_default="pending"),
        sa.Column("provider", sa.String(50), nullable=False, server_default="deepseek"),
        sa.Column("model", sa.String(120), nullable=False),
        sa.Column("payload_json", sa.JSON(), nullable=False, server_default="{}"),
        sa.Column("result_artifact_id", sa.String(36), sa.ForeignKey("ai_artifacts.id"), nullable=True),
        sa.Column("attempt_count", sa.Integer(), nullable=False, server_default="0"),
        sa.Column("max_attempts", sa.Integer(), nullable=False, server_default="3"),
        sa.Column("last_error", sa.String(500), nullable=True),
        sa.Column("created_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("updated_at", sa.DateTime(timezone=True), nullable=False),
    )
    for name, columns in [
        ("ix_processing_jobs_family_id", ["family_id"]), ("ix_processing_jobs_job_type", ["job_type"]),
        ("ix_processing_jobs_record_id", ["record_id"]), ("ix_processing_jobs_person_id", ["person_id"]),
        ("ix_processing_jobs_status", ["status"]),
    ]:
        op.create_index(name, "processing_jobs", columns)


def downgrade() -> None:
    op.drop_table("processing_jobs")
    op.drop_table("ai_artifacts")
    op.drop_table("claims")
