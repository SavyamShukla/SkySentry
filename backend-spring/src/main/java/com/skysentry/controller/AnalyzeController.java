package com.skysentry.controller;

import com.skysentry.dto.Base64Request;
import com.skysentry.dto.InferenceResult;
import com.skysentry.service.InferenceService;
import com.skysentry.service.MediaService;
import com.skysentry.service.ModelManagerService;
import com.skysentry.util.VideoFrameExtractor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.util.Base64;
import java.util.Map;

/**
 * Analyze endpoints — run YOLO inference on images/videos.
 * Matches Python: /analyze, /analyze/base64, /analyze/media/{filename}
 */
@RestController
public class AnalyzeController {

    @Autowired
    private ModelManagerService modelManager;

    @Autowired
    private InferenceService inferenceService;

    @Autowired
    private MediaService mediaService;

    /**
     * POST /analyze — Analyze an uploaded image using the YOLO model.
     * Accepts multipart/form-data with an image file.
     */
    @PostMapping("/analyze")
    public ResponseEntity<?> analyzeImage(
            @RequestParam(value = "file", required = false) MultipartFile file) {

        // Check model availability
        ResponseEntity<?> modelCheck = checkModelAvailable();
        if (modelCheck != null) return modelCheck;

        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest()
                .body(Map.of("detail", "No image file provided"));
        }

        try {
            BufferedImage image = ImageIO.read(file.getInputStream());
            if (image == null) {
                return ResponseEntity.badRequest()
                    .body(Map.of("detail", "Invalid image file"));
            }

            InferenceResult result = inferenceService.runInference(
                modelManager.getSession(), modelManager.getEnvironment(),
                modelManager.getClassNames(), image
            );
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            return ResponseEntity.badRequest()
                .body(Map.of("detail", "Invalid image file: " + e.getMessage()));
        }
    }

    /**
     * POST /analyze/base64 — Analyze a base64-encoded image.
     * Useful for webcam frame capture from the frontend.
     */
    @PostMapping("/analyze/base64")
    public ResponseEntity<?> analyzeBase64(@RequestBody Base64Request req) {
        // Check model availability
        ResponseEntity<?> modelCheck = checkModelAvailable();
        if (modelCheck != null) return modelCheck;

        try {
            // Strip data URI prefix if present (e.g., "data:image/jpeg;base64,...")
            String imageData = req.getImage();
            if (imageData.contains(",")) {
                imageData = imageData.substring(imageData.indexOf(",") + 1);
            }

            byte[] decoded = Base64.getDecoder().decode(imageData);
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(decoded));
            if (image == null) {
                return ResponseEntity.badRequest()
                    .body(Map.of("detail", "Invalid base64 image"));
            }

            // Ensure RGB format
            if (image.getType() != BufferedImage.TYPE_INT_RGB) {
                BufferedImage rgb = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
                rgb.createGraphics().drawImage(image, 0, 0, null);
                image = rgb;
            }

            InferenceResult result = inferenceService.runInference(
                modelManager.getSession(), modelManager.getEnvironment(),
                modelManager.getClassNames(), image
            );
            return ResponseEntity.ok(result);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                .body(Map.of("detail", "Invalid base64 image: " + e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                .body(Map.of("detail", "Invalid base64 image: " + e.getMessage()));
        }
    }

    /**
     * POST /analyze/media/{filename} — Analyze a media file from the media/ folder directly.
     * For images: runs inference directly.
     * For videos: extracts a frame at time_sec and runs inference.
     */
    @PostMapping("/analyze/media/{filename}")
    public ResponseEntity<?> analyzeMediaFile(
            @PathVariable String filename,
            @RequestParam(value = "time_sec", defaultValue = "2.0") double timeSec) {

        // Security: resolve and validate the file path
        File file = mediaService.resolveMediaFile(filename);
        if (file == null) {
            if (filename.contains("..")) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("detail", "Access denied"));
            }
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("detail", "File not found: " + filename));
        }

        // Check model availability
        ResponseEntity<?> modelCheck = checkModelAvailable();
        if (modelCheck != null) return modelCheck;

        BufferedImage image;

        if (mediaService.isImage(filename)) {
            try {
                image = ImageIO.read(file);
                if (image == null) {
                    return ResponseEntity.badRequest()
                        .body(Map.of("detail", "Cannot open image: " + filename));
                }
                // Ensure RGB format
                if (image.getType() != BufferedImage.TYPE_INT_RGB) {
                    BufferedImage rgb = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
                    rgb.createGraphics().drawImage(image, 0, 0, null);
                    image = rgb;
                }
            } catch (Exception e) {
                return ResponseEntity.badRequest()
                    .body(Map.of("detail", "Cannot open image: " + e.getMessage()));
            }
        } else if (mediaService.isVideo(filename)) {
            image = VideoFrameExtractor.extractFrame(file.getAbsolutePath(), timeSec);
            if (image == null) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("detail", "Failed to extract video frame. Ensure JavaCV is properly installed."));
            }
        } else {
            String ext = filename.substring(filename.lastIndexOf('.'));
            return ResponseEntity.badRequest()
                .body(Map.of("detail", "Unsupported file type: " + ext));
        }

        InferenceResult result = inferenceService.runInference(
            modelManager.getSession(), modelManager.getEnvironment(),
            modelManager.getClassNames(), image
        );
        result.setMediaFile(filename);
        result.setMediaType(mediaService.isImage(filename) ? "image" : "video");
        return ResponseEntity.ok(result);
    }

    /**
     * Check if the model is available, return error response if not.
     */
    private ResponseEntity<?> checkModelAvailable() {
        if (modelManager.getSession() == null) {
            Map<String, Object> health = modelManager.getHealth();
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of(
                    "detail", Map.of(
                        "error", "Model not available",
                        "reason", health.getOrDefault("error", "Unknown"),
                        "model_path", health.get("model_path")
                    )
                ));
        }
        return null;
    }
}
