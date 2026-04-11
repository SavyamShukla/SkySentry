# SkySentry AI — Media Database

Place your drone/aircraft/bird photos and videos in this folder.

## Supported Formats
- **Images**: `.jpg`, `.jpeg`, `.png`, `.bmp`, `.webp`
- **Videos**: `.mp4`, `.avi`, `.mov`, `.webm`, `.mkv`

## How It Works
When a scan is initiated from the dashboard, the backend picks a **random** file from this folder and:
1. Displays it in the camera feed panel
2. Runs YOLO inference on a frame from the media
3. Returns detection results (class, confidence, bounding boxes)
4. Archives the scan results

## Tips
- Use real drone footage for best detection results
- Name files descriptively (e.g., `drone_park_01.mp4`, `bird_flying.jpg`)
- Higher resolution images give better detection accuracy
- For videos, the system analyzes a frame at ~2 seconds in
