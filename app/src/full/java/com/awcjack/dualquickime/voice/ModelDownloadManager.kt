package com.awcjack.dualquickime.voice

import android.content.Context
import android.util.Log
import java.io.*
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

/**
 * Manages downloading and extracting voice recognition models.
 * Now uses SenseVoice model for better Cantonese accuracy.
 */
object ModelDownloadManager {

    private const val TAG = "ModelDownloadManager"

    // SenseVoice model directory name
    const val SENSEVOICE_MODEL_DIR = "sherpa-onnx-sense-voice-zh-en-ja-ko-yue-int8-2025-09-09"

    // Silero VAD model filename
    const val VAD_MODEL_FILE = "silero_vad.onnx"

    // HuggingFace base URL for SenseVoice model
    private const val SENSEVOICE_BASE_URL = "https://huggingface.co/csukuangfj/sherpa-onnx-sense-voice-zh-en-ja-ko-yue-int8-2025-09-09/resolve/main"

    // GitHub URL for Silero VAD
    private const val VAD_URL = "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/silero_vad.onnx"

    // SenseVoice model files
    private val SENSEVOICE_FILES = listOf(
        "model.int8.onnx" to 226_000_000L,  // ~226 MB
        "tokens.txt" to 320_000L             // ~320 KB
    )

    // VAD model file
    private val VAD_FILE = VAD_MODEL_FILE to 630_000L  // ~630 KB

    // Total expected size in bytes (~227 MB)
    const val TOTAL_MODEL_SIZE = 227_000_000L

    /**
     * Download status callback
     */
    interface DownloadCallback {
        fun onProgress(bytesDownloaded: Long, totalBytes: Long, currentFile: String)
        fun onComplete()
        fun onError(message: String)
    }

    /**
     * Check if all model files exist.
     */
    fun isModelDownloaded(context: Context): Boolean {
        val modelDir = File(context.filesDir, SENSEVOICE_MODEL_DIR)
        if (!modelDir.exists()) return false

        // Check SenseVoice model files
        val senseVoiceReady = SENSEVOICE_FILES.all { (filename, _) ->
            File(modelDir, filename).exists()
        }

        // Check VAD model file
        val vadFile = File(context.filesDir, VAD_MODEL_FILE)

        return senseVoiceReady && vadFile.exists()
    }

    /**
     * Get the download progress if partially downloaded.
     * @return Pair of (downloaded bytes, total bytes)
     */
    fun getDownloadProgress(context: Context): Pair<Long, Long> {
        val modelDir = File(context.filesDir, SENSEVOICE_MODEL_DIR)
        var downloaded = 0L

        if (modelDir.exists()) {
            SENSEVOICE_FILES.forEach { (filename, _) ->
                val file = File(modelDir, filename)
                if (file.exists()) {
                    downloaded += file.length()
                }
            }
        }

        val vadFile = File(context.filesDir, VAD_MODEL_FILE)
        if (vadFile.exists()) {
            downloaded += vadFile.length()
        }

        return downloaded to TOTAL_MODEL_SIZE
    }

    /**
     * Download model files asynchronously.
     */
    fun downloadModel(context: Context, callback: DownloadCallback) {
        thread(name = "ModelDownloadThread") {
            try {
                val modelDir = File(context.filesDir, SENSEVOICE_MODEL_DIR)
                if (!modelDir.exists()) {
                    modelDir.mkdirs()
                }

                var totalDownloaded = 0L

                // Download SenseVoice model files
                for ((filename, expectedSize) in SENSEVOICE_FILES) {
                    val targetFile = File(modelDir, filename)

                    // Skip if already downloaded
                    if (targetFile.exists() && targetFile.length() > expectedSize * 0.9) {
                        totalDownloaded += targetFile.length()
                        callback.onProgress(totalDownloaded, TOTAL_MODEL_SIZE, filename)
                        continue
                    }

                    Log.i(TAG, "Downloading SenseVoice $filename...")
                    totalDownloaded = downloadFile(
                        "$SENSEVOICE_BASE_URL/$filename",
                        targetFile,
                        totalDownloaded,
                        filename,
                        callback
                    )
                }

                // Download VAD model file
                val vadFile = File(context.filesDir, VAD_MODEL_FILE)
                if (!vadFile.exists() || vadFile.length() < VAD_FILE.second * 0.9) {
                    Log.i(TAG, "Downloading Silero VAD...")
                    totalDownloaded = downloadFile(
                        VAD_URL,
                        vadFile,
                        totalDownloaded,
                        VAD_MODEL_FILE,
                        callback
                    )
                } else {
                    totalDownloaded += vadFile.length()
                    callback.onProgress(totalDownloaded, TOTAL_MODEL_SIZE, VAD_MODEL_FILE)
                }

                callback.onComplete()

            } catch (e: Exception) {
                Log.e(TAG, "Download failed: ${e.message}", e)
                callback.onError("Download failed: ${e.message}")
            }
        }
    }

    /**
     * Download a single file with progress reporting.
     */
    private fun downloadFile(
        urlString: String,
        targetFile: File,
        currentTotal: Long,
        filename: String,
        callback: DownloadCallback
    ): Long {
        var totalDownloaded = currentTotal

        val url = URL(urlString)
        val connection = url.openConnection() as HttpURLConnection
        connection.connectTimeout = 30000
        connection.readTimeout = 60000
        connection.requestMethod = "GET"
        connection.setRequestProperty("User-Agent", "DualQuickIME/1.0")
        connection.instanceFollowRedirects = true

        val responseCode = connection.responseCode
        if (responseCode != HttpURLConnection.HTTP_OK) {
            throw IOException("HTTP error: $responseCode for $filename")
        }

        // Download to temp file first
        val tempFile = File(targetFile.parent, "${targetFile.name}.tmp")

        connection.inputStream.use { input ->
            FileOutputStream(tempFile).use { output ->
                val buffer = ByteArray(8192)
                var bytesRead: Int

                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    totalDownloaded += bytesRead
                    callback.onProgress(totalDownloaded, TOTAL_MODEL_SIZE, filename)
                }
            }
        }

        // Rename temp file to final name
        tempFile.renameTo(targetFile)
        Log.i(TAG, "Downloaded $filename (${targetFile.length()} bytes)")

        return totalDownloaded
    }

    /**
     * Delete all downloaded model files.
     */
    fun deleteModel(context: Context) {
        // Delete SenseVoice model directory
        val modelDir = File(context.filesDir, SENSEVOICE_MODEL_DIR)
        if (modelDir.exists()) {
            modelDir.deleteRecursively()
        }

        // Delete VAD model
        val vadFile = File(context.filesDir, VAD_MODEL_FILE)
        if (vadFile.exists()) {
            vadFile.delete()
        }

        // Also delete old Paraformer model if it exists
        val oldModelDir = File(context.filesDir, "sherpa-onnx-streaming-paraformer-trilingual-zh-cantonese-en")
        if (oldModelDir.exists()) {
            oldModelDir.deleteRecursively()
        }
    }

    /**
     * Get model size in human-readable format.
     */
    fun getModelSizeString(): String {
        val mb = TOTAL_MODEL_SIZE / 1_000_000
        return "${mb} MB"
    }
}
