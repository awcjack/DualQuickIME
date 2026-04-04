package com.awcjack.dualquickime.voice

import com.awcjack.dualquickime.R

/**
 * Stub VoiceModelType enum for lite flavor (no voice input support).
 */
enum class VoiceModelType(
    val id: String,
    val displayNameResId: Int,
    val descriptionResId: Int,
    val modelDir: String,
    val sizeBytes: Long,
    val sizeDisplayMB: Int
) {
    SENSE_VOICE(
        id = "sensevoice",
        displayNameResId = R.string.voice_model_sensevoice_name,
        descriptionResId = R.string.voice_model_sensevoice_desc,
        modelDir = "",
        sizeBytes = 0L,
        sizeDisplayMB = 0
    );

    companion object {
        val DEFAULT = SENSE_VOICE

        fun fromId(id: String): VoiceModelType = DEFAULT
    }
}
