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
    val sizeDisplayMB: Int
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
        sizeDisplayMB = 228
    ),

    /**
     * Whisper Cantonese - Fine-tuned model optimized for Cantonese.
     * Achieves 7.93% CER on Common Voice zh-HK dataset.
     * Best for primarily Cantonese input with some English code-switching.
     * Converted from alvanlii/whisper-small-cantonese via CI workflow.
     */
    WHISPER_CANTONESE(
        id = "whisper_cantonese",
        displayNameResId = com.awcjack.dualquickime.R.string.voice_model_whisper_cantonese_name,
        descriptionResId = com.awcjack.dualquickime.R.string.voice_model_whisper_cantonese_desc,
        modelDir = "sherpa-onnx-whisper-small-cantonese",
        sizeBytes = 288_000_000L,
        sizeDisplayMB = 274
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
