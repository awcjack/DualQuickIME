package com.awcjack.dualquickime.ui

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout

/**
 * Stub VoiceInputView for lite flavor (no voice input support).
 */
class VoiceInputView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    enum class State {
        HIDDEN,
        DOWNLOADING,
        LISTENING,
        PROCESSING,
        ERROR
    }

    init {
        visibility = View.GONE
    }

    fun setOnCancelListener(callback: () -> Unit) {}
    fun setState(state: State) {}
    fun setTranscript(text: String) {}
    fun setDownloadProgress(progress: Int, statusMessage: String) {}
    fun setErrorMessage(message: String) {}
    fun refreshTheme() {}
}
