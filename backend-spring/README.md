# SkySentry AI — Spring Boot Backend

**YOLO-powered aerial threat detection API** — drop-in replacement for the Python (FastAPI) backend.

## Prerequisites

- **JDK 17+** (e.g., [Eclipse Temurin](https://adoptium.net/))
- **Maven 3.8+** (or use the included `mvnw` wrapper)
- **Python 3.10+** (one-time, only for model conversion)

## Quick Start

### 1. Convert the YOLO model (one-time)

The Python backend uses `best.pt` (PyTorch). The Java backend needs ONNX format:

```bash
# From the project root (where best.pt lives)
pip install ultralytics
python -c "from ultralytics import YOLO; YOLO('best.pt').export(format='onnx')"
```

This creates `best.onnx` in the same directory.

### 2. Build & Run

```bash
cd backend-spring
mvn clean package -DskipTests
mvn spring-boot:run
```

The server starts on **http://localhost:8000** — same port as the Python backend.

### 3. Verify

```bash
curl http://localhost:8000/health
```

You should see:
```json
{
  "status": "ok",
  "model": { "model_loaded": true, ... },
  "media_count": 5,
  "archive_count": 0
}
```

### 4. Open the Frontend

Open `index.html` in your browser. Everything works exactly as before — same scan flow, same bounding boxes, same archive.

## API Endpoints

All endpoints match the Python backend 1:1:

| Endpoint | Method | Description |
|---|---|---|
| `/health` | GET | System & model health check |
| `/media/random` | GET | Pick a random media file |
| `/media/list` | GET | List all available media files |
| `/media/{filename}` | GET | Serve a specific media file |
| `/analyze` | POST | Analyze uploaded image (multipart) |
| `/analyze/base64` | POST | Analyze base64-encoded image |
| `/analyze/media/{filename}` | POST | Analyze a media file from disk |
| `/archive` | POST | Save scan result to database |
| `/archive` | GET | List archived scans |
| `/archive/{id}` | GET | Get single archived scan |
| `/reload` | POST | Force reload YOLO model |

## Project Structure

```
backend-spring/
├── pom.xml                                 # Maven dependencies
├── src/main/java/com/skysentry/
│   ├── SkySentryApplication.java           # Entry point
│   ├── config/
│   │   └── CorsConfig.java                # CORS (allow-all for local dev)
│   ├── controller/
│   │   ├── HealthController.java           # GET /health
│   │   ├── MediaController.java            # /media/* endpoints
│   │   ├── AnalyzeController.java          # /analyze/* endpoints
│   │   ├── ArchiveController.java          # /archive endpoints
│   │   └── ModelController.java            # POST /reload
│   ├── service/
│   │   ├── ModelManagerService.java        # ONNX model lifecycle
│   │   ├── InferenceService.java           # YOLO preprocessing + postprocessing
│   │   ├── MediaService.java               # Media folder scanning
│   │   └── DatabaseService.java            # SQLite archive (JdbcTemplate)
│   ├── dto/
│   │   ├── Detection.java                  # Single detection result
│   │   ├── InferenceResult.java            # Full inference response
│   │   ├── Base64Request.java              # Request DTO
│   │   └── ArchiveScanRequest.java         # Request DTO
│   └── util/
│       └── VideoFrameExtractor.java        # Video frame extraction (JavaCV)
└── src/main/resources/
    ├── application.properties              # Server & model config
    └── schema.sql                          # SQLite table schema
```

## Python → Java Mapping

| Python (FastAPI) | Java (Spring Boot) |
|---|---|
| `main.py` | Controllers + `SkySentryApplication.java` |
| `model_manager.py` | `ModelManagerService.java` |
| `inference.py` | `InferenceService.java` |
| `database.py` | `DatabaseService.java` |
| `ultralytics.YOLO` | ONNX Runtime (`com.microsoft.onnxruntime`) |
| `cv2.VideoCapture` | JavaCV `FFmpegFrameGrabber` |
| `PIL.Image` | `java.awt.image.BufferedImage` |
| `sqlite3` | Spring `JdbcTemplate` + `xerial/sqlite-jdbc` |
| `uvicorn` | Embedded Tomcat (Spring Boot) |

## Configuration

All configurable via `application.properties`:

| Property | Default | Description |
|---|---|---|
| `server.port` | `8000` | Server port |
| `skysentry.model-path` | `../best.onnx` | Path to ONNX model |
| `skysentry.media-dir` | `../backend/media` | Media files directory |
| `skysentry.confidence-threshold` | `0.25` | Min detection confidence |

## Supported Model Formats

The inference engine auto-detects the YOLO output format:

- **YOLOv10**: Output shape `[1, 300, 6]` — post-NMS, direct parsing
- **YOLOv8**: Output shape `[1, 4+C, 8400]` — includes NMS postprocessing

## Notes

- The `archive.db` SQLite database is created automatically on first run
- Model hot-reload: replace `best.onnx` on disk and the server auto-detects the change within 5 seconds
- The `javacv-platform` dependency is large (~300MB). For production, use platform-specific classifiers
