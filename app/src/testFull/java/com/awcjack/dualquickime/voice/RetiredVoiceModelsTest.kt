package com.awcjack.dualquickime.voice

import android.content.Context
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.awcjack.dualquickime.R
import com.awcjack.dualquickime.SettingsActivity
import com.awcjack.dualquickime.theme.ThemeManager
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class RetiredVoiceModelsTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()
    private val retiredModels = listOf(VoiceModelType.WHISPER_CANTONESE, VoiceModelType.U2PP_CONFORMER_YUE)

    @Before fun setUp() {
        context.getSharedPreferences("dualquick_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        ThemeManager.invalidateCache()
        cleanFiles()
    }

    @After fun tearDown() = cleanFiles()

    @Test fun retiredSelectionsFallBackAndPickerOnlyOffersSupportedModels() {
        retiredModels.forEach { model ->
            val oldDownload = modelFile(model, "model.onnx.tmp")
            ThemeManager.setVoiceModelType(context, model.id)
            withSettings { activity ->
                assertEquals(VoiceModelType.DEFAULT.id, ThemeManager.getVoiceModelType(context))
                assertEquals(context.getString(VoiceModelType.DEFAULT.displayNameResId),
                    activity.findViewById<TextView>(R.id.textSelectedModel).text.toString())
                assertTrue(oldDownload.exists())
                activity.findViewById<Button>(R.id.btnSelectModel).performClick()
                val dialog = latestDialog()
                val names = (0 until dialog.listView.count).map {
                    dialog.listView.adapter.getItem(it).toString()
                }
                assertEquals(3, names.size)
                listOf(VoiceModelType.SENSE_VOICE, VoiceModelType.QWEN3_ASR, VoiceModelType.QWEN3_ASR_1_7B).forEach {
                    assertTrue(names.any { name -> name.startsWith(context.getString(it.displayNameResId)) })
                }
                retiredModels.forEach {
                    assertFalse(names.any { name -> name.startsWith(context.getString(it.displayNameResId)) })
                }
                dialog.dismiss()
            }
        }
    }

    @Test fun cleanupIsHiddenWhenOnlySupportedModelFilesExist() {
        modelFile(VoiceModelType.SENSE_VOICE, "model.int8.onnx")
        withSettings { activity ->
            assertEquals(View.GONE, activity.findViewById<Button>(R.id.btnDeleteOldVoiceModels).visibility)
        }
    }

    @Test fun cleanupDeletesIncompleteModelsAndPreservesCurrentModelAndSharedVad() {
        // Neither retired model is complete: no tokenizer, version file, or valid weights.
        // Cleanup must still offer both, even though isModelDownloaded returns false.
        val whisper = modelFile(VoiceModelType.WHISPER_CANTONESE, "small-decoder.int8.onnx.tmp")
        val u2pp = modelFile(VoiceModelType.U2PP_CONFORMER_YUE, "model.int8.onnx")
        val current = modelFile(VoiceModelType.QWEN3_ASR, "encoder.int8.onnx")
        val vad = File(context.filesDir, ModelDownloadManager.VAD_MODEL_FILE).apply { writeText("shared") }
        ThemeManager.setVoiceModelType(context, VoiceModelType.QWEN3_ASR.id)
        assertFalse(ModelDownloadManager.isModelDownloaded(context, VoiceModelType.WHISPER_CANTONESE))
        assertFalse(ModelDownloadManager.isModelDownloaded(context, VoiceModelType.U2PP_CONFORMER_YUE))

        withSettings { activity ->
            val cleanup = activity.findViewById<Button>(R.id.btnDeleteOldVoiceModels)
            assertEquals(View.VISIBLE, cleanup.visibility)
            chooseCleanupModel(activity, VoiceModelType.WHISPER_CANTONESE)
            respondToConfirmation(AlertDialog.BUTTON_POSITIVE)
            assertFalse(whisper.exists())
            assertTrue(u2pp.exists())
            assertEquals(View.VISIBLE, cleanup.visibility)

            chooseCleanupModel(activity, VoiceModelType.U2PP_CONFORMER_YUE)
            respondToConfirmation(AlertDialog.BUTTON_POSITIVE)
            assertFalse(u2pp.exists())
            assertEquals(View.GONE, cleanup.visibility)
            assertTrue(current.exists())
            assertEquals("shared", vad.readText())
            assertEquals(VoiceModelType.QWEN3_ASR.id, ThemeManager.getVoiceModelType(context))
        }
    }

    @Test fun cancelingCleanupKeepsOldDownload() {
        val oldDownload = modelFile(VoiceModelType.WHISPER_CANTONESE, "small-encoder.int8.onnx")
        withSettings { activity ->
            chooseCleanupModel(activity, VoiceModelType.WHISPER_CANTONESE)
            respondToConfirmation(AlertDialog.BUTTON_NEGATIVE)
            assertTrue(oldDownload.exists())
            assertEquals(View.VISIBLE, activity.findViewById<Button>(R.id.btnDeleteOldVoiceModels).visibility)
        }
    }

    @Test fun retiredModelsCannotBeDownloadedAgain() {
        retiredModels.forEach { model ->
            val finished = CountDownLatch(1)
            val error = AtomicReference<String>()
            ModelDownloadManager.downloadModel(context, model, object : ModelDownloadManager.DownloadCallback {
                override fun onProgress(bytesDownloaded: Long, totalBytes: Long, currentFile: String) = Unit
                override fun onComplete() = finished.countDown()
                override fun onError(message: String) {
                    error.set(message)
                    finished.countDown()
                }
            })
            assertTrue(finished.await(5, TimeUnit.SECONDS))
            assertEquals(context.getString(R.string.voice_model_retired), error.get())
            assertFalse(File(context.filesDir, model.modelDir).exists())
        }
    }

    private fun chooseCleanupModel(activity: SettingsActivity, model: VoiceModelType) {
        activity.findViewById<Button>(R.id.btnDeleteOldVoiceModels).performClick()
        val list = latestDialog().listView
        val index = (0 until list.count).first {
            list.adapter.getItem(it).toString() == context.getString(model.displayNameResId)
        }
        list.performItemClick(null, index, list.adapter.getItemId(index))
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun respondToConfirmation(which: Int) {
        latestDialog().getButton(which).performClick()
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun latestDialog() = ShadowDialog.getLatestDialog() as AlertDialog

    private fun withSettings(block: (SettingsActivity) -> Unit) {
        val controller = Robolectric.buildActivity(SettingsActivity::class.java).setup()
        try {
            block(controller.get())
        } finally {
            controller.pause().stop().destroy()
        }
    }

    private fun modelFile(model: VoiceModelType, name: String): File =
        File(context.filesDir, "${model.modelDir}/$name").apply {
            parentFile!!.mkdirs()
            writeText("download")
        }

    private fun cleanFiles() {
        VoiceModelType.entries.forEach { File(context.filesDir, it.modelDir).deleteRecursively() }
        File(context.filesDir, ModelDownloadManager.VAD_MODEL_FILE).delete()
    }
}
