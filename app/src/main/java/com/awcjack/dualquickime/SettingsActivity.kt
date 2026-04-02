package com.awcjack.dualquickime

import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.SwitchCompat
import com.awcjack.dualquickime.data.ClipboardHistoryManager
import com.awcjack.dualquickime.theme.ThemeManager

/**
 * Settings activity for the DualQuick IME.
 * Allows users to configure theme, composition display, and candidate count.
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var themeRadioGroup: RadioGroup
    private lateinit var previewContainer: LinearLayout
    private lateinit var previewKeyRow: LinearLayout
    private lateinit var switchShowComposition: SwitchCompat
    private lateinit var seekBarCandidates: SeekBar
    private lateinit var textCandidatesValue: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        themeRadioGroup = findViewById(R.id.themeRadioGroup)
        previewContainer = findViewById(R.id.previewContainer)
        previewKeyRow = findViewById(R.id.previewKeyRow)
        switchShowComposition = findViewById(R.id.switchShowComposition)
        seekBarCandidates = findViewById(R.id.seekBarCandidates)
        textCandidatesValue = findViewById(R.id.textCandidatesValue)

        setupThemeSelection()
        setupCompositionToggle()
        setupCandidatesSeekBar()
        setupCharacterSetSettings()
        setupClipboardSettings()
        setupGitHubLink()
        updatePreview()
    }

    private fun setupCharacterSetSettings() {
        val switchExtended = findViewById<SwitchCompat>(R.id.switchExtendedCharset)
        val hintText = findViewById<TextView>(R.id.textCharsetHint)

        // Set current value (default: extended = true)
        switchExtended.isChecked = ThemeManager.getUseExtendedCharset(this)

        // Listen for changes
        switchExtended.setOnCheckedChangeListener { _, isChecked ->
            ThemeManager.setUseExtendedCharset(this, isChecked)
            // Show restart hint
            hintText.visibility = View.VISIBLE
        }
    }

    private fun setupClipboardSettings() {
        val switchEnabled = findViewById<SwitchCompat>(R.id.switchClipboardEnabled)
        val btnClear = findViewById<Button>(R.id.btnClearClipboard)

        // Set current value
        switchEnabled.isChecked = ClipboardHistoryManager.isEnabled(this)

        // Listen for changes
        switchEnabled.setOnCheckedChangeListener { _, isChecked ->
            ClipboardHistoryManager.setEnabled(this, isChecked)
        }

        // Clear history button
        btnClear.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(R.string.settings_clipboard_clear_confirm_title)
                .setMessage(R.string.settings_clipboard_clear_confirm_message)
                .setPositiveButton(R.string.settings_clipboard_clear_confirm_yes) { _, _ ->
                    ClipboardHistoryManager.clearHistory(this, includePinned = true)
                    Toast.makeText(this, R.string.settings_clipboard_cleared, Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
    }

    private fun setupGitHubLink() {
        findViewById<LinearLayout>(R.id.githubLink).setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.github_url)))
            startActivity(intent)
        }
    }

    private fun setupThemeSelection() {
        // Set current theme selection
        when (ThemeManager.getThemeMode(this)) {
            ThemeManager.THEME_LIGHT -> themeRadioGroup.check(R.id.radioLight)
            ThemeManager.THEME_DARK -> themeRadioGroup.check(R.id.radioDark)
            ThemeManager.THEME_AUTO -> themeRadioGroup.check(R.id.radioAuto)
        }

        // Listen for changes
        themeRadioGroup.setOnCheckedChangeListener { _, checkedId ->
            val mode = when (checkedId) {
                R.id.radioLight -> ThemeManager.THEME_LIGHT
                R.id.radioDark -> ThemeManager.THEME_DARK
                R.id.radioAuto -> ThemeManager.THEME_AUTO
                else -> ThemeManager.THEME_AUTO
            }
            ThemeManager.setThemeMode(this, mode)
            updatePreview()

            // Update app theme
            val nightMode = when (mode) {
                ThemeManager.THEME_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
                ThemeManager.THEME_DARK -> AppCompatDelegate.MODE_NIGHT_YES
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
            AppCompatDelegate.setDefaultNightMode(nightMode)
        }
    }

    private fun setupCompositionToggle() {
        // Set current value
        switchShowComposition.isChecked = ThemeManager.getShowComposition(this)

        // Listen for changes
        switchShowComposition.setOnCheckedChangeListener { _, isChecked ->
            ThemeManager.setShowComposition(this, isChecked)
        }
    }

    private fun setupCandidatesSeekBar() {
        // Set current value
        val currentValue = ThemeManager.getCandidatesPerPage(this)
        seekBarCandidates.progress = currentValue
        textCandidatesValue.text = currentValue.toString()

        // Listen for changes
        seekBarCandidates.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val value = progress.coerceIn(ThemeManager.CANDIDATES_MIN, ThemeManager.CANDIDATES_MAX)
                textCandidatesValue.text = value.toString()
                if (fromUser) {
                    ThemeManager.setCandidatesPerPage(this@SettingsActivity, value)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun updatePreview() {
        val colors = ThemeManager.getColors(this)

        // Update preview container background
        previewContainer.setBackgroundColor(colors.keyboardBackground)

        // Clear and rebuild preview keys
        previewKeyRow.removeAllViews()

        // Sample keys: Q W E R T
        val sampleKeys = listOf(
            Pair("Q", "手"),
            Pair("W", "田"),
            Pair("E", "水"),
            Pair("R", "口"),
            Pair("T", "廿")
        )

        sampleKeys.forEach { (letter, radical) ->
            previewKeyRow.addView(createPreviewKey(letter, radical, colors))
        }
    }

    private fun createPreviewKey(letter: String, radical: String, colors: com.awcjack.dualquickime.theme.KeyboardColors): View {
        return LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f).apply {
                setMargins(dpToPx(3), dpToPx(3), dpToPx(3), dpToPx(3))
            }
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER

            // Modern rounded background with shadow effect
            background = createKeyBackground(colors.keyBackground, colors.keyShadowColor)
            elevation = dpToPx(2).toFloat()

            // Letter
            addView(TextView(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    0,
                    1f
                )
                gravity = Gravity.CENTER or Gravity.BOTTOM
                text = letter
                textSize = 16f
                setTextColor(colors.keyTextPrimary)
            })

            // Radical
            addView(TextView(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    0,
                    0.7f
                )
                gravity = Gravity.CENTER or Gravity.TOP
                text = radical
                textSize = 11f
                setTextColor(colors.keyTextSecondary)
            })
        }
    }

    private fun createKeyBackground(color: Int, shadowColor: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dpToPx(8).toFloat()
            setColor(color)
        }
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }
}
