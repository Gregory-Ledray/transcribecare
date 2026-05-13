package com.transcribecare.app.service

import android.content.Context
import android.content.Intent
import android.net.Uri
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
     * uses ACTION_SEND_MULTIPLE to share both the audio file and a transcript
     * text file as separate attachments. This ensures apps like WhatsApp receive
     * both the audio and the transcript (WhatsApp drops EXTRA_TEXT when an audio
     * stream is attached via ACTION_SEND).
     *
     * If no audio file is available, shares plain text only via ACTION_SEND.
     *
     * @param session The recording session to share.
     * @param context The Android context used for FileProvider URI resolution.
     * @return An Intent configured with the session's share content.
     */
    fun createShareIntent(session: RecordingSession, context: Context): Intent {
        val shareText = formatShareText(session)
        val authority = "${context.packageName}.fileprovider"

        val audioFilePath = session.audioFilePath
        if (audioFilePath != null) {
            val audioFile = File(audioFilePath)
            if (audioFile.exists()) {
                try {
                    val audioUri = FileProvider.getUriForFile(context, authority, audioFile)
                    val transcriptFile = createTranscriptFile(session, context)
                    val transcriptUri = FileProvider.getUriForFile(context, authority, transcriptFile)

                    val uris = ArrayList<Uri>().apply {
                        add(audioUri)
                        add(transcriptUri)
                    }

                    return Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                        type = "*/*"
                        putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                        putExtra(Intent.EXTRA_SUBJECT, session.title)
                        putExtra(Intent.EXTRA_TEXT, shareText)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                } catch (e: IllegalArgumentException) {
                    Log.w(TAG, "Failed to create URI for share files: ${e.message}")
                }
            }
        }

        // Fallback: text-only share when no audio file is available
        return Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, shareText)
            putExtra(Intent.EXTRA_SUBJECT, session.title)
        }
    }

    /**
     * Creates a temporary .txt file containing the formatted transcript for sharing.
     *
     * The file is written to the app's cache directory under a "shared_transcripts"
     * subfolder so it can be served via FileProvider.
     *
     * @param session The recording session whose transcript to write.
     * @param context The Android context for accessing the cache directory.
     * @return The created transcript File.
     */
    private fun createTranscriptFile(session: RecordingSession, context: Context): File {
        val shareDir = File(context.cacheDir, "shared_transcripts")
        if (!shareDir.exists()) {
            shareDir.mkdirs()
        }

        val sanitizedTitle = session.title.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val transcriptFile = File(shareDir, "${sanitizedTitle}_transcript.txt")
        transcriptFile.writeText(formatShareText(session))
        return transcriptFile
    }
}
