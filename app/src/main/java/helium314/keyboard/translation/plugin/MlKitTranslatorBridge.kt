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
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.ZipInputStream

class MlKitTranslatorBridge(private val context: Context) {
    private val modelReady = ConcurrentHashMap<String, Boolean>()
    private val downloading = ConcurrentHashMap<String, AtomicBoolean>()

    init {
        loadNativeLibrary(context)
        ensureMlKitInitialized(context)
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
            Log.i(TAG, "MlKitContext successfully initialized with NaturalLanguageTranslateRegistrar on ${ctx.applicationContext.javaClass.name}")
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
            Tasks.await(translator.translate(text), 15, TimeUnit.SECONDS)
        } catch (e: Throwable) {
            Log.e(TAG, "ML Kit translation execution error: ${e.message}", e)
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
        if (tag == "en" || checkModelReady(tag)) {
            listener.onComplete(true, null)
            return
        }

        Thread {
            try {
                val modelName = try {
                    com.google.mlkit.nl.translate.internal.zzac.zzb(tag)
                } catch (e: Throwable) {
                    val normalized = if (tag == "he") "iw" else tag
                    if (normalized == "en") "en" else "en_$normalized"
                }

                val targetDir = File(context.noBackupFilesDir ?: context.filesDir, "com.google.mlkit.translate.models/$modelName")
                targetDir.mkdirs()

                val urls = listOf(
                    "https://dl.google.com/translate/offline/v5/high/r29/$modelName.zip",
                    "https://redirector.gvt1.com/edgedl/translate/offline/v5/high/r29/$modelName.zip"
                )

                var downloaded = false
                var lastErr: Exception? = null

                for (urlStr in urls) {
                    try {
                        val url = java.net.URL(urlStr)
                        val conn = url.openConnection() as java.net.HttpURLConnection
                        conn.setRequestProperty("User-Agent", "TRANSLATE_OPM5_TEST_1")
                        conn.connectTimeout = 30000
                        conn.readTimeout = 60000
                        conn.instanceFollowRedirects = true

                        if (conn.responseCode == java.net.HttpURLConnection.HTTP_OK) {
                            val zipIn = java.util.zip.ZipInputStream(conn.inputStream.buffered())
                            var entry = zipIn.nextEntry
                            while (entry != null) {
                                val entryName = entry.name
                                val relPath = if (entryName.contains("/")) entryName.substringAfter("/") else entryName
                                if (relPath.isNotEmpty()) {
                                    val outFile = File(targetDir, relPath)
                                    if (entry.isDirectory) {
                                        outFile.mkdirs()
                                    } else {
                                        outFile.parentFile?.mkdirs()
                                        java.io.FileOutputStream(outFile).use { out ->
                                            zipIn.copyTo(out)
                                        }
                                    }
                                }
                                zipIn.closeEntry()
                                entry = zipIn.nextEntry
                            }
                            zipIn.close()
                            downloaded = true
                            break
                        } else {
                            lastErr = java.io.IOException("HTTP ${conn.responseCode} ${conn.responseMessage}")
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Download attempt failed for $urlStr", e)
                        lastErr = e
                    }
                }

                if (downloaded) {
                    modelReady[tag] = true
                    Log.i(TAG, "Direct ML Kit model download & extraction succeeded for $tag in $targetDir")
                    listener.onComplete(true, null)
                } else {
                    val errMsg = lastErr?.message ?: "Download failed for all CDN endpoints"
                    Log.e(TAG, "All download attempts failed for $tag: $errMsg")
                    listener.onComplete(false, errMsg)
                }
            } catch (e: Throwable) {
                val errMsg = "${e::class.java.simpleName}: ${e.message ?: "Unknown error"}"
                Log.e(TAG, "Direct model download failed for $tag", e)
                listener.onComplete(false, errMsg)
            }
        }.start()
    }

    fun deleteModel(langCode: String): Boolean {
        val tag = TranslateLanguage.fromLanguageTag(langCode) ?: langCode
        return try {
            val modelName = try {
                com.google.mlkit.nl.translate.internal.zzac.zzb(tag)
            } catch (_: Throwable) {
                val normalized = if (tag == "he") "iw" else tag
                if (normalized == "en") "en" else "en_$normalized"
            }
            val targetDir = File(context.noBackupFilesDir ?: context.filesDir, "com.google.mlkit.translate.models/$modelName")
            val deleted = targetDir.deleteRecursively()
            try {
                val model = TranslateRemoteModel.Builder(tag).build()
                RemoteModelManager.getInstance().deleteDownloadedModel(model)
            } catch (_: Throwable) {}
            modelReady[tag] = false
            deleted
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

        // 1. Direct check on disk (reliable for sideloaded/imported models)
        val modelName = try {
            com.google.mlkit.nl.translate.internal.zzac.zzb(tag)
        } catch (_: Throwable) {
            val normalized = if (tag == "he") "iw" else tag
            if (normalized == "en") "en" else "en_$normalized"
        }
        val baseDirs = listOfNotNull(context.noBackupFilesDir, context.filesDir).distinct()
        var hasModelFiles = false
        for (baseDir in baseDirs) {
            val mDir = File(baseDir, "com.google.mlkit.translate.models/$modelName")
            val vZero = File(mDir, "0")
            if (mDir.exists() && mDir.isDirectory) {
                if (!vZero.exists()) vZero.mkdirs()
                mDir.listFiles()?.forEach { file ->
                    if (file.isFile) {
                        val dest = File(vZero, file.name)
                        if (!dest.exists() || dest.length() != file.length()) {
                            try { file.copyTo(dest, overwrite = true) } catch (_: Throwable) {}
                        }
                    }
                }
                vZero.listFiles()?.forEach { file ->
                    if (file.isFile) {
                        val dest = File(mDir, file.name)
                        if (!dest.exists() || dest.length() != file.length()) {
                            try { file.copyTo(dest, overwrite = true) } catch (_: Throwable) {}
                        }
                    }
                }
                if (vZero.listFiles()?.any { it.isFile } == true || mDir.listFiles()?.any { it.isFile } == true) {
                    hasModelFiles = true
                }
            }
        }

        if (hasModelFiles) {
            modelReady[tag] = true
            return true
        }

        // 2. Fallback to RemoteModelManager check
        return try {
            val model = TranslateRemoteModel.Builder(tag).build()
            val downloaded = Tasks.await(
                RemoteModelManager.getInstance().isModelDownloaded(model),
                1,
                TimeUnit.SECONDS
            )
            modelReady[tag] = downloaded
            downloaded
        } catch (_: Throwable) {
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
