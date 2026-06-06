package com.awcjack.dualquickime.voice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.content.ContextCompat
import com.awcjack.dualquickime.BuildConfig
import com.k2fsa.sherpa.onnx.*
import openccjava.OpenCC
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * Manages voice input using Sherpa-ONNX for offline speech recognition.
 *
 * Supported models:
 * - SenseVoice: Multilingual (Cantonese, Mandarin, English, Japanese, Korean)
 * - Whisper Cantonese: Optimized for Cantonese (7.93% CER)
 * - U2pp-Conformer-Yue: Best accuracy-to-size ratio (5.05% MER)
 * - Qwen3-ASR: Best Cantonese accuracy (~4.12% WER) via Sherpa-ONNX
 *   prebuilt INT8 export (PR #3409); autoregressive decode, higher latency.
 *
 * Uses Silero VAD for voice activity detection (shared across all models).
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

        // SenseVoice model files
        private const val SENSEVOICE_MODEL_FILE = "model.int8.onnx"
        private const val SENSEVOICE_TOKENS_FILE = "tokens.txt"

        // Whisper Cantonese model files (converted via CI workflow)
        private const val WHISPER_ENCODER_FILE = "small-encoder.int8.onnx"
        private const val WHISPER_DECODER_FILE = "small-decoder.int8.onnx"
        private const val WHISPER_TOKENS_FILE = "small-tokens.txt"

        // U2pp-Conformer-Yue model files (CTC architecture)
        private const val U2PP_CONFORMER_MODEL_FILE = "model.int8.onnx"
        private const val U2PP_CONFORMER_TOKENS_FILE = "tokens.txt"

        // Qwen3-ASR model files (Sherpa-ONNX prebuilt INT8 export, PR #3409)
        private const val QWEN3_ASR_CONV_FRONTEND_FILE = "conv_frontend.onnx"
        private const val QWEN3_ASR_ENCODER_FILE = "encoder.int8.onnx"
        private const val QWEN3_ASR_DECODER_FILE = "decoder.int8.onnx"
        private const val QWEN3_ASR_TOKENIZER_DIR = "tokenizer"

        // Memory-pressure guards for the 700 MB Qwen3-ASR recognizer.
        // Release the model after this many ms of recording idle so the IME
        // process doesn't sit on a gigabyte of weights while the user is
        // typing normally. The next mic tap pays a ~1–2 s reload cost.
        private const val QWEN3_ASR_IDLE_RELEASE_MS = 30_000L
        // How often the idle monitor thread wakes to check. Coarse-grained to
        // avoid burning battery on a tight loop.
        private const val IDLE_CHECK_INTERVAL_MS = 5_000L

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

    // Current model type
    private var currentModelType: VoiceModelType = VoiceModelType.DEFAULT

    // Sherpa-ONNX recognizer (used for every supported model type)
    private var recognizer: OfflineRecognizer? = null

    // Silero VAD (shared across all model types via Sherpa-ONNX)
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

    // Wall-clock of the last user-visible voice activity. Drives idle release
    // of the Qwen3-ASR recognizer so the IME doesn't hold ~700 MB resident
    // between sessions. Updated on init, start/stop recording, and segment
    // recognition.
    @Volatile
    private var lastActivityMillis: Long = 0L

    private var idleReleaseThread: Thread? = null
    private val idleReleaseActive = AtomicBoolean(false)
    // Guards the recognizer field across the lazy-reload path and the idle
    // releaser thread — both can flip it between null and non-null.
    private val recognizerLock = Any()

    private var onResultCallback: ((String, Boolean) -> Unit)? = null
    private var onErrorCallback: ((String) -> Unit)? = null

    // Track the last recognized text for committing when stopped manually
    @Volatile
    private var lastRecognizedText: String = ""

    // Accumulated text from all segments in current session
    private var accumulatedText = StringBuilder()

    // Track last segment result to detect VAD duplicates
    private var lastSegmentText: String = ""

    /**
     * Check if the model files are downloaded and available for the current model type.
     */
    fun isModelAvailable(): Boolean {
        return ModelDownloadManager.isModelDownloaded(context, currentModelType)
    }

    /**
     * Check if a specific model type is available.
     */
    fun isModelAvailable(modelType: VoiceModelType): Boolean {
        return ModelDownloadManager.isModelDownloaded(context, modelType)
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
     * Get the current model type.
     */
    fun getCurrentModelType(): VoiceModelType = currentModelType

    /**
     * Set the model type to use. Will reinitialize if already initialized.
     */
    fun setModelType(modelType: VoiceModelType) {
        if (currentModelType != modelType) {
            val wasInitialized = isInitialized
            if (wasInitialized) {
                release()
            }
            currentModelType = modelType
            if (wasInitialized && isModelAvailable(modelType)) {
                initialize()
            }
        }
    }

    /**
     * Initialize the recognizer. Must be called on a background thread.
     * @return true if initialization succeeded
     */
    fun initialize(): Boolean {
        if (isInitialized) return true
        if (!isModelAvailable()) {
            if (BuildConfig.DEBUG) {
                Log.e(TAG, "Model files not available for ${currentModelType.id}")
            }
            return false
        }

        try {
            val modelDir = File(context.filesDir, currentModelType.modelDir).absolutePath
            val vadModelPath = File(context.filesDir, ModelDownloadManager.VAD_MODEL_FILE).absolutePath

            // Initialize OpenCC converter (Simplified to Hong Kong Traditional)
            // s2hk: Simplified Chinese to Hong Kong Traditional Chinese
            openccConverter = OpenCC("s2hk")
            if (BuildConfig.DEBUG) {
                Log.i(TAG, "OpenCC converter initialized for s2hk conversion")
            }

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

            // Initialize the appropriate recognizer based on model type
            recognizer = when (currentModelType) {
                VoiceModelType.SENSE_VOICE -> initSenseVoiceRecognizer(modelDir)
                VoiceModelType.WHISPER_CANTONESE -> initWhisperRecognizer(modelDir)
                VoiceModelType.U2PP_CONFORMER_YUE -> initU2ppConformerRecognizer(modelDir)
                VoiceModelType.QWEN3_ASR -> initQwen3AsrRecognizer(modelDir)
            }

            // Warm up Qwen3-ASR so the first real utterance isn't penalized by
            // graph optimization + weight unpack. No-op for the lighter models.
            warmUpRecognizer()

            isInitialized = true
            markActivity()
            // Idle releaser is a no-op for non-Qwen3-ASR models.
            startIdleReleaseMonitor()
            if (BuildConfig.DEBUG) {
                Log.i(TAG, "${currentModelType.id} recognizer initialized successfully")
            }
            return true
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) {
                Log.e(TAG, "Failed to initialize recognizer: ${e.message}", e)
            }
            return false
        }
    }

    /**
     * Initialize SenseVoice recognizer.
     */
    private fun initSenseVoiceRecognizer(modelDir: String): OfflineRecognizer {
        val senseVoiceConfig = OfflineSenseVoiceModelConfig(
            model = "$modelDir/$SENSEVOICE_MODEL_FILE",
            language = "auto",  // Auto-detect language (zh/yue/en/ja/ko)
            useInverseTextNormalization = false  // 2025-09-09 model doesn't support ITN
        )

        val modelConfig = OfflineModelConfig(
            senseVoice = senseVoiceConfig,
            tokens = "$modelDir/$SENSEVOICE_TOKENS_FILE",
            numThreads = 2,
            debug = false
        )

        val config = OfflineRecognizerConfig(
            modelConfig = modelConfig,
            decodingMethod = "greedy_search"
        )

        return OfflineRecognizer(config = config)
    }

    /**
     * Initialize Whisper Cantonese recognizer.
     * Note: Uses "zh" language code because the fine-tuned model was trained on Cantonese
     * data using the Chinese (zh) base model. The model doesn't have a native "yue" language token.
     *
     * Key configuration:
     * - tailPaddings: Add padding at the end of audio to help with short segments
     * - language: "zh" for Chinese/Cantonese
     * - task: "transcribe" for speech-to-text
     */
    private fun initWhisperRecognizer(modelDir: String): OfflineRecognizer {
        val whisperConfig = OfflineWhisperModelConfig(
            encoder = "$modelDir/$WHISPER_ENCODER_FILE",
            decoder = "$modelDir/$WHISPER_DECODER_FILE",
            language = "zh",  // Use Chinese - model fine-tuned for Cantonese on zh base
            task = "transcribe",
            tailPaddings = 1000  // Add padding to help with short VAD segments
        )

        val modelConfig = OfflineModelConfig(
            whisper = whisperConfig,
            tokens = "$modelDir/$WHISPER_TOKENS_FILE",
            numThreads = 2,
            debug = false
        )

        val config = OfflineRecognizerConfig(
            modelConfig = modelConfig,
            decodingMethod = "greedy_search"
        )

        return OfflineRecognizer(config = config)
    }

    /**
     * Initialize U2pp-Conformer-Yue recognizer.
     * CTC-based Conformer model trained on WenetSpeech-Yue.
     * Best accuracy-to-size ratio for Cantonese (130M params, 5.05% MER).
     */
    private fun initU2ppConformerRecognizer(modelDir: String): OfflineRecognizer {
        // U2pp-Conformer uses WeNet CTC architecture
        val wenetConfig = OfflineWenetCtcModelConfig(
            model = "$modelDir/$U2PP_CONFORMER_MODEL_FILE"
        )

        val modelConfig = OfflineModelConfig(
            wenetCtc = wenetConfig,
            tokens = "$modelDir/$U2PP_CONFORMER_TOKENS_FILE",
            numThreads = 2,
            debug = false
        )

        val config = OfflineRecognizerConfig(
            modelConfig = modelConfig,
            decodingMethod = "greedy_search"
        )

        return OfflineRecognizer(config = config)
    }

    /**
     * Initialize Qwen3-ASR recognizer.
     *
     * Backed by Sherpa-ONNX's prebuilt INT8 export
     * (sherpa-onnx-qwen3-asr-0.6B-int8-2026-03-25, PR #3409): a 3-stage
     * pipeline of conv_frontend → AuT audio encoder → Qwen3 LLM decoder with
     * KV-cache. `tokens` is intentionally empty — Qwen3-ASR carries its
     * tokenizer files under the `tokenizer/` directory specified in the
     * sub-config.
     *
     * Speed tuning for IME use:
     * - numThreads=4 to take advantage of the big-core cluster while voice
     *   recording is the user's foreground task. Anything higher risks
     *   thermal throttling and L2 contention.
     * - maxNewTokens=80 instead of upstream's 128. Real IME utterances cap
     *   out around 60 tokens; the tighter ceiling bounds pathological
     *   "decoder hallucinates a long repetition" failures so the keyboard
     *   doesn't freeze for 10+ seconds.
     * - Temperature/topP/seed left at upstream defaults — temperature=1e-6
     *   is effectively greedy so topP/seed are inert.
     *
     * Latency note: autoregressive decode means transcription is still
     * several× slower than CTC models on mobile CPUs — usable for
     * tap-and-wait but not real-time dictation.
     */
    private fun initQwen3AsrRecognizer(modelDir: String): OfflineRecognizer {
        val qwen3Config = OfflineQwen3AsrModelConfig(
            convFrontend = "$modelDir/$QWEN3_ASR_CONV_FRONTEND_FILE",
            encoder = "$modelDir/$QWEN3_ASR_ENCODER_FILE",
            decoder = "$modelDir/$QWEN3_ASR_DECODER_FILE",
            tokenizer = "$modelDir/$QWEN3_ASR_TOKENIZER_DIR",
            maxNewTokens = 80
        )

        val modelConfig = OfflineModelConfig(
            qwen3Asr = qwen3Config,
            tokens = "",  // Qwen3-ASR carries its own tokenizer dir, no separate tokens file
            numThreads = 4,
            debug = false
        )

        val config = OfflineRecognizerConfig(
            modelConfig = modelConfig,
            decodingMethod = "greedy_search"
        )

        return OfflineRecognizer(config = config)
    }

    /**
     * Whether the current model is heavy enough to warrant idle release.
     * Only Qwen3-ASR qualifies — the other recognizers are <400 MB resident
     * and the reload cost would outweigh the memory savings.
     */
    private fun usesIdleRelease(): Boolean = currentModelType == VoiceModelType.QWEN3_ASR

    /**
     * Mark the recognizer as freshly used. Resets the idle clock so the
     * background releaser holds onto the model for at least
     * QWEN3_ASR_IDLE_RELEASE_MS more.
     */
    private fun markActivity() {
        lastActivityMillis = System.currentTimeMillis()
    }

    /**
     * Start the background thread that releases the Qwen3-ASR recognizer
     * after [QWEN3_ASR_IDLE_RELEASE_MS] of no voice activity. No-op for
     * lighter models. Idempotent.
     */
    private fun startIdleReleaseMonitor() {
        if (!usesIdleRelease()) return
        if (!idleReleaseActive.compareAndSet(false, true)) return

        idleReleaseThread = thread(name = "VoiceIdleReleaseThread") {
            try {
                while (idleReleaseActive.get()) {
                    try {
                        Thread.sleep(IDLE_CHECK_INTERVAL_MS)
                    } catch (e: InterruptedException) {
                        break
                    }

                    val now = System.currentTimeMillis()
                    val idleFor = now - lastActivityMillis
                    val canRelease = idleFor >= QWEN3_ASR_IDLE_RELEASE_MS &&
                        !isRecording &&
                        synchronized(recognizerLock) { recognizer != null }

                    if (canRelease) {
                        if (BuildConfig.DEBUG) {
                            Log.i(TAG, "Qwen3-ASR idle for ${idleFor}ms; releasing recognizer to free ~700 MB")
                        }
                        releaseRecognizerOnly()
                        // Keep the monitor running. The next mic tap triggers a lazy
                        // reload which will arm us again via markActivity().
                    }
                }
            } finally {
                idleReleaseActive.set(false)
            }
        }
    }

    /**
     * Stop the idle release monitor. Called on full release.
     */
    private fun stopIdleReleaseMonitor() {
        idleReleaseActive.set(false)
        idleReleaseThread?.interrupt()
        idleReleaseThread = null
    }

    /**
     * Release only the recognizer (the heavy ~700 MB component), keeping
     * VAD, OpenCC and audio recording state alive. The next call to
     * [ensureRecognizerLoaded] rebuilds it.
     */
    private fun releaseRecognizerOnly() {
        synchronized(recognizerLock) {
            try {
                recognizer?.release()
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) {
                    Log.w(TAG, "Recognizer release threw: ${e.message}")
                }
            }
            recognizer = null
        }
    }

    /**
     * Rebuild the recognizer after a previous idle release. Returns true if
     * the recognizer is ready after the call. Called from [startRecording]
     * before audio capture begins so the reload cost happens up front
     * rather than mid-utterance.
     */
    private fun ensureRecognizerLoaded(): Boolean {
        synchronized(recognizerLock) {
            if (recognizer != null) return true
            if (!isInitialized) return false

            return try {
                val modelDir = File(context.filesDir, currentModelType.modelDir).absolutePath
                recognizer = when (currentModelType) {
                    VoiceModelType.SENSE_VOICE -> initSenseVoiceRecognizer(modelDir)
                    VoiceModelType.WHISPER_CANTONESE -> initWhisperRecognizer(modelDir)
                    VoiceModelType.U2PP_CONFORMER_YUE -> initU2ppConformerRecognizer(modelDir)
                    VoiceModelType.QWEN3_ASR -> initQwen3AsrRecognizer(modelDir)
                }
                warmUpRecognizer()
                if (BuildConfig.DEBUG) {
                    Log.i(TAG, "Recognizer reloaded after idle release")
                }
                true
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) {
                    Log.e(TAG, "Failed to reload recognizer: ${e.message}", e)
                }
                false
            }
        }
    }

    /**
     * Run one throwaway decode so the ONNX Runtime graph optimizer, weight
     * dequantization caches and KV-cache buffers are populated before the
     * user speaks. Only meaningful for Qwen3-ASR — the autoregressive
     * decoder otherwise pays a 0.5–2 s one-time tax on the first utterance.
     *
     * Trade-off: this extends "Loading…" by the warmup time. For the
     * smaller CTC models the tax is negligible, so we skip warmup for them
     * and surface "Listening…" sooner.
     */
    private fun warmUpRecognizer() {
        val rec = recognizer ?: return
        if (currentModelType != VoiceModelType.QWEN3_ASR) return

        try {
            // 0.5 s of silence — long enough to drive a full encoder+decoder pass
            // through the pipeline, short enough that warmup stays under ~1 s
            // wall clock on a mid-range device.
            val silence = FloatArray(SAMPLE_RATE / 2)
            val stream = rec.createStream()
            stream.acceptWaveform(silence, SAMPLE_RATE)
            rec.decode(stream)
            // Discard result — silence usually decodes to empty or filler tokens.
            rec.getResult(stream)
            stream.release()
            if (BuildConfig.DEBUG) {
                Log.i(TAG, "Qwen3-ASR warmup complete")
            }
        } catch (e: Exception) {
            // Warmup failure is non-fatal; first real utterance will pay the tax.
            if (BuildConfig.DEBUG) {
                Log.w(TAG, "Qwen3-ASR warmup failed (non-fatal): ${e.message}")
            }
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

        // If the idle releaser dropped the recognizer between sessions,
        // rebuild + warm it before opening the mic so users aren't holding
        // a stale handle.
        if (!ensureRecognizerLoaded()) {
            onErrorCallback?.invoke("Failed to reload recognizer")
            return false
        }
        markActivity()

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
            lastSegmentText = ""

            // Reset VAD state to clear any leftover segments from previous recordings
            vad?.reset()

            audioRecord?.startRecording()

            recordingThread = thread(name = "VoiceInputThread") {
                processAudioWithVad()
            }

            if (BuildConfig.DEBUG) {
                Log.i(TAG, "Recording started with ${currentModelType.id}")
            }
            return true
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) {
                Log.e(TAG, "Failed to start recording: ${e.message}", e)
            }
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
            if (BuildConfig.DEBUG) {
                Log.e(TAG, "Error stopping audio record: ${e.message}")
            }
        }

        recordingThread?.join(1000)
        recordingThread = null

        // Bump activity so the idle releaser holds the recognizer for the
        // QWEN3_ASR_IDLE_RELEASE_MS grace window after a session ends —
        // long enough to cover the user immediately tapping mic again.
        markActivity()

        if (BuildConfig.DEBUG) {
            Log.i(TAG, "Recording stopped")
        }
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
        stopIdleReleaseMonitor()
        releaseRecognizerOnly()
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
     * Check if a character belongs to a CJK Unicode block that OpenCC should process.
     * Covers the main CJK ranges:
     * - 0x2E80..0x2FDF: CJK Radicals Supplement, Kangxi Radicals
     * - 0x3000..0x303F: CJK Symbols and Punctuation
     * - 0x3400..0x4DBF: CJK Unified Ideographs Extension A
     * - 0x4E00..0x9FFF: CJK Unified Ideographs (main block)
     * - 0xF900..0xFAFF: CJK Compatibility Ideographs
     * - 0xFE30..0xFE4F: CJK Compatibility Forms
     * - 0xFF00..0xFFEF: Halfwidth and Fullwidth Forms
     */
    private fun isCjkCharacter(ch: Char): Boolean {
        return ch.code in 0x4E00..0x9FFF || ch.code in 0x3400..0x4DBF ||
                ch.code in 0x2E80..0x2FDF || ch.code in 0x3000..0x303F ||
                ch.code in 0xF900..0xFAFF || ch.code in 0xFE30..0xFE4F ||
                ch.code in 0xFF00..0xFFEF
    }

    /**
     * Convert Simplified Chinese to Traditional Chinese using OpenCC.
     * Uses s2hk (Simplified to Hong Kong Traditional) for best Cantonese support.
     * This is phrase-aware and handles context-dependent conversions.
     * Only CJK characters are passed to OpenCC; non-CJK segments are preserved as-is.
     */
    private fun convertToTraditional(text: String): String {
        return try {
            val converter = openccConverter ?: return text
            // Split text into CJK and non-CJK segments, apply OpenCC only to CJK portions
            val result = StringBuilder()
            val segment = StringBuilder()
            var inCjk = false

            for (ch in text) {
                if (isCjkCharacter(ch) == inCjk) {
                    segment.append(ch)
                } else {
                    // Flush the previous segment
                    if (segment.isNotEmpty()) {
                        result.append(if (inCjk) converter.convert(segment.toString()) else segment)
                        segment.clear()
                    }
                    segment.append(ch)
                    inCjk = !inCjk
                }
            }
            // Flush remaining segment
            if (segment.isNotEmpty()) {
                result.append(if (inCjk) converter.convert(segment.toString()) else segment)
            }
            result.toString()
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) {
                Log.w(TAG, "OpenCC conversion failed, returning original text: ${e.message}")
            }
            text
        }
    }

    /**
     * Lowercase Latin characters in text while preserving non-Latin characters.
     * Voice recognition models often output English in uppercase; this normalizes it.
     * Handles full Unicode Latin character set including accented characters.
     */
    private fun lowercaseLatinCharacters(text: String): String {
        val result = StringBuilder(text.length)
        for (ch in text) {
            result.append(if (ch.isUpperCase() && ch.isLetter() && !isCjkCharacter(ch)) ch.lowercaseChar() else ch)
        }
        return result.toString()
    }

    /**
     * Process recognized text: convert to traditional Chinese and replace punctuation.
     * English portions are lowercased since voice models tend to output uppercase English.
     * OpenCC conversion is applied only to CJK characters to avoid affecting English text.
     */
    private fun processRecognizedText(text: String): String {
        // Strip Whisper special tokens (e.g., <|transcribe|>, <|notimestamps|>, <|en|>, etc.)
        var cleaned = stripWhisperTokens(text)

        // For Whisper models only, remove repetition patterns (a known Whisper hallucination issue)
        // Qwen3-ASR and conformer-based models do not exhibit this behavior
        if (currentModelType == VoiceModelType.WHISPER_CANTONESE) {
            cleaned = removeRepetition(cleaned)
        }

        // Lowercase Latin characters first (voice models often output uppercase English)
        val lowered = lowercaseLatinCharacters(cleaned)

        // Apply OpenCC conversion (only affects CJK characters)
        val processed = convertToTraditional(lowered)

        // Then convert spoken punctuation to symbols
        return convertPunctuation(processed)
    }

    /**
     * Recognize a single VAD speech segment using the current Sherpa-ONNX
     * recognizer. Returns empty string if no recognizer is active.
     *
     * Snapshots the recognizer reference under the lock so we don't crash
     * if the idle releaser nulls it out mid-call.
     */
    private fun recognizeSegment(samples: FloatArray): String {
        val rec = synchronized(recognizerLock) { recognizer } ?: return ""
        markActivity()
        val stream = rec.createStream()
        stream.acceptWaveform(samples, SAMPLE_RATE)
        rec.decode(stream)
        val result = rec.getResult(stream)
        stream.release()
        markActivity()
        return result.text.trim()
    }

    /**
     * Strip Whisper special tokens from text.
     * Whisper outputs tokens like <|transcribe|>, <|notimestamps|>, <|zh|>, <|en|>, etc.
     */
    private fun stripWhisperTokens(text: String): String {
        // Remove all <|...|> tokens
        return text.replace(Regex("<\\|[^|]+\\|>"), "").trim()
    }

    /**
     * Remove repetition patterns from Whisper output.
     * Whisper sometimes produces repeated text like "hellohellohello" or "早晨早晨早晨".
     * This function detects and removes such repetitions.
     *
     * Also handles the case where Whisper outputs the entire text twice (e.g., "hello worldhello world").
     */
    private fun removeRepetition(text: String): String {
        if (text.length < 2) return text

        // First check: is the text exactly the same thing repeated twice?
        // This handles cases like "hello worldhello world" or "早晨早晨"
        val halfLen = text.length / 2
        if (text.length >= 2 && text.length % 2 == 0) {
            val firstHalf = text.substring(0, halfLen)
            val secondHalf = text.substring(halfLen)
            if (firstHalf == secondHalf) {
                if (BuildConfig.DEBUG) {
                    Log.w(TAG, "Detected 2x duplication")
                }
                return firstHalf
            }
        }

        // Check for 4x duplication (common Whisper pattern)
        if (text.length >= 4 && text.length % 4 == 0) {
            val quarterLen = text.length / 4
            val quarter = text.substring(0, quarterLen)
            if (text == quarter.repeat(4)) {
                if (BuildConfig.DEBUG) {
                    Log.w(TAG, "Detected 4x duplication")
                }
                return quarter
            }
        }

        // Try to find repeating patterns of various lengths
        for (patternLen in 1..minOf(text.length / 2, 20)) {
            val pattern = text.substring(0, patternLen)
            var isRepeating = true
            var repeatCount = 1

            // Check if the entire string is just this pattern repeated
            for (i in patternLen until text.length step patternLen) {
                val endIdx = minOf(i + patternLen, text.length)
                val segment = text.substring(i, endIdx)
                if (segment == pattern || (endIdx == text.length && pattern.startsWith(segment))) {
                    repeatCount++
                } else {
                    isRepeating = false
                    break
                }
            }

            // If we found a repeating pattern (2 or more times), return just one instance
            if (isRepeating && repeatCount >= 2) {
                if (BuildConfig.DEBUG) {
                    Log.w(TAG, "Detected repetition pattern repeated $repeatCount times")
                }
                return pattern
            }
        }

        return text
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
     * Process audio using VAD for endpoint detection and the selected model for transcription.
     */
    private fun processAudioWithVad() {
        val vadInstance = vad ?: return

        val bufferSize = 512  // Process in small chunks for responsive VAD
        val buffer = ShortArray(bufferSize)

        try {
            while (isRecording) {
                val ret = audioRecord?.read(buffer, 0, buffer.size) ?: -1

                if (ret > 0) {
                    val samples = FloatArray(ret) { buffer[it] / 32768.0f }
                    vadInstance.acceptWaveform(samples)

                    while (!vadInstance.empty()) {
                        val segment = vadInstance.front()
                        vadInstance.pop()

                        val segmentSamples = segment.samples
                        if (BuildConfig.DEBUG) {
                            Log.d(TAG, "VAD segment: ${segmentSamples.size} samples (${segmentSamples.size / SAMPLE_RATE.toFloat()}s)")
                        }
                        if (segmentSamples.isNotEmpty()) {
                            handleSegment(segmentSamples)
                        }
                    }
                }
            }

            // Flush remaining audio in VAD buffer on stop
            vadInstance.flush()
            while (!vadInstance.empty()) {
                val segment = vadInstance.front()
                vadInstance.pop()
                if (segment.samples.isNotEmpty()) {
                    handleSegment(segment.samples)
                }
            }

        } catch (e: Exception) {
            if (BuildConfig.DEBUG) {
                Log.e(TAG, "Error processing audio: ${e.message}", e)
            }
            onErrorCallback?.invoke("Error processing audio: ${e.message}")
        }
    }

    /**
     * Transcribe one VAD speech segment and append the result to accumulatedText.
     */
    private fun handleSegment(segmentSamples: FloatArray) {
        var text = recognizeSegment(segmentSamples)
        if (text.isEmpty()) return

        text = processRecognizedText(text)
        if (text.isEmpty()) return

        // Skip VAD duplicate segments
        if (text == lastSegmentText) {
            if (BuildConfig.DEBUG) Log.w(TAG, "Skipping duplicate segment")
            return
        }
        lastSegmentText = text

        if (accumulatedText.isNotEmpty()) accumulatedText.append(" ")
        accumulatedText.append(text)

        lastRecognizedText = accumulatedText.toString()
        onResultCallback?.invoke(lastRecognizedText, true)
    }
}
