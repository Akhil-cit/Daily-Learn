import sys
from pathlib import Path
from fastapi.testclient import TestClient

# Ensure backend root is on sys.path
BASE_DIR = Path(__file__).resolve().parent
sys.path.insert(0, str(BASE_DIR))

from main import app
from test_analyzer import generate_sample_syllabus_pdf, generate_sample_syllabus_image
from services.image_ocr import check_tesseract_installed

client = TestClient(app)


def test_health():
    """Verify /health endpoint returns status ok"""
    response = client.get("/health")
    assert response.status_code == 200
    data = response.json()
    assert data["status"] == "ok"
    print("[PASS] GET /health test passed")


def test_health_ollama():
    """Verify /health/ollama endpoint checks local Ollama"""
    response = client.get("/health/ollama")
    assert response.status_code == 200
    data = response.json()
    assert "ollama" in data
    assert "model" in data
    print(f"[PASS] GET /health/ollama test passed (status={data['ollama']}, model={data['model']})")


def test_health_ocr():
    """Verify /health/ocr endpoint checks local Tesseract OCR"""
    response = client.get("/health/ocr")
    assert response.status_code == 200
    data = response.json()
    assert "ocr" in data
    assert "installed" in data
    print(f"[PASS] GET /health/ocr test passed (ocr={data['ocr']}, installed={data['installed']}, version={data.get('version')})")


def test_analyze_endpoint_invalid_file():
    """Verify uploading unsupported file type returns 400 Bad Request"""
    response = client.post(
        "/api/v1/syllabus/analyze",
        files={"file": ("syllabus.docx", b"Mock docx content", "application/vnd.openxmlformats-officedocument.wordprocessingml.document")}
    )
    assert response.status_code == 400
    data = response.json()
    assert "Invalid file format" in data["detail"]
    assert "PDF, JPG, JPEG, PNG" in data["detail"]
    print("[PASS] POST /api/v1/syllabus/analyze invalid file test passed")


def test_analyze_endpoint_valid_pdf():
    """Verify uploading a valid syllabus PDF returns structured syllabus JSON"""
    sample_pdf_path = BASE_DIR / "data" / "uploads" / "sample_oop_syllabus.pdf"
    if not sample_pdf_path.exists():
        generate_sample_syllabus_pdf(sample_pdf_path)

    with open(sample_pdf_path, "rb") as f:
        response = client.post(
            "/api/v1/syllabus/analyze",
            files={"file": ("sample_oop_syllabus.pdf", f, "application/pdf")}
        )

    assert response.status_code == 200, f"Error: {response.text}"
    data = response.json()
    assert data["success"] is True
    assert "data" in data
    syllabus = data["data"]
    assert "courseName" in syllabus
    assert len(syllabus["units"]) > 0
    print(f"[PASS] POST /api/v1/syllabus/analyze valid PDF test passed (course='{syllabus['courseName']}', units={len(syllabus['units'])})")


def test_analyze_endpoint_valid_jpg():
    """Verify uploading a valid syllabus JPG returns structured syllabus JSON"""
    ocr_status = check_tesseract_installed()
    if not ocr_status["installed"]:
        print("[SKIP] POST /api/v1/syllabus/analyze JPG test skipped (Tesseract not installed on server)")
        return

    sample_jpg_path = BASE_DIR / "data" / "uploads" / "sample_oop_syllabus.jpg"
    generate_sample_syllabus_image(sample_jpg_path)

    with open(sample_jpg_path, "rb") as f:
        response = client.post(
            "/api/v1/syllabus/analyze",
            files={"file": ("sample_oop_syllabus.jpg", f, "image/jpeg")}
        )

    assert response.status_code == 200, f"Error: {response.text}"
    data = response.json()
    assert data["success"] is True
    syllabus = data["data"]
    assert "courseName" in syllabus
    assert len(syllabus["units"]) > 0
    # Verify sourcePages has 1
    for unit in syllabus["units"]:
        for topic in unit["topics"]:
            if topic["sourcePages"]:
                assert 1 in topic["sourcePages"]
    print(f"[PASS] POST /api/v1/syllabus/analyze valid JPG test passed (course='{syllabus['courseName']}', units={len(syllabus['units'])})")


def test_analyze_endpoint_valid_png():
    """Verify uploading a valid syllabus PNG returns structured syllabus JSON"""
    ocr_status = check_tesseract_installed()
    if not ocr_status["installed"]:
        print("[SKIP] POST /api/v1/syllabus/analyze PNG test skipped (Tesseract not installed on server)")
        return

    sample_png_path = BASE_DIR / "data" / "uploads" / "sample_oop_syllabus.png"
    generate_sample_syllabus_image(sample_png_path)

    with open(sample_png_path, "rb") as f:
        response = client.post(
            "/api/v1/syllabus/analyze",
            files={"file": ("sample_oop_syllabus.png", f, "image/png")}
        )

    assert response.status_code == 200, f"Error: {response.text}"
    data = response.json()
    assert data["success"] is True
    syllabus = data["data"]
    assert "courseName" in syllabus
    assert len(syllabus["units"]) > 0
    print(f"[PASS] POST /api/v1/syllabus/analyze valid PNG test passed (course='{syllabus['courseName']}', units={len(syllabus['units'])})")


if __name__ == "__main__":
    print("Running API endpoint tests...")
    test_health()
    test_health_ollama()
    test_health_ocr()
    test_analyze_endpoint_invalid_file()
    test_analyze_endpoint_valid_pdf()
    test_analyze_endpoint_valid_jpg()
    test_analyze_endpoint_valid_png()
    print("\nALL API TESTS COMPLETED!")
