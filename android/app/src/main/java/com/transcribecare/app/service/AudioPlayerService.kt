package com.transcribecare.app.service

import android.media.MediaPlayer
import android.media.PlaybackParams
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException

/**
 * Observable state representing the current audio player status.
 */
data class AudioPlayerState(
    val isPlaying: Boolean = false,
    val currentPosition: Int = 0,
    val totalDuration: Int = 0,
    val currentSpeed: Float = 1.0f,
    val error: String? = null
)

/**
 * Wrapper around Android's MediaPlayer that provides audio playback
 * with variable speed support and observable state via StateFlow.
 *
 * Supports playback speeds of 1x, 1.25x, 1.5x, and 2x.
 * Tracks progress position and total duration, and handles
 * end-of-playback by resetting to the beginning.
 *
 * @param scope CoroutineScope used for progress update polling.
 */
class AudioPlayerService(
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main)
) {

    private var mediaPlayer: MediaPlayer? = null
    private var progressJob: Job? = null

    private val _state = MutableStateFlow(AudioPlayerState())
    val state: StateFlow<AudioPlayerState> = _state.asStateFlow()

    companion object {
        val SUPPORTED_SPEEDS = listOf(1.0f, 1.25f, 1.5f, 2.0f)
        private const val PROGRESS_UPDATE_INTERVAL_MS = 250L
    }

    /**
     * Starts playback of the audio file at the given path.
     *
     * If a file is already playing, it will be stopped and released before
     * starting the new file. Resets speed to 1x on new file load.
     *
     * @param filePath Absolute path to the audio file to play.
     */
    fun play(filePath: String) {
        // Validate file exists
        val file = File(filePath)
        if (!file.exists()) {
            _state.value = _state.value.copy(error = "Audio file not found.")
            return
        }

        // Release any existing player
        releasePlayer()

        try {
            val player = MediaPlayer().apply {
                setDataSource(filePath)
                prepare()
            }

            player.setOnCompletionListener {
                handlePlaybackComplete()
            }

            player.setOnErrorListener { _, what, extra ->
                val message = when (what) {
                    MediaPlayer.MEDIA_ERROR_IO -> "Error reading audio file."
                    MediaPlayer.MEDIA_ERROR_MALFORMED -> "Audio file format not supported."
                    MediaPlayer.MEDIA_ERROR_UNSUPPORTED -> "Audio format not supported."
                    else -> "An error occurred during playback."
                }
                _state.value = _state.value.copy(
                    isPlaying = false,
                    error = message
                )
                stopProgressUpdates()
                true
            }

            mediaPlayer = player

            val duration = player.duration
            _state.value = AudioPlayerState(
                isPlaying = true,
                currentPosition = 0,
                totalDuration = duration,
                currentSpeed = 1.0f,
                error = null
            )

            player.start()
            startProgressUpdates()
        } catch (e: IOException) {
            _state.value = _state.value.copy(error = "Unable to open audio file.")
        } catch (e: IllegalStateException) {
            _state.value = _state.value.copy(error = "Failed to initialize audio player.")
        } catch (e: IllegalArgumentException) {
            _state.value = _state.value.copy(error = "Invalid audio file path.")
        }
    }

    /**
     * Pauses the current playback. Has no effect if not currently playing.
     */
    fun pause() {
        val player = mediaPlayer ?: return
        if (!player.isPlaying) return

        try {
            player.pause()
            _state.value = _state.value.copy(
                isPlaying = false,
                currentPosition = player.currentPosition
            )
            stopProgressUpdates()
        } catch (e: IllegalStateException) {
            _state.value = _state.value.copy(error = "Failed to pause playback.")
        }
    }

    /**
     * Resumes playback from the current position. Has no effect if already playing.
     */
    fun resume() {
        val player = mediaPlayer ?: return
        if (player.isPlaying) return

        try {
            player.start()
            // Re-apply current speed after resuming
            applySpeed(_state.value.currentSpeed)
            _state.value = _state.value.copy(
                isPlaying = true,
                error = null
            )
            startProgressUpdates()
        } catch (e: IllegalStateException) {
            _state.value = _state.value.copy(error = "Failed to resume playback.")
        }
    }

    /**
     * Stops playback and resets position to the beginning.
     * The player remains prepared so play can be resumed via resume().
     */
    fun stop() {
        val player = mediaPlayer ?: return

        try {
            if (player.isPlaying) {
                player.pause()
            }
            player.seekTo(0)
            _state.value = _state.value.copy(
                isPlaying = false,
                currentPosition = 0
            )
            stopProgressUpdates()
        } catch (e: IllegalStateException) {
            _state.value = _state.value.copy(error = "Failed to stop playback.")
        }
    }

    /**
     * Sets the playback speed. Only values in [SUPPORTED_SPEEDS] are accepted.
     *
     * @param speed The desired playback speed (1.0f, 1.25f, 1.5f, or 2.0f).
     */
    fun setSpeed(speed: Float) {
        if (speed !in SUPPORTED_SPEEDS) return

        _state.value = _state.value.copy(currentSpeed = speed)
        applySpeed(speed)
    }

    /**
     * Seeks to a specific position in the audio.
     *
     * @param positionMs The target position in milliseconds.
     */
    fun seekTo(positionMs: Int) {
        val player = mediaPlayer ?: return
        val clampedPosition = positionMs.coerceIn(0, _state.value.totalDuration)

        try {
            player.seekTo(clampedPosition)
            _state.value = _state.value.copy(currentPosition = clampedPosition)
        } catch (e: IllegalStateException) {
            _state.value = _state.value.copy(error = "Failed to seek.")
        }
    }

    /**
     * Releases all resources held by the MediaPlayer.
     * Should be called when the service is no longer needed.
     * After calling release(), a new play() call is required to use the service again.
     */
    fun release() {
        stopProgressUpdates()
        releasePlayer()
        _state.value = AudioPlayerState()
    }

    /**
     * Clears the current error state.
     */
    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }

    private fun handlePlaybackComplete() {
        val player = mediaPlayer ?: return
        try {
            player.seekTo(0)
        } catch (_: IllegalStateException) {
            // Ignore seek errors on completion
        }
        _state.value = _state.value.copy(
            isPlaying = false,
            currentPosition = 0
        )
        stopProgressUpdates()
    }

    private fun applySpeed(speed: Float) {
        val player = mediaPlayer ?: return
        try {
            val params = PlaybackParams().setSpeed(speed)
            player.playbackParams = params
        } catch (_: IllegalStateException) {
            // Speed change not possible in current state — ignore silently
        } catch (_: IllegalArgumentException) {
            // Invalid speed value — ignore silently
        }
    }

    private fun startProgressUpdates() {
        stopProgressUpdates()
        progressJob = scope.launch {
            while (isActive) {
                val player = mediaPlayer
                if (player != null && player.isPlaying) {
                    _state.value = _state.value.copy(
                        currentPosition = player.currentPosition
                    )
                }
                delay(PROGRESS_UPDATE_INTERVAL_MS)
            }
        }
    }

    private fun stopProgressUpdates() {
        progressJob?.cancel()
        progressJob = null
    }

    private fun releasePlayer() {
        try {
            mediaPlayer?.release()
        } catch (_: Exception) {
            // Ignore release errors
        }
        mediaPlayer = null
    }
}
