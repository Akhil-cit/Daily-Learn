"""
Phase 4 — Daily Learn Orchestration Service.

Orchestrates Phase 2 (Study Plan) and Phase 3 (RAG/Ollama) to
produce daily learning content.
"""

from typing import List, Dict

from logger import logger
from models.study_plan_models import ScheduledTopic
from models.rag_models import SourceRef
from models.daily_learn_models import DailyLearnRequest, DailyLearnResponse, LearningContent
from services.study_plan_storage import study_plan_storage
from services.rag_service import rag_service
from services.ollama_client import ollama_client


class DailyLearnService:
    """
    Orchestrates the retrieval of daily study topics, semantic search for syllabus context,
    and LLM generation of learning content.
    """

    async def get_daily_content(self, request: DailyLearnRequest) -> DailyLearnResponse:
        # 1. Fetch study plan
        plan = study_plan_storage.load_plan(request.courseCode)
        if not plan:
            raise ValueError(f"No study plan found for courseCode '{request.courseCode}'. Please generate one first.")

        # 2. Find today's topics
        all_days = plan.studyDays + plan.revisionDays
        # Day numbers are 1-indexed
        day = next((d for d in all_days if d.dayNumber == request.dayNumber), None)
        
        if not day:
            raise ValueError(f"Invalid day number {request.dayNumber}. Plan has {plan.totalDays} days.")

        if not day.topics:
            raise ValueError(f"Day {request.dayNumber} has no scheduled topics.")

        topics = day.topics

        # 3. Retrieve RAG chunks for ALL topics scheduled for this day
        # We will collect unique chunks based on chunkId or text hash to avoid duplicates
        all_hits = []
        seen_texts = set()

        for topic in topics:
            # Create a search query for the topic
            query = f"{topic.topicName}"
            if topic.subtopics:
                query += f" {', '.join(topic.subtopics)}"

            hits = rag_service.search(query, top_k=request.topK)
            for hit in hits:
                if hit.text not in seen_texts:
                    seen_texts.add(hit.text)
                    all_hits.append(hit)

        # Build SourceRefs for the response
        sources_map: Dict[str, SourceRef] = {}
        for hit in all_hits:
            key = f"{hit.metadata.get('unitNumber')}_{hit.metadata.get('topicName')}"
            if key not in sources_map:
                sources_map[key] = SourceRef(
                    unitNumber=hit.metadata.get("unitNumber", 0),
                    unitName=hit.metadata.get("unitName", ""),
                    topicName=hit.metadata.get("topicName", ""),
                    sourcePages=hit.metadata.get("sourcePages", [])
                )

        # 4. Formulate Prompt
        context_parts = []
        for i, hit in enumerate(all_hits, 1):
            context_parts.append(f"[Chunk {i}]\n{hit.text}")
        context = "\n\n".join(context_parts)

        topic_names = [t.topicName for t in topics]
        topics_str = ", ".join(topic_names)

        system_prompt = (
            "You are an expert tutor. Using ONLY the provided syllabus context, "
            f"explain the following topic(s): {topics_str}.\n"
            "Do not invent information outside the syllabus.\n"
            "Format the response in clear, educational language suitable for a student."
        )

        user_message = (
            f"Context:\n{context}\n\n"
            f"Please provide a clear explanation for today's topics: {topics_str}."
        )

        # 5. Query Ollama
        logger.info(f"[DailyLearn] Generating content for Day {request.dayNumber}: {topics_str}")
        try:
            answer = await ollama_client.chat(
                messages=[
                    {"role": "system", "content": system_prompt},
                    {"role": "user", "content": user_message}
                ],
                format="", # Plain text
                temperature=0.3
            )
        except Exception as e:
            logger.error(f"[DailyLearn] Failed to generate content with Ollama: {e}")
            raise RuntimeError(f"Ollama generation failed: {str(e)}")

        # Assemble learning content
        content = LearningContent(
            title=" & ".join(topic_names),
            explanation=answer.strip(),
            keyPoints=[],  # Could optionally use another prompt to extract these, but plain text is fine
            sources=list(sources_map.values())
        )

        return DailyLearnResponse(
            dayNumber=request.dayNumber,
            topics=topics,
            learningContent=content
        )

# Module-level singleton
daily_learn_service = DailyLearnService()
