import asyncio
import json
import os
import sys
import argparse
from pathlib import Path
import pymupdf as fitz

# Add backend directory to sys.path
BASE_DIR = Path(__file__).resolve().parent
sys.path.insert(0, str(BASE_DIR))

from logger import logger
from services.pdf_extractor import extract_text_from_pdf
from services.image_ocr import extract_text_from_image, check_tesseract_installed
from services.ollama_client import ollama_client
from services.syllabus_analyzer import syllabus_analyzer


def generate_sample_syllabus_pdf(output_path: Path) -> Path:
    """
    Generates a realistic sample Object Oriented Programming syllabus PDF
    containing academic curriculum sections, lecture hours, and non-academic noise
    (faculty info, reference books, evaluation scheme) to verify filtering.
    """
    output_path.parent.mkdir(parents=True, exist_ok=True)
    doc = fitz.open()

    # Page 1: Course header, Faculty info, Unit 1, Unit 2
    page1 = doc.new_page()
    page1_text = """DEPARTMENT OF COMPUTER SCIENCE & ENGINEERING
SYLLABUS: OBJECT ORIENTED PROGRAMMING
Course Code: 25SS212 | Credits: 4 | Total Hours: 36

--------------------------------------------------------------------------------
FACULTY INFORMATION (Office Hours: Mon/Wed 2-4 PM)
Instructor: Dr. Sarah Jenkins (Email: sjenkins@univ.edu, Phone: +1-555-0199, Room: CS-402)
Teaching Assistant: Alex Rivera (Email: arivera@univ.edu)

COURSE OBJECTIVES:
1. To understand the fundamental concepts of OOP.
2. To design and implement robust C++ programs.

--------------------------------------------------------------------------------
UNIT 1: PRINCIPLES OF OBJECT ORIENTED PROGRAMMING [6 Hours]
- Software Crisis and Software Evolution
- Procedure-Oriented vs Object-Oriented Programming Paradigm
- Basic Concepts of OOP: Objects, Classes, Data Abstraction, Encapsulation, Inheritance, Polymorphism
- Benefits and Applications of OOP

UNIT 2: FUNCTIONS AND OBJECTS IN C++ [8 Hours]
- Function Prototyping and Inline Functions
- Default Arguments and Const Arguments
- Function Overloading
- Classes and Objects, Specifying a Class, Defining Member Functions
- Static Data Members and Static Member Functions
- Arrays of Objects, Objects as Function Arguments, Friendly Functions
"""
    page1.insert_text((50, 50), page1_text, fontsize=10, fontname="helv")

    # Page 2: Unit 3, Unit 4, Unit 5, Reference Books, Grading
    page2 = doc.new_page()
    page2_text = """UNIT 3: CONSTRUCTORS AND DESTRUCTORS [6 Hours]
- Constructors: Default, Parameterized, and Copy Constructors
- Multiple Constructors in a Class and Dynamic Initialization of Objects
- Dynamic Constructors
- Destructors

UNIT 4: INHERITANCE AND POLYMORPHISM [8 Hours]
- Defining Derived Classes and Single Inheritance
- Multilevel Inheritance, Multiple Inheritance, Hierarchical Inheritance, Hybrid Inheritance
- Virtual Base Classes and Abstract Classes
- Pointers to Objects and Pointers to Derived Classes
- Virtual Functions and Pure Virtual Functions

UNIT 5: STREAMS, FILES AND TEMPLATES [8 Hours]
- C++ Streams and C++ Stream Classes
- Formatted and Unformatted Console I/O Operations
- Classes for File Stream Operations, Opening and Closing a File, File Modes, File Pointers
- Function Templates and Class Templates
- Exception Handling: try, throw, and catch blocks

--------------------------------------------------------------------------------
EVALUATION SCHEME:
- Internal Assessment / Quizzes: 20%
- Mid-Term Examination: 30%
- End-Semester Practical & Theory Exam: 50%

TEXTBOOKS & REFERENCES:
1. E. Balagurusamy, "Object Oriented Programming with C++", 8th Edition, McGraw Hill Education.
2. Bjarne Stroustrup, "The C++ Programming Language", 4th Edition, Addison-Wesley.
3. Robert Lafore, "Object-Oriented Programming in C++", 4th Edition, Sams Publishing.
"""
    page2.insert_text((50, 50), page2_text, fontsize=10, fontname="helv")

    doc.save(str(output_path))
    doc.close()
    logger.info(f"[TEST] Generated sample PDF at: {output_path}")
    return output_path


def generate_sample_syllabus_image(output_path: Path) -> Path:
    """
    Renders the sample syllabus PDF Page 1 into a high-resolution JPG/PNG image
    for OCR testing.
    """
    sample_pdf = output_path.parent / "sample_oop_syllabus.pdf"
    if not sample_pdf.exists():
        generate_sample_syllabus_pdf(sample_pdf)

    doc = fitz.open(str(sample_pdf))
    page = doc.load_page(0)  # Page 1
    pix = page.get_pixmap(dpi=200)  # High resolution for crisp OCR
    pix.save(str(output_path))
    doc.close()
    logger.info(f"[TEST] Generated sample syllabus image at: {output_path}")
    return output_path


async def run_test(file_path: Path):
    """
    Executes the full pipeline for a PDF or Image:
    1. Check Ollama connectivity
    2. Check OCR availability (if image)
    3. Extract text (PyMuPDF for PDF or Tesseract for Image)
    4. Analyze with Ollama llama3.2:3b
    5. Validate and print structured JSON
    """
    print("\n" + "=" * 60)
    print("DAILY LEARN - LOCAL AI SYLLABUS ENGINE")
    print("=" * 60)

    # 1. Health check for Ollama
    print("\n[1/4] Checking Ollama Connection & Model...")
    health = await ollama_client.check_health()
    print(f"  - Ollama Status: {health.ollama}")
    print(f"  - Target Model : {health.model}")
    print(f"  - Model Ready  : {health.available}")

    if not health.available and health.ollama != "connected":
        print(f"\n[ERROR] Ollama is not accessible at {ollama_client.base_url}")
        print("Please start Ollama using: 'ollama serve' and ensure model is pulled: 'ollama pull llama3.2:3b'")
        return

    # 2. Check file type and extract text
    ext = file_path.suffix.lower()
    print(f"\n[2/4] Extracting text from document ({ext}): {file_path.name}...")

    if ext == ".pdf":
        pages = extract_text_from_pdf(file_path, source_name=file_path.name)
    elif ext in (".jpg", ".jpeg", ".png"):
        ocr_status = check_tesseract_installed()
        print(f"  - Tesseract OCR: {'Installed (' + str(ocr_status.get('version')) + ')' if ocr_status['installed'] else 'NOT FOUND'}")
        if not ocr_status["installed"]:
            print(f"  [ERROR] {ocr_status.get('error')}")
            return
        pages = extract_text_from_image(file_path, source_name=file_path.name)
    else:
        print(f"  [ERROR] Unsupported file extension: {ext}")
        return

    print(f"  - Total Pages/Sections: {len(pages)}")
    for p in pages:
        print(f"  - Page {p.page}: {p.charCount} characters extracted")

    # 3. Analyze syllabus with Ollama
    print(f"\n[3/4] Analyzing Syllabus with Ollama ({ollama_client.model})...")
    print("  - Sending prompt + syllabus context...")
    syllabus = await syllabus_analyzer.analyze(pages)

    # 4. Results Display
    print("\n[4/4] Extraction Results:")
    print("=" * 60)
    print(f"Course Name: {syllabus.courseName}")
    print(f"Course Code: {syllabus.courseCode}")
    print(f"Total Units: {len(syllabus.units)}")
    print("-" * 60)

    for unit in syllabus.units:
        hours_str = f"{unit.lectureHours} hrs" if unit.lectureHours is not None else "No hours specified"
        print(f"\nUnit {unit.unitNumber}: {unit.unitName} ({hours_str})")
        for topic in unit.topics:
            pages_str = f"[Page {', '.join(map(str, topic.sourcePages))}]" if topic.sourcePages else ""
            print(f"   - {topic.name} {pages_str}")
            for sub in topic.subtopics:
                print(f"       * {sub}")

    print("\n" + "=" * 60)
    print("RAW STRUCTURED JSON:")
    print("=" * 60)
    print(json.dumps(syllabus.model_dump(), indent=2))
    print("\n>>> TEST COMPLETED SUCCESSFULLY! <<<")


def main():
    parser = argparse.ArgumentParser(description="Test Daily Learn Local Syllabus Analyzer")
    parser.add_argument("--pdf", type=str, default=None, help="Path to a PDF syllabus file")
    parser.add_argument("--image", type=str, default=None, help="Path to a JPG/JPEG/PNG syllabus image")
    args = parser.parse_args()

    if args.pdf:
        target_path = Path(args.pdf)
        if not target_path.exists():
            print(f"Error: Specified PDF file does not exist: {target_path}")
            sys.exit(1)
    elif args.image:
        target_path = Path(args.image)
        if not target_path.exists():
            print(f"Error: Specified image file does not exist: {target_path}")
            sys.exit(1)
    else:
        # Default test: create and run sample PDF
        sample_path = BASE_DIR / "data" / "uploads" / "sample_oop_syllabus.pdf"
        target_path = generate_sample_syllabus_pdf(sample_path)

    asyncio.run(run_test(target_path))


if __name__ == "__main__":
    main()
