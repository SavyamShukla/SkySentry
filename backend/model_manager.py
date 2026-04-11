"""
SkySentry AI — Model Manager
Handles YOLO model lifecycle: loading, reloading, health checks.
The model file (best.pt) can be hot-swapped without restarting the server.
"""

import os
import time
import threading
import logging

logger = logging.getLogger("skysentry.model")


class ModelManager:
    """
    Manages a YOLO model lifecycle.
    - Loads model from a file path
    - Auto-detects file changes (mtime) and reloads
    - Thread-safe access
    - Never crashes if model is missing or corrupt
    """

    def __init__(self, model_path: str, auto_reload_cooldown: float = 5.0):
        self.model_path = os.path.abspath(model_path)
        self.auto_reload_cooldown = auto_reload_cooldown

        self._model = None
        self._model_classes: dict = {}
        self._lock = threading.Lock()
        self._last_mtime: float = 0.0
        self._last_check_time: float = 0.0
        self._load_error: str | None = None
        self._is_loading: bool = False

    @property
    def is_loaded(self) -> bool:
        return self._model is not None

    @property
    def load_error(self) -> str | None:
        return self._load_error

    @property
    def model_classes(self) -> dict:
        return self._model_classes.copy()

    def load_model(self) -> bool:
        """
        Load or reload the model from disk.
        Returns True if successful, False otherwise.
        """
        with self._lock:
            if self._is_loading:
                logger.warning("Model is already being loaded, skipping.")
                return False
            self._is_loading = True

        try:
            if not os.path.exists(self.model_path):
                self._load_error = f"Model file not found: {self.model_path}"
                logger.error(self._load_error)
                return False

            logger.info(f"Loading YOLO model from: {self.model_path}")
            start = time.time()

            from ultralytics import YOLO
            model = YOLO(self.model_path)

            # Extract class names from the model
            class_names = {}
            if hasattr(model, 'names') and model.names:
                class_names = model.names  # dict {0: 'drone', 1: 'bird', ...}

            elapsed = time.time() - start

            with self._lock:
                self._model = model
                self._model_classes = class_names
                self._last_mtime = os.path.getmtime(self.model_path)
                self._load_error = None

            logger.info(
                f"Model loaded successfully in {elapsed:.2f}s. "
                f"Classes: {list(class_names.values())}"
            )
            return True

        except Exception as e:
            self._load_error = f"Failed to load model: {str(e)}"
            logger.error(self._load_error, exc_info=True)
            return False

        finally:
            with self._lock:
                self._is_loading = False

    def get_model(self):
        """
        Get the loaded model. Auto-checks for file changes.
        Returns None if model is not available.
        """
        self._check_for_updates()
        return self._model

    def _check_for_updates(self):
        """Check if the model file has been modified and reload if needed."""
        now = time.time()
        if now - self._last_check_time < self.auto_reload_cooldown:
            return
        self._last_check_time = now

        try:
            if not os.path.exists(self.model_path):
                return
            current_mtime = os.path.getmtime(self.model_path)
            if current_mtime != self._last_mtime and self._last_mtime > 0:
                logger.info("Model file changed on disk, reloading...")
                self.load_model()
        except Exception as e:
            logger.warning(f"Error checking model file: {e}")

    def get_health(self) -> dict:
        """Return health/status information about the model."""
        model_file_exists = os.path.exists(self.model_path)
        return {
            "model_loaded": self.is_loaded,
            "model_path": self.model_path,
            "model_file_exists": model_file_exists,
            "model_classes": list(self._model_classes.values()) if self._model_classes else [],
            "last_modified": self._last_mtime if self._last_mtime > 0 else None,
            "error": self._load_error,
            "is_loading": self._is_loading,
        }
