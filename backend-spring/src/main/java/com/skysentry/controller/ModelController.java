package com.skysentry.controller;

import com.skysentry.service.ModelManagerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * POST /reload — Force reload the YOLO model from disk.
 * Matches Python: app.post("/reload")
 */
@RestController
public class ModelController {

    @Autowired
    private ModelManagerService modelManager;

    @PostMapping("/reload")
    public Map<String, Object> reloadModel() {
        boolean success = modelManager.loadModel();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", success);
        response.put("model", modelManager.getHealth());
        return response;
    }
}
