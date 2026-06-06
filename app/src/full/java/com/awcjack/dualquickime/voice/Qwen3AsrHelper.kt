package com.awcjack.dualquickime.voice

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.util.Log
import com.awcjack.dualquickime.BuildConfig
import org.json.JSONObject
import java.io.File
import java.nio.FloatBuffer
import java.nio.LongBuffer
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Handles Qwen3-ASR inference using ONNX Runtime for Android.
 *
 * Architecture: AuT audio encoder + Qwen3 LLM decoder.
 * Mel spectrogram extraction is implemented in Kotlin to avoid ONNX export complexity.
 * Supports Cantonese, Mandarin, English and Cantonese-English code-switching.
 *
 * Model files (in modelDir):
 *   encoder.onnx     - Audio encoder (mel features → audio embeddings)
 *   decoder.onnx     - LLM decoder with KV-cache (audio embeddings + tokens → logits)
 *   vocab.json       - Token ID → string map (tiktoken byte-level BPE)
 *   config.json      - Model configuration (bos/eos tokens, mel params, n_layers, etc.)
 */
class Qwen3AsrHelper(
    private val context: Context,
    private val modelDir: String
) {

    companion object {
        private const val TAG = "Qwen3AsrHelper"

        private const val ENCODER_FILE = "encoder.onnx"
        private const val DECODER_FILE = "decoder.onnx"
        private const val VOCAB_FILE = "vocab.json"
        private const val CONFIG_FILE = "config.json"

        // Defaults; overridden by config.json at runtime
        private const val DEFAULT_N_MELS = 128
        private const val DEFAULT_N_FFT = 400
        private const val DEFAULT_HOP_LENGTH = 160
        private const val DEFAULT_SAMPLE_RATE = 16000
        private const val DEFAULT_N_LAYERS = 28
        private const val DEFAULT_N_HEADS = 16
        private const val DEFAULT_HEAD_DIM = 64
        private const val DEFAULT_HIDDEN_SIZE = 1024
        private const val DEFAULT_BOS_TOKEN_ID = 151644  // Qwen3 <|im_start|>
        private const val DEFAULT_EOS_TOKEN_ID = 151645  // Qwen3 <|im_end|>
        private const val DEFAULT_MAX_NEW_TOKENS = 448
        private const val DEFAULT_MAX_AUDIO_SECONDS = 30

        // Tiktoken byte-to-unicode offset (bytes 0-32 and 127-160 are remapped to 256+)
        private val BYTES_TO_UNICODE: Map<Int, Int> by lazy { buildBytesToUnicode() }
        private val UNICODE_TO_BYTES: Map<Char, Byte> by lazy {
            BYTES_TO_UNICODE.entries.associate { (b, u) -> u.toChar() to b.toByte() }
        }

        private fun buildBytesToUnicode(): Map<Int, Int> {
            val visible = (33..126).toList() + (161..255).toList()
            val result = mutableMapOf<Int, Int>()
            var n = 0
            for (b in 0..255) {
                if (b in visible) {
                    result[b] = b
                } else {
                    result[b] = 256 + n
                    n++
                }
            }
            return result
        }
    }

    private var ortEnv: OrtEnvironment? = null
    private var encoderSession: OrtSession? = null
    private var decoderSession: OrtSession? = null

    private var vocab: Map<Int, String> = emptyMap()
    private var bosTokenId: Int = DEFAULT_BOS_TOKEN_ID
    private var eosTokenId: Int = DEFAULT_EOS_TOKEN_ID
    private var maxNewTokens: Int = DEFAULT_MAX_NEW_TOKENS
    private var nLayers: Int = DEFAULT_N_LAYERS
    private var nHeads: Int = DEFAULT_N_HEADS
    private var headDim: Int = DEFAULT_HEAD_DIM
    private var hiddenSize: Int = DEFAULT_HIDDEN_SIZE
    private var nMels: Int = DEFAULT_N_MELS
    private var nFft: Int = DEFAULT_N_FFT
    private var hopLength: Int = DEFAULT_HOP_LENGTH
    private var sampleRate: Int = DEFAULT_SAMPLE_RATE
    private var maxAudioSeconds: Int = DEFAULT_MAX_AUDIO_SECONDS

    // Pre-computed mel filterbank [n_mels][n_fft/2 + 1]
    private lateinit var melFilterbank: Array<FloatArray>

    // Pre-computed Hann window [n_fft]
    private lateinit var hannWindow: FloatArray

    /**
     * Initialize ONNX sessions and load configuration.
     * Must be called on a background thread.
     */
    fun initialize(): Boolean {
        return try {
            ortEnv = OrtEnvironment.getEnvironment()
            val env = ortEnv!!

            val sessionOptions = OrtSession.SessionOptions().apply {
                setIntraOpNumThreads(2)
                setOptimizationLevel(OrtSession.SessionOptions.OptLevel.BASIC_OPT)
            }

            encoderSession = env.createSession("$modelDir/$ENCODER_FILE", sessionOptions)
            decoderSession = env.createSession("$modelDir/$DECODER_FILE", sessionOptions)

            loadConfig()
            loadVocabulary()

            // Pre-compute Hann window and mel filterbank after loading config
            hannWindow = FloatArray(nFft) { i ->
                (0.5 * (1.0 - cos(2.0 * PI * i / (nFft - 1)))).toFloat()
            }
            melFilterbank = computeMelFilterbank()

            if (BuildConfig.DEBUG) {
                Log.i(TAG, "Initialized: bos=$bosTokenId eos=$eosTokenId layers=$nLayers heads=$nHeads headDim=$headDim")
            }
            true
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) {
                Log.e(TAG, "Initialization failed: ${e.message}", e)
            }
            release()
            false
        }
    }

    /**
     * Transcribe raw audio samples to text.
     * Runs encoder then greedy-decodes with the LLM decoder.
     * Must be called on a background thread.
     */
    fun transcribe(samples: FloatArray): String {
        if (samples.isEmpty()) return ""

        try {
            val maxSamples = maxAudioSeconds * sampleRate
            val clipped = if (samples.size > maxSamples) samples.copyOf(maxSamples) else samples

            val (melData, nFrames) = extractMelFeatures(clipped)
            val (audioEmbeds, audioSeqLen) = runEncoder(melData, nFrames)
            val tokenIds = decode(audioEmbeds, audioSeqLen)
            return detokenize(tokenIds)
        } catch (e: OutOfMemoryError) {
            throw RuntimeException("Out of memory during Qwen3-ASR inference. Device may not have enough RAM for this model.", e)
        }
    }

    /** Release all ONNX Runtime resources. */
    fun release() {
        encoderSession?.close()
        encoderSession = null
        decoderSession?.close()
        decoderSession = null
        ortEnv?.close()
        ortEnv = null
    }

    // -------------------------------------------------------------------------
    // Mel spectrogram extraction (Kotlin, no ONNX needed for preprocessing)
    // -------------------------------------------------------------------------

    /**
     * Extract log-mel spectrogram from raw audio samples.
     * Returns flat [n_mels * n_frames] array and the frame count.
     */
    private fun extractMelFeatures(samples: FloatArray): Pair<FloatArray, Int> {
        val fftSize = nextPow2(nFft)  // e.g., 400 → 512
        val halfBins = nFft / 2       // 200 positive-frequency bins

        // Center-pad the signal by nFft/2 on each side (as Whisper does)
        val padded = FloatArray(samples.size + nFft)
        samples.copyInto(padded, nFft / 2)

        val numFrames = (padded.size - nFft) / hopLength + 1

        val re = FloatArray(fftSize)
        val im = FloatArray(fftSize)
        val magnitudes = Array(halfBins + 1) { FloatArray(numFrames) }

        for (frame in 0 until numFrames) {
            val start = frame * hopLength
            // Apply Hann window and zero-pad to fftSize
            for (i in 0 until nFft) re[i] = padded[start + i] * hannWindow[i]
            re.fill(0f, nFft, fftSize)
            im.fill(0f)

            fft(re, im, fftSize)

            for (bin in 0..halfBins) {
                magnitudes[bin][frame] = sqrt(re[bin] * re[bin] + im[bin] * im[bin])
            }
        }

        // Apply mel filterbank: [n_mels][numFrames]
        val melSpec = Array(nMels) { m ->
            FloatArray(numFrames) { f ->
                var s = 0f
                for (bin in 0..halfBins) s += melFilterbank[m][bin] * magnitudes[bin][f]
                s
            }
        }

        // Log-mel normalization (Whisper-style: clamp, log10, normalize to ~[-1, 1])
        val LOG_FLOOR = 1e-10f
        val logMel = Array(nMels) { m -> FloatArray(numFrames) { f -> log10(max(melSpec[m][f], LOG_FLOOR)) } }

        var maxVal = Float.NEGATIVE_INFINITY
        for (m in 0 until nMels) for (f in 0 until numFrames) if (logMel[m][f] > maxVal) maxVal = logMel[m][f]

        val flat = FloatArray(nMels * numFrames)
        for (m in 0 until nMels) {
            for (f in 0 until numFrames) {
                flat[m * numFrames + f] = (max(logMel[m][f], maxVal - 8.0f) + 4.0f) / 4.0f
            }
        }

        return Pair(flat, numFrames)
    }

    /** Compute the mel filterbank matrix [n_mels][n_fft/2 + 1] (HTK-style mel scale). */
    private fun computeMelFilterbank(): Array<FloatArray> {
        val fMin = 0.0
        val fMax = sampleRate / 2.0
        val nBins = nFft / 2 + 1

        fun hzToMel(hz: Double) = 2595.0 * log10(1.0 + hz / 700.0)
        fun melToHz(mel: Double) = 700.0 * (10.0.pow(mel / 2595.0) - 1.0)

        val melMin = hzToMel(fMin)
        val melMax = hzToMel(fMax)

        // n_mels + 2 center frequencies (including edge bins)
        val freqPoints = DoubleArray(nMels + 2) { i ->
            melToHz(melMin + (melMax - melMin) * i / (nMels + 1))
        }
        // Map center frequencies to FFT bin indices
        val binPoints = IntArray(nMels + 2) { i ->
            floor((nFft + 1) * freqPoints[i] / sampleRate).toInt().coerceIn(0, nBins - 1)
        }

        return Array(nMels) { m ->
            FloatArray(nBins) { k ->
                when {
                    k in binPoints[m] until binPoints[m + 1] ->
                        (k - binPoints[m]).toFloat() / (binPoints[m + 1] - binPoints[m]).coerceAtLeast(1)
                    k in binPoints[m + 1] until binPoints[m + 2] ->
                        (binPoints[m + 2] - k).toFloat() / (binPoints[m + 2] - binPoints[m + 1]).coerceAtLeast(1)
                    else -> 0f
                }
            }
        }
    }

    /** Cooley-Tukey radix-2 FFT, in-place. Size must be a power of 2. */
    private fun fft(re: FloatArray, im: FloatArray, n: Int) {
        // Bit-reversal permutation
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) { j = j xor bit; bit = bit shr 1 }
            j = j xor bit
            if (i < j) {
                var t = re[i]; re[i] = re[j]; re[j] = t
                t = im[i]; im[i] = im[j]; im[j] = t
            }
        }
        // FFT butterfly
        var len = 2
        while (len <= n) {
            val half = len / 2
            val ang = -2.0 * PI / len
            val wRe = cos(ang).toFloat()
            val wIm = sin(ang).toFloat()
            var i = 0
            while (i < n) {
                var curRe = 1f; var curIm = 0f
                for (k in 0 until half) {
                    val uRe = re[i + k]; val uIm = im[i + k]
                    val vRe = re[i + k + half] * curRe - im[i + k + half] * curIm
                    val vIm = re[i + k + half] * curIm + im[i + k + half] * curRe
                    re[i + k] = uRe + vRe; im[i + k] = uIm + vIm
                    re[i + k + half] = uRe - vRe; im[i + k + half] = uIm - vIm
                    val nextRe = curRe * wRe - curIm * wIm
                    curIm = curRe * wIm + curIm * wRe; curRe = nextRe
                }
                i += len
            }
            len = len shl 1
        }
    }

    private fun nextPow2(n: Int): Int {
        var p = 1; while (p < n) p = p shl 1; return p
    }

    // -------------------------------------------------------------------------
    // ONNX Runtime inference
    // -------------------------------------------------------------------------

    /**
     * Run the audio encoder.
     * Input: flat mel features [n_mels * n_frames]
     * Output: flat audio embeddings [audio_seq_len * hidden_size], and audio_seq_len
     */
    private fun runEncoder(melData: FloatArray, nFrames: Int): Pair<FloatArray, Int> {
        val env = ortEnv!!
        val session = encoderSession!!

        val inputShape = longArrayOf(1, nMels.toLong(), nFrames.toLong())
        val inputTensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(melData), inputShape)

        val inputs = mapOf("mel_features" to inputTensor)
        val result = session.run(inputs)

        val outputTensor = result["audio_embeds"]?.get() as? OnnxTensor
            ?: throw RuntimeException("Encoder did not produce 'audio_embeds' output")

        val outputShape = outputTensor.info.shape  // [1, audio_seq_len, hidden_size]
        val audioSeqLen = outputShape[1].toInt()
        val embedData = outputTensor.floatData

        outputTensor.close()
        result.close()
        inputTensor.close()

        return Pair(embedData, audioSeqLen)
    }

    /**
     * Greedy decode: run the LLM decoder autoregressively until EOS or maxNewTokens.
     * First step: input_ids=[bos], audio_embeds=real values, past_kv empty.
     * Subsequent steps: input_ids=[next_token], audio_embeds=empty, past_kv=accumulated.
     */
    private fun decode(audioEmbeds: FloatArray, audioSeqLen: Int): List<Int> {
        val env = ortEnv!!
        val session = decoderSession!!

        // Build initial empty KV cache: each layer has key/value of shape [1, n_heads, 0, head_dim]
        val emptyKvShape = longArrayOf(1, nHeads.toLong(), 0, headDim.toLong())
        var pastKvKeys = Array(nLayers) { FloatArray(0) }
        var pastKvValues = Array(nLayers) { FloatArray(0) }
        var pastSeqLen = 0

        val tokenIds = mutableListOf<Int>()
        var currentAudioEmbeds = audioEmbeds
        var currentAudioSeqLen = audioSeqLen
        var inputIds = longArrayOf(bosTokenId.toLong())

        for (step in 0..maxNewTokens) {
            val inputs = mutableMapOf<String, OnnxTensor>()
            try {
                // input_ids: [1, seq_len]
                inputs["input_ids"] = OnnxTensor.createTensor(
                    env, LongBuffer.wrap(inputIds), longArrayOf(1, inputIds.size.toLong())
                )

                // audio_embeds: [1, audio_seq_len, hidden_size] (empty after first step)
                inputs["audio_embeds"] = OnnxTensor.createTensor(
                    env,
                    FloatBuffer.wrap(currentAudioEmbeds),
                    longArrayOf(1, currentAudioSeqLen.toLong(), hiddenSize.toLong())
                )

                // Past KV cache tensors
                val kvShape = longArrayOf(1, nHeads.toLong(), pastSeqLen.toLong(), headDim.toLong())
                for (i in 0 until nLayers) {
                    inputs["past_key_values.$i.key"] = OnnxTensor.createTensor(
                        env, FloatBuffer.wrap(pastKvKeys[i]), kvShape
                    )
                    inputs["past_key_values.$i.value"] = OnnxTensor.createTensor(
                        env, FloatBuffer.wrap(pastKvValues[i]), kvShape
                    )
                }

                val result = session.run(inputs)

                // Extract logits [1, seq_len, vocab_size] — take the last position
                val logitsTensor = result["logits"]?.get() as? OnnxTensor
                    ?: throw RuntimeException("Decoder did not produce 'logits'")
                val logitsFlat = logitsTensor.floatData
                val vocabSize = logitsTensor.info.shape[2].toInt()
                val lastPosOffset = (logitsTensor.info.shape[1].toInt() - 1) * vocabSize
                val nextToken = argmax(logitsFlat, lastPosOffset, vocabSize)
                logitsTensor.close()

                // Extract updated KV cache
                val newPastSeqLen = pastSeqLen + inputIds.size
                val newKvKeys = Array(nLayers) { i ->
                    val t = result["present_key_values.$i.key"]?.get() as? OnnxTensor
                    val d = t?.floatData ?: FloatArray(0)
                    t?.close()
                    d
                }
                val newKvValues = Array(nLayers) { i ->
                    val t = result["present_key_values.$i.value"]?.get() as? OnnxTensor
                    val d = t?.floatData ?: FloatArray(0)
                    t?.close()
                    d
                }
                result.close()

                pastKvKeys = newKvKeys
                pastKvValues = newKvValues
                pastSeqLen = newPastSeqLen

                if (nextToken == eosTokenId) break
                tokenIds.add(nextToken)

                // After the first step, stop sending audio_embeds to the decoder
                currentAudioEmbeds = FloatArray(0)
                currentAudioSeqLen = 0
                inputIds = longArrayOf(nextToken.toLong())

            } finally {
                inputs.values.forEach { it.close() }
            }
        }

        return tokenIds
    }

    /** Return the index of the maximum value in arr[offset..offset+length). */
    private fun argmax(arr: FloatArray, offset: Int, length: Int): Int {
        var bestIdx = 0
        var bestVal = Float.NEGATIVE_INFINITY
        for (i in 0 until length) {
            if (arr[offset + i] > bestVal) {
                bestVal = arr[offset + i]
                bestIdx = i
            }
        }
        return bestIdx
    }

    // -------------------------------------------------------------------------
    // Detokenization (tiktoken byte-level BPE)
    // -------------------------------------------------------------------------

    /**
     * Convert a list of token IDs to a UTF-8 string.
     * Handles the tiktoken byte-level encoding where each token string
     * is a sequence of unicode characters that map back to individual bytes.
     */
    private fun detokenize(tokenIds: List<Int>): String {
        val bytes = mutableListOf<Byte>()
        for (id in tokenIds) {
            val token = vocab[id] ?: continue
            // Skip special tokens enclosed in <| ... |>
            if (token.startsWith("<|") && token.endsWith("|>")) continue
            // Each character in the token string maps to one byte via UNICODE_TO_BYTES
            for (ch in token) {
                val b = UNICODE_TO_BYTES[ch]
                if (b != null) bytes.add(b)
            }
        }
        return try {
            String(bytes.toByteArray(), Charsets.UTF_8)
        } catch (e: Exception) {
            String(bytes.toByteArray(), Charsets.ISO_8859_1)
        }
    }

    // -------------------------------------------------------------------------
    // Config & vocabulary loading
    // -------------------------------------------------------------------------

    private fun loadConfig() {
        val configFile = File(modelDir, CONFIG_FILE)
        if (!configFile.exists()) return

        val obj = JSONObject(configFile.readText())
        bosTokenId = obj.optInt("bos_token_id", DEFAULT_BOS_TOKEN_ID)
        eosTokenId = obj.optInt("eos_token_id", DEFAULT_EOS_TOKEN_ID)
        maxNewTokens = obj.optInt("max_new_tokens", DEFAULT_MAX_NEW_TOKENS)
        nLayers = obj.optInt("num_hidden_layers", DEFAULT_N_LAYERS)
        nHeads = obj.optInt("num_key_value_heads", DEFAULT_N_HEADS)
        headDim = obj.optInt("head_dim", DEFAULT_HEAD_DIM)
        hiddenSize = obj.optInt("hidden_size", DEFAULT_HIDDEN_SIZE)
        nMels = obj.optInt("n_mels", DEFAULT_N_MELS)
        nFft = obj.optInt("n_fft", DEFAULT_N_FFT)
        hopLength = obj.optInt("hop_length", DEFAULT_HOP_LENGTH)
        sampleRate = obj.optInt("sample_rate", DEFAULT_SAMPLE_RATE)
        maxAudioSeconds = obj.optInt("max_audio_seconds", DEFAULT_MAX_AUDIO_SECONDS)
    }

    private fun loadVocabulary() {
        val vocabFile = File(modelDir, VOCAB_FILE)
        if (!vocabFile.exists()) {
            if (BuildConfig.DEBUG) Log.w(TAG, "vocab.json not found, detokenization will be empty")
            return
        }

        val map = mutableMapOf<Int, String>()
        val obj = JSONObject(vocabFile.readText())
        val iter = obj.keys()
        while (iter.hasNext()) {
            val key = iter.next()
            map[key.toInt()] = obj.getString(key)
        }
        vocab = map

        if (BuildConfig.DEBUG) Log.i(TAG, "Loaded vocabulary: ${vocab.size} tokens")
    }
}
