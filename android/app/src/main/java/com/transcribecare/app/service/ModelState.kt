package com.transcribecare.app.service

/**
 * Observable state representing the lifecycle of the on-device transcription model.
 *
 * Exposed by [HomeViewModel][com.transcribecare.app.viewmodel.HomeViewModel] as a
 * [StateFlow][kotlinx.coroutines.flow.StateFlow] for the UI to observe and react to
 * model readiness before starting a recording session.
 *
 * State transitions follow: [Idle] → [Loading] → [Ready] or [Error].
 */
sealed class ModelState {

    /** Initial state before model assembly has been triggered. */
    object Idle : ModelState()

    /** Model assembly or engine initialization is in progress. */
    object Loading : ModelState()

    /** Engine is initialized and ready to accept inference requests. */
    object Ready : ModelState()

    /**
     * Model assembly or engine initialization failed.
     *
     * @property message A user-facing description of what went wrong.
     */
    data class Error(val message: String) : ModelState()
}
