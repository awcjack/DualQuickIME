package com.awcjack.dualquickime.ui

import android.content.Context
import android.os.Build
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.widget.TextView
import com.awcjack.dualquickime.data.PronunciationMode
import com.awcjack.dualquickime.data.PronunciationTable
import java.io.IOException

/** Shared label formatting for the candidate strip and expanded grid. */
object CandidatePronunciation {
    private val tables = mutableMapOf<PronunciationMode, PronunciationTable?>()

    fun label(context: Context, candidate: String, mode: PronunciationMode, grey: Int,
              precedingCharacter: String = ""): CharSequence {
        val assetName = mode.assetName ?: return candidate
        val table = synchronized(tables) {
            if (!tables.containsKey(mode)) {
                tables[mode] = try {
                    context.assets.open("pronunciation/$assetName").use { characters ->
                        context.assets.open("pronunciation/${assetName.removeSuffix(".tsv")}-words.tsv").use { words ->
                            PronunciationTable.parse(characters, words)
                        }
                    }
                } catch (e: IOException) {
                    // A missing asset must never make the keyboard unusable.
                    Log.e("CandidatePronunciation", "Unable to load $assetName", e)
                    null
                } catch (e: IllegalArgumentException) {
                    Log.e("CandidatePronunciation", "Invalid pronunciation data in $assetName", e)
                    null
                }
            }
            tables[mode]
        }
        val reading = table?.readingFor(candidate, precedingCharacter) ?: return candidate
        return styledLabel(candidate, reading, grey)
    }

    private fun styledLabel(candidate: String, reading: String, grey: Int): CharSequence {
        return SpannableString("$reading\n$candidate").apply {
            // The newline must use the reading's size too. Otherwise Android
            // reserves a full character-sized first line for the separator.
            setSpan(RelativeSizeSpan(0.55f), 0, reading.length + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(ForegroundColorSpan(grey), 0, reading.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    fun configureLineMetrics(view: TextView) {
        view.includeFontPadding = true
        // Do not compress baselines: CJK fallback fonts can have taller glyphs.
        view.setLineSpacing(dp(view.context, 2).toFloat(), 1f)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            view.setFallbackLineSpacing(true)
        }
    }

    /** Measure once per keyboard rebuild, independently of the current page.
     * Uses the same Android text layout and CJK fallback fonts as real pills,
     * including the user's font size, rather than assuming a fixed dp height.
     */
    fun minimumCellHeight(context: Context, characterSizeSp: Float, verticalPaddingDp: Int): Int {
        val probe = TextView(context).apply {
            textSize = characterSizeSp
            // Include both common and supplementary Han fallback fonts.
            text = styledLabel("國𨋢", "pǐng", 0)
            setPadding(0, dp(context, verticalPaddingDp), 0, dp(context, verticalPaddingDp))
            maxLines = 2
            configureLineMetrics(this)
        }
        val unspecified = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        probe.measure(unspecified, unspecified)
        return probe.measuredHeight + dp(context, 4)
    }

    private fun dp(context: Context, value: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), context.resources.displayMetrics
    ).toInt()
}
