// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.translation.plugin

interface IMlKitBridge {
    fun translateIfReady(text: String, sourceTag: String, targetTag: String): String?
    fun getSupportedLanguages(): List<String>
    fun isModelDownloaded(langCode: String): Boolean
    fun downloadModel(langCode: String, onComplete: (Boolean) -> Unit)
    fun deleteModel(langCode: String): Boolean
    fun cleanup()
}
