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

                    // Only notify if text changed
                    if (text != lastText && text.isNotEmpty()) {
                        lastText = text
                        onResultCallback?.invoke(text, isEndpoint)
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
