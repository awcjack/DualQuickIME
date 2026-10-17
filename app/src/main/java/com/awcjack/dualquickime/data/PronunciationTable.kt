package com.awcjack.dualquickime.data

import java.io.InputStream

/** One sourced, customary reading per Unicode code point. No guessed fallbacks. */
class PronunciationTable private constructor(
    private val readings: Map<Int, String>,
    private val words: Map<String, String>
) {
    fun readingFor(candidate: String, precedingCharacter: String = ""): String? {
        if (candidate.isEmpty()) return null
        val count = candidate.codePointCount(0, candidate.length)
        // Associated suggestions are suffixes: 銀 + 行 uses 銀行's reading,
        // but only the suggested suffix's syllables are displayed.
        if (precedingCharacter.isNotEmpty()) {
            words[precedingCharacter + candidate]?.let { return it.split(' ').takeLast(count).joinToString(" ") }
        }
        words[candidate]?.let { return it }
        val syllables = mutableListOf<String>()
        var offset = 0
        while (offset < candidate.length) {
            val codePoint = candidate.codePointAt(offset)
            syllables.add(readings[codePoint] ?: return null)
            offset += Character.charCount(codePoint)
        }
        return syllables.joinToString(" ")
    }

    companion object {
        fun parse(input: InputStream, wordInput: InputStream? = null): PronunciationTable {
            val readings = HashMap<Int, String>()
            input.bufferedReader(Charsets.UTF_8).useLines { lines ->
                lines.filter { it.isNotBlank() && !it.startsWith("#") }.forEach { line ->
                    val fields = line.split('\t', limit = 2)
                    require(fields.size == 2 && fields[1].isNotBlank()) { "Invalid pronunciation entry" }
                    val codePoint = fields[0].toInt(16)
                    require(Character.isValidCodePoint(codePoint)) { "Invalid pronunciation code point" }
                    require(readings.put(codePoint, fields[1]) == null) { "Duplicate pronunciation entry" }
                }
            }
            val words = HashMap<String, String>()
            wordInput?.bufferedReader(Charsets.UTF_8)?.useLines { lines ->
                lines.filter { it.isNotBlank() && !it.startsWith("#") }.forEach { line ->
                    val fields = line.split('\t', limit = 2)
                    require(fields.size == 2 && fields[0].isNotEmpty()) { "Invalid word pronunciation entry" }
                    require(fields[1].split(' ').size == fields[0].codePointCount(0, fields[0].length)) {
                        "Word pronunciation must have one syllable per character"
                    }
                    require(words.put(fields[0], fields[1]) == null) { "Duplicate word pronunciation entry" }
                }
            }
            return PronunciationTable(readings, words)
        }
    }
}
