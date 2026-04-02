package com.example.dualquick.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.example.dualquick.R

/**
 * Custom candidate view that displays Chinese characters in a tappable grid.
 * Users TAP to select characters (no number key selection).
 * SPACE key triggers page navigation, handled by the InputMethodService.
 */
class CandidateView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private var onCandidateSelected: ((String) -> Unit)? = null
    private val candidateButtons = mutableListOf<TextView>()
    private lateinit var pageIndicator: TextView
    private lateinit var compositionText: TextView

    private val candidatesPerPage = 9

    init {
        orientation = VERTICAL
        setBackgroundColor(Color.parseColor("#1B1B1B"))

        // Composition bar (shows radicals like "手口")
        compositionText = TextView(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dpToPx(36))
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dpToPx(16), 0, dpToPx(16), 0)
            setTextColor(Color.parseColor("#4FC3F7"))
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            visibility = View.GONE
        }
        addView(compositionText)

        // Candidate row container
        val candidateRow = LinearLayout(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dpToPx(48))
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.parseColor("#2D2D2D"))
        }

        // Create candidate slots
        repeat(candidatesPerPage) {
            val btn = createCandidateButton()
            candidateButtons.add(btn)
            candidateRow.addView(btn)
        }

        // Page indicator on the right
        pageIndicator = TextView(context).apply {
            layoutParams = LayoutParams(dpToPx(48), LayoutParams.MATCH_PARENT)
            gravity = Gravity.CENTER
            textSize = 11f
            setTextColor(Color.parseColor("#888888"))
        }
        candidateRow.addView(pageIndicator)

        addView(candidateRow)
    }

    private fun createCandidateButton(): TextView {
        return TextView(context).apply {
            layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, 1f)
            gravity = Gravity.CENTER
            textSize = 20f
            setTextColor(Color.WHITE)
            setBackgroundResource(R.drawable.candidate_button_background)

            setOnClickListener {
                text?.toString()?.takeIf { it.isNotBlank() }?.let { char ->
                    onCandidateSelected?.invoke(char)
                }
            }
        }
    }

    /**
     * Set the callback for when a candidate is selected (tapped).
     */
    fun setOnCandidateSelectedListener(listener: (String) -> Unit) {
        onCandidateSelected = listener
    }

    /**
     * Update the composition display (radicals).
     */
    fun setComposition(radicals: String, rawKeys: String) {
        if (radicals.isNotEmpty()) {
            compositionText.text = "$radicals ($rawKeys)"
            compositionText.visibility = View.VISIBLE
        } else {
            compositionText.visibility = View.GONE
        }
    }

    /**
     * Update the displayed candidates and page info.
     *
     * @param candidates List of candidates for the current page
     * @param currentPage Current page number (1-based for display)
     * @param totalPages Total number of pages
     */
    fun setCandidates(candidates: List<String>, currentPage: Int, totalPages: Int) {
        // Update candidate buttons
        candidateButtons.forEachIndexed { index, button ->
            if (index < candidates.size) {
                button.text = candidates[index]
                button.visibility = View.VISIBLE
                button.isClickable = true
            } else {
                button.text = ""
                button.visibility = View.INVISIBLE
                button.isClickable = false
            }
        }

        // Update page indicator
        pageIndicator.text = if (totalPages > 1) "$currentPage/$totalPages" else ""
        pageIndicator.visibility = if (totalPages > 1) View.VISIBLE else View.GONE
    }

    /**
     * Show a "no candidates" message.
     */
    fun showNoMatch() {
        candidateButtons.forEachIndexed { index, button ->
            if (index == 0) {
                button.text = "無此字"
                button.visibility = View.VISIBLE
                button.isClickable = false
                button.setTextColor(Color.parseColor("#888888"))
            } else {
                button.text = ""
                button.visibility = View.INVISIBLE
            }
        }
        pageIndicator.visibility = View.GONE
    }

    /**
     * Reset the candidate button text color (after showing "no match").
     */
    fun resetButtonColors() {
        candidateButtons.forEach { it.setTextColor(Color.WHITE) }
    }

    /**
     * Clear all displayed candidates.
     */
    fun clear() {
        compositionText.visibility = View.GONE
        candidateButtons.forEach {
            it.text = ""
            it.visibility = View.INVISIBLE
            it.setTextColor(Color.WHITE)
        }
        pageIndicator.visibility = View.GONE
    }

    private fun dpToPx(dp: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp.toFloat(),
            resources.displayMetrics
        ).toInt()
    }
}
