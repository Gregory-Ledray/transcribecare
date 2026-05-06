package com.transcribecare.app.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity representing a recording session's metadata.
 * Segments are stored separately in [SegmentEntity] with a foreign key relationship.
 */
@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey val id: String,
    val title: String,
    val date: String,
    val time: String,
    val createdAt: Long,
    val duration: String,
    val audioFilePath: String?,
    val statusLabel: String
)
