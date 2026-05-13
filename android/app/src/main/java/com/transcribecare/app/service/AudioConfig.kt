package com.transcribecare.app.service

import android.media.AudioFormat
import android.media.MediaRecorder

/**
 * Audio configuration constants used by the UnifiedAudioCaptureService
 * and its consumers for consistent AudioRecord initialization.
 */
object AudioConfig {
    /** 
     * Sample rate in Hz for audio capture.
     * 16000 Hz is the standard for most speech-to-text and multimodal audio models.
     */
    const val SAMPLE_RATE = 16000

    /** Channel configuration for AudioRecord (mono input). */
    const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO

    /** Number of audio channels (1 = mono). */
    const val CHANNEL_COUNT = 1

    /** Audio encoding format (16-bit PCM). */
    const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT

    /** Audio source optimized for voice capture. */
    const val AUDIO_SOURCE = MediaRecorder.AudioSource.VOICE_RECOGNITION

    /** Minimum available storage in bytes required before recording (10 MB). */
    const val MIN_STORAGE_BYTES = 10L * 1024 * 1024
}
