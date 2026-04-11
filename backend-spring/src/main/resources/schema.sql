-- SkySentry AI — Archive Database Schema
-- Matches the Python backend's SQLite schema exactly

CREATE TABLE IF NOT EXISTS scans (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    timestamp       TEXT    NOT NULL,
    scan_number     INTEGER NOT NULL,
    media_file      TEXT,
    media_type      TEXT,
    detections      TEXT    NOT NULL DEFAULT '[]',
    top_class       TEXT,
    top_confidence  REAL,
    threat_level    TEXT,
    sector          TEXT,
    distance_km     REAL,
    altitude_m      REAL,
    latitude        REAL,
    longitude       REAL,
    inference_time_ms REAL,
    track_id        TEXT
);
