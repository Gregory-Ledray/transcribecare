package com.transcribecare.app.model

import java.util.UUID

/**
 * Represents a complete recording session containing audio data, transcript segments, and metadata.
 *
 * @property id Unique identifier for the session.
 * @property title Display title for the session.
 * @property date Formatted display date (e.g., "Jun 15, 2025").
 * @property time Formatted display time (e.g., "02:30 PM").
 * @property createdAt Epoch milliseconds when the session was created, used for sorting.
 * @property duration Formatted duration string (e.g., "05:32").
 * @property audioFilePath Local file path to the recorded audio, or null if no audio was saved.
 * @property segments List of transcript segments captured during the session.
 * @property statusLabel Relative time label for display (e.g., "TODAY", "YESTERDAY", "THIS WEEK").
 */
data class RecordingSession(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val date: String,
    val time: String,
    val createdAt: Long,
    val duration: String,
    val audioFilePath: String? = null,
    val segments: List<TranscriptSegment> = emptyList(),
    val statusLabel: String
)
