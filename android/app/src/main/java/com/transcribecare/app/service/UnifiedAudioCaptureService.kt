package com.transcribecare.app.service

import android.media.AudioRecord

/**
 * Central audio capture service that owns the [AudioRecord] instance,
 * reads PCM data on a dedicated background thread, and dispatches
 * independent frame copies to registered [AudioConsumer] instances.
 *
 * This service implements a fan-out architecture: a single microphone
 * owner reads raw audio and duplicates each frame for all consumers,
 * eliminating contention between speech recognition and file recording.
 *
 * @param onError Callback invoked when an error occurs during capture
 *   lifecycle (initialization failure, read error, no consumers, etc.).
 */
class UnifiedAudioCaptureService(
    private val onError: (message: String) -> Unit
) {
    private val consumers = mutableListOf<AudioConsumer>()
    private var audioRecord: AudioRecord? = null
    private var readingThread: Thread? = null
    private var buffer: ShortArray? = null

    @Volatile
    private var captureState: CaptureState = CaptureState.IDLE

    /** Volatile flag used to signal the reading thread to stop. */
    @Volatile
    private var stopRequested: Boolean = false

    /**
     * Returns the current [CaptureState] of this service.
     */
    fun getState(): CaptureState = captureState

    /**
     * Registers an [AudioConsumer] to receive audio frame copies during capture.
     *
     * Consumers must be registered before calling [startCapture]. Registration
     * is rejected if capture is currently active.
     *
     * @param consumer The consumer to register for frame dispatch.
     * @throws IllegalStateException if called while capture is active.
     */
    fun registerConsumer(consumer: AudioConsumer) {
        if (captureState != CaptureState.IDLE) {
            onError("Cannot register consumer while capture is active")
            return
        }
        consumers.add(consumer)
    }

    /**
     * Starts audio capture.
     *
     * Validates that at least one consumer is registered, creates an [AudioRecord]
     * instance with [AudioConfig] constants, verifies initialization, calls
     * [AudioConsumer.prepare] on each consumer, and spawns the dedicated reading thread.
     *
     * State transition: IDLE → CAPTURING
     */
    fun startCapture() {
        if (captureState != CaptureState.IDLE) {
            onError("Cannot start capture: service is not in IDLE state")
            return
        }

        if (consumers.isEmpty()) {
            onError("Cannot start capture: no consumers registered")
            return
        }

        // Calculate minimum buffer size
        val minBufferSize = AudioRecord.getMinBufferSize(
            AudioConfig.SAMPLE_RATE,
            AudioConfig.CHANNEL_CONFIG,
            AudioConfig.AUDIO_FORMAT
        )

        if (minBufferSize == AudioRecord.ERROR || minBufferSize == AudioRecord.ERROR_BAD_VALUE) {
            onError("Failed to determine minimum buffer size for AudioRecord")
            return
        }

        // Allocate buffer at least as large as the minimum
        val bufferSize = minBufferSize
        buffer = ShortArray(bufferSize)

        // Create AudioRecord instance
        val record: AudioRecord
        try {
            record = AudioRecord(
                AudioConfig.AUDIO_SOURCE,
                AudioConfig.SAMPLE_RATE,
                AudioConfig.CHANNEL_CONFIG,
                AudioConfig.AUDIO_FORMAT,
                minBufferSize * 2 // byte buffer size (Short = 2 bytes)
            )
        } catch (e: Exception) {
            onError("Failed to create AudioRecord: ${e.message}")
            buffer = null
            return
        }

        // Verify AudioRecord initialized successfully
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            buffer = null
            onError("AudioRecord failed to initialize (state: ${record.state})")
            return
        }

        audioRecord = record

        // Prepare all consumers
        consumers.forEach { consumer ->
            consumer.prepare(
                AudioConfig.SAMPLE_RATE,
                AudioConfig.CHANNEL_COUNT,
                AudioConfig.AUDIO_FORMAT
            )
        }

        // Start recording
        record.startRecording()
        stopRequested = false
        captureState = CaptureState.CAPTURING

        // Spawn dedicated reading thread
        readingThread = Thread({
            readLoop()
        }, "UnifiedAudioCapture-ReadThread").also { it.start() }
    }

    /**
     * Stops audio capture.
     *
     * Signals the reading thread to stop, waits for it to complete,
     * calls [AudioConsumer.release] on each consumer, and releases
     * the [AudioRecord] instance.
     *
     * State transition: CAPTURING → STOPPING → IDLE
     */
    fun stopCapture() {
        if (captureState != CaptureState.CAPTURING) {
            return
        }

        captureState = CaptureState.STOPPING
        stopRequested = true

        // Wait for reading thread to finish
        readingThread?.let { thread ->
            try {
                thread.join()
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }
        readingThread = null

        // Release all consumers
        consumers.forEach { consumer ->
            consumer.release()
        }

        // Stop and release AudioRecord
        audioRecord?.let { record ->
            record.stop()
            record.release()
        }
        audioRecord = null
        buffer = null

        captureState = CaptureState.IDLE
    }

    /**
     * Destroys the service, releasing all resources.
     *
     * If capture is currently active, it will be stopped first.
     * After destruction, the consumer list is cleared.
     */
    fun destroy() {
        if (captureState == CaptureState.CAPTURING || captureState == CaptureState.STOPPING) {
            stopCapture()
        }
        consumers.clear()
    }

    /**
     * The main reading loop executed on the dedicated background thread.
     *
     * Continuously reads PCM frames from [AudioRecord], duplicates each frame
     * using [duplicateFrame], and dispatches independent copies to each consumer.
     * Exits when [stopRequested] is set to true or a read error occurs.
     */
    private fun readLoop() {
        val localBuffer = buffer ?: return
        val localRecord = audioRecord ?: return

        while (!stopRequested) {
            val samplesRead = localRecord.read(localBuffer, 0, localBuffer.size)

            if (samplesRead < 0) {
                // Read error — invoke error callback and stop
                onError("AudioRecord.read() returned error code: $samplesRead")
                // Signal stop from within the thread; stopCapture() will be
                // called by the error handler or externally.
                stopRequested = true
                break
            }

            if (samplesRead > 0) {
                // Duplicate frame and dispatch to each consumer
                val frames = duplicateFrame(localBuffer, samplesRead, consumers.size)
                consumers.forEachIndexed { index, consumer ->
                    consumer.onAudioFrame(frames[index], samplesRead)
                }
            }
        }
    }
}
