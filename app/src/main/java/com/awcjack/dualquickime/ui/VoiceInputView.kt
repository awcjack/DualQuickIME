package com.awcjack.dualquickime.ui

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import com.awcjack.dualquickime.R
import com.awcjack.dualquickime.theme.KeyboardColors
import com.awcjack.dualquickime.theme.ThemeManager

/**
 * Voice input overlay UI showing recording status and transcription results.
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

    private var currentState = State.HIDDEN
    private lateinit var colors: KeyboardColors

    // UI components
    private var contentContainer: LinearLayout? = null
    private var statusIcon: TextView? = null
    private var statusText: TextView? = null
    private var transcriptText: TextView? = null
    private var progressBar: ProgressBar? = null
    private var progressText: TextView? = null
    private var cancelButton: TextView? = null

    private var pulseAnimator: ObjectAnimator? = null

    private var onCancelCallback: (() -> Unit)? = null

    init {
        loadTheme()
        buildView()
        visibility = View.GONE
    }

    private fun loadTheme() {
        colors = ThemeManager.getKeyboardColors(context)
    }

    private fun buildView() {
        // Full overlay background
        setBackgroundColor(colors.keyboardBackground)

        contentContainer = LinearLayout(context).apply {
            layoutParams = LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT
            )
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dpToPx(24), dpToPx(16), dpToPx(24), dpToPx(16))
        }

        // Status icon (mic or spinner emoji)
        statusIcon = TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            textSize = 48f
            text = "🎤"
            gravity = Gravity.CENTER
        }
        contentContainer?.addView(statusIcon)

        // Status text
        statusText = TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dpToPx(12)
            }
            textSize = 16f
            setTextColor(colors.keyTextPrimary)
            gravity = Gravity.CENTER
            text = context.getString(R.string.voice_listening)
        }
        contentContainer?.addView(statusText)

        // Progress bar (for download)
        progressBar = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(8)
            ).apply {
                topMargin = dpToPx(16)
                marginStart = dpToPx(32)
                marginEnd = dpToPx(32)
            }
            max = 100
            progress = 0
            visibility = View.GONE
        }
        contentContainer?.addView(progressBar)

        // Progress text
        progressText = TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dpToPx(8)
            }
            textSize = 14f
            setTextColor(colors.keyTextSecondary)
            gravity = Gravity.CENTER
            visibility = View.GONE
        }
        contentContainer?.addView(progressText)

        // Transcript text (shows recognized speech)
        transcriptText = TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dpToPx(24)
            }
            textSize = 20f
            setTextColor(colors.keyTextPrimary)
            gravity = Gravity.CENTER
            minHeight = dpToPx(60)
            visibility = View.GONE
        }
        contentContainer?.addView(transcriptText)

        // Cancel button
        cancelButton = TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                dpToPx(44)
            ).apply {
                topMargin = dpToPx(24)
            }
            setPadding(dpToPx(32), 0, dpToPx(32), 0)
            gravity = Gravity.CENTER
            textSize = 16f
            text = context.getString(R.string.voice_cancel)
            setTextColor(colors.accentColor)
            background = createButtonBackground()

            setOnClickListener {
                onCancelCallback?.invoke()
            }
        }
        contentContainer?.addView(cancelButton)

        addView(contentContainer)
    }

    private fun createButtonBackground(): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dpToPx(22).toFloat()
            setColor(colors.keyBackground)
        }
    }

    fun setOnCancelListener(callback: () -> Unit) {
        onCancelCallback = callback
    }

    fun setState(state: State) {
        currentState = state

        when (state) {
            State.HIDDEN -> {
                visibility = View.GONE
                stopPulseAnimation()
            }
            State.DOWNLOADING -> {
                visibility = View.VISIBLE
                statusIcon?.text = "⬇️"
                statusText?.text = context.getString(R.string.voice_downloading_model)
                progressBar?.visibility = View.VISIBLE
                progressText?.visibility = View.VISIBLE
                transcriptText?.visibility = View.GONE
                stopPulseAnimation()
            }
            State.LISTENING -> {
                visibility = View.VISIBLE
                statusIcon?.text = "🎤"
                statusText?.text = context.getString(R.string.voice_listening)
                progressBar?.visibility = View.GONE
                progressText?.visibility = View.GONE
                transcriptText?.visibility = View.VISIBLE
                transcriptText?.text = ""
                startPulseAnimation()
            }
            State.PROCESSING -> {
                visibility = View.VISIBLE
                statusIcon?.text = "⏳"
                statusText?.text = context.getString(R.string.voice_processing)
                stopPulseAnimation()
            }
            State.ERROR -> {
                visibility = View.VISIBLE
                statusIcon?.text = "❌"
                progressBar?.visibility = View.GONE
                progressText?.visibility = View.GONE
                stopPulseAnimation()
            }
        }
    }

    fun setTranscript(text: String) {
        transcriptText?.text = text
        transcriptText?.visibility = if (text.isNotEmpty()) View.VISIBLE else View.GONE
    }

    fun setDownloadProgress(progress: Int, statusMessage: String) {
        progressBar?.progress = progress
        progressText?.text = statusMessage
    }

    fun setErrorMessage(message: String) {
        statusText?.text = message
    }

    private fun startPulseAnimation() {
        pulseAnimator?.cancel()
        pulseAnimator = ObjectAnimator.ofFloat(statusIcon, "alpha", 1f, 0.4f, 1f).apply {
            duration = 1500
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            start()
        }
    }

    private fun stopPulseAnimation() {
        pulseAnimator?.cancel()
        statusIcon?.alpha = 1f
    }

    fun refreshTheme() {
        loadTheme()
        removeAllViews()
        buildView()
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }
}
