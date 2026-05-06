package com.transcribecare.app.model

/**
 * Represents the classification of a transcript segment based on its recency.
 *
 * When a new finalized transcript result arrives:
 * - Previous CURRENT segments become RECENT
 * - Previous RECENT segments become PAST
 * - The new segment is classified as CURRENT
 */
enum class SegmentType {
    /** Previously finalized segments from earlier in the session. */
    PAST,

    /** Recently finalized segment (was "current", now superseded). */
    RECENT,

    /** Most recently finalized segment. */
    CURRENT
}
