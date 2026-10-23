package com.awcjack.dualquickime.voice

import android.content.Context
import android.os.Looper
import android.widget.Spinner
import com.awcjack.dualquickime.R
import com.awcjack.dualquickime.SettingsActivity
import com.awcjack.dualquickime.theme.ThemeManager
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class VoiceRetentionSettingsTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Before fun resetPreferences() {
        context.getSharedPreferences("dualquick_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        ThemeManager.invalidateCache()
    }

    @Test fun defaultsToTwoMinutesAndRestoresTheChosenTimeout() {
        val controller = Robolectric.buildActivity(SettingsActivity::class.java).setup()
        val spinner = controller.get().findViewById<Spinner>(R.id.voiceModelUnloadTimeoutSpinner)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(120, ThemeManager.getVoiceModelUnloadSeconds(context))
        assertEquals(2, spinner.selectedItemPosition)
        assertEquals(VoiceModelRetentionPolicy.timeoutOptionsSeconds.size, spinner.count)
        spinner.setSelection(3)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(300, ThemeManager.getVoiceModelUnloadSeconds(context))
        controller.pause().stop().destroy()

        val reopened = Robolectric.buildActivity(SettingsActivity::class.java).setup()
        try {
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(3, reopened.get().findViewById<Spinner>(R.id.voiceModelUnloadTimeoutSpinner).selectedItemPosition)
        } finally {
            reopened.pause().stop().destroy()
        }
    }

    @Test fun invalidSavedTimeoutUsesTheDefault() {
        context.getSharedPreferences("dualquick_prefs", Context.MODE_PRIVATE).edit()
            .putInt("voice_model_unload_seconds", -1).commit()
        assertEquals(120, ThemeManager.getVoiceModelUnloadSeconds(context))
        ThemeManager.setVoiceModelUnloadSeconds(context, Int.MAX_VALUE)
        assertEquals(120, ThemeManager.getVoiceModelUnloadSeconds(context))
    }
}
