import re
from pathlib import Path
from typing import List, Union, BinaryIO
import pymupdf as fitz

from models.syllabus_models import PageExtractionResult
from logger import logger


def clean_page_text(text: str) -> str:
    """
    Normalizes extracted PDF page text while strictly preserving
    academic structure, headings, indentations, and bullet points.
    """
    if not text:
        return ""

    # Normalize line endings to \n
    text = text.replace("\r\n", "\n").replace("\r", "\n")

    # Remove non-printable control characters except standard whitespace
    text = re.sub(r"[\x00-\x08\x0b\x0c\x0e-\x1f\x7f]", "", text)

    # Strip trailing whitespace on each line, keeping leading indentation
    lines = [line.rstrip() for line in text.split("\n")]

    # Collapse excessive consecutive blank lines (limit to max 2)
    cleaned_lines = []
    blank_counter = 0
    for line in lines:
        if not line:
            blank_counter += 1
            if blank_counter <= 2:
                cleaned_lines.append("")
        else:
            blank_counter = 0
            cleaned_lines.append(line)

    return "\n".join(cleaned_lines).strip()


def extract_text_from_pdf(
    pdf_source: Union[str, Path, bytes, BinaryIO],
    source_name: str = "Uploaded PDF"
) -> List[PageExtractionResult]:
    """
    Extracts text page-by-page from a PDF document using PyMuPDF (fitz).
    
    Args:
        pdf_source: File path (str/Path), raw bytes, or file-like object.
        source_name: Descriptive name for logging.
        
    Returns:
        List of PageExtractionResult objects containing 1-indexed page numbers and extracted text.
    """
    logger.info(f"[PDF] Extracting document: {source_name}")
    
    doc = None
    try:
        if isinstance(pdf_source, (str, Path)):
            doc = fitz.open(str(pdf_source))
        elif isinstance(pdf_source, bytes):
            doc = fitz.open(stream=pdf_source, filetype="pdf")
        elif hasattr(pdf_source, "read"):
            doc = fitz.open(stream=pdf_source.read(), filetype="pdf")
        else:
            raise ValueError(f"Unsupported PDF source type: {type(pdf_source)}")

        if doc.is_encrypted:
            raise ValueError("The provided PDF is password protected and cannot be extracted.")

        total_pages = len(doc)
        logger.info(f"[PDF] Pages: {total_pages}")

        results: List[PageExtractionResult] = []
        total_chars = 0

        for page_index in range(total_pages):
            page_num = page_index + 1
            page = doc.load_page(page_index)
            raw_text = page.get_text("text")
            
            cleaned_text = clean_page_text(raw_text)
            char_count = len(cleaned_text)
            total_chars += char_count

            results.append(
                PageExtractionResult(
                    page=page_num,
                    text=cleaned_text,
                    charCount=char_count
                )
            )

        logger.info(f"[PDF] Characters extracted: {total_chars} across {total_pages} page(s)")
        
        if total_chars == 0:
            logger.warning("[PDF] Warning: Document contains 0 extracted characters. It might be scanned/image-only.")

        return results

    except Exception as e:
        logger.error(f"[PDF] Extraction error: {str(e)}")
        raise e
    finally:
        if doc is not None:
            doc.close()
