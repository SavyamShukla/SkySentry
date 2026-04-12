package com.skysentry.util;

import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.Java2DFrameConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.image.BufferedImage;

/**
 * SkySentry AI — Video Frame Extractor
 * Extracts a single frame from a video file at a given timestamp.
 * Uses JavaCV (FFmpeg) — direct replacement for Python's cv2.VideoCapture.
 */
public class VideoFrameExtractor {

    private static final Logger logger = LoggerFactory.getLogger(VideoFrameExtractor.class);

    /**
     * Extract a frame from a video at the specified time.
     *
     * @param videoPath Absolute path to the video file
     * @param timeSec   Time in seconds to extract the frame from
     * @return BufferedImage of the extracted frame, or null on failure
     */
    public static BufferedImage extractFrame(String videoPath, double timeSec) {
        try (FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(videoPath)) {
            grabber.start();

            double duration = grabber.getLengthInTime() / 1_000_000.0; // microseconds → seconds
            double targetTime = timeSec;

            // Clamp: if video is shorter than requested time, go to middle
            if (targetTime >= duration && duration > 0) {
                targetTime = duration / 2.0;
            }

            // Seek to target timestamp (in microseconds)
            long timestampMicros = (long) (targetTime * 1_000_000);
            grabber.setTimestamp(timestampMicros);

            // Grab the nearest image frame
            Frame frame = grabber.grabImage();
            if (frame != null && frame.image != null) {
                Java2DFrameConverter converter = new Java2DFrameConverter();
                BufferedImage image = converter.convert(frame);
                logger.info("Extracted frame at {}s from {}", String.format("%.1f", targetTime), videoPath);
                return image;
            } else {
                logger.error("Failed to read frame at {}s from {}", timeSec, videoPath);
                return null;
            }

        } catch (Exception e) {
            logger.error("Error extracting video frame from {}: {}", videoPath, e.getMessage(), e);
            return null;
        }
    }
}
