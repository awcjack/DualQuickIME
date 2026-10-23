package com.awcjack.dualquickime.voice

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.SystemClock
import android.os.Debug
import android.util.Log
import com.awcjack.dualquickime.BuildConfig
import com.k2fsa.sherpa.onnx.*
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Bound Service that owns the Qwen3-ASR Sherpa-ONNX recognizer and runs in
 * its own `:voice` process (declared in the manifest). The IME process binds
 * to this service via [IVoiceRecognizer] AIDL and pipes audio segments
 * across the Binder boundary for transcription.
 *
 * Why a separate process: a loaded Qwen3-ASR-0.6B INT8 recognizer pins
 * ~700 MB of weights plus activation and KV-cache buffers in resident
 * memory; the 1.7B model needs substantially more. Keeping these weights
 * in the IME increases memory pressure and the chance of Android
 * killing it; an IME holding that model could be killed
 * mid-utterance, taking the keyboard with it. With the model in `:voice`,
 * LMK can kill the model process independently while the IME keeps
 * running — the user sees a slow reload, not a vanished keyboard.
 *
 * The service is purely a model host. VAD, audio capture, OpenCC and
 * text post-processing all stay in the IME process where they belong.
 */
class VoiceService : Service() {

    companion object {
        private const val TAG = "VoiceService"
        private const val SAMPLE_RATE = 16000
    }

    // Single Sherpa-ONNX recognizer for the lifetime of one initialize() call.
    // Guarded by [recognizerLock] — initialize/release/transcribe can race.
    private var recognizer: OfflineRecognizer? = null
    private var loadedModelDir: String? = null
    private val recognizerLock = Any()

    private val binder = object : IVoiceRecognizer.Stub() {
        override fun initialize(modelDir: String, numThreads: Int, maxNewTokens: Int): Boolean {
            synchronized(recognizerLock) {
                if (recognizer != null && loadedModelDir == modelDir) {
                    if (BuildConfig.DEBUG) Log.i(TAG, "initialize() called but recognizer already loaded")
                    return true
                }
                // A new manager can bind before the old service is destroyed.
                // Switching between 0.6B and 1.7B must replace the old weights.
                releaseModel()
                val loadStarted = SystemClock.elapsedRealtime()
                return try {
                    val qwen3Config = OfflineQwen3AsrModelConfig(
                        convFrontend = "$modelDir/conv_frontend.onnx",
                        encoder = "$modelDir/encoder.int8.onnx",
                        decoder = "$modelDir/decoder.int8.onnx",
                        tokenizer = "$modelDir/tokenizer",
                        maxNewTokens = maxNewTokens
                    )
                    val modelConfig = OfflineModelConfig(
                        qwen3Asr = qwen3Config,
                        tokens = "",
                        numThreads = numThreads,
                        debug = false
                    )
                    val config = OfflineRecognizerConfig(
                        modelConfig = modelConfig,
                        decodingMethod = "greedy_search"
                    )
                    // Use the file-based constructor: Sherpa 1.13.8 opens the
                    // ONNX paths directly, avoiding temporary full-file copies.
                    // Do not add a dummy decode to the startup path.
                    recognizer = OfflineRecognizer(config = config)
                    loadedModelDir = modelDir
                    if (BuildConfig.DEBUG) {
                        Log.i(TAG, "Qwen3-ASR loaded in ${SystemClock.elapsedRealtime() - loadStarted}ms (numThreads=$numThreads, maxNewTokens=$maxNewTokens, PSS=${Debug.getPss() / 1024}MiB)")
                    }
                    true
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to initialize Qwen3-ASR in :voice process", e)
                    false
                }
            }
        }

        override fun transcribe(pcm16le: ByteArray): String = synchronized(recognizerLock) {
            // Hold recognizerLock for the ENTIRE decode, not just the field read.
            // createStream/decode/getResult dereference the native recognizer;
            // a concurrent releaseModel() (idle releaser or onDestroy, dispatched
            // on a different Binder thread) that freed it mid-decode would be a
            // native use-after-free (SIGSEGV). With the full body locked,
            // releaseModel() blocks until the in-flight decode completes.
            val rec = recognizer ?: return@synchronized ""
            try {
                // Unpack little-endian int16 bytes back to the float range
                // Sherpa-ONNX wants. The IME packed these from its existing
                // int16 AudioRecord samples, so the round-trip is lossless.
                val sampleCount = pcm16le.size / 2
                val shortBuf = ByteBuffer.wrap(pcm16le)
                    .order(ByteOrder.LITTLE_ENDIAN)
                    .asShortBuffer()
                val floats = FloatArray(sampleCount) { shortBuf.get(it) / 32768.0f }
                val stream = rec.createStream()
                stream.acceptWaveform(floats, SAMPLE_RATE)
                rec.decode(stream)
                val text = rec.getResult(stream).text.trim()
                stream.release()
                if (BuildConfig.DEBUG) Log.i(TAG, "Qwen3-ASR after transcription: PSS=${Debug.getPss() / 1024}MiB")
                text
            } catch (e: Exception) {
                Log.e(TAG, "transcribe() failed", e)
                ""
            }
        }

        override fun releaseModel() {
            synchronized(recognizerLock) {
                try {
                    recognizer?.release()
                } catch (e: Exception) {
                    if (BuildConfig.DEBUG) Log.w(TAG, "recognizer.release() threw: ${e.message}")
                }
                recognizer = null
                loadedModelDir = null
                if (BuildConfig.DEBUG) Log.i(TAG, "Qwen3-ASR released; :voice process can now drop back to a few MB")
            }
        }

        override fun isLoaded(): Boolean {
            return synchronized(recognizerLock) { recognizer != null }
        }
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        // Belt-and-braces: if the process is killed (LMK or otherwise), the
        // OS reclaims memory anyway. This is for the clean-shutdown path.
        binder.releaseModel()
        super.onDestroy()
    }
}
