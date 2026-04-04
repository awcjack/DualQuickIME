package com.awcjack.dualquickime.theme

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.Color

/**
 * Manages keyboard theme settings (light/dark/auto).
 */
object ThemeManager {
    private const val PREFS_NAME = "dualquick_prefs"
    private const val KEY_THEME = "theme_mode"
    private const val KEY_SHOW_COMPOSITION = "show_composition"
    private const val KEY_CANDIDATES_PER_PAGE = "candidates_per_page"
    private const val KEY_USE_EXTENDED_CHARSET = "use_extended_charset"
    private const val KEY_VOICE_INPUT_ENABLED = "voice_input_enabled"
    private const val KEY_VOICE_MODEL_TYPE = "voice_model_type"
    private const val KEY_RECENT_CANDIDATES_ENABLED = "recent_candidates_enabled"

    const val THEME_LIGHT = 0
    const val THEME_DARK = 1
    const val THEME_AUTO = 2

    // Default candidates per page (affects pill sizing)
    const val CANDIDATES_MIN = 4
    const val CANDIDATES_MAX = 10
    const val CANDIDATES_DEFAULT = 6

    private var cachedTheme: Int = -1
    private var cachedShowComposition: Boolean? = null
    private var cachedCandidatesPerPage: Int = -1
    private var cachedUseExtendedCharset: Boolean? = null
    private var cachedVoiceInputEnabled: Boolean? = null
    private var cachedVoiceModelType: String? = null
    private var cachedRecentCandidatesEnabled: Boolean? = null

    fun getThemeMode(context: Context): Int {
        if (cachedTheme == -1) {
            cachedTheme = getPrefs(context).getInt(KEY_THEME, THEME_AUTO)
        }
        return cachedTheme
    }

    fun setThemeMode(context: Context, mode: Int) {
        cachedTheme = mode
        getPrefs(context).edit().putInt(KEY_THEME, mode).apply()
    }

    // Composition display settings
    fun getShowComposition(context: Context): Boolean {
        if (cachedShowComposition == null) {
            cachedShowComposition = getPrefs(context).getBoolean(KEY_SHOW_COMPOSITION, true)
        }
        return cachedShowComposition!!
    }

    fun setShowComposition(context: Context, show: Boolean) {
        cachedShowComposition = show
        getPrefs(context).edit().putBoolean(KEY_SHOW_COMPOSITION, show).apply()
    }

    // Candidates per page settings
    fun getCandidatesPerPage(context: Context): Int {
        if (cachedCandidatesPerPage == -1) {
            cachedCandidatesPerPage = getPrefs(context).getInt(KEY_CANDIDATES_PER_PAGE, CANDIDATES_DEFAULT)
        }
        return cachedCandidatesPerPage
    }

    fun setCandidatesPerPage(context: Context, count: Int) {
        cachedCandidatesPerPage = count.coerceIn(CANDIDATES_MIN, CANDIDATES_MAX)
        getPrefs(context).edit().putInt(KEY_CANDIDATES_PER_PAGE, cachedCandidatesPerPage).apply()
    }

    // Extended character set settings (default: true for extended)
    fun getUseExtendedCharset(context: Context): Boolean {
        if (cachedUseExtendedCharset == null) {
            cachedUseExtendedCharset = getPrefs(context).getBoolean(KEY_USE_EXTENDED_CHARSET, true)
        }
        return cachedUseExtendedCharset!!
    }

    fun setUseExtendedCharset(context: Context, useExtended: Boolean) {
        cachedUseExtendedCharset = useExtended
        getPrefs(context).edit().putBoolean(KEY_USE_EXTENDED_CHARSET, useExtended).apply()
    }

    /**
     * Get the appropriate simplex.cin filename based on settings.
     */
    fun getSimplexFilename(context: Context): String {
        return if (getUseExtendedCharset(context)) "simplex-ext.cin" else "simplex.cin"
    }

    // Voice input settings (default: true to enable)
    fun getVoiceInputEnabled(context: Context): Boolean {
        if (cachedVoiceInputEnabled == null) {
            cachedVoiceInputEnabled = getPrefs(context).getBoolean(KEY_VOICE_INPUT_ENABLED, true)
        }
        return cachedVoiceInputEnabled!!
    }

    fun setVoiceInputEnabled(context: Context, enabled: Boolean) {
        cachedVoiceInputEnabled = enabled
        getPrefs(context).edit().putBoolean(KEY_VOICE_INPUT_ENABLED, enabled).apply()
    }

    // Voice model type settings (default: "sensevoice")
    fun getVoiceModelType(context: Context): String {
        if (cachedVoiceModelType == null) {
            cachedVoiceModelType = getPrefs(context).getString(KEY_VOICE_MODEL_TYPE, "sensevoice") ?: "sensevoice"
        }
        return cachedVoiceModelType!!
    }

    fun setVoiceModelType(context: Context, modelTypeId: String) {
        cachedVoiceModelType = modelTypeId
        getPrefs(context).edit().putString(KEY_VOICE_MODEL_TYPE, modelTypeId).apply()
    }

    // Recent candidates settings (default: false to disable)
    fun getRecentCandidatesEnabled(context: Context): Boolean {
        if (cachedRecentCandidatesEnabled == null) {
            cachedRecentCandidatesEnabled = getPrefs(context).getBoolean(KEY_RECENT_CANDIDATES_ENABLED, false)
        }
        return cachedRecentCandidatesEnabled!!
    }

    fun setRecentCandidatesEnabled(context: Context, enabled: Boolean) {
        cachedRecentCandidatesEnabled = enabled
        getPrefs(context).edit().putBoolean(KEY_RECENT_CANDIDATES_ENABLED, enabled).apply()
    }

    /**
     * Returns true if dark theme should be used based on current settings.
     */
    fun isDarkTheme(context: Context): Boolean {
        return when (getThemeMode(context)) {
            THEME_LIGHT -> false
            THEME_DARK -> true
            THEME_AUTO -> {
                val nightMode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
                nightMode == Configuration.UI_MODE_NIGHT_YES
            }
            else -> true
        }
    }

    /**
     * Get the current theme colors.
     */
    fun getColors(context: Context): KeyboardColors {
        return if (isDarkTheme(context)) darkColors else lightColors
    }

    /**
     * Alias for getColors() for consistency.
     */
    fun getKeyboardColors(context: Context): KeyboardColors = getColors(context)

    /**
     * Get the accent color for the current theme.
     */
    fun getAccentColor(context: Context): Int {
        return if (isDarkTheme(context)) darkColors.compositionText else lightColors.compositionText
    }

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Invalidate all caches (call when settings might have changed externally).
     */
    fun invalidateCache() {
        cachedTheme = -1
        cachedShowComposition = null
        cachedCandidatesPerPage = -1
        cachedUseExtendedCharset = null
        cachedVoiceInputEnabled = null
        cachedVoiceModelType = null
        cachedRecentCandidatesEnabled = null
    }

    // Modern Dark Theme Colors (Material You inspired)
    private val darkColors = KeyboardColors(
        keyboardBackground = Color.parseColor("#1F1F1F"),
        keyBackground = Color.parseColor("#3A3A3A"),
        keyBackgroundPressed = Color.parseColor("#525252"),
        specialKeyBackground = Color.parseColor("#2A2A2A"),
        specialKeyBackgroundPressed = Color.parseColor("#424242"),
        spaceKeyBackground = Color.parseColor("#3A3A3A"),
        candidateBarBackground = Color.parseColor("#1F1F1F"),
        candidatePillBackground = Color.parseColor("#2A2A2A"),
        candidatePillBackgroundPressed = Color.parseColor("#424242"),
        keyTextPrimary = Color.parseColor("#E8E8E8"),
        keyTextSecondary = Color.parseColor("#9E9E9E"),
        candidateText = Color.parseColor("#E8E8E8"),
        compositionText = Color.parseColor("#8AB4F8"),  // Google Blue
        englishPillText = Color.parseColor("#8AB4F8"),
        pageIndicatorText = Color.parseColor("#9E9E9E"),
        noMatchText = Color.parseColor("#757575"),
        keyShadowColor = Color.parseColor("#0D000000"),
        emojiCategoryBackground = Color.parseColor("#2A2A2A"),
        emojiCategorySelectedBackground = Color.parseColor("#424242"),
        emojiCategoryText = Color.parseColor("#9E9E9E"),
        emojiCategorySelectedText = Color.parseColor("#E8E8E8"),
        // Clipboard colors
        clipboardItemBackground = Color.parseColor("#2A2A2A"),
        clipboardItemBackgroundPressed = Color.parseColor("#424242"),
        clipboardItemText = Color.parseColor("#E8E8E8"),
        clipboardPinnedIcon = Color.parseColor("#8AB4F8"),
        clipboardDeleteIcon = Color.parseColor("#9E9E9E"),
        clipboardEmptyText = Color.parseColor("#757575")
    )

    // Modern Light Theme Colors (Material You inspired)
    private val lightColors = KeyboardColors(
        keyboardBackground = Color.parseColor("#E8EAF0"),
        keyBackground = Color.parseColor("#FFFFFF"),
        keyBackgroundPressed = Color.parseColor("#D0D0D0"),
        specialKeyBackground = Color.parseColor("#D4D8E0"),
        specialKeyBackgroundPressed = Color.parseColor("#B8BCC4"),
        spaceKeyBackground = Color.parseColor("#FFFFFF"),
        candidateBarBackground = Color.parseColor("#E8EAF0"),
        candidatePillBackground = Color.parseColor("#FFFFFF"),
        candidatePillBackgroundPressed = Color.parseColor("#D0D0D0"),
        keyTextPrimary = Color.parseColor("#1F1F1F"),
        keyTextSecondary = Color.parseColor("#5F6368"),
        candidateText = Color.parseColor("#1F1F1F"),
        compositionText = Color.parseColor("#1A73E8"),  // Google Blue
        englishPillText = Color.parseColor("#1A73E8"),
        pageIndicatorText = Color.parseColor("#5F6368"),
        noMatchText = Color.parseColor("#9E9E9E"),
        keyShadowColor = Color.parseColor("#1A000000"),
        emojiCategoryBackground = Color.parseColor("#FFFFFF"),
        emojiCategorySelectedBackground = Color.parseColor("#D4D8E0"),
        emojiCategoryText = Color.parseColor("#5F6368"),
        emojiCategorySelectedText = Color.parseColor("#1F1F1F"),
        // Clipboard colors
        clipboardItemBackground = Color.parseColor("#FFFFFF"),
        clipboardItemBackgroundPressed = Color.parseColor("#D0D0D0"),
        clipboardItemText = Color.parseColor("#1F1F1F"),
        clipboardPinnedIcon = Color.parseColor("#1A73E8"),
        clipboardDeleteIcon = Color.parseColor("#5F6368"),
        clipboardEmptyText = Color.parseColor("#9E9E9E")
    )
}
