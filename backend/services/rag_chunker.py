"""
RAG Chunker — Phase 3.

Converts a structured Syllabus (Phase 1 output) into a flat list of
RagChunk objects. Each syllabus topic becomes exactly one semantic chunk.

No LLM calls are made here. All content comes directly from the syllabus.
"""

import uuid
from dataclasses import dataclass, field
from typing import List

from models.syllabus_models import Syllabus


# ---------------------------------------------------------------------------
# Chunk data class
# ---------------------------------------------------------------------------

@dataclass
class RagChunk:
    """
    A single semantic unit derived from one syllabus topic.
    The 'text' field is what gets embedded by the embedding service.
    The 'metadata' dict is stored alongside the FAISS index for retrieval.
    """
    chunk_id: str
    text: str
    metadata: dict


# ---------------------------------------------------------------------------
# Chunker
# ---------------------------------------------------------------------------

def create_chunks(syllabus: Syllabus) -> List[RagChunk]:
    """
    Converts a Syllabus into one RagChunk per topic, preserving syllabus order.

    Chunk text format:
        Course: {courseName} ({courseCode})
        Unit {unitNumber}: {unitName}
        Topic: {topicName}
        Subtopics: {comma-separated subtopics or 'None'}

    Metadata keys:
        chunkId, courseName, courseCode, unitNumber, unitName,
        topicName, subtopics, sourcePages

    Returns an empty list if the syllabus has no units or topics.
    """
    chunks: List[RagChunk] = []
    course_code = syllabus.courseCode or "N/A"

    for unit in syllabus.units:
        if not unit.topics:
            continue

        for topic_idx, topic in enumerate(unit.topics):
            # Deterministic, human-readable chunk ID
            chunk_id = f"u{unit.unitNumber}_t{topic_idx + 1}"

            # Subtopics as a readable comma-separated string
            subtopics_str = (
                ", ".join(topic.subtopics) if topic.subtopics else "None"
            )

            # Chunk text — what gets embedded
            text = (
                f"Course: {syllabus.courseName} ({course_code})\n"
                f"Unit {unit.unitNumber}: {unit.unitName}\n"
                f"Topic: {topic.name}\n"
                f"Subtopics: {subtopics_str}"
            )

            # Metadata stored alongside the FAISS index
            metadata = {
                "chunkId": chunk_id,
                "courseName": syllabus.courseName,
                "courseCode": syllabus.courseCode,
                "unitNumber": unit.unitNumber,
                "unitName": unit.unitName,
                "topicName": topic.name,
                "subtopics": list(topic.subtopics),
                "sourcePages": list(topic.sourcePages),
                "text": text,   # stored so we can return it on retrieval without re-generation
            }

            chunks.append(RagChunk(
                chunk_id=chunk_id,
                text=text,
                metadata=metadata,
            ))

    return chunks
