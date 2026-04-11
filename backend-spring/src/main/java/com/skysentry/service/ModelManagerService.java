package com.skysentry.service;

import ai.onnxruntime.*;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

/**
 * SkySentry AI — Model Manager Service
 * Handles YOLO ONNX model lifecycle: loading, reloading, health checks.
 * Direct replacement for Python's model_manager.py.
 *
 * The model file (best.onnx) can be hot-swapped without restarting the server.
 */
@Service
public class ModelManagerService {

    private static final Logger logger = LoggerFactory.getLogger(ModelManagerService.class);

    @Value("${skysentry.model-path:../best.onnx}")
    private String modelPathConfig;

    private File modelFile;
    private OrtEnvironment env;
    private OrtSession session;
    private Map<Integer, String> classNames = new HashMap<>();
    private final ReentrantLock lock = new ReentrantLock();
    private long lastMtime = 0;
    private long lastCheckTime = 0;
    private String loadError = null;
    private boolean isLoading = false;

    private static final long AUTO_RELOAD_COOLDOWN_MS = 5000;

    @PostConstruct
    public void init() {
        modelFile = new File(modelPathConfig).getAbsoluteFile();
        env = OrtEnvironment.getEnvironment();
        logger.info("SkySentry AI Backend starting...");
        loadModel();
        logger.info("Startup complete.");
    }

    @PreDestroy
    public void destroy() {
        lock.lock();
        try {
            if (session != null) {
                session.close();
                session = null;
            }
        } catch (Exception e) {
            logger.warn("Error closing ONNX session: {}", e.getMessage());
        } finally {
            lock.unlock();
        }
    }

    /**
     * Load or reload the ONNX model from disk.
     * @return true if successful
     */
    public boolean loadModel() {
        lock.lock();
        if (isLoading) {
            lock.unlock();
            logger.warn("Model is already being loaded, skipping.");
            return false;
        }
        isLoading = true;
        lock.unlock();

        try {
            if (!modelFile.exists()) {
                loadError = "Model file not found: " + modelFile.getAbsolutePath();
                logger.error(loadError);
                return false;
            }

            logger.info("Loading ONNX model from: {}", modelFile.getAbsolutePath());
            long start = System.currentTimeMillis();

            OrtSession.SessionOptions opts = new OrtSession.SessionOptions();
            opts.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.BASIC_OPT);

            OrtSession newSession = env.createSession(modelFile.getAbsolutePath(), opts);

            // Extract class names from model metadata
            Map<Integer, String> newClassNames = new HashMap<>();
            try {
                Map<String, String> metadata = newSession.getMetadata().getCustomMetadata();
                String names = metadata.get("names");
                if (names != null) {
                    newClassNames = parseClassNames(names);
                }
            } catch (Exception e) {
                logger.warn("Could not read class names from model metadata: {}", e.getMessage());
            }

            long elapsed = System.currentTimeMillis() - start;

            lock.lock();
            try {
                // Close old session
                if (session != null) {
                    try { session.close(); } catch (Exception ignored) {}
                }
                session = newSession;
                classNames = newClassNames;
                lastMtime = modelFile.lastModified();
                loadError = null;
            } finally {
                lock.unlock();
            }

            logger.info("Model loaded successfully in {}ms. Classes: {}",
                        elapsed, new ArrayList<>(classNames.values()));
            return true;

        } catch (Exception e) {
            loadError = "Failed to load model: " + e.getMessage();
            logger.error(loadError, e);
            return false;
        } finally {
            lock.lock();
            try { isLoading = false; } finally { lock.unlock(); }
        }
    }

    /**
     * Get the loaded ONNX session. Auto-checks for file changes.
     * @return OrtSession or null if model is not available
     */
    public OrtSession getSession() {
        checkForUpdates();
        return session;
    }

    /**
     * Get the ONNX runtime environment.
     */
    public OrtEnvironment getEnvironment() {
        return env;
    }

    /**
     * Get the model's class name mapping.
     */
    public Map<Integer, String> getClassNames() {
        return Collections.unmodifiableMap(classNames);
    }

    /**
     * Return health/status information about the model.
     * JSON shape matches the Python backend exactly.
     */
    public Map<String, Object> getHealth() {
        Map<String, Object> health = new LinkedHashMap<>();
        health.put("model_loaded", session != null);
        health.put("model_path", modelFile.getAbsolutePath());
        health.put("model_file_exists", modelFile.exists());
        health.put("model_classes", new ArrayList<>(classNames.values()));
        health.put("last_modified", lastMtime > 0 ? (double) lastMtime / 1000.0 : null);
        health.put("error", loadError);
        health.put("is_loading", isLoading);
        return health;
    }

    /**
     * Check if the model file has been modified and reload if needed.
     */
    private void checkForUpdates() {
        long now = System.currentTimeMillis();
        if (now - lastCheckTime < AUTO_RELOAD_COOLDOWN_MS) return;
        lastCheckTime = now;

        try {
            if (!modelFile.exists()) return;
            long currentMtime = modelFile.lastModified();
            if (currentMtime != lastMtime && lastMtime > 0) {
                logger.info("Model file changed on disk, reloading...");
                loadModel();
            }
        } catch (Exception e) {
            logger.warn("Error checking model file: {}", e.getMessage());
        }
    }

    /**
     * Parse class names from ONNX metadata.
     * Ultralytics exports store them as: {0: 'drone', 1: 'bird', 2: 'aircraft'}
     */
    private Map<Integer, String> parseClassNames(String raw) {
        Map<Integer, String> result = new HashMap<>();
        try {
            // Remove outer braces
            String s = raw.trim();
            if (s.startsWith("{")) s = s.substring(1);
            if (s.endsWith("}")) s = s.substring(0, s.length() - 1);

            // Split by comma, parse key: value pairs
            for (String pair : s.split(",")) {
                pair = pair.trim();
                if (pair.isEmpty()) continue;
                String[] kv = pair.split(":", 2);
                if (kv.length == 2) {
                    int id = Integer.parseInt(kv[0].trim());
                    String name = kv[1].trim()
                        .replace("'", "")
                        .replace("\"", "");
                    result.put(id, name);
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to parse class names from: {}", raw);
        }
        return result;
    }
}
