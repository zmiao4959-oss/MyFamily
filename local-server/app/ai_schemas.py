from __future__ import annotations

from datetime import date, datetime
from typing import Literal

from pydantic import BaseModel, ConfigDict, Field


class StrictModel(BaseModel):
    model_config = ConfigDict(extra="forbid")


class PersonCandidate(StrictModel):
    name: str = Field(min_length=1, max_length=120)
    matched_person_id: str | None = None
    confidence: float = Field(ge=0, le=1)


class LocationCandidate(StrictModel):
    name: str = Field(min_length=1, max_length=240)
    confidence: float = Field(ge=0, le=1)


class ClaimSuggestion(StrictModel):
    subject_person_id: str | None = None
    predicate: str = Field(min_length=1, max_length=120)
    object_person_id: str | None = None
    object_text: str = Field(default="", max_length=500)
    confidence: float = Field(ge=0, le=1)


class OrganizeResult(StrictModel):
    title: str = Field(min_length=1, max_length=240)
    summary: str = Field(min_length=1, max_length=4000)
    people_candidates: list[PersonCandidate] = Field(default_factory=list, max_length=30)
    locations: list[LocationCandidate] = Field(default_factory=list, max_length=20)
    event_date: date | None = None
    date_text: str = Field(default="", max_length=120)
    date_precision: Literal["exact", "year", "approximate", "unknown"] = "unknown"
    tags: list[str] = Field(default_factory=list, max_length=20)
    claims: list[ClaimSuggestion] = Field(default_factory=list, max_length=30)
    questions_for_user: list[str] = Field(default_factory=list, max_length=20)
    needs_confirmation: bool = True


class BiographyResult(StrictModel):
    biography: str = Field(min_length=1, max_length=12000)
    source_record_ids: list[str] = Field(default_factory=list, max_length=100)
    uncertainties: list[str] = Field(default_factory=list, max_length=30)
    needs_confirmation: bool = True


class InterviewQuestion(StrictModel):
    question: str = Field(min_length=1, max_length=300)
    topic: str = Field(min_length=1, max_length=80)


class InterviewResult(StrictModel):
    person_id: str
    questions: list[InterviewQuestion] = Field(min_length=3, max_length=30)
    needs_confirmation: bool = True


class AskResult(StrictModel):
    answer: str = Field(min_length=1, max_length=8000)
    source_record_ids: list[str] = Field(default_factory=list, max_length=100)
    unknown: bool = False
    needs_confirmation: bool = False


class AIObjectRequest(BaseModel):
    record_id: str | None = None
    person_id: str | None = None


class AIAskRequest(BaseModel):
    question: str = Field(min_length=2, max_length=1000)
    person_id: str | None = None


class AIStatusRead(BaseModel):
    enabled: bool
    configured: bool
    provider: str
    fast_model: str
    main_model: str


class ProcessingJobRead(BaseModel):
    model_config = ConfigDict(from_attributes=True)
    id: str
    job_type: str
    record_id: str | None
    person_id: str | None
    status: str
    provider: str
    model: str
    result_artifact_id: str | None
    attempt_count: int
    max_attempts: int
    last_error: str | None
    created_at: datetime
    updated_at: datetime


class AIArtifactRead(BaseModel):
    model_config = ConfigDict(from_attributes=True)
    id: str
    record_id: str | None
    person_id: str | None
    artifact_type: str
    provider: str
    model: str
    prompt_version: str
    output_json: dict
    status: str
    user_confirmed: bool
    created_at: datetime


class ConfirmArtifactRequest(BaseModel):
    confirmed: bool = True
