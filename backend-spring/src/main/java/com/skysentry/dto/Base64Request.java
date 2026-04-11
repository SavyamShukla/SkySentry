package com.skysentry.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Request body for POST /analyze/base64.
 * Matches Pydantic model: Base64Request(image: str, media_type: str).
 */
public class Base64Request {

    /** Base64-encoded image data (may include data URI prefix) */
    private String image;

    /** MIME type, e.g. "image/jpeg" */
    @JsonProperty("media_type")
    private String mediaType = "image/jpeg";

    public Base64Request() {}

    public String getImage()     { return image; }
    public String getMediaType() { return mediaType; }

    public void setImage(String image)           { this.image = image; }
    public void setMediaType(String mediaType)   { this.mediaType = mediaType; }
}
