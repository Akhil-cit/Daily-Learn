"""
Demo script — shows real /rag/search and /rag/ask results.

Runs the full Phase 3 RAG pipeline directly (no HTTP server needed):
  1. Indexes the OOP syllabus
  2. Runs /rag/search for "What is inheritance?"
  3. Runs /rag/ask for "Explain inheritance in OOP" with a 300-second timeout

Usage:
    python demo_rag.py
"""

import sys
import os
import asyncio
import json
import time
from pathlib import Path

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from models.syllabus_models import Syllabus, Unit, Topic
from services.rag_service import rag_service
from services.ollama_client import OllamaClient


def make_oop_syllabus() -> Syllabus:
    return Syllabus(
        courseName="Object Oriented Programming",
        courseCode="25SS212",
        units=[
            Unit(unitNumber=1, unitName="Principles of OOP", lectureHours=6.0, topics=[
                Topic(name="Software Crisis and Software Evolution", subtopics=[], sourcePages=[1]),
                Topic(name="Procedure vs Object Oriented Programming", subtopics=[], sourcePages=[1]),
                Topic(name="Basic Concepts of OOP", subtopics=["Objects", "Classes", "Inheritance", "Polymorphism"], sourcePages=[1]),
            ]),
            Unit(unitNumber=2, unitName="Functions and Objects in C++", lectureHours=8.0, topics=[
                Topic(name="Function Prototyping and Inline Functions", subtopics=[], sourcePages=[2]),
                Topic(name="Default Arguments and Const Arguments", subtopics=[], sourcePages=[2]),
                Topic(name="Function Overloading", subtopics=[], sourcePages=[2]),
                Topic(name="Classes and Objects, Defining Member Functions", subtopics=[], sourcePages=[2]),
                Topic(name="Static Data Members and Static Member Functions", subtopics=[], sourcePages=[2]),
                Topic(name="Arrays of Objects, Objects as Function Arguments", subtopics=[], sourcePages=[2]),
            ]),
            Unit(unitNumber=3, unitName="Constructors and Destructors", lectureHours=6.0, topics=[
                Topic(name="Constructors", subtopics=["Default Constructor", "Parameterized Constructor"], sourcePages=[3]),
                Topic(name="Parameterized Constructors", subtopics=[], sourcePages=[3]),
                Topic(name="Copy Constructor", subtopics=[], sourcePages=[3]),
                Topic(name="Destructors", subtopics=[], sourcePages=[3]),
                Topic(name="Operator Overloading", subtopics=[], sourcePages=[3]),
            ]),
            Unit(unitNumber=4, unitName="Inheritance", lectureHours=6.0, topics=[
                Topic(name="Inheritance", subtopics=["Single", "Multiple", "Multilevel"], sourcePages=[4]),
                Topic(name="Virtual Base Classes", subtopics=[], sourcePages=[4]),
                Topic(name="Abstract Classes", subtopics=[], sourcePages=[4]),
                Topic(name="Constructors in Derived Classes", subtopics=[], sourcePages=[4]),
                Topic(name="Pointers to Objects", subtopics=[], sourcePages=[4]),
            ]),
            Unit(unitNumber=5, unitName="File Handling and Templates", lectureHours=4.0, topics=[
                Topic(name="File Streams and File Modes", subtopics=[], sourcePages=[5]),
                Topic(name="Reading and Writing Files", subtopics=[], sourcePages=[5]),
                Topic(name="Exception Handling", subtopics=[], sourcePages=[5]),
                Topic(name="Function Templates", subtopics=[], sourcePages=[5]),
                Topic(name="Class Templates", subtopics=[], sourcePages=[5]),
            ]),
        ],
    )


def section(title: str):
    print("\n" + "=" * 65)
    print(f"  {title}")
    print("=" * 65)


async def main():
    section("Phase 3 RAG Demo")

    # -------------------------------------------------------------------
    # Step 1 - Index OOP syllabus
    # -------------------------------------------------------------------
    section("Step 1: Index OOP Syllabus")
    syllabus = make_oop_syllabus()
    n = rag_service.index_syllabus(syllabus)
    print(f"  Chunks indexed: {n}")
    print(f"  Embedding model: all-MiniLM-L6-v2 (local, 384-dim)")

    # -------------------------------------------------------------------
    # Step 2 - /rag/search
    # -------------------------------------------------------------------
    section("Step 2: /rag/search -- 'What is inheritance?'")
    hits = rag_service.search("What is inheritance?", top_k=3)
    for i, h in enumerate(hits, 1):
        print(f"\n  Result {i}  score={h.score:.4f}")
        print(f"    Topic    : {h.metadata.get('topicName')}")
        print(f"    Unit     : {h.metadata.get('unitNumber')} -- {h.metadata.get('unitName')}")
        print(f"    Pages    : {h.metadata.get('sourcePages')}")
        print(f"    Subtopics: {h.metadata.get('subtopics')}")

    print("\n  Top result (full JSON):")
    top = hits[0]
    print(json.dumps({
        "score": top.score,
        "topicName": top.metadata.get("topicName"),
        "unitNumber": top.metadata.get("unitNumber"),
        "unitName": top.metadata.get("unitName"),
        "sourcePages": top.metadata.get("sourcePages"),
        "subtopics": top.metadata.get("subtopics"),
        "text": top.text,
    }, indent=4))

    # -------------------------------------------------------------------
    # Step 3 - /rag/ask with 300-second timeout
    # -------------------------------------------------------------------
    section("Step 3: /rag/ask -- 'Explain inheritance in OOP'")

    # Reuse the existing ollama_client pattern; just set a longer timeout
    from services.ollama_client import ollama_client as base_client
    long_client = OllamaClient(
        base_url=base_client.base_url,
        model=base_client.model,
        timeout_seconds=300.0,   # 5 minutes for cold CPU inference
    )

    print("  Checking Ollama availability...")
    health = await long_client.check_health()
    print(f"  Ollama  : {health.ollama}")
    print(f"  Model   : {health.model}")
    print(f"  Ready   : {health.available}")

    if not health.available:
        print("\n  [SKIP] Ollama/llama3.2:3b not available. Skipping ask demo.")
        print("         Run: ollama serve && ollama pull llama3.2:3b")
        return

    hits_ask = rag_service.search("Explain inheritance in OOP", top_k=3)
    context = "\n\n".join(
        f"[Chunk {i}]\n{h.text}" for i, h in enumerate(hits_ask, 1)
    )
    prompt_path = Path(__file__).parent / "prompts" / "rag_prompt.txt"
    template = prompt_path.read_text(encoding="utf-8").strip()
    filled = template.replace("{context}", context).replace("{question}", "Explain inheritance in OOP")

    print("\n  Sending to llama3.2:3b (timeout=300s, this may take 1-3 min on CPU)...")
    t0 = time.time()
    try:
        answer = await long_client.chat(
            messages=[{"role": "user", "content": filled}],
            format="",        # plain text, NOT JSON mode
            temperature=0.3,
        )
        elapsed = time.time() - t0
        print(f"\n  Response received in {elapsed:.1f}s")
        print("\n  --- RAG Answer ---")
        print(answer.strip())
        print("\n  --- Sources used ---")
        for h in hits_ask:
            print(f"    Unit {h.metadata.get('unitNumber')}: {h.metadata.get('topicName')}  "
                  f"(pages {h.metadata.get('sourcePages')})")
    except TimeoutError:
        print("\n  [TIMEOUT] llama3.2:3b did not respond within 300s.")
        print("  Try: ollama run llama3.2:3b 'hello' to warm up the model first.")
    except ConnectionError as e:
        print(f"\n  [ERROR] {e}")

    section("Demo Complete")


if __name__ == "__main__":
    asyncio.run(main())
