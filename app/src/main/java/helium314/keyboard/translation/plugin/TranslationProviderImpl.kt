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
        val clean = lang.lowercase().trim()
        // If already a short 2-3 letter ISO code, use directly
        if (clean.length in 2..3 && clean.all { it.isLetter() }) {
            return clean
        }
        return when {
            clean.contains("malayalam") || clean == "ml" -> "ml"
            clean.contains("hindi") || clean == "hi" -> "hi"
            clean.contains("tamil") || clean == "ta" -> "ta"
            clean.contains("telugu") || clean == "te" -> "te"
            clean.contains("kannada") || clean == "kn" -> "kn"
            clean.contains("bengali") || clean == "bn" -> "bn"
            clean.contains("marathi") || clean == "mr" -> "mr"
            clean.contains("gujarati") || clean == "gu" -> "gu"
            clean.contains("punjabi") || clean == "pa" -> "pa"
            clean.contains("spanish") || clean == "es" -> "es"
            clean.contains("french") || clean == "fr" -> "fr"
            clean.contains("german") || clean == "de" -> "de"
            clean.contains("chinese") || clean == "zh" -> "zh-CN"
            clean.contains("japanese") || clean == "ja" -> "ja"
            clean.contains("korean") || clean == "ko" -> "ko"
            clean.contains("russian") || clean == "ru" -> "ru"
            clean.contains("italian") || clean == "it" -> "it"
            clean.contains("portuguese") || clean == "pt" -> "pt"
            clean.contains("arabic") || clean == "ar" -> "ar"
            clean.contains("turkish") || clean == "tr" -> "tr"
            clean.contains("dutch") || clean == "nl" -> "nl"
            clean.contains("polish") || clean == "pl" -> "pl"
            clean.contains("swedish") || clean == "sv" -> "sv"
            clean.contains("greek") || clean == "el" -> "el"
            clean.contains("hebrew") || clean == "he" -> "he"
            clean.contains("thai") || clean == "th" -> "th"
            clean.contains("vietnamese") || clean == "vi" -> "vi"
            clean.contains("indonesian") || clean == "id" -> "id"
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
