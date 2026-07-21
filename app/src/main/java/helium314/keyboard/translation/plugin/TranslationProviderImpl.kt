// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.translation.plugin

import android.content.Context
import helium314.keyboard.latin.translation.ITranslationProvider
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import org.json.JSONArray

class TranslationProviderImpl : ITranslationProvider {
    private var appContext: Context? = null
    private var initialized = false

    override fun getInterfaceVersion(): Int = 1

    override fun init(context: Context) {
        this.appContext = context.applicationContext
        this.initialized = true
    }

    override fun isAvailable(): Boolean = initialized

    override fun translate(text: String, targetLang: String, sourceLang: String): String {
        if (text.isBlank()) return text
        return try {
            val langCode = mapLanguageToCode(targetLang)
            val encodedText = URLEncoder.encode(text, "UTF-8")
            val urlStr = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=$sourceLang&tl=$langCode&dt=t&q=$encodedText"
            
            val url = URL(urlStr)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", "Mozilla/5.0")
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            conn.connect()

            if (conn.responseCode == 200) {
                val responseStr = conn.inputStream.bufferedReader().use { it.readText() }
                parseGoogleTranslateResponse(responseStr) ?: text
            } else {
                text
            }
        } catch (e: Throwable) {
            e.printStackTrace()
            text
        }
    }

    override fun cleanup() {
        appContext = null
        initialized = false
    }

    private fun mapLanguageToCode(lang: String): String {
        val lower = lang.lowercase().trim()
        return when {
            lower.contains("spanish") || lower == "es" -> "es"
            lower.contains("french") || lower == "fr" -> "fr"
            lower.contains("german") || lower == "de" -> "de"
            lower.contains("chinese") || lower == "zh" -> "zh-CN"
            lower.contains("japanese") || lower == "ja" -> "ja"
            lower.contains("korean") || lower == "ko" -> "ko"
            lower.contains("russian") || lower == "ru" -> "ru"
            lower.contains("italian") || lower == "it" -> "it"
            lower.contains("portuguese") || lower == "pt" -> "pt"
            lower.contains("hindi") || lower == "hi" -> "hi"
            lower.contains("arabic") || lower == "ar" -> "ar"
            else -> "en"
        }
    }

    private fun parseGoogleTranslateResponse(jsonStr: String): String? {
        return try {
            val outerArray = JSONArray(jsonStr)
            val sentencesArray = outerArray.getJSONArray(0)
            val sb = StringBuilder()
            for (i in 0 until sentencesArray.length()) {
                val sentence = sentencesArray.getJSONArray(i)
                sb.append(sentence.getString(0))
            }
            sb.toString()
        } catch (e: Throwable) {
            null
        }
    }
}
