package com.transcribecare.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.transcribecare.app.data.AppDatabase
import com.transcribecare.app.data.entity.SegmentEntity
import com.transcribecare.app.data.entity.SessionEntity
import com.transcribecare.app.model.RecordingSession
import com.transcribecare.app.model.SegmentType
import com.transcribecare.app.model.TranscriptSegment
import com.transcribecare.app.service.AudioPlayerService
import com.transcribecare.app.service.AudioPlayerState
import com.transcribecare.app.service.ShareService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for the History screen managing session list, search filtering,
 * audio playback state, and sharing.
 *
 * Extends AndroidViewModel to access the Application context for database access.
 */
class HistoryViewModel(application: Application) : AndroidViewModel(application) {

    private val sessionDao = AppDatabase.getInstance(application).sessionDao()

    private val audioPlayerService = AudioPlayerService(viewModelScope)
    val shareService = ShareService()

    private val _sessions = MutableStateFlow<List<RecordingSession>>(emptyList())
    val sessions: StateFlow<List<RecordingSession>> = _sessions.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _filteredSessions = MutableStateFlow<List<RecordingSession>>(emptyList())
    val filteredSessions: StateFlow<List<RecordingSession>> = _filteredSessions.asStateFlow()

    private val _currentPlaybackSessionId = MutableStateFlow<String?>(null)
    val currentPlaybackSessionId: StateFlow<String?> = _currentPlaybackSessionId.asStateFlow()

    /**
     * Exposes the audio player state from the AudioPlayerService.
     */
    val audioPlayerState: StateFlow<AudioPlayerState> = audioPlayerService.state

    /**
     * Convenience property exposing whether audio is currently playing.
     * Derived from the AudioPlayerService state.
     */
    val isPlaying: StateFlow<Boolean>
        get() = _isPlayingDerived

    private val _isPlayingDerived = MutableStateFlow(false)

    init {
        loadSessions()
        observePlaybackState()
    }

    /**
     * Observes the AudioPlayerService state and keeps the isPlaying derived state in sync.
     */
    private fun observePlaybackState() {
        viewModelScope.launch {
            audioPlayerService.state.collect { state ->
                _isPlayingDerived.value = state.isPlaying
            }
        }
    }

    /**
     * Collects sessions from the Room database Flow and converts entities to domain models.
     */
    private fun loadSessions() {
        viewModelScope.launch {
            sessionDao.getAllSessions().collect { sessionEntities ->
                val domainSessions = sessionEntities.map { entity ->
                    convertToDomainModel(entity)
                }
                _sessions.value = domainSessions
                _filteredSessions.value = filterSessions(domainSessions, _searchQuery.value)
            }
        }
    }

    /**
     * Converts a SessionEntity (and its associated SegmentEntities) into a RecordingSession domain model.
     */
    private suspend fun convertToDomainModel(entity: SessionEntity): RecordingSession {
        val segmentEntities = sessionDao.getSegmentsBySessionId(entity.id)
        val segments = segmentEntities.map { segEntity ->
            convertSegmentToDomainModel(segEntity)
        }
        return RecordingSession(
            id = entity.id,
            title = entity.title,
            date = entity.date,
            time = entity.time,
            createdAt = entity.createdAt,
            duration = entity.duration,
            audioFilePath = entity.audioFilePath,
            segments = segments,
            statusLabel = entity.statusLabel
        )
    }

    /**
     * Converts a SegmentEntity into a TranscriptSegment domain model.
     */
    private fun convertSegmentToDomainModel(entity: SegmentEntity): TranscriptSegment {
        val segmentType = when (entity.type.lowercase()) {
            "past" -> SegmentType.PAST
            "recent" -> SegmentType.RECENT
            "current" -> SegmentType.CURRENT
            else -> SegmentType.PAST
        }
        return TranscriptSegment(
            id = entity.id,
            text = entity.text,
            type = segmentType,
            timestamp = entity.timestamp
        )
    }

    /**
     * Updates the search query and recomputes the filtered session list.
     *
     * When the query is empty, all sessions are returned.
     * Otherwise, sessions are filtered by case-insensitive substring match
     * on the session title OR any segment's text.
     *
     * @param query The search query string.
     */
    fun search(query: String) {
        _searchQuery.value = query
        _filteredSessions.value = filterSessions(_sessions.value, query)
    }

    /**
     * Sets the full list of sessions (e.g., loaded from the database)
     * and recomputes the filtered list based on the current query.
     *
     * @param sessions The complete list of recording sessions.
     */
    fun setSessions(sessions: List<RecordingSession>) {
        _sessions.value = sessions
        _filteredSessions.value = filterSessions(sessions, _searchQuery.value)
    }

    // --- Audio Playback ---

    /**
     * Starts playback of the given session's audio file.
     * If the session has no audio file path, this is a no-op.
     *
     * @param session The recording session to play.
     */
    fun playSession(session: RecordingSession) {
        val filePath = session.audioFilePath ?: return
        _currentPlaybackSessionId.value = session.id
        audioPlayerService.play(filePath)
    }

    /**
     * Pauses the current audio playback.
     */
    fun pausePlayback() {
        audioPlayerService.pause()
    }

    /**
     * Resumes the current audio playback.
     */
    fun resumePlayback() {
        audioPlayerService.resume()
    }

    /**
     * Sets the playback speed. Only supported speeds (1.0, 1.25, 1.5, 2.0) are accepted.
     *
     * @param speed The desired playback speed.
     */
    fun setPlaybackSpeed(speed: Float) {
        audioPlayerService.setSpeed(speed)
    }

    /**
     * Seeks to a specific position in the currently playing audio.
     *
     * @param positionMs The target position in milliseconds.
     */
    fun seekTo(positionMs: Int) {
        audioPlayerService.seekTo(positionMs)
    }

    /**
     * Stops playback and clears the current playback session.
     */
    fun stopPlayback() {
        audioPlayerService.stop()
        _currentPlaybackSessionId.value = null
    }

    override fun onCleared() {
        super.onCleared()
        audioPlayerService.release()
    }

    companion object {
        /**
         * Pure function that filters sessions by case-insensitive substring match.
         *
         * A session matches if:
         * - Its title contains the query (case-insensitive), OR
         * - At least one of its segments' text contains the query (case-insensitive)
         *
         * Returns all sessions when the query is empty or blank.
         *
         * @param sessions The list of sessions to filter.
         * @param query The search query string.
         * @return The filtered list of sessions matching the query.
         */
        fun filterSessions(
            sessions: List<RecordingSession>,
            query: String
        ): List<RecordingSession> {
            if (query.isBlank()) {
                return sessions
            }

            val lowerQuery = query.lowercase()

            return sessions.filter { session ->
                session.title.lowercase().contains(lowerQuery) ||
                    session.segments.any { segment ->
                        segment.text.lowercase().contains(lowerQuery)
                    }
            }
        }
    }
}
