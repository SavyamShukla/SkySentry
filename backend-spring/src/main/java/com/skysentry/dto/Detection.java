package com.skysentry.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * A single object detection from YOLO inference.
 * JSON shape matches the Python backend exactly.
 */
public class Detection {

    /** Model class name (e.g. "drone", "bird") */
    @JsonProperty("class")
    private String className;

    /** Numeric class ID from the model */
    @JsonProperty("class_id")
    private int classId;

    /** Confidence score 0..1 */
    private double confidence;

    /** Bounding box [x1, y1, x2, y2] in original image coordinates */
    private List<Double> bbox;

    public Detection() {}

    public Detection(String className, int classId, double confidence, List<Double> bbox) {
        this.className = className;
        this.classId = classId;
        this.confidence = confidence;
        this.bbox = bbox;
    }

    public String getClassName()    { return className; }
    public int getClassId()         { return classId; }
    public double getConfidence()   { return confidence; }
    public List<Double> getBbox()   { return bbox; }

    public void setClassName(String className)      { this.className = className; }
    public void setClassId(int classId)              { this.classId = classId; }
    public void setConfidence(double confidence)     { this.confidence = confidence; }
    public void setBbox(List<Double> bbox)            { this.bbox = bbox; }
}
