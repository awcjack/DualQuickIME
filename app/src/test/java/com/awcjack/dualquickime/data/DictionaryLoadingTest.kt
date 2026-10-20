package com.awcjack.dualquickime.data

import java.io.ByteArrayInputStream
import java.io.File
import java.util.concurrent.CancellationException
import org.junit.Assert.*
import org.junit.Test

class DictionaryLoadingTest {
    @Test fun bundledQuickTablesKeepEveryCodeAndItsOriginalCandidateOrder() {
        val entryPattern = Regex("([a-zA-Z]{1,2})\\s+(.+)")
        for (filename in listOf("simplex.cin", "simplex-ext.cin")) {
            val file = File("src/main/assets/$filename")
            val expected = linkedMapOf<String, MutableList<String>>()
            var inCharacters = false
            file.forEachLine { line ->
                val trimmed = line.trim()
                when (trimmed) {
                    "%chardef begin" -> inCharacters = true
                    "%chardef end" -> inCharacters = false
                    else -> if (inCharacters) entryPattern.matchEntire(trimmed)?.let {
                        expected.getOrPut(it.groupValues[1].lowercase()) { mutableListOf() }.add(it.groupValues[2])
                    }
                }
            }
            val actual = CinParser().parse(file.inputStream())
            assertTrue(actual.entryCount > 10000)
            assertEquals(expected.size, actual.codeCount)
            assertEquals(expected.values.sumOf { it.size }, actual.entryCount)
            expected.forEach { (code, candidates) -> assertEquals("$filename $code", candidates, actual.lookup(code)) }
        }
    }

    @Test fun streamingQuickParserPreservesSourceOrderAndAcceptedEntries() {
        val table = CinParser.parseString("""
            %keyname begin
            a 日
            %keyname end
            %chardef begin
            HI 的
            a 日
            hi 得
            hi 我
            hi 得
            zz 𨋢
            abc 忽略
            a1 忽略
            missing
            %chardef end
            hi 忽略
        """.trimIndent())
        assertEquals(listOf("的", "得", "我", "得"), table.lookup("HI"))
        assertEquals(listOf("𨋢"), table.lookup("zz"))
        assertEquals(3, table.codeCount)
        assertEquals(6, table.entryCount)
    }

    @Test fun suggestionParserPreservesWhitespaceSeparatedPhrasesAndOrder() {
        val table = AssociatedPhrasesParser.parseString("%chardef begin\n我\t們\n我   的\n你 好\n%chardef end")
        assertEquals(listOf("們", "的"), table.lookup("我"))
        assertEquals(3, table.totalPhrases)
    }

    @Test fun canceledSuggestionParseClosesStreamAndStopsBeforeReadingEverything() {
        val input = ClosingStream("%chardef begin\n我 們\n我 的\n我 國\n%chardef end")
        var rows = 0
        assertThrows(CancellationException::class.java) {
            AssociatedPhrasesParser().parse(input) { ++rows < 3 }
        }
        assertEquals(3, rows)
        assertTrue(input.closed)
    }

    @Test fun canceledPronunciationParseClosesCharacterAndWordStreams() {
        val characters = ClosingStream("6211\tngo5\n")
        val words = ClosingStream("我我\tngo5 ngo5\n我我我\tngo5 ngo5 ngo5\n")
        var rows = 0
        assertThrows(CancellationException::class.java) {
            PronunciationTable.parse(characters, words) { ++rows < 3 }
        }
        assertTrue(characters.closed)
        assertTrue(words.closed)
        assertEquals(3, rows)
    }

    private class ClosingStream(text: String) : ByteArrayInputStream(text.toByteArray()) {
        var closed = false
        override fun close() { closed = true; super.close() }
    }
}
