package com.github.numq.vad

/**
 * Represents the operating ("aggressiveness") modes for voice activity detection.
 *
 * A more aggressive mode is more restrictive in reporting speech, meaning it
 * increases the likelihood that detected speech is actually speech (reducing
 * false positives). However, this also increases the likelihood of missed
 * detections (false negatives).
 */
enum class VoiceActivityDetectionMode {
    /**
     * Default mode. Balanced for general use.
     */
    QUALITY,

    /**
     * Optimized for lower bitrate applications.
     */
    LOW_BITRATE,

    /**
     * More aggressive, with stricter speech detection.
     */
    AGGRESSIVE,

    /**
     * Most aggressive, with the highest likelihood of reducing noise.
     */
    VERY_AGGRESSIVE
}