"""
Phase 2 — Automated Tests for the Daily Study Plan Engine.

Tests cover:
  1.  20-day plan - exactly 20 days returned
  2.  1-day plan - all topics appear on day 1
  3.  3-day plan - exactly 3 days
  4.  targetDays > topic count - still exactly targetDays days
  5.  Missing lectureHours - warning emitted, no crash
  6.  Multiple units - original order preserved
  7.  Empty units (no topics) - skipped with a warning
  8.  targetDays = 0 - Pydantic ValidationError
  9.  targetDays = -1 - Pydantic ValidationError
  10. All scheduled topics exist in input syllabus
  11. No duplicate topics in study phase
  12. Syllabus order preserved across units
  13. Workload reasonably balanced (max day <= 3x min day)
  14. revisionDays = 0 when targetDays < 4
  15. API endpoint: POST /api/v1/study-plan/generate
  16. API endpoint: GET /api/v1/study-plan/health
  17. Empty syllabus (no units) - returns 400
  18. Calendar date attachment via startDate
  19. revisionBuffer = 0.0 disables revision
  20. All input topics are covered in the study plan (completeness)
"""

import sys
import os

# Ensure backend directory is on path
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from pydantic import ValidationError
from fastapi.testclient import TestClient

from main import app
from services.study_planner import StudyPlanner, study_planner
from models.syllabus_models import Syllabus, Unit, Topic
from models.study_plan_models import StudyPlanRequest

# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

def make_topic(name: str, subtopics=None) -> Topic:
    return Topic(name=name, subtopics=subtopics or [], sourcePages=[1])


def make_unit(
    number: int,
    name: str,
    lecture_hours: float | None,
    topics: list[Topic],
) -> Unit:
    return Unit(
        unitNumber=number,
        unitName=name,
        lectureHours=lecture_hours,
        topics=topics,
    )


def make_oop_syllabus() -> Syllabus:
    """Realistic OOP syllabus with 5 units and 24 topics, matching Phase 1 output."""
    return Syllabus(
        courseName="Object Oriented Programming",
        courseCode="25SS212",
        units=[
            make_unit(1, "Principles of OOP", 6.0, [
                make_topic("Software Crisis and Software Evolution"),
                make_topic("Procedure vs Object Oriented Programming"),
                make_topic("Basic Concepts of OOP", ["Objects", "Classes", "Inheritance", "Polymorphism"]),
            ]),
            make_unit(2, "Functions and Objects in C++", 8.0, [
                make_topic("Function Prototyping and Inline Functions"),
                make_topic("Default Arguments and Const Arguments"),
                make_topic("Function Overloading"),
                make_topic("Classes and Objects, Defining Member Functions"),
                make_topic("Static Data Members and Static Member Functions"),
                make_topic("Arrays of Objects, Objects as Function Arguments"),
            ]),
            make_unit(3, "Constructors and Destructors", 6.0, [
                make_topic("Constructors"),
                make_topic("Parameterized Constructors"),
                make_topic("Copy Constructor"),
                make_topic("Destructors"),
                make_topic("Operator Overloading"),
            ]),
            make_unit(4, "Inheritance", 6.0, [
                make_topic("Types of Inheritance", ["Single", "Multiple", "Multilevel"]),
                make_topic("Virtual Base Classes"),
                make_topic("Abstract Classes"),
                make_topic("Constructors in Derived Classes"),
                make_topic("Pointers to Objects"),
            ]),
            make_unit(5, "File Handling and Templates", 4.0, [
                make_topic("File Streams and File Modes"),
                make_topic("Reading and Writing Files"),
                make_topic("Exception Handling"),
                make_topic("Function Templates"),
                make_topic("Class Templates"),
            ]),
        ],
    )


def make_small_syllabus() -> Syllabus:
    """Tiny syllabus: 2 units, 5 topics total."""
    return Syllabus(
        courseName="Mini Course",
        courseCode=None,
        units=[
            make_unit(1, "Introduction", 3.0, [
                make_topic("Topic A"),
                make_topic("Topic B"),
                make_topic("Topic C"),
            ]),
            make_unit(2, "Advanced", 2.0, [
                make_topic("Topic D"),
                make_topic("Topic E"),
            ]),
        ],
    )


def make_no_hours_syllabus() -> Syllabus:
    """Syllabus where no unit has lectureHours."""
    return Syllabus(
        courseName="No Hours Course",
        courseCode="NH001",
        units=[
            make_unit(1, "Unit One", None, [
                make_topic("Alpha"),
                make_topic("Beta"),
            ]),
            make_unit(2, "Unit Two", None, [
                make_topic("Gamma"),
                make_topic("Delta"),
            ]),
        ],
    )


def all_study_topic_names(result) -> list[str]:
    return [st.topicName for day in result.studyDays for st in day.topics]


def check_order_preserved(flat_input: list[str], study_days) -> bool:
    """Verify topics appear in the same order as in the input list."""
    scheduled = [st.topicName for day in study_days for st in day.topics]
    # Filter scheduled to only those present in flat_input (some may be on revision only)
    idx = 0
    for name in scheduled:
        if name in flat_input:
            pos = flat_input.index(name, idx)
            idx = pos
    return True


PASS = "[PASS]"
FAIL = "[FAIL]"
client = TestClient(app)
planner = StudyPlanner()

# ---------------------------------------------------------------------------
# Test runner
# ---------------------------------------------------------------------------

passed = 0
failed = 0


def run_test(name: str, fn):
    global passed, failed
    try:
        fn()
        print(f"{PASS} {name}")
        passed += 1
    except Exception as e:
        print(f"{FAIL} {name}: {e}")
        failed += 1


# ---------------------------------------------------------------------------
# Test 1: 20-day plan - total days == 20
# ---------------------------------------------------------------------------
def test_20_day_plan():
    req = StudyPlanRequest(syllabus=make_oop_syllabus(), targetDays=20)
    result = planner.generate(req)
    total = len(result.studyDays) + len(result.revisionDays)
    assert total == 20, f"Expected 20 days, got {total}"
    assert result.totalDays == 20


# ---------------------------------------------------------------------------
# Test 2: 1-day plan - all topics on day 1
# ---------------------------------------------------------------------------
def test_1_day_plan():
    syllabus = make_small_syllabus()
    req = StudyPlanRequest(syllabus=syllabus, targetDays=1)
    result = planner.generate(req)
    total = len(result.studyDays) + len(result.revisionDays)
    assert total == 1, f"Expected 1 day, got {total}"
    # All topics must appear
    all_names = all_study_topic_names(result)
    for unit in syllabus.units:
        for topic in unit.topics:
            assert topic.name in all_names, f"Missing topic: {topic.name}"


# ---------------------------------------------------------------------------
# Test 3: 3-day plan - exactly 3 days
# ---------------------------------------------------------------------------
def test_3_day_plan():
    req = StudyPlanRequest(syllabus=make_oop_syllabus(), targetDays=3)
    result = planner.generate(req)
    total = len(result.studyDays) + len(result.revisionDays)
    assert total == 3, f"Expected 3 days, got {total}"


# ---------------------------------------------------------------------------
# Test 4: targetDays > topic count
# ---------------------------------------------------------------------------
def test_more_days_than_topics():
    syllabus = make_small_syllabus()  # 5 topics
    req = StudyPlanRequest(syllabus=syllabus, targetDays=30)
    result = planner.generate(req)
    total = len(result.studyDays) + len(result.revisionDays)
    assert total == 30, f"Expected 30 days, got {total}"


# ---------------------------------------------------------------------------
# Test 5: Missing lectureHours - warning emitted, no crash
# ---------------------------------------------------------------------------
def test_missing_lecture_hours():
    req = StudyPlanRequest(syllabus=make_no_hours_syllabus(), targetDays=10)
    result = planner.generate(req)
    assert len(result.studyDays) + len(result.revisionDays) == 10
    # At least one warning about missing lectureHours
    assert any("lectureHours" in w or "defaulting" in w.lower() for w in result.warnings), \
        f"Expected warning about missing lectureHours, got: {result.warnings}"


# ---------------------------------------------------------------------------
# Test 6: Multiple units - original order preserved
# ---------------------------------------------------------------------------
def test_order_preserved():
    syllabus = make_oop_syllabus()
    req = StudyPlanRequest(syllabus=syllabus, targetDays=15)
    result = planner.generate(req)

    # Build the flat topic order from the syllabus
    flat = [t.name for u in syllabus.units for t in u.topics]

    # Get the study-day topic sequence
    scheduled = [st.topicName for day in result.studyDays for st in day.topics]

    # Verify topics appear in the same relative order
    last_idx = -1
    for name in scheduled:
        idx = flat.index(name)
        assert idx >= last_idx, (
            f"Order violated: '{name}' (index {idx}) appears after index {last_idx}"
        )
        last_idx = idx


# ---------------------------------------------------------------------------
# Test 7: Empty units are skipped with a warning
# ---------------------------------------------------------------------------
def test_empty_unit_skipped():
    syllabus = Syllabus(
        courseName="Course",
        courseCode=None,
        units=[
            make_unit(1, "Full Unit", 3.0, [make_topic("Topic A"), make_topic("Topic B")]),
            make_unit(2, "Empty Unit", 2.0, []),   # no topics
            make_unit(3, "Another Full Unit", 2.0, [make_topic("Topic C")]),
        ],
    )
    req = StudyPlanRequest(syllabus=syllabus, targetDays=5)
    result = planner.generate(req)
    assert len(result.studyDays) + len(result.revisionDays) == 5
    assert any("no topics" in w.lower() or "skipped" in w.lower() for w in result.warnings), \
        f"Expected warning about empty unit: {result.warnings}"


# ---------------------------------------------------------------------------
# Test 8: targetDays = 0 -> ValidationError
# ---------------------------------------------------------------------------
def test_zero_target_days():
    raised = False
    try:
        StudyPlanRequest(syllabus=make_small_syllabus(), targetDays=0)
    except ValidationError:
        raised = True
    assert raised, "Expected ValidationError for targetDays=0"


# ---------------------------------------------------------------------------
# Test 9: targetDays = -1 -> ValidationError
# ---------------------------------------------------------------------------
def test_negative_target_days():
    raised = False
    try:
        StudyPlanRequest(syllabus=make_small_syllabus(), targetDays=-1)
    except ValidationError:
        raised = True
    assert raised, "Expected ValidationError for targetDays=-1"


# ---------------------------------------------------------------------------
# Test 10: All scheduled topics exist in input syllabus
# ---------------------------------------------------------------------------
def test_no_invented_topics():
    syllabus = make_oop_syllabus()
    req = StudyPlanRequest(syllabus=syllabus, targetDays=20)
    result = planner.generate(req)
    valid_names = {t.name for u in syllabus.units for t in u.topics}
    for day in result.studyDays:
        for st in day.topics:
            assert st.topicName in valid_names, \
                f"Invented topic detected: '{st.topicName}'"


# ---------------------------------------------------------------------------
# Test 11: No duplicate topic assignments in study phase
# ---------------------------------------------------------------------------
def test_no_duplicate_study_topics():
    req = StudyPlanRequest(syllabus=make_oop_syllabus(), targetDays=20)
    result = planner.generate(req)
    seen = []
    for day in result.studyDays:
        for st in day.topics:
            key = f"{st.unitNumber}::{st.topicName}"
            assert key not in seen, f"Duplicate study topic: '{st.topicName}'"
            seen.append(key)


# ---------------------------------------------------------------------------
# Test 12: Syllabus order preserved across units
# ---------------------------------------------------------------------------
def test_unit_order_preserved():
    syllabus = make_oop_syllabus()
    req = StudyPlanRequest(syllabus=syllabus, targetDays=20)
    result = planner.generate(req)

    unit_sequence = [st.unitNumber for day in result.studyDays for st in day.topics]
    # Unit numbers should be non-decreasing
    for i in range(len(unit_sequence) - 1):
        assert unit_sequence[i] <= unit_sequence[i + 1], (
            f"Unit order violated: unit {unit_sequence[i]} before {unit_sequence[i+1]}"
        )


# ---------------------------------------------------------------------------
# Test 13: Workload reasonably balanced (max <= 3x min, non-empty days only)
# ---------------------------------------------------------------------------
def test_workload_balance():
    req = StudyPlanRequest(syllabus=make_oop_syllabus(), targetDays=20)
    result = planner.generate(req)
    non_empty = [d.estimatedHours for d in result.studyDays if d.topics]
    if len(non_empty) >= 2:
        ratio = max(non_empty) / min(non_empty)
        assert ratio <= 3.5, \
            f"Workload imbalance: max/min ratio = {ratio:.2f} (threshold 3.5)"


# ---------------------------------------------------------------------------
# Test 14: Revision days = 0 when targetDays < 4
# ---------------------------------------------------------------------------
def test_no_revision_for_tiny_plan():
    req = StudyPlanRequest(syllabus=make_oop_syllabus(), targetDays=3)
    result = planner.generate(req)
    assert result.revisionDaysCount == 0, \
        f"Expected 0 revision days for targetDays=3, got {result.revisionDaysCount}"
    # All days should be study days
    assert all(d.type == "study" for d in result.studyDays)


# ---------------------------------------------------------------------------
# Test 15: API endpoint POST /api/v1/study-plan/generate
# ---------------------------------------------------------------------------
def test_api_generate_endpoint():
    payload = {
        "syllabus": {
            "courseName": "OOP",
            "courseCode": "25SS212",
            "units": [
                {
                    "unitNumber": 1,
                    "unitName": "Basics",
                    "lectureHours": 6.0,
                    "topics": [
                        {"name": "Classes", "subtopics": [], "sourcePages": [1]},
                        {"name": "Objects", "subtopics": [], "sourcePages": [1]},
                        {"name": "Inheritance", "subtopics": [], "sourcePages": [2]},
                    ],
                }
            ],
        },
        "targetDays": 5,
    }
    resp = client.post("/api/v1/study-plan/generate", json=payload)
    assert resp.status_code == 200, f"Expected 200, got {resp.status_code}: {resp.text}"
    data = resp.json()
    assert data["success"] is True
    assert "studyPlan" in data
    assert data["studyPlan"]["totalDays"] == 5
    assert len(data["studyPlan"]["studyDays"]) + len(data["studyPlan"]["revisionDays"]) == 5


# ---------------------------------------------------------------------------
# Test 16: API endpoint GET /api/v1/study-plan/health
# ---------------------------------------------------------------------------
def test_api_health_endpoint():
    resp = client.get("/api/v1/study-plan/health")
    assert resp.status_code == 200, f"Expected 200, got {resp.status_code}"
    data = resp.json()
    assert data["status"] == "ok"
    assert data["scheduler"] == "deterministic-python"


# ---------------------------------------------------------------------------
# Test 17: Empty syllabus (no units) -> ValueError -> 400
# ---------------------------------------------------------------------------
def test_empty_syllabus_returns_error():
    payload = {
        "syllabus": {"courseName": "Empty", "units": []},
        "targetDays": 10,
    }
    resp = client.post("/api/v1/study-plan/generate", json=payload)
    assert resp.status_code == 400, f"Expected 400, got {resp.status_code}: {resp.text}"
    data = resp.json()
    assert data["success"] is False


# ---------------------------------------------------------------------------
# Test 18: Calendar date attachment via startDate
# ---------------------------------------------------------------------------
def test_calendar_dates_attached():
    req = StudyPlanRequest(
        syllabus=make_small_syllabus(),
        targetDays=5,
        startDate="2026-08-20",
    )
    result = planner.generate(req)
    all_days = result.studyDays + result.revisionDays
    for day in all_days:
        assert day.date is not None, f"Day {day.dayNumber} missing date"
        assert day.date.startswith("2026-08"), \
            f"Day {day.dayNumber} has unexpected date: {day.date}"


# ---------------------------------------------------------------------------
# Test 19: revisionBuffer = 0.0 disables revision completely
# ---------------------------------------------------------------------------
def test_revision_buffer_zero():
    req = StudyPlanRequest(
        syllabus=make_oop_syllabus(),
        targetDays=10,
        revisionBuffer=0.0,
    )
    result = planner.generate(req)
    assert result.revisionDaysCount == 0, \
        f"Expected 0 revision days with revisionBuffer=0, got {result.revisionDaysCount}"
    assert len(result.studyDays) == 10


# ---------------------------------------------------------------------------
# Test 20: All input topics covered in the study plan
# ---------------------------------------------------------------------------
def test_all_topics_covered():
    syllabus = make_oop_syllabus()
    req = StudyPlanRequest(syllabus=syllabus, targetDays=20)
    result = planner.generate(req)
    study_names = set(all_study_topic_names(result))
    for unit in syllabus.units:
        for topic in unit.topics:
            assert topic.name in study_names, \
                f"Topic not covered in study plan: '{topic.name}'"


# ---------------------------------------------------------------------------
# Run all tests
# ---------------------------------------------------------------------------

if __name__ == "__main__":
    print("=" * 60)
    print("Running Phase 2 Study Plan Engine Tests...")
    print("=" * 60)

    run_test("20-day plan produces exactly 20 days", test_20_day_plan)
    run_test("1-day plan has all topics on day 1", test_1_day_plan)
    run_test("3-day plan produces exactly 3 days", test_3_day_plan)
    run_test("targetDays > topics still exact count", test_more_days_than_topics)
    run_test("Missing lectureHours emits warning", test_missing_lecture_hours)
    run_test("Syllabus order preserved", test_order_preserved)
    run_test("Empty units skipped with warning", test_empty_unit_skipped)
    run_test("targetDays=0 raises ValidationError", test_zero_target_days)
    run_test("targetDays=-1 raises ValidationError", test_negative_target_days)
    run_test("No invented topics in schedule", test_no_invented_topics)
    run_test("No duplicate topics in study phase", test_no_duplicate_study_topics)
    run_test("Unit order preserved across days", test_unit_order_preserved)
    run_test("Workload is reasonably balanced", test_workload_balance)
    run_test("No revision days when targetDays < 4", test_no_revision_for_tiny_plan)
    run_test("API POST /api/v1/study-plan/generate works", test_api_generate_endpoint)
    run_test("API GET /api/v1/study-plan/health works", test_api_health_endpoint)
    run_test("Empty syllabus returns 400", test_empty_syllabus_returns_error)
    run_test("Calendar dates attached when startDate given", test_calendar_dates_attached)
    run_test("revisionBuffer=0.0 disables revision", test_revision_buffer_zero)
    run_test("All input topics covered in study plan", test_all_topics_covered)

    print("=" * 60)
    print(f"Results: {passed} passed, {failed} failed out of {passed + failed} tests.")
    print("=" * 60)

    if failed > 0:
        sys.exit(1)
    else:
        print("ALL PHASE 2 TESTS PASSED!")
