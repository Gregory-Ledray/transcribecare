package com.transcribecare.app.service

/**
 * Represents the lifecycle state of the [UnifiedAudioCaptureService].
 *
 * State transitions follow the pattern: IDLE → CAPTURING → STOPPING → IDLE.
 */
enum class CaptureState {
    /** No AudioRecord instance exists, no reading thread is active. */
    IDLE,

    /** AudioRecord is active and the reading thread is dispatching frames to consumers. */
    CAPTURING,

    /** The reading thread has been signaled to stop and is awaiting join before resource release. */
    STOPPING
}
