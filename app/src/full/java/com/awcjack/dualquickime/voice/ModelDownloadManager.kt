package com.awcjack.dualquickime.voice

import android.content.Context
import android.util.Log
import java.io.*
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream
import kotlin.concurrent.thread

/**
 * Manages downloading and extracting voice recognition models.
 */
object ModelDownloadManager {

    private const val TAG = "ModelDownloadManager"

    // Model download URL (GitHub releases)
    private const val MODEL_URL = "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-streaming-paraformer-trilingual-zh-cantonese-en.tar.bz2"

    // Alternative: HuggingFace URL
    private const val MODEL_URL_HF = "https://huggingface.co/csukuangfj/sherpa-onnx-streaming-paraformer-trilingual-zh-cantonese-en/resolve/main/encoder.int8.onnx"

    // Individual file URLs from HuggingFace (more reliable for mobile)
    private const val BASE_HF_URL = "https://huggingface.co/csukuangfj/sherpa-onnx-streaming-paraformer-trilingual-zh-cantonese-en/resolve/main"

    private val MODEL_FILES = listOf(
        "encoder.int8.onnx" to 159_000_000L,  // ~159 MB
        "decoder.int8.onnx" to 69_000_000L,   // ~69 MB
        "tokens.txt" to 100_000L              // ~100 KB
    )

    // Total expected size in bytes (~228 MB)
    const val TOTAL_MODEL_SIZE = 228_000_000L

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
        val modelDir = File(context.filesDir, VoiceInputManager.MODEL_DIR)
        if (!modelDir.exists()) return false

        return MODEL_FILES.all { (filename, _) ->
            File(modelDir, filename).exists()
        }
    }

    /**
     * Get the download progress if partially downloaded.
     * @return Pair of (downloaded bytes, total bytes)
     */
    fun getDownloadProgress(context: Context): Pair<Long, Long> {
        val modelDir = File(context.filesDir, VoiceInputManager.MODEL_DIR)
        if (!modelDir.exists()) return 0L to TOTAL_MODEL_SIZE

        var downloaded = 0L
        MODEL_FILES.forEach { (filename, expectedSize) ->
            val file = File(modelDir, filename)
            if (file.exists()) {
                downloaded += file.length()
            }
        }

        return downloaded to TOTAL_MODEL_SIZE
    }

    /**
     * Download model files asynchronously.
     */
    fun downloadModel(context: Context, callback: DownloadCallback) {
        thread(name = "ModelDownloadThread") {
            try {
                val modelDir = File(context.filesDir, VoiceInputManager.MODEL_DIR)
                if (!modelDir.exists()) {
                    modelDir.mkdirs()
                }

                var totalDownloaded = 0L

                for ((filename, expectedSize) in MODEL_FILES) {
                    val targetFile = File(modelDir, filename)

                    // Skip if already downloaded
                    if (targetFile.exists() && targetFile.length() > expectedSize * 0.9) {
                        totalDownloaded += targetFile.length()
                        callback.onProgress(totalDownloaded, TOTAL_MODEL_SIZE, filename)
                        continue
                    }

                    Log.i(TAG, "Downloading $filename...")

                    val url = URL("$BASE_HF_URL/$filename")
                    val connection = url.openConnection() as HttpURLConnection
                    connection.connectTimeout = 30000
                    connection.readTimeout = 60000
                    connection.requestMethod = "GET"
                    connection.setRequestProperty("User-Agent", "DualQuickIME/1.0")

                    // Handle redirects
                    connection.instanceFollowRedirects = true

                    val responseCode = connection.responseCode
                    if (responseCode != HttpURLConnection.HTTP_OK) {
                        throw IOException("HTTP error: $responseCode for $filename")
                    }

                    val fileSize = connection.contentLengthLong

                    // Download to temp file first
                    val tempFile = File(modelDir, "$filename.tmp")

                    connection.inputStream.use { input ->
                        FileOutputStream(tempFile).use { output ->
                            val buffer = ByteArray(8192)
                            var bytesRead: Int
                            var fileDownloaded = 0L

                            while (input.read(buffer).also { bytesRead = it } != -1) {
                                output.write(buffer, 0, bytesRead)
                                fileDownloaded += bytesRead
                                totalDownloaded += bytesRead

                                callback.onProgress(totalDownloaded, TOTAL_MODEL_SIZE, filename)
                            }
                        }
                    }

                    // Rename temp file to final name
                    tempFile.renameTo(targetFile)
                    Log.i(TAG, "Downloaded $filename (${targetFile.length()} bytes)")
                }

                callback.onComplete()

            } catch (e: Exception) {
                Log.e(TAG, "Download failed: ${e.message}", e)
                callback.onError("Download failed: ${e.message}")
            }
        }
    }

    /**
     * Delete all downloaded model files.
     */
    fun deleteModel(context: Context) {
        val modelDir = File(context.filesDir, VoiceInputManager.MODEL_DIR)
        if (modelDir.exists()) {
            modelDir.deleteRecursively()
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
