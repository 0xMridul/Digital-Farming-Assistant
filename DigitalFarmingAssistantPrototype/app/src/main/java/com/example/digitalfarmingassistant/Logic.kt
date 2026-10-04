package com.example.digitalfarmingassistant

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.location.Location
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import androidx.core.content.ContextCompat
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ---------------- Language ----------------
fun tr(isBn: Boolean, en: String, bnText: String): String = if (isBn) bnText else en

fun num(d: Double, dec: Int = 0): String = String.format(Locale.US, "%." + dec + "f", d)

fun fmtTime(t: Long): String =
    SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(t))

// ---------------- Connectivity / permissions ----------------
fun checkOnline(ctx: Context): Boolean {
    return try {
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val n = cm.activeNetwork ?: return false
        val c = cm.getNetworkCapabilities(n) ?: return false
        c.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    } catch (e: Exception) {
        false
    }
}

fun hasLocationPermission(ctx: Context): Boolean {
    val fine = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION)
    val coarse = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION)
    return fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED
}

@SuppressLint("MissingPermission")
fun lastKnownLocation(ctx: Context): Location? {
    if (!hasLocationPermission(ctx)) return null
    return try {
        val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        var best: Location? = null
        for (p in lm.getProviders(true)) {
            val l = (try {
                lm.getLastKnownLocation(p)
            } catch (e: Exception) {
                null
            }) ?: continue
            val b = best
            if (b == null || l.time > b.time) best = l
        }
        best
    } catch (e: Exception) {
        null
    }
}

// ---------------- Images ----------------
fun newImageFile(ctx: Context): File {
    val dir = File(ctx.filesDir, "images")
    if (!dir.exists()) dir.mkdirs()
    return File(dir, "img_" + System.nanoTime() + ".jpg")
}

fun copyUriToFile(ctx: Context, uri: Uri): String? {
    return try {
        val f = newImageFile(ctx)
        val input = ctx.contentResolver.openInputStream(uri) ?: return null
        input.use { i -> f.outputStream().use { o -> i.copyTo(o) } }
        f.absolutePath
    } catch (e: Exception) {
        null
    }
}

fun saveBitmap(ctx: Context, bmp: Bitmap): String? {
    return try {
        val f = newImageFile(ctx)
        f.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        f.absolutePath
    } catch (e: Exception) {
        null
    }
}

fun loadBitmap(path: String, max: Int): Bitmap? {
    return try {
        val o = BitmapFactory.Options()
        o.inJustDecodeBounds = true
        BitmapFactory.decodeFile(path, o)
        var s = 1
        while (o.outWidth / s > max || o.outHeight / s > max) s *= 2
        val o2 = BitmapFactory.Options()
        o2.inSampleSize = s
        BitmapFactory.decodeFile(path, o2)
    } catch (e: Exception) {
        null
    }
}

/**
 * PROTOTYPE analysis: a simple colour-based estimate (green vs brown/yellow pixels).
 * This is NOT a real AI model. Replace this function with a call to a real model/API.
 */
fun analyzeImage(path: String): String {
    val bmp = loadBitmap(path, 128) ?: return "UNCLEAR"
    var green = 0
    var bad = 0
    val total = bmp.width * bmp.height
    for (y in 0 until bmp.height) {
        for (x in 0 until bmp.width) {
            val p = bmp.getPixel(x, y)
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            if (g > r + 8 && g > b + 8) {
                green++
            } else if (r > b + 25 && r > 60 && r * 10 >= g * 8) {
                bad++
            }
        }
    }
    val plant = green + bad
    if (total == 0 || plant < total * 0.15) return "UNCLEAR"
    val ratio = bad.toDouble() / plant.toDouble()
    return if (ratio < 0.12) "HEALTHY" else if (ratio < 0.35) "EARLY" else "SEVERE"
}

fun diagTitle(code: String, b: Boolean): String = when (code) {
    "HEALTHY" -> tr(b, "Looks healthy", "সুস্থ দেখাচ্ছে")
    "EARLY" -> tr(b, "Possible early stress or fungal spots", "প্রাথমিক রোগ বা পুষ্টির অভাবের সম্ভাবনা")
    "SEVERE" -> tr(b, "Possible serious leaf disease", "গুরুতর পাতার রোগের সম্ভাবনা")
    else -> tr(b, "Could not detect a crop leaf", "ছবিতে ফসলের পাতা শনাক্ত করা যায়নি")
}

fun diagAdvice(code: String, b: Boolean): String = when (code) {
    "HEALTHY" -> tr(
        b,
        "No obvious problem found. Keep checking leaves weekly, avoid over-watering and use balanced fertilizer.",
        "স্পষ্ট কোনো সমস্যা পাওয়া যায়নি। প্রতি সপ্তাহে পাতা পর্যবেক্ষণ করুন, অতিরিক্ত সেচ এড়িয়ে চলুন এবং সুষম সার দিন।"
    )
    "EARLY" -> tr(
        b,
        "Treatment: remove and destroy badly affected leaves, avoid watering from above, and ask your local agriculture extension officer before using any fungicide or fertilizer.\nPrevention: keep space between plants for airflow, use clean seed and rotate crops.",
        "করণীয়: বেশি আক্রান্ত পাতা তুলে ধ্বংস করুন, উপর থেকে পানি দেওয়া এড়িয়ে চলুন এবং ছত্রাকনাশক বা সার ব্যবহারের আগে স্থানীয় কৃষি সম্প্রসারণ কর্মকর্তার পরামর্শ নিন।\nপ্রতিরোধ: বাতাস চলাচলের জন্য গাছের মধ্যে দূরত্ব রাখুন, পরিষ্কার বীজ ব্যবহার করুন এবং ফসল পর্যায়ক্রমে চাষ করুন।"
    )
    "SEVERE" -> tr(
        b,
        "Treatment: isolate or remove heavily infected plants, clean tools between fields, and contact your local agriculture extension officer quickly for a confirmed diagnosis and approved treatment.\nPrevention: use resistant varieties, avoid excess nitrogen, keep good drainage and clean the field after harvest.",
        "করণীয়: মারাত্মক আক্রান্ত গাছ আলাদা করুন বা তুলে ফেলুন, এক জমি থেকে অন্য জমিতে যাওয়ার আগে যন্ত্রপাতি পরিষ্কার করুন এবং নিশ্চিত রোগনির্ণয় ও অনুমোদিত চিকিৎসার জন্য দ্রুত স্থানীয় কৃষি সম্প্রসারণ কর্মকর্তার সাথে যোগাযোগ করুন।\nপ্রতিরোধ: রোগ প্রতিরোধী জাত ব্যবহার করুন, অতিরিক্ত নাইট্রোজেন সার এড়িয়ে চলুন, পানি নিষ্কাশন ভালো রাখুন এবং ফসল তোলার পর জমি পরিষ্কার করুন।"
    )
    else -> tr(
        b,
        "Take a clear, well-lit close-up of one affected leaf on a plain background and try again.",
        "একটি আক্রান্ত পাতার পরিষ্কার ও পর্যাপ্ত আলোতে তোলা ক্লোজ-আপ ছবি সাদামাটা পটভূমিতে তুলে আবার চেষ্টা করুন।"
    )
}

// ---------------- Weather ----------------
fun weatherUrl(lat: Double, lon: Double): String =
    "https://api.open-meteo.com/v1/forecast?latitude=" + String.format(Locale.US, "%.4f", lat) +
            "&longitude=" + String.format(Locale.US, "%.4f", lon) +
            "&current=temperature_2m,weather_code,wind_speed_10m" +
            "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_sum" +
            "&timezone=auto&forecast_days=5"

fun httpGet(url: String): String {
    val c = URL(url).openConnection() as HttpURLConnection
    try {
        c.connectTimeout = 10000
        c.readTimeout = 10000
        c.requestMethod = "GET"
        if (c.responseCode != 200) throw IOException("HTTP " + c.responseCode)
        return c.inputStream.bufferedReader().use { it.readText() }
    } finally {
        c.disconnect()
    }
}

fun parseWeather(json: String, time: Long): WeatherData {
    val root = JSONObject(json)
    val cur = root.getJSONObject("current")
    val d = root.getJSONObject("daily")
    val times = d.getJSONArray("time")
    val codes = d.getJSONArray("weather_code")
    val mx = d.getJSONArray("temperature_2m_max")
    val mn = d.getJSONArray("temperature_2m_min")
    val pr = d.getJSONArray("precipitation_sum")
    val days = ArrayList<DayForecast>()
    for (i in 0 until times.length()) {
        days.add(
            DayForecast(
                times.getString(i),
                mx.optDouble(i, 0.0),
                mn.optDouble(i, 0.0),
                pr.optDouble(i, 0.0),
                codes.optInt(i, 0)
            )
        )
    }
    return WeatherData(
        cur.optDouble("temperature_2m", 0.0),
        cur.optInt("weather_code", 0),
        cur.optDouble("wind_speed_10m", 0.0),
        days,
        time
    )
}

fun weatherText(code: Int, b: Boolean): String = when (code) {
    0 -> tr(b, "☀ Clear", "☀ পরিষ্কার আকাশ")
    1, 2, 3 -> tr(b, "⛅ Partly cloudy", "⛅ আংশিক মেঘলা")
    45, 48 -> tr(b, "🌫 Fog", "🌫 কুয়াশা")
    in 51..57 -> tr(b, "🌦 Drizzle", "🌦 গুঁড়ি গুঁড়ি বৃষ্টি")
    in 61..67 -> tr(b, "🌧 Rain", "🌧 বৃষ্টি")
    in 71..77 -> tr(b, "❄ Snow", "❄ তুষারপাত")
    in 80..82 -> tr(b, "🌧 Rain showers", "🌧 বৃষ্টির ঝাপটা")
    in 95..99 -> tr(b, "⛈ Thunderstorm", "⛈ বজ্রসহ বৃষ্টি")
    else -> "🌡 -"
}

fun alertsFor(w: WeatherData): List<Alert> {
    val l = ArrayList<Alert>()
    for (d in w.days) {
        if (d.rain >= 30.0) l.add(Alert("RAIN", d.date, d.rain))
        if (d.max >= 38.0) l.add(Alert("HEAT", d.date, d.max))
    }
    if (w.wind >= 40.0) {
        val today = if (w.days.isNotEmpty()) w.days[0].date else ""
        l.add(Alert("WIND", today, w.wind))
    }
    return l
}

fun alertText(kind: String, date: String, v: Double, b: Boolean): String = when (kind) {
    "RAIN" -> tr(
        b,
        "⚠ Heavy rain expected on " + date + " (about " + num(v) + " mm). Delay spraying and fertilizer, and clear field drainage.",
        "⚠ " + date + " তারিখে ভারী বৃষ্টির সম্ভাবনা (প্রায় " + num(v) + " মিমি)। স্প্রে ও সার প্রয়োগ পিছিয়ে দিন এবং জমির পানি নিষ্কাশনের ব্যবস্থা করুন।"
    )
    "HEAT" -> tr(
        b,
        "🔥 Very hot day expected on " + date + " (" + num(v) + "°C). Irrigate early morning or evening and protect seedlings.",
        "🔥 " + date + " তারিখে অত্যধিক গরমের সম্ভাবনা (" + num(v) + "°সে)। খুব সকালে বা সন্ধ্যায় সেচ দিন এবং চারা রক্ষা করুন।"
    )
    else -> tr(
        b,
        "💨 Strong wind now (" + num(v) + " km/h). Avoid spraying and secure tall crops.",
        "💨 এখন প্রবল বাতাস (" + num(v) + " কিমি/ঘণ্টা)। স্প্রে এড়িয়ে চলুন এবং উঁচু ফসল বেঁধে রাখুন।"
    )
}

fun noteText(n: Note, b: Boolean): String = when (n.kind) {
    "WELCOME" -> tr(
        b,
        "Welcome to Digital Farming Assistant! You can diagnose crops, check weather and join the forum.",
        "ডিজিটাল ফার্মিং অ্যাসিস্ট্যান্টে স্বাগতম! এখানে ফসলের রোগ নির্ণয়, আবহাওয়া দেখা ও ফোরামে অংশ নেওয়া যায়।"
    )
    "DIAG" -> tr(b, "🌾 Your crop diagnosis is ready.", "🌾 আপনার ফসলের ডায়াগনোসিস প্রস্তুত।")
    "COMMENT" -> tr(b, "💬 " + n.arg + " commented on your post.", "💬 " + n.arg + " আপনার পোস্টে মন্তব্য করেছেন।")
    "LIKE" -> tr(b, "👍 " + n.arg + " liked your post.", "👍 " + n.arg + " আপনার পোস্ট পছন্দ করেছেন।")
    "ALERT" -> {
        val p = n.arg.split("|")
        if (p.size >= 3) alertText(p[0], p[1], p[2].toDoubleOrNull() ?: 0.0, b) else ""
    }
    else -> n.arg
}

// ---------------- Crop library (bundled = works offline) ----------------
data class Bi(val en: String, val bnText: String) {
    fun of(isBn: Boolean): String = if (isBn) bnText else en
}

data class CropInfo(
    val name: Bi,
    val growing: Bi,
    val planting: Bi,
    val fertilizer: Bi,
    val pests: Bi,
    val diseases: Bi,
    val tips: Bi
)

fun CropInfo.sections(b: Boolean): List<Triple<Int, String, String>> = listOf(
    Triple(1, tr(b, "Growing conditions", "চাষের পরিবেশ"), growing.of(b)),
    Triple(2, tr(b, "Planting", "রোপণ / বপন"), planting.of(b)),
    Triple(3, tr(b, "Fertilizer", "সার"), fertilizer.of(b)),
    Triple(4, tr(b, "Common pests", "সাধারণ পোকামাকড়"), pests.of(b)),
    Triple(5, tr(b, "Diseases", "রোগ"), diseases.of(b)),
    Triple(6, tr(b, "Farming tips", "চাষের পরামর্শ"), tips.of(b))
)

fun CropInfo.matches(q: String): Boolean {
    if (q.isBlank()) return true
    val all = listOf(name, growing, planting, fertilizer, pests, diseases, tips)
    return all.any { it.en.contains(q, ignoreCase = true) || it.bnText.contains(q, ignoreCase = true) }
}

val LIBRARY: List<CropInfo> = listOf(
    CropInfo(
        Bi("Rice", "ধান"),
        Bi("Warm, humid climate (about 20-35°C) with standing water in the field for most of the season.",
            "উষ্ণ ও আর্দ্র আবহাওয়া (প্রায় ২০-৩৫°সে) এবং মৌসুমের বেশিরভাগ সময় জমিতে দাঁড়ানো পানি।"),
        Bi("Transplant healthy 20-30 day old seedlings. Common seasons: Aus, Aman (Jul-Aug) and Boro (Dec-Jan).",
            "২০-৩০ দিন বয়সী সুস্থ চারা রোপণ করুন। প্রধান মৌসুম: আউশ, আমন (জুলাই-আগস্ট) ও বোরো (ডিসেম্বর-জানুয়ারি)।"),
        Bi("Commonly urea, TSP, MoP, gypsum and zinc. Apply urea in split doses. Follow a soil test or local recommendation.",
            "সাধারণত ইউরিয়া, টিএসপি, এমওপি, জিপসাম ও জিংক। ইউরিয়া কয়েক কিস্তিতে দিন। মাটি পরীক্ষা বা স্থানীয় সুপারিশ অনুসরণ করুন।"),
        Bi("Stem borer, brown planthopper, rice bug, leaf folder.",
            "মাজরা পোকা, বাদামি গাছফড়িং, গান্ধি পোকা, পাতা মোড়ানো পোকা।"),
        Bi("Blast, bacterial leaf blight, sheath blight, tungro.",
            "ব্লাস্ট, ব্যাকটেরিয়াজনিত পাতা পোড়া, শীথ ব্লাইট, টুংরো।"),
        Bi("Use certified seed, keep fields weed-free and avoid excess nitrogen, which raises disease risk.",
            "প্রত্যয়িত বীজ ব্যবহার করুন, জমি আগাছামুক্ত রাখুন এবং অতিরিক্ত নাইট্রোজেন সার এড়িয়ে চলুন, কারণ এতে রোগের ঝুঁকি বাড়ে।")
    ),
    CropInfo(
        Bi("Potato", "আলু"),
        Bi("Cool, dry winter weather (about 15-25°C) and well-drained loamy soil.",
            "শীতল ও শুকনো আবহাওয়া (প্রায় ১৫-২৫°সে) এবং ভালো পানি নিষ্কাশন হয় এমন দোআঁশ মাটি।"),
        Bi("Plant seed tubers from mid-November to early December. Earth up plants after about 30 days.",
            "নভেম্বরের মাঝামাঝি থেকে ডিসেম্বরের শুরুর মধ্যে বীজ আলু লাগান। প্রায় ৩০ দিন পর গোড়ায় মাটি তুলে দিন।"),
        Bi("Commonly urea, TSP, MoP and organic manure. Follow soil test advice.",
            "সাধারণত ইউরিয়া, টিএসপি, এমওপি ও জৈব সার। মাটি পরীক্ষার পরামর্শ অনুসরণ করুন।"),
        Bi("Aphids, cutworm, potato tuber moth.",
            "জাব পোকা, কাটুই পোকা, আলুর মথ।"),
        Bi("Late blight, early blight, bacterial wilt.",
            "লেট ব্লাইট, আর্লি ব্লাইট, ব্যাকটেরিয়াজনিত ঢলে পড়া রোগ।"),
        Bi("Watch for late blight in cool, foggy, humid weather and avoid waterlogging.",
            "ঠান্ডা, কুয়াশাচ্ছন্ন ও আর্দ্র আবহাওয়ায় লেট ব্লাইটের দিকে নজর রাখুন এবং জলাবদ্ধতা এড়িয়ে চলুন।")
    ),
    CropInfo(
        Bi("Tomato", "টমেটো"),
        Bi("Sunny weather (about 20-27°C) and fertile, well-drained sandy loam soil.",
            "রৌদ্রোজ্জ্বল আবহাওয়া (প্রায় ২০-২৭°সে) এবং উর্বর, পানি নিষ্কাশনযোগ্য বেলে দোআঁশ মাটি।"),
        Bi("Transplant 25-30 day old seedlings in October-November (winter). Stake plants as they grow.",
            "অক্টোবর-নভেম্বরে (শীতকাল) ২৫-৩০ দিন বয়সী চারা রোপণ করুন। গাছ বড় হলে খুঁটি দিয়ে বেঁধে দিন।"),
        Bi("Compost or cow dung plus balanced NPK fertilizer. Follow soil test advice.",
            "কম্পোস্ট বা গোবর সারের সাথে সুষম এনপিকে সার। মাটি পরীক্ষার পরামর্শ অনুসরণ করুন।"),
        Bi("Fruit borer, whitefly, aphids.",
            "ফল ছিদ্রকারী পোকা, সাদা মাছি, জাব পোকা।"),
        Bi("Leaf curl virus, early blight, bacterial wilt.",
            "পাতা কোঁকড়ানো ভাইরাস, আর্লি ব্লাইট, ব্যাকটেরিয়াজনিত ঢলে পড়া রোগ।"),
        Bi("Water regularly and evenly, remove infected plants early and control whiteflies to limit leaf curl.",
            "নিয়মিত ও সমানভাবে সেচ দিন, আক্রান্ত গাছ আগেই তুলে ফেলুন এবং পাতা কোঁকড়ানো কমাতে সাদা মাছি দমন করুন।")
    ),
    CropInfo(
        Bi("Jute", "পাট"),
        Bi("Warm, humid weather (about 24-37°C), plenty of rain and fertile loamy alluvial soil.",
            "উষ্ণ ও আর্দ্র আবহাওয়া (প্রায় ২৪-৩৭°সে), পর্যাপ্ত বৃষ্টি এবং উর্বর দোআঁশ পলি মাটি।"),
        Bi("Sow seeds from March to May. Thin plants and weed 2-3 times during growth.",
            "মার্চ থেকে মে মাসের মধ্যে বীজ বপন করুন। চারা পাতলা করুন এবং বৃদ্ধির সময় ২-৩ বার আগাছা পরিষ্কার করুন।"),
        Bi("Commonly urea, TSP and MoP in small amounts. Follow soil test advice.",
            "সাধারণত অল্প পরিমাণে ইউরিয়া, টিএসপি ও এমওপি। মাটি পরীক্ষার পরামর্শ অনুসরণ করুন।"),
        Bi("Hairy caterpillar, yellow mite, stem weevil.",
            "বিছা পোকা, হলুদ মাকড়, কাণ্ড ঘুণ পোকা।"),
        Bi("Stem rot, anthracnose, soft rot.",
            "কাণ্ড পচা, অ্যানথ্রাকনোজ, নরম পচা।"),
        Bi("Harvest at about 100-120 days. Retting in clean, slow-moving water gives better fibre quality.",
            "প্রায় ১০০-১২০ দিনে পাট কাটুন। পরিষ্কার ও ধীর গতির পানিতে পাট পচালে আঁশের মান ভালো হয়।")
    )
)
