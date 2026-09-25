import io
import os
from pathlib import Path
from typing import List, Union, BinaryIO, Dict, Any, Optional
from PIL import Image, ImageOps

from config import settings
from logger import logger
from models.syllabus_models import PageExtractionResult
from services.pdf_extractor import clean_page_text

# Flag for pytesseract
_has_pytesseract = False
try:
    import pytesseract
    _has_pytesseract = True
except ImportError:
    _has_pytesseract = False

# Flag for RapidOCR
_has_rapidocr = False
_rapidocr_engine = None
try:
    from rapidocr_onnxruntime import RapidOCR
    _has_rapidocr = True
except ImportError:
    _has_rapidocr = False


def get_configured_tesseract_path() -> Optional[str]:
    """
    Finds and assigns the Tesseract binary path to pytesseract if available.
    """
    if not _has_pytesseract:
        return None
    exec_path = settings.get_tesseract_executable()
    if exec_path:
        pytesseract.pytesseract.tesseract_cmd = exec_path
    return exec_path


def get_rapidocr_engine():
    """
    Lazily initializes and returns the local RapidOCR engine instance.
    """
    global _rapidocr_engine
    if _rapidocr_engine is None and _has_rapidocr:
        _rapidocr_engine = RapidOCR()
    return _rapidocr_engine


def check_tesseract_installed() -> Dict[str, Any]:
    """
    Checks OCR availability. Reports Tesseract status or local ONNX OCR availability.
    """
    tess_path = get_configured_tesseract_path()
    if tess_path and _has_pytesseract:
        try:
            version = str(pytesseract.get_tesseract_version())
            return {
                "installed": True,
                "engine": "tesseract",
                "path": tess_path,
                "version": version,
                "error": None
            }
        except Exception:
            pass

    if _has_rapidocr:
        return {
            "installed": True,
            "engine": "rapidocr-onnx",
            "path": "builtin/rapidocr-onnxruntime",
            "version": "1.2.3",
            "error": None
        }

    return {
        "installed": False,
        "engine": "none",
        "path": None,
        "version": None,
        "error": "No local OCR engine found. Install Tesseract OCR or rapidocr-onnxruntime."
    }


def preprocess_image(image: Image.Image) -> Image.Image:
    """
    Preprocesses the PIL Image to improve local OCR accuracy:
    - Removes alpha transparency against white background
    - Auto-orients image based on EXIF tag
    - Converts to standard RGB
    """
    try:
        image = ImageOps.exif_transpose(image)
    except Exception:
        pass

    if image.mode in ("RGBA", "LA") or (image.mode == "P" and "transparency" in image.info):
        alpha_image = image.convert("RGBA")
        background = Image.new("RGBA", alpha_image.size, (255, 255, 255))
        alpha_composite = Image.alpha_composite(background, alpha_image)
        image = alpha_composite.convert("RGB")
    elif image.mode != "RGB":
        image = image.convert("RGB")

    return image


def extract_text_from_image(
    image_source: Union[str, Path, bytes, BinaryIO],
    source_name: str = "Uploaded Image"
) -> List[PageExtractionResult]:
    """
    Extracts syllabus text from an image (JPG, JPEG, PNG) using local OCR.
    Prioritizes Tesseract if configured, otherwise uses built-in RapidOCR.
    Returns a single-page PageExtractionResult with page=1.
    """
    status = check_tesseract_installed()
    if not status["installed"]:
        raise RuntimeError(
            "Local OCR is not installed or configured on the server. "
            "Please install Tesseract OCR (e.g. 'winget install UB-Mannheim.TesseractOCR') "
            "or set TESSERACT_CMD in your .env file."
        )

    logger.info(f"[OCR] Processing image: {source_name} (engine={status['engine']})")

    image: Optional[Image.Image] = None
    try:
        if isinstance(image_source, (str, Path)):
            image = Image.open(str(image_source))
        elif isinstance(image_source, bytes):
            image = Image.open(io.BytesIO(image_source))
        elif hasattr(image_source, "read"):
            image = Image.open(image_source)
        else:
            raise ValueError(f"Unsupported image source type: {type(image_source)}")

        width, height = image.size
        logger.info(f"[OCR] Image Resolution: {width}x{height}, Mode: {image.mode}")

        processed_img = preprocess_image(image)
        raw_text = ""

        # Use Tesseract if available
        if status["engine"] == "tesseract":
            raw_text = pytesseract.image_to_string(processed_img)
        else:
            # Use RapidOCR ONNX
            engine = get_rapidocr_engine()
            # Convert PIL to numpy array or bytes
            img_byte_arr = io.BytesIO()
            processed_img.save(img_byte_arr, format='JPEG')
            result, _ = engine(img_byte_arr.getvalue())
            if result:
                raw_text = "\n".join([line[1] for line in result])

        # Clean and normalize extracted text while preserving headings/bullets
        cleaned_text = clean_page_text(raw_text)
        char_count = len(cleaned_text)

        logger.info(f"[OCR] Characters extracted: {char_count}")

        if char_count == 0:
            logger.warning("[OCR] Warning: OCR extracted 0 characters from the image.")

        # Single image represents page 1
        return [
            PageExtractionResult(
                page=1,
                text=cleaned_text,
                charCount=char_count
            )
        ]

    except Exception as e:
        logger.error(f"[OCR] Image OCR error: {str(e)}")
        raise e
    finally:
        if image is not None:
            image.close()
