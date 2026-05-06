package com.transcribecare.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.transcribecare.app.data.entity.SegmentEntity
import com.transcribecare.app.data.entity.SessionEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for recording sessions and their transcript segments.
 */
@Dao
interface SessionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: SessionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSegments(segments: List<SegmentEntity>)

    /**
     * Inserts a session along with its segments in a single transaction.
     */
    @Transaction
    suspend fun insertSessionWithSegments(session: SessionEntity, segments: List<SegmentEntity>) {
        insertSession(session)
        insertSegments(segments)
    }

    /**
     * Returns all sessions sorted by createdAt descending (most recent first).
     */
    @Query("SELECT * FROM sessions ORDER BY createdAt DESC")
    fun getAllSessions(): Flow<List<SessionEntity>>

    /**
     * Returns a single session by its ID, or null if not found.
     */
    @Query("SELECT * FROM sessions WHERE id = :sessionId")
    suspend fun getSessionById(sessionId: String): SessionEntity?

    /**
     * Returns all segments belonging to a session, ordered by orderIndex.
     */
    @Query("SELECT * FROM segments WHERE sessionId = :sessionId ORDER BY orderIndex ASC")
    suspend fun getSegmentsBySessionId(sessionId: String): List<SegmentEntity>

    /**
     * Searches sessions by title or segment text (case-insensitive substring match).
     * Returns distinct sessions where the title matches OR at least one segment's text matches.
     */
    @Query(
        """
        SELECT DISTINCT s.* FROM sessions s
        LEFT JOIN segments seg ON s.id = seg.sessionId
        WHERE s.title LIKE '%' || :query || '%' COLLATE NOCASE
           OR seg.text LIKE '%' || :query || '%' COLLATE NOCASE
        ORDER BY s.createdAt DESC
        """
    )
    suspend fun searchSessions(query: String): List<SessionEntity>

    /**
     * Deletes a session by its ID. Associated segments are cascade-deleted.
     */
    @Query("DELETE FROM sessions WHERE id = :sessionId")
    suspend fun deleteSessionById(sessionId: String)
}
