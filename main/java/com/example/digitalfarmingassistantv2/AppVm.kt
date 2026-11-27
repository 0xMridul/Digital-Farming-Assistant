
package com.example.digitalfarmingassistantv2

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Base64
import com.google.firebase.FirebaseApp
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.Query
import com.google.firebase.database.ValueEventListener
import java.io.ByteArrayOutputStream
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel

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
    var simOffline by mutableStateOf(false)
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

    // cloud forum state (must be declared before init)
    var cloudReady by mutableStateOf(false)
        private set
    var cloudError by mutableStateOf<String?>(null)
        private set
    /** The logged-in account name. Posts, likes and reports are tied to it. */
    val myUid: String
        get() = user?.username ?: ""
    val cloudUsers = mutableStateListOf<Account>()
    private var cloudOn = false
    private var usersRef: DatabaseReference? = null
    private var usersListener: ValueEventListener? = null
    private var rtdbInstance: FirebaseDatabase? = null
    private val imageLoading = HashSet<String>()
    /** Photos of posts, loaded on demand (post id -> Base64 JPEG). */
    val imageCache = mutableStateMapOf<String, String>()
    /** Simple moderation: the local "admin" account can delete posts and dismiss reports. */
    val cloudAdmin: Boolean
        get() = user?.isAdmin == true
    private var postsQuery: Query? = null
    private var postsListener: ValueEventListener? = null

    val isOnline: Boolean
        get() = deviceOnline && !simOffline

    /** What the admin dashboard lists: every cloud account (or the local ones if there is no cloud). */
    val userList: List<Account>
        get() = if (cloudUsers.isEmpty()) accounts.toList()
        else (accounts.filter { it.isAdmin } + cloudUsers).distinctBy { it.username.lowercase() }

    init {
        bn = db.getString("bn") == "1"
        accounts.addAll(db.accounts())
        diags.addAll(db.diags())
        notes.addAll(db.notes())
        logs.addAll(db.logs())

        if (accounts.none { it.isAdmin }) {
            val salt = newSalt()
            accounts.add(Account("admin", salt, hashPassword(salt, "admin123"), true, true))
            saveAccounts()
            log("Default admin account created")
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
        loadCachedWeather()
        updateLocation()
        refreshWeather()
        processQueue()
        startCloud()
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

    // ---------- auth (accounts are stored in the cloud database: node "users") ----------
    /** Realtime Database keys cannot contain . $ # [ ] / so those characters are encoded. */
    private fun safeKey(name: String): String {
        val sb = StringBuilder()
        for (ch in name.trim().lowercase()) {
            if (ch == '.' || ch == '$' || ch == '#' || ch == '[' || ch == ']' || ch == '/' || ch == '%' ||
                ch.code < 32 || ch.code == 127
            ) {
                sb.append('%').append(Integer.toHexString(ch.code).uppercase().padStart(2, '0'))
            } else {
                sb.append(ch)
            }
        }
        return sb.toString()
    }

    private fun dbFailText(e: Exception?): String {
        val m = e?.message ?: ""
        val low = m.lowercase()
        return if (low.contains("offline") || low.contains("unavailable") || low.contains("timeout")) {
            tr(
                bn,
                "Cannot reach the database. Check your internet and the DATABASE_URL in CloudConfig.kt.",
                "ডাটাবেসে পৌঁছানো যায়নি। ইন্টারনেট এবং CloudConfig.kt-এর DATABASE_URL পরীক্ষা করুন।"
            )
        } else {
            tr(bn, "Could not reach the database: ", "ডাটাবেসে পৌঁছানো যায়নি: ") + m
        }
    }

    private fun deactivatedText(): String =
        tr(bn, "This account is deactivated", "এই অ্যাকাউন্টটি নিষ্ক্রিয় করা হয়েছে")

    private fun wrongText(): String =
        tr(bn, "Wrong name or password", "নাম বা পাসওয়ার্ড ভুল")

    private fun takenText(): String =
        tr(bn, "This name is already taken", "এই নামটি ইতিমধ্যে ব্যবহৃত হয়েছে")

    /** Remembers the account on this phone (so login also works offline) and opens the app. */
    private fun finishLogin(a: Account) {
        accounts.removeAll { it.username.equals(a.username, ignoreCase = true) && !it.isAdmin }
        if (!accounts.any { it.username.equals(a.username, ignoreCase = true) }) accounts.add(a)
        saveAccounts()
        user = a
        loadSaved()
        db.putString("session", a.username)
        log("Login: " + a.username)
        checkAlerts()
        if (a.isAdmin) startUsersListener()
    }

    private fun localLogin(u: String, pass: String): String? {
        val a = accounts.firstOrNull { it.username.equals(u, ignoreCase = true) }
        if (a == null || hashPassword(a.salt, pass) != a.hash) {
            log("Failed login attempt: " + u)
            return wrongText()
        }
        if (!a.active) {
            log("Blocked login (deactivated account): " + a.username)
            return deactivatedText()
        }
        finishLogin(a)
        return null
    }

    fun login(name: String, pass: String, done: (String?) -> Unit) {
        val u = name.trim()
        if (u.isEmpty() || !cloudOn || !isOnline) {
            done(localLogin(u, pass))
            return
        }
        try {
            rtdb().getReference("users").child(safeKey(u)).get()
                .addOnSuccessListener { d ->
                    if (!d.exists()) {
                        done(localLogin(u, pass))
                    } else {
                        val salt = d.child("salt").getValue(String::class.java) ?: ""
                        val hash = d.child("hash").getValue(String::class.java) ?: ""
                        val display = d.child("username").getValue(String::class.java) ?: u
                        val active = (d.child("active").value as? Boolean) ?: true
                        if (hashPassword(salt, pass) != hash) {
                            log("Failed login attempt: " + u)
                            done(wrongText())
                        } else if (!active) {
                            log("Blocked login (deactivated account): " + display)
                            done(deactivatedText())
                        } else {
                            finishLogin(Account(display, salt, hash, false, true))
                            done(null)
                        }
                    }
                }
                .addOnFailureListener { e ->
                    // Offline or unreachable: accounts already used on this phone still work.
                    if (accounts.any { it.username.equals(u, ignoreCase = true) }) done(localLogin(u, pass))
                    else done(dbFailText(e))
                }
        } catch (e: Exception) {
            done(localLogin(u, pass))
        }
    }

    private fun finishRegister(a: Account) {
        accounts.removeAll { it.username.equals(a.username, ignoreCase = true) && !it.isAdmin }
        accounts.add(a)
        saveAccounts()
        addNote(a.username, "WELCOME", "")
        log("Registered: " + a.username)
        user = a
        loadSaved()
        db.putString("session", a.username)
        checkAlerts()
    }

    fun register(name: String, pass: String, done: (String?) -> Unit) {
        val u = name.trim()
        if (u.length < 3) {
            done(tr(bn, "Name must be at least 3 characters", "নাম কমপক্ষে ৩ অক্ষরের হতে হবে"))
            return
        }
        if (pass.length < 6) {
            done(tr(bn, "Password must be at least 6 characters", "পাসওয়ার্ড কমপক্ষে ৬ অক্ষরের হতে হবে"))
            return
        }
        if (u.equals("admin", ignoreCase = true) || accounts.any { it.isAdmin && it.username.equals(u, ignoreCase = true) }) {
            done(takenText())
            return
        }
        val salt = newSalt()
        val hash = hashPassword(salt, pass)
        val account = Account(u, salt, hash, false, true)

        if (!cloudOn) {
            // The cloud is not set up at all: keep a local-only account so the app still works.
            if (accounts.any { it.username.equals(u, ignoreCase = true) }) {
                done(takenText())
                return
            }
            finishRegister(account)
            done(null)
            return
        }
        if (!isOnline) {
            done(tr(bn, "Connect to the internet to create an account", "অ্যাকাউন্ট তৈরি করতে ইন্টারনেটে সংযুক্ত হোন"))
            return
        }
        try {
            val ref = rtdb().getReference("users").child(safeKey(u))
            ref.get()
                .addOnSuccessListener { d ->
                    if (d.exists()) {
                        done(takenText())
                    } else {
                        val data = HashMap<String, Any>()
                        data["username"] = u
                        data["salt"] = salt
                        data["hash"] = hash
                        data["active"] = true
                        data["createdAt"] = System.currentTimeMillis()
                        ref.setValue(data)
                            .addOnSuccessListener {
                                finishRegister(account)
                                done(null)
                            }
                            .addOnFailureListener { e ->
                                done(tr(bn, "Could not create the account: ", "অ্যাকাউন্ট তৈরি করা যায়নি: ") + (e.message ?: ""))
                            }
                    }
                }
                .addOnFailureListener { e -> done(dbFailText(e)) }
        } catch (e: Exception) {
            done(tr(bn, "Could not reach the database", "ডাটাবেসে পৌঁছানো যায়নি"))
        }
    }

    /** Admin only: live list of every account in the cloud. */
    private fun startUsersListener() {
        if (!cloudOn || usersListener != null) return
        try {
            val ref = rtdb().getReference("users")
            val l = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val list = ArrayList<Account>()
                    for (c in snapshot.children) {
                        list.add(
                            Account(
                                c.child("username").getValue(String::class.java) ?: (c.key ?: "?"),
                                c.child("salt").getValue(String::class.java) ?: "",
                                c.child("hash").getValue(String::class.java) ?: "",
                                false,
                                (c.child("active").value as? Boolean) ?: true
                            )
                        )
                    }
                    list.sortBy { it.username.lowercase() }
                    cloudUsers.clear()
                    cloudUsers.addAll(list)
                }

                override fun onCancelled(error: DatabaseError) {
                }
            }
            ref.addValueEventListener(l)
            usersRef = ref
            usersListener = l
        } catch (e: Exception) {
        }
    }

    private fun stopUsersListener() {
        val r = usersRef
        val l = usersListener
        if (r != null && l != null) r.removeEventListener(l)
        usersRef = null
        usersListener = null
        cloudUsers.clear()
    }

    fun logout() {
        val u = user
        if (u != null) log("Logout: " + u.username)
        user = null
        stopUsersListener()
        savedCrops.clear()
        previewPath = null
        db.remove("session")
    }

    // ---------- connectivity ----------
    fun updateDeviceOnline(v: Boolean) {
        deviceOnline = v
        onConnectivityChanged()
    }

    fun updateSimOffline(v: Boolean) {
        simOffline = v
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
            var ai: AiResult? = null
            var aiError = ""
            if (AiConfig.isFilled()) {
                try {
                    ai = aiAnalyze(d.imagePath)
                } catch (e: Exception) {
                    aiError = (e.message ?: "AI error").take(120)
                }
            }
            val local: AnalysisResult? = if (ai == null) analyzeImage(d.imagePath) else null
            main.post {
                val i = diags.indexOfFirst { it.id == d.id }
                if (i >= 0) {
                    val a = ai
                    val l = local
                    diags[i] = if (a != null) {
                        diags[i].copy(
                            done = true, code = a.code,
                            disease = a.disease, rot = a.rot, pest = a.pest,
                            problem = a.problem, adviceEn = a.adviceEn, adviceBn = a.adviceBn,
                            source = "AI"
                        )
                    } else {
                        val r = l ?: AnalysisResult("UNCLEAR", 0, 0, 0)
                        diags[i].copy(
                            done = true, code = r.code,
                            disease = r.disease, rot = r.rot, pest = r.pest,
                            source = "LOCAL"
                        )
                    }
                    saveDiags()
                    addNote(d.owner, "DIAG", d.id.toString())
                    log("Diagnosis completed for " + d.owner + " (" + (if (a != null) "AI" else "on-device") + ")")
                    if (aiError.isNotEmpty()) {
                        say(
                            "AI unavailable (" + aiError + "). Used the on-device estimate.",
                            "এআই পাওয়া যায়নি (" + aiError + ")। ডিভাইসের অনুমান ব্যবহার করা হয়েছে।"
                        )
                    }
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

    // ---------- cloud forum (Firebase Realtime Database) ----------
    private fun rtdb(): FirebaseDatabase {
        val cur = rtdbInstance
        if (cur != null) return cur
        val d = FirebaseDatabase.getInstance(CloudConfig.DATABASE_URL)
        try {
            d.setPersistenceEnabled(true)
        } catch (e: Exception) {
        }
        rtdbInstance = d
        return d
    }

    fun startCloud() {
        // If the Google Services plugin already started Firebase, CloudConfig's app values are not needed.
        if (FirebaseApp.getApps(ctx).isEmpty()) {
            if (!CloudConfig.isFilled()) {
                cloudError = tr(bn, "Cloud is not set up yet. Fill in CloudConfig.kt.", "ক্লাউড এখনো সেট করা হয়নি। CloudConfig.kt পূরণ করুন।")
                return
            }
            if (!initFirebase(ctx)) {
                cloudError = tr(bn, "Could not start Firebase.", "ফায়ারবেস চালু করা যায়নি।")
                return
            }
        }
        connectCloud()
    }

    fun retryCloud() {
        cloudError = null
        startCloud()
    }

    private fun connectCloud() {
        cloudOn = true
        cloudError = null
        if (user?.isAdmin == true) startUsersListener()
        if (postsListener != null) return
        try {
            val q = rtdb().getReference("posts").orderByChild("time").limitToLast(40)
            val l = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val list = ArrayList<Post>()
                    for (c in snapshot.children) {
                        val p = postFromSnap(c)
                        if (p != null) list.add(p)
                    }
                    list.sortByDescending { it.time }
                    val first = !cloudReady
                    val old = HashMap<String, Post>()
                    for (p in posts) old[p.id] = p
                    posts.clear()
                    posts.addAll(list)
                    cloudReady = true
                    cloudError = null
                    if (!first) notifyAboutChanges(old, list)
                }

                override fun onCancelled(error: DatabaseError) {
                    cloudError = error.message
                }
            }
            q.addValueEventListener(l)
            postsQuery = q
            postsListener = l
        } catch (e: Exception) {
            cloudError = e.message ?: "Cloud error"
        }
    }

    private fun valuesOf(d: DataSnapshot): List<String> {
        val out = ArrayList<String>()
        for (c in d.children) {
            val v = c.getValue(String::class.java)
            if (v != null) out.add(v)
        }
        return out
    }

    private fun postFromSnap(d: DataSnapshot): Post? {
        val id = d.key ?: return null
        val text = d.child("text").getValue(String::class.java) ?: return null
        val t = (d.child("time").value as? Number)?.toLong() ?: 0L
        return Post(
            id,
            d.child("ownerUid").getValue(String::class.java) ?: "",
            d.child("author").getValue(String::class.java) ?: "?",
            text,
            d.child("image").getValue(String::class.java) ?: "",
            valuesOf(d.child("likedBy")),
            valuesOf(d.child("comments")),
            valuesOf(d.child("reportedBy")),
            t
        )
    }

    /** Loads a post's photo the first time it is shown. */
    fun loadImage(id: String) {
        if (!cloudOn || imageCache.containsKey(id)) return
        if (!imageLoading.add(id)) return
        try {
            rtdb().getReference("images").child(id).get()
                .addOnSuccessListener { s ->
                    imageCache[id] = s.getValue(String::class.java) ?: ""
                    imageLoading.remove(id)
                }
                .addOnFailureListener { imageLoading.remove(id) }
        } catch (e: Exception) {
            imageLoading.remove(id)
        }
    }

    /** Turns new likes/comments on MY posts (made by other people) into in-app notifications. */
    private fun notifyAboutChanges(old: Map<String, Post>, now: List<Post>) {
        val me = user?.username ?: return
        for (p in now) {
            if (p.ownerUid != me) continue
            val o = old[p.id] ?: continue
            val newLikers = p.likedBy.filter { it != me && !o.likedBy.contains(it) }
            if (newLikers.isNotEmpty()) addNote(me, "LIKE", tr(bn, "a farmer", "একজন কৃষক"))
            if (p.comments.size > o.comments.size) {
                for (c in p.comments.drop(o.comments.size)) {
                    val name = c.substringBefore(":")
                    if (name != me) addNote(me, "COMMENT", name)
                }
            }
        }
    }

    /** Shrinks the photo so one post stays small. */
    private fun encodeImage(path: String): String {
        var max = 800
        var q = 70
        for (i in 0 until 4) {
            val bmp = loadBitmap(path, max) ?: return ""
            val bos = ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.JPEG, q, bos)
            val s = Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP)
            if (s.length <= 600000) return s
            max = max * 3 / 4
            q = q - 10
        }
        return ""
    }

    fun createPost(text: String, uri: Uri?) {
        val me = user ?: return
        val t = text.trim()
        if (t.isEmpty()) return
        if (myUid.isEmpty() || !cloudOn) {
            say("Not connected to the cloud yet", "ক্লাউডের সাথে এখনো সংযুক্ত নয়")
            return
        }
        val author = me.username
        Thread {
            var img = ""
            if (uri != null) {
                val p = copyUriToFile(ctx, uri)
                if (p != null) img = encodeImage(p)
            }
            main.post {
                try {
                    val root = rtdb().reference
                    val id = root.child("posts").push().key
                    if (id == null) {
                        say("Could not post", "পোস্ট করা যায়নি")
                        return@post
                    }
                    val postMap = HashMap<String, Any>()
                    postMap["ownerUid"] = author
                    postMap["author"] = author
                    postMap["text"] = t
                    postMap["image"] = if (img.isEmpty()) "" else "1"
                    postMap["time"] = System.currentTimeMillis()
                    val updates = HashMap<String, Any>()
                    updates["posts/" + id] = postMap
                    if (img.isNotEmpty()) updates["images/" + id] = img
                    root.updateChildren(updates)
                        .addOnFailureListener { e ->
                            say("Could not post: " + (e.message ?: ""), "পোস্ট করা যায়নি: " + (e.message ?: ""))
                        }
                    log("Post created by " + author)
                } catch (e: Exception) {
                    say("Could not post", "পোস্ট করা যায়নি")
                }
            }
        }.start()
    }

    fun toggleLike(postId: String) {
        val me = myUid
        if (me.isEmpty() || !cloudOn) return
        val p = posts.firstOrNull { it.id == postId } ?: return
        val liked = p.likedBy.contains(me)
        try {
            val ref = rtdb().getReference("posts").child(postId).child("likedBy").child(safeKey(me))
            val task = if (liked) ref.removeValue() else ref.setValue(me)
            task.addOnFailureListener { e -> say("Could not like: " + (e.message ?: ""), "পছন্দ করা যায়নি") }
        } catch (e: Exception) {
        }
    }

    fun addComment(postId: String, text: String) {
        val me = myUid
        val t = text.trim()
        if (t.isEmpty() || me.isEmpty() || !cloudOn) return
        try {
            rtdb().getReference("posts").child(postId).child("comments").push().setValue(me + ": " + t)
                .addOnFailureListener { e -> say("Could not comment: " + (e.message ?: ""), "মন্তব্য করা যায়নি") }
        } catch (e: Exception) {
        }
    }

    fun reportPost(postId: String) {
        val me = myUid
        if (me.isEmpty() || !cloudOn) return
        val p = posts.firstOrNull { it.id == postId } ?: return
        if (p.reportedBy.contains(me)) return
        try {
            rtdb().getReference("posts").child(postId).child("reportedBy").child(safeKey(me)).setValue(me)
                .addOnSuccessListener {
                    log("Post reported by " + me + " (author: " + p.author + ")")
                    say("Thanks, the post was reported to moderators.", "ধন্যবাদ, পোস্টটি মডারেটরদের কাছে রিপোর্ট করা হয়েছে।")
                }
                .addOnFailureListener { e -> say("Could not report: " + (e.message ?: ""), "রিপোর্ট করা যায়নি") }
        } catch (e: Exception) {
        }
    }

    fun removePost(postId: String) {
        val p = posts.firstOrNull { it.id == postId } ?: return
        val mine = myUid.isNotEmpty() && p.ownerUid == myUid
        if (!mine && !cloudAdmin) {
            say(
                "Only the author or the admin can delete this post",
                "শুধু লেখক বা অ্যাডমিন এই পোস্ট মুছতে পারবেন"
            )
            return
        }
        try {
            val updates = HashMap<String, Any?>()
            updates["posts/" + postId] = null
            updates["images/" + postId] = null
            rtdb().reference.updateChildren(updates)
                .addOnSuccessListener { log("Post removed (author: " + p.author + ")") }
                .addOnFailureListener { e -> say("Could not delete: " + (e.message ?: ""), "মুছা যায়নি") }
        } catch (e: Exception) {
        }
    }

    fun dismissReports(postId: String) {
        if (!cloudAdmin) {
            say(
                "Only the admin can do this.",
                "শুধু অ্যাডমিন এটি করতে পারবেন।"
            )
            return
        }
        try {
            rtdb().getReference("posts").child(postId).child("reportedBy").removeValue()
                .addOnSuccessListener { log("Reports dismissed") }
                .addOnFailureListener { e -> say("Could not dismiss: " + (e.message ?: ""), "খারিজ করা যায়নি") }
        } catch (e: Exception) {
        }
    }

    override fun onCleared() {
        val q = postsQuery
        val l = postsListener
        if (q != null && l != null) q.removeEventListener(l)
        stopUsersListener()
        super.onCleared()
    }

    // ---------- admin ----------
    fun setActive(username: String, active: Boolean) {
        val me = user ?: return
        if (!me.isAdmin) return
        val i = accounts.indexOfFirst { it.username == username }
        if (i >= 0 && accounts[i].isAdmin) return
        if (i >= 0) {
            accounts[i] = accounts[i].copy(active = active)
            saveAccounts()
        }
        if (cloudOn) {
            try {
                rtdb().getReference("users").child(safeKey(username)).child("active").setValue(active)
                    .addOnFailureListener { e ->
                        say("Could not update the account: " + (e.message ?: ""), "অ্যাকাউন্ট আপডেট করা যায়নি")
                    }
            } catch (e: Exception) {
            }
        }
        log("Admin " + me.username + " set " + username + " active=" + active)
    }
}
