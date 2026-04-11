package com.skysentry.controller;

import com.skysentry.service.DatabaseService;
import com.skysentry.service.MediaService;
import com.skysentry.service.ModelManagerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * GET /health — Model and system health check.
 * Matches Python: app.get("/health")
 */
@RestController
public class HealthController {

    @Autowired
    private ModelManagerService modelManager;

    @Autowired
    private MediaService mediaService;

    @Autowired
    private DatabaseService databaseService;

    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "ok");
        response.put("model", modelManager.getHealth());
        response.put("media_count", mediaService.getMediaFiles().size());
        response.put("archive_count", databaseService.getScanCount());
        return response;
    }
}
