package com.transcribecare.app.service

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.transcribecare.app.model.RecordingSession
import java.io.File

/**
 * Service responsible for formatting and sharing recording session data
 * via the Android native share sheet (ACTION_SEND intent).
 */
class ShareService {

    companion object {
        /**
         * Formats the session data into a human-readable share text string.
         *
         * The formatted text includes:
         * - Session title
         * - Session date
         * - All transcript segment text in order
         *
         * @param session The recording session to format.
         * @return A formatted string suitable for sharing.
         */
        fun formatShareText(session: RecordingSession): String {
            val builder = StringBuilder()
            builder.appendLine(session.title)
            builder.appendLine(session.date)
            builder.appendLine()

            session.segments.forEach { segment ->
                builder.appendLine(segment.text)
            }

            return builder.toString().trimEnd()
        }
    }

    /**
     * Creates a share Intent for the given recording session.
     *
     * If the session has an associated audio file, the intent will include
     * the audio file URI as EXTRA_STREAM and use a wildcard MIME type.
     * Otherwise, it shares plain text only.
     *
     * @param session The recording session to share.
     * @param context The Android context used for FileProvider URI resolution.
     * @return An ACTION_SEND Intent configured with the session's share content.
     */
    fun createShareIntent(session: RecordingSession, context: Context): Intent {
        val shareText = formatShareText(session)

        val intent = Intent(Intent.ACTION_SEND).apply {
            putExtra(Intent.EXTRA_TEXT, shareText)
        }

        val audioFilePath = session.audioFilePath
        if (audioFilePath != null) {
            val audioFile = File(audioFilePath)
            if (audioFile.exists()) {
                val authority = "${context.packageName}.fileprovider"
                val audioUri = FileProvider.getUriForFile(context, authority, audioFile)
                intent.type = "*/*"
                intent.putExtra(Intent.EXTRA_STREAM, audioUri)
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } else {
                intent.type = "text/plain"
            }
        } else {
            intent.type = "text/plain"
        }

        return intent
    }
}
