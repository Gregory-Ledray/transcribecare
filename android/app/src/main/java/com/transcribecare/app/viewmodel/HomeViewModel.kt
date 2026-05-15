package com.transcribecare.app.viewmodel

import android.app.Application
import android.os.PowerManager
import android.os.StatFs
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.transcribecare.app.data.AppDatabase
import com.transcribecare.app.data.entity.SegmentEntity
import com.transcribecare.app.data.entity.SessionEntity
import com.transcribecare.app.model.RecordingSession
import com.transcribecare.app.model.SegmentType
import com.transcribecare.app.model.TranscriptSegment
import com.transcribecare.app.service.AudioConfig
import com.transcribecare.app.service.FileRecordingConsumer
import com.transcribecare.app.service.GemmaEngineWrapper
import com.transcribecare.app.service.ModelFileLoader
import com.transcribecare.app.service.ModelState
import com.transcribecare.app.service.GemmaTranscriptionConsumer
import com.transcribecare.app.service.UnifiedAudioCaptureService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * ViewModel for the Home screen managing recording state, transcript segments,
 * speech recognition, audio recording, and session persistence.
 *
 * Uses [UnifiedAudioCaptureService] to coordinate a single microphone owner
 * with fan-out to [GemmaTranscriptionConsumer] and [FileRecordingConsumer].
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

    private val _modelState = MutableStateFlow<ModelState>(ModelState.Idle)
    val modelState: StateFlow<ModelState> = _modelState.asStateFlow()

    private val database: AppDatabase = AppDatabase.getInstance(application)

    init {
        initializeEngine()
    }

    private var captureService: UnifiedAudioCaptureService? = null
    private var gemmaConsumer: GemmaTranscriptionConsumer? = null
    private var fileConsumer: FileRecordingConsumer? = null

    /** Wake lock to prevent the device from sleeping during recording. */
    private var wakeLock: PowerManager.WakeLock? = null

    private var recordingStartTime: Long = 0L

    /**
     * Initializes the Gemma 4 E2B engine on a background thread.
     *
     * Checks available storage, assembles the model file from split assets,
     * and initializes the LiteRT-LM engine. Updates [_modelState] on the main
     * thread to reflect progress (Loading → Ready or Error).
     */
    private fun initializeEngine() {
        _modelState.value = ModelState.Loading
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // 1. Check free storage >= AudioConfig.MIN_STORAGE_BYTES
                val context = getApplication<Application>()
                val stat = StatFs(context.filesDir.absolutePath)
                val availableBytes = stat.availableBytes
                if (availableBytes < AudioConfig.MIN_STORAGE_BYTES) {
                    withContext(Dispatchers.Main) {
                        _modelState.value = ModelState.Error(
                            "Insufficient storage. At least 10 MB required for model assembly."
                        )
                    }
                    return@launch
                }

                // 2. Assemble model file via ModelFileLoader
                ModelFileLoader.loadModel(context)

                // 3. Initialize engine via GemmaEngineWrapper
                val engine = GemmaEngineWrapper.getInstance(context)
                val result = engine.initialize()

                // 4. Update state on Main dispatcher
                withContext(Dispatchers.Main) {
                    if (result.isSuccess) {
                        _modelState.value = ModelState.Ready
                    } else {
                        val errorMessage = result.exceptionOrNull()?.message
                            ?: "Unknown engine initialization error"
                        _modelState.value = ModelState.Error(errorMessage)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _modelState.value = ModelState.Error(
                        e.message ?: "Failed to initialize transcription model"
                    )
                }
            }
        }
    }

    /**
     * Starts recording. Creates a [UnifiedAudioCaptureService], registers both
     * [GemmaTranscriptionConsumer] and [FileRecordingConsumer], and begins capture.
     *
     * Guards against starting when the transcription model is not ready.
     */
    fun startRecording() {
        if (_modelState.value != ModelState.Ready) {
            _error.value = "Transcription model is not ready"
            return
        }

        // Clear previous session's transcript before starting a new one
        _segments.value = emptyList()
        _interimText.value = ""
        _error.value = null

        // Create UnifiedAudioCaptureService with error callback
        val service = UnifiedAudioCaptureService(
            onError = { message -> _error.value = message }
        )

        // Clear conversation history from any prior session
        val engineInstance = GemmaEngineWrapper.getInstance(getApplication())
        engineInstance.clearHistory()

        // Create GemmaTranscriptionConsumer with partial/final/error callbacks
        val gemma = GemmaTranscriptionConsumer(
            engine = engineInstance,
            onPartialResult = { text -> onInterimResult(text) },
            onFinalResult = { text -> onFinalResult(text) },
            onError = { message -> _error.value = message },
            coroutineScope = viewModelScope
        )

        // Create FileRecordingConsumer with error callback
        val file = FileRecordingConsumer(
            context = getApplication(),
            onError = { message -> _error.value = message }
        )

        // Register both consumers
        service.registerConsumer(gemma)
        service.registerConsumer(file)

        // Start unified capture
        service.startCapture()

        // Store references
        captureService = service
        gemmaConsumer = gemma
        fileConsumer = file

        // Track recording start time for duration calculation
        recordingStartTime = System.currentTimeMillis()

        // Acquire wake lock to prevent device from sleeping during recording
        val powerManager = getApplication<Application>()
            .getSystemService(Application.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "TranscribeCare::RecordingWakeLock"
        ).apply { acquire() }

        _isRecording.value = true
    }

    /**
     * Stops recording. Calls [UnifiedAudioCaptureService.stopCapture] which stops
     * the background thread and releases all consumers, then retrieves the file path
     * from [FileRecordingConsumer] and saves the session.
     *
     * Always saves the session to history regardless of whether transcript segments
     * were captured, so audio-only recordings are preserved for later playback.
     */
    fun stopRecording() {
        // Stop unified capture (stops thread, calls release() on consumers)
        captureService?.stopCapture()

        // Retrieve the audio file path from the file consumer
        val audioFilePath = fileConsumer?.getOutputFilePath()

        // Calculate duration
        val durationMs = System.currentTimeMillis() - recordingStartTime
        val formattedDuration = formatDuration(durationMs)

        // Create and save session regardless of whether segments exist.
        // Audio-only recordings (no transcript) still appear in history.
        val currentSegments = _segments.value
        val session = createSession(currentSegments, audioFilePath, formattedDuration)
        saveSession(session)

        // Keep segments on screen until next recording starts
        _interimText.value = ""
        _isRecording.value = false

        // Release wake lock now that recording has stopped
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
        wakeLock = null

        // Clear service references
        captureService = null
        gemmaConsumer = null
        fileConsumer = null
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
     * When no transcript segments exist, the title defaults to "Audio Recording".
     */
    private fun createSession(
        segments: List<TranscriptSegment>,
        audioFilePath: String?,
        duration: String
    ): RecordingSession {
        val now = Date()
        val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.US)
        val timeFormat = SimpleDateFormat("hh:mm a", Locale.US)

        // Title from first segment text (truncated to 50 chars), or fallback for audio-only
        val title = segments.firstOrNull()?.text?.take(50) ?: "Audio Recording"

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
        captureService?.destroy()
        captureService = null
        gemmaConsumer = null
        fileConsumer = null
        // Safety net: release wake lock if still held when ViewModel is destroyed
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
        wakeLock = null
        GemmaEngineWrapper.getInstance(getApplication()).release()
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
