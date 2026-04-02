package com.awcjack.dualquickime

import android.content.ClipboardManager
import android.content.Context
import android.inputmethodservice.InputMethodService
import android.view.View
import android.view.inputmethod.EditorInfo
import com.awcjack.dualquickime.data.CinParser
import com.awcjack.dualquickime.data.ClipboardHistoryManager
import com.awcjack.dualquickime.data.CompositionState
import com.awcjack.dualquickime.data.SimplexTable
import com.awcjack.dualquickime.theme.ThemeManager
import com.awcjack.dualquickime.ui.KeyboardView

/**
 * Quick (速成) Input Method Service for Android.
 *
 * Supports dual-mode Chinese/English input:
 * - TAP on candidate pill to commit Chinese character
 * - SPACE to navigate candidate pages
 * - Type 3rd letter key to commit previous as English and start new composition
 * - ENTER to commit composition as English
 * - Numbers commit composition as English first, then the number
 *
 * Uses embedded Gboard-style candidate bar (not system candidates view).
 */
class DualQuickInputMethodService : InputMethodService() {

    private lateinit var simplexTable: SimplexTable
    private var composition = CompositionState.EMPTY

    private var keyboardView: KeyboardView? = null

    // Track current keyboard mode
    private var isSymbolMode = false

    // Track original case of letters for English output
    // Maps index position to whether it was uppercase
    private var letterCases = mutableListOf<Boolean>()

    // System clipboard manager and listener
    private var clipboardManager: ClipboardManager? = null
    private val clipboardListener = ClipboardManager.OnPrimaryClipChangedListener {
        handleSystemClipboardChange()
    }

    override fun onCreate() {
        super.onCreate()
        // Load simplex.cin from assets
        try {
            simplexTable = CinParser().parse(assets.open("simplex.cin"))
        } catch (e: Exception) {
            // Fallback to empty table if loading fails
            simplexTable = SimplexTable(emptyList())
        }

        // Register clipboard listener to capture system clipboard changes
        clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboardManager?.addPrimaryClipChangedListener(clipboardListener)
    }

    override fun onDestroy() {
        super.onDestroy()
        // Unregister clipboard listener to avoid memory leaks
        clipboardManager?.removePrimaryClipChangedListener(clipboardListener)
    }

    /**
     * Handle system clipboard changes (text copied from other apps).
     */
    private fun handleSystemClipboardChange() {
        if (!ClipboardHistoryManager.isEnabled(this)) return

        val clip = clipboardManager?.primaryClip ?: return
        if (clip.itemCount == 0) return

        val item = clip.getItemAt(0)
        val text = item.coerceToText(this)?.toString()

        if (!text.isNullOrBlank()) {
            ClipboardHistoryManager.addItem(this, text)
        }
    }

    override fun onCreateInputView(): View {
        keyboardView = KeyboardView(this).apply {
            setOnKeyPressListener { event ->
                handleKeyEvent(event)
            }
            setOnModeChangeListener { symbolMode ->
                isSymbolMode = symbolMode
            }
            setOnCandidateSelectedListener { candidate ->
                // User TAPPED a Chinese candidate pill - commit Chinese
                commitChinese(candidate)
                clearComposition()
            }
            setOnEnglishSelectedListener { _ ->
                // User TAPPED the English pill - commit as English with preserved case
                commitEnglishPreservingCase(composition.rawKeys, letterCases)
                clearComposition()
            }
        }
        return keyboardView!!
    }

    /**
     * Never use fullscreen mode so keyboard is always visible properly.
     */
    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        // Invalidate caches to pick up any settings changes
        ThemeManager.invalidateCache()
        ClipboardHistoryManager.invalidateCache()
        // Refresh theme in case it changed in settings
        keyboardView?.refreshTheme()
        // Clear composition when starting new input
        clearComposition()
        // Reset to letter mode
        isSymbolMode = false
        keyboardView?.setLetterMode()
    }

    private fun handleKeyEvent(event: KeyboardView.KeyEvent) {
        when (event) {
            is KeyboardView.KeyEvent.Letter -> handleLetter(event.char)
            is KeyboardView.KeyEvent.Number -> handleNumber(event.digit)
            is KeyboardView.KeyEvent.Symbol -> handleSymbol(event.char)
            is KeyboardView.KeyEvent.Emoji -> handleEmoji(event.emoji)
            is KeyboardView.KeyEvent.ClipboardPaste -> handleClipboardPaste(event.text)
            KeyboardView.KeyEvent.Space -> handleSpace()
            KeyboardView.KeyEvent.Backspace -> handleBackspace()
            KeyboardView.KeyEvent.Enter -> handleEnter()
        }
    }

    private fun handleLetter(char: Char) {
        val isUpperCase = char.isUpperCase()
        val lowerChar = char.lowercaseChar()
        val newRawKeys = composition.rawKeys + lowerChar

        if (newRawKeys.length > 2) {
            // 3rd key: commit first 2 as English (preserving case), start new composition
            commitEnglishPreservingCase(composition.rawKeys, letterCases)
            letterCases.clear()
            letterCases.add(isUpperCase)
            updateComposition(lowerChar.toString())
        } else {
            letterCases.add(isUpperCase)
            updateComposition(newRawKeys)
        }
    }

    private fun handleNumber(digit: Int) {
        // Numbers are regular input - commit any composition as English first
        if (composition.rawKeys.isNotEmpty()) {
            commitEnglish(composition.rawKeys)
            clearComposition()
        }
        // Then commit the number
        commitText(digit.toString())
    }

    private fun handleSymbol(char: Char) {
        // Symbols commit composition as English first
        if (composition.rawKeys.isNotEmpty()) {
            commitEnglish(composition.rawKeys)
            clearComposition()
        }
        // Then commit the symbol
        commitText(char.toString())
    }

    private fun handleEmoji(emoji: String) {
        // Emoji commits composition as English first
        if (composition.rawKeys.isNotEmpty()) {
            commitEnglish(composition.rawKeys)
            clearComposition()
        }
        // Then commit the emoji
        commitText(emoji)
    }

    private fun handleClipboardPaste(text: String) {
        // Clipboard paste commits composition as English first
        if (composition.rawKeys.isNotEmpty()) {
            commitEnglish(composition.rawKeys)
            clearComposition()
        }
        // Commit the clipboard text without adding to clipboard history
        // (it's already in the history since user selected it from there)
        commitText(text, addToClipboard = false)
    }

    private fun handleSpace() {
        if (composition.hasCandidates) {
            // NEXT PAGE - do NOT commit
            composition = composition.nextPage()
            updateCandidateView()
        } else if (composition.rawKeys.isNotEmpty()) {
            // No candidates exist - commit as English + space
            commitEnglish(composition.rawKeys + " ")
            clearComposition()
        } else {
            // Just a space (IDLE state)
            commitText(" ")
        }
    }

    private fun handleBackspace() {
        if (composition.rawKeys.isNotEmpty()) {
            val newKeys = composition.rawKeys.dropLast(1)
            // Also remove the case tracking for the deleted letter
            if (letterCases.isNotEmpty()) {
                letterCases.removeAt(letterCases.lastIndex)
            }
            if (newKeys.isEmpty()) {
                clearComposition()
            } else {
                updateComposition(newKeys)
            }
        } else {
            // Delete character in text field
            currentInputConnection?.deleteSurroundingText(1, 0)
        }
    }

    private fun handleEnter() {
        if (composition.rawKeys.isNotEmpty()) {
            // Commit composition as English
            commitEnglish(composition.rawKeys)
            clearComposition()
        }
        // Send enter key event
        sendDownUpKeyEvents(android.view.KeyEvent.KEYCODE_ENTER)
    }

    private fun commitChinese(text: String) {
        commitText(text)
    }

    private fun commitEnglish(text: String) {
        commitEnglishPreservingCase(text, letterCases)
        letterCases.clear()
    }

    private fun commitEnglishPreservingCase(text: String, cases: List<Boolean>) {
        val result = StringBuilder()
        for (i in text.indices) {
            val char = text[i]
            // Use stored case if available, otherwise keep original
            val shouldBeUpper = cases.getOrNull(i) ?: false
            result.append(if (shouldBeUpper) char.uppercaseChar() else char.lowercaseChar())
        }
        commitText(result.toString())
    }

    private fun commitText(text: String, addToClipboard: Boolean = true) {
        currentInputConnection?.commitText(text, 1)
        // Add to clipboard history if enabled and text is substantial
        if (addToClipboard && ClipboardHistoryManager.isEnabled(this)) {
            ClipboardHistoryManager.addItem(this, text)
        }
    }

    private fun updateComposition(rawKeys: String) {
        val candidates = simplexTable.lookup(rawKeys)
        val pageSize = ThemeManager.getCandidatesPerPage(this)
        composition = CompositionState(
            rawKeys = rawKeys,
            candidates = candidates,
            currentPage = 0,
            pageSize = pageSize
        )
        updateUI()
    }

    private fun clearComposition() {
        composition = CompositionState.EMPTY
        letterCases.clear()
        keyboardView?.clearCandidates()
    }

    /**
     * Get the raw keys with proper case applied for display purposes.
     */
    private fun getDisplayKeys(): String {
        val result = StringBuilder()
        for (i in composition.rawKeys.indices) {
            val char = composition.rawKeys[i]
            val shouldBeUpper = letterCases.getOrNull(i) ?: false
            result.append(if (shouldBeUpper) char.uppercaseChar() else char.lowercaseChar())
        }
        return result.toString()
    }

    private fun updateUI() {
        updateCandidateView()
    }

    private fun updateCandidateView() {
        keyboardView?.let { view ->
            // Update composition display (radicals) with properly cased keys for English preview
            view.setComposition(composition.radicalDisplay, getDisplayKeys())

            if (composition.hasCandidates) {
                view.setCandidates(
                    candidates = composition.currentPageCandidates,
                    currentPage = composition.currentPage + 1, // 1-based for display
                    totalPages = composition.totalPages
                )
            } else if (composition.rawKeys.isNotEmpty()) {
                // Show "no match" message
                view.showNoMatch()
            } else {
                view.clearCandidates()
            }
        }
    }
}
