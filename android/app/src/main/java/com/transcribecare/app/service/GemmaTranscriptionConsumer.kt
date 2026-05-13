package com.transcribecare.app.service

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean

/**
 * AudioConsumer implementation that performs on-device transcription using
 * the Gemma 4 E2B model via LiteRT-LM.
 *
 * Buffers raw PCM frames into time-based chunks and submits them to the
 * locally-running LLM for transcription inference. Uses mutual exclusion
 * to ensure only one inference call is in progress at a time.
 *
 * @param engine The [GemmaEngineWrapper] singleton for model inference.
 * @param onPartialResult Callback invoked with accumulation status or processing indicators.
 * @param onFinalResult Callback invoked with the final transcribed text from each chunk.
 * @param onError Callback invoked when an unrecoverable inference error occurs.
 * @param chunkDurationSeconds Duration threshold (in seconds) before submitting a chunk for inference.
 * @param coroutineScope Scope for launching inference coroutines.
 */
class GemmaTranscriptionConsumer(
    private val engine: GemmaEngineWrapper,
    private val onPartialResult: (text: String) -> Unit,
    private val onFinalResult: (text: String) -> Unit,
    private val onError: (message: String) -> Unit,
    private val chunkDurationSeconds: Float = 2.0f,
    private val coroutineScope: CoroutineScope
) : AudioConsumer {

    companion object {
        private const val TAG = "GemmaTranscriptionConsumer"

        /**
         * RMS amplitude threshold below which audio is considered silence.
         * 16-bit PCM has a range of -32768 to 32767. A typical silence/noise
         * floor sits well below 300. This threshold avoids sending quiet
         * background noise to the model for transcription.
         */
        private const val SILENCE_RMS_THRESHOLD = 50
    }

    private var buffer: AudioChunkBuffer? = null
    private val isActive = AtomicBoolean(false)
    private val isInferring = AtomicBoolean(false)
    private val hasCeasedInference = AtomicBoolean(false)
    private var inferenceJob: Job? = null

    /**
     * Initializes the internal [AudioChunkBuffer] and marks the consumer as active.
     *
     * @param sampleRate The audio sample rate in Hz (e.g., 44100).
     * @param channelCount The number of audio channels (e.g., 1 for mono).
     * @param encoding The audio encoding format (e.g., AudioFormat.ENCODING_PCM_16BIT).
     */
    override fun prepare(sampleRate: Int, channelCount: Int, encoding: Int) {
        buffer = AudioChunkBuffer(sampleRate)
        isActive.set(true)
        hasCeasedInference.set(false)
        Log.d(TAG, "Prepared with sampleRate=$sampleRate, channelCount=$channelCount, encoding=$encoding")
    }

    /**
     * Appends a PCM frame to the internal buffer without blocking the capture thread.
     *
     * When the buffer accumulates enough audio to meet the chunk duration threshold,
     * an inference coroutine is launched (if one is not already in progress).
     * Invokes [onPartialResult] with accumulation status feedback.
     *
     * @param frame ShortArray containing PCM samples.
     * @param frameSize Number of valid samples in the frame.
     */
    override fun onAudioFrame(frame: ShortArray, frameSize: Int) {
        if (!isActive.get()) return

        val currentBuffer = buffer ?: return
        currentBuffer.append(frame, frameSize)

        // Check if threshold is reached and trigger inference if not already running
        if (currentBuffer.durationSeconds() >= chunkDurationSeconds) {
            if (!hasCeasedInference.get() && isInferring.compareAndSet(false, true)) {
                launchInference()
            }
        }


    }

    /**
     * Releases resources: flushes remaining buffered audio, clears the buffer,
     * and cancels any in-progress inference.
     */
    override fun release() {
        isActive.set(false)

        // Flush remaining buffer if non-empty
        val currentBuffer = buffer
        if (currentBuffer != null && !currentBuffer.isEmpty() && !hasCeasedInference.get()) {
            if (isInferring.compareAndSet(false, true)) {
                // Submit final chunk synchronously in a coroutine
                coroutineScope.launch {
                    performInference(isFinalFlush = true)
                }
            }
        }

        // Clear buffer and cancel scope
        currentBuffer?.clear()
        buffer = null
        inferenceJob?.cancel()
        inferenceJob = null
        Log.d(TAG, "Released")
    }

    /**
     * Launches an inference coroutine that drains the buffer and submits
     * the audio chunk to the engine for transcription.
     */
    private fun launchInference() {
        inferenceJob = coroutineScope.launch {
            performInference(isFinalFlush = false)
        }
    }

    /**
     * Performs the actual inference: drains the buffer, encodes audio as a prompt,
     * sends it to the engine, and delivers results via callbacks.
     *
     * Implements silence detection: if the drained audio chunk's RMS amplitude
     * is below [SILENCE_RMS_THRESHOLD], the chunk is discarded without inference,
     * preventing repeated transcription of silence/noise.
     *
     * Implements conversation recovery: on failure, attempts to create a new
     * conversation and retry once. If the retry also fails, invokes [onError]
     * and ceases further inference.
     *
     * @param isFinalFlush Whether this is the final flush during release().
     */
    private suspend fun performInference(isFinalFlush: Boolean) {
        try {
            val currentBuffer = buffer ?: run {
                isInferring.set(false)
                return
            }

            val audioData = currentBuffer.drain() ?: run {
                isInferring.set(false)
                return
            }

            // Silence detection: skip inference if audio energy is below threshold
            if (!isFinalFlush && isSilent(audioData)) {
                Log.d(TAG, "Chunk is silent (RMS below threshold), skipping inference")
                isInferring.set(false)
                return
            }

            val prompt = buildPrompt(audioData)

            // Attempt inference on IO dispatcher
            val result = withContext(Dispatchers.IO) {
                engine.sendMessage(prompt)
            }

            if (result.isSuccess) {
                val transcription = result.getOrDefault("")
                if (transcription.isNotEmpty()) {
                    withContext(Dispatchers.Main) {
                        onFinalResult(transcription)
                        onPartialResult("")
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        onPartialResult("")
                    }
                }
            } else {
                // Attempt conversation recovery
                Log.w(TAG, "Inference failed, attempting conversation recovery", result.exceptionOrNull())
                val recovered = recoverAndRetry(prompt)
                if (!recovered) {
                    return
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error during inference: ${e.message}", e)
            withContext(Dispatchers.Main) {
                onError("Transcription error: ${e.message ?: "Unknown error"}")
                onPartialResult("")
            }
        } finally {
            isInferring.set(false)
        }
    }

    /**
     * Attempts conversation recovery: creates a new conversation and retries
     * the inference once. If both attempts fail, ceases inference and invokes [onError].
     *
     * @param prompt The transcription prompt to retry.
     * @return true if recovery succeeded and result was delivered, false otherwise.
     */
    private suspend fun recoverAndRetry(prompt: com.google.ai.edge.litertlm.Contents): Boolean {
        val recoveryResult = engine.createNewConversation()
        if (recoveryResult.isFailure) {
            // Terminal failure — cease inference
            hasCeasedInference.set(true)
            withContext(Dispatchers.Main) {
                onError("Transcription unavailable. Please restart recording.")
                onPartialResult("")
            }
            Log.e(TAG, "Conversation recovery failed. Ceasing inference.")
            return false
        }

        // Retry inference with new conversation
        val retryResult = withContext(Dispatchers.IO) {
            engine.sendMessage(prompt)
        }

        if (retryResult.isSuccess) {
            val transcription = retryResult.getOrDefault("")
            if (transcription.isNotEmpty()) {
                withContext(Dispatchers.Main) {
                    onFinalResult(transcription)
                    onPartialResult("")
                }
            } else {
                withContext(Dispatchers.Main) {
                    onPartialResult("")
                }
            }
            return true
        } else {
            // Retry also failed — cease inference
            hasCeasedInference.set(true)
            withContext(Dispatchers.Main) {
                onError("Transcription unavailable. Please restart recording.")
                onPartialResult("")
            }
            Log.e(TAG, "Retry after recovery also failed. Ceasing inference.", retryResult.exceptionOrNull())
            return false
        }
    }

    /**
     * Determines whether an audio chunk is effectively silent by computing
     * its RMS (root mean square) amplitude and comparing against the threshold.
     *
     * @param audioData The PCM 16-bit samples to analyze.
     * @return true if the audio is below the silence threshold, false otherwise.
     */
    private fun isSilent(audioData: ShortArray): Boolean {
        if (audioData.isEmpty()) return true

        var sumOfSquares = 0L
        for (sample in audioData) {
            sumOfSquares += sample.toLong() * sample.toLong()
        }
        val rms = Math.sqrt(sumOfSquares.toDouble() / audioData.size).toInt()
        return rms < SILENCE_RMS_THRESHOLD
    }

    /**
     * Builds the transcription prompt from raw PCM audio data.
     *
     * Wraps the PCM samples in a WAV container so the miniaudio decoder in
     * LiteRT-LM can parse sample rate, bit depth, and channel count from the
     * header. Sends the WAV bytes alongside a text prompt to the Gemma 4 E2B model.
     *
     * @param audioData The raw PCM samples to encode.
     * @return The formatted transcription [Contents] for the model.
     */
    private fun buildPrompt(audioData: ShortArray): com.google.ai.edge.litertlm.Contents {
        val wavBytes = pcmToWav(audioData, AudioConfig.SAMPLE_RATE, AudioConfig.CHANNEL_COUNT)
        Log.d(TAG, "Built WAV prompt: ${wavBytes.size} bytes, sampleRate=${AudioConfig.SAMPLE_RATE}")

        val stringPrompt = """Transcribe the following speech segment in English into English text.

Follow these specific instructions for formatting the answer:
*   Only output the transcription, with no newlines.
*   When transcribing numbers, write the digits, i.e. write 1.7 and not one point seven, and write 3 instead of three.
"""
        // Putting Text first as some multimodal models expect the instruction before the media.
        return com.google.ai.edge.litertlm.Contents.of(
            com.google.ai.edge.litertlm.Content.Text(stringPrompt),
            com.google.ai.edge.litertlm.Content.AudioBytes(wavBytes)
        )
    }

    /**
     * Wraps raw PCM 16-bit samples in a standard WAV (RIFF) container.
     *
     * Produces a valid WAV file byte array with a 44-byte header followed by
     * the PCM data. This allows the miniaudio decoder to correctly identify
     * the audio format without external metadata.
     *
     * @param samples The PCM 16-bit samples.
     * @param sampleRate The sample rate in Hz (e.g., 44100).
     * @param channels The number of audio channels (e.g., 1 for mono).
     * @return A ByteArray containing the complete WAV file.
     */
    private fun pcmToWav(samples: ShortArray, sampleRate: Int, channels: Int): ByteArray {
        val bitsPerSample = 16
        val dataSize = samples.size * 2 // 2 bytes per 16-bit sample
        val headerSize = 44
        val fileSize = headerSize + dataSize

        val buffer = ByteBuffer.allocate(fileSize)
        buffer.order(ByteOrder.LITTLE_ENDIAN)

        // RIFF header
        buffer.put('R'.code.toByte())
        buffer.put('I'.code.toByte())
        buffer.put('F'.code.toByte())
        buffer.put('F'.code.toByte())
        buffer.putInt(fileSize - 8) // File size minus RIFF header (8 bytes)

        // WAVE identifier
        buffer.put('W'.code.toByte())
        buffer.put('A'.code.toByte())
        buffer.put('V'.code.toByte())
        buffer.put('E'.code.toByte())

        // fmt sub-chunk
        buffer.put('f'.code.toByte())
        buffer.put('m'.code.toByte())
        buffer.put('t'.code.toByte())
        buffer.put(' '.code.toByte())
        buffer.putInt(16) // Sub-chunk size (16 for PCM)
        buffer.putShort(1) // Audio format (1 = PCM)
        buffer.putShort(channels.toShort()) // Number of channels
        buffer.putInt(sampleRate) // Sample rate
        buffer.putInt(sampleRate * channels * bitsPerSample / 8) // Byte rate
        buffer.putShort((channels * bitsPerSample / 8).toShort()) // Block align
        buffer.putShort(bitsPerSample.toShort()) // Bits per sample

        // data sub-chunk
        buffer.put('d'.code.toByte())
        buffer.put('a'.code.toByte())
        buffer.put('t'.code.toByte())
        buffer.put('a'.code.toByte())
        buffer.putInt(dataSize) // Data size

        // PCM sample data
        for (sample in samples) {
            buffer.putShort(sample)
        }

        return buffer.array()
    }
}
