package com.awcjack.dualquickime.voice

import com.awcjack.dualquickime.R

/**
 * Stub VoiceModelType enum for lite flavor (no voice input support).
 * Must have the same enum values as the full flavor for compilation compatibility.
 */
enum class VoiceModelType(
    val id: String,
    val displayNameResId: Int,
    val descriptionResId: Int,
    val modelDir: String,
    val sizeBytes: Long,
    val sizeDisplayMB: Int,
    val isAvailable: Boolean = false  // Always false in lite flavor
) {
    SENSE_VOICE(
        id = "sensevoice",
        displayNameResId = R.string.voice_model_sensevoice_name,
        descriptionResId = R.string.voice_model_sensevoice_desc,
        modelDir = "",
        sizeBytes = 0L,
        sizeDisplayMB = 0
    ),
    WHISPER_CANTONESE(
        id = "whisper_cantonese",
        displayNameResId = R.string.voice_model_whisper_cantonese_name,
        descriptionResId = R.string.voice_model_whisper_cantonese_desc,
        modelDir = "",
        sizeBytes = 0L,
        sizeDisplayMB = 0
    ),
    U2PP_CONFORMER_YUE(
        id = "u2pp_conformer_yue",
        displayNameResId = R.string.voice_model_u2pp_conformer_yue_name,
        descriptionResId = R.string.voice_model_u2pp_conformer_yue_desc,
        modelDir = "",
        sizeBytes = 0L,
        sizeDisplayMB = 0
    ),
    QWEN3_ASR(
        id = "qwen3_asr",
        displayNameResId = R.string.voice_model_qwen3_asr_name,
        descriptionResId = R.string.voice_model_qwen3_asr_desc,
        modelDir = "",
        sizeBytes = 0L,
        sizeDisplayMB = 0
    );

    companion object {
        val DEFAULT = SENSE_VOICE

        fun fromId(id: String): VoiceModelType = DEFAULT
    }
}
