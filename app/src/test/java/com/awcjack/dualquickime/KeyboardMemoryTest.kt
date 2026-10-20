package com.awcjack.dualquickime

import android.app.ActivityManager
import android.content.ComponentCallbacks2
import android.content.Context
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.TextView
import com.awcjack.dualquickime.data.PronunciationMode
import com.awcjack.dualquickime.theme.ThemeManager
import com.awcjack.dualquickime.ui.CandidatePronunciation
import com.awcjack.dualquickime.ui.KeyboardView
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class KeyboardMemoryTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Before fun setup() {
        context.getSharedPreferences("dualquick_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        ThemeManager.invalidateCache()
        ThemeManager.setPronunciationMode(context, PronunciationMode.CANTONESE)
        setLowMemory(false)
        CandidatePronunciation.resumeLoading()
        CandidatePronunciation.preload(context, PronunciationMode.CANTONESE).get(10, TimeUnit.SECONDS)
    }

    private fun setLowMemory(low: Boolean) {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        shadowOf(manager).setMemoryInfo(ActivityManager.MemoryInfo().apply { lowMemory = low })
    }

    @Test fun firstOpeningReusesCoreDictionaryInsteadOfAllocatingItTwice() {
        val controller = Robolectric.buildService(DualQuickInputMethodService::class.java).create()
        try {
            val service = controller.get()
            val table = ReflectionHelpers.getField<Any>(service, "simplexTable")
            service.onCreateInputView()
            service.onStartInputView(EditorInfo(), false)
            assertSame(table, ReflectionHelpers.getField<Any>(service, "simplexTable"))
            service.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN)
            service.onStartInputView(EditorInfo(), false)
            assertSame(table, ReflectionHelpers.getField<Any>(service, "simplexTable"))
        } finally { controller.destroy() }
    }

    @Test fun memoryTrimClosesGridAndKeepsQuickCandidatesAndTypingKeysAvailable() {
        val controller = Robolectric.buildService(DualQuickInputMethodService::class.java).create()
        try {
            val service = controller.get()
            val root = service.onCreateInputView()
            service.onStartInputView(EditorInfo(), false)
            val keyboard = (root as ViewGroup).getChildAt(0) as KeyboardView
            pressLetter(root, "H")
            pressLetter(root, "I")
            keyboard.showCandidateGrid(listOf("凡", "么", "丟"))
            service.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL)
            assertTrue(textViews(root).any { it.text.toString() == "Q" })
            assertTrue(textViews(root).any { it.text.toString() == "凡" && it.visibility == View.VISIBLE })
            assertEquals("我", CandidatePronunciation.label("我", PronunciationMode.CANTONESE, Color.GRAY))
            assertEquals(PronunciationMode.CANTONESE, ThemeManager.getPronunciationMode(context))
            pressLetter(root, "A") // The recovered key listener must still update composition.
            assertTrue(textViews(root).any { it.text.toString() == "hia" })
        } finally { controller.destroy() }
    }

    @Test fun lowMemoryStartupUsesPlainLabelsAndRestoresPreferenceAfterRecovery() {
        setLowMemory(true)
        val controller = Robolectric.buildService(DualQuickInputMethodService::class.java).create()
        try {
            val service = controller.get()
            val root = service.onCreateInputView() as ViewGroup
            service.onStartInputView(EditorInfo(), false)
            val keyboard = root.getChildAt(0) as KeyboardView
            keyboard.setCandidates(listOf("我"), 1, 1, 0)
            assertTrue(textViews(root).any { it.text.toString() == "我" })
            assertFalse(textViews(root).any { it.text.contains('\n') })
            setLowMemory(false)
            service.onStartInputView(EditorInfo(), false)
            CandidatePronunciation.preload(context, PronunciationMode.CANTONESE).get(10, TimeUnit.SECONDS)
            keyboard.setCandidates(listOf("我"), 1, 1, 0)
            assertTrue(textViews(root).any { it.text.toString() == "ngo5\n我" })
            assertEquals(PronunciationMode.CANTONESE, ThemeManager.getPronunciationMode(context))
        } finally { controller.destroy() }
    }

    @Test fun hiddenPanelIsDetachedOnTrimAndKeyboardRebuildsOnNextOpening() {
        val controller = Robolectric.buildService(DualQuickInputMethodService::class.java).create()
        try {
            val service = controller.get()
            val root = service.onCreateInputView() as ViewGroup
            service.onStartInputView(EditorInfo(), false)
            val keyboard = root.getChildAt(0) as KeyboardView
            keyboard.showCandidateGrid(listOf("我"))
            assertTrue(keyboard.childCount > 0)
            service.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN)
            assertEquals(0, keyboard.childCount)
            service.onStartInputView(EditorInfo(), false)
            assertTrue(textViews(root).any { it.text.toString() == "Q" })
            pressLetter(root, "H")
            assertTrue(textViews(root).any { it.text.toString() == "h" })
        } finally { controller.destroy() }
    }

    private fun pressLetter(root: View, letter: String) {
        val label = textViews(root).single { it.text.toString() == letter }
        assertTrue((label.parent as View).performClick())
    }

    private fun textViews(view: View): List<TextView> = when (view) {
        is TextView -> listOf(view)
        is ViewGroup -> (0 until view.childCount).flatMap { textViews(view.getChildAt(it)) }
        else -> emptyList()
    }
}
