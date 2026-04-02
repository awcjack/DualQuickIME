package com.example.dualquick.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.example.dualquick.R
import com.example.dualquick.util.KeyMapping

/**
 * Custom keyboard view displaying QWERTY layout with Chinese radicals.
 * Each key shows the English letter and its corresponding radical below.
 */
class KeyboardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private var onKeyPress: ((KeyEvent) -> Unit)? = null
    private var onModeChange: ((Boolean) -> Unit)? = null
    private var isShiftOn = false
    private var isSymbolMode = false
    private var isSymbolPage2 = false
    private var shiftKey: TextView? = null
    private var modeToggleKey: TextView? = null

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

    init {
        orientation = VERTICAL
        setBackgroundColor(Color.parseColor("#1B1B1B"))
        setPadding(dpToPx(2), dpToPx(4), dpToPx(2), dpToPx(4))

        buildKeyboard()
    }

    private fun buildKeyboard() {
        removeAllViews()

        if (isSymbolMode) {
            if (isSymbolPage2) {
                addView(createSymbolRow(symRow1Page2))
                addView(createSymbolRow(symRow2Page2, leftPadding = 0.5f))
                addView(createSymbolSpecialRow3(symRow3Page2))
            } else {
                addView(createSymbolRow(numRow1))
                addView(createSymbolRow(symRow2Page1, leftPadding = 0.5f))
                addView(createSymbolSpecialRow3(symRow3Page1))
            }
            addView(createSymbolBottomRow())
        } else {
            addView(createKeyRow(row1))
            addView(createKeyRow(row2, leftPadding = 0.5f))
            addView(createSpecialRow3())
            addView(createBottomRow())
        }
    }

    private fun createKeyRow(keys: List<Char>, leftPadding: Float = 0f): LinearLayout {
        return LinearLayout(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dpToPx(54))
            orientation = HORIZONTAL
            gravity = Gravity.CENTER

            // Left padding spacer
            if (leftPadding > 0) {
                addView(View(context).apply {
                    layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, leftPadding)
                })
            }

            // Create letter keys
            keys.forEach { char ->
                addView(createLetterKey(char))
            }

            // Right padding spacer
            if (leftPadding > 0) {
                addView(View(context).apply {
                    layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, leftPadding)
                })
            }
        }
    }

    private fun createSpecialRow3(): LinearLayout {
        return LinearLayout(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dpToPx(54))
            orientation = HORIZONTAL
            gravity = Gravity.CENTER

            // Shift key
            shiftKey = createSpecialKey("⇧", 1.5f) {
                isShiftOn = !isShiftOn
                updateShiftState()
            }
            addView(shiftKey)

            // Letter keys
            row3.forEach { char ->
                addView(createLetterKey(char))
            }

            // Backspace key
            addView(createSpecialKey("⌫", 1.5f) {
                onKeyPress?.invoke(KeyEvent.Backspace)
            }.apply {
                setOnLongClickListener {
                    // Repeat backspace on long press
                    onKeyPress?.invoke(KeyEvent.Backspace)
                    true
                }
            })
        }
    }

    private fun createBottomRow(): LinearLayout {
        return LinearLayout(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dpToPx(54))
            orientation = HORIZONTAL
            gravity = Gravity.CENTER

            // Number/Symbol toggle
            modeToggleKey = createSpecialKey("123", 1.2f) {
                isSymbolMode = true
                isSymbolPage2 = false
                onModeChange?.invoke(true)
                buildKeyboard()
            }
            addView(modeToggleKey)

            // Comma
            addView(createSpecialKey(",", 1f) {
                onKeyPress?.invoke(KeyEvent.Symbol(','))
            })

            // Space bar
            addView(createSpecialKey("Space", 4f) {
                onKeyPress?.invoke(KeyEvent.Space)
            }.apply {
                setBackgroundResource(R.drawable.space_key_background)
            })

            // Period
            addView(createSpecialKey(".", 1f) {
                onKeyPress?.invoke(KeyEvent.Symbol('.'))
            })

            // Enter key
            addView(createSpecialKey("↵", 1.2f) {
                onKeyPress?.invoke(KeyEvent.Enter)
            })
        }
    }

    private fun createSymbolRow(chars: List<Char>, leftPadding: Float = 0f): LinearLayout {
        return LinearLayout(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dpToPx(54))
            orientation = HORIZONTAL
            gravity = Gravity.CENTER

            if (leftPadding > 0) {
                addView(View(context).apply {
                    layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, leftPadding)
                })
            }

            chars.forEach { char ->
                addView(createSymbolKey(char))
            }

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
                setMargins(dpToPx(2), dpToPx(2), dpToPx(2), dpToPx(2))
            }
            gravity = Gravity.CENTER
            text = char.toString()
            textSize = 20f
            setTextColor(Color.WHITE)
            setBackgroundResource(R.drawable.key_background)

            setOnClickListener {
                if (char.isDigit()) {
                    onKeyPress?.invoke(KeyEvent.Number(char.digitToInt()))
                } else {
                    onKeyPress?.invoke(KeyEvent.Symbol(char))
                }
            }

            setOnTouchListener { v, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> v.alpha = 0.7f
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> v.alpha = 1f
                }
                false
            }
        }
    }

    private fun createSymbolSpecialRow3(chars: List<Char>): LinearLayout {
        return LinearLayout(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dpToPx(54))
            orientation = HORIZONTAL
            gravity = Gravity.CENTER

            // Page toggle key (1/2 or 2/2)
            val pageLabel = if (isSymbolPage2) "1/2" else "2/2"
            addView(createSpecialKey(pageLabel, 1.5f) {
                isSymbolPage2 = !isSymbolPage2
                buildKeyboard()
            })

            // Symbol keys
            chars.forEach { char ->
                addView(createSymbolKey(char))
            }

            // Backspace key
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
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dpToPx(54))
            orientation = HORIZONTAL
            gravity = Gravity.CENTER

            // ABC toggle (back to letters)
            addView(createSpecialKey("ABC", 1.2f) {
                isSymbolMode = false
                onModeChange?.invoke(false)
                buildKeyboard()
            })

            // Comma
            addView(createSpecialKey(",", 1f) {
                onKeyPress?.invoke(KeyEvent.Symbol(','))
            })

            // Space bar
            addView(createSpecialKey("Space", 4f) {
                onKeyPress?.invoke(KeyEvent.Space)
            }.apply {
                setBackgroundResource(R.drawable.space_key_background)
            })

            // Period
            addView(createSpecialKey(".", 1f) {
                onKeyPress?.invoke(KeyEvent.Symbol('.'))
            })

            // Enter key
            addView(createSpecialKey("↵", 1.2f) {
                onKeyPress?.invoke(KeyEvent.Enter)
            })
        }
    }

    private fun createLetterKey(char: Char): LinearLayout {
        val radical = KeyMapping.getRadical(char) ?: ""

        return LinearLayout(context).apply {
            layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, 1f).apply {
                setMargins(dpToPx(2), dpToPx(2), dpToPx(2), dpToPx(2))
            }
            orientation = VERTICAL
            gravity = Gravity.CENTER
            setBackgroundResource(R.drawable.key_background)

            // English letter (larger)
            val letterView = TextView(context).apply {
                layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f)
                gravity = Gravity.CENTER or Gravity.BOTTOM
                text = char.uppercaseChar().toString()
                textSize = 18f
                setTextColor(Color.WHITE)
                typeface = Typeface.DEFAULT_BOLD
            }
            addView(letterView)

            // Chinese radical (smaller)
            addView(TextView(context).apply {
                layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, 0, 0.7f)
                gravity = Gravity.CENTER or Gravity.TOP
                text = radical
                textSize = 12f
                setTextColor(Color.parseColor("#888888"))
            })

            // Click handler
            setOnClickListener {
                val letter = if (isShiftOn) char.uppercaseChar() else char
                onKeyPress?.invoke(KeyEvent.Letter(letter))
                // Turn off shift after one letter (like standard keyboard)
                if (isShiftOn) {
                    isShiftOn = false
                    updateShiftState()
                }
            }

            // Visual feedback
            setOnTouchListener { v, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        v.alpha = 0.7f
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        v.alpha = 1f
                    }
                }
                false
            }
        }
    }

    private fun createSpecialKey(label: String, weight: Float, onClick: () -> Unit): TextView {
        return TextView(context).apply {
            layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, weight).apply {
                setMargins(dpToPx(2), dpToPx(2), dpToPx(2), dpToPx(2))
            }
            gravity = Gravity.CENTER
            text = label
            textSize = 16f
            setTextColor(Color.WHITE)
            setBackgroundResource(R.drawable.special_key_background)

            setOnClickListener { onClick() }

            setOnTouchListener { v, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> v.alpha = 0.7f
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> v.alpha = 1f
                }
                false
            }
        }
    }

    private fun updateShiftState() {
        shiftKey?.setTextColor(
            if (isShiftOn) Color.parseColor("#4FC3F7") else Color.WHITE
        )
    }

    /**
     * Set the callback for key press events.
     */
    fun setOnKeyPressListener(listener: (KeyEvent) -> Unit) {
        onKeyPress = listener
    }

    /**
     * Set the callback for mode changes (letter/symbol mode).
     */
    fun setOnModeChangeListener(listener: (Boolean) -> Unit) {
        onModeChange = listener
    }

    /**
     * Switch back to letter mode programmatically.
     */
    fun setLetterMode() {
        if (isSymbolMode) {
            isSymbolMode = false
            isSymbolPage2 = false
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

    /**
     * Key event types emitted by the keyboard.
     */
    sealed class KeyEvent {
        data class Letter(val char: Char) : KeyEvent()
        data class Number(val digit: Int) : KeyEvent()
        data class Symbol(val char: Char) : KeyEvent()
        object Space : KeyEvent()
        object Backspace : KeyEvent()
        object Enter : KeyEvent()
    }
}
