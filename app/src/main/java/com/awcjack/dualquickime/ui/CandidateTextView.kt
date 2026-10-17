package com.awcjack.dualquickime.ui

import android.content.Context
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextUtils
import android.text.style.RelativeSizeSpan
import android.widget.TextView

/** Centered reading and character lines, each kept inside the actual cell. */
class CandidateTextView(context: Context) : TextView(context) {
    private var fullLabel: CharSequence = ""

    fun bindLabel(label: CharSequence) {
        fullLabel = label
        maxLines = if (label.contains('\n')) 2 else 1
        text = label
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = if (MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.UNSPECIFIED) maxWidth
            else minOf(maxWidth, MeasureSpec.getSize(widthMeasureSpec))
        val contentWidth = (width - compoundPaddingLeft - compoundPaddingRight).coerceAtLeast(0).toFloat()
        val newline = fullLabel.indexOf('\n')
        val fitted = if (newline < 0) {
            TextUtils.ellipsize(fullLabel, paint, contentWidth, TextUtils.TruncateAt.END, false, null)
        } else {
            val reading = TextUtils.ellipsize(fullLabel.subSequence(0, newline), paint,
                contentWidth, TextUtils.TruncateAt.END, false, null)
            val characters = TextUtils.ellipsize(fullLabel.subSequence(newline + 1, fullLabel.length), paint,
                contentWidth, TextUtils.TruncateAt.END, false, null)
            SpannableStringBuilder().append(reading).append('\n').append(characters).apply {
                // Preserve the small reading's metrics through its newline.
                getSpans(0, reading.length, RelativeSizeSpan::class.java).forEach { span ->
                    setSpan(span, 0, reading.length + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }
        }
        if (!TextUtils.equals(text, fitted)) text = fitted
        // Horizontal scrolling gives centered TextViews a huge virtual layout
        // width. Bound each line above instead, so both stay visibly centered.
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }
}
