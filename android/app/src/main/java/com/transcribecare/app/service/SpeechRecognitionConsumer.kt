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
 * AudioConsumer that wraps Android's [SpeechRecognizer] to provide continuous
 * speech-to-text transcription as part of the unified audio capture pipeline.
 *
 * On [prepare], the consumer initializes a [SpeechRecognizer] instance and begins
 * listening for speech. On [release], the recognizer is destroyed and all pending
 * handler callbacks are cleared.
 *
 * Because Android's standard [SpeechRecognizer] captures audio internally via its
 * own audio source, [onAudioFrame] is a no-op for this consumer. The consumer
 * participates in the [AudioConsumer] lifecycle to coordinate start/stop with the
 * [UnifiedAudioCaptureService], but speech recognition uses Android's built-in
 * audio pipeline.
 *
 * The consumer automatically restarts recognition sessions that end due to silence
 * timeout or session limits, maintaining continuous transcription while capture is active.
 *
 * @param context Application context used to create the [SpeechRecognizer] instance.
 * @param onPartialResult Callback invoked with interim (non-final) recognition text.
 * @param onFinalResult Callback invoked with finalized recognition text.
 * @param onError Callback invoked with a user-facing error message.
 */
class SpeechRecognitionConsumer(
    private val context: Context,
    private val onPartialResult: (interimText: String) -> Unit,
    private val onFinalResult: (text: String) -> Unit,
    private val onError: (message: String) -> Unit
) : AudioConsumer {

    private var speechRecognizer: SpeechRecognizer? = null
    private var isActive: Boolean = false
    private val handler = Handler(Looper.getMainLooper())

    companion object {
        /** Delay before retrying recognition after a recoverable error. */
        private const val RETRY_DELAY_MS = 500L
    }

    /**
     * Initializes the [SpeechRecognizer] and begins listening for speech.
     *
     * The audio format parameters are accepted for interface compliance but are not
     * used directly, since Android's [SpeechRecognizer] manages its own audio capture.
     *
     * @param sampleRate The audio sample rate in Hz (informational only).
     * @param channelCount The number of audio channels (informational only).
     * @param encoding The audio encoding format (informational only).
     */
    override fun prepare(sampleRate: Int, channelCount: Int, encoding: Int) {
        isActive = true
        handler.post {
            initializeRecognizer()
            speechRecognizer?.startListening(createRecognizerIntent())
        }
    }

    /**
     * Receives a PCM audio frame from the unified capture service.
     *
     * This is a no-op for the standard Android [SpeechRecognizer], which captures
     * audio internally. The consumer implements this method to satisfy the
     * [AudioConsumer] interface and participate in the unified lifecycle.
     *
     * TODO: A custom speech recognition engine (e.g., Vosk, Whisper) could use
     *  this raw PCM data directly for on-device transcription.
     *
     * @param frame ShortArray containing PCM samples (not used).
     * @param frameSize Number of valid samples in the frame (not used).
     */
    override fun onAudioFrame(frame: ShortArray, frameSize: Int) {
        // No-op: Android's SpeechRecognizer captures its own audio internally.
    }

    /**
     * Stops speech recognition and releases all resources.
     *
     * Clears the active flag to prevent auto-restart, removes pending handler
     * callbacks, and destroys the [SpeechRecognizer] instance synchronously
     * on the main thread to ensure the audio source is fully released before
     * a new recording session can begin.
     */
    override fun release() {
        isActive = false
        handler.removeCallbacksAndMessages(null)

        // Destroy recognizer synchronously on the main looper to ensure the
        // internal audio source is released before the next recording starts.
        if (Looper.myLooper() == Looper.getMainLooper()) {
            destroyRecognizer()
        } else {
            // Post and wait for completion using a CountDownLatch
            val latch = java.util.concurrent.CountDownLatch(1)
            handler.post {
                destroyRecognizer()
                latch.countDown()
            }
            try {
                latch.await(1000, java.util.concurrent.TimeUnit.MILLISECONDS)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }
    }

    /**
     * Creates a new [SpeechRecognizer] instance with a [RecognitionListener] attached.
     * Destroys any existing recognizer before creating a new one.
     */
    private fun initializeRecognizer() {
        destroyRecognizer()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(createRecognitionListener())
        }
    }

    /**
     * Destroys the current [SpeechRecognizer] instance and nulls the reference.
     */
    private fun destroyRecognizer() {
        speechRecognizer?.destroy()
        speechRecognizer = null
    }

    /**
     * Creates the [Intent] used to configure speech recognition.
     * Enables partial results for real-time interim transcription.
     */
    private fun createRecognizerIntent(): Intent {
        return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
    }

    /**
     * Creates a [RecognitionListener] that propagates results and errors via callbacks
     * and auto-restarts recognition sessions for continuous transcription.
     */
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
                // Raw audio buffer from recognizer — not used
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
     * Handles [SpeechRecognizer] error codes with appropriate recovery strategies:
     * - ERROR_NO_MATCH / ERROR_SPEECH_TIMEOUT: Continue listening silently (silence timeout)
     * - ERROR_NETWORK: Display warning, attempt to continue
     * - ERROR_RECOGNIZER_BUSY: Retry after a short delay
     * - Other errors: Stop recognition and report to user
     */
    private fun handleError(errorCode: Int) {
        when (errorCode) {
            SpeechRecognizer.ERROR_NO_MATCH -> {
                // No speech detected (silence timeout) — restart for continuous transcription
                restartIfActive()
            }

            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                // Speech input timed out — restart for continuous transcription
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
                // Client-side error — attempt restart if still active
                restartIfActive()
            }

            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                isActive = false
                onError("Microphone permission is required for speech recognition.")
                destroyRecognizer()
            }

            SpeechRecognizer.ERROR_SERVER -> {
                onError("Speech recognition server error. Please try again.")
                isActive = false
                destroyRecognizer()
            }

            else -> {
                onError("Speech recognition error. Please try again.")
                isActive = false
                destroyRecognizer()
            }
        }
    }

    /**
     * Restarts recognition if the consumer is still active.
     *
     * This enables continuous transcription across recognition sessions by
     * automatically starting a new session when the previous one ends (due to
     * silence timeout, final results delivery, or recoverable errors).
     *
     * If the consumer is no longer active (i.e., [release] was called), the
     * recognizer is destroyed to free resources.
     */
    private fun restartIfActive() {
        if (isActive) {
            initializeRecognizer()
            speechRecognizer?.startListening(createRecognizerIntent())
        } else {
            destroyRecognizer()
        }
    }
}
