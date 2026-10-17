package com.awcjack.dualquickime.ui

import android.content.Context
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.util.Log
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
        return SpannableString("$reading\n$candidate").apply {
            setSpan(RelativeSizeSpan(0.55f), 0, reading.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(ForegroundColorSpan(grey), 0, reading.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }
}
