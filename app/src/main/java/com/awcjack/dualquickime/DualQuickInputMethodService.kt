package com.awcjack.dualquickime

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

    override fun onCreate() {
        super.onCreate()
        // Load simplex.cin from assets
        try {
            simplexTable = CinParser().parse(assets.open("simplex.cin"))
        } catch (e: Exception) {
            // Fallback to empty table if loading fails
            simplexTable = SimplexTable(emptyList())
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
            setOnEnglishSelectedListener { english ->
                // User TAPPED the English pill - commit as English
                commitEnglish(english)
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
        val lowerChar = char.lowercaseChar()
        val newRawKeys = composition.rawKeys + lowerChar

        if (newRawKeys.length > 2) {
            // 3rd key: commit first 2 as English, start new composition
            commitEnglish(composition.rawKeys)
            updateComposition(lowerChar.toString())
        } else {
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
        // Then commit the clipboard text
        commitText(text)
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
        commitText(text)
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
        keyboardView?.clearCandidates()
    }

    private fun updateUI() {
        updateCandidateView()
    }

    private fun updateCandidateView() {
        keyboardView?.let { view ->
            // Update composition display (radicals)
            view.setComposition(composition.radicalDisplay, composition.rawKeys)

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
