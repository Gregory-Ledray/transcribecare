package com.transcribecare.app.model

import java.util.UUID

/**
 * A discrete unit of transcribed text with a classification indicating its recency.
 *
 * @property id Unique identifier for the segment.
 * @property text The transcribed text content.
 * @property type Classification of the segment (PAST, RECENT, or CURRENT).
 * @property timestamp Epoch milliseconds when the segment was finalized.
 */
data class TranscriptSegment(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val type: SegmentType,
    val timestamp: Long
)
