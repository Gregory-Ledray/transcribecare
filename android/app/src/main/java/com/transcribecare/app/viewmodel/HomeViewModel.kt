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
import com.transcribecare.app.service.AudioRecorderService
import com.transcribecare.app.service.SpeechRecognitionService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * ViewModel for the Home screen managing recording state, transcript segments,
 * speech recognition, audio recording, and session persistence.
 */
class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _segments = MutableStateFlow<List<TranscriptSegment>>(emptyList())
    val segments: StateFlow<List<TranscriptSegment>> = _segments.asStateFlow()

    private val _interimText = MutableStateFlow("")
    val interimText: StateFlow<String> = _interimText.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val database: AppDatabase = AppDatabase.getInstance(application)

    private var speechRecognitionService: SpeechRecognitionService? = null
    private var audioRecorderService: AudioRecorderService? = null

    private var recordingStartTime: Long = 0L
    private var currentAudioFilePath: String? = null

    /**
     * Starts recording. Creates speech recognition and audio recorder services,
     * begins speech recognition and audio capture.
     */
    fun startRecording() {
        _error.value = null

        // Create SpeechRecognitionService with callbacks
        speechRecognitionService = SpeechRecognitionService(
            context = getApplication(),
            onPartialResult = { interimText -> onInterimResult(interimText) },
            onFinalResult = { text -> onFinalResult(text) },
            onError = { message -> _error.value = message }
        )

        // Create AudioRecorderService with error callback
        audioRecorderService = AudioRecorderService(
            onError = { message -> _error.value = message }
        )

        // Start audio recording first so MediaRecorder acquires the mic
        // with VOICE_RECOGNITION source before SpeechRecognizer begins
        currentAudioFilePath = audioRecorderService?.startRecording(getApplication())

        // Start speech recognition (shares audio via VOICE_RECOGNITION source)
        speechRecognitionService?.startListening()

        // Track recording start time for duration calculation
        recordingStartTime = System.currentTimeMillis()

        _isRecording.value = true
    }

    /**
     * Stops recording. Stops speech recognition and audio capture,
     * creates a RecordingSession from the current segments and audio file path,
     * saves the session to the Room database, and resets state for the next session.
     */
    fun stopRecording() {
        // Stop speech recognition first — stopListening() triggers a final onResults
        // callback before the recognizer is destroyed, allowing last segment to be captured
        speechRecognitionService?.stopListening()

        // Stop audio recording after speech recognition has finished
        val audioFilePath = audioRecorderService?.stopRecording() ?: currentAudioFilePath

        // Calculate duration
        val durationMs = System.currentTimeMillis() - recordingStartTime
        val formattedDuration = formatDuration(durationMs)

        // Create session from current segments
        val currentSegments = _segments.value
        if (currentSegments.isNotEmpty()) {
            val session = createSession(currentSegments, audioFilePath, formattedDuration)
            saveSession(session)
        }

        // Reset state for next session
        _segments.value = emptyList()
        _interimText.value = ""
        _isRecording.value = false
        currentAudioFilePath = null
    }

    /**
     * Called when a finalized transcript result arrives from the speech recognizer.
     * Reclassifies existing segments and appends the new text as the current segment.
     */
    fun onFinalResult(text: String) {
        _segments.value = reclassifyAndAppend(_segments.value, text)
        _interimText.value = ""
    }

    /**
     * Called when interim (partial) text arrives from the speech recognizer.
     */
    fun onInterimResult(text: String) {
        _interimText.value = text
    }

    /**
     * Clears the current error state.
     */
    fun clearError() {
        _error.value = null
    }

    /**
     * Generates session metadata from the current recording state.
     */
    private fun createSession(
        segments: List<TranscriptSegment>,
        audioFilePath: String?,
        duration: String
    ): RecordingSession {
        val now = Date()
        val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.US)
        val timeFormat = SimpleDateFormat("hh:mm a", Locale.US)

        // Title from first segment text (truncated to 50 chars)
        val title = segments.firstOrNull()?.text?.take(50) ?: "Recording"

        return RecordingSession(
            id = UUID.randomUUID().toString(),
            title = title,
            date = dateFormat.format(now),
            time = timeFormat.format(now),
            createdAt = now.time,
            duration = duration,
            audioFilePath = audioFilePath,
            segments = segments,
            statusLabel = "TODAY"
        )
    }

    /**
     * Saves a RecordingSession to the Room database via coroutine.
     */
    private fun saveSession(session: RecordingSession) {
        viewModelScope.launch {
            try {
                val sessionEntity = SessionEntity(
                    id = session.id,
                    title = session.title,
                    date = session.date,
                    time = session.time,
                    createdAt = session.createdAt,
                    duration = session.duration,
                    audioFilePath = session.audioFilePath,
                    statusLabel = session.statusLabel
                )

                val segmentEntities = session.segments.mapIndexed { index, segment ->
                    SegmentEntity(
                        id = segment.id,
                        sessionId = session.id,
                        text = segment.text,
                        type = segment.type.name.lowercase(),
                        timestamp = segment.timestamp,
                        orderIndex = index
                    )
                }

                database.sessionDao().insertSessionWithSegments(sessionEntity, segmentEntities)
            } catch (e: Exception) {
                _error.value = "Failed to save session. Please try again."
            }
        }
    }

    /**
     * Formats a duration in milliseconds to a "MM:SS" string.
     */
    private fun formatDuration(durationMs: Long): String {
        val totalSeconds = durationMs / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }

    override fun onCleared() {
        super.onCleared()
        speechRecognitionService?.destroy()
        audioRecorderService?.release()
        speechRecognitionService = null
        audioRecorderService = null
    }

    companion object {
        /**
         * Pure function that reclassifies transcript segments and appends a new current segment.
         *
         * Order of operations:
         * 1. Reclassify all "recent" segments → "past"
         * 2. Reclassify all "current" segments → "recent"
         * 3. Append a new segment with type "current" and the given text
         *
         * This ordering prevents cascading (current→recent→past in one step).
         *
         * @param segments The existing list of transcript segments.
         * @param newText The finalized text for the new current segment.
         * @return A new list with reclassified segments and the new current segment appended.
         */
        fun reclassifyAndAppend(
            segments: List<TranscriptSegment>,
            newText: String
        ): List<TranscriptSegment> {
            val reclassified = segments.map { segment ->
                when (segment.type) {
                    SegmentType.RECENT -> segment.copy(type = SegmentType.PAST)
                    SegmentType.CURRENT -> segment.copy(type = SegmentType.RECENT)
                    SegmentType.PAST -> segment
                }
            }

            val newSegment = TranscriptSegment(
                text = newText,
                type = SegmentType.CURRENT,
                timestamp = System.currentTimeMillis()
            )

            return reclassified + newSegment
        }
    }
}
