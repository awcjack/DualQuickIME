package com.awcjack.dualquickime.voice

import android.content.Context

/**
 * Stub ModelDownloadManager for lite flavor (no voice input support).
 */
object ModelDownloadManager {

    const val TOTAL_MODEL_SIZE = 0L

    interface DownloadCallback {
        fun onProgress(bytesDownloaded: Long, totalBytes: Long, currentFile: String)
        fun onComplete()
        fun onError(message: String)
    }

    fun isModelDownloaded(context: Context): Boolean = false
    fun getModelSizeOnDisk(context: Context): Long = 0
    fun downloadModel(context: Context, callback: DownloadCallback) {
        callback.onError("Voice input not available in lite version")
    }
    fun deleteModel(context: Context): Boolean = true
}
