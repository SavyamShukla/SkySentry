package com.skysentry.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Full inference result returned by /analyze endpoints.
 * JSON shape matches the Python backend exactly.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class InferenceResult {

    private List<Detection> detections;

    @JsonProperty("model_classes")
    private List<String> modelClasses;

    @JsonProperty("inference_time_ms")
    private double inferenceTimeMs;

    @JsonProperty("image_size")
    private List<Integer> imageSize;

    /** Set when analyzing a media file from disk */
    @JsonProperty("media_file")
    private String mediaFile;

    /** "image" or "video" */
    @JsonProperty("media_type")
    private String mediaType;

    /** Non-null only on error */
    private String error;

    public InferenceResult() {}

    // ── Getters ──
    public List<Detection> getDetections()   { return detections; }
    public List<String> getModelClasses()    { return modelClasses; }
    public double getInferenceTimeMs()       { return inferenceTimeMs; }
    public List<Integer> getImageSize()      { return imageSize; }
    public String getMediaFile()             { return mediaFile; }
    public String getMediaType()             { return mediaType; }
    public String getError()                 { return error; }

    // ── Setters ──
    public void setDetections(List<Detection> detections)       { this.detections = detections; }
    public void setModelClasses(List<String> modelClasses)      { this.modelClasses = modelClasses; }
    public void setInferenceTimeMs(double inferenceTimeMs)      { this.inferenceTimeMs = inferenceTimeMs; }
    public void setImageSize(List<Integer> imageSize)            { this.imageSize = imageSize; }
    public void setMediaFile(String mediaFile)                   { this.mediaFile = mediaFile; }
    public void setMediaType(String mediaType)                   { this.mediaType = mediaType; }
    public void setError(String error)                           { this.error = error; }
}
