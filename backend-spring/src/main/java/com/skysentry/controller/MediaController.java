package com.skysentry.controller;

import com.skysentry.service.MediaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Media endpoints — serve images/videos from the media/ folder.
 * Supports HTTP Range requests for video streaming (required by browsers).
 * Matches Python FastAPI's FileResponse behavior.
 */
@RestController
public class MediaController {

    @Autowired
    private MediaService mediaService;

    /**
     * GET /media/random — Pick a random media file and return its metadata + URL.
     */
    @GetMapping("/media/random")
    public ResponseEntity<?> randomMedia() {
        Map<String, Object> chosen = mediaService.getRandomMedia();
        if (chosen == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("detail", "No media files found in the media/ folder. Add images or videos to media/."));
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("filename", chosen.get("filename"));
        response.put("type", chosen.get("type"));
        response.put("extension", chosen.get("extension"));
        response.put("url", "/media/" + chosen.get("filename"));
        response.put("size_bytes", chosen.get("size_bytes"));
        return ResponseEntity.ok(response);
    }

    /**
     * GET /media/list — List all available media files.
     */
    @GetMapping("/media/list")
    public Map<String, Object> listMedia() {
        List<Map<String, Object>> files = mediaService.getMediaFiles();
        return Map.of("files", files, "count", files.size());
    }

    /**
     * GET /media/{filename} — Serve a specific media file.
     * Supports HTTP Range requests for video streaming.
     */
    @GetMapping("/media/{filename}")
    public ResponseEntity<?> serveMedia(
            @PathVariable String filename,
            @RequestHeader(value = "Range", required = false) String rangeHeader) {

        File file = mediaService.resolveMediaFile(filename);

        if (file == null) {
            if (filename.contains("..")) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("detail", "Access denied"));
            }
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("detail", "File not found: " + filename));
        }

        try {
            String contentType = Files.probeContentType(file.toPath());
            if (contentType == null) {
                // Fallback content types based on extension
                String ext = filename.substring(filename.lastIndexOf('.')).toLowerCase();
                contentType = switch (ext) {
                    case ".mp4" -> "video/mp4";
                    case ".webm" -> "video/webm";
                    case ".avi" -> "video/x-msvideo";
                    case ".mov" -> "video/quicktime";
                    case ".mkv" -> "video/x-matroska";
                    case ".jpg", ".jpeg" -> "image/jpeg";
                    case ".png" -> "image/png";
                    case ".webp" -> "image/webp";
                    case ".bmp" -> "image/bmp";
                    default -> "application/octet-stream";
                };
            }

            long fileLength = file.length();

            // Handle Range requests (required for video playback in browsers)
            if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
                return handleRangeRequest(file, fileLength, contentType, rangeHeader);
            }

            // Full file response
            Resource resource = new FileSystemResource(file);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType(contentType));
            headers.setContentLength(fileLength);
            headers.set("Accept-Ranges", "bytes");

            return new ResponseEntity<>(resource, headers, HttpStatus.OK);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("detail", "Error serving file: " + e.getMessage()));
        }
    }

    /**
     * Handle HTTP Range request for partial content (video streaming).
     */
    private ResponseEntity<?> handleRangeRequest(File file, long fileLength,
                                                   String contentType, String rangeHeader) throws IOException {
        // Parse range: "bytes=start-end" or "bytes=start-"
        String rangeValue = rangeHeader.replace("bytes=", "").trim();
        String[] parts = rangeValue.split("-", 2);

        long start = Long.parseLong(parts[0]);
        long end = (parts.length > 1 && !parts[1].isEmpty())
                   ? Long.parseLong(parts[1])
                   : fileLength - 1;

        // Clamp
        if (end >= fileLength) end = fileLength - 1;
        if (start > end) start = end;

        long contentLength = end - start + 1;

        // Read the requested byte range
        byte[] data = new byte[(int) contentLength];
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            raf.seek(start);
            raf.readFully(data);
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(contentType));
        headers.setContentLength(contentLength);
        headers.set("Accept-Ranges", "bytes");
        headers.set("Content-Range", "bytes " + start + "-" + end + "/" + fileLength);

        return new ResponseEntity<>(data, headers, HttpStatus.PARTIAL_CONTENT);
    }
}
