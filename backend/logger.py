import logging
import sys

def setup_logger(name: str = "daily_learn") -> logging.Logger:
    """
    Sets up a consistent logger across backend services.
    Forces UTF-8 encoding on the console stream so Unicode characters
    (e.g. arrows in log messages) don't raise UnicodeEncodeError on
    Windows cp1252 terminals.
    """
    logger = logging.getLogger(name)
    if not logger.handlers:
        logger.setLevel(logging.INFO)
        formatter = logging.Formatter(
            fmt="%(asctime)s [%(levelname)s] %(message)s",
            datefmt="%Y-%m-%d %H:%M:%S"
        )
        # Force UTF-8 on Windows to avoid cp1252 UnicodeEncodeError
        try:
            stream = open(sys.stdout.fileno(), mode="w", encoding="utf-8", buffering=1)
        except Exception:
            stream = sys.stdout
        console_handler = logging.StreamHandler(stream)
        console_handler.setFormatter(formatter)
        logger.addHandler(console_handler)
    return logger

logger = setup_logger()
