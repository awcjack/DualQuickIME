package com.awcjack.dualquickime.data

import com.awcjack.dualquickime.util.KeyMapping

/**
 * Lookup table for 速成 (Quick/Simplified Cangjie) input method.
 * Maps input codes to lists of Chinese characters, preserving frequency order.
 */
class SimplexTable private constructor(private val codeToChars: Map<String, List<String>>) {

    // Map from code -> list of characters (ordered by frequency from .cin file)
    constructor(entries: List<SimplexEntry>) : this(
        // Group entries by code, preserving order within each group
        entries
            .groupBy { it.code }
            .mapValues { (_, entries) ->
                entries.sortedBy { it.order }.map { it.character }
            }
    )

    companion object {
        /** Parser-owned lists already follow source frequency order. */
        internal fun fromOrderedCharacters(characters: Map<String, List<String>>) = SimplexTable(characters)
    }

    /**
     * Look up all characters matching the given code.
     * Returns characters in frequency order (most common first).
     *
     * @param code The input code (1-2 lowercase letters)
     * @return List of matching Chinese characters, or empty list if none
     */
    fun lookup(code: String): List<String> {
        return codeToChars[code.lowercase()] ?: emptyList()
    }

    /**
     * Get the radical display string for a code.
     * Example: "qr" -> "手口"
     */
    fun getRadicalSequence(code: String): String {
        return KeyMapping.getRadicalSequence(code)
    }

    /**
     * Get the radical for a single key.
     */
    fun getRadical(key: Char): String? {
        return KeyMapping.getRadical(key)
    }

    /**
     * Check if any characters exist for the given code.
     */
    fun hasEntries(code: String): Boolean {
        return codeToChars.containsKey(code.lowercase())
    }

    /**
     * Get the number of entries for a code.
     */
    fun getEntryCount(code: String): Int {
        return codeToChars[code.lowercase()]?.size ?: 0
    }

    /**
     * Total number of unique codes in the table.
     */
    val codeCount: Int
        get() = codeToChars.size

    /**
     * Total number of character entries.
     */
    val entryCount: Int
        get() = codeToChars.values.sumOf { it.size }
}
