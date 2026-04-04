package com.awcjack.dualquickime.voice

import android.content.Context

/**
 * Stub ModelDownloadManager for lite flavor (no voice input support).
 */
object ModelDownloadManager {

    const val SENSEVOICE_MODEL_DIR = ""
    const val VAD_MODEL_FILE = ""
    const val TOTAL_MODEL_SIZE = 0L

    interface DownloadCallback {
        fun onProgress(bytesDownloaded: Long, totalBytes: Long, currentFile: String)
        fun onComplete()
        fun onError(message: String)
    }

    fun isModelDownloaded(context: Context): Boolean = false
    fun isModelDownloaded(context: Context, modelType: VoiceModelType): Boolean = false
    fun getDownloadProgress(context: Context): Pair<Long, Long> = 0L to 0L
    fun downloadModel(context: Context, callback: DownloadCallback) {
        callback.onError("Voice input not available in lite version")
    }
    fun downloadModel(context: Context, modelType: VoiceModelType, callback: DownloadCallback) {
        callback.onError("Voice input not available in lite version")
    }
    fun deleteModel(context: Context) {}
    fun deleteModel(context: Context, modelType: VoiceModelType) {}
    fun getModelSizeString(): String = "0 MB"
}
