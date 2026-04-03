package com.awcjack.dualquickime.ui

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.awcjack.dualquickime.BuildConfig
import com.awcjack.dualquickime.theme.KeyboardColors
import com.awcjack.dualquickime.theme.ThemeManager
import com.awcjack.dualquickime.util.KeyMapping

/**
 * Custom keyboard view displaying QWERTY layout with Chinese radicals.
 * Includes an embedded candidate bar at the top (Gboard-style).
 * Supports dynamic light/dark theming via ThemeManager.
 */
class KeyboardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private var onKeyPress: ((KeyEvent) -> Unit)? = null
    private var onModeChange: ((Boolean) -> Unit)? = null
    private var onCandidateSelected: ((String) -> Unit)? = null
    private var onEnglishSelected: ((String) -> Unit)? = null
    private var currentRawKeys: String = ""
    private var isShiftOn = false
    private var isCapsLock = false
    private var lastShiftTapTime = 0L
    private val DOUBLE_TAP_INTERVAL = 300L  // milliseconds
    private var isSymbolMode = false
    private var symbolPage = 0  // 0 = numbers, 1 = symbols, 2 = emoji
    private var shiftKey: TextView? = null
    private var modeToggleKey: TextView? = null

    // Current theme colors
    private lateinit var colors: KeyboardColors

    // Settings
    private var showComposition = true
    private var candidatesPerPage = 6

    // Candidate bar components (embedded, Gboard-style) - now with fixed slots
    private var candidateContainer: LinearLayout? = null
    private var compositionText: TextView? = null
    private var candidateRow: LinearLayout? = null
    private var pageIndicator: TextView? = null
    private val candidateSlots = mutableListOf<TextView>()
    private var englishPill: TextView? = null

    // Emoji keyboard view (full-featured)
    private var emojiKeyboardView: EmojiKeyboardView? = null
    private var clipboardKeyboardView: ClipboardKeyboardView? = null
    private var mainKeyboardContainer: LinearLayout? = null

    // QWERTY layout rows
    private val row1 = listOf('q', 'w', 'e', 'r', 't', 'y', 'u', 'i', 'o', 'p')
    private val row2 = listOf('a', 's', 'd', 'f', 'g', 'h', 'j', 'k', 'l')
    private val row3 = listOf('z', 'x', 'c', 'v', 'b', 'n', 'm')

    // Number/Symbol rows (page 1) - Common punctuation
    private val numRow1 = listOf('1', '2', '3', '4', '5', '6', '7', '8', '9', '0')
    private val symRow2Page1 = listOf('@', '#', '$', '%', '&', '-', '+', '(', ')')
    private val symRow3Page1 = listOf('*', '"', '\'', ':', ';', '!', '?')

    // Symbol rows (page 2) - Brackets and math
    private val symRow1Page2 = listOf('~', '`', '|', '\\', '/', '<', '>', '{', '}', '^')
    private val symRow2Page2 = listOf('[', ']', '(', ')', '「', '」', '『', '』', '【')
    private val symRow3Page2 = listOf('=', '_', '《', '》', '〈', '〉', '】')

    // Symbol rows (page 3) - Currency and units
    private val symRow1Page3 = listOf('$', '¥', '€', '£', '¢', '₩', '₹', '฿', '₱', '₽')
    private val symRow2Page3 = listOf('%', '‰', '°', '℃', '℉', '±', '×', '÷', '√')
    private val symRow3Page3 = listOf('∞', '≈', '≠', '≤', '≥', '∑', '∏')

    // Symbol rows (page 4) - Miscellaneous symbols
    private val symRow1Page4 = listOf('©', '®', '™', '℠', '†', '‡', '§', '¶', '•', '·')
    private val symRow2Page4 = listOf('…', '—', '–', '―', '‖', '¦', '※', '♪', '♫')
    private val symRow3Page4 = listOf('♠', '♣', '♥', '♦', '★', '☆', '○')

    // Symbol rows (page 5) - Arrows and shapes
    private val symRow1Page5 = listOf('←', '→', '↑', '↓', '↔', '↕', '⇐', '⇒', '⇑', '⇓')
    private val symRow2Page5 = listOf('▲', '▼', '◀', '▶', '◆', '◇', '□', '■', '△')
    private val symRow3Page5 = listOf('▽', '○', '●', '◎', '⊙', '⊕', '⊗')

    init {
        orientation = VERTICAL
        loadTheme()
        buildKeyboard()
    }

    /**
     * Load theme colors and settings from ThemeManager.
     */
    private fun loadTheme() {
        colors = ThemeManager.getColors(context)
        showComposition = ThemeManager.getShowComposition(context)
        candidatesPerPage = ThemeManager.getCandidatesPerPage(context)
        setBackgroundColor(colors.keyboardBackground)
        setPadding(dpToPx(3), dpToPx(6), dpToPx(3), dpToPx(8))
    }

    /**
     * Refresh the keyboard when theme changes.
     */
    fun refreshTheme() {
        loadTheme()
        buildKeyboard()
    }

    private fun buildKeyboard() {
        removeAllViews()

        // Check if we're in full emoji mode (page 99 is emoji mode)
        if (isSymbolMode && symbolPage == 99) {
            // Show full-featured emoji keyboard
            if (emojiKeyboardView == null) {
                emojiKeyboardView = EmojiKeyboardView(context).apply {
                    setOnEmojiSelectedListener { emoji ->
                        onKeyPress?.invoke(KeyEvent.Emoji(emoji))
                    }
                    setOnBackspacePressedListener {
                        onKeyPress?.invoke(KeyEvent.Backspace)
                    }
                    setOnAbcPressedListener {
                        isSymbolMode = false
                        symbolPage = 0
                        onModeChange?.invoke(false)
                        buildKeyboard()
                    }
                }
            }
            emojiKeyboardView?.refreshTheme()
            addView(emojiKeyboardView)
            return
        }

        // Check if we're in clipboard mode (page 100 is clipboard mode)
        if (isSymbolMode && symbolPage == 100) {
            if (clipboardKeyboardView == null) {
                clipboardKeyboardView = ClipboardKeyboardView(context).apply {
                    setOnClipboardItemSelectedListener { text ->
                        onKeyPress?.invoke(KeyEvent.ClipboardPaste(text))
                    }
                    setOnBackspacePressedListener {
                        onKeyPress?.invoke(KeyEvent.Backspace)
                    }
                    setOnAbcPressedListener {
                        isSymbolMode = false
                        symbolPage = 0
                        onModeChange?.invoke(false)
                        buildKeyboard()
                    }
                }
            }
            clipboardKeyboardView?.refreshTheme()
            clipboardKeyboardView?.refreshContent()
            addView(clipboardKeyboardView)
            return
        }

        // Always add the candidate bar at the top (Gboard-style)
        addView(createCandidateBar())

        if (isSymbolMode) {
            when (symbolPage) {
                0 -> {
                    addView(createSymbolRow(numRow1))
                    addView(createSymbolRow(symRow2Page1, leftPadding = 0.5f))
                    addView(createSymbolSpecialRow3(symRow3Page1))
                }
                1 -> {
                    addView(createSymbolRow(symRow1Page2))
                    addView(createSymbolRow(symRow2Page2, leftPadding = 0.5f))
                    addView(createSymbolSpecialRow3(symRow3Page2))
                }
                2 -> {
                    addView(createSymbolRow(symRow1Page3))
                    addView(createSymbolRow(symRow2Page3, leftPadding = 0.5f))
                    addView(createSymbolSpecialRow3(symRow3Page3))
                }
                3 -> {
                    addView(createSymbolRow(symRow1Page4))
                    addView(createSymbolRow(symRow2Page4, leftPadding = 0.5f))
                    addView(createSymbolSpecialRow3(symRow3Page4))
                }
                4 -> {
                    addView(createSymbolRow(symRow1Page5))
                    addView(createSymbolRow(symRow2Page5, leftPadding = 0.5f))
                    addView(createSymbolSpecialRow3(symRow3Page5))
                }
            }
            addView(createSymbolBottomRow())
        } else {
            addView(createKeyRow(row1))
            addView(createKeyRow(row2, leftPadding = 0.5f))
            addView(createSpecialRow3())
            addView(createBottomRow())
        }
    }

    // ==================== CANDIDATE BAR ====================

    private fun createCandidateBar(): LinearLayout {
        candidateSlots.clear()
        englishPill = null

        candidateContainer = LinearLayout(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dpToPx(46))
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(colors.candidateBarBackground)
            setPadding(dpToPx(4), dpToPx(4), dpToPx(4), dpToPx(4))
        }

        // Composition text (radicals display) - only if setting enabled
        compositionText = TextView(context).apply {
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dpToPx(8), 0, dpToPx(10), 0)
            setTextColor(colors.compositionText)
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            visibility = View.GONE
        }
        candidateContainer?.addView(compositionText)

        // English pill (first slot, shows raw keys for English commit)
        englishPill = createEnglishPillSlot()
        candidateContainer?.addView(englishPill)

        // Fixed candidate row (no scrolling)
        candidateRow = LinearLayout(context).apply {
            layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, 1f)
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        // Create fixed number of candidate slots
        for (i in 0 until candidatesPerPage) {
            val slot = createCandidatePillSlot()
            candidateSlots.add(slot)
            candidateRow?.addView(slot)
        }
        candidateContainer?.addView(candidateRow)

        // Page indicator
        pageIndicator = TextView(context).apply {
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dpToPx(6), 0, dpToPx(6), 0)
            setTextColor(colors.pageIndicatorText)
            textSize = 11f
            visibility = View.GONE
        }
        candidateContainer?.addView(pageIndicator)

        return candidateContainer!!
    }

    private fun createCandidatePillSlot(): TextView {
        return TextView(context).apply {
            layoutParams = LayoutParams(0, dpToPx(34), 1f).apply {
                setMargins(dpToPx(2), 0, dpToPx(2), 0)
            }
            gravity = Gravity.CENTER
            setPadding(dpToPx(4), dpToPx(4), dpToPx(4), dpToPx(4))
            textSize = 18f
            setTextColor(colors.candidateText)
            background = createPillBackground(colors.candidatePillBackground, colors.candidatePillBackgroundPressed)
            elevation = dpToPx(1).toFloat()
            visibility = View.INVISIBLE  // Hidden by default
        }
    }

    private fun createEnglishPillSlot(): TextView {
        return TextView(context).apply {
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, dpToPx(34)).apply {
                setMargins(dpToPx(2), 0, dpToPx(4), 0)
            }
            gravity = Gravity.CENTER
            minWidth = dpToPx(36)
            setPadding(dpToPx(10), dpToPx(4), dpToPx(10), dpToPx(4))
            textSize = 17f
            setTextColor(colors.englishPillText)
            background = createPillBackground(colors.candidatePillBackground, colors.candidatePillBackgroundPressed)
            elevation = dpToPx(1).toFloat()
            visibility = View.GONE  // Hidden by default

            setOnClickListener {
                if (currentRawKeys.isNotEmpty()) {
                    onEnglishSelected?.invoke(currentRawKeys)
                }
            }
        }
    }

    private fun createPillBackground(normalColor: Int, pressedColor: Int): StateListDrawable {
        val pressed = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dpToPx(18).toFloat()
            setColor(pressedColor)
        }
        val normal = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dpToPx(18).toFloat()
            setColor(normalColor)
        }
        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), pressed)
            addState(intArrayOf(), normal)
        }
    }

    // ==================== PUBLIC CANDIDATE METHODS ====================

    fun setComposition(radicals: String, rawKeys: String) {
        currentRawKeys = rawKeys

        // Update English pill
        englishPill?.let { pill ->
            if (rawKeys.isNotEmpty()) {
                pill.text = rawKeys
                pill.visibility = View.VISIBLE
            } else {
                pill.visibility = View.GONE
            }
        }

        // Update composition text (only if showComposition is enabled)
        compositionText?.let { tv ->
            if (showComposition && radicals.isNotEmpty()) {
                tv.text = radicals
                tv.visibility = View.VISIBLE
            } else {
                tv.visibility = View.GONE
            }
        }
    }

    fun setCandidates(candidates: List<String>, currentPage: Int, totalPages: Int) {
        // Update fixed candidate slots
        candidateSlots.forEachIndexed { index, slot ->
            if (index < candidates.size) {
                slot.text = candidates[index]
                slot.visibility = View.VISIBLE
                slot.setOnClickListener {
                    onCandidateSelected?.invoke(candidates[index])
                }
            } else {
                slot.text = ""
                slot.visibility = View.INVISIBLE
                slot.setOnClickListener(null)
            }
        }

        // Update page indicator
        pageIndicator?.let { pi ->
            if (totalPages > 1) {
                pi.text = "$currentPage/$totalPages"
                pi.visibility = View.VISIBLE
            } else {
                pi.visibility = View.GONE
            }
        }
    }

    fun showNoMatch() {
        // Clear all candidate slots
        candidateSlots.forEach { slot ->
            slot.text = ""
            slot.visibility = View.INVISIBLE
            slot.setOnClickListener(null)
        }

        // Show "無此字" in first slot
        candidateSlots.firstOrNull()?.let { slot ->
            slot.text = "無此字"
            slot.visibility = View.VISIBLE
            slot.setTextColor(colors.noMatchText)
            slot.setOnClickListener(null)
        }

        pageIndicator?.visibility = View.GONE
    }

    fun clearCandidates() {
        currentRawKeys = ""

        // Hide composition
        compositionText?.visibility = View.GONE

        // Hide English pill
        englishPill?.visibility = View.GONE

        // Clear all candidate slots
        candidateSlots.forEach { slot ->
            slot.text = ""
            slot.visibility = View.INVISIBLE
            slot.setTextColor(colors.candidateText)  // Reset color
            slot.setOnClickListener(null)
        }

        pageIndicator?.visibility = View.GONE
    }

    // ==================== KEYBOARD ROWS ====================

    private fun createKeyRow(keys: List<Char>, leftPadding: Float = 0f): LinearLayout {
        return LinearLayout(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dpToPx(58))
            orientation = HORIZONTAL
            gravity = Gravity.CENTER

            if (leftPadding > 0) {
                addView(View(context).apply {
                    layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, leftPadding)
                })
            }

            keys.forEach { char -> addView(createLetterKey(char)) }

            if (leftPadding > 0) {
                addView(View(context).apply {
                    layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, leftPadding)
                })
            }
        }
    }

    private fun createLetterKey(char: Char): LinearLayout {
        val radical = KeyMapping.getRadical(char) ?: ""

        return LinearLayout(context).apply {
            layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, 1f).apply {
                setMargins(dpToPx(3), dpToPx(4), dpToPx(3), dpToPx(4))
            }
            orientation = VERTICAL
            gravity = Gravity.CENTER
            background = createKeyBackground(colors.keyBackground, colors.keyBackgroundPressed)
            elevation = dpToPx(2).toFloat()

            // English letter
            addView(TextView(context).apply {
                layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f)
                gravity = Gravity.CENTER or Gravity.BOTTOM
                text = char.uppercaseChar().toString()
                textSize = 20f
                setTextColor(colors.keyTextPrimary)
                typeface = Typeface.DEFAULT_BOLD
                includeFontPadding = false
            })

            // Chinese radical
            addView(TextView(context).apply {
                layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, 0, 0.65f)
                gravity = Gravity.CENTER or Gravity.TOP
                text = radical
                textSize = 12f
                setTextColor(colors.keyTextSecondary)
                includeFontPadding = false
            })

            setOnClickListener {
                val letter = if (isShiftOn || isCapsLock) char.uppercaseChar() else char
                onKeyPress?.invoke(KeyEvent.Letter(letter))
                // Turn off shift after typing (but not caps lock)
                if (isShiftOn && !isCapsLock) {
                    isShiftOn = false
                    updateShiftState()
                }
            }
        }
    }

    private fun createSpecialRow3(): LinearLayout {
        return LinearLayout(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dpToPx(58))
            orientation = HORIZONTAL
            gravity = Gravity.CENTER

            shiftKey = createShiftKey {
                val currentTime = System.currentTimeMillis()

                if (isCapsLock) {
                    // If caps lock is on, single tap turns it off
                    isCapsLock = false
                    isShiftOn = false
                } else if (isShiftOn && (currentTime - lastShiftTapTime) < DOUBLE_TAP_INTERVAL) {
                    // Double tap detected - enable caps lock
                    isCapsLock = true
                    isShiftOn = true
                } else {
                    // Single tap - toggle shift
                    isShiftOn = !isShiftOn
                }

                lastShiftTapTime = currentTime
                updateShiftState()
            }
            addView(shiftKey)

            row3.forEach { char -> addView(createLetterKey(char)) }

            addView(createSpecialKey("⌫", 1.5f) {
                onKeyPress?.invoke(KeyEvent.Backspace)
            }.apply {
                setOnLongClickListener {
                    onKeyPress?.invoke(KeyEvent.Backspace)
                    true
                }
            })
        }
    }

    private fun createBottomRow(): LinearLayout {
        return LinearLayout(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dpToPx(58))
            orientation = HORIZONTAL
            gravity = Gravity.CENTER

            modeToggleKey = createSpecialKey("123", 1.2f) {
                isSymbolMode = true
                symbolPage = 0
                onModeChange?.invoke(true)
                buildKeyboard()
            }
            addView(modeToggleKey)

            addView(createSpecialKey(",", 1f) {
                onKeyPress?.invoke(KeyEvent.Symbol(','))
            })

            // Voice input button (only in full version)
            if (BuildConfig.VOICE_INPUT_ENABLED && ThemeManager.getVoiceInputEnabled(context)) {
                addView(createSpecialKey("🎤", 1f) {
                    onKeyPress?.invoke(KeyEvent.VoiceInput)
                })
            }

            addView(createSpaceKey())

            addView(createSpecialKey(".", 1f) {
                onKeyPress?.invoke(KeyEvent.Symbol('.'))
            })

            addView(createSpecialKey("↵", 1.2f) {
                onKeyPress?.invoke(KeyEvent.Enter)
            })
        }
    }

    private fun createSpaceKey(): TextView {
        return TextView(context).apply {
            layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, 4f).apply {
                setMargins(dpToPx(3), dpToPx(4), dpToPx(3), dpToPx(4))
            }
            gravity = Gravity.CENTER
            text = "space"
            textSize = 14f
            setTextColor(colors.keyTextSecondary)
            background = createKeyBackground(colors.spaceKeyBackground, colors.keyBackgroundPressed)
            elevation = dpToPx(2).toFloat()

            setOnClickListener { onKeyPress?.invoke(KeyEvent.Space) }
        }
    }

    private fun createSpecialKey(label: String, weight: Float, onClick: () -> Unit): TextView {
        return TextView(context).apply {
            layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, weight).apply {
                setMargins(dpToPx(3), dpToPx(4), dpToPx(3), dpToPx(4))
            }
            gravity = Gravity.CENTER
            text = label
            textSize = 18f
            setTextColor(colors.keyTextPrimary)
            background = createKeyBackground(colors.specialKeyBackground, colors.specialKeyBackgroundPressed)
            elevation = dpToPx(1).toFloat()

            setOnClickListener { onClick() }
        }
    }

    /**
     * Create the shift key with bolder styling.
     */
    private fun createShiftKey(onClick: () -> Unit): TextView {
        return TextView(context).apply {
            layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, 1.8f).apply {
                setMargins(dpToPx(3), dpToPx(4), dpToPx(3), dpToPx(4))
            }
            gravity = Gravity.CENTER
            text = "⇧"
            textSize = 22f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(colors.keyTextPrimary)
            background = createKeyBackground(colors.specialKeyBackground, colors.specialKeyBackgroundPressed)
            elevation = dpToPx(2).toFloat()

            setOnClickListener { onClick() }
        }
    }

    private fun createKeyBackground(normalColor: Int, pressedColor: Int): StateListDrawable {
        val cornerRadiusPx = dpToPx(12).toFloat()  // More rounded for modern look
        val pressed = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = cornerRadiusPx
            setColor(pressedColor)
        }
        val normal = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = cornerRadiusPx
            setColor(normalColor)
        }
        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), pressed)
            addState(intArrayOf(), normal)
        }
    }

    // ==================== SYMBOL/EMOJI KEYBOARD ====================

    private fun createSymbolRow(chars: List<Char>, leftPadding: Float = 0f): LinearLayout {
        return LinearLayout(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dpToPx(58))
            orientation = HORIZONTAL
            gravity = Gravity.CENTER

            if (leftPadding > 0) {
                addView(View(context).apply {
                    layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, leftPadding)
                })
            }

            chars.forEach { char -> addView(createSymbolKey(char)) }

            if (leftPadding > 0) {
                addView(View(context).apply {
                    layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, leftPadding)
                })
            }
        }
    }

    private fun createSymbolKey(char: Char): TextView {
        return TextView(context).apply {
            layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, 1f).apply {
                setMargins(dpToPx(3), dpToPx(4), dpToPx(3), dpToPx(4))
            }
            gravity = Gravity.CENTER
            text = char.toString()
            textSize = 22f
            setTextColor(colors.keyTextPrimary)
            background = createKeyBackground(colors.keyBackground, colors.keyBackgroundPressed)
            elevation = dpToPx(2).toFloat()

            setOnClickListener {
                if (char.isDigit()) {
                    onKeyPress?.invoke(KeyEvent.Number(char.digitToInt()))
                } else {
                    onKeyPress?.invoke(KeyEvent.Symbol(char))
                }
            }
        }
    }

    private fun createSymbolSpecialRow3(chars: List<Char>): LinearLayout {
        return LinearLayout(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dpToPx(58))
            orientation = HORIZONTAL
            gravity = Gravity.CENTER

            // Page button: cycles through pages 1-5
            val totalPages = 5
            val pageLabel = "${symbolPage + 1}/$totalPages"
            addView(createSpecialKey(pageLabel, 1.5f) {
                symbolPage = (symbolPage + 1) % totalPages
                buildKeyboard()
            })

            chars.forEach { char -> addView(createSymbolKey(char)) }

            addView(createSpecialKey("⌫", 1.5f) {
                onKeyPress?.invoke(KeyEvent.Backspace)
            }.apply {
                setOnLongClickListener {
                    onKeyPress?.invoke(KeyEvent.Backspace)
                    true
                }
            })
        }
    }

    private fun createSymbolBottomRow(): LinearLayout {
        return LinearLayout(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dpToPx(58))
            orientation = HORIZONTAL
            gravity = Gravity.CENTER

            addView(createSpecialKey("ABC", 1.2f) {
                isSymbolMode = false
                symbolPage = 0
                onModeChange?.invoke(false)
                buildKeyboard()
            })

            // Emoji button to switch to full emoji keyboard
            addView(createSpecialKey("😀", 1f) {
                symbolPage = 99  // Emoji mode
                buildKeyboard()
            })

            // Clipboard button
            addView(createSpecialKey("📋", 1f) {
                symbolPage = 100  // Clipboard mode
                buildKeyboard()
            })

            // Voice input button (only in full version)
            if (BuildConfig.VOICE_INPUT_ENABLED && ThemeManager.getVoiceInputEnabled(context)) {
                addView(createSpecialKey("🎤", 1f) {
                    onKeyPress?.invoke(KeyEvent.VoiceInput)
                })
            }

            addView(createSpaceKey())

            addView(createSpecialKey("↵", 1.2f) {
                onKeyPress?.invoke(KeyEvent.Enter)
            })
        }
    }

    private fun updateShiftState() {
        shiftKey?.apply {
            when {
                isCapsLock -> {
                    // Caps lock: filled arrow with underline, accent color
                    text = "⇧̲"  // Shift with combining underline
                    setTextColor(colors.compositionText)
                    background = createKeyBackground(colors.compositionText, colors.specialKeyBackgroundPressed)
                    setTextColor(colors.keyBackground)  // Inverted color for visibility
                }
                isShiftOn -> {
                    // Shift on: accent color
                    text = "⇧"
                    setTextColor(colors.compositionText)
                    background = createKeyBackground(colors.specialKeyBackground, colors.specialKeyBackgroundPressed)
                }
                else -> {
                    // Shift off: normal
                    text = "⇧"
                    setTextColor(colors.keyTextPrimary)
                    background = createKeyBackground(colors.specialKeyBackground, colors.specialKeyBackgroundPressed)
                }
            }
        }
    }

    // ==================== PUBLIC LISTENERS ====================

    fun setOnKeyPressListener(listener: (KeyEvent) -> Unit) {
        onKeyPress = listener
    }

    fun setOnModeChangeListener(listener: (Boolean) -> Unit) {
        onModeChange = listener
    }

    fun setOnCandidateSelectedListener(listener: (String) -> Unit) {
        onCandidateSelected = listener
    }

    fun setOnEnglishSelectedListener(listener: (String) -> Unit) {
        onEnglishSelected = listener
    }

    fun setLetterMode() {
        if (isSymbolMode) {
            isSymbolMode = false
            symbolPage = 0
            buildKeyboard()
        }
    }

    private fun dpToPx(dp: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp.toFloat(),
            resources.displayMetrics
        ).toInt()
    }

    sealed class KeyEvent {
        data class Letter(val char: Char) : KeyEvent()
        data class Number(val digit: Int) : KeyEvent()
        data class Symbol(val char: Char) : KeyEvent()
        data class Emoji(val emoji: String) : KeyEvent()
        data class ClipboardPaste(val text: String) : KeyEvent()
        object Space : KeyEvent()
        object Backspace : KeyEvent()
        object Enter : KeyEvent()
        object VoiceInput : KeyEvent()
    }
}
