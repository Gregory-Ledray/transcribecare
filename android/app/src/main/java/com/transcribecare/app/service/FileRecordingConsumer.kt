package com.transcribecare.app.service

import android.content.Context
import android.os.StatFs
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * AudioConsumer that writes raw PCM frames into a WAV file for later playback.
 *
 * On [prepare], the consumer checks available storage, creates a timestamped WAV file
 * in the app's recordings directory, and writes a 44-byte WAV header placeholder.
 * Each call to [onAudioFrame] appends PCM data to the file incrementally.
 * On [release], the WAV header is patched with the final data size and the file is closed.
 *
 * @param context Application context used to resolve the recordings directory.
 * @param onError Callback invoked with a descriptive message when an error occurs.
 */
class FileRecordingConsumer(
    private val context: Context,
    private val onError: (message: String) -> Unit
) : AudioConsumer {

    private var outputFile: RandomAccessFile? = null
    private var outputFilePath: String? = null
    private var dataSize: Int = 0
    private var wavHeader: WavHeader? = null

    companion object {
        private const val RECORDINGS_DIR = "recordings"
        private const val FILE_PREFIX = "recording_"
        private const val FILE_EXTENSION = ".wav"
    }

    /**
     * Prepares the consumer for recording.
     *
     * Checks that at least [AudioConfig.MIN_STORAGE_BYTES] of storage is available,
     * creates a timestamped WAV file, and writes a 44-byte header placeholder.
     *
     * @param sampleRate The audio sample rate in Hz.
     * @param channelCount The number of audio channels.
     * @param encoding The audio encoding format (used to derive bits per sample).
     */
    override fun prepare(sampleRate: Int, channelCount: Int, encoding: Int) {
        val recordingsDir = getRecordingsDirectory()
        if (recordingsDir == null) {
            onError("Unable to access storage for recording.")
            return
        }

        if (!hasAvailableStorage(recordingsDir)) {
            onError("Not enough storage space to record audio. At least 10 MB required.")
            return
        }

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val fileName = "$FILE_PREFIX$timestamp$FILE_EXTENSION"
        val file = File(recordingsDir, fileName)

        wavHeader = WavHeader(
            sampleRate = sampleRate,
            channelCount = channelCount,
            bitsPerSample = 16,
            dataSize = 0
        )

        try {
            outputFile = RandomAccessFile(file, "rw")
            outputFilePath = file.absolutePath
            dataSize = 0
            writeWavHeader(wavHeader!!)
        } catch (e: IOException) {
            onError("Failed to create recording file: ${e.message}")
            outputFile = null
            outputFilePath = null
        }
    }

    /**
     * Writes PCM audio data to the WAV file incrementally.
     *
     * Converts the ShortArray frame to little-endian bytes and appends to the file.
     *
     * @param frame ShortArray containing PCM samples.
     * @param frameSize Number of valid samples in the frame.
     */
    override fun onAudioFrame(frame: ShortArray, frameSize: Int) {
        val file = outputFile ?: return

        try {
            val byteBuffer = ByteBuffer.allocate(frameSize * 2)
            byteBuffer.order(ByteOrder.LITTLE_ENDIAN)
            for (i in 0 until frameSize) {
                byteBuffer.putShort(frame[i])
            }
            file.write(byteBuffer.array())
            dataSize += frameSize * 2
        } catch (e: IOException) {
            onError("Failed to write audio data: ${e.message}")
        }
    }

    /**
     * Finalizes the WAV file by patching the header with the actual data size,
     * then closes the file.
     */
    override fun release() {
        val file = outputFile ?: return

        try {
            patchWavHeader(file, dataSize)
        } catch (e: IOException) {
            onError("Failed to finalize recording file: ${e.message}")
        } finally {
            try {
                file.close()
            } catch (_: IOException) {
                // Ignore close errors
            }
            outputFile = null
        }
    }

    /**
     * Returns the absolute path of the output WAV file, or null if [prepare] has not
     * been called or failed.
     */
    fun getOutputFilePath(): String? = outputFilePath

    /**
     * Writes the 44-byte WAV header to the beginning of the file.
     * The data size fields are set to zero and will be patched on [release].
     */
    private fun writeWavHeader(header: WavHeader) {
        val file = outputFile ?: return
        val buffer = ByteBuffer.allocate(header.headerSize)
        buffer.order(ByteOrder.LITTLE_ENDIAN)

        // RIFF chunk descriptor
        buffer.put("RIFF".toByteArray(Charsets.US_ASCII))
        buffer.putInt(0) // Placeholder for file size - 8 (patched on release)
        buffer.put("WAVE".toByteArray(Charsets.US_ASCII))

        // fmt sub-chunk
        buffer.put("fmt ".toByteArray(Charsets.US_ASCII))
        buffer.putInt(16) // Sub-chunk size for PCM
        buffer.putShort(1) // Audio format: PCM = 1
        buffer.putShort(header.channelCount.toShort())
        buffer.putInt(header.sampleRate)
        buffer.putInt(header.byteRate)
        buffer.putShort(header.blockAlign.toShort())
        buffer.putShort(header.bitsPerSample.toShort())

        // data sub-chunk
        buffer.put("data".toByteArray(Charsets.US_ASCII))
        buffer.putInt(0) // Placeholder for data size (patched on release)

        file.write(buffer.array())
    }

    /**
     * Patches the WAV header with the final data size and overall file size.
     *
     * Seeks to byte offset 4 to write the RIFF chunk size (dataSize + 36),
     * then seeks to byte offset 40 to write the data sub-chunk size.
     */
    private fun patchWavHeader(file: RandomAccessFile, dataSize: Int) {
        // Patch RIFF chunk size at offset 4: total file size - 8
        file.seek(4)
        val riffSize = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(dataSize + 36).array()
        file.write(riffSize)

        // Patch data sub-chunk size at offset 40
        file.seek(40)
        val dataSizeBytes = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(dataSize).array()
        file.write(dataSizeBytes)
    }

    /**
     * Returns the recordings directory, preferring external storage with internal fallback.
     */
    private fun getRecordingsDirectory(): File? {
        val externalDir = context.getExternalFilesDir(RECORDINGS_DIR)
        if (externalDir != null && (externalDir.exists() || externalDir.mkdirs())) {
            return externalDir
        }

        val internalDir = File(context.filesDir, RECORDINGS_DIR)
        if (internalDir.exists() || internalDir.mkdirs()) {
            return internalDir
        }

        return null
    }

    /**
     * Checks whether the storage location has at least [AudioConfig.MIN_STORAGE_BYTES] available.
     */
    private fun hasAvailableStorage(directory: File): Boolean {
        return try {
            val stat = StatFs(directory.absolutePath)
            stat.availableBytes > AudioConfig.MIN_STORAGE_BYTES
        } catch (_: IllegalArgumentException) {
            false
        }
    }
}
