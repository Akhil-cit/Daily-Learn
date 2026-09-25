import os
import shutil
from pathlib import Path
from typing import Optional
from pydantic_settings import BaseSettings

# Absolute path to backend directory
BASE_DIR = Path(__file__).resolve().parent


class Settings(BaseSettings):
    """
    Application configuration loaded from environment variables and .env file.
    """
    ollama_base_url: str = "http://localhost:11434"
    ollama_model: str = "llama3.2:3b"
    ollama_timeout_seconds: float = 120.0
    upload_dir: str = str(BASE_DIR / "data" / "uploads")
    max_upload_size_mb: int = 20
    tesseract_cmd: Optional[str] = None
    # Phase 3 — RAG
    embedding_model: str = "all-MiniLM-L6-v2"
    rag_index_dir: str = str(BASE_DIR / "data" / "rag")
    # Phase 4 — Daily Learn
    study_plans_dir: str = str(BASE_DIR / "data" / "study_plans")

    class Config:
        env_file = str(BASE_DIR / ".env")
        env_file_encoding = "utf-8"
        extra = "ignore"

    def get_tesseract_executable(self) -> Optional[str]:
        """
        Resolves the path to the local Tesseract binary using:
        1. Explicit TESSERACT_CMD in .env
        2. System PATH
        3. Standard Windows default installation directories
        """
        # 1. Explicit configuration from environment
        if self.tesseract_cmd and self.tesseract_cmd.strip():
            cmd_path = self.tesseract_cmd.strip()
            if os.path.exists(cmd_path):
                return cmd_path
            # Check if specified name is in PATH
            which_res = shutil.which(cmd_path)
            if which_res:
                return which_res

        # 2. Check system PATH
        path_binary = shutil.which("tesseract")
        if path_binary:
            return path_binary

        # 3. Check common Windows installation paths
        common_windows_paths = [
            r"C:\Program Files\Tesseract-OCR\tesseract.exe",
            r"C:\Program Files (x86)\Tesseract-OCR\tesseract.exe",
            os.path.expandvars(r"%LOCALAPPDATA%\Programs\Tesseract-OCR\tesseract.exe"),
        ]
        for p in common_windows_paths:
            if os.path.exists(p):
                return p

        return None


settings = Settings()

# Ensure uploads directory exists on startup
os.makedirs(settings.upload_dir, exist_ok=True)

# Ensure RAG index directory exists on startup
os.makedirs(settings.rag_index_dir, exist_ok=True)

# Ensure study plans directory exists on startup
os.makedirs(settings.study_plans_dir, exist_ok=True)
