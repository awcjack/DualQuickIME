package com.awcjack.dualquickime.voice

import android.app.ActivityManager
import android.app.KeyguardManager
import android.content.Context
import android.os.PowerManager
import android.os.SystemClock
import com.awcjack.dualquickime.theme.ThemeManager
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class VoiceModelRetentionTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()
    private lateinit var manager: VoiceInputManager
    private lateinit var binder: TestRecognizer
    private val gib = 1024L * 1024 * 1024

    @Before fun setUp() {
        context.getSharedPreferences("dualquick_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        shadowOf(context.getSystemService(Context.POWER_SERVICE) as PowerManager).setIsInteractive(true)
        shadowOf(context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager).setKeyguardLocked(false)
        setAvailableMemory(4 * gib)
        manager = VoiceInputManager(context)
        binder = TestRecognizer()
        field("currentModelType").set(manager, VoiceModelType.QWEN3_ASR_1_7B)
        field("voiceServiceBinder").set(manager, binder)
        setIdleMillis(0)
    }

    @Test fun timerUsesTwoMinutesAndObservesSettingChangesWithoutReinitializing() {
        setIdleMillis(31_000)
        checkRetention()
        assertEquals(0, binder.releases.get())
        setIdleMillis(120_000)
        checkRetention()
        assertEquals(1, binder.releases.get())

        binder.loaded = true
        ThemeManager.setVoiceModelUnloadSeconds(context, 300)
        checkRetention()
        assertEquals(1, binder.releases.get())
        ThemeManager.setVoiceModelUnloadSeconds(context, 30)
        checkRetention()
        assertEquals(2, binder.releases.get())
    }

    @Test fun screenLockAndLowMemoryWaitUntilRecordingAndFinalAudioProcessingEnd() {
        shadowOf(context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager).setKeyguardLocked(true)
        field("isRecording").set(manager, true)
        checkRetention()
        assertEquals(0, binder.releases.get())

        field("isRecording").set(manager, false)
        val captureWorkers = field("captureWorkers").get(manager) as AtomicInteger
        captureWorkers.incrementAndGet()
        checkRetention()
        assertEquals(0, binder.releases.get())
        captureWorkers.decrementAndGet()
        checkRetention()
        assertEquals(1, binder.releases.get())

        binder.loaded = true
        shadowOf(context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager).setKeyguardLocked(false)
        setAvailableMemory(gib / 2)
        checkRetention()
        assertEquals(2, binder.releases.get())

        binder.loaded = true
        setAvailableMemory(4 * gib)
        shadowOf(context.getSystemService(Context.POWER_SERVICE) as PowerManager).setIsInteractive(false)
        checkRetention()
        assertEquals(3, binder.releases.get())
    }

    @Test fun retentionSkipsASlowDecodeInsteadOfWaitingThenUnloadingItsWeights() {
        val decoding = CountDownLatch(1)
        val finishDecode = CountDownLatch(1)
        binder.decode = {
            decoding.countDown()
            check(finishDecode.await(5, TimeUnit.SECONDS))
            "你好"
        }
        shadowOf(context.getSystemService(Context.POWER_SERVICE) as PowerManager).setIsInteractive(false)
        val executor = Executors.newSingleThreadExecutor()
        try {
            val result = executor.submit<String> {
                method("recognizeSegment", FloatArray::class.java).invoke(manager, floatArrayOf(0.1f)) as String
            }
            assertTrue(decoding.await(5, TimeUnit.SECONDS))
            checkRetention()
            assertEquals(0, binder.releases.get())
            finishDecode.countDown()
            assertEquals("你好", result.get(5, TimeUnit.SECONDS))
            checkRetention()
            assertEquals(1, binder.releases.get())
        } finally {
            finishDecode.countDown()
            executor.shutdownNow()
        }
    }

    @Test fun stoppingAnOldMonitorDoesNotClearItsReplacement() {
        try {
            method("startIdleReleaseMonitor").invoke(manager)
            val old = field("idleReleaseThread").get(manager) as Thread
            method("stopIdleReleaseMonitor").invoke(manager)
            method("startIdleReleaseMonitor").invoke(manager)
            val replacement = field("idleReleaseThread").get(manager) as Thread
            old.join(1000)
            assertFalse(old.isAlive)
            assertNotSame(old, replacement)
            assertSame(replacement, field("idleReleaseThread").get(manager))
            assertTrue(replacement.isAlive)
        } finally {
            val monitor = field("idleReleaseThread").get(manager) as? Thread
            method("stopIdleReleaseMonitor").invoke(manager)
            monitor?.join(1000)
        }
    }

    private fun setAvailableMemory(available: Long) {
        shadowOf(context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).setMemoryInfo(
            ActivityManager.MemoryInfo().apply {
                availMem = available
                totalMem = 8 * gib
                threshold = gib / 4
                lowMemory = false
            }
        )
    }

    private fun setIdleMillis(idle: Long) = field("lastActivityMillis").set(manager, SystemClock.elapsedRealtime() - idle)
    private fun checkRetention() { method("checkIdleRelease").invoke(manager) }
    private fun field(name: String) = VoiceInputManager::class.java.getDeclaredField(name).apply { isAccessible = true }
    private fun method(name: String, vararg args: Class<*>) =
        VoiceInputManager::class.java.getDeclaredMethod(name, *args).apply { isAccessible = true }

    private class TestRecognizer : IVoiceRecognizer.Stub() {
        @Volatile var loaded = true
        val releases = AtomicInteger()
        var decode: () -> String = { "" }
        override fun initialize(modelDir: String, numThreads: Int, maxNewTokens: Int) = true
        override fun isLoaded() = loaded
        override fun releaseModel() { loaded = false; releases.incrementAndGet() }
        override fun transcribe(pcm16le: ByteArray) = decode()
    }
}
