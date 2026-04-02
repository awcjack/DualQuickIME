package com.example.dualquick.util

/**
 * Key-to-Radical mapping for 速成 (Quick/Simplified Cangjie) input method.
 * Based on OpenVanilla's simplex.cin data format.
 */
object KeyMapping {

    /**
     * Maps English keyboard keys (a-z) to Chinese radicals (部首).
     */
    val keyToRadical: Map<Char, String> = mapOf(
        'a' to "日",
        'b' to "月",
        'c' to "金",
        'd' to "木",
        'e' to "水",
        'f' to "火",
        'g' to "土",
        'h' to "竹",
        'i' to "戈",
        'j' to "十",
        'k' to "大",
        'l' to "中",
        'm' to "一",
        'n' to "弓",
        'o' to "人",
        'p' to "心",
        'q' to "手",
        'r' to "口",
        's' to "尸",
        't' to "廿",
        'u' to "山",
        'v' to "女",
        'w' to "田",
        'x' to "難",
        'y' to "卜",
        'z' to "重"
    )

    /**
     * Get the radical for a given key, or null if not a valid key.
     */
    fun getRadical(key: Char): String? = keyToRadical[key.lowercaseChar()]

    /**
     * Convert a sequence of keys to their radical representation.
     * Example: "qr" -> "手口"
     */
    fun getRadicalSequence(keys: String): String {
        return keys.lowercase().mapNotNull { keyToRadical[it] }.joinToString("")
    }

    /**
     * Check if a character is a valid Cangjie key (a-z).
     */
    fun isValidKey(char: Char): Boolean = char.lowercaseChar() in 'a'..'z'
}
