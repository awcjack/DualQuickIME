package com.example.dualquick.ui

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.example.dualquick.data.EmojiData
import com.example.dualquick.theme.KeyboardColors
import com.example.dualquick.theme.ThemeManager

/**
 * Full emoji keyboard with category tabs and scrolling grid.
 */
class EmojiKeyboardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private var onEmojiSelected: ((String) -> Unit)? = null
    private var onBackspacePressed: (() -> Unit)? = null
    private var onAbcPressed: (() -> Unit)? = null

    private lateinit var colors: KeyboardColors
    private var currentCategory = 0
    private val categoryTabs = mutableListOf<TextView>()
    private var emojiGrid: GridLayout? = null
    private var emojiScrollView: ScrollView? = null

    // Category icons (emoji representations)
    private val categoryIcons = listOf("😀", "👋", "🐱", "🍔", "⚽", "🚗", "💡", "❤️", "🏳️")

    init {
        orientation = VERTICAL
        loadTheme()
        buildView()
    }

    private fun loadTheme() {
        colors = ThemeManager.getColors(context)
        setBackgroundColor(colors.keyboardBackground)
    }

    fun refreshTheme() {
        loadTheme()
        buildView()
    }

    private fun buildView() {
        removeAllViews()

        // Emoji grid area (takes most of the space)
        addView(createEmojiGridArea())

        // Category tabs at bottom
        addView(createCategoryBar())

        // Bottom row with ABC and backspace
        addView(createBottomRow())
    }

    private fun createEmojiGridArea(): FrameLayout {
        return FrameLayout(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dpToPx(180))
            setBackgroundColor(colors.keyboardBackground)

            emojiScrollView = ScrollView(context).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
                isVerticalScrollBarEnabled = false
                isFillViewport = true
            }

            emojiGrid = GridLayout(context).apply {
                layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
                columnCount = 8
                setPadding(dpToPx(4), dpToPx(4), dpToPx(4), dpToPx(4))
            }

            emojiScrollView?.addView(emojiGrid)
            addView(emojiScrollView)

            // Populate with initial category
            populateEmojis(currentCategory)
        }
    }

    private fun populateEmojis(categoryIndex: Int) {
        emojiGrid?.removeAllViews()
        emojiScrollView?.scrollTo(0, 0)

        val emojis = when (categoryIndex) {
            0 -> EmojiData.smileys
            1 -> EmojiData.gestures
            2 -> EmojiData.animals
            3 -> EmojiData.food
            4 -> EmojiData.activities
            5 -> EmojiData.travel
            6 -> EmojiData.objects
            7 -> EmojiData.symbols
            8 -> EmojiData.flags
            else -> EmojiData.smileys
        }

        emojis.forEach { emoji ->
            emojiGrid?.addView(createEmojiKey(emoji))
        }
    }

    private fun createEmojiKey(emoji: String): TextView {
        val cellSize = dpToPx(42)
        return TextView(context).apply {
            layoutParams = GridLayout.LayoutParams().apply {
                width = cellSize
                height = cellSize
                setMargins(dpToPx(2), dpToPx(2), dpToPx(2), dpToPx(2))
            }
            gravity = Gravity.CENTER
            text = emoji
            textSize = 24f
            background = createKeyBackground(colors.keyBackground, colors.keyBackgroundPressed)

            setOnClickListener {
                onEmojiSelected?.invoke(emoji)
            }
        }
    }

    private fun createCategoryBar(): HorizontalScrollView {
        return HorizontalScrollView(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dpToPx(44))
            isHorizontalScrollBarEnabled = false
            setBackgroundColor(colors.candidateBarBackground)

            val tabContainer = LinearLayout(context).apply {
                layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT)
                orientation = HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dpToPx(4), 0, dpToPx(4), 0)
            }

            categoryTabs.clear()
            categoryIcons.forEachIndexed { index, icon ->
                val tab = createCategoryTab(icon, index)
                categoryTabs.add(tab)
                tabContainer.addView(tab)
            }

            updateCategorySelection()
            addView(tabContainer)
        }
    }

    private fun createCategoryTab(icon: String, index: Int): TextView {
        return TextView(context).apply {
            layoutParams = LayoutParams(dpToPx(44), dpToPx(36)).apply {
                setMargins(dpToPx(2), dpToPx(4), dpToPx(2), dpToPx(4))
            }
            gravity = Gravity.CENTER
            text = icon
            textSize = 20f

            setOnClickListener {
                if (currentCategory != index) {
                    currentCategory = index
                    updateCategorySelection()
                    populateEmojis(index)
                }
            }
        }
    }

    private fun updateCategorySelection() {
        categoryTabs.forEachIndexed { index, tab ->
            if (index == currentCategory) {
                tab.background = createCategorySelectedBackground()
                tab.setTextColor(colors.emojiCategorySelectedText)
            } else {
                tab.background = createCategoryBackground()
                tab.setTextColor(colors.emojiCategoryText)
            }
        }
    }

    private fun createCategoryBackground(): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dpToPx(8).toFloat()
            setColor(colors.emojiCategoryBackground)
        }
    }

    private fun createCategorySelectedBackground(): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dpToPx(8).toFloat()
            setColor(colors.emojiCategorySelectedBackground)
        }
    }

    private fun createBottomRow(): LinearLayout {
        return LinearLayout(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dpToPx(50))
            orientation = HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dpToPx(4), dpToPx(2), dpToPx(4), dpToPx(6))

            // ABC button
            addView(createSpecialKey("ABC", 1.2f) {
                onAbcPressed?.invoke()
            })

            // Spacer
            addView(android.view.View(context).apply {
                layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, 4f)
            })

            // Backspace
            addView(createSpecialKey("⌫", 1.2f) {
                onBackspacePressed?.invoke()
            })
        }
    }

    private fun createSpecialKey(label: String, weight: Float, onClick: () -> Unit): TextView {
        return TextView(context).apply {
            layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, weight).apply {
                setMargins(dpToPx(3), dpToPx(2), dpToPx(3), dpToPx(2))
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

    fun setOnEmojiSelectedListener(listener: (String) -> Unit) {
        onEmojiSelected = listener
    }

    fun setOnBackspacePressedListener(listener: () -> Unit) {
        onBackspacePressed = listener
    }

    fun setOnAbcPressedListener(listener: () -> Unit) {
        onAbcPressed = listener
    }

    private fun dpToPx(dp: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp.toFloat(),
            resources.displayMetrics
        ).toInt()
    }
}
