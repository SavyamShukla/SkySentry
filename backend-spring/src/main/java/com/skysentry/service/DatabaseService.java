package com.skysentry.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

/**
 * SkySentry AI — Database Service (SQLite Archive)
 * Stores scan history for the ARCHIVE tab.
 * Direct replacement for Python's database.py using Spring JdbcTemplate.
 */
@Service
public class DatabaseService {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private JdbcTemplate jdbc;

    @PostConstruct
    public void init() {
        // Enable WAL mode for better concurrency (matches Python: PRAGMA journal_mode=WAL)
        try {
            jdbc.execute("PRAGMA journal_mode=WAL");
            logger.info("Database initialized with WAL mode");
        } catch (Exception e) {
            logger.warn("Could not set WAL mode: {}", e.getMessage());
        }
    }

    /**
     * Save a scan result to the archive.
     * @return the inserted row ID
     */
    public int saveScan(Map<String, Object> data) {
        String detectionsJson;
        try {
            detectionsJson = objectMapper.writeValueAsString(data.getOrDefault("detections", List.of()));
        } catch (JsonProcessingException e) {
            detectionsJson = "[]";
        }

        String timestamp = (String) data.getOrDefault("timestamp", Instant.now().toString());

        jdbc.update("""
            INSERT INTO scans (
                timestamp, scan_number, media_file, media_type,
                detections, top_class, top_confidence, threat_level,
                sector, distance_km, altitude_m, latitude, longitude,
                inference_time_ms, track_id
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            timestamp,
            toInt(data.get("scan_number"), 0),
            data.get("media_file"),
            data.get("media_type"),
            detectionsJson,
            data.get("top_class"),
            toDouble(data.get("top_confidence")),
            data.get("threat_level"),
            data.get("sector"),
            toDouble(data.get("distance_km")),
            toDouble(data.get("altitude_m")),
            toDouble(data.get("latitude")),
            toDouble(data.get("longitude")),
            toDouble(data.get("inference_time_ms")),
            data.get("track_id")
        );

        Integer rowId = jdbc.queryForObject("SELECT last_insert_rowid()", Integer.class);
        int id = rowId != null ? rowId : 0;
        logger.info("Scan #{} saved to archive (id={})", data.get("scan_number"), id);
        return id;
    }

    /**
     * Retrieve recent scans from the archive, newest first.
     */
    public List<Map<String, Object>> getScans(int limit, int offset) {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT * FROM scans ORDER BY id DESC LIMIT ? OFFSET ?", limit, offset
        );

        // Parse detections JSON back to list
        for (Map<String, Object> row : rows) {
            Object dets = row.get("detections");
            if (dets instanceof String s) {
                try {
                    row.put("detections", objectMapper.readValue(s, new TypeReference<List<Object>>() {}));
                } catch (Exception e) {
                    row.put("detections", List.of());
                }
            }
        }
        return rows;
    }

    /**
     * Retrieve a single scan by ID.
     */
    public Map<String, Object> getScan(int scanId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT * FROM scans WHERE id = ?", scanId
        );
        if (rows.isEmpty()) return null;

        Map<String, Object> row = rows.get(0);
        Object dets = row.get("detections");
        if (dets instanceof String s) {
            try {
                row.put("detections", objectMapper.readValue(s, new TypeReference<List<Object>>() {}));
            } catch (Exception e) {
                row.put("detections", List.of());
            }
        }
        return row;
    }

    /**
     * Return total number of scans in the archive.
     */
    public int getScanCount() {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM scans", Integer.class);
        return count != null ? count : 0;
    }

    // ── Helpers ──
    private int toInt(Object val, int def) {
        if (val instanceof Number n) return n.intValue();
        if (val instanceof String s) { try { return Integer.parseInt(s); } catch (Exception e) { /* ignore */ } }
        return def;
    }

    private Double toDouble(Object val) {
        if (val == null) return null;
        if (val instanceof Number n) return n.doubleValue();
        if (val instanceof String s) { try { return Double.parseDouble(s); } catch (Exception e) { /* ignore */ } }
        return null;
    }
}
