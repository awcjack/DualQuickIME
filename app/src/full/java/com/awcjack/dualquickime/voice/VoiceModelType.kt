package com.awcjack.dualquickime.voice

/**
 * Enum representing available voice recognition models.
 * Users can select their preferred model in settings.
 */
enum class VoiceModelType(
    val id: String,
    val displayNameResId: Int,
    val descriptionResId: Int,
    val modelDir: String,
    val sizeBytes: Long,
    val sizeDisplayMB: Int,
    val isAvailable: Boolean = true  // Whether this model can be used
) {
    /**
     * SenseVoice - Multilingual model supporting auto language detection.
     * Supports: Cantonese, Mandarin, English, Japanese, Korean
     * Good for mixed language input and general use.
     */
    SENSE_VOICE(
        id = "sensevoice",
        displayNameResId = com.awcjack.dualquickime.R.string.voice_model_sensevoice_name,
        descriptionResId = com.awcjack.dualquickime.R.string.voice_model_sensevoice_desc,
        modelDir = "sherpa-onnx-sense-voice-zh-en-ja-ko-yue-int8-2025-09-09",
        sizeBytes = 227_000_000L,
        sizeDisplayMB = 228,
        isAvailable = true
    ),

    /**
     * Retired. Keep its ID and directory so existing downloads can be deleted.
     */
    WHISPER_CANTONESE(
        id = "whisper_cantonese",
        displayNameResId = com.awcjack.dualquickime.R.string.voice_model_whisper_cantonese_name,
        descriptionResId = com.awcjack.dualquickime.R.string.voice_model_whisper_cantonese_desc,
        modelDir = "sherpa-onnx-whisper-small-cantonese",
        sizeBytes = 395_000_000L,  // ~107 MB encoder + ~287 MB decoder + ~1 MB tokens
        sizeDisplayMB = 395,
        isAvailable = false
    ),

    /**
     * Retired. Keep its ID and directory so existing downloads can be deleted.
     */
    U2PP_CONFORMER_YUE(
        id = "u2pp_conformer_yue",
        displayNameResId = com.awcjack.dualquickime.R.string.voice_model_u2pp_conformer_yue_name,
        descriptionResId = com.awcjack.dualquickime.R.string.voice_model_u2pp_conformer_yue_desc,
        modelDir = "sherpa-onnx-wenetspeech-yue-u2pp-conformer-ctc-zh-en-cantonese-int8-2025-09-10",
        sizeBytes = 260_000_000L,  // ~130M parameters = ~260MB model files (int8)
        sizeDisplayMB = 260,
        isAvailable = false
    ),

    /**
     * Qwen3-ASR 0.6B - Alibaba's multilingual LLM-based ASR model.
     * Uses Sherpa-ONNX's pre-built Qwen3-ASR-0.6B INT8 (PR #3409, 2026-03-25):
     * conv_frontend + AuT audio encoder + Qwen3 28-layer LLM decoder w/ KV-cache.
     * Note: ~942 MB download; decoder is autoregressive so latency is
     * significantly higher than CTC models like U2pp-Conformer.
     */
    QWEN3_ASR(
        id = "qwen3_asr",
        displayNameResId = com.awcjack.dualquickime.R.string.voice_model_qwen3_asr_name,
        descriptionResId = com.awcjack.dualquickime.R.string.voice_model_qwen3_asr_desc,
        modelDir = "sherpa-onnx-qwen3-asr-0.6B-int8-2026-03-25",
        sizeBytes = 987_000_000L,  // conv_frontend 42 + encoder 174 + decoder 721 + tokenizer ~4 MB
        sizeDisplayMB = 942,
        isAvailable = true
    ),

    /**
     * Qwen3-ASR 1.7B - larger multilingual model, using a community INT8
     * Sherpa-ONNX export. Higher memory use and latency than the 0.6B model.
     * Shares the same recognizer configuration and out-of-process lifecycle.
     */
    QWEN3_ASR_1_7B(
        id = "qwen3_asr_1_7b",
        displayNameResId = com.awcjack.dualquickime.R.string.voice_model_qwen3_asr_1_7b_name,
        descriptionResId = com.awcjack.dualquickime.R.string.voice_model_qwen3_asr_1_7b_desc,
        modelDir = "sherpa-onnx-qwen3-asr-1.7B-int8-69eb686",
        sizeBytes = 2_404_222_421L,
        sizeDisplayMB = 2293,
        isAvailable = true
    );

    val isQwen3Asr: Boolean
        get() = this == QWEN3_ASR || this == QWEN3_ASR_1_7B

    companion object {
        /**
         * Default model to use when none is selected.
         */
        val DEFAULT = SENSE_VOICE

        /**
         * Find a model type by its ID.
         */
        fun fromId(id: String): VoiceModelType {
            return entries.find { it.id == id && it.isAvailable } ?: DEFAULT
        }
    }
}
