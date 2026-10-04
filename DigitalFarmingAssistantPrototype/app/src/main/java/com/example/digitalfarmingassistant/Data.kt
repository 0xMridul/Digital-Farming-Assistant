package com.example.digitalfarmingassistant

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom

data class Account(
    val username: String,
    val salt: String,
    val hash: String,
    val isAdmin: Boolean,
    val active: Boolean
)

data class Diagnosis(
    val id: Long,
    val owner: String,
    val imagePath: String,
    val time: Long,
    val done: Boolean,
    val code: String
)

data class Post(
    val id: Long,
    val author: String,
    val text: String,
    val imagePath: String,
    val likedBy: List<String>,
    val comments: List<String>,
    val reportedBy: List<String>,
    val time: Long
)

data class Note(
    val id: Long,
    val owner: String,
    val kind: String,
    val arg: String,
    val time: Long
)

data class LogEntry(val time: Long, val text: String)

data class DayForecast(
    val date: String,
    val max: Double,
    val min: Double,
    val rain: Double,
    val code: Int
)

data class WeatherData(
    val temp: Double,
    val code: Int,
    val wind: Double,
    val days: List<DayForecast>,
    val time: Long
)

data class Alert(val kind: String, val date: String, val value: Double)

fun hashPassword(salt: String, pass: String): String {
    val md = MessageDigest.getInstance("SHA-256")
    return md.digest((salt + pass).toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}

fun newSalt(): String {
    val b = ByteArray(16)
    SecureRandom().nextBytes(b)
    return b.joinToString("") { "%02x".format(it) }
}

private fun JSONArray.strings(): List<String> {
    val out = ArrayList<String>()
    for (i in 0 until length()) out.add(getString(i))
    return out
}

class Db(ctx: Context) {
    private val sp = ctx.getSharedPreferences("dfa_store", Context.MODE_PRIVATE)

    fun getString(k: String): String? = sp.getString(k, null)
    fun putString(k: String, v: String) {
        sp.edit().putString(k, v).apply()
    }

    fun remove(k: String) {
        sp.edit().remove(k).apply()
    }

    private fun <T> read(key: String, f: (JSONObject) -> T): List<T> {
        val s = sp.getString(key, null) ?: return emptyList()
        return try {
            val a = JSONArray(s)
            val out = ArrayList<T>()
            for (i in 0 until a.length()) out.add(f(a.getJSONObject(i)))
            out
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun <T> write(key: String, list: List<T>, f: (T) -> JSONObject) {
        val a = JSONArray()
        for (item in list) a.put(f(item))
        sp.edit().putString(key, a.toString()).apply()
    }

    fun accounts(): List<Account> = read("accounts") { o ->
        Account(
            o.getString("u"), o.getString("s"), o.getString("h"),
            o.getBoolean("a"), o.getBoolean("x")
        )
    }

    fun saveAccounts(l: List<Account>) = write("accounts", l) { a ->
        JSONObject().put("u", a.username).put("s", a.salt).put("h", a.hash)
            .put("a", a.isAdmin).put("x", a.active)
    }

    fun diags(): List<Diagnosis> = read("diags") { o ->
        Diagnosis(
            o.getLong("id"), o.getString("o"), o.getString("p"),
            o.getLong("t"), o.getBoolean("d"), o.optString("c", "")
        )
    }

    fun saveDiags(l: List<Diagnosis>) = write("diags", l) { d ->
        JSONObject().put("id", d.id).put("o", d.owner).put("p", d.imagePath)
            .put("t", d.time).put("d", d.done).put("c", d.code)
    }

    fun posts(): List<Post> = read("posts") { o ->
        Post(
            o.getLong("id"), o.getString("a"), o.getString("x"), o.optString("p", ""),
            o.getJSONArray("l").strings(), o.getJSONArray("c").strings(),
            o.getJSONArray("r").strings(), o.getLong("t")
        )
    }

    fun savePosts(l: List<Post>) = write("posts", l) { p ->
        JSONObject().put("id", p.id).put("a", p.author).put("x", p.text).put("p", p.imagePath)
            .put("l", JSONArray(p.likedBy)).put("c", JSONArray(p.comments))
            .put("r", JSONArray(p.reportedBy)).put("t", p.time)
    }

    fun notes(): List<Note> = read("notes") { o ->
        Note(
            o.getLong("id"), o.getString("o"), o.getString("k"),
            o.optString("a", ""), o.getLong("t")
        )
    }

    fun saveNotes(l: List<Note>) = write("notes", l) { n ->
        JSONObject().put("id", n.id).put("o", n.owner).put("k", n.kind)
            .put("a", n.arg).put("t", n.time)
    }

    fun logs(): List<LogEntry> = read("logs") { o ->
        LogEntry(o.getLong("t"), o.getString("x"))
    }

    fun saveLogs(l: List<LogEntry>) = write("logs", l) { e ->
        JSONObject().put("t", e.time).put("x", e.text)
    }
}
