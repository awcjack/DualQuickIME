package com.awcjack.dualquickime.ui

import android.content.Context
import android.graphics.Color
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.RadioGroup
import com.awcjack.dualquickime.R
import com.awcjack.dualquickime.SettingsActivity
import com.awcjack.dualquickime.data.PronunciationMode
import com.awcjack.dualquickime.theme.ThemeManager
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class CandidatePronunciationTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Before fun resetPreferences() {
        context.getSharedPreferences("dualquick_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        ThemeManager.invalidateCache()
        CandidatePronunciation.resumeLoading()
        CandidatePronunciation.preload(context, PronunciationMode.CANTONESE).get(10, TimeUnit.SECONDS)
        CandidatePronunciation.preload(context, PronunciationMode.MANDARIN).get(10, TimeUnit.SECONDS)
    }

    @Test fun pronunciationIsSmallerAndGreyAboveTheCharacter() {
        for ((mode, expected) in listOf(PronunciationMode.CANTONESE to "ngo5", PronunciationMode.MANDARIN to "wǒ")) {
            val label = CandidatePronunciation.label("我", mode, Color.GRAY) as Spanned
            assertEquals("$expected\n我", label.toString())
            val color = label.getSpans(0, expected.length, ForegroundColorSpan::class.java).single()
            assertEquals(Color.GRAY, color.foregroundColor)
            assertEquals(expected.length, label.getSpanEnd(color))
            assertTrue(label.getSpans(0, expected.length, RelativeSizeSpan::class.java).single().sizeChange < 1f)
            assertTrue(label.getSpans(label.length - 1, label.length, ForegroundColorSpan::class.java).isEmpty())
        }
    }

    @Test fun offAndUnknownReadingsLeaveLabelsUntouched() {
        assertEquals("我", CandidatePronunciation.label("我", PronunciationMode.NONE, Color.GRAY))
        assertEquals("hi", CandidatePronunciation.label("hi", PronunciationMode.CANTONESE, Color.GRAY))
    }

    @Test fun allThreeModesPersistAndRefreshOnTheSameKeyboard() {
        val keyboard = KeyboardView(context)
        var committed: String? = null
        keyboard.setOnCandidateSelectedListener { committed = it }
        for ((mode, label) in listOf(
            PronunciationMode.CANTONESE to "ngo5\n我",
            PronunciationMode.MANDARIN to "wǒ\n我",
            PronunciationMode.NONE to "我"
        )) {
            ThemeManager.setPronunciationMode(context, mode)
            ThemeManager.invalidateCache()
            assertEquals(mode, ThemeManager.getPronunciationMode(context))
            keyboard.refreshTheme()
            keyboard.setCandidates(listOf("我"), 1, 1, 0)
            val pill = textViews(keyboard).single { it.text.toString() == label }
            pill.performClick()
            assertEquals("我", committed)
        }
    }

    @Test fun settingsSelectionSavesTheModeAndRestoresItWhenReopened() {
        val controller = Robolectric.buildActivity(SettingsActivity::class.java).setup()
        val group = controller.get().findViewById<RadioGroup>(R.id.pronunciationRadioGroup)
        assertEquals(R.id.radioPronunciationNone, group.checkedRadioButtonId)
        group.check(R.id.radioPronunciationCantonese)
        assertEquals(PronunciationMode.CANTONESE, ThemeManager.getPronunciationMode(context))
        group.check(R.id.radioPronunciationMandarin)
        assertEquals(PronunciationMode.MANDARIN, ThemeManager.getPronunciationMode(context))
        controller.pause().stop().destroy()
        val reopened = Robolectric.buildActivity(SettingsActivity::class.java).setup()
        assertEquals(R.id.radioPronunciationMandarin,
            reopened.get().findViewById<RadioGroup>(R.id.pronunciationRadioGroup).checkedRadioButtonId)
        reopened.pause().stop().destroy()
    }

    @Test fun expandedGridLabelsStillCommitOnlyTheCharacter() {
        ThemeManager.setPronunciationMode(context, PronunciationMode.CANTONESE)
        val grid = CandidateGridView(context)
        var committed: String? = null
        grid.setOnCandidateSelectedListener { committed = it }
        grid.setCandidates(listOf("我", "行"))
        textViews(grid).single { it.text.toString() == "hang4\n行" }.performClick()
        assertEquals("行", committed)
    }

    @Test fun suggestionsAndExpandedGridKeepWordContextAndCommitOnlyTheSuggestion() {
        ThemeManager.setPronunciationMode(context, PronunciationMode.CANTONESE)
        val keyboard = KeyboardView(context)
        var committed: String? = null
        keyboard.setOnCandidateSelectedListener { committed = it }
        keyboard.setCandidates(listOf("行"), 1, 1, 0, "銀")
        textViews(keyboard).single { it.text.toString() == "hong4\n行" }.performClick()
        assertEquals("行", committed)
        keyboard.showCandidateGrid(listOf("行"))
        textViews(keyboard).single { it.text.toString() == "hong4\n行" }.performClick()
        assertEquals("行", committed)

        keyboard.closeCandidateGrid()
        keyboard.setCandidates(listOf("音樂"), 1, 1, 0)
        textViews(keyboard).single { it.text.toString() == "jam1 ngok6\n音樂" }.performClick()
        assertEquals("音樂", committed)
    }

    @Test fun longSuggestionsPageWithoutGrowingTheStripWithOrWithoutPronunciation() {
        for (mode in listOf(PronunciationMode.NONE, PronunciationMode.CANTONESE, PronunciationMode.MANDARIN)) {
            ThemeManager.setPronunciationMode(context, mode)
            val keyboard = KeyboardView(context)
            fun layoutKeyboard() {
                keyboard.measure(View.MeasureSpec.makeMeasureSpec(260, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
                keyboard.layout(0, 0, 260, keyboard.measuredHeight)
            }
            val slots = textViews(keyboard).filter { it.visibility == View.INVISIBLE }
            slots.forEachIndexed { index, slot -> slot.minWidth = if (index == 1) 400 else 50 }
            layoutKeyboard()
            val idleHeight = keyboard.measuredHeight
            val longWord = "一".repeat(40)
            val suggestions = listOf("音樂", longWord, "我")
            val count = keyboard.setCandidates(suggestions, 1, suggestions.size, 0)
            layoutKeyboard()
            assertEquals(1, count)
            assertEquals(idleHeight, keyboard.measuredHeight)
            assertTrue(textViews(keyboard).any { it.visibility == View.VISIBLE && it.text.toString().startsWith("1/") })
            assertEquals(if (mode == PronunciationMode.NONE) 1 else 2, slots.first().maxLines)

            keyboard.setCandidates(suggestions.drop(count), 2, suggestions.size, count)
            layoutKeyboard()
            assertEquals(idleHeight, keyboard.measuredHeight)
            assertEquals(longWord, slots.first().text.toString().substringAfter('\n'))
            assertTrue(slots.first().measuredWidth <= keyboard.width)
            assertTrue(slots.first().lineCount <= if (mode == PronunciationMode.NONE) 1 else 2)
        }
    }

    @Test fun narrowStripKeepsAContiguousPrefixForPagination() {
        ThemeManager.setPronunciationMode(context, PronunciationMode.CANTONESE)
        val keyboard = KeyboardView(context)
        // Deterministic pill widths: the second must overflow and subsequent
        // narrow pills must stay on the next page. This does not depend on
        // Robolectric's host font measurements.
        val slots = textViews(keyboard).filter { it.visibility == View.INVISIBLE }
        assertEquals(6, slots.size)
        slots.forEachIndexed { index, slot -> slot.minWidth = if (index == 1) 300 else 50 }
        keyboard.measure(View.MeasureSpec.makeMeasureSpec(260, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        keyboard.layout(0, 0, 260, keyboard.measuredHeight)
        val candidates = listOf("一", "雙", "人", "我", "行", "風")
        val displayed = keyboard.setCandidates(candidates, 1, candidates.size, 0)
        val visible = textViews(keyboard).filter { it.visibility == View.VISIBLE && it.text.contains('\n') }
        assertTrue(displayed > 0)
        assertTrue(displayed < candidates.size)
        assertEquals(1, displayed)
        assertEquals(candidates.take(displayed), visible.map { it.text.toString().substringAfter('\n') })
    }

    private fun textViews(view: View): List<TextView> = when (view) {
        is TextView -> listOf(view)
        is ViewGroup -> (0 until view.childCount).flatMap { textViews(view.getChildAt(it)) }
        else -> emptyList()
    }
}
