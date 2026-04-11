package com.skysentry.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skysentry.dto.ArchiveScanRequest;
import com.skysentry.service.DatabaseService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Archive endpoints — CRUD for scan history.
 * Matches Python: POST /archive, GET /archive, GET /archive/{scan_id}
 */
@RestController
public class ArchiveController {

    @Autowired
    private DatabaseService databaseService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * POST /archive — Save a scan result to the archive database.
     */
    @PostMapping("/archive")
    public ResponseEntity<?> archiveScan(@RequestBody ArchiveScanRequest scan) {
        try {
            // Convert the request to a map for the database service
            @SuppressWarnings("unchecked")
            Map<String, Object> data = objectMapper.convertValue(scan, Map.class);

            int rowId = databaseService.saveScan(data);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("id", rowId);
            response.put("status", "saved");
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("detail", "Failed to save scan: " + e.getMessage()));
        }
    }

    /**
     * GET /archive — Retrieve archived scans, newest first.
     */
    @GetMapping("/archive")
    public Map<String, Object> getArchive(
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "0") int offset) {

        limit = Math.max(1, Math.min(limit, 200));
        offset = Math.max(0, offset);

        List<Map<String, Object>> scans = databaseService.getScans(limit, offset);
        int total = databaseService.getScanCount();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("scans", scans);
        response.put("total", total);
        response.put("limit", limit);
        response.put("offset", offset);
        return response;
    }

    /**
     * GET /archive/{scan_id} — Retrieve a single archived scan by ID.
     */
    @GetMapping("/archive/{scanId}")
    public ResponseEntity<?> getArchivedScan(@PathVariable int scanId) {
        Map<String, Object> scan = databaseService.getScan(scanId);
        if (scan == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("detail", "Scan " + scanId + " not found"));
        }
        return ResponseEntity.ok(scan);
    }
}
