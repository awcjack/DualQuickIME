package com.awcjack.dualquickime.data

import java.io.InputStream

/**
 * Represents the associated phrases table loaded from OpenVanilla's associated-phrases.cin.
 *
 * Associated phrases provide word/phrase suggestions based on the last committed character.
 * For example, after typing "我", it might suggest "們", "的", "國" etc.
 *
 * The data structure maps a single character to a list of associated phrases,
 * ordered by frequency (most frequent first).
 */
class AssociatedPhrasesTable(
    private val phraseMap: Map<String, List<String>>
) {
    /**
     * Look up associated phrases for a given character.
     *
     * @param character The character to look up (typically the last committed character)
     * @return List of associated phrases, ordered by frequency. Empty list if none found.
     */
    fun lookup(character: String): List<String> {
        return phraseMap[character] ?: emptyList()
    }

    /**
     * Check if this table has any entries for the given character.
     */
    fun hasEntries(character: String): Boolean {
        return phraseMap.containsKey(character) && phraseMap[character]?.isNotEmpty() == true
    }

    /**
     * Get the total number of prefix characters in the table.
     */
    val prefixCount: Int
        get() = phraseMap.size

    /**
     * Get the total number of phrase entries across all characters.
     */
    val totalPhrases: Int
        get() = phraseMap.values.sumOf { it.size }

    companion object {
        val EMPTY = AssociatedPhrasesTable(emptyMap())
    }
}

/**
 * Parser for OpenVanilla associated-phrases.cin files.
 *
 * The format is similar to regular .cin files:
 * - Header directives (%gen_inp, %ename, %cname, etc.)
 * - Empty keyname block
 * - Character definition block (%chardef begin ... %chardef end)
 *
 * Each line in %chardef is: <prefix_character> <associated_phrase>
 * where prefix_character is a single Chinese character and associated_phrase
 * is the suggested word/phrase to follow it.
 *
 * Phrases are pre-sorted by frequency in the file (most frequent first).
 */
class AssociatedPhrasesParser {

    /**
     * Parse an associated-phrases.cin file from an InputStream.
     *
     * @param inputStream The input stream to read from
     * @return An AssociatedPhrasesTable containing all parsed entries
     */
    fun parse(inputStream: InputStream): AssociatedPhrasesTable {
        val phraseMap = mutableMapOf<String, MutableList<String>>()
        var inChardef = false

        inputStream.bufferedReader(Charsets.UTF_8).useLines { lines ->
            for (line in lines) {
                val trimmed = line.trim()

                when {
                    trimmed == "%chardef begin" -> {
                        inChardef = true
                    }
                    trimmed == "%chardef end" -> {
                        inChardef = false
                    }
                    inChardef && trimmed.isNotBlank() && !trimmed.startsWith("%") -> {
                        // Parse character definition line: <prefix> <phrase>
                        val parts = trimmed.split(Regex("\\s+"), limit = 2)
                        if (parts.size == 2) {
                            val prefix = parts[0]
                            val phrase = parts[1]

                            // Add to the list for this prefix character
                            phraseMap.getOrPut(prefix) { mutableListOf() }.add(phrase)
                        }
                    }
                }
            }
        }

        return AssociatedPhrasesTable(phraseMap)
    }

    companion object {
        /**
         * Convenience method to parse from a string.
         */
        fun parseString(content: String): AssociatedPhrasesTable {
            return AssociatedPhrasesParser().parse(content.byteInputStream())
        }
    }
}
