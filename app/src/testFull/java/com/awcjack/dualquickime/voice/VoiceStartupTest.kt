package com.awcjack.dualquickime.voice

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.ServiceConnection
import android.os.Binder
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class VoiceStartupTest {
    @Test fun cancellingDuringIdleReloadDoesNotOpenTheMicrophone() {
        val context = RuntimeEnvironment.getApplication()
        shadowOf(context).grantPermissions(Manifest.permission.RECORD_AUDIO)
        val manager = VoiceInputManager(context)
        val loading = CountDownLatch(1)
        val finishLoading = CountDownLatch(1)
        val current = AtomicBoolean(true)
        val binder = object : IVoiceRecognizer.Stub() {
            override fun initialize(modelDir: String, numThreads: Int, maxNewTokens: Int): Boolean {
                loading.countDown()
                check(finishLoading.await(5, TimeUnit.SECONDS))
                return true
            }
            override fun isLoaded() = false
            override fun releaseModel() = Unit
            override fun transcribe(pcm16le: ByteArray) = ""
        }
        setField(manager, "isInitialized", true)
        setField(manager, "currentModelType", VoiceModelType.QWEN3_ASR_1_7B)
        setField(manager, "voiceServiceBinder", binder)
        val executor = Executors.newSingleThreadExecutor()
        try {
            val started = executor.submit<Boolean> { manager.startRecording { current.get() } }
            assertTrue(loading.await(5, TimeUnit.SECONDS))
            current.set(false)
            finishLoading.countDown()
            assertFalse(started.get(5, TimeUnit.SECONDS))
            val field = VoiceInputManager::class.java.getDeclaredField("audioRecord")
            field.isAccessible = true
            assertNull(field.get(manager))
        } finally {
            finishLoading.countDown()
            executor.shutdownNow()
        }
    }

    @Test fun concurrentStartupSharesOneBindingAndCanWaitForReconnection() {
        val context = object : ContextWrapper(RuntimeEnvironment.getApplication()) {
            val bound = CountDownLatch(1)
            lateinit var connection: ServiceConnection
            var binds = 0
            override fun bindService(intent: Intent, conn: ServiceConnection, flags: Int): Boolean {
                binds++
                connection = conn
                bound.countDown()
                return true
            }
        }
        val manager = VoiceInputManager(context)
        val bind = VoiceInputManager::class.java.getDeclaredMethod("bindVoiceService")
        bind.isAccessible = true
        val executor = Executors.newFixedThreadPool(2)
        val name = ComponentName(context, VoiceService::class.java)
        try {
            val first = executor.submit<Boolean> { bind.invoke(manager) as Boolean }
            assertTrue(context.bound.await(5, TimeUnit.SECONDS))
            val second = executor.submit<Boolean> { bind.invoke(manager) as Boolean }
            context.connection.onServiceConnected(name, Binder())
            assertTrue(first.get(2, TimeUnit.SECONDS))
            assertTrue(second.get(2, TimeUnit.SECONDS))
            assertEquals(1, context.binds)

            context.connection.onServiceDisconnected(name)
            val reconnected = executor.submit<Boolean> { bind.invoke(manager) as Boolean }
            context.connection.onServiceConnected(name, Binder())
            assertTrue(reconnected.get(2, TimeUnit.SECONDS))
            assertEquals(1, context.binds)
        } finally {
            executor.shutdownNow()
        }
    }

    private fun setField(manager: VoiceInputManager, name: String, value: Any) {
        VoiceInputManager::class.java.getDeclaredField(name).apply { isAccessible = true }.set(manager, value)
    }
}
