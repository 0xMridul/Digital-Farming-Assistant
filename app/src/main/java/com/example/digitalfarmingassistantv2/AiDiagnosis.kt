
package com.example.digitalfarmingassistantv2

import android.graphics.Bitmap
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * DeepSeek vision settings. Paste your key below.
 * WARNING: a key stored inside an app can be extracted from the APK. Use it for demos with a small
 * balance only. For a real release, keep the key on a small server (proxy) and call that instead.
 */
object AiConfig {
    const val API_KEY = "sk-4ae0461449404cdbb3755ba81e36b9ad"
    const val BASE_URL = "https://api.deepseek.com"

    /** Tried in order. If the first is rejected (HTTP 400 / 404) the next one is tried. */
    val MODELS = listOf("deepseek-flash", "deepseek-v4-flash-vision-exp")

    fun isFilled(): Boolean = !API_KEY.startsWith("PASTE") && API_KEY.length > 10
}

data class AiResult(
    val code: String,
    val disease: Int,
    val rot: Int,
    val pest: Int,
    val problem: String,
    val adviceEn: String,
    val adviceBn: String
)

private class ModelRejected(msg: String) : IOException(msg)

private val AI_PROMPT = """
You are a crop health assistant for small farmers in Bangladesh.
Look at the photo. If it does not clearly show a crop plant, leaf, fruit or stem, answer with status UNCLEAR.
Reply with ONLY one JSON object (no markdown, no extra text) with exactly these keys:
{
  "status": "HEALTHY" or "EARLY" or "SEVERE" or "UNCLEAR",
  "problem": "most likely problem in a few words, or None",
  "disease_pct": integer 0-100,
  "rot_pct": integer 0-100,
  "pest_pct": integer 0-100,
  "advice_en": "2-4 short sentences: what to do now and how to prevent it",
  "advice_bn": "the same advice in Bengali"
}
The three percentages are your rough estimate of how much of the visible plant tissue shows disease spots, rot, and pest damage. Their sum must not exceed 100.
Do not give pesticide names or doses. Tell the farmer to confirm with the local agriculture extension officer before using any chemical.
""".trimIndent()

/** Sends the photo to DeepSeek and returns the parsed answer. Throws if anything fails. */
fun aiAnalyze(path: String): AiResult {
    val b64 = imageToBase64(path)
    if (b64.isEmpty()) throw IOException("Could not read the image")
    var last: Exception = IOException("No AI model available")
    for (m in AiConfig.MODELS) {
        try {
            return callDeepSeek(m, b64)
        } catch (e: ModelRejected) {
            last = e
        }
    }
    throw last
}

private fun imageToBase64(path: String): String {
    val bmp = loadBitmap(path, 1024) ?: return ""
    val bos = ByteArrayOutputStream()
    bmp.compress(Bitmap.CompressFormat.JPEG, 80, bos)
    return Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP)
}

private fun callDeepSeek(model: String, b64: String): AiResult {
    val content = JSONArray()
        .put(JSONObject().put("type", "text").put("text", AI_PROMPT))
        .put(
            JSONObject().put("type", "image_url")
                .put("image_url", JSONObject().put("url", "data:image/jpeg;base64," + b64))
        )
    val msg = JSONObject().put("role", "user").put("content", content)
    val body = JSONObject()
        .put("model", model)
        .put("messages", JSONArray().put(msg))
        .put("temperature", 0.2)
        .put("max_tokens", 1500)
        .put("stream", false)

    val conn = URL(AiConfig.BASE_URL + "/chat/completions").openConnection() as HttpURLConnection
    try {
        conn.requestMethod = "POST"
        conn.connectTimeout = 20000
        conn.readTimeout = 90000
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("Authorization", "Bearer " + AiConfig.API_KEY)
        conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }

        val code = conn.responseCode
        if (code == 200) {
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            return parseAi(text)
        }
        val err = try {
            conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
        } catch (e: Exception) {
            ""
        }
        val shortErr = "HTTP " + code + " " + err.take(160)
        if (code == 400 || code == 404) throw ModelRejected(shortErr)
        if (code == 401) throw IOException("HTTP 401: the DeepSeek API key was rejected")
        if (code == 402) throw IOException("HTTP 402: the DeepSeek account has no balance")
        throw IOException(shortErr)
    } finally {
        conn.disconnect()
    }
}

private fun parseAi(raw: String): AiResult {
    val root = JSONObject(raw)
    val message = root.getJSONArray("choices").getJSONObject(0).getJSONObject("message")
    val text = message.optString("content", "")
    val a = text.indexOf('{')
    val e = text.lastIndexOf('}')
    if (a < 0 || e <= a) throw IOException("The AI reply was not understandable")
    val o = JSONObject(text.substring(a, e + 1))

    var status = o.optString("status", "UNCLEAR").trim().uppercase()
    if (status != "HEALTHY" && status != "EARLY" && status != "SEVERE") status = "UNCLEAR"

    var d = o.optInt("disease_pct", 0).coerceIn(0, 100)
    var r = o.optInt("rot_pct", 0).coerceIn(0, 100)
    var p = o.optInt("pest_pct", 0).coerceIn(0, 100)
    val sum = d + r + p
    if (sum > 100) {
        d = d * 100 / sum
        r = r * 100 / sum
        p = p * 100 / sum
    }
    if (status == "UNCLEAR") {
        d = 0
        r = 0
        p = 0
    }
    var problem = o.optString("problem", "").trim()
    if (problem.equals("none", ignoreCase = true)) problem = ""
    return AiResult(
        status, d, r, p, problem.take(80),
        o.optString("advice_en", "").trim(),
        o.optString("advice_bn", "").trim()
    )
}
