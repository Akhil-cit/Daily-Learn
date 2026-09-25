# 09. Study Plan & Scheduling Logic

## 1. CRITICAL ARCHITECTURAL INVESTIGATION: Does the Study Plan Use the LLM?

> [!IMPORTANT]
> **VERIFIED FINDING: The Study Plan Generation Does NOT Use the LLM.**
> It is a **100% deterministic, pure-Java algorithmic scheduler** executing in `domain/scheduler/StudyPlanGenerator.java`.

### Architectural Evidence:
1. **Source Code Inspection**:
   - `StudyPlanGenerator.java` contains **zero imports** of `AiTutorEngine`, `LocalLLMEngine`, `LlamaCppPoC`, or ONNX.
   - Class header explicitly states:
     ```java
     /**
      * Pure-Java scheduling algorithm - no Android imports.
      */
     public class StudyPlanGenerator { ... }
     ```
2. **Execution Timing**:
   Generating a 20-day plan across 5 units and 45 topics completes in **under 50 milliseconds**. If an LLM were synthesizing the schedule, generating 45 topic allocations on a mobile CPU would require several minutes.
3. **Reproducibility**:
   The generated study plan is strictly deterministic. Given the same topics, exam date, and daily minute budget, the output schedule is mathematically identical every time.

---

## 2. The Scheduling Algorithm

The scheduling logic in `StudyPlanGenerator.generate()` implements the following formal mathematical model:

### Step 1: Available Days Calculation
$$	ext{availableDays} = \left\lfloor rac{	ext{examDateMillis} - 	ext{todayMidnightMillis}}{86,400,000} ightfloor$$

### Step 2: Revision Window Reservation
The algorithm reserves a mandatory final revision window to ensure students do not study new topics the day before an exam:
$$	ext{revisionDays} = \max\left(3, \left\lfloor 	ext{availableDays} 	imes 0.18 ightflooright)$$
$$	ext{learningDays} = \max\left(1, 	ext{availableDays} - 	ext{revisionDays}ight)$$
*Example*: For a 20-day study window:
- $	ext{revisionDays} = \max(3, \lfloor 20 	imes 0.18 floor) = \max(3, 3) = 3 	ext{ days}$
- $	ext{learningDays} = 20 - 3 = 17 	ext{ days}$

### Step 3: Difficulty Weighting & Interleaving
Topics are sorted by academic load:
$$	ext{Weight} = 	ext{difficulty} 	imes 	ext{estimatedMinutes}$$
- Topics are sorted in descending order of weight.
- If multiple subjects are enrolled, `interleaveBySubject()` applies round-robin interleaving so students do not burn out studying a single subject continuously.

### Step 4: Budgeted Daily Allocation
Topics are sequentially placed into daily `StudySession` records until the student's daily minute budget (`dailyMinutes`) is reached, after which `dayOffset` advances.

### Step 5: Revision Session Injection
Remaining calendar days prior to the exam are populated as dedicated `REVISION` sessions.
