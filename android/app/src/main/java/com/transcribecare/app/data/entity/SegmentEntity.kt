package com.transcribecare.app.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity representing a transcript segment belonging to a session.
 * Cascade delete ensures segments are removed when their parent session is deleted.
 */
@Entity(
    tableName = "segments",
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["sessionId"])]
)
data class SegmentEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val text: String,
    val type: String, // "past", "recent", "current"
    val timestamp: Long,
    val orderIndex: Int
)
