package com.awcjack.dualquickime

import android.Manifest
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import com.awcjack.dualquickime.data.AssociatedPhrasesParser
import com.awcjack.dualquickime.data.AssociatedPhrasesTable
import com.awcjack.dualquickime.data.CinParser
import com.awcjack.dualquickime.data.ClipboardHistoryManager
import com.awcjack.dualquickime.data.CompositionState
import com.awcjack.dualquickime.data.RecentCandidateManager
import com.awcjack.dualquickime.data.SimplexTable
import com.awcjack.dualquickime.theme.ThemeManager
import com.awcjack.dualquickime.ui.KeyboardView
import com.awcjack.dualquickime.ui.VoiceInputView
import com.awcjack.dualquickime.voice.ModelDownloadManager
import com.awcjack.dualquickime.voice.VoiceInputManager
import com.awcjack.dualquickime.voice.VoiceModelType
import kotlin.concurrent.thread

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
    private lateinit var associatedPhrasesTable: AssociatedPhrasesTable
    private var composition = CompositionState.EMPTY

    private var keyboardView: KeyboardView? = null

    // Associated phrases mode: when true, candidate bar shows associated phrases
    private var isAssociatedPhrasesMode = false
    private var associatedPhrases = listOf<String>()
    private var associatedPhrasesPage = 0
    private var associatedPhrasesOffset = 0  // Actual start index for dynamic pagination
    private var associatedPhrasesDisplayedCount = 0  // How many were displayed on current page
    private var lastCommittedChar = ""

    // Track current keyboard mode
    private var isSymbolMode = false

    // Track original case of letters for English output
    // Maps index position to whether it was uppercase
    private var letterCases = mutableListOf<Boolean>()

    // Track which character set is currently loaded
    private var currentCharsetExtended: Boolean? = null

    // System clipboard manager and listener
    private var clipboardManager: ClipboardManager? = null

    // Voice input components
    private var voiceInputManager: VoiceInputManager? = null
    private var voiceInputView: VoiceInputView? = null
    private var rootContainer: FrameLayout? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val clipboardListener = ClipboardManager.OnPrimaryClipChangedListener {
        handleSystemClipboardChange()
    }

    override fun onCreate() {
        super.onCreate()
        // Load simplex data based on user setting (extended by default)
        loadSimplexTable()
        // Load associated phrases table
        loadAssociatedPhrasesTable()

        // Register clipboard listener to capture system clipboard changes
        clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboardManager?.addPrimaryClipChangedListener(clipboardListener)
    }

    /**
     * Load the simplex table based on user's character set preference.
     */
    private fun loadSimplexTable() {
        val filename = ThemeManager.getSimplexFilename(this)
        try {
            simplexTable = CinParser().parse(assets.open(filename))
        } catch (e: Exception) {
            // Fallback to empty table if loading fails
            simplexTable = SimplexTable(emptyList())
        }
    }

    /**
     * Reload the simplex table (call when character set setting changes).
     */
    fun reloadSimplexTable() {
        loadSimplexTable()
        // Clear current composition since character mappings may have changed
        clearComposition()
    }

    /**
     * Load the associated phrases table from assets.
     */
    private fun loadAssociatedPhrasesTable() {
        try {
            associatedPhrasesTable = AssociatedPhrasesParser().parse(assets.open("associated-phrases.cin"))
        } catch (e: Exception) {
            // Fallback to empty table if loading fails
            associatedPhrasesTable = AssociatedPhrasesTable.EMPTY
        }
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
        // Create root container to hold keyboard and voice input overlay
        rootContainer = FrameLayout(this)

        keyboardView = KeyboardView(this).apply {
            setOnKeyPressListener { event ->
                handleKeyEvent(event)
            }
            setOnModeChangeListener { symbolMode ->
                isSymbolMode = symbolMode
            }
            setOnCandidateSelectedListener { candidate ->
                if (isAssociatedPhrasesMode) {
                    // User TAPPED an associated phrase - commit it
                    handleAssociatedPhraseSelected(candidate)
                } else {
                    // User TAPPED a Chinese candidate pill - commit Chinese character
                    // Consume only the active segment (first 1-2 chars) from the buffer
                    val consumed = composition.activeKeyLength
                    val remaining = composition.rawKeys.drop(consumed)
                    val remainingCases = letterCases.drop(consumed).toMutableList()

                    // Record recent candidate usage before composition state changes
                    val lookupCode = composition.rawKeys.take(consumed)
                    if (ThemeManager.getRecentCandidatesEnabled(this@DualQuickInputMethodService) && lookupCode.isNotEmpty()) {
                        RecentCandidateManager.recordUsage(this@DualQuickInputMethodService, lookupCode, candidate)
                    }

                    if (remaining.isNotEmpty()) {
                        // Commit character and continue with remaining buffer
                        commitText(candidate)
                        letterCases = remainingCases
                        // Close candidate grid if open so it doesn't show stale candidates
                        keyboardView?.closeCandidateGrid()
                        updateComposition(remaining)
                    } else {
                        // Buffer fully consumed - commit and show associated phrases
                        clearComposition()
                        commitChinese(candidate)
                    }
                }
            }
            setOnEnglishSelectedListener { _ ->
                // User TAPPED the English pill - commit as English with preserved case
                commitEnglishPreservingCase(composition.rawKeys, letterCases)
                clearComposition()
            }
            setOnPageIndicatorClickedListener {
                handlePageIndicatorClicked()
            }
            setOnCandidateRefreshRequestedListener {
                // Refresh candidate view when returning from symbol/emoji/clipboard/grid mode
                updateCandidateView()
            }
        }
        rootContainer?.addView(keyboardView)

        // Create voice input overlay (initially hidden)
        voiceInputView = VoiceInputView(this).apply {
            setOnCancelListener {
                // Cancel: close voice input without committing
                closeVoiceInput()
            }
            setOnResetListener {
                // Reset: clear the pending text but keep listening
                clearVoiceTranscript()
            }
            setOnCommitListener { text ->
                // Commit: commit the text and close voice input
                commitVoiceText(text)
            }
        }
        rootContainer?.addView(voiceInputView)

        return rootContainer!!
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
        RecentCandidateManager.invalidateCache()

        // Check if character set setting changed - reload if needed
        val useExtended = ThemeManager.getUseExtendedCharset(this)
        if (currentCharsetExtended != useExtended) {
            currentCharsetExtended = useExtended
            loadSimplexTable()
        }

        // Refresh theme in case it changed in settings
        keyboardView?.refreshTheme()
        // Clear composition when starting new input
        clearComposition()
        // Clear associated phrases mode
        clearAssociatedPhrases()
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
            KeyboardView.KeyEvent.VoiceInput -> handleVoiceInput()
        }
    }

    private fun handleLetter(char: Char) {
        // Exit associated phrases mode when typing
        if (isAssociatedPhrasesMode) {
            clearAssociatedPhrases()
        }

        val isUpperCase = char.isUpperCase()
        val lowerChar = char.lowercaseChar()
        val newRawKeys = composition.rawKeys + lowerChar

        // Accumulate all letters in the buffer without auto-committing.
        // Candidates are shown for the first 1-2 chars of the buffer.
        letterCases.add(isUpperCase)
        updateComposition(newRawKeys)
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
        // Commit the clipboard text
        commitText(text)
    }

    private fun handleSpace() {
        if (isAssociatedPhrasesMode) {
            // Navigate to next page of associated phrases
            nextAssociatedPhrasesPage()
        } else if (composition.hasCandidates) {
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
        // Exit associated phrases mode on backspace
        if (isAssociatedPhrasesMode) {
            clearAssociatedPhrases()
            // Also delete the character in text field (grapheme-aware)
            deleteOneGrapheme()
            return
        }

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
            // Delete character in text field (grapheme-aware for emojis)
            deleteOneGrapheme()
        }
    }

    /**
     * Delete one grapheme cluster (visual character) before the cursor.
     * This handles emojis with skin tones, ZWJ sequences, and other multi-codepoint characters.
     */
    private fun deleteOneGrapheme() {
        val ic = currentInputConnection ?: return

        // Get text before cursor (enough to cover longest emoji sequences)
        val textBefore = ic.getTextBeforeCursor(32, 0)?.toString() ?: return
        if (textBefore.isEmpty()) return

        // Use BreakIterator to find grapheme cluster boundaries
        val breakIterator = android.icu.text.BreakIterator.getCharacterInstance()
        breakIterator.setText(textBefore)

        // Find the last grapheme cluster boundary
        var end = breakIterator.last()
        var start = breakIterator.previous()

        if (start == android.icu.text.BreakIterator.DONE) {
            // Only one grapheme cluster, delete all
            start = 0
        }

        // Calculate how many UTF-16 code units to delete
        val charsToDelete = textBefore.length - start

        if (charsToDelete > 0) {
            ic.deleteSurroundingText(charsToDelete, 0)
        }
    }

    private fun handleEnter() {
        // Exit associated phrases mode on enter
        if (isAssociatedPhrasesMode) {
            clearAssociatedPhrases()
        }

        if (composition.rawKeys.isNotEmpty()) {
            // Commit composition as English
            commitEnglish(composition.rawKeys)
            clearComposition()
        }
        // Send enter key event
        sendDownUpKeyEvents(android.view.KeyEvent.KEYCODE_ENTER)
    }

    private fun commitChinese(text: String) {
        // Record usage for recent candidates feature
        if (ThemeManager.getRecentCandidatesEnabled(this) && composition.rawKeys.isNotEmpty()) {
            RecentCandidateManager.recordUsage(this, composition.rawKeys, text)
        }
        commitText(text)
        // Trigger associated phrases lookup based on the last character committed
        showAssociatedPhrases(text.lastOrNull()?.toString() ?: "")
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

    private fun commitText(text: String) {
        currentInputConnection?.commitText(text, 1)
    }

    // ==================== ASSOCIATED PHRASES ====================

    /**
     * Show associated phrases for the given character.
     * This is called after committing a Chinese character.
     */
    private fun showAssociatedPhrases(character: String) {
        if (character.isEmpty()) {
            clearAssociatedPhrases()
            return
        }

        val phrases = associatedPhrasesTable.lookup(character)
        if (phrases.isEmpty()) {
            clearAssociatedPhrases()
            return
        }

        // Enter associated phrases mode
        isAssociatedPhrasesMode = true
        associatedPhrases = phrases
        associatedPhrasesPage = 0
        associatedPhrasesOffset = 0
        associatedPhrasesDisplayedCount = 0
        lastCommittedChar = character

        updateAssociatedPhrasesView()
    }

    /**
     * Clear associated phrases mode and return to normal input.
     */
    private fun clearAssociatedPhrases() {
        isAssociatedPhrasesMode = false
        associatedPhrases = emptyList()
        associatedPhrasesPage = 0
        associatedPhrasesOffset = 0
        associatedPhrasesDisplayedCount = 0
        lastCommittedChar = ""
        keyboardView?.clearCandidates()
    }

    /**
     * Update the candidate bar to show associated phrases.
     * Uses dynamic pagination based on what fits on screen.
     */
    private fun updateAssociatedPhrasesView() {
        val pageSize = ThemeManager.getCandidatesPerPage(this)
        val totalPages = (associatedPhrases.size + pageSize - 1) / pageSize
        // Use dynamic offset instead of fixed page calculation
        val startIndex = associatedPhrasesOffset
        val endIndex = minOf(startIndex + pageSize, associatedPhrases.size)
        val currentPagePhrases = if (startIndex < associatedPhrases.size) {
            associatedPhrases.subList(startIndex, endIndex)
        } else {
            emptyList()
        }

        keyboardView?.let { view ->
            // Clear composition display since we're showing associated phrases
            view.setComposition("", "")

            // Show associated phrases in candidate bar
            val displayedCount = view.setCandidates(
                candidates = currentPagePhrases,
                currentPage = associatedPhrasesPage + 1,
                totalPages = totalPages
            )
            // Track how many were actually displayed for dynamic pagination
            associatedPhrasesDisplayedCount = displayedCount
        }
    }

    /**
     * Handle associated phrase selection.
     */
    private fun handleAssociatedPhraseSelected(phrase: String) {
        commitText(phrase)
        // Show associated phrases for the last character of the selected phrase
        val lastChar = phrase.lastOrNull()?.toString() ?: ""
        showAssociatedPhrases(lastChar)
    }

    /**
     * Navigate to the next page of associated phrases.
     * Uses dynamic offset based on how many were actually displayed.
     */
    private fun nextAssociatedPhrasesPage() {
        // Move offset by the number of phrases that were actually displayed
        val nextOffset = associatedPhrasesOffset + associatedPhrasesDisplayedCount.coerceAtLeast(1)
        if (nextOffset >= associatedPhrases.size) {
            // Wrap to beginning
            associatedPhrasesPage = 0
            associatedPhrasesOffset = 0
        } else {
            associatedPhrasesPage++
            associatedPhrasesOffset = nextOffset
        }
        updateAssociatedPhrasesView()
    }

    private fun updateComposition(rawKeys: String) {
        val pageSize = ThemeManager.getCandidatesPerPage(this)

        // Look up candidates for the first 1-2 chars of the buffer.
        // Try 2-char code first, fall back to 1-char if no match.
        var lookupKeys = rawKeys.take(2)
        var candidates = simplexTable.lookup(lookupKeys)

        if (candidates.isEmpty() && lookupKeys.length == 2) {
            lookupKeys = rawKeys.take(1)
            candidates = simplexTable.lookup(lookupKeys)
        }

        // Reorder candidates based on recent usage if enabled
        if (ThemeManager.getRecentCandidatesEnabled(this)) {
            candidates = RecentCandidateManager.reorderCandidates(this, lookupKeys, candidates)
        }

        composition = CompositionState(
            rawKeys = rawKeys,
            candidates = candidates,
            currentPage = 0,
            pageSize = pageSize,
            activeKeyLength = if (candidates.isNotEmpty()) lookupKeys.length else minOf(rawKeys.length, 2)
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
                val displayedCount = view.setCandidates(
                    candidates = composition.currentPageCandidates,
                    currentPage = composition.currentPage + 1, // 1-based for display
                    totalPages = composition.totalPages
                )
                // Track how many candidates were actually displayed for dynamic pagination
                composition = composition.withDisplayedCount(displayedCount)
            } else if (composition.rawKeys.isNotEmpty()) {
                // Show "no match" message
                view.showNoMatch()
            } else {
                view.clearCandidates()
            }
        }
    }

    // ==================== VIEW ALL CANDIDATES ====================

    /**
     * Handle clicking the page indicator to show all candidates in a full grid view.
     */
    private fun handlePageIndicatorClicked() {
        val allCandidates = if (isAssociatedPhrasesMode) {
            associatedPhrases
        } else {
            composition.candidates
        }

        if (allCandidates.isEmpty()) return

        keyboardView?.showCandidateGrid(allCandidates)
    }

    // ==================== VOICE INPUT ====================

    private fun handleVoiceInput() {
        // Check if voice input is enabled in settings
        if (!ThemeManager.getVoiceInputEnabled(this)) {
            return
        }

        // Get the user's selected model type
        val selectedModelType = VoiceModelType.fromId(ThemeManager.getVoiceModelType(this))

        // Check if the selected model is downloaded
        if (!ModelDownloadManager.isModelDownloaded(this, selectedModelType)) {
            // Start model download for the selected model
            startModelDownload(selectedModelType)
            return
        }

        // Check audio permission
        if (!hasAudioPermission()) {
            // Open settings to request permission (IME can't directly request permissions)
            requestAudioPermission()
            return
        }

        // Start voice recognition
        startVoiceRecognition()
    }

    private fun hasAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestAudioPermission() {
        // IME services cannot directly request runtime permissions
        // Open the app settings activity which can request the permission
        try {
            val intent = Intent(this, SettingsActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra("request_audio_permission", true)
            }
            startActivity(intent)
        } catch (e: Exception) {
            voiceInputView?.setState(VoiceInputView.State.ERROR)
            voiceInputView?.setErrorMessage(getString(R.string.voice_permission_required))
        }
    }

    private fun startModelDownload(modelType: VoiceModelType) {
        voiceInputView?.setState(VoiceInputView.State.DOWNLOADING)
        voiceInputView?.setDownloadProgress(0, getString(R.string.voice_download_starting))

        ModelDownloadManager.downloadModel(this, modelType, object : ModelDownloadManager.DownloadCallback {
            override fun onProgress(bytesDownloaded: Long, totalBytes: Long, currentFile: String) {
                val progress = ((bytesDownloaded.toFloat() / totalBytes) * 100).toInt()
                val mbDownloaded = bytesDownloaded / 1_000_000
                val mbTotal = totalBytes / 1_000_000
                val message = "$mbDownloaded / $mbTotal MB"

                mainHandler.post {
                    voiceInputView?.setDownloadProgress(progress, message)
                }
            }

            override fun onComplete() {
                mainHandler.post {
                    voiceInputView?.setState(VoiceInputView.State.HIDDEN)
                    // After download complete, check permission and start
                    if (hasAudioPermission()) {
                        startVoiceRecognition()
                    } else {
                        requestAudioPermission()
                    }
                }
            }

            override fun onError(message: String) {
                mainHandler.post {
                    voiceInputView?.setState(VoiceInputView.State.ERROR)
                    voiceInputView?.setErrorMessage(message)
                }
            }
        })
    }

    private fun startVoiceRecognition() {
        // Get the user's selected model type
        val selectedModelType = VoiceModelType.fromId(ThemeManager.getVoiceModelType(this))

        // Initialize voice input manager if needed
        if (voiceInputManager == null) {
            voiceInputManager = VoiceInputManager(this)
        }

        val manager = voiceInputManager ?: return

        // Set the model type before initializing
        manager.setModelType(selectedModelType)

        // Initialize recognizer on background thread
        thread {
            val initialized = manager.initialize()

            mainHandler.post {
                if (!initialized) {
                    voiceInputView?.setState(VoiceInputView.State.ERROR)
                    voiceInputView?.setErrorMessage(getString(R.string.voice_init_failed))
                    return@post
                }

                // Set up callbacks
                manager.setOnResultListener { text, _ ->
                    mainHandler.post {
                        // Only update the transcript display, don't auto-commit
                        voiceInputView?.setTranscript(text)
                    }
                }

                manager.setOnErrorListener { error ->
                    mainHandler.post {
                        voiceInputView?.setState(VoiceInputView.State.ERROR)
                        voiceInputView?.setErrorMessage(error)
                    }
                }

                // Start recording
                if (manager.startRecording()) {
                    voiceInputView?.setState(VoiceInputView.State.LISTENING)
                } else {
                    voiceInputView?.setState(VoiceInputView.State.ERROR)
                    voiceInputView?.setErrorMessage(getString(R.string.voice_start_failed))
                }
            }
        }
    }

    /**
     * Close voice input without committing any pending text.
     */
    private fun closeVoiceInput() {
        voiceInputManager?.stopRecording()
        voiceInputView?.setState(VoiceInputView.State.HIDDEN)
    }

    /**
     * Clear the pending transcript but keep listening.
     */
    private fun clearVoiceTranscript() {
        voiceInputView?.clearTranscript()
        voiceInputManager?.clearAccumulatedText()
    }

    /**
     * Commit the recognized voice text and close voice input.
     */
    private fun commitVoiceText(text: String) {
        if (text.isNotEmpty()) {
            commitText(text)
        }
        closeVoiceInput()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Release voice input resources
        voiceInputManager?.release()
        voiceInputManager = null
        // Unregister clipboard listener to avoid memory leaks
        clipboardManager?.removePrimaryClipChangedListener(clipboardListener)
    }
}
