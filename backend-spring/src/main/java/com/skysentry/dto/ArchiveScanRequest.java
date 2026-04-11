package com.skysentry.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.List;

/**
 * Request body for POST /archive.
 * Matches the Python Pydantic model exactly.
 */
public class ArchiveScanRequest {

    @JsonProperty("scan_number")
    private int scanNumber;

    @JsonProperty("media_file")
    private String mediaFile;

    @JsonProperty("media_type")
    private String mediaType;

    private List<Object> detections = new ArrayList<>();

    @JsonProperty("top_class")
    private String topClass;

    @JsonProperty("top_confidence")
    private Double topConfidence;

    @JsonProperty("threat_level")
    private String threatLevel;

    private String sector;

    @JsonProperty("distance_km")
    private Double distanceKm;

    @JsonProperty("altitude_m")
    private Double altitudeM;

    private Double latitude;
    private Double longitude;

    @JsonProperty("inference_time_ms")
    private Double inferenceTimeMs;

    @JsonProperty("track_id")
    private String trackId;

    // ── Getters ──
    public int getScanNumber()          { return scanNumber; }
    public String getMediaFile()        { return mediaFile; }
    public String getMediaType()        { return mediaType; }
    public List<Object> getDetections() { return detections; }
    public String getTopClass()         { return topClass; }
    public Double getTopConfidence()    { return topConfidence; }
    public String getThreatLevel()      { return threatLevel; }
    public String getSector()           { return sector; }
    public Double getDistanceKm()       { return distanceKm; }
    public Double getAltitudeM()        { return altitudeM; }
    public Double getLatitude()         { return latitude; }
    public Double getLongitude()        { return longitude; }
    public Double getInferenceTimeMs()  { return inferenceTimeMs; }
    public String getTrackId()          { return trackId; }

    // ── Setters ──
    public void setScanNumber(int scanNumber)              { this.scanNumber = scanNumber; }
    public void setMediaFile(String mediaFile)             { this.mediaFile = mediaFile; }
    public void setMediaType(String mediaType)             { this.mediaType = mediaType; }
    public void setDetections(List<Object> detections)     { this.detections = detections; }
    public void setTopClass(String topClass)               { this.topClass = topClass; }
    public void setTopConfidence(Double topConfidence)      { this.topConfidence = topConfidence; }
    public void setThreatLevel(String threatLevel)         { this.threatLevel = threatLevel; }
    public void setSector(String sector)                   { this.sector = sector; }
    public void setDistanceKm(Double distanceKm)           { this.distanceKm = distanceKm; }
    public void setAltitudeM(Double altitudeM)             { this.altitudeM = altitudeM; }
    public void setLatitude(Double latitude)               { this.latitude = latitude; }
    public void setLongitude(Double longitude)             { this.longitude = longitude; }
    public void setInferenceTimeMs(Double inferenceTimeMs) { this.inferenceTimeMs = inferenceTimeMs; }
    public void setTrackId(String trackId)                 { this.trackId = trackId; }
}
