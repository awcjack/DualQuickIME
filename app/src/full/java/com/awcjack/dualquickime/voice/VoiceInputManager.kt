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
import openccjava.OpenCC
import java.io.File
import kotlin.concurrent.thread

/**
 * Manages voice input using Sherpa-ONNX SenseVoice for offline speech recognition.
 * Uses Silero VAD for voice activity detection (simulated streaming).
 * Supports Cantonese, Mandarin Chinese, English, Japanese, and Korean.
 *
 * The SenseVoice model is non-streaming but extremely fast (~70ms for 10s audio),
 * so we use VAD to detect speech segments and transcribe them as they complete.
 */
class VoiceInputManager(private val context: Context) {

    companion object {
        private const val TAG = "VoiceInputManager"
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val AUDIO_SOURCE = MediaRecorder.AudioSource.MIC

        // Model directory name (for compatibility with ModelDownloadManager)
        const val MODEL_DIR = ModelDownloadManager.SENSEVOICE_MODEL_DIR

        // Model files
        private const val MODEL_FILE = "model.int8.onnx"
        private const val TOKENS_FILE = "tokens.txt"

        // Punctuation conversion map (spoken words to symbols)
        // Supports Cantonese, Mandarin and English
        private val PUNCTUATION_MAP = mapOf(
            // Chinese punctuation - Mandarin (Simplified)
            "逗号" to "，",
            "句号" to "。",
            "问号" to "？",
            "感叹号" to "！",
            "叹号" to "！",
            "惊叹号" to "！",
            "冒号" to "：",
            "分号" to "；",
            "顿号" to "、",
            "省略号" to "……",
            "破折号" to "——",
            "连接号" to "—",
            "左括号" to "（",
            "右括号" to "）",
            "括号" to "（",
            "圆括号" to "（",
            "引号" to "「",
            "左引号" to "「",
            "右引号" to "」",
            "双引号" to "「",
            "单引号" to "『",
            "书名号" to "《",
            "左书名号" to "《",
            "右书名号" to "》",
            "方括号" to "【",
            "左方括号" to "【",
            "右方括号" to "】",
            "间隔号" to "·",
            "分隔号" to "／",
            "斜线" to "／",
            "波浪号" to "～",

            // Chinese punctuation - Cantonese/Traditional
            "逗號" to "，",
            "句號" to "。",
            "問號" to "？",
            "感嘆號" to "！",
            "感歎號" to "！",
            "嘆號" to "！",
            "歎號" to "！",
            "驚嘆號" to "！",
            "驚歎號" to "！",
            "冒號" to "：",
            "分號" to "；",
            "頓號" to "、",
            "省略號" to "……",
            "破折號" to "——",
            "連接號" to "—",
            "左括號" to "（",
            "右括號" to "）",
            "括號" to "（",
            "圓括號" to "（",
            "引號" to "「",
            "左引號" to "「",
            "右引號" to "」",
            "雙引號" to "「",
            "單引號" to "『",
            "書名號" to "《",
            "左書名號" to "《",
            "右書名號" to "》",
            "方括號" to "【",
            "左方括號" to "【",
            "右方括號" to "】",
            "間隔號" to "·",
            "分隔號" to "／",
            "斜線" to "／",
            "波浪號" to "～",

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
            "left bracket" to "[",
            "right bracket" to "]",
            "open bracket" to "[",
            "close bracket" to "]",
            "left brace" to "{",
            "right brace" to "}",
            "quotation mark" to "\"",
            "quote" to "\"",
            "double quote" to "\"",
            "single quote" to "'",
            "apostrophe" to "'",
            "slash" to "/",
            "backslash" to "\\",
            "at sign" to "@",
            "hash" to "#",
            "hashtag" to "#",
            "percent" to "%",
            "ampersand" to "&",
            "asterisk" to "*",
            "plus" to "+",
            "equals" to "=",
            "minus" to "-",
            "underscore" to "_",
            "tilde" to "~",

            // Common variations / shortcuts
            "dot" to "。",
            "點" to "。",
            "点" to "。",
            "句點" to "。",
            "句点" to "。",
            "逗點" to "，",
            "逗点" to "，",

            // Special commands (formatting)
            "空格" to " ",
            "换行" to "\n",
            "換行" to "\n",
            "新行" to "\n",
            "new line" to "\n",
            "newline" to "\n"
        )
    }

    // SenseVoice offline recognizer
    private var recognizer: OfflineRecognizer? = null

    // Silero VAD
    private var vad: Vad? = null

    // OpenCC converter for Simplified to Traditional Chinese (Hong Kong variant)
    // Cached instance for performance - initialization has overhead
    private var openccConverter: OpenCC? = null

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

    // Accumulated text from all segments in current session
    private var accumulatedText = StringBuilder()

    /**
     * Check if the model files are downloaded and available.
     */
    fun isModelAvailable(): Boolean {
        return ModelDownloadManager.isModelDownloaded(context)
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
            val vadModelPath = File(context.filesDir, ModelDownloadManager.VAD_MODEL_FILE).absolutePath

            // Initialize OpenCC converter (Simplified to Hong Kong Traditional)
            // s2hk: Simplified Chinese to Hong Kong Traditional Chinese
            openccConverter = OpenCC("s2hk")
            Log.i(TAG, "OpenCC converter initialized for s2hk conversion")

            // Initialize Silero VAD
            val vadConfig = VadModelConfig(
                sileroVadModelConfig = SileroVadModelConfig(
                    model = vadModelPath,
                    threshold = 0.5f,
                    minSilenceDuration = 0.5f,  // 500ms silence to detect endpoint
                    minSpeechDuration = 0.25f,  // Minimum 250ms speech
                    windowSize = 512,
                    maxSpeechDuration = 30.0f   // Maximum 30s per segment
                ),
                sampleRate = SAMPLE_RATE,
                numThreads = 1
            )

            vad = Vad(config = vadConfig)

            // Initialize SenseVoice recognizer
            val senseVoiceConfig = OfflineSenseVoiceModelConfig(
                model = "$modelDir/$MODEL_FILE",
                language = "auto",  // Auto-detect language (zh/yue/en/ja/ko)
                useInverseTextNormalization = false  // 2025-09-09 model doesn't support ITN
            )

            val modelConfig = OfflineModelConfig(
                senseVoice = senseVoiceConfig,
                tokens = "$modelDir/$TOKENS_FILE",
                numThreads = 2,
                debug = false
            )

            val config = OfflineRecognizerConfig(
                modelConfig = modelConfig,
                decodingMethod = "greedy_search"
            )

            recognizer = OfflineRecognizer(config = config)
            isInitialized = true
            Log.i(TAG, "SenseVoice recognizer initialized successfully")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize recognizer: ${e.message}", e)
            return false
        }
    }

    /**
     * Set callback for recognition results.
     * @param callback Called with (text, isFinal) - isFinal is true when segment completed
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
            accumulatedText.clear()
            audioRecord?.startRecording()

            recordingThread = thread(name = "VoiceInputThread") {
                processAudioWithVad()
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
        vad?.release()
        vad = null
        openccConverter = null
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
     * Convert Simplified Chinese to Traditional Chinese using OpenCC.
     * Uses s2hk (Simplified to Hong Kong Traditional) for best Cantonese support.
     * This is phrase-aware and handles context-dependent conversions.
     */
    private fun convertToTraditional(text: String): String {
        return try {
            openccConverter?.convert(text) ?: text
        } catch (e: Exception) {
            Log.w(TAG, "OpenCC conversion failed, returning original text: ${e.message}")
            text
        }
    }

    /**
     * Process recognized text: convert to traditional Chinese and replace punctuation.
     */
    private fun processRecognizedText(text: String): String {
        // First convert simplified to traditional Chinese using OpenCC
        val traditional = convertToTraditional(text)
        // Then convert spoken punctuation to symbols
        return convertPunctuation(traditional)
    }

    /**
     * Get the last recognized text (for committing when manually stopped).
     */
    fun getLastRecognizedText(): String = lastRecognizedText

    /**
     * Clear the accumulated text buffer (for reset functionality).
     */
    fun clearAccumulatedText() {
        accumulatedText.clear()
        lastRecognizedText = ""
    }

    /**
     * Process audio using VAD for endpoint detection and SenseVoice for transcription.
     */
    private fun processAudioWithVad() {
        val rec = recognizer ?: return
        val vadInstance = vad ?: return

        val bufferSize = 512  // Process in small chunks for responsive VAD
        val buffer = ShortArray(bufferSize)

        try {
            while (isRecording) {
                val ret = audioRecord?.read(buffer, 0, buffer.size) ?: -1

                if (ret > 0) {
                    // Convert 16-bit PCM to float
                    val samples = FloatArray(ret) { buffer[it] / 32768.0f }

                    // Feed samples to VAD
                    vadInstance.acceptWaveform(samples)

                    // Check if VAD detected speech segments
                    while (!vadInstance.empty()) {
                        val segment = vadInstance.front()
                        vadInstance.pop()

                        // Transcribe the speech segment using SenseVoice
                        val segmentSamples = segment.samples
                        if (segmentSamples.isNotEmpty()) {
                            val stream = rec.createStream()
                            stream.acceptWaveform(segmentSamples, SAMPLE_RATE)
                            rec.decode(stream)

                            val result = rec.getResult(stream)
                            var text = result.text.trim()

                            if (text.isNotEmpty()) {
                                // Process text: convert to traditional Chinese and replace punctuation
                                text = processRecognizedText(text)

                                // Append to accumulated text
                                if (accumulatedText.isNotEmpty()) {
                                    accumulatedText.append(" ")
                                }
                                accumulatedText.append(text)

                                lastRecognizedText = accumulatedText.toString()
                                onResultCallback?.invoke(lastRecognizedText, true)
                            }

                            stream.release()
                        }
                    }
                }
            }

            // Process any remaining audio in VAD buffer when stopping
            vadInstance.flush()
            while (!vadInstance.empty()) {
                val segment = vadInstance.front()
                vadInstance.pop()

                val segmentSamples = segment.samples
                if (segmentSamples.isNotEmpty()) {
                    val stream = rec.createStream()
                    stream.acceptWaveform(segmentSamples, SAMPLE_RATE)
                    rec.decode(stream)

                    val result = rec.getResult(stream)
                    var text = result.text.trim()

                    if (text.isNotEmpty()) {
                        text = processRecognizedText(text)

                        if (accumulatedText.isNotEmpty()) {
                            accumulatedText.append(" ")
                        }
                        accumulatedText.append(text)

                        lastRecognizedText = accumulatedText.toString()
                        onResultCallback?.invoke(lastRecognizedText, true)
                    }

                    stream.release()
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error processing audio: ${e.message}", e)
            onErrorCallback?.invoke("Error processing audio: ${e.message}")
        }
    }
}
