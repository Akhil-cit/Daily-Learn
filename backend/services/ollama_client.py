import time
from typing import List, Dict, Any, Optional
import httpx

from config import settings
from logger import logger
from models.syllabus_models import OllamaHealthResponse


class OllamaClient:
    """
    HTTP client for communicating with local Ollama instance (/api/chat, /api/tags).
    """
    def __init__(
        self,
        base_url: Optional[str] = None,
        model: Optional[str] = None,
        timeout_seconds: Optional[float] = None
    ):
        self.base_url = (base_url or settings.ollama_base_url).rstrip("/")
        self.model = model or settings.ollama_model
        self.timeout_seconds = timeout_seconds or settings.ollama_timeout_seconds

    async def check_health(self) -> OllamaHealthResponse:
        """
        Checks if Ollama is reachable and whether the target model is downloaded/available.
        """
        tags_url = f"{self.base_url}/api/tags"
        try:
            async with httpx.AsyncClient(timeout=10.0) as client:
                response = await client.get(tags_url)
                if response.status_code == 200:
                    data = response.json()
                    models = [m.get("name", "") for m in data.get("models", [])]
                    
                    # Check if requested model or tag matches
                    is_available = any(
                        self.model == m or self.model in m or m.startswith(self.model)
                        for m in models
                    )
                    
                    return OllamaHealthResponse(
                        ollama="connected",
                        model=self.model,
                        available=is_available,
                        models_available=models
                    )
                else:
                    return OllamaHealthResponse(
                        ollama="error",
                        model=self.model,
                        available=False,
                        error=f"Ollama returned HTTP status {response.status_code}"
                    )
        except httpx.ConnectError:
            return OllamaHealthResponse(
                ollama="unreachable",
                model=self.model,
                available=False,
                error=f"Cannot connect to Ollama at {self.base_url}. Is Ollama running?"
            )
        except Exception as e:
            return OllamaHealthResponse(
                ollama="error",
                model=self.model,
                available=False,
                error=f"Error communicating with Ollama: {str(e)}"
            )

    async def chat(
        self,
        messages: List[Dict[str, str]],
        format: str = "json",
        temperature: float = 0.1
    ) -> str:
        """
        Sends chat messages to Ollama /api/chat endpoint with JSON output mode.
        """
        chat_url = f"{self.base_url}/api/chat"
        payload = {
            "model": self.model,
            "messages": messages,
            "format": format,
            "stream": False,
            "options": {
                "temperature": temperature
            }
        }

        logger.info(f"[AI] Sending syllabus to Ollama at {chat_url}")
        logger.info(f"[AI] Model: {self.model} (format={format}, temp={temperature})")
        
        start_time = time.time()
        try:
            async with httpx.AsyncClient(timeout=self.timeout_seconds) as client:
                response = await client.post(chat_url, json=payload)
                
            elapsed_ms = int((time.time() - start_time) * 1000)
            
            if response.status_code != 200:
                error_msg = f"Ollama returned error {response.status_code}: {response.text}"
                logger.error(f"[AI] {error_msg}")
                raise RuntimeError(error_msg)

            data = response.json()
            raw_content = data.get("message", {}).get("content", "")
            logger.info(f"[AI] Response received in {elapsed_ms}ms ({len(raw_content)} chars)")
            return raw_content

        except httpx.ConnectError as e:
            logger.error(f"[AI] Connection failed to Ollama at {self.base_url}: {str(e)}")
            raise ConnectionError(
                f"Could not connect to Ollama at {self.base_url}. Please ensure Ollama is started ('ollama serve')."
            )
        except httpx.TimeoutException:
            logger.error(f"[AI] Request to Ollama timed out after {self.timeout_seconds}s")
            raise TimeoutError(
                f"Ollama request timed out after {self.timeout_seconds} seconds for model {self.model}."
            )
        except Exception as e:
            logger.error(f"[AI] Ollama communication failure: {str(e)}")
            raise e


ollama_client = OllamaClient()
