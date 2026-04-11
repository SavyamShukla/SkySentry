"""
SkySentry AI — Inference Engine
Runs YOLO inference on images and returns structured detection results.
Class names are read dynamically from the model — never hardcoded.
"""

import time
import logging
from PIL import Image

logger = logging.getLogger("skysentry.inference")


def run_inference(model, image: Image.Image, confidence_threshold: float = 0.25) -> dict:
    """
    Run YOLO inference on a PIL Image.

    Args:
        model: Loaded YOLO model instance
        image: PIL Image to analyze
        confidence_threshold: Minimum confidence to include a detection

    Returns:
        dict with 'detections', 'model_classes', 'inference_time_ms', 'image_size'
    """
    if model is None:
        return {
            "detections": [],
            "model_classes": [],
            "inference_time_ms": 0,
            "image_size": [image.width, image.height],
            "error": "Model not loaded"
        }

    try:
        start = time.time()

        # Run YOLO prediction
        results = model.predict(
            source=image,
            conf=confidence_threshold,
            verbose=False,
        )

        elapsed_ms = (time.time() - start) * 1000

        # Extract class names from model
        class_names = {}
        if hasattr(model, 'names') and model.names:
            class_names = model.names

        # Parse detections
        detections = []
        if results and len(results) > 0:
            result = results[0]  # First (and only) image result

            if result.boxes is not None and len(result.boxes) > 0:
                boxes = result.boxes

                for i in range(len(boxes)):
                    # Bounding box coordinates (xyxy format)
                    bbox = boxes.xyxy[i].tolist()
                    x1, y1, x2, y2 = [round(v, 2) for v in bbox]

                    # Confidence score
                    conf = round(float(boxes.conf[i]), 4)

                    # Class ID and name
                    cls_id = int(boxes.cls[i])
                    cls_name = class_names.get(cls_id, f"class_{cls_id}")

                    detections.append({
                        "class": cls_name,
                        "class_id": cls_id,
                        "confidence": conf,
                        "bbox": [x1, y1, x2, y2],
                    })

            # Sort by confidence (highest first)
            detections.sort(key=lambda d: d["confidence"], reverse=True)

        logger.info(
            f"Inference complete: {len(detections)} detections in {elapsed_ms:.1f}ms"
        )

        return {
            "detections": detections,
            "model_classes": list(class_names.values()),
            "inference_time_ms": round(elapsed_ms, 1),
            "image_size": [image.width, image.height],
        }

    except Exception as e:
        logger.error(f"Inference error: {e}", exc_info=True)
        return {
            "detections": [],
            "model_classes": [],
            "inference_time_ms": 0,
            "image_size": [image.width, image.height],
            "error": str(e),
        }


def extract_video_frame(video_path: str, time_sec: float = 2.0) -> Image.Image | None:
    """
    Extract a single frame from a video file at the given timestamp.
    Falls back to the first frame if the video is shorter than time_sec.

    Args:
        video_path: Path to the video file
        time_sec: Time in seconds to extract the frame from

    Returns:
        PIL Image of the extracted frame, or None on failure
    """
    try:
        import cv2

        cap = cv2.VideoCapture(video_path)
        if not cap.isOpened():
            logger.error(f"Cannot open video: {video_path}")
            return None

        fps = cap.get(cv2.CAP_PROP_FPS) or 30
        total_frames = int(cap.get(cv2.CAP_PROP_FRAME_COUNT))
        target_frame = int(time_sec * fps)

        # Clamp to valid range
        if target_frame >= total_frames:
            target_frame = max(0, total_frames // 2)

        cap.set(cv2.CAP_PROP_POS_FRAMES, target_frame)
        ret, frame = cap.read()
        cap.release()

        if ret and frame is not None:
            # Convert BGR to RGB
            frame_rgb = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
            return Image.fromarray(frame_rgb)
        else:
            logger.error(f"Failed to read frame at {time_sec}s from {video_path}")
            return None

    except ImportError:
        logger.error("opencv-python not installed. Install with: pip install opencv-python")
        return None
    except Exception as e:
        logger.error(f"Error extracting video frame: {e}", exc_info=True)
        return None
