// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.translation

import android.content.Context

interface ITranslationProvider {
    fun getInterfaceVersion(): Int = 2
    fun init(context: Context)
    fun translate(text: String, targetLang: String, sourceLang: String = "auto"): String
    fun isAvailable(): Boolean
    fun cleanup()

    fun getSupportedLanguages(): List<String> = emptyList()
    fun isModelDownloaded(langCode: String): Boolean = false
    fun downloadModel(langCode: String, onComplete: (Boolean) -> Unit) { onComplete(false) }
    fun deleteModel(langCode: String): Boolean = false
}
