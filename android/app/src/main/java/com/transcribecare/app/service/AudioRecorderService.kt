package com.transcribecare.app.service

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.os.StatFs
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Wrapper around Android's MediaRecorder that provides AAC audio capture
 * with file path management and graceful error handling.
 *
 * Recordings are stored in the app's external files directory (or internal files
 * directory as fallback) using timestamped filenames for uniqueness.
 *
 * @param onError Callback invoked with a user-facing error message when recording
 *               encounters an issue (microphone in use, storage full, init failure).
 */
class AudioRecorderService(
    private val onError: (message: String) -> Unit
) {

    private var mediaRecorder: MediaRecorder? = null
    private var currentFilePath: String? = null
    private var isRecording: Boolean = false

    companion object {
        private const val AUDIO_FILE_PREFIX = "recording_"
        private const val AUDIO_FILE_EXTENSION = ".m4a"
        private const val RECORDINGS_DIR = "recordings"
        private const val MIN_STORAGE_BYTES = 10 * 1024 * 1024L // 10 MB minimum
    }

    /**
     * Starts audio recording in AAC format (MPEG-4 container).
     *
     * Creates a unique file in the app's recordings directory and begins capture.
     * Returns the file path on success, or null if recording could not be started.
     *
     * @param context Application context used to resolve the recordings directory.
     * @return The absolute file path of the recording, or null on failure.
     */
    fun startRecording(context: Context): String? {
        if (isRecording) {
            return currentFilePath
        }

        val outputFile = createOutputFile(context) ?: return null

        if (!hasAvailableStorage(outputFile)) {
            onError("Not enough storage space to record audio.")
            return null
        }

        return try {
            val recorder = createMediaRecorder(context, outputFile.absolutePath)
            recorder.prepare()
            recorder.start()

            mediaRecorder = recorder
            currentFilePath = outputFile.absolutePath
            isRecording = true

            currentFilePath
        } catch (e: IOException) {
            cleanup()
            outputFile.delete()
            onError("Microphone is in use by another app.")
            null
        } catch (e: IllegalStateException) {
            cleanup()
            outputFile.delete()
            onError("Failed to initialize audio recorder. Please try again.")
            null
        } catch (e: RuntimeException) {
            cleanup()
            outputFile.delete()
            onError("Failed to start recording. Please try again.")
            null
        }
    }

    /**
     * Stops the current recording and finalizes the audio file.
     *
     * @return The file path of the completed recording, or null if no recording was active.
     */
    fun stopRecording(): String? {
        if (!isRecording) {
            return null
        }

        val filePath = currentFilePath

        try {
            mediaRecorder?.stop()
        } catch (e: RuntimeException) {
            // stop() can throw if no audio data was recorded
            filePath?.let { File(it).delete() }
            cleanup()
            onError("Recording failed. No audio data was captured.")
            return null
        }

        cleanup()
        return filePath
    }

    /**
     * Releases all resources held by the MediaRecorder.
     * Should be called when the service is no longer needed.
     */
    fun release() {
        if (isRecording) {
            try {
                mediaRecorder?.stop()
            } catch (_: RuntimeException) {
                // Ignore — we're cleaning up regardless
            }
        }
        cleanup()
    }

    /**
     * Returns the file path of the current or most recent recording.
     */
    fun getCurrentFilePath(): String? = currentFilePath

    /**
     * Returns whether a recording is currently in progress.
     */
    fun isCurrentlyRecording(): Boolean = isRecording

    @Suppress("DEPRECATION")
    private fun createMediaRecorder(context: Context, outputPath: String): MediaRecorder {
        val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            MediaRecorder()
        }

        recorder.apply {
            setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioEncodingBitRate(128000)
            setAudioSamplingRate(44100)
            setOutputFile(outputPath)
            setOnErrorListener { _, what, _ ->
                val message = when (what) {
                    MediaRecorder.MEDIA_RECORDER_ERROR_UNKNOWN ->
                        "An unexpected recording error occurred."
                    MediaRecorder.MEDIA_ERROR_SERVER_DIED ->
                        "Recording service stopped unexpectedly."
                    else ->
                        "Recording error occurred."
                }
                onError(message)
                cleanup()
            }
        }

        return recorder
    }

    private fun createOutputFile(context: Context): File? {
        val recordingsDir = getRecordingsDirectory(context)
        if (recordingsDir == null) {
            onError("Unable to access storage for recording.")
            return null
        }

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val fileName = "$AUDIO_FILE_PREFIX$timestamp$AUDIO_FILE_EXTENSION"

        return File(recordingsDir, fileName)
    }

    private fun getRecordingsDirectory(context: Context): File? {
        // Prefer external files directory (accessible but app-private)
        val externalDir = context.getExternalFilesDir(RECORDINGS_DIR)
        if (externalDir != null && (externalDir.exists() || externalDir.mkdirs())) {
            return externalDir
        }

        // Fallback to internal files directory
        val internalDir = File(context.filesDir, RECORDINGS_DIR)
        if (internalDir.exists() || internalDir.mkdirs()) {
            return internalDir
        }

        return null
    }

    private fun hasAvailableStorage(outputFile: File): Boolean {
        return try {
            val parentDir = outputFile.parentFile ?: return false
            val stat = StatFs(parentDir.absolutePath)
            stat.availableBytes > MIN_STORAGE_BYTES
        } catch (_: IllegalArgumentException) {
            false
        }
    }

    private fun cleanup() {
        try {
            mediaRecorder?.reset()
            mediaRecorder?.release()
        } catch (_: Exception) {
            // Ignore cleanup errors
        }
        mediaRecorder = null
        isRecording = false
    }
}
