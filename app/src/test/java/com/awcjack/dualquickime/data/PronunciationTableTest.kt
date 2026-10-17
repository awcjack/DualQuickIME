package com.awcjack.dualquickime.data

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class PronunciationTableTest {
    private fun bundled(name: String) = PronunciationTable.parse(
        File("src/main/assets/pronunciation/$name.tsv").inputStream(),
        File("src/main/assets/pronunciation/$name-words.tsv").inputStream()
    )

    @Test fun bundledReadingsMatchTheRequestedCandidateStrip() {
        val cantonese = bundled("jyutping")
        val mandarin = bundled("pinyin")
        val candidates = listOf("的", "得", "我", "等", "簿", "風")
        assertEquals(listOf("dik1", "dak1", "ngo5", "dang2", "bou6", "fung1"), candidates.map(cantonese::readingFor))
        assertEquals(listOf("de", "de", "wǒ", "děng", "bù", "fēng"), candidates.map(mandarin::readingFor))
    }

    @Test fun polyphonicCharactersHaveOneSourcedCustomaryReading() {
        assertEquals("hang4", bundled("jyutping").readingFor("行"))
        assertEquals("xíng", bundled("pinyin").readingFor("行"))
        // kHanyuPinlu reports de(5096), dé(1496), děi(637), while
        // kMandarin defaults to dé. Prefer the measured highest frequency.
        assertEquals("de", bundled("pinyin").readingFor("得"))
        // The two Unihan values are regional preferences, not a frequency list.
        assertEquals("wàn", bundled("pinyin").readingFor("万"))
    }

    @Test fun supplementaryCharactersUseWholeCodePoints() {
        val table = PronunciationTable.parse("20000\the1\n".byteInputStream())
        assertEquals("he1", table.readingFor(String(Character.toChars(0x20000))))
        assertNull(table.readingFor("\uD840"))
    }

    @Test fun unknownCharactersAndEnglishHaveNoGuessedReading() {
        val table = bundled("jyutping")
        listOf("", "hi", "A", "🙂", "你🙂", "\u0378").forEach { assertNull(table.readingFor(it)) }
    }

    @Test fun suggestedWordsUseSourcedWordReadingsAndCharacterFallbacks() {
        assertEquals("jam1 ngok6", bundled("jyutping").readingFor("音樂"))
        assertEquals("yīn yuè", bundled("pinyin").readingFor("音樂"))
        assertEquals("nei5 hou2", bundled("jyutping").readingFor("你好"))
        assertEquals("nǐ hǎo", bundled("pinyin").readingFor("你好"))
    }

    @Test fun suggestedSuffixUsesTheCommittedCharactersWordContext() {
        assertEquals("hong4", bundled("jyutping").readingFor("行", "銀"))
        assertEquals("háng", bundled("pinyin").readingFor("行", "銀"))
        assertEquals("ngok6", bundled("jyutping").readingFor("樂", "音"))
        assertEquals("yuè", bundled("pinyin").readingFor("樂", "音"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun duplicateReadingsAreRejected() {
        PronunciationTable.parse("6211\tngo5\n6211\tngo4\n".byteInputStream())
    }

    @Test fun unknownPreferenceDefaultsToOff() {
        assertEquals(PronunciationMode.NONE, PronunciationMode.fromPreference(null))
        assertEquals(PronunciationMode.NONE, PronunciationMode.fromPreference("future-mode"))
    }
}
