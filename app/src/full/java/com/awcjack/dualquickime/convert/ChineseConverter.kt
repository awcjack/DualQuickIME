package com.awcjack.dualquickime.convert

import android.util.Log
import com.awcjack.dualquickime.BuildConfig
import openccjava.OpenCC

/**
 * Converts text between Simplified and Traditional Chinese using OpenCC.
 * Uses Hong Kong Traditional variants (s2hk / hk2s) to match the IME's
 * Cantonese-leaning character data.
 *
 * Converters are created lazily and cached because OpenCC initialization
 * loads dictionary files and is non-trivial. Conversion is split into
 * CJK / non-CJK runs so English text inside a selection is preserved.
 */
object ChineseConverter {

    private const val TAG = "ChineseConverter"

    @Volatile private var s2hk: OpenCC? = null
    @Volatile private var hk2s: OpenCC? = null

    fun isAvailable(): Boolean = true

    /** Convert Simplified Chinese in [text] to Hong Kong Traditional. */
    fun toTraditional(text: String): String {
        if (text.isEmpty()) return text
        val converter = s2hk ?: synchronized(this) {
            s2hk ?: try { OpenCC("s2hk").also { s2hk = it } }
            catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.w(TAG, "Failed to init s2hk: ${e.message}")
                return text
            }
        }
        return convertCjkOnly(text, converter)
    }

    /**
     * Auto-detect direction and convert. We try Traditional→Simplified first;
     * if it changes the text, the input contained Traditional characters, so
     * we return the simplified result. Otherwise we run Simplified→Traditional.
     * If neither changes the text (e.g. all ASCII), we return the input.
     *
     * Mixed-script selections are biased toward Simplified — running hk2s
     * leaves any already-Simplified characters untouched and converts the
     * Traditional ones, producing a uniformly-Simplified result.
     */
    fun convertAuto(text: String): String {
        if (text.isEmpty()) return text
        val asSimplified = toSimplified(text)
        if (asSimplified != text) return asSimplified
        return toTraditional(text)
    }

    /** Convert Hong Kong Traditional Chinese in [text] to Simplified. */
    fun toSimplified(text: String): String {
        if (text.isEmpty()) return text
        val converter = hk2s ?: synchronized(this) {
            hk2s ?: try { OpenCC("hk2s").also { hk2s = it } }
            catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.w(TAG, "Failed to init hk2s: ${e.message}")
                return text
            }
        }
        return convertCjkOnly(text, converter)
    }

    private fun convertCjkOnly(text: String, converter: OpenCC): String {
        return try {
            val result = StringBuilder(text.length)
            val segment = StringBuilder()
            var inCjk = false
            for (ch in text) {
                val cjk = isCjkCharacter(ch)
                if (cjk == inCjk) {
                    segment.append(ch)
                } else {
                    if (segment.isNotEmpty()) {
                        result.append(if (inCjk) converter.convert(segment.toString()) else segment)
                        segment.clear()
                    }
                    segment.append(ch)
                    inCjk = cjk
                }
            }
            if (segment.isNotEmpty()) {
                result.append(if (inCjk) converter.convert(segment.toString()) else segment)
            }
            result.toString()
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.w(TAG, "OpenCC conversion failed: ${e.message}")
            text
        }
    }

    private fun isCjkCharacter(ch: Char): Boolean {
        val code = ch.code
        return code in 0x4E00..0x9FFF || code in 0x3400..0x4DBF ||
                code in 0x2E80..0x2FDF || code in 0x3000..0x303F ||
                code in 0xF900..0xFAFF || code in 0xFE30..0xFE4F ||
                code in 0xFF00..0xFFEF
    }
}
