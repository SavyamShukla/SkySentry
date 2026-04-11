package com.skysentry.service;

import ai.onnxruntime.*;
import com.skysentry.dto.Detection;
import com.skysentry.dto.InferenceResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.FloatBuffer;
import java.util.*;
import java.util.List;

/**
 * SkySentry AI — Inference Engine
 * Runs YOLO inference on images and returns structured detection results.
 * Direct replacement for Python's inference.py.
 *
 * Supports:
 *   - YOLOv10 output: [1, N, 6] where each row = [x1, y1, x2, y2, conf, class_id]
 *   - YOLOv8  output: [1, 4+num_classes, 8400] (with NMS postprocessing)
 */
@Service
public class InferenceService {

    private static final Logger logger = LoggerFactory.getLogger(InferenceService.class);

    @Value("${skysentry.confidence-threshold:0.25}")
    private float confidenceThreshold;

    /**
     * Run YOLO inference on a BufferedImage.
     *
     * @param session    The loaded ONNX session
     * @param env        ONNX runtime environment
     * @param classNames Class ID → name mapping from model metadata
     * @param image      The image to analyze
     * @return InferenceResult with detections, timing, and metadata
     */
    public InferenceResult runInference(OrtSession session, OrtEnvironment env,
                                        Map<Integer, String> classNames,
                                        BufferedImage image) {
        int origW = image.getWidth();
        int origH = image.getHeight();

        if (session == null) {
            InferenceResult err = new InferenceResult();
            err.setDetections(List.of());
            err.setModelClasses(List.of());
            err.setInferenceTimeMs(0);
            err.setImageSize(List.of(origW, origH));
            err.setError("Model not loaded");
            return err;
        }

        try {
            long startNano = System.nanoTime();

            // ── 1. Read model input dimensions ──
            String inputName = session.getInputNames().iterator().next();
            TensorInfo tensorInfo = (TensorInfo) session.getInputInfo().get(inputName).getInfo();
            int targetSize = (int) tensorInfo.getShape()[3]; // typically 640

            // ── 2. Letterbox: resize while preserving aspect ratio ──
            float scale = Math.min((float) targetSize / origW, (float) targetSize / origH);
            int newW = Math.round(origW * scale);
            int newH = Math.round(origH * scale);
            float padW = (targetSize - newW) / 2.0f;
            float padH = (targetSize - newH) / 2.0f;

            BufferedImage letterboxed = new BufferedImage(targetSize, targetSize, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = letterboxed.createGraphics();
            g.setColor(new Color(114, 114, 114)); // YOLO standard letterbox fill
            g.fillRect(0, 0, targetSize, targetSize);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(image,
                (int) padW, (int) padH, (int) padW + newW, (int) padH + newH,
                0, 0, origW, origH, null);
            g.dispose();

            // ── 3. Convert to CHW float tensor [1, 3, H, W], normalized to [0,1] ──
            float[] inputData = new float[3 * targetSize * targetSize];
            for (int y = 0; y < targetSize; y++) {
                for (int x = 0; x < targetSize; x++) {
                    int rgb = letterboxed.getRGB(x, y);
                    int idx = y * targetSize + x;
                    inputData[0 * targetSize * targetSize + idx] = ((rgb >> 16) & 0xFF) / 255.0f; // R
                    inputData[1 * targetSize * targetSize + idx] = ((rgb >> 8) & 0xFF) / 255.0f;  // G
                    inputData[2 * targetSize * targetSize + idx] = (rgb & 0xFF) / 255.0f;         // B
                }
            }

            // ── 4. Create ONNX tensor and run inference ──
            List<Detection> detections;
            try (OnnxTensor inputTensor = OnnxTensor.createTensor(
                    env, FloatBuffer.wrap(inputData), new long[]{1, 3, targetSize, targetSize});
                 OrtSession.Result results = session.run(Map.of(inputName, inputTensor))) {

                double elapsedMs = (System.nanoTime() - startNano) / 1_000_000.0;

                // ── 5. Parse output based on its shape ──
                OnnxTensor outputTensor = (OnnxTensor) results.get(0);
                long[] shape = outputTensor.getInfo().getShape();

                if (shape.length == 3 && shape[2] == 6) {
                    // YOLOv10 format: [1, N, 6] = [x1, y1, x2, y2, conf, class_id]
                    detections = parseYolov10(outputTensor, classNames, padW, padH, scale, origW, origH);
                } else if (shape.length == 3 && shape[1] > 6) {
                    // YOLOv8 format: [1, 4+num_classes, num_boxes]
                    detections = parseYolov8(outputTensor, classNames, padW, padH, scale, origW, origH);
                } else {
                    logger.warn("Unknown model output shape: {}", Arrays.toString(shape));
                    detections = new ArrayList<>();
                }

                // Sort by confidence (highest first)
                detections.sort((a, b) -> Double.compare(b.getConfidence(), a.getConfidence()));

                logger.info("Inference complete: {} detections in {}ms",
                           detections.size(), String.format("%.1f", elapsedMs));

                InferenceResult result = new InferenceResult();
                result.setDetections(detections);
                result.setModelClasses(new ArrayList<>(classNames.values()));
                result.setInferenceTimeMs(Math.round(elapsedMs * 10) / 10.0);
                result.setImageSize(List.of(origW, origH));
                return result;
            }

        } catch (Exception e) {
            logger.error("Inference error: {}", e.getMessage(), e);
            InferenceResult err = new InferenceResult();
            err.setDetections(List.of());
            err.setModelClasses(List.of());
            err.setInferenceTimeMs(0);
            err.setImageSize(List.of(origW, origH));
            err.setError(e.getMessage());
            return err;
        }
    }

    // ── YOLOv10 output parser ──────────────────────────────────
    // Output shape: [1, max_det (300), 6]
    // Each row: [x1, y1, x2, y2, confidence, class_id]
    // Already post-NMS — no duplicate removal needed.
    private List<Detection> parseYolov10(OnnxTensor tensor,
                                          Map<Integer, String> classNames,
                                          float padW, float padH, float scale,
                                          int origW, int origH) throws OrtException {
        float[][][] output = (float[][][]) tensor.getValue();
        List<Detection> detections = new ArrayList<>();

        for (float[] det : output[0]) {
            float conf = det[4];
            if (conf < confidenceThreshold) continue;

            // Un-letterbox: scale coordinates back to original image space
            double x1 = clamp((det[0] - padW) / scale, 0, origW);
            double y1 = clamp((det[1] - padH) / scale, 0, origH);
            double x2 = clamp((det[2] - padW) / scale, 0, origW);
            double y2 = clamp((det[3] - padH) / scale, 0, origH);

            int classId = Math.round(det[5]);
            String className = classNames.getOrDefault(classId, "class_" + classId);

            detections.add(new Detection(
                className, classId,
                round4(conf),
                List.of(round2(x1), round2(y1), round2(x2), round2(y2))
            ));
        }
        return detections;
    }

    // ── YOLOv8 output parser ──────────────────────────────────
    // Output shape: [1, 4+num_classes, num_boxes (8400)]
    // Row 0..3: cx, cy, w, h (center format)
    // Row 4+: class probabilities
    // Requires NMS postprocessing.
    private List<Detection> parseYolov8(OnnxTensor tensor,
                                         Map<Integer, String> classNames,
                                         float padW, float padH, float scale,
                                         int origW, int origH) throws OrtException {
        float[][][] raw = (float[][][]) tensor.getValue();
        float[][] data = raw[0]; // [4+num_classes, num_boxes]

        int numClasses = data.length - 4;
        int numBoxes = data[0].length;
        List<Detection> candidates = new ArrayList<>();

        for (int i = 0; i < numBoxes; i++) {
            // Find best class
            int bestClass = 0;
            float bestScore = 0;
            for (int c = 0; c < numClasses; c++) {
                float score = data[4 + c][i];
                if (score > bestScore) {
                    bestScore = score;
                    bestClass = c;
                }
            }

            if (bestScore < confidenceThreshold) continue;

            // Center-format to corner-format
            float cx = data[0][i], cy = data[1][i], w = data[2][i], h = data[3][i];
            float bx1 = cx - w / 2, by1 = cy - h / 2;
            float bx2 = cx + w / 2, by2 = cy + h / 2;

            // Un-letterbox
            double x1 = clamp((bx1 - padW) / scale, 0, origW);
            double y1 = clamp((by1 - padH) / scale, 0, origH);
            double x2 = clamp((bx2 - padW) / scale, 0, origW);
            double y2 = clamp((by2 - padH) / scale, 0, origH);

            String className = classNames.getOrDefault(bestClass, "class_" + bestClass);
            candidates.add(new Detection(
                className, bestClass,
                round4(bestScore),
                List.of(round2(x1), round2(y1), round2(x2), round2(y2))
            ));
        }

        // Simple NMS (greedy, per-class)
        return nms(candidates, 0.45);
    }

    // ── Non-Maximum Suppression ──────────────────────────────────
    private List<Detection> nms(List<Detection> detections, double iouThreshold) {
        detections.sort((a, b) -> Double.compare(b.getConfidence(), a.getConfidence()));
        List<Detection> result = new ArrayList<>();

        boolean[] suppressed = new boolean[detections.size()];
        for (int i = 0; i < detections.size(); i++) {
            if (suppressed[i]) continue;
            result.add(detections.get(i));
            for (int j = i + 1; j < detections.size(); j++) {
                if (suppressed[j]) continue;
                if (detections.get(i).getClassId() == detections.get(j).getClassId()
                    && iou(detections.get(i).getBbox(), detections.get(j).getBbox()) > iouThreshold) {
                    suppressed[j] = true;
                }
            }
        }
        return result;
    }

    private double iou(List<Double> a, List<Double> b) {
        double x1 = Math.max(a.get(0), b.get(0));
        double y1 = Math.max(a.get(1), b.get(1));
        double x2 = Math.min(a.get(2), b.get(2));
        double y2 = Math.min(a.get(3), b.get(3));
        double inter = Math.max(0, x2 - x1) * Math.max(0, y2 - y1);
        double areaA = (a.get(2) - a.get(0)) * (a.get(3) - a.get(1));
        double areaB = (b.get(2) - b.get(0)) * (b.get(3) - b.get(1));
        return inter / (areaA + areaB - inter + 1e-6);
    }

    // ── Utility ──
    private double clamp(double val, double min, double max) {
        return Math.max(min, Math.min(val, max));
    }

    private double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private double round4(float v) {
        return Math.round(v * 10000.0) / 10000.0;
    }
}
