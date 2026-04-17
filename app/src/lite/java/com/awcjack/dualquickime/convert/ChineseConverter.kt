package com.awcjack.dualquickime.convert

/**
 * Stub for the lite flavor: OpenCC is not bundled to keep the APK small,
 * so the conversion feature is disabled. Callers should gate UI on
 * [isAvailable].
 */
object ChineseConverter {
    fun isAvailable(): Boolean = false
    fun toTraditional(text: String): String = text
    fun toSimplified(text: String): String = text
}
