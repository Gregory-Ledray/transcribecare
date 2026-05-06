package com.transcribecare.app.service

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/**
 * Wrapper around Android's SpeechRecognizer that provides continuous speech-to-text
 * recognition with interim and final results, auto-restart on unexpected session end,
 * and graceful error handling.
 *
 * @param context Application context used to create the SpeechRecognizer instance.
 * @param onPartialResult Callback invoked with interim (non-final) recognition text.
 * @param onFinalResult Callback invoked with finalized recognition text.
 * @param onError Callback invoked with a user-facing error message.
 */
class SpeechRecognitionService(
    private val context: Context,
    private val onPartialResult: (interimText: String) -> Unit,
    private val onFinalResult: (text: String) -> Unit,
    private val onError: (message: String) -> Unit
) {

    private var speechRecognizer: SpeechRecognizer? = null
    private var isRecordingIntent: Boolean = false
    private val handler = Handler(Looper.getMainLooper())

    companion object {
        private const val RETRY_DELAY_MS = 500L
    }

    /**
     * Starts continuous speech recognition. Creates a new SpeechRecognizer instance
     * and begins listening with partial results enabled.
     */
    fun startListening() {
        isRecordingIntent = true
        initializeRecognizer()
        speechRecognizer?.startListening(createRecognizerIntent())
    }

    /**
     * Stops speech recognition. Prevents auto-restart by clearing the recording
     * intent flag. The recognizer is not destroyed immediately — it will be
     * destroyed after delivering its final results via onResults, or on the next
     * restartIfActive() call which will see isRecordingIntent is false.
     */
    fun stopListening() {
        isRecordingIntent = false
        speechRecognizer?.stopListening()
    }

    /**
     * Releases the SpeechRecognizer and clears all pending handler callbacks.
     * Should be called when the service is no longer needed (e.g., ViewModel onCleared).
     */
    fun destroy() {
        isRecordingIntent = false
        handler.removeCallbacksAndMessages(null)
        destroyRecognizer()
    }

    private fun initializeRecognizer() {
        destroyRecognizer()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(createRecognitionListener())
        }
    }

    private fun destroyRecognizer() {
        speechRecognizer?.destroy()
        speechRecognizer = null
    }

    private fun createRecognizerIntent(): Intent {
        return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
    }

    private fun createRecognitionListener(): RecognitionListener {
        return object : RecognitionListener {

            override fun onReadyForSpeech(params: Bundle?) {
                // Recognition is ready — no action needed
            }

            override fun onBeginningOfSpeech() {
                // User started speaking — no action needed
            }

            override fun onRmsChanged(rmsdB: Float) {
                // Audio level changed — could be used for visual feedback
            }

            override fun onBufferReceived(buffer: ByteArray?) {
                // Raw audio buffer — not used
            }

            override fun onEndOfSpeech() {
                // User stopped speaking — results will follow via onResults
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val interimText = matches?.firstOrNull().orEmpty()
                if (interimText.isNotEmpty()) {
                    onPartialResult(interimText)
                }
            }

            override fun onResults(results: Bundle?) {
                val matches = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val finalText = matches?.firstOrNull().orEmpty()
                if (finalText.isNotEmpty()) {
                    onFinalResult(finalText)
                }
                // Auto-restart to maintain continuous recognition
                restartIfActive()
            }

            override fun onError(error: Int) {
                handleError(error)
            }

            override fun onEvent(eventType: Int, params: Bundle?) {
                // Reserved for future use by the framework
            }
        }
    }

    /**
     * Handles SpeechRecognizer error codes with appropriate recovery strategies:
     * - ERROR_NO_MATCH: Continue listening silently (no user-facing error)
     * - ERROR_NETWORK: Display warning, attempt to continue
     * - ERROR_RECOGNIZER_BUSY: Retry after a short delay
     * - Other errors: Stop recognition and report to user
     */
    private fun handleError(errorCode: Int) {
        when (errorCode) {
            SpeechRecognizer.ERROR_NO_MATCH -> {
                // No speech detected — continue listening without notifying user
                restartIfActive()
            }

            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                // Speech input timed out — restart if still recording
                restartIfActive()
            }

            SpeechRecognizer.ERROR_NETWORK,
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> {
                onError("Network unavailable. Attempting to continue...")
                restartIfActive()
            }

            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> {
                // Recognizer is busy — retry after a short delay
                handler.postDelayed({
                    restartIfActive()
                }, RETRY_DELAY_MS)
            }

            SpeechRecognizer.ERROR_CLIENT -> {
                // Client-side error — attempt restart if recording
                restartIfActive()
            }

            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                isRecordingIntent = false
                onError("Microphone permission is required for speech recognition.")
                destroyRecognizer()
            }

            SpeechRecognizer.ERROR_SERVER -> {
                onError("Speech recognition server error. Please try again.")
                isRecordingIntent = false
                destroyRecognizer()
            }

            else -> {
                onError("Speech recognition error. Please try again.")
                isRecordingIntent = false
                destroyRecognizer()
            }
        }
    }

    /**
     * Restarts recognition if the user's recording intent is still active.
     * This enables continuous transcription across recognition sessions.
     * If recording is no longer intended, destroys the recognizer to free resources.
     */
    private fun restartIfActive() {
        if (isRecordingIntent) {
            initializeRecognizer()
            speechRecognizer?.startListening(createRecognizerIntent())
        } else {
            destroyRecognizer()
        }
    }
}
