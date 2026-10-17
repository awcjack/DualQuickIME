package com.awcjack.dualquickime.ui

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.awcjack.dualquickime.data.PronunciationMode
import com.awcjack.dualquickime.theme.ThemeManager
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Real font metrics, including Android's CJK fallback fonts. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w400dp-h800dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CandidateSizingTest {
    private fun contextAt(scale: Float): Context {
        val app = RuntimeEnvironment.getApplication()
        val config = Configuration(app.resources.configuration).apply { fontScale = scale }
        return app.createConfigurationContext(config)
    }

    private fun layout(view: View, context: Context) {
        val width = (400 * context.resources.displayMetrics.density).toInt()
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        view.layout(0, 0, width, view.measuredHeight)
    }

    @Test fun stripFitsBothLinesAtNormalAndLargeFontSizesWithoutGrowingForSuggestions() {
        for (mode in listOf(PronunciationMode.CANTONESE, PronunciationMode.MANDARIN)) {
            for (scale in listOf(1f, 1.3f, 2f)) {
                val context = contextAt(scale)
                assertEquals(scale, context.resources.displayMetrics.scaledDensity /
                    context.resources.displayMetrics.density, 0.01f)
                ThemeManager.setPronunciationMode(context, mode)
                val keyboard = KeyboardView(context)
                layout(keyboard, context)
                val idleHeight = keyboard.height
                keyboard.setCandidates(listOf("皮", "棣", "板", "樣", "枝"), 1, 5, 0)
                layout(keyboard, context)
                assertFits(keyboard, "$mode at font scale $scale")
                assertEquals(idleHeight, keyboard.height)
                keyboard.setCandidates(listOf("音樂", "你好", "一".repeat(20)), 1, 3, 0)
                layout(keyboard, context)
                assertFits(keyboard, "$mode suggestions at font scale $scale")
                assertEquals(idleHeight, keyboard.height)
                val longWord = "一".repeat(40)
                var committed: String? = null
                keyboard.setOnCandidateSelectedListener { committed = it }
                keyboard.setCandidates(listOf(longWord), 1, 1, 0)
                layout(keyboard, context)
                assertFits(keyboard, "$mode oversized word at font scale $scale")
                val longPill = textViews(keyboard).single { it.visibility == View.VISIBLE && it.text.contains('\n') }
                assertTrue(longPill.text.toString().substringAfter('\n').length < longWord.length)
                longPill.performClick()
                assertEquals(longWord, committed)
                if (mode == PronunciationMode.MANDARIN && scale == 1.3f) {
                    keyboard.setCandidates(listOf("皮", "棣", "板", "樣", "枝"), 1, 5, 0)
                    layout(keyboard, context)
                    savePreview(keyboard)
                }
            }
        }
    }

    @Test fun expandedGridFitsBothLinesAtNormalAndLargeFontSizes() {
        for (mode in listOf(PronunciationMode.CANTONESE, PronunciationMode.MANDARIN)) {
            for (scale in listOf(1f, 1.3f, 2f)) {
                val context = contextAt(scale)
                ThemeManager.setPronunciationMode(context, mode)
                val grid = CandidateGridView(context)
                grid.setCandidates(listOf("皮", "棣", "板", "樣", "枝", "音樂"))
                layout(grid, context)
                assertFits(grid, "$mode grid at font scale $scale")
            }
        }
    }

    private fun assertFits(view: View, description: String) {
        val pills = textViews(view).filter { it.visibility == View.VISIBLE && it.text.contains('\n') }
        assertTrue(description, pills.isNotEmpty())
        for (pill in pills) {
            val textLayout = requireNotNull(pill.layout)
            assertEquals(description, 2, textLayout.lineCount)
            assertEquals(description, pill.text.indexOf('\n') + 1, textLayout.getLineStart(1))
            val required = textLayout.height + pill.compoundPaddingTop + pill.compoundPaddingBottom
            assertTrue("$description: ${pill.text} requires $required px, cell has ${pill.height} px",
                required <= pill.height)
            assertTrue(description, textLayout.width <= pill.width - pill.compoundPaddingLeft - pill.compoundPaddingRight)
            for (line in 0 until textLayout.lineCount) {
                assertTrue(description, textLayout.getLineLeft(line) >= 0)
                assertTrue(description, textLayout.getLineRight(line) <= textLayout.width)
            }
            val parent = pill.parent as ViewGroup
            assertTrue(description, pill.top >= parent.paddingTop)
            assertTrue(description, pill.bottom <= parent.height - parent.paddingBottom)
        }
    }

    private fun savePreview(view: View) {
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        val output = File("build/outputs/layout-preview/pronunciation-strip-130.png")
        requireNotNull(output.parentFile).mkdirs()
        output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    private fun textViews(view: View): List<TextView> = when (view) {
        is TextView -> listOf(view)
        is ViewGroup -> (0 until view.childCount).flatMap { textViews(view.getChildAt(it)) }
        else -> emptyList()
    }
}
