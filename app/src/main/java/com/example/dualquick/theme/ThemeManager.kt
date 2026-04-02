package com.example.dualquick.theme

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

    const val THEME_LIGHT = 0
    const val THEME_DARK = 1
    const val THEME_AUTO = 2

    private var cachedTheme: Int = -1

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

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
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
        keyShadowColor = Color.parseColor("#0D000000")
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
        keyShadowColor = Color.parseColor("#1A000000")
    )
}

/**
 * Data class holding all keyboard theme colors.
 */
data class KeyboardColors(
    val keyboardBackground: Int,
    val keyBackground: Int,
    val keyBackgroundPressed: Int,
    val specialKeyBackground: Int,
    val specialKeyBackgroundPressed: Int,
    val spaceKeyBackground: Int,
    val candidateBarBackground: Int,
    val candidatePillBackground: Int,
    val candidatePillBackgroundPressed: Int,
    val keyTextPrimary: Int,
    val keyTextSecondary: Int,
    val candidateText: Int,
    val compositionText: Int,
    val englishPillText: Int,
    val pageIndicatorText: Int,
    val noMatchText: Int,
    val keyShadowColor: Int
)
