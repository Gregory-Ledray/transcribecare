package com.transcribecare.app.service

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.FileProvider
import com.transcribecare.app.model.RecordingSession
import java.io.File

/**
 * Service responsible for formatting and sharing recording session data
 * via the Android native share sheet (ACTION_SEND intent).
 *
 * Supports sharing audio files to messaging apps (WhatsApp, SMS, etc.)
 * and other apps via the system share chooser.
 */
class ShareService {

    companion object {
        private const val TAG = "ShareService"

        /**
         * Formats the session data into a human-readable share text string.
         *
         * The formatted text includes:
         * - Session title
         * - Session date and time
         * - Duration
         * - All transcript segment text in order (if any)
         *
         * @param session The recording session to format.
         * @return A formatted string suitable for sharing.
         */
        fun formatShareText(session: RecordingSession): String {
            val builder = StringBuilder()
            builder.appendLine(session.title)
            builder.appendLine("${session.date} at ${session.time}")
            builder.appendLine("Duration: ${session.duration}")

            if (session.segments.isNotEmpty()) {
                builder.appendLine()
                builder.appendLine("Transcript:")
                session.segments.forEach { segment ->
                    builder.appendLine(segment.text)
                }
            }

            return builder.toString().trimEnd()
        }
    }

    /**
     * Creates a share Intent for the given recording session.
     *
     * If the session has an associated audio file that exists on disk, the intent
     * includes the audio file URI via FileProvider and uses "audio/wav" MIME type
     * for compatibility with messaging apps (WhatsApp, SMS/MMS, etc.).
     * The transcript text is included as EXTRA_TEXT alongside the audio attachment.
     *
     * If no audio file is available, shares plain text only.
     *
     * @param session The recording session to share.
     * @param context The Android context used for FileProvider URI resolution.
     * @return An ACTION_SEND Intent configured with the session's share content.
     */
    fun createShareIntent(session: RecordingSession, context: Context): Intent {
        val shareText = formatShareText(session)

        val intent = Intent(Intent.ACTION_SEND).apply {
            putExtra(Intent.EXTRA_TEXT, shareText)
            putExtra(Intent.EXTRA_SUBJECT, session.title)
        }

        val audioFilePath = session.audioFilePath
        if (audioFilePath != null) {
            val audioFile = File(audioFilePath)
            if (audioFile.exists()) {
                try {
                    val authority = "${context.packageName}.fileprovider"
                    val audioUri = FileProvider.getUriForFile(context, authority, audioFile)
                    intent.type = "audio/wav"
                    intent.putExtra(Intent.EXTRA_STREAM, audioUri)
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } catch (e: IllegalArgumentException) {
                    // FileProvider could not resolve the path — fall back to text-only
                    Log.w(TAG, "Failed to create URI for audio file: ${e.message}")
                    intent.type = "text/plain"
                }
            } else {
                intent.type = "text/plain"
            }
        } else {
            intent.type = "text/plain"
        }

        return intent
    }
}
