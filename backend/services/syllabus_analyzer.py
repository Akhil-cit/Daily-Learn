import json
import re
from pathlib import Path
from typing import List, Optional

from config import BASE_DIR
from logger import logger
from models.syllabus_models import Syllabus, PageExtractionResult
from services.ollama_client import ollama_client, OllamaClient


PROMPT_PATH = BASE_DIR / "prompts" / "syllabus_prompt.txt"


def load_system_prompt() -> str:
    """
    Loads the system prompt from prompts/syllabus_prompt.txt.
    """
    if not PROMPT_PATH.exists():
        raise FileNotFoundError(f"System prompt file not found at: {PROMPT_PATH}")
    return PROMPT_PATH.read_text(encoding="utf-8").strip()


def extract_json_substring(raw_text: str) -> str:
    """
    Extracts the JSON object substring from raw LLM output,
    handling markdown fences (```json ... ```) or conversational wrappers.
    """
    cleaned = raw_text.strip()
    
    # Strip markdown code fences if present
    if cleaned.startswith("```"):
        cleaned = re.sub(r"^```(?:json)?\s*", "", cleaned, flags=re.IGNORECASE)
        cleaned = re.sub(r"\s*```$", "", cleaned)
        cleaned = cleaned.strip()

    # Find boundaries of the outermost JSON object { ... }
    first_brace = cleaned.find("{")
    last_brace = cleaned.rfind("}")

    if first_brace != -1 and last_brace != -1 and last_brace > first_brace:
        return cleaned[first_brace:last_brace + 1]
    
    return cleaned


def format_syllabus_context(pages: List[PageExtractionResult]) -> str:
    """
    Formats extracted pages into a structured representation with page markers.
    """
    sections = []
    for page in pages:
        if page.text.strip():
            sections.append(f"[PAGE {page.page}]\n{page.text}")
    return "\n\n".join(sections)


class SyllabusAnalyzer:
    """
    Service responsible for converting extracted syllabus text into structured Syllabus JSON
    via local Ollama inference, schema validation, and controlled self-correction.
    """
    def __init__(self, client: Optional[OllamaClient] = None):
        self.client = client or ollama_client
        self.system_prompt = load_system_prompt()

    async def analyze(self, pages: List[PageExtractionResult]) -> Syllabus:
        """
        Runs the full analysis pipeline:
        1. Validates input text.
        2. Formats document context with page markers.
        3. Calls local Ollama model.
        4. Validates structured JSON with Pydantic.
        5. Performs 1-step repair retry if validation fails.
        """
        if not pages or all(not p.text.strip() for p in pages):
            raise ValueError("Cannot analyze syllabus: No text content found in document.")

        formatted_doc = format_syllabus_context(pages)
        total_chars = len(formatted_doc)
        logger.info(f"[AI] Prepared document context: {len(pages)} pages, {total_chars} characters")

        # Initial prompt message
        messages = [
            {"role": "system", "content": self.system_prompt},
            {
                "role": "user",
                "content": (
                    "Please analyze this uploaded syllabus document and extract its structured JSON:\n\n"
                    f"{formatted_doc}"
                )
            }
        ]

        # Step 1: Initial LLM Inference
        raw_response = await self.client.chat(messages=messages, format="json", temperature=0.1)

        # Step 2: Parse and Validate JSON
        logger.info("[AI] Validating JSON output against Syllabus schema")
        try:
            return self._parse_and_validate(raw_response)
        except Exception as first_error:
            logger.warning(
                f"[AI] Initial JSON validation failed: {str(first_error)}. "
                f"Attempting 1 controlled repair retry with Ollama..."
            )

            # Step 3: Controlled 1-step Repair Retry
            repair_messages = list(messages)
            repair_messages.append({"role": "assistant", "content": raw_response})
            repair_messages.append({
                "role": "user",
                "content": (
                    f"Your previous response had the following JSON validation error: {str(first_error)}.\n"
                    f"Please fix the error and output ONLY the corrected, valid JSON object strictly matching the schema."
                )
            })

            repaired_response = await self.client.chat(
                messages=repair_messages,
                format="json",
                temperature=0.0
            )

            try:
                syllabus = self._parse_and_validate(repaired_response)
                logger.info("[AI] Controlled repair retry succeeded!")
                return syllabus
            except Exception as second_error:
                logger.error(f"[AI] Repair retry also failed: {str(second_error)}")
                raise ValueError(
                    f"Failed to extract valid syllabus JSON from model after retry. "
                    f"Validation error: {str(second_error)}"
                )

    def _parse_and_validate(self, raw_text: str) -> Syllabus:
        """
        Helper method to extract JSON substring and validate against Syllabus Pydantic model.
        """
        json_candidate = extract_json_substring(raw_text)
        
        # Verify it's valid JSON syntax
        try:
            parsed_data = json.loads(json_candidate)
        except json.JSONDecodeError as jde:
            raise ValueError(f"Invalid JSON syntax returned by model: {str(jde)}")

        # Validate with Pydantic
        syllabus = Syllabus.model_validate(parsed_data)
        
        total_units = len(syllabus.units)
        total_topics = sum(len(u.topics) for u in syllabus.units)
        logger.info(
            f"[AI] Syllabus extraction successful: course='{syllabus.courseName}', "
            f"{total_units} unit(s), {total_topics} topic(s)"
        )
        return syllabus


syllabus_analyzer = SyllabusAnalyzer()
