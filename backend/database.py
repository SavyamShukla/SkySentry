"""
SkySentry AI — Database (SQLite Archive)
Stores scan history for the ARCHIVE tab.
"""

import os
import json
import sqlite3
import logging
from datetime import datetime, timezone

logger = logging.getLogger("skysentry.database")

DB_PATH = os.path.join(os.path.dirname(os.path.abspath(__file__)), "archive.db")


def get_connection() -> sqlite3.Connection:
    """Get a SQLite connection with row_factory enabled."""
    conn = sqlite3.connect(DB_PATH)
    conn.row_factory = sqlite3.Row
    conn.execute("PRAGMA journal_mode=WAL")
    return conn


def init_db():
    """Create the scans table if it doesn't exist."""
    conn = get_connection()
    try:
        conn.execute("""
            CREATE TABLE IF NOT EXISTS scans (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                timestamp TEXT NOT NULL,
                scan_number INTEGER NOT NULL,
                media_file TEXT,
                media_type TEXT,
                detections TEXT NOT NULL DEFAULT '[]',
                top_class TEXT,
                top_confidence REAL,
                threat_level TEXT,
                sector TEXT,
                distance_km REAL,
                altitude_m REAL,
                latitude REAL,
                longitude REAL,
                inference_time_ms REAL,
                track_id TEXT
            )
        """)
        conn.commit()
        logger.info(f"Database initialized at {DB_PATH}")
    finally:
        conn.close()


def save_scan(data: dict) -> int:
    """
    Save a scan result to the archive.

    Args:
        data: dict with scan details (detections, media_file, threat_level, etc.)

    Returns:
        The inserted row ID
    """
    conn = get_connection()
    try:
        detections_json = json.dumps(data.get("detections", []))

        cursor = conn.execute("""
            INSERT INTO scans (
                timestamp, scan_number, media_file, media_type,
                detections, top_class, top_confidence, threat_level,
                sector, distance_km, altitude_m, latitude, longitude,
                inference_time_ms, track_id
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """, (
            data.get("timestamp", datetime.now(timezone.utc).isoformat()),
            data.get("scan_number", 0),
            data.get("media_file"),
            data.get("media_type"),
            detections_json,
            data.get("top_class"),
            data.get("top_confidence"),
            data.get("threat_level"),
            data.get("sector"),
            data.get("distance_km"),
            data.get("altitude_m"),
            data.get("latitude"),
            data.get("longitude"),
            data.get("inference_time_ms"),
            data.get("track_id"),
        ))
        conn.commit()
        row_id = cursor.lastrowid
        logger.info(f"Scan #{data.get('scan_number', '?')} saved to archive (id={row_id})")
        return row_id
    finally:
        conn.close()


def get_scans(limit: int = 50, offset: int = 0) -> list[dict]:
    """
    Retrieve recent scans from the archive, newest first.

    Args:
        limit: Maximum number of records to return
        offset: Number of records to skip

    Returns:
        List of scan dicts
    """
    conn = get_connection()
    try:
        rows = conn.execute(
            "SELECT * FROM scans ORDER BY id DESC LIMIT ? OFFSET ?",
            (limit, offset)
        ).fetchall()

        results = []
        for row in rows:
            scan = dict(row)
            # Parse detections JSON back to list
            try:
                scan["detections"] = json.loads(scan["detections"])
            except (json.JSONDecodeError, TypeError):
                scan["detections"] = []
            results.append(scan)

        return results
    finally:
        conn.close()


def get_scan(scan_id: int) -> dict | None:
    """
    Retrieve a single scan by ID.

    Args:
        scan_id: The scan row ID

    Returns:
        Scan dict, or None if not found
    """
    conn = get_connection()
    try:
        row = conn.execute(
            "SELECT * FROM scans WHERE id = ?", (scan_id,)
        ).fetchone()

        if row is None:
            return None

        scan = dict(row)
        try:
            scan["detections"] = json.loads(scan["detections"])
        except (json.JSONDecodeError, TypeError):
            scan["detections"] = []
        return scan
    finally:
        conn.close()


def get_scan_count() -> int:
    """Return total number of scans in the archive."""
    conn = get_connection()
    try:
        result = conn.execute("SELECT COUNT(*) FROM scans").fetchone()
        return result[0] if result else 0
    finally:
        conn.close()
