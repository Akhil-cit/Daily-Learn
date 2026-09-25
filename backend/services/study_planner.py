"""
Phase 2 — Deterministic Study Plan Scheduler.

Converts a structured Syllabus (Phase 1 output) into a Day 1 → Day N
daily study plan using pure Python. No LLM calls are made here.

Algorithm overview:
  1. Flatten syllabus into an ordered list of FlatTopics with hour weights.
  2. Calculate revision buffer (disabled for very short plans).
  3. Determine target hours-per-study-day from total workload.
  4. Greedily pack topics into study days in syllabus order.
  5. Distribute topics proportionally across revision days.
  6. Tag the last revision day as 'review' when >= 2 revision days exist.
  7. Optionally attach calendar dates when startDate is provided.
  8. Run post-scheduling validation before returning.
"""

import math
from dataclasses import dataclass, field
from datetime import date, timedelta
from typing import List, Optional, Tuple

from logger import logger
from models.syllabus_models import Syllabus, Unit, Topic
from models.study_plan_models import (
    ScheduledTopic,
    StudyDay,
    StudyPlanResult,
    StudyPlanRequest,
)


# ---------------------------------------------------------------------------
# Internal flat representation — one entry per syllabus topic
# ---------------------------------------------------------------------------

@dataclass
class FlatTopic:
    """
    A denormalised view of a single topic used during scheduling.
    All values come directly from the syllabus — nothing is invented.
    """
    unit_number: int
    unit_name: str
    topic_name: str
    subtopics: List[str]
    weight_hours: float          # Estimated study hours for this topic

    def to_scheduled_topic(self) -> ScheduledTopic:
        return ScheduledTopic(
            unitNumber=self.unit_number,
            unitName=self.unit_name,
            topicName=self.topic_name,
            subtopics=self.subtopics,
            estimatedHours=round(self.weight_hours, 2),
        )


# ---------------------------------------------------------------------------
# Core scheduler
# ---------------------------------------------------------------------------

class StudyPlanner:
    """
    Deterministic syllabus-to-study-plan converter.

    Usage:
        planner = StudyPlanner()
        result  = planner.generate(request)
    """

    # Minimum hours assigned to any single topic (prevents zero-weight topics)
    MIN_TOPIC_HOURS: float = 0.5

    # Default hours per topic when no lectureHours are present in the syllabus
    DEFAULT_TOPIC_HOURS: float = 1.0

    # Greedy packing overflow factor: a new day starts when adding the next
    # topic would exceed target_hours_per_day * OVERFLOW_FACTOR.
    # A value > 1.0 makes days slightly flexible to avoid tiny orphan days.
    OVERFLOW_FACTOR: float = 1.5

    # Revision buffer is disabled if targetDays is below this threshold.
    MIN_DAYS_FOR_REVISION: int = 4

    def generate(self, request: StudyPlanRequest) -> StudyPlanResult:
        """
        Main entry point.  Returns a StudyPlanResult or raises ValueError.
        """
        warnings: List[str] = []

        # --- 1. Flatten the syllabus into ordered FlatTopics -----------------
        flat_topics, flatten_warnings = self._flatten_syllabus(request.syllabus)
        warnings.extend(flatten_warnings)

        if not flat_topics:
            raise ValueError(
                "The syllabus contains no topics. "
                "Please upload a syllabus with at least one unit and topic."
            )

        total_hours = sum(t.weight_hours for t in flat_topics)

        # --- 2. Revision buffer calculation ----------------------------------
        target_days = request.targetDays

        if target_days >= self.MIN_DAYS_FOR_REVISION and request.revisionBuffer > 0:
            revision_day_count = max(1, round(target_days * request.revisionBuffer))
            # Never allocate more revision days than study days
            revision_day_count = min(revision_day_count, target_days - 1)
        else:
            revision_day_count = 0
            if target_days < self.MIN_DAYS_FOR_REVISION:
                warnings.append(
                    f"Revision buffer disabled: {target_days} days is too short "
                    f"(minimum {self.MIN_DAYS_FOR_REVISION} days required)."
                )

        study_day_count = target_days - revision_day_count

        logger.info(
            f"[Planner] targetDays={target_days}, studyDays={study_day_count}, "
            f"revisionDays={revision_day_count}, totalHours={total_hours:.1f}, "
            f"topics={len(flat_topics)}"
        )

        # --- 3. Pack topics into study days (greedy, order-preserving) -------
        study_days, pack_warnings = self._pack_into_days(
            flat_topics=flat_topics,
            study_day_count=study_day_count,
            total_hours=total_hours,
        )
        warnings.extend(pack_warnings)

        # --- 4. Build revision days ------------------------------------------
        revision_days = self._build_revision_days(
            flat_topics=flat_topics,
            revision_day_count=revision_day_count,
            day_offset=study_day_count,
        )

        # --- 5. Attach calendar dates (optional) ----------------------------
        if request.startDate:
            self._attach_dates(study_days + revision_days, request.startDate)

        # --- 6. Validate the final schedule ----------------------------------
        self._validate(
            flat_topics=flat_topics,
            study_days=study_days,
            revision_days=revision_days,
            target_days=target_days,
        )

        # --- 7. Balance warnings --------------------------------------------
        if study_days:
            hours_per_day = [d.estimatedHours for d in study_days]
            max_h, min_h = max(hours_per_day), min(hours_per_day)
            if min_h > 0 and max_h / min_h > 3.0:
                warnings.append(
                    "Day workload is unbalanced (heaviest day > 3x lightest day). "
                    "This may happen when a unit has very few or very many topics."
                )

        return StudyPlanResult(
            courseName=request.syllabus.courseName,
            courseCode=request.syllabus.courseCode,
            totalDays=target_days,
            studyDaysCount=len(study_days),
            revisionDaysCount=len(revision_days),
            totalEstimatedHours=round(total_hours, 2),
            studyDays=study_days,
            revisionDays=revision_days,
            warnings=warnings,
        )

    # -----------------------------------------------------------------------
    # Step 1 — Syllabus flattening
    # -----------------------------------------------------------------------

    def _flatten_syllabus(
        self, syllabus: Syllabus
    ) -> Tuple[List[FlatTopic], List[str]]:
        """
        Converts the nested Syllabus structure into an ordered flat list.
        Preserves unit → topic order exactly.
        Returns (flat_topics, warnings).
        """
        flat: List[FlatTopic] = []
        warnings: List[str] = []

        for unit in syllabus.units:
            if not unit.topics:
                warnings.append(
                    f"Unit {unit.unitNumber} '{unit.unitName}' has no topics — skipped."
                )
                continue

            # Weight assignment: lectureHours / topic count, else default
            if unit.lectureHours is not None and unit.lectureHours > 0:
                hours_per_topic = unit.lectureHours / len(unit.topics)
            else:
                hours_per_topic = self.DEFAULT_TOPIC_HOURS
                warnings.append(
                    f"Unit {unit.unitNumber} '{unit.unitName}' has no lectureHours; "
                    f"defaulting to {self.DEFAULT_TOPIC_HOURS}h per topic."
                )

            # Clamp to minimum
            hours_per_topic = max(hours_per_topic, self.MIN_TOPIC_HOURS)

            for topic in unit.topics:
                flat.append(FlatTopic(
                    unit_number=unit.unitNumber,
                    unit_name=unit.unitName,
                    topic_name=topic.name,
                    subtopics=list(topic.subtopics),
                    weight_hours=round(hours_per_topic, 4),
                ))

        return flat, warnings

    # -----------------------------------------------------------------------
    # Step 3 — Greedy bin-packing into study days
    # -----------------------------------------------------------------------

    def _pack_into_days(
        self,
        flat_topics: List[FlatTopic],
        study_day_count: int,
        total_hours: float,
    ) -> Tuple[List[StudyDay], List[str]]:
        """
        Greedy, order-preserving packing of flat_topics into exactly
        study_day_count days.

        Strategy:
          - Compute target_hours_per_day = total_hours / study_day_count.
          - Iterate topics in order, accumulating into the current day.
          - Start a new day when the accumulated load exceeds
            target_hours_per_day * OVERFLOW_FACTOR AND we still have
            remaining days to fill.
          - Guarantee: the number of returned days always == study_day_count.
        """
        warnings: List[str] = []

        if study_day_count <= 0:
            return [], warnings

        target_h = total_hours / study_day_count
        days: List[List[FlatTopic]] = [[] for _ in range(study_day_count)]
        current_day_idx = 0
        accumulated = 0.0

        for i, topic in enumerate(flat_topics):
            # Overflow: start a new day if we've exceeded the target load
            # and there is still a next day available.
            if (
                current_day_idx < study_day_count - 1
                and accumulated > 0
                and accumulated + topic.weight_hours > target_h * self.OVERFLOW_FACTOR
            ):
                current_day_idx += 1
                accumulated = 0.0

            days[current_day_idx].append(topic)
            accumulated += topic.weight_hours

        # Build StudyDay objects (day numbers start at 1)
        study_days: List[StudyDay] = []
        for idx, bucket in enumerate(days):
            day_hours = round(sum(t.weight_hours for t in bucket), 2)
            study_days.append(StudyDay(
                dayNumber=idx + 1,
                type="study",
                topics=[t.to_scheduled_topic() for t in bucket],
                estimatedHours=day_hours,
            ))

        # Warn if many days ended up empty (targetDays >> topic count)
        empty_days = sum(1 for d in study_days if not d.topics)
        if empty_days > 0:
            warnings.append(
                f"{empty_days} study day(s) have no topics because targetDays "
                f"({study_day_count}) far exceeds the number of syllabus topics "
                f"({len(flat_topics)}). Consider reducing targetDays."
            )

        return study_days, warnings

    # -----------------------------------------------------------------------
    # Step 4 — Revision day construction
    # -----------------------------------------------------------------------

    def _build_revision_days(
        self,
        flat_topics: List[FlatTopic],
        revision_day_count: int,
        day_offset: int,
    ) -> List[StudyDay]:
        """
        Distributes all topics proportionally across revision_day_count days.
        The last day is typed 'review' when revision_day_count >= 2.
        """
        if revision_day_count <= 0:
            return []

        revision_days: List[StudyDay] = []
        n = len(flat_topics)

        for i in range(revision_day_count):
            # Proportional slice of the flat topic list
            start_idx = math.floor(i * n / revision_day_count)
            end_idx = math.floor((i + 1) * n / revision_day_count)
            bucket = flat_topics[start_idx:end_idx]

            day_type = "review" if (revision_day_count >= 2 and i == revision_day_count - 1) else "revision"
            day_hours = round(sum(t.weight_hours for t in bucket), 2)

            revision_days.append(StudyDay(
                dayNumber=day_offset + i + 1,
                type=day_type,
                topics=[t.to_scheduled_topic() for t in bucket],
                estimatedHours=day_hours,
            ))

        return revision_days

    # -----------------------------------------------------------------------
    # Step 5 — Calendar date attachment
    # -----------------------------------------------------------------------

    def _attach_dates(self, all_days: List[StudyDay], start_date_str: str) -> None:
        """
        Attaches an ISO date string to each StudyDay based on startDate.
        Day 1 → startDate, Day 2 → startDate + 1, etc.
        """
        try:
            start = date.fromisoformat(start_date_str)
            for study_day in all_days:
                d = start + timedelta(days=study_day.dayNumber - 1)
                study_day.date = d.isoformat()
        except (ValueError, TypeError) as e:
            logger.warning(f"[Planner] Could not attach dates from '{start_date_str}': {e}")

    # -----------------------------------------------------------------------
    # Step 6 — Post-scheduling validation
    # -----------------------------------------------------------------------

    def _validate(
        self,
        flat_topics: List[FlatTopic],
        study_days: List[StudyDay],
        revision_days: List[StudyDay],
        target_days: int,
    ) -> None:
        """
        Invariant checks. Raises AssertionError on violation (should never
        trigger in normal operation — guards against algorithm bugs).
        """
        all_days = study_days + revision_days

        # 1. Total day count matches targetDays
        assert len(all_days) == target_days, (
            f"Day count mismatch: got {len(all_days)}, expected {target_days}"
        )

        # 2. Day numbers are sequential starting from 1
        for idx, day in enumerate(all_days):
            assert day.dayNumber == idx + 1, (
                f"Day number gap: day at index {idx} has dayNumber={day.dayNumber}"
            )

        # 3. All scheduled topic names exist in the input syllabus
        valid_names = {t.topic_name for t in flat_topics}
        for day in study_days:
            for scheduled_topic in day.topics:
                assert scheduled_topic.topicName in valid_names, (
                    f"Invented topic detected: '{scheduled_topic.topicName}' "
                    f"is not in the syllabus"
                )

        # 4. No topic duplicated in the study phase
        seen: List[str] = []
        for day in study_days:
            for scheduled_topic in day.topics:
                key = f"{scheduled_topic.unitNumber}::{scheduled_topic.topicName}"
                assert key not in seen, (
                    f"Duplicate topic in study phase: '{scheduled_topic.topicName}'"
                )
                seen.append(key)

        # 5. Every study-phase topic from the syllabus is present exactly once
        all_study_topic_names = [
            st.topicName
            for day in study_days
            for st in day.topics
        ]
        for ft in flat_topics:
            assert ft.topic_name in all_study_topic_names, (
                f"Missing topic in study plan: '{ft.topic_name}'"
            )

        # 6. Syllabus order preserved in study days
        #    (the index of each topic in flat_topics must be non-decreasing)
        flat_index_map = {t.topic_name: i for i, t in enumerate(flat_topics)}
        last_idx = -1
        for day in study_days:
            for st in day.topics:
                idx = flat_index_map.get(st.topicName, -1)
                assert idx >= last_idx, (
                    f"Syllabus order violated: '{st.topicName}' appears out of order"
                )
                last_idx = idx

        logger.info(
            f"[Planner] Validation passed: {len(all_days)} days, "
            f"{len(seen)} unique study topics."
        )


# Module-level singleton
study_planner = StudyPlanner()
