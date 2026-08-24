// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.translation.plugin

import android.content.Context
import android.util.Log
import android.util.LruCache
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class MlKitTranslatorBridge(private val context: Context) {
    private val modelReady = ConcurrentHashMap<String, Boolean>()
    private val downloading = ConcurrentHashMap<String, AtomicBoolean>()

    init {
        loadNativeLibrary(context)
        ensureWorkManagerInitialized(context)
        ensureMlKitInitialized(context)
    }

    private fun ensureWorkManagerInitialized(ctx: Context) {
        try {
            androidx.work.WorkManager.getInstance(ctx)
        } catch (_: Throwable) {
            try {
                val target = if (ctx is android.content.ContextWrapper && ctx.baseContext != null) ctx.baseContext else ctx
                val config = androidx.work.Configuration.Builder().build()
                androidx.work.WorkManager.initialize(target, config)
                Log.i(TAG, "WorkManager successfully initialized in translation plugin")
            } catch (e: Throwable) {
                Log.w(TAG, "Failed to initialize WorkManager in translation plugin", e)
            }
        }
    }

    private fun loadNativeLibrary(ctx: Context) {
        try {
            System.loadLibrary("translate_jni")
            Log.i(TAG, "Loaded translate_jni via System.loadLibrary")
        } catch (e: Throwable) {
            try {
                val libFile = java.io.File(ctx.filesDir, "plugin_libs/translation/libtranslate_jni.so")
                if (libFile.exists()) {
                    System.load(libFile.absolutePath)
                    Log.i(TAG, "Loaded translate_jni via System.load: ${libFile.absolutePath}")
                }
            } catch (e2: Throwable) {
                Log.e(TAG, "Failed to load translate_jni library", e2)
            }
        }
    }

    private fun ensureMlKitInitialized(ctx: Context) {
        try {
            val mlKitContextClass = Class.forName("com.google.mlkit.common.sdkinternal.MlKitContext")
            val zzbField = mlKitContextClass.getDeclaredField("zzb").apply { isAccessible = true }
            val registrars = listOf(
                com.google.mlkit.common.internal.CommonComponentRegistrar(),
                com.google.mlkit.nl.translate.NaturalLanguageTranslateRegistrar()
            )
            synchronized(mlKitContextClass) {
                zzbField.set(null, null)
                val initMethod = mlKitContextClass.getMethod("initialize", Context::class.java, List::class.java)
                initMethod.invoke(null, ctx.applicationContext, registrars)
            }
            Log.i(TAG, "MlKitContext successfully initialized with NaturalLanguageTranslateRegistrar")
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to initialize MlKitContext with NaturalLanguageTranslateRegistrar", e)
        }
    }

    private val translators = object : LruCache<String, Translator>(2) {
        override fun entryRemoved(
            evicted: Boolean,
            key: String?,
            oldValue: Translator?,
            newValue: Translator?
        ) {
            oldValue?.close()
        }
    }

    fun translateIfReady(text: String, sourceTag: String, targetTag: String): String? {
        val srcMl = TranslateLanguage.fromLanguageTag(sourceTag) ?: return null
        val tgtMl = TranslateLanguage.fromLanguageTag(targetTag) ?: return null

        if (!isReady(srcMl, tgtMl)) {
            ensureModelsDownload(srcMl, tgtMl)
            return null
        }

        val key = "$srcMl->$tgtMl"
        val translator = synchronized(translators) {
            translators.get(key) ?: run {
                val options = TranslatorOptions.Builder()
                    .setSourceLanguage(srcMl)
                    .setTargetLanguage(tgtMl)
                    .build()
                val created = Translation.getClient(options)
                translators.put(key, created)
                created
            }
        }

        return try {
            Tasks.await(translator.translate(text), 2, TimeUnit.SECONDS)
        } catch (e: Throwable) {
            Log.d(TAG, "ML Kit translation execution error: ${e.message}")
            null
        }
    }

    fun getSupportedLanguages(): List<String> {
        return try {
            TranslateLanguage.getAllLanguages().toList()
        } catch (_: Throwable) {
            emptyList()
        }
    }

    fun isModelDownloaded(langCode: String): Boolean {
        val tag = TranslateLanguage.fromLanguageTag(langCode) ?: langCode
        return checkModelReady(tag)
    }

    fun downloadModel(langCode: String, listener: helium314.keyboard.latin.translation.TranslationModelDownloadListener) {
        val tag = TranslateLanguage.fromLanguageTag(langCode) ?: langCode
        Log.i(TAG, "downloadModel requested for langCode=$langCode, tag=$tag")
        val options = TranslatorOptions.Builder()
            .setSourceLanguage(if (tag == TranslateLanguage.ENGLISH) TranslateLanguage.SPANISH else TranslateLanguage.ENGLISH)
            .setTargetLanguage(tag)
            .build()
        val client = Translation.getClient(options)
        val conditions = DownloadConditions.Builder().build()
        Log.i(TAG, "Calling client.downloadModelIfNeeded for $tag")
        client.downloadModelIfNeeded(conditions)
            .addOnSuccessListener {
                Log.i(TAG, "ML Kit model download succeeded for $tag")
                modelReady[tag] = true
                listener.onComplete(true, null)
                try { client.close() } catch (_: Throwable) {}
            }
            .addOnFailureListener { e ->
                val errorMsg = "${e::class.java.simpleName}: ${e.message ?: "Unknown error"}"
                Log.e(TAG, "ML Kit model download failed for $tag: $errorMsg", e)
                modelReady[tag] = false
                listener.onComplete(false, errorMsg)
                try { client.close() } catch (_: Throwable) {}
            }
    }

    fun deleteModel(langCode: String): Boolean {
        val tag = TranslateLanguage.fromLanguageTag(langCode) ?: langCode
        return try {
            val model = TranslateRemoteModel.Builder(tag).build()
            Tasks.await(RemoteModelManager.getInstance().deleteDownloadedModel(model), 5, TimeUnit.SECONDS)
            modelReady[tag] = false
            true
        } catch (e: Throwable) {
            Log.e(TAG, "ML Kit model delete failed for $tag: ${e.message}", e)
            false
        }
    }

    private fun isReady(sourceTag: String, targetTag: String): Boolean {
        val srcReady = sourceTag == "en" || checkModelReady(sourceTag)
        val tgtReady = targetTag == "en" || checkModelReady(targetTag)
        return srcReady && tgtReady
    }

    private fun checkModelReady(tag: String): Boolean {
        if (tag == "en") return true
        modelReady[tag]?.let { return it }

        return try {
            val model = TranslateRemoteModel.Builder(tag).build()
            val downloaded = Tasks.await(
                RemoteModelManager.getInstance().isModelDownloaded(model),
                5,
                TimeUnit.SECONDS
            )
            modelReady[tag] = downloaded
            downloaded
        } catch (e: Throwable) {
            Log.w(TAG, "ML Kit checkModelReady failed for $tag: ${e.message}")
            modelReady[tag] = false
            false
        }
    }

    private fun ensureModelsDownload(sourceTag: String, targetTag: String) {
        if (sourceTag != "en") ensureModelDownload(sourceTag)
        if (targetTag != "en") ensureModelDownload(targetTag)
    }

    private fun ensureModelDownload(tag: String) {
        if (tag == "en" || modelReady[tag] == true) return

        val flag = downloading.getOrPut(tag) { AtomicBoolean(false) }
        if (!flag.compareAndSet(false, true)) return

        val model = TranslateRemoteModel.Builder(tag).build()
        val conditions = DownloadConditions.Builder().build()

        RemoteModelManager.getInstance()
            .download(model, conditions)
            .addOnSuccessListener {
                modelReady[tag] = true
                flag.set(false)
                Log.i(TAG, "ML Kit model downloaded: $tag")
            }
            .addOnFailureListener { e ->
                modelReady[tag] = false
                flag.set(false)
                Log.w(TAG, "ML Kit model download failed for $tag: ${e.message}")
            }
    }

    fun cleanup() {
        synchronized(translators) {
            translators.evictAll()
        }
        modelReady.clear()
        downloading.clear()
    }

    companion object {
        private const val TAG = "MlKitTranslatorBridge"
    }
}
