package com.awcjack.dualquickime.ui

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.util.Log
import android.util.LruCache
import android.util.TypedValue
import android.view.View
import android.widget.TextView
import com.awcjack.dualquickime.data.PronunciationMode
import com.awcjack.dualquickime.data.PronunciationRepository
import com.awcjack.dualquickime.data.PronunciationTable
import com.awcjack.dualquickime.data.KeyboardDataWorker
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CancellationException

/** Shared label formatting for the candidate strip and expanded grid. */
object CandidatePronunciation {
    @Volatile private var repository: PronunciationRepository? = null
    private data class CellMetricsKey(val configuration: Configuration, val density: Float,
                                      val textSizeSp: Float, val paddingDp: Int)
    private val cellHeights = LruCache<CellMetricsKey, Int>(8)

    private fun repository(context: Context): PronunciationRepository {
        repository?.let { return it }
        return synchronized(this) {
            repository ?: run {
                // Retain only the application, never a keyboard view/service.
                val app = context.applicationContext
                PronunciationRepository(KeyboardDataWorker.executor) { mode, shouldContinue ->
                    val assetName = requireNotNull(mode.assetName)
                    try {
                        app.assets.open("pronunciation/$assetName").use { characters ->
                            app.assets.open("pronunciation/${assetName.removeSuffix(".tsv")}-words.tsv").use { words ->
                                PronunciationTable.parse(characters, words, shouldContinue)
                            }
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.e("CandidatePronunciation", "Unable to load $assetName", e)
                        throw e
                    }
                }.also { repository = it }
            }
        }
    }

    fun preload(context: Context, mode: PronunciationMode): CompletableFuture<PronunciationTable?> {
        if (mode == PronunciationMode.NONE) return CompletableFuture.completedFuture(null)
        return repository(context).preload(mode)
    }

    fun resumeLoading() { repository?.resume() }

    fun releaseMemory() {
        repository?.releaseMemory()
        cellHeights.evictAll()
    }

    fun label(candidate: String, mode: PronunciationMode, grey: Int,
              precedingCharacter: String = ""): CharSequence {
        if (mode == PronunciationMode.NONE) return candidate
        // A cold dictionary must never hold up typing. The service refreshes
        // visible candidates when its background preload completes.
        val reading = repository?.readingFor(candidate, mode, precedingCharacter) ?: return candidate
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
        val key = CellMetricsKey(Configuration(context.resources.configuration),
            context.resources.displayMetrics.density, characterSizeSp, verticalPaddingDp)
        cellHeights.get(key)?.let { return it }
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
        return (probe.measuredHeight + dp(context, 4)).also { cellHeights.put(key, it) }
    }

    private fun dp(context: Context, value: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), context.resources.displayMetrics
    ).toInt()
}
