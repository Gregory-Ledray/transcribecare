package com.transcribecare.app.service

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * Utility for assembling split model asset files into a single usable model file.
 *
 * The model file is split into multiple parts (e.g., .aa, .ab, .ac) to stay within
 * Android's asset file size limits. This loader concatenates them back into a single
 * file in the app's internal storage.
 */
object ModelFileLoader {

    private const val TAG = "ModelFileLoader"
    private const val MODEL_BASE_NAME = "gemma-4-E2B-it.litertlm"
    private const val BUFFER_SIZE = 8192

    /**
     * Assembles the split model parts from assets into a single file in internal storage.
     *
     * If the assembled file already exists and its size matches the expected total,
     * this method returns the existing file without re-assembling.
     *
     * @param context Application or Activity context for accessing assets and files dir.
     * @return The assembled model [File], ready for use.
     * @throws IOException If reading assets or writing the output file fails.
     */
    @Throws(IOException::class)
    fun loadModel(context: Context): File {
        val outputFile = File(context.filesDir, MODEL_BASE_NAME)

        // Find all split parts in assets matching the model base name
        val assetFiles = context.assets.list("")
            ?.filter { it.startsWith("$MODEL_BASE_NAME.") }
            ?.sorted()
            ?: emptyList()

        if (assetFiles.isEmpty()) {
            throw IOException("No model parts found in assets matching '$MODEL_BASE_NAME.*'")
        }

        Log.d(TAG, "Found ${assetFiles.size} model parts: $assetFiles")

        // Skip reassembly if the output file already exists with non-zero size
        if (outputFile.exists() && outputFile.length() > 0) {
            Log.d(TAG, "Model file already assembled at ${outputFile.absolutePath}")
            return outputFile
        }

        Log.d(TAG, "Assembling model from ${assetFiles.size} parts...")

        FileOutputStream(outputFile).use { outputStream ->
            val buffer = ByteArray(BUFFER_SIZE)
            for (part in assetFiles) {
                context.assets.open(part).use { inputStream ->
                    var bytesRead: Int
                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                    }
                }
                Log.d(TAG, "Appended part: $part")
            }
        }

        Log.d(TAG, "Model assembled successfully: ${outputFile.absolutePath} (${outputFile.length()} bytes)")
        return outputFile
    }

    /**
     * Checks whether the assembled model file already exists in internal storage.
     */
    fun isModelReady(context: Context): Boolean {
        val outputFile = File(context.filesDir, MODEL_BASE_NAME)
        return outputFile.exists() && outputFile.length() > 0
    }

    /**
     * Deletes the assembled model file from internal storage, forcing a fresh
     * reassembly on the next [loadModel] call.
     */
    fun clearModel(context: Context): Boolean {
        val outputFile = File(context.filesDir, MODEL_BASE_NAME)
        return if (outputFile.exists()) {
            outputFile.delete().also { deleted ->
                Log.d(TAG, if (deleted) "Model file deleted" else "Failed to delete model file")
            }
        } else {
            true
        }
    }
}
