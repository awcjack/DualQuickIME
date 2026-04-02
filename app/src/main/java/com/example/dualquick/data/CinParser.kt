package com.example.dualquick.data

import java.io.InputStream

/**
 * Parser for OpenVanilla .cin (Chinese Input) files.
 *
 * The .cin format consists of:
 * - Header directives (%gen_inp, %ename, %cname, etc.)
 * - Key name block (%keyname begin ... %keyname end)
 * - Character definition block (%chardef begin ... %chardef end)
 *
 * Each line in %chardef is: <code> <character>
 * where code is 1-2 lowercase letters and character is a Chinese character.
 */
class CinParser {

    /**
     * Parse a .cin file from an InputStream.
     *
     * @param inputStream The input stream to read from
     * @return A SimplexTable containing all parsed entries
     */
    fun parse(inputStream: InputStream): SimplexTable {
        val entries = mutableListOf<SimplexEntry>()
        var order = 0
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
                    inChardef && trimmed.isNotBlank() -> {
                        // Parse character definition line: <code> <character>
                        val parts = trimmed.split(Regex("\\s+"), limit = 2)
                        if (parts.size == 2) {
                            val code = parts[0].lowercase()
                            val character = parts[1]

                            // Only accept 1-2 character codes (a-z only)
                            if (code.length in 1..2 && code.all { it in 'a'..'z' }) {
                                entries.add(SimplexEntry(
                                    code = code,
                                    character = character,
                                    order = order++
                                ))
                            }
                        }
                    }
                }
            }
        }

        return SimplexTable(entries)
    }

    companion object {
        /**
         * Convenience method to parse from a string.
         */
        fun parseString(content: String): SimplexTable {
            return CinParser().parse(content.byteInputStream())
        }
    }
}
