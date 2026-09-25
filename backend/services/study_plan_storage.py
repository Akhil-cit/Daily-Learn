"""
Phase 4 — Local JSON Storage for Study Plans.

Temporary persistence layer for generated study plans.
Saves plans locally using the courseCode as the file identifier.
This allows the Daily Learn service to retrieve the plan later.
Will be replaced by a database/Room when moving to Android.
"""

import os
import json
import re
from pathlib import Path
from typing import Optional

from config import settings
from logger import logger
from models.study_plan_models import StudyPlanResult


class StudyPlanStorage:
    """
    Handles saving and loading StudyPlanResult JSON to/from disk.
    """

    def __init__(self):
        self.storage_dir = Path(settings.study_plans_dir)
        # Directory is created in config.py, but just to be safe:
        self.storage_dir.mkdir(parents=True, exist_ok=True)

    def _sanitize_course_code(self, course_code: str) -> str:
        """
        Sanitize course code to be safe for filenames.
        Replaces non-alphanumeric characters with underscores.
        """
        if not course_code:
            return "unknown_course"
        return re.sub(r'[^a-zA-Z0-9_-]', '_', course_code)

    def _get_file_path(self, course_code: str) -> Path:
        safe_code = self._sanitize_course_code(course_code)
        return self.storage_dir / f"{safe_code}.json"

    def save_plan(self, plan: StudyPlanResult) -> None:
        """
        Saves a StudyPlanResult as JSON.
        Uses plan.courseCode (or courseName if code is missing).
        """
        # Prefer courseCode, fallback to courseName if code is somehow missing
        identifier = plan.courseCode or plan.courseName
        file_path = self._get_file_path(identifier)
        
        try:
            # Pydantic v2 .model_dump_json() handles serialization
            json_data = plan.model_dump_json(indent=2)
            file_path.write_text(json_data, encoding="utf-8")
            logger.info(f"[Storage] Saved study plan for '{identifier}' to {file_path}")
        except Exception as e:
            logger.error(f"[Storage] Failed to save study plan for '{identifier}': {e}")
            raise

    def load_plan(self, course_code: str) -> Optional[StudyPlanResult]:
        """
        Loads a StudyPlanResult from JSON for the given courseCode.
        Returns None if not found.
        """
        file_path = self._get_file_path(course_code)
        
        if not file_path.exists():
            return None
            
        try:
            json_data = file_path.read_text(encoding="utf-8")
            # Parse back into the Pydantic model
            plan = StudyPlanResult.model_validate_json(json_data)
            logger.info(f"[Storage] Loaded study plan for '{course_code}' from {file_path}")
            return plan
        except Exception as e:
            logger.error(f"[Storage] Failed to load study plan for '{course_code}': {e}")
            raise


# Module-level singleton
study_plan_storage = StudyPlanStorage()
