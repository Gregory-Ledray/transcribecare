package com.transcribecare.app.service

/**
 * WAV file header for PCM audio.
 *
 * Written at file creation with a placeholder [dataSize] of zero,
 * then patched on [FileRecordingConsumer.release] with the actual
 * byte count of recorded PCM data.
 *
 * @property sampleRate Audio sample rate in Hz.
 * @property channelCount Number of audio channels (1 = mono, 2 = stereo).
 * @property bitsPerSample Bit depth per sample (typically 16 for PCM).
 * @property dataSize Total size of the PCM data section in bytes. Zero until finalized.
 */
data class WavHeader(
    val sampleRate: Int = AudioConfig.SAMPLE_RATE,
    val channelCount: Int = AudioConfig.CHANNEL_COUNT,
    val bitsPerSample: Int = 16,
    val dataSize: Int = 0
) {
    /** Bytes per second of audio data: sampleRate × channelCount × (bitsPerSample / 8). */
    val byteRate: Int = sampleRate * channelCount * bitsPerSample / 8

    /** Size in bytes of a single audio frame across all channels. */
    val blockAlign: Int = channelCount * bitsPerSample / 8

    /** Standard WAV header size in bytes (RIFF + fmt + data chunk headers). */
    val headerSize: Int = 44
}

/**
 * Creates independent copies of a PCM frame for each consumer.
 *
 * This is the core fan-out duplication logic used by [UnifiedAudioCaptureService]
 * to guarantee that each registered [AudioConsumer] receives its own isolated
 * copy of the audio data, preventing cross-consumer mutation.
 *
 * @param frame The original frame read from AudioRecord.
 * @param frameSize Number of valid samples in the frame.
 * @param consumerCount Number of registered consumers.
 * @return List of independent [ShortArray] copies, one per consumer.
 */
fun duplicateFrame(frame: ShortArray, frameSize: Int, consumerCount: Int): List<ShortArray> {
    return (0 until consumerCount).map { frame.copyOf(frameSize) }
}
