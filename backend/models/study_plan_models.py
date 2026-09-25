"""
Pydantic models for Phase 2 — Daily Study Plan Engine.

These models define the request body and response envelope for
POST /api/v1/study-plan/generate.

No LLM is involved at this stage; the scheduler is deterministic Python.
"""

from typing import List, Optional
from pydantic import BaseModel, Field, field_validator

from .syllabus_models import Syllabus


# ---------------------------------------------------------------------------
# Scheduled topic entry — one topic assigned to a day
# ---------------------------------------------------------------------------

class ScheduledTopic(BaseModel):
    """
    A single topic from the syllabus assigned to a study or revision day.
    All fields are sourced directly from the structured syllabus; nothing
    is invented by the scheduler.
    """
    unitNumber: int = Field(description="Unit number this topic belongs to")
    unitName: str = Field(description="Name of the parent unit")
    topicName: str = Field(description="Exact topic name from the syllabus")
    subtopics: List[str] = Field(
        default_factory=list,
        description="Subtopics listed under this topic in the syllabus"
    )
    estimatedHours: float = Field(
        description="Estimated study hours for this topic (derived from unit lectureHours)"
    )


# ---------------------------------------------------------------------------
# Day model
# ---------------------------------------------------------------------------

class StudyDay(BaseModel):
    """
    Represents a single calendar day in the study plan.

    type:
      'study'    — new syllabus content is being studied
      'revision' — reviewing previously studied content
      'review'   — final full-syllabus review (last revision day when >= 2)
    """
    dayNumber: int = Field(description="Sequential day number starting from 1")
    date: Optional[str] = Field(
        default=None,
        description="ISO calendar date (YYYY-MM-DD) if startDate was provided in the request"
    )
    type: str = Field(
        description="Day type: 'study', 'revision', or 'review'"
    )
    topics: List[ScheduledTopic] = Field(
        default_factory=list,
        description="Topics assigned to this day"
    )
    estimatedHours: float = Field(
        description="Total estimated study hours for this day"
    )


# ---------------------------------------------------------------------------
# Top-level study plan result
# ---------------------------------------------------------------------------

class StudyPlanResult(BaseModel):
    """
    The full study plan produced by the deterministic scheduler.
    """
    courseName: str
    courseCode: Optional[str] = None
    totalDays: int = Field(description="Equals targetDays from the request")
    studyDaysCount: int = Field(description="Number of new-content study days")
    revisionDaysCount: int = Field(description="Number of revision/review days")
    totalEstimatedHours: float = Field(
        description="Total estimated syllabus workload in hours"
    )
    studyDays: List[StudyDay] = Field(
        description="Ordered list of study days (type='study')"
    )
    revisionDays: List[StudyDay] = Field(
        description="Ordered list of revision/review days (type='revision' or 'review')"
    )
    warnings: List[str] = Field(
        default_factory=list,
        description="Non-fatal notices (e.g. missing lectureHours, sparse day allocation)"
    )


# ---------------------------------------------------------------------------
# Request model
# ---------------------------------------------------------------------------

class StudyPlanRequest(BaseModel):
    """
    Request body for POST /api/v1/study-plan/generate.

    Only 'syllabus' and 'targetDays' are required.
    Optional fields are accepted and stored but calendar-level date
    tagging (per-day dates) requires startDate.
    """
    syllabus: Syllabus = Field(description="Structured syllabus from Phase 1 analysis")
    targetDays: int = Field(
        description="Total number of calendar days until exam/midsem (>= 1)",
        ge=1
    )
    dailyStudyHours: Optional[float] = Field(
        default=None,
        description="Hours the student plans to study per day (informational in v1)",
        gt=0
    )
    startDate: Optional[str] = Field(
        default=None,
        description="ISO date the study plan begins (YYYY-MM-DD). Enables per-day date labels."
    )
    examDate: Optional[str] = Field(
        default=None,
        description="ISO date of the exam (YYYY-MM-DD). Stored for future reference."
    )
    revisionBuffer: float = Field(
        default=0.15,
        description="Fraction of targetDays to reserve for revision (0.0–0.4). Default 15%.",
        ge=0.0,
        le=0.4
    )

    @field_validator("targetDays")
    @classmethod
    def validate_target_days(cls, v: int) -> int:
        if v < 1:
            raise ValueError("targetDays must be >= 1")
        return v

    @field_validator("startDate", "examDate", mode="before")
    @classmethod
    def validate_date_format(cls, v):
        if v is None:
            return v
        import re
        if not re.match(r"^\d{4}-\d{2}-\d{2}$", str(v)):
            raise ValueError("Date must be in YYYY-MM-DD format (e.g. '2026-08-20')")
        return v


# ---------------------------------------------------------------------------
# Response envelope
# ---------------------------------------------------------------------------

class StudyPlanResponse(BaseModel):
    """
    Successful response envelope for POST /api/v1/study-plan/generate.
    """
    success: bool = True
    studyPlan: StudyPlanResult


class StudyPlanHealthResponse(BaseModel):
    """
    Health check response for the study plan service.
    """
    status: str = "ok"
    scheduler: str = "deterministic-python"
    version: str = "2.0.0"
