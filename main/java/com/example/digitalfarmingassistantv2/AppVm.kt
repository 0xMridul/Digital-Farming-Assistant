
package com.example.digitalfarmingassistantv2

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

class AppVm(app: Application) : AndroidViewModel(app) {

    private val ctx: Context = app.applicationContext
    private val db = Db(ctx)
    private val main = Handler(Looper.getMainLooper())
    private val processing = HashSet<Long>()
    private var lastId = 0L

    var bn by mutableStateOf(false)
        private set
    var user by mutableStateOf<Account?>(null)
        private set
    var deviceOnline by mutableStateOf(true)
        private set
    var weather by mutableStateOf<WeatherData?>(null)
        private set
    var weatherState by mutableStateOf("idle")
        private set
    var lat by mutableStateOf(23.8103)
        private set
    var lon by mutableStateOf(90.4125)
        private set
    var usingDefaultLocation by mutableStateOf(true)
        private set
    var toast by mutableStateOf<String?>(null)
    var previewPath by mutableStateOf<String?>(null)
        private set

    val savedCrops = mutableStateListOf<CropInfo>()

    private val cropDb by lazy { CropDb(ctx) }

    fun searchCrops(q: String, category: String, season: String, soil: String): List<CropInfo> {
        return try {
            cropDb.search(q, category, season, soil)
        } catch (e: Exception) {
            CROP_SEED.filter { it.matches(q) && it.passes(category, season, soil) }
        }
    }

    fun cropCount(): Int {
        return try {
            cropDb.count()
        } catch (e: Exception) {
            CROP_SEED.size
        }
    }

    val accounts = mutableStateListOf<Account>()
    val diags = mutableStateListOf<Diagnosis>()
    val posts = mutableStateListOf<Post>()
    val notes = mutableStateListOf<Note>()
    val logs = mutableStateListOf<LogEntry>()

    val isOnline: Boolean
        get() = deviceOnline

    init {
        bn = db.getString("bn") == "1"
        accounts.addAll(db.accounts())
        diags.addAll(db.diags())
        posts.addAll(db.posts())
        notes.addAll(db.notes())
        logs.addAll(db.logs())

        if (accounts.none { it.isAdmin }) {
            val salt = newSalt()
            accounts.add(Account("admin", salt, hashPassword(salt, "admin123"), true, true))
            saveAccounts()
            log("Default admin account created")
        }
        if (db.getString("seeded") == null) {
            val t = System.currentTimeMillis()
            posts.add(Post(nextId(), "Rahim", "My rice leaves have brown spots. What should I do?", "", emptyList(), emptyList(), emptyList(), t - 7200000))
            posts.add(Post(nextId(), "Salma", "When is the best time to plant potato this season?", "", emptyList(), emptyList(), emptyList(), t - 3600000))
            savePosts()
            db.putString("seeded", "1")
        }

        val savedName = db.getString("session")
        if (savedName != null) {
            user = accounts.firstOrNull { it.username == savedName && it.active }
        }

        loadSaved()

        val sl = db.getString("lat")?.toDoubleOrNull()
        val so = db.getString("lon")?.toDoubleOrNull()
        if (sl != null && so != null) {
            lat = sl
            lon = so
            usingDefaultLocation = false
        }

        deviceOnline = checkOnline(ctx)
        viewModelScope.launch {
            ConnectivityObserver(ctx).observe().collect { online ->
                updateDeviceOnline(online)
            }
        }
        loadCachedWeather()
        updateLocation()
        refreshWeather()
        processQueue()
    }

    // ---------- helpers ----------
    private fun nextId(): Long {
        val n = System.currentTimeMillis()
        lastId = if (n > lastId) n else lastId + 1
        return lastId
    }

    fun say(en: String, b: String) {
        toast = tr(bn, en, b)
    }

    private fun saveAccounts() = db.saveAccounts(accounts.toList())
    private fun saveDiags() = db.saveDiags(diags.toList())
    private fun savePosts() = db.savePosts(posts.toList())
    private fun saveNotes() = db.saveNotes(notes.toList())

    private fun log(text: String) {
        logs.add(0, LogEntry(System.currentTimeMillis(), text))
        while (logs.size > 200) logs.removeAt(logs.size - 1)
        db.saveLogs(logs.toList())
    }

    private fun addNote(owner: String, kind: String, arg: String) {
        notes.add(0, Note(nextId(), owner, kind, arg, System.currentTimeMillis()))
        while (notes.size > 200) notes.removeAt(notes.size - 1)
        saveNotes()
    }

    // ---------- language ----------
    fun setLanguage(isBn: Boolean) {
        bn = isBn
        db.putString("bn", if (isBn) "1" else "0")
    }

    // ---------- auth ----------
    fun login(name: String, pass: String): String? {
        val u = name.trim()
        val a = accounts.firstOrNull { it.username.equals(u, ignoreCase = true) }
        if (a == null || hashPassword(a.salt, pass) != a.hash) {
            log("Failed login attempt: " + u)
            return tr(bn, "Wrong name or password", "নাম বা পাসওয়ার্ড ভুল")
        }
        if (!a.active) {
            log("Blocked login (deactivated account): " + a.username)
            return tr(bn, "This account is deactivated", "এই অ্যাকাউন্টটি নিষ্ক্রিয় করা হয়েছে")
        }
        user = a
        loadSaved()
        db.putString("session", a.username)
        log("Login: " + a.username)
        checkAlerts()
        return null
    }

    fun register(name: String, pass: String): String? {
        val u = name.trim()
        if (u.length < 3) return tr(bn, "Name must be at least 3 characters", "নাম কমপক্ষে ৩ অক্ষরের হতে হবে")
        if (pass.length < 6) return tr(bn, "Password must be at least 6 characters", "পাসওয়ার্ড কমপক্ষে ৬ অক্ষরের হতে হবে")
        if (accounts.any { it.username.equals(u, ignoreCase = true) }) {
            return tr(bn, "This name is already taken", "এই নামটি ইতিমধ্যে ব্যবহৃত হয়েছে")
        }
        val salt = newSalt()
        val a = Account(u, salt, hashPassword(salt, pass), false, true)
        accounts.add(a)
        saveAccounts()
        addNote(u, "WELCOME", "")
        log("Registered: " + u)
        user = a
        loadSaved()
        db.putString("session", u)
        checkAlerts()
        return null
    }

    fun logout() {
        val u = user
        if (u != null) log("Logout: " + u.username)
        user = null
        savedCrops.clear()
        previewPath = null
        db.remove("session")
    }

    // ---------- connectivity ----------
    fun updateDeviceOnline(v: Boolean) {
        deviceOnline = v
        onConnectivityChanged()
    }

    private fun onConnectivityChanged() {
        processQueue()
        refreshWeather()
    }

    // ---------- diagnosis ----------
    fun submitUri(uri: Uri) {
        val owner = user?.username ?: return
        Thread {
            val path = copyUriToFile(ctx, uri)
            main.post {
                if (path != null) addDiagnosis(owner, path)
                else say("Could not read the image", "ছবিটি পড়া যায়নি")
            }
        }.start()
    }

    fun submitBitmap(bmp: Bitmap) {
        val owner = user?.username ?: return
        Thread {
            val path = saveBitmap(ctx, bmp)
            main.post {
                if (path != null) addDiagnosis(owner, path)
                else say("Could not save the photo", "ছবিটি সংরক্ষণ করা যায়নি")
            }
        }.start()
    }

    private fun addDiagnosis(owner: String, path: String) {
        val d = Diagnosis(nextId(), owner, path, System.currentTimeMillis(), false, "")
        diags.add(0, d)
        saveDiags()
        log("Diagnosis requested by " + owner)
        if (isOnline) {
            process(d)
        } else {
            say(
                "You are offline. The diagnosis is queued and will run when internet returns.",
                "আপনি অফলাইনে আছেন। ডায়াগনোসিস অপেক্ষমাণ থাকবে এবং ইন্টারনেট এলে চলবে।"
            )
        }
    }

    fun processQueue() {
        if (!isOnline) return
        for (d in diags.filter { !it.done }) process(d)
    }

    private fun process(d: Diagnosis) {
        if (!processing.add(d.id)) return
        Thread {
            val res = analyzeImage(d.imagePath)
            try {
                Thread.sleep(800)
            } catch (e: InterruptedException) {
            }
            main.post {
                val i = diags.indexOfFirst { it.id == d.id }
                if (i >= 0) {
                    diags[i] = diags[i].copy(
                        done = true, code = res.code,
                        disease = res.disease, rot = res.rot, pest = res.pest
                    )
                    saveDiags()
                    addNote(d.owner, "DIAG", d.id.toString())
                    log("Diagnosis completed for " + d.owner)
                }
                processing.remove(d.id)
            }
        }.start()
    }

    // ---------- location & weather ----------
    fun updateLocation() {
        val loc = lastKnownLocation(ctx)
        if (loc != null) {
            lat = loc.latitude
            lon = loc.longitude
            usingDefaultLocation = false
            db.putString("lat", loc.latitude.toString())
            db.putString("lon", loc.longitude.toString())
        }
    }

    fun onPermissionResult() {
        updateLocation()
        if (usingDefaultLocation) {
            say(
                "No location available yet. Using default location (Dhaka).",
                "এখনো অবস্থান পাওয়া যায়নি। ডিফল্ট অবস্থান (ঢাকা) ব্যবহার হচ্ছে।"
            )
        }
        refreshWeather()
    }

    private fun loadCachedWeather() {
        val json = db.getString("wx_json") ?: return
        val t = db.getString("wx_time")?.toLongOrNull() ?: 0L
        try {
            weather = parseWeather(json, t)
            weatherState = "cached"
        } catch (e: Exception) {
        }
    }

    fun refreshWeather() {
        if (!isOnline) {
            weatherState = if (weather != null) "cached" else "nodata"
            return
        }
        weatherState = "loading"
        val la = lat
        val lo = lon
        Thread {
            try {
                val json = httpGet(weatherUrl(la, lo))
                val now = System.currentTimeMillis()
                val w = parseWeather(json, now)
                main.post {
                    weather = w
                    weatherState = "live"
                    db.putString("wx_json", json)
                    db.putString("wx_time", now.toString())
                    checkAlerts()
                }
            } catch (e: Exception) {
                main.post { weatherState = if (weather != null) "cached" else "error" }
            }
        }.start()
    }

    private fun checkAlerts() {
        val u = user ?: return
        val w = weather ?: return
        val key = "seen_" + u.username
        val seen = (db.getString(key) ?: "").split(";").filter { it.isNotEmpty() }.toMutableSet()
        var changed = false
        for (a in alertsFor(w)) {
            val k = a.kind + "," + a.date
            if (seen.add(k)) {
                addNote(u.username, "ALERT", a.kind + "|" + a.date + "|" + a.value.toString())
                showSystemNotification(
                    ctx, (a.kind + a.date).hashCode(),
                    tr(bn, "Weather alert", "আবহাওয়া সতর্কতা"),
                    alertText(a.kind, a.date, a.value, bn)
                )
                changed = true
            }
        }
        if (changed) db.putString(key, seen.joinToString(";"))
    }

    // ---------- image preview (5.1) ----------
    fun stageUri(uri: Uri) {
        Thread {
            val path = copyUriToFile(ctx, uri)
            main.post {
                if (path != null) previewPath = path
                else say("Could not read the image", "ছবিটি পড়া যায়নি")
            }
        }.start()
    }

    fun confirmPreview() {
        val p = previewPath ?: return
        val owner = user?.username ?: return
        previewPath = null
        addDiagnosis(owner, p)
    }

    fun cancelPreview() {
        previewPath = null
    }

    // ---------- saved crops (3.4 / 3.5) ----------
    private fun loadSaved() {
        savedCrops.clear()
        val u = user?.username ?: return
        val names = (db.getString("saved_" + u) ?: "").split(";").filter { it.isNotEmpty() }
        for (n in names) {
            val js = db.getString("cropcache_" + n) ?: continue
            val c = cropFromJson(js)
            if (c != null) savedCrops.add(c)
        }
    }

    fun isSaved(en: String): Boolean = savedCrops.any { it.name.en == en }

    fun toggleSaved(c: CropInfo) {
        val u = user?.username ?: return
        if (isSaved(c.name.en)) {
            savedCrops.removeAll { it.name.en == c.name.en }
            say("Removed from saved crops", "সংরক্ষিত ফসল থেকে সরানো হয়েছে")
        } else {
            db.putString("cropcache_" + c.name.en, cropToJson(c))
            savedCrops.add(c)
            say("Saved on your device for offline use", "অফলাইনে ব্যবহারের জন্য ডিভাইসে সংরক্ষিত হয়েছে")
        }
        db.putString("saved_" + u, savedCrops.joinToString(";") { it.name.en })
    }

    // ---------- demo alert (4.4) ----------
    fun sendTestAlert() {
        val u = user ?: return
        val date = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
        addNote(u.username, "ALERT", "RAIN|" + date + "|45.0")
        showSystemNotification(
            ctx, 4401,
            tr(bn, "Weather alert (test)", "আবহাওয়া সতর্কতা (পরীক্ষা)"),
            alertText("RAIN", date, 45.0, bn)
        )
        say("Test alert sent. See Profile > Notifications.", "পরীক্ষামূলক সতর্কতা পাঠানো হয়েছে। প্রোফাইল > নোটিফিকেশন দেখুন।")
    }

    // ---------- notifications ----------
    fun clearMyNotes() {
        val u = user ?: return
        notes.removeAll { it.owner == u.username }
        saveNotes()
    }

    // ---------- forum ----------
    fun createPost(text: String, uri: Uri?) {
        val author = user?.username ?: return
        val t = text.trim()
        if (t.isEmpty()) return
        Thread {
            val path = if (uri != null) copyUriToFile(ctx, uri) else null
            main.post {
                posts.add(0, Post(nextId(), author, t, path ?: "", emptyList(), emptyList(), emptyList(), System.currentTimeMillis()))
                savePosts()
                log("Post created by " + author)
            }
        }.start()
    }

    fun toggleLike(postId: Long) {
        val me = user?.username ?: return
        val i = posts.indexOfFirst { it.id == postId }
        if (i < 0) return
        val p = posts[i]
        val liked = p.likedBy.contains(me)
        val newList = if (liked) p.likedBy.filter { it != me } else p.likedBy + me
        posts[i] = p.copy(likedBy = newList)
        savePosts()
        if (!liked && p.author != me) addNote(p.author, "LIKE", me)
    }

    fun addComment(postId: Long, text: String) {
        val me = user?.username ?: return
        val t = text.trim()
        if (t.isEmpty()) return
        val i = posts.indexOfFirst { it.id == postId }
        if (i < 0) return
        val p = posts[i]
        posts[i] = p.copy(comments = p.comments + (me + ": " + t))
        savePosts()
        if (p.author != me) addNote(p.author, "COMMENT", me)
    }

    fun reportPost(postId: Long) {
        val me = user?.username ?: return
        val i = posts.indexOfFirst { it.id == postId }
        if (i < 0) return
        val p = posts[i]
        if (p.reportedBy.contains(me)) return
        posts[i] = p.copy(reportedBy = p.reportedBy + me)
        savePosts()
        log("Post reported by " + me + " (author: " + p.author + ")")
        say("Thanks, the post was reported to moderators.", "ধন্যবাদ, পোস্টটি মডারেটরদের কাছে রিপোর্ট করা হয়েছে।")
    }

    fun removePost(postId: Long) {
        val me = user ?: return
        val p = posts.firstOrNull { it.id == postId } ?: return
        if (!me.isAdmin && p.author != me.username) return
        posts.removeAll { it.id == postId }
        savePosts()
        log("Post removed by " + me.username + " (author: " + p.author + ")")
    }

    fun dismissReports(postId: Long) {
        val me = user ?: return
        if (!me.isAdmin) return
        val i = posts.indexOfFirst { it.id == postId }
        if (i < 0) return
        posts[i] = posts[i].copy(reportedBy = emptyList())
        savePosts()
        log("Reports dismissed by " + me.username)
    }

    // ---------- admin ----------
    fun setActive(username: String, active: Boolean) {
        val me = user ?: return
        if (!me.isAdmin) return
        val i = accounts.indexOfFirst { it.username == username }
        if (i < 0) return
        if (accounts[i].isAdmin) return
        accounts[i] = accounts[i].copy(active = active)
        saveAccounts()
        log("Admin " + me.username + " set " + username + " active=" + active)
    }
}
