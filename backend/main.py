"""
SkySentry AI — FastAPI Backend
Main application: routes, CORS, model initialization.
"""

import os
import io
import random
import base64
import logging
from pathlib import Path

from fastapi import FastAPI, File, UploadFile, HTTPException, Query
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import FileResponse, JSONResponse
from pydantic import BaseModel
from PIL import Image

from model_manager import ModelManager
from inference import run_inference, extract_video_frame
import database as db

# ── Logging ──────────────────────────────────────────
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(name)s] %(levelname)s: %(message)s",
)
logger = logging.getLogger("skysentry.api")

# ── Config ───────────────────────────────────────────
MODEL_PATH = os.environ.get("MODEL_PATH", os.path.join(os.path.dirname(__file__), "..", "best.pt"))
MEDIA_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "media")
IMAGE_EXTENSIONS = {".jpg", ".jpeg", ".png", ".bmp", ".webp"}
VIDEO_EXTENSIONS = {".mp4", ".avi", ".mov", ".webm", ".mkv"}
ALLOWED_EXTENSIONS = IMAGE_EXTENSIONS | VIDEO_EXTENSIONS

# ── App ──────────────────────────────────────────────
app = FastAPI(
    title="SkySentry AI Backend",
    description="YOLO-powered aerial threat detection API",
    version="1.0.0",
)

# CORS — allow frontend to call from any origin (local dev)
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# ── Model Manager ────────────────────────────────────
model_mgr = ModelManager(MODEL_PATH)


# ── Startup ──────────────────────────────────────────
@app.on_event("startup")
async def startup():
    logger.info("SkySentry AI Backend starting...")
    db.init_db()
    model_mgr.load_model()
    logger.info("Startup complete.")


# ── Pydantic Models ──────────────────────────────────
class Base64Request(BaseModel):
    image: str  # base64-encoded image data
    media_type: str = "image/jpeg"


class ArchiveScanRequest(BaseModel):
    scan_number: int
    media_file: str | None = None
    media_type: str | None = None
    detections: list = []
    top_class: str | None = None
    top_confidence: float | None = None
    threat_level: str | None = None
    sector: str | None = None
    distance_km: float | None = None
    altitude_m: float | None = None
    latitude: float | None = None
    longitude: float | None = None
    inference_time_ms: float | None = None
    track_id: str | None = None


# ── Helper Functions ─────────────────────────────────
def get_media_files() -> list[dict]:
    """Scan the media directory for supported files."""
    if not os.path.isdir(MEDIA_DIR):
        return []

    files = []
    for filename in os.listdir(MEDIA_DIR):
        ext = os.path.splitext(filename)[1].lower()
        if ext in ALLOWED_EXTENSIONS:
            filepath = os.path.join(MEDIA_DIR, filename)
            media_type = "image" if ext in IMAGE_EXTENSIONS else "video"
            files.append({
                "filename": filename,
                "path": filepath,
                "type": media_type,
                "extension": ext,
                "size_bytes": os.path.getsize(filepath),
            })
    return files


# ── Routes ───────────────────────────────────────────

@app.get("/health")
async def health():
    """Model and system health check."""
    media_files = get_media_files()
    return {
        "status": "ok",
        "model": model_mgr.get_health(),
        "media_count": len(media_files),
        "archive_count": db.get_scan_count(),
    }


@app.get("/media/random")
async def random_media():
    """Pick a random media file and return its metadata + URL."""
    files = get_media_files()
    if not files:
        raise HTTPException(
            status_code=404,
            detail="No media files found in the media/ folder. Add images or videos to backend/media/."
        )

    chosen = random.choice(files)
    return {
        "filename": chosen["filename"],
        "type": chosen["type"],
        "extension": chosen["extension"],
        "url": f"/media/{chosen['filename']}",
        "size_bytes": chosen["size_bytes"],
    }


@app.get("/media/list")
async def list_media():
    """List all available media files."""
    files = get_media_files()
    return {"files": files, "count": len(files)}


@app.get("/media/{filename}")
async def serve_media(filename: str):
    """Serve a specific media file."""
    filepath = os.path.join(MEDIA_DIR, filename)

    # Security: prevent path traversal
    if not os.path.abspath(filepath).startswith(os.path.abspath(MEDIA_DIR)):
        raise HTTPException(status_code=403, detail="Access denied")

    if not os.path.exists(filepath):
        raise HTTPException(status_code=404, detail=f"File not found: {filename}")

    return FileResponse(filepath)


@app.post("/analyze")
async def analyze_image(file: UploadFile = File(None)):
    """
    Analyze an uploaded image using the YOLO model.
    Accepts multipart/form-data with an image file.
    """
    model = model_mgr.get_model()
    if model is None:
        health = model_mgr.get_health()
        raise HTTPException(
            status_code=503,
            detail={
                "error": "Model not available",
                "reason": health.get("error", "Unknown"),
                "model_path": health.get("model_path"),
            }
        )

    if file is None:
        raise HTTPException(status_code=400, detail="No image file provided")

    try:
        contents = await file.read()
        image = Image.open(io.BytesIO(contents)).convert("RGB")
    except Exception as e:
        raise HTTPException(status_code=400, detail=f"Invalid image file: {str(e)}")

    result = run_inference(model, image)
    return JSONResponse(content=result)


@app.post("/analyze/base64")
async def analyze_base64(req: Base64Request):
    """
    Analyze a base64-encoded image using the YOLO model.
    Useful for webcam frame capture from the frontend.
    """
    model = model_mgr.get_model()
    if model is None:
        health = model_mgr.get_health()
        raise HTTPException(
            status_code=503,
            detail={
                "error": "Model not available",
                "reason": health.get("error", "Unknown"),
            }
        )

    try:
        # Strip data URI prefix if present (e.g., "data:image/jpeg;base64,...")
        image_data = req.image
        if "," in image_data:
            image_data = image_data.split(",", 1)[1]

        decoded = base64.b64decode(image_data)
        image = Image.open(io.BytesIO(decoded)).convert("RGB")
    except Exception as e:
        raise HTTPException(status_code=400, detail=f"Invalid base64 image: {str(e)}")

    result = run_inference(model, image)
    return JSONResponse(content=result)


@app.post("/analyze/media/{filename}")
async def analyze_media_file(filename: str, time_sec: float = Query(2.0, ge=0)):
    """
    Analyze a media file from the media/ folder directly.
    For images: runs inference directly.
    For videos: extracts a frame at time_sec and runs inference.
    """
    filepath = os.path.join(MEDIA_DIR, filename)

    # Security: prevent path traversal
    if not os.path.abspath(filepath).startswith(os.path.abspath(MEDIA_DIR)):
        raise HTTPException(status_code=403, detail="Access denied")

    if not os.path.exists(filepath):
        raise HTTPException(status_code=404, detail=f"File not found: {filename}")

    model = model_mgr.get_model()
    if model is None:
        health = model_mgr.get_health()
        raise HTTPException(
            status_code=503,
            detail={
                "error": "Model not available",
                "reason": health.get("error", "Unknown"),
            }
        )

    ext = os.path.splitext(filename)[1].lower()

    if ext in IMAGE_EXTENSIONS:
        try:
            image = Image.open(filepath).convert("RGB")
        except Exception as e:
            raise HTTPException(status_code=400, detail=f"Cannot open image: {str(e)}")
    elif ext in VIDEO_EXTENSIONS:
        image = extract_video_frame(filepath, time_sec=time_sec)
        if image is None:
            raise HTTPException(
                status_code=500,
                detail="Failed to extract video frame. Ensure opencv-python is installed."
            )
    else:
        raise HTTPException(status_code=400, detail=f"Unsupported file type: {ext}")

    result = run_inference(model, image)
    result["media_file"] = filename
    result["media_type"] = "image" if ext in IMAGE_EXTENSIONS else "video"
    return JSONResponse(content=result)


@app.post("/archive")
async def archive_scan(scan: ArchiveScanRequest):
    """Save a scan result to the archive database."""
    try:
        row_id = db.save_scan(scan.model_dump())
        return {"id": row_id, "status": "saved"}
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Failed to save scan: {str(e)}")


@app.get("/archive")
async def get_archive(limit: int = Query(50, ge=1, le=200), offset: int = Query(0, ge=0)):
    """Retrieve archived scans, newest first."""
    scans = db.get_scans(limit=limit, offset=offset)
    total = db.get_scan_count()
    return {"scans": scans, "total": total, "limit": limit, "offset": offset}


@app.get("/archive/{scan_id}")
async def get_archived_scan(scan_id: int):
    """Retrieve a single archived scan by ID."""
    scan = db.get_scan(scan_id)
    if scan is None:
        raise HTTPException(status_code=404, detail=f"Scan {scan_id} not found")
    return scan


@app.post("/reload")
async def reload_model():
    """Force reload the YOLO model from disk."""
    success = model_mgr.load_model()
    health = model_mgr.get_health()
    return {
        "success": success,
        "model": health,
    }


# ── Run directly ─────────────────────────────────────
if __name__ == "__main__":
    import uvicorn
    uvicorn.run("main:app", host="0.0.0.0", port=8000, reload=True)
