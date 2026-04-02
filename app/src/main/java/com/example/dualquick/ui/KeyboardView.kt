package com.example.dualquick.ui

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import com.example.dualquick.theme.KeyboardColors
import com.example.dualquick.theme.ThemeManager
import com.example.dualquick.util.KeyMapping

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
    private var isSymbolMode = false
    private var symbolPage = 0  // 0 = numbers, 1 = symbols, 2 = emoji
    private var shiftKey: TextView? = null
    private var modeToggleKey: TextView? = null

    // Current theme colors
    private lateinit var colors: KeyboardColors

    // Candidate bar components (embedded, Gboard-style)
    private var candidateContainer: LinearLayout? = null
    private var compositionText: TextView? = null
    private var candidateScrollView: HorizontalScrollView? = null
    private var candidateRow: LinearLayout? = null
    private var pageIndicator: TextView? = null
    private val candidateButtons = mutableListOf<TextView>()

    // QWERTY layout rows
    private val row1 = listOf('q', 'w', 'e', 'r', 't', 'y', 'u', 'i', 'o', 'p')
    private val row2 = listOf('a', 's', 'd', 'f', 'g', 'h', 'j', 'k', 'l')
    private val row3 = listOf('z', 'x', 'c', 'v', 'b', 'n', 'm')

    // Number/Symbol rows (page 1)
    private val numRow1 = listOf('1', '2', '3', '4', '5', '6', '7', '8', '9', '0')
    private val symRow2Page1 = listOf('@', '#', '$', '%', '&', '-', '+', '(', ')')
    private val symRow3Page1 = listOf('*', '"', '\'', ':', ';', '!', '?')

    // Symbol rows (page 2)
    private val symRow1Page2 = listOf('~', '`', '|', '·', '√', 'π', '÷', '×', '¶', '∆')
    private val symRow2Page2 = listOf('£', '¥', '€', '¢', '^', '°', '=', '{', '}')
    private val symRow3Page2 = listOf('\\', '©', '®', '™', '℅', '[', ']')

    // Emoji rows (page 3)
    private val emojiRow1 = listOf("😀", "😂", "🥹", "😍", "🥰", "😘", "😎", "🤔", "😢", "😭")
    private val emojiRow2 = listOf("👍", "👎", "👏", "🙏", "💪", "❤️", "🔥", "✨", "🎉")
    private val emojiRow3 = listOf("👋", "✌️", "🤝", "💯", "⭐", "💀", "🤡")

    init {
        orientation = VERTICAL
        loadTheme()
        buildKeyboard()
    }

    /**
     * Load theme colors from ThemeManager.
     */
    private fun loadTheme() {
        colors = ThemeManager.getColors(context)
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
                    addView(createEmojiRow(emojiRow1))
                    addView(createEmojiRow(emojiRow2, leftPadding = 0.5f))
                    addView(createEmojiSpecialRow3(emojiRow3))
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
        candidateButtons.clear()

        candidateContainer = LinearLayout(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dpToPx(46))
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(colors.candidateBarBackground)
            setPadding(dpToPx(8), dpToPx(4), dpToPx(8), dpToPx(4))
        }

        // Composition text (radicals display)
        compositionText = TextView(context).apply {
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dpToPx(10), 0, dpToPx(14), 0)
            setTextColor(colors.compositionText)
            textSize = 17f
            typeface = Typeface.DEFAULT_BOLD
            visibility = View.GONE
        }
        candidateContainer?.addView(compositionText)

        // Horizontal scroll view for candidates
        candidateScrollView = HorizontalScrollView(context).apply {
            layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, 1f)
            isHorizontalScrollBarEnabled = false
            isFillViewport = true
        }

        candidateRow = LinearLayout(context).apply {
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT)
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        candidateScrollView?.addView(candidateRow)
        candidateContainer?.addView(candidateScrollView)

        // Page indicator
        pageIndicator = TextView(context).apply {
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dpToPx(10), 0, dpToPx(6), 0)
            setTextColor(colors.pageIndicatorText)
            textSize = 12f
            visibility = View.GONE
        }
        candidateContainer?.addView(pageIndicator)

        return candidateContainer!!
    }

    private fun createCandidatePill(text: String): TextView {
        return TextView(context).apply {
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, dpToPx(34)).apply {
                setMargins(dpToPx(4), 0, dpToPx(4), 0)
            }
            gravity = Gravity.CENTER
            minWidth = dpToPx(44)
            setPadding(dpToPx(14), dpToPx(6), dpToPx(14), dpToPx(6))
            this.text = text
            textSize = 19f
            setTextColor(colors.candidateText)
            background = createPillBackground(colors.candidatePillBackground, colors.candidatePillBackgroundPressed)
            elevation = dpToPx(1).toFloat()

            setOnClickListener {
                this.text?.toString()?.takeIf { it.isNotBlank() }?.let { char ->
                    onCandidateSelected?.invoke(char)
                }
            }
        }
    }

    private fun createEnglishPill(text: String): TextView {
        return TextView(context).apply {
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, dpToPx(34)).apply {
                setMargins(dpToPx(4), 0, dpToPx(4), 0)
            }
            gravity = Gravity.CENTER
            minWidth = dpToPx(44)
            setPadding(dpToPx(14), dpToPx(6), dpToPx(14), dpToPx(6))
            this.text = text
            textSize = 19f
            setTextColor(colors.englishPillText)
            background = createPillBackground(colors.candidatePillBackground, colors.candidatePillBackgroundPressed)
            elevation = dpToPx(1).toFloat()

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
        compositionText?.let { tv ->
            if (radicals.isNotEmpty()) {
                tv.text = radicals
                tv.visibility = View.VISIBLE
            } else {
                tv.visibility = View.GONE
            }
        }
    }

    fun setCandidates(candidates: List<String>, currentPage: Int, totalPages: Int) {
        candidateRow?.removeAllViews()
        candidateButtons.clear()
        candidateScrollView?.scrollTo(0, 0)

        if (currentRawKeys.isNotEmpty()) {
            candidateRow?.addView(createEnglishPill(currentRawKeys))
        }

        candidates.forEach { candidate ->
            val pill = createCandidatePill(candidate)
            candidateButtons.add(pill)
            candidateRow?.addView(pill)
        }

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
        candidateRow?.removeAllViews()
        candidateButtons.clear()

        if (currentRawKeys.isNotEmpty()) {
            candidateRow?.addView(createEnglishPill(currentRawKeys))
        }

        val noMatchText = TextView(context).apply {
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dpToPx(10), 0, dpToPx(10), 0)
            text = "無此字"
            textSize = 14f
            setTextColor(colors.noMatchText)
        }
        candidateRow?.addView(noMatchText)
        pageIndicator?.visibility = View.GONE
    }

    fun clearCandidates() {
        currentRawKeys = ""
        compositionText?.visibility = View.GONE
        candidateRow?.removeAllViews()
        candidateButtons.clear()
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
                val letter = if (isShiftOn) char.uppercaseChar() else char
                onKeyPress?.invoke(KeyEvent.Letter(letter))
                if (isShiftOn) {
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

            shiftKey = createSpecialKey("⇧", 1.5f) {
                isShiftOn = !isShiftOn
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
            textSize = 16f
            setTextColor(colors.keyTextPrimary)
            background = createKeyBackground(colors.specialKeyBackground, colors.specialKeyBackgroundPressed)
            elevation = dpToPx(1).toFloat()

            setOnClickListener { onClick() }
        }
    }

    private fun createKeyBackground(normalColor: Int, pressedColor: Int): StateListDrawable {
        val pressed = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dpToPx(10).toFloat()
            setColor(pressedColor)
        }
        val normal = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dpToPx(10).toFloat()
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

            val pageLabel = "${symbolPage + 1}/3"
            addView(createSpecialKey(pageLabel, 1.5f) {
                symbolPage = (symbolPage + 1) % 3
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

    private fun createEmojiRow(emojis: List<String>, leftPadding: Float = 0f): LinearLayout {
        return LinearLayout(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dpToPx(58))
            orientation = HORIZONTAL
            gravity = Gravity.CENTER

            if (leftPadding > 0) {
                addView(View(context).apply {
                    layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, leftPadding)
                })
            }

            emojis.forEach { emoji -> addView(createEmojiKey(emoji)) }

            if (leftPadding > 0) {
                addView(View(context).apply {
                    layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, leftPadding)
                })
            }
        }
    }

    private fun createEmojiKey(emoji: String): TextView {
        return TextView(context).apply {
            layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, 1f).apply {
                setMargins(dpToPx(3), dpToPx(4), dpToPx(3), dpToPx(4))
            }
            gravity = Gravity.CENTER
            text = emoji
            textSize = 26f
            background = createKeyBackground(colors.keyBackground, colors.keyBackgroundPressed)
            elevation = dpToPx(2).toFloat()

            setOnClickListener {
                onKeyPress?.invoke(KeyEvent.Emoji(emoji))
            }
        }
    }

    private fun createEmojiSpecialRow3(emojis: List<String>): LinearLayout {
        return LinearLayout(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dpToPx(58))
            orientation = HORIZONTAL
            gravity = Gravity.CENTER

            val pageLabel = "${symbolPage + 1}/3"
            addView(createSpecialKey(pageLabel, 1.5f) {
                symbolPage = (symbolPage + 1) % 3
                buildKeyboard()
            })

            emojis.forEach { emoji -> addView(createEmojiKey(emoji)) }

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
                onModeChange?.invoke(false)
                buildKeyboard()
            })

            addView(createSpecialKey(",", 1f) {
                onKeyPress?.invoke(KeyEvent.Symbol(','))
            })

            addView(createSpaceKey())

            addView(createSpecialKey(".", 1f) {
                onKeyPress?.invoke(KeyEvent.Symbol('.'))
            })

            addView(createSpecialKey("↵", 1.2f) {
                onKeyPress?.invoke(KeyEvent.Enter)
            })
        }
    }

    private fun updateShiftState() {
        shiftKey?.setTextColor(
            if (isShiftOn) colors.compositionText else colors.keyTextPrimary
        )
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
        object Space : KeyEvent()
        object Backspace : KeyEvent()
        object Enter : KeyEvent()
    }
}
