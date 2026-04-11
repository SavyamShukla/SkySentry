package com.skysentry.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * SkySentry AI — Media Service
 * Scans the media directory for supported image and video files.
 * Replaces the get_media_files() helper in Python's main.py.
 */
@Service
public class MediaService {

    private static final Logger logger = LoggerFactory.getLogger(MediaService.class);

    private static final Set<String> IMAGE_EXTENSIONS = Set.of(
        ".jpg", ".jpeg", ".png", ".bmp", ".webp"
    );
    private static final Set<String> VIDEO_EXTENSIONS = Set.of(
        ".mp4", ".avi", ".mov", ".webm", ".mkv"
    );

    @Value("${skysentry.media-dir:media}")
    private String mediaDirPath;

    private Path mediaDir;

    @PostConstruct
    public void init() {
        mediaDir = Paths.get(mediaDirPath).toAbsolutePath().normalize();
        logger.info("Media directory configured at: {}", mediaDir);
        if (!mediaDir.toFile().isDirectory()) {
            logger.warn("Media directory does not exist: {}", mediaDir);
        }
    }

    /**
     * Get the absolute path to the media directory.
     */
    public Path getMediaDir() {
        return mediaDir;
    }

    /**
     * Scan the media directory for supported files.
     * Returns list of file info maps matching Python's get_media_files() output.
     */
    public List<Map<String, Object>> getMediaFiles() {
        File dir = mediaDir.toFile();
        if (!dir.isDirectory()) return List.of();

        List<Map<String, Object>> files = new ArrayList<>();
        File[] children = dir.listFiles();
        if (children == null) return files;

        for (File f : children) {
            if (!f.isFile()) continue;
            String name = f.getName();
            String ext = getExtension(name).toLowerCase();

            if (IMAGE_EXTENSIONS.contains(ext) || VIDEO_EXTENSIONS.contains(ext)) {
                Map<String, Object> info = new LinkedHashMap<>();
                info.put("filename", name);
                info.put("path", f.getAbsolutePath());
                info.put("type", IMAGE_EXTENSIONS.contains(ext) ? "image" : "video");
                info.put("extension", ext);
                info.put("size_bytes", f.length());
                files.add(info);
            }
        }
        return files;
    }

    /**
     * Pick a random media file.
     */
    public Map<String, Object> getRandomMedia() {
        List<Map<String, Object>> files = getMediaFiles();
        if (files.isEmpty()) return null;
        return files.get(new Random().nextInt(files.size()));
    }

    /**
     * Resolve a filename to an absolute path, with path traversal protection.
     * Returns null if the file doesn't exist or is outside the media directory.
     */
    public File resolveMediaFile(String filename) {
        Path filepath = mediaDir.resolve(filename).normalize();

        // Security: prevent path traversal
        if (!filepath.startsWith(mediaDir)) return null;

        File f = filepath.toFile();
        return f.exists() && f.isFile() ? f : null;
    }

    /**
     * Check if a file extension is an image type.
     */
    public boolean isImage(String filename) {
        return IMAGE_EXTENSIONS.contains(getExtension(filename).toLowerCase());
    }

    /**
     * Check if a file extension is a video type.
     */
    public boolean isVideo(String filename) {
        return VIDEO_EXTENSIONS.contains(getExtension(filename).toLowerCase());
    }

    private String getExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot) : "";
    }
}
