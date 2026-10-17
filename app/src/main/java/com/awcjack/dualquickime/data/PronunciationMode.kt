package com.awcjack.dualquickime.data

enum class PronunciationMode(val preferenceValue: String, val assetName: String?) {
    NONE("none", null),
    CANTONESE("cantonese", "jyutping.tsv"),
    MANDARIN("mandarin", "pinyin.tsv");

    companion object {
        fun fromPreference(value: String?): PronunciationMode =
            entries.firstOrNull { it.preferenceValue == value } ?: NONE
    }
}
