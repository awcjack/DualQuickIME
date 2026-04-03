package com.awcjack.dualquickime

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
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
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.awcjack.dualquickime.data.ClipboardHistoryManager
import com.awcjack.dualquickime.data.RecentCandidateManager
import com.awcjack.dualquickime.theme.ThemeManager
import com.awcjack.dualquickime.voice.ModelDownloadManager

/**
 * Settings activity for the DualQuick IME.
 * Allows users to configure theme, composition display, and candidate count.
 */
class SettingsActivity : AppCompatActivity() {

    companion object {
        private const val REQUEST_RECORD_AUDIO_PERMISSION = 200
    }

    private lateinit var themeRadioGroup: RadioGroup
    private lateinit var previewContainer: LinearLayout
    private lateinit var previewKeyRow: LinearLayout
    private lateinit var switchShowComposition: SwitchCompat
    private lateinit var seekBarCandidates: SeekBar
    private lateinit var textCandidatesValue: TextView

    // Voice input settings
    private lateinit var switchVoiceEnabled: SwitchCompat
    private lateinit var btnVoiceModel: Button
    private lateinit var textVoiceModelStatus: TextView
    private lateinit var btnVoicePermission: Button
    private lateinit var textVoicePermissionStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        themeRadioGroup = findViewById(R.id.themeRadioGroup)
        previewContainer = findViewById(R.id.previewContainer)
        previewKeyRow = findViewById(R.id.previewKeyRow)
        switchShowComposition = findViewById(R.id.switchShowComposition)
        seekBarCandidates = findViewById(R.id.seekBarCandidates)
        textCandidatesValue = findViewById(R.id.textCandidatesValue)

        // Voice input settings
        switchVoiceEnabled = findViewById(R.id.switchVoiceEnabled)
        btnVoiceModel = findViewById(R.id.btnVoiceModel)
        textVoiceModelStatus = findViewById(R.id.textVoiceModelStatus)
        btnVoicePermission = findViewById(R.id.btnVoicePermission)
        textVoicePermissionStatus = findViewById(R.id.textVoicePermissionStatus)

        setupThemeSelection()
        setupCompositionToggle()
        setupCandidatesSeekBar()
        setupRecentCandidatesSettings()
        setupCharacterSetSettings()
        setupClipboardSettings()

        // Voice input settings only available in full version
        if (BuildConfig.VOICE_INPUT_ENABLED) {
            setupVoiceInputSettings()
        } else {
            // Hide voice input section in lite version
            findViewById<View>(R.id.voiceInputSection)?.visibility = View.GONE
            findViewById<View>(R.id.voiceInputSectionHeader)?.visibility = View.GONE
        }

        setupGitHubLink()
        updatePreview()

        // Handle permission request from IME
        if (BuildConfig.VOICE_INPUT_ENABLED && intent.getBooleanExtra("request_audio_permission", false)) {
            requestAudioPermission()
        }
    }

    override fun onResume() {
        super.onResume()
        // Update voice settings UI when returning to activity
        if (BuildConfig.VOICE_INPUT_ENABLED) {
            updateVoiceSettingsUI()
        }
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

    private fun setupVoiceInputSettings() {
        // Set current value
        switchVoiceEnabled.isChecked = ThemeManager.getVoiceInputEnabled(this)

        // Listen for changes
        switchVoiceEnabled.setOnCheckedChangeListener { _, isChecked ->
            ThemeManager.setVoiceInputEnabled(this, isChecked)
        }

        // Model download/delete button
        btnVoiceModel.setOnClickListener {
            if (ModelDownloadManager.isModelDownloaded(this)) {
                // Confirm delete
                AlertDialog.Builder(this)
                    .setTitle(R.string.settings_voice_delete_confirm_title)
                    .setMessage(R.string.settings_voice_delete_confirm_message)
                    .setPositiveButton(R.string.settings_voice_delete_confirm_yes) { _, _ ->
                        ModelDownloadManager.deleteModel(this)
                        updateVoiceSettingsUI()
                        Toast.makeText(this, "Voice model deleted", Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            } else {
                // Start download
                startModelDownload()
            }
        }

        // Permission button
        btnVoicePermission.setOnClickListener {
            requestAudioPermission()
        }

        updateVoiceSettingsUI()
    }

    private fun updateVoiceSettingsUI() {
        val modelDownloaded = ModelDownloadManager.isModelDownloaded(this)
        val hasPermission = hasAudioPermission()

        // Update model button and status
        if (modelDownloaded) {
            btnVoiceModel.text = getString(R.string.settings_voice_delete_model)
            textVoiceModelStatus.text = getString(R.string.settings_voice_model_downloaded)
        } else {
            btnVoiceModel.text = getString(R.string.settings_voice_download_model)
            textVoiceModelStatus.text = getString(R.string.settings_voice_model_size)
        }

        // Update permission button and status
        if (hasPermission) {
            btnVoicePermission.visibility = View.GONE
            textVoicePermissionStatus.text = getString(R.string.settings_voice_permission_granted)
        } else {
            btnVoicePermission.visibility = View.VISIBLE
            textVoicePermissionStatus.text = getString(R.string.settings_voice_permission_desc)
        }
    }

    private fun hasAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestAudioPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.RECORD_AUDIO),
            REQUEST_RECORD_AUDIO_PERMISSION
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_RECORD_AUDIO_PERMISSION) {
            updateVoiceSettingsUI()
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, R.string.settings_voice_permission_granted, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun startModelDownload() {
        btnVoiceModel.isEnabled = false
        btnVoiceModel.text = getString(R.string.voice_downloading_model)

        ModelDownloadManager.downloadModel(this, object : ModelDownloadManager.DownloadCallback {
            override fun onProgress(bytesDownloaded: Long, totalBytes: Long, currentFile: String) {
                val progress = ((bytesDownloaded.toFloat() / totalBytes) * 100).toInt()
                val mbDownloaded = bytesDownloaded / 1_000_000
                val mbTotal = totalBytes / 1_000_000
                runOnUiThread {
                    textVoiceModelStatus.text = "$mbDownloaded / $mbTotal MB ($progress%)"
                }
            }

            override fun onComplete() {
                runOnUiThread {
                    btnVoiceModel.isEnabled = true
                    updateVoiceSettingsUI()
                    Toast.makeText(this@SettingsActivity, R.string.settings_voice_model_downloaded, Toast.LENGTH_SHORT).show()
                }
            }

            override fun onError(message: String) {
                runOnUiThread {
                    btnVoiceModel.isEnabled = true
                    btnVoiceModel.text = getString(R.string.settings_voice_download_model)
                    Toast.makeText(this@SettingsActivity, "Download failed: $message", Toast.LENGTH_LONG).show()
                }
            }
        })
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

    private fun setupRecentCandidatesSettings() {
        val switchRecent = findViewById<SwitchCompat>(R.id.switchRecentCandidates)
        val btnClear = findViewById<Button>(R.id.btnClearRecentCandidates)

        // Set current value
        switchRecent.isChecked = ThemeManager.getRecentCandidatesEnabled(this)

        // Listen for changes
        switchRecent.setOnCheckedChangeListener { _, isChecked ->
            ThemeManager.setRecentCandidatesEnabled(this, isChecked)
        }

        // Clear recent candidates button
        btnClear.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(R.string.settings_recent_candidates_clear_confirm_title)
                .setMessage(R.string.settings_recent_candidates_clear_confirm_message)
                .setPositiveButton(R.string.settings_recent_candidates_clear_confirm_yes) { _, _ ->
                    RecentCandidateManager.clearAll(this)
                    Toast.makeText(this, R.string.settings_recent_candidates_cleared, Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
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
