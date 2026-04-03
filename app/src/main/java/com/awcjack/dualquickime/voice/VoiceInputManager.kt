package com.awcjack.dualquickime.voice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.content.ContextCompat
import com.k2fsa.sherpa.onnx.*
import java.io.File
import kotlin.concurrent.thread

/**
 * Manages voice input using Sherpa-ONNX for offline speech recognition.
 * Supports Cantonese, Mandarin Chinese, and English.
 */
class VoiceInputManager(private val context: Context) {

    companion object {
        private const val TAG = "VoiceInputManager"
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val AUDIO_SOURCE = MediaRecorder.AudioSource.MIC

        // Model directory name
        const val MODEL_DIR = "sherpa-onnx-streaming-paraformer-trilingual-zh-cantonese-en"

        // Model files
        private const val ENCODER_FILE = "encoder.int8.onnx"
        private const val DECODER_FILE = "decoder.int8.onnx"
        private const val TOKENS_FILE = "tokens.txt"

        // Punctuation conversion map (spoken words to symbols)
        // Supports Cantonese, Mandarin and English
        private val PUNCTUATION_MAP = mapOf(
            // Chinese punctuation - Mandarin
            "逗号" to "，",
            "句号" to "。",
            "问号" to "？",
            "感叹号" to "！",
            "叹号" to "！",
            "冒号" to "：",
            "分号" to "；",
            "顿号" to "、",
            "省略号" to "……",
            "破折号" to "——",
            "左括号" to "（",
            "右括号" to "）",
            "引号" to "「",
            "左引号" to "「",
            "右引号" to "」",
            "书名号" to "《",
            "左书名号" to "《",
            "右书名号" to "》",

            // Chinese punctuation - Cantonese/Traditional
            "逗號" to "，",
            "句號" to "。",
            "問號" to "？",
            "感嘆號" to "！",
            "感歎號" to "！",
            "嘆號" to "！",
            "歎號" to "！",
            "冒號" to "：",
            "分號" to "；",
            "頓號" to "、",
            "省略號" to "……",
            "破折號" to "——",
            "左括號" to "（",
            "右括號" to "）",
            "引號" to "「",
            "左引號" to "「",
            "右引號" to "」",
            "書名號" to "《",
            "左書名號" to "《",
            "右書名號" to "》",

            // English punctuation
            "comma" to ",",
            "period" to ".",
            "full stop" to ".",
            "question mark" to "?",
            "exclamation mark" to "!",
            "exclamation point" to "!",
            "colon" to ":",
            "semicolon" to ";",
            "ellipsis" to "...",
            "dash" to "-",
            "hyphen" to "-",
            "left parenthesis" to "(",
            "right parenthesis" to ")",
            "open parenthesis" to "(",
            "close parenthesis" to ")",
            "quotation mark" to "\"",
            "quote" to "\"",
            "apostrophe" to "'",

            // Common variations
            "dot" to "。",
            "點" to "。",
            "点" to "。"
        )
    }

    private var recognizer: OnlineRecognizer? = null
    private var audioRecord: AudioRecord? = null
    private var recordingThread: Thread? = null

    @Volatile
    private var isRecording = false

    @Volatile
    private var isInitialized = false

    private var onResultCallback: ((String, Boolean) -> Unit)? = null
    private var onErrorCallback: ((String) -> Unit)? = null

    // Track the last recognized text for committing when stopped manually
    @Volatile
    private var lastRecognizedText: String = ""

    /**
     * Check if the model files are downloaded and available.
     */
    fun isModelAvailable(): Boolean {
        val modelDir = File(context.filesDir, MODEL_DIR)
        if (!modelDir.exists()) return false

        val encoder = File(modelDir, ENCODER_FILE)
        val decoder = File(modelDir, DECODER_FILE)
        val tokens = File(modelDir, TOKENS_FILE)

        return encoder.exists() && decoder.exists() && tokens.exists()
    }

    /**
     * Check if audio recording permission is granted.
     */
    fun hasAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Initialize the recognizer. Must be called on a background thread.
     * @return true if initialization succeeded
     */
    fun initialize(): Boolean {
        if (isInitialized) return true
        if (!isModelAvailable()) {
            Log.e(TAG, "Model files not available")
            return false
        }

        try {
            val modelDir = File(context.filesDir, MODEL_DIR).absolutePath

            val featConfig = FeatureConfig(
                sampleRate = SAMPLE_RATE,
                featureDim = 80
            )

            val modelConfig = OnlineModelConfig(
                paraformer = OnlineParaformerModelConfig(
                    encoder = "$modelDir/$ENCODER_FILE",
                    decoder = "$modelDir/$DECODER_FILE"
                ),
                tokens = "$modelDir/$TOKENS_FILE",
                numThreads = 2,
                debug = false
            )

            val endpointConfig = EndpointConfig(
                rule1 = EndpointRule(false, 2.4f, 0f),
                rule2 = EndpointRule(true, 1.4f, 0f),
                rule3 = EndpointRule(false, 0f, 20f)
            )

            val config = OnlineRecognizerConfig(
                featConfig = featConfig,
                modelConfig = modelConfig,
                endpointConfig = endpointConfig,
                enableEndpoint = true,
                decodingMethod = "greedy_search",
                maxActivePaths = 4
            )

            recognizer = OnlineRecognizer(config = config)
            isInitialized = true
            Log.i(TAG, "Recognizer initialized successfully")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize recognizer: ${e.message}", e)
            return false
        }
    }

    /**
     * Set callback for recognition results.
     * @param callback Called with (text, isFinal) - isFinal is true when endpoint detected
     */
    fun setOnResultListener(callback: (String, Boolean) -> Unit) {
        onResultCallback = callback
    }

    /**
     * Set callback for errors.
     */
    fun setOnErrorListener(callback: (String) -> Unit) {
        onErrorCallback = callback
    }

    /**
     * Start voice recording and recognition.
     * @return true if started successfully
     */
    fun startRecording(): Boolean {
        if (!isInitialized) {
            onErrorCallback?.invoke("Recognizer not initialized")
            return false
        }

        if (!hasAudioPermission()) {
            onErrorCallback?.invoke("Audio permission not granted")
            return false
        }

        if (isRecording) {
            return true
        }

        try {
            val minBufferSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT
            )

            audioRecord = AudioRecord(
                AUDIO_SOURCE,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                minBufferSize * 2
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                onErrorCallback?.invoke("Failed to initialize audio recorder")
                return false
            }

            isRecording = true
            lastRecognizedText = ""
            audioRecord?.startRecording()

            recordingThread = thread(name = "VoiceInputThread") {
                processAudio()
            }

            Log.i(TAG, "Recording started")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start recording: ${e.message}", e)
            onErrorCallback?.invoke("Failed to start recording: ${e.message}")
            isRecording = false
            return false
        }
    }

    /**
     * Stop voice recording.
     */
    fun stopRecording() {
        isRecording = false

        try {
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping audio record: ${e.message}")
        }

        recordingThread?.join(1000)
        recordingThread = null

        Log.i(TAG, "Recording stopped")
    }

    /**
     * Check if currently recording.
     */
    fun isRecording(): Boolean = isRecording

    /**
     * Release all resources.
     */
    fun release() {
        stopRecording()
        recognizer?.release()
        recognizer = null
        isInitialized = false
    }

    /**
     * Convert spoken punctuation words to actual punctuation marks.
     */
    private fun convertPunctuation(text: String): String {
        var result = text
        for ((spoken, symbol) in PUNCTUATION_MAP) {
            // Replace whole word matches (case-insensitive for English)
            result = result.replace(spoken, symbol, ignoreCase = true)
        }
        return result
    }

    /**
     * Get the last recognized text (for committing when manually stopped).
     */
    fun getLastRecognizedText(): String = lastRecognizedText

    private fun processAudio() {
        val rec = recognizer ?: return
        val stream = rec.createStream()

        val interval = 0.1 // 100ms chunks
        val bufferSize = (interval * SAMPLE_RATE).toInt()
        val buffer = ShortArray(bufferSize)

        var lastText = ""

        try {
            while (isRecording) {
                val ret = audioRecord?.read(buffer, 0, buffer.size) ?: -1

                if (ret > 0) {
                    // Convert 16-bit PCM to float
                    val samples = FloatArray(ret) { buffer[it] / 32768.0f }
                    stream.acceptWaveform(samples, sampleRate = SAMPLE_RATE)

                    while (rec.isReady(stream)) {
                        rec.decode(stream)
                    }

                    val isEndpoint = rec.isEndpoint(stream)
                    var text = rec.getResult(stream).text

                    // Add tail padding for paraformer at endpoint
                    if (isEndpoint) {
                        val tailPaddings = FloatArray((0.8 * SAMPLE_RATE).toInt())
                        stream.acceptWaveform(tailPaddings, sampleRate = SAMPLE_RATE)
                        while (rec.isReady(stream)) {
                            rec.decode(stream)
                        }
                        text = rec.getResult(stream).text
                    }

                    // Convert spoken punctuation to symbols
                    val processedText = convertPunctuation(text)

                    // Only notify if text changed
                    if (processedText != lastText && processedText.isNotEmpty()) {
                        lastText = processedText
                        lastRecognizedText = processedText
                        onResultCallback?.invoke(processedText, isEndpoint)
                    }

                    if (isEndpoint) {
                        rec.reset(stream)
                        lastText = ""
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing audio: ${e.message}", e)
            onErrorCallback?.invoke("Error processing audio: ${e.message}")
        } finally {
            stream.release()
        }
    }
}
