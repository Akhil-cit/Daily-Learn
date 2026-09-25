from typing import List, Optional, Any
from pydantic import BaseModel, Field


class Topic(BaseModel):
    """
    Represents an individual academic topic within a syllabus unit.
    """
    name: str = Field(description="Exact terminology of the topic from the syllabus")
    subtopics: List[str] = Field(
        default_factory=list,
        description="Explicit subtopics listed under this topic, if any"
    )
    sourcePages: List[int] = Field(
        default_factory=list,
        description="Page numbers where this topic is referenced"
    )


class Unit(BaseModel):
    """
    Represents a syllabus unit / module / chapter.
    """
    unitNumber: int = Field(description="Sequential unit number (1, 2, 3...)")
    unitName: str = Field(description="Title / name of the unit")
    lectureHours: Optional[float] = Field(
        default=None,
        description="Explicit lecture hours allocated to this unit, or null if not stated"
    )
    topics: List[Topic] = Field(
        default_factory=list,
        description="List of academic topics belonging to this unit"
    )


class Syllabus(BaseModel):
    """
    Complete structured syllabus extracted from the document.
    """
    courseName: str = Field(description="Name or title of the course / subject")
    courseCode: Optional[str] = Field(
        default=None,
        description="Course code (e.g. 25SS212, CS101) if available"
    )
    units: List[Unit] = Field(
        default_factory=list,
        description="Ordered list of units/modules in the syllabus"
    )


class PageExtractionResult(BaseModel):
    """
    Raw extracted text and metadata for a single PDF page or image.
    """
    page: int
    text: str
    charCount: int


class SyllabusAnalysisResponse(BaseModel):
    """
    Successful API response envelope containing parsed syllabus data.
    """
    success: bool = True
    data: Syllabus


class ErrorResponse(BaseModel):
    """
    Standard error envelope returned when a request fails.
    """
    success: bool = False
    error: str
    details: Optional[Any] = None


class HealthResponse(BaseModel):
    """
    General health check response.
    """
    status: str = "ok"


class OllamaHealthResponse(BaseModel):
    """
    Ollama service and model availability status response.
    """
    ollama: str
    model: str
    available: bool
    models_available: List[str] = Field(default_factory=list)
    error: Optional[str] = None


class OcrHealthResponse(BaseModel):
    """
    Local Tesseract OCR status response.
    """
    ocr: str
    installed: bool
    path: Optional[str] = None
    version: Optional[str] = None
    error: Optional[str] = None
