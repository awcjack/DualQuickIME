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
     * Whisper Medium Yue - Fine-tuned Whisper Medium model on WenetSpeech-Yue.
     * Higher accuracy for Cantonese (5.05% MER on WSYue-eval).
     * Larger model but more accurate than whisper-small-cantonese.
     * Based on ASLP-lab/WSYue-ASR from HuggingFace.
     */
    WHISPER_MEDIUM_YUE(
        id = "whisper_medium_yue",
        displayNameResId = com.awcjack.dualquickime.R.string.voice_model_whisper_medium_yue_name,
        descriptionResId = com.awcjack.dualquickime.R.string.voice_model_whisper_medium_yue_desc,
        modelDir = "sherpa-onnx-whisper-medium-yue",
        sizeBytes = 1_530_000_000L,  // ~769M parameters = ~1.5GB model files
        sizeDisplayMB = 1530,
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
