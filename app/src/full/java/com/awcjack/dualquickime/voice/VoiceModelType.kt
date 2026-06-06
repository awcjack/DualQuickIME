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
     * Whisper Cantonese - Fine-tuned Whisper model optimized for Cantonese.
     * Best accuracy for Cantonese-only input (7.93% CER).
     * Based on alvanlii/whisper-small-cantonese from HuggingFace.
     */
    WHISPER_CANTONESE(
        id = "whisper_cantonese",
        displayNameResId = com.awcjack.dualquickime.R.string.voice_model_whisper_cantonese_name,
        descriptionResId = com.awcjack.dualquickime.R.string.voice_model_whisper_cantonese_desc,
        modelDir = "sherpa-onnx-whisper-small-cantonese",
        sizeBytes = 395_000_000L,  // ~107 MB encoder + ~287 MB decoder + ~1 MB tokens
        sizeDisplayMB = 395,
        isAvailable = true
    ),

    /**
     * U2pp-Conformer-Yue - WeNet Conformer model trained on WenetSpeech-Yue.
     * State-of-the-art Cantonese ASR (5.05% MER) with smallest model size.
     * 130M parameters - best accuracy-to-size ratio for Cantonese.
     * Based on ASLP-lab/WSYue-ASR from HuggingFace.
     */
    U2PP_CONFORMER_YUE(
        id = "u2pp_conformer_yue",
        displayNameResId = com.awcjack.dualquickime.R.string.voice_model_u2pp_conformer_yue_name,
        descriptionResId = com.awcjack.dualquickime.R.string.voice_model_u2pp_conformer_yue_desc,
        modelDir = "sherpa-onnx-wenetspeech-yue-u2pp-conformer-ctc-zh-en-cantonese-int8-2025-09-10",
        sizeBytes = 260_000_000L,  // ~130M parameters = ~260MB model files (int8)
        sizeDisplayMB = 260,
        isAvailable = true
    ),

    /**
     * Qwen3-ASR - Alibaba's LLM-based ASR model, best Cantonese accuracy.
     * WER on Cantonese: 4.12% (vs Whisper 7.93% CER, U2pp 5.05% MER).
     * Native Cantonese-English code-switching support.
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
    );

    companion object {
        /**
         * Default model to use when none is selected.
         */
        val DEFAULT = SENSE_VOICE

        /**
         * Find a model type by its ID.
         */
        fun fromId(id: String): VoiceModelType {
            return entries.find { it.id == id } ?: DEFAULT
        }
    }
}
