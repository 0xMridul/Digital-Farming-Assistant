@file:OptIn(ExperimentalMaterial3Api::class)
package com.example.digitalfarmingassistant


import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    RootApp()
                }
            }
        }
    }
}

fun createCameraUri(ctx: android.content.Context): Uri {
    val dir = File(ctx.filesDir, "images")
    if (!dir.exists()) dir.mkdirs()
    val f = File(dir, "cam_" + System.nanoTime() + ".jpg")
    return FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", f)
}

fun Modifier.clickableRow(onClick: () -> Unit): Modifier = this.clickable(onClick = onClick)

@Composable
fun RootApp() {
    val vm: AppVm = viewModel()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(vm.toast) {
        val msg = vm.toast
        if (msg != null) {
            snackbar.showSnackbar(msg)
            vm.toast = null
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            if (vm.user == null) {
                AuthScreen(vm)
            } else {
                HomeScaffold(vm)
            }
        }
    }
}

// ---------------- Auth ----------------
@Composable
fun AuthScreen(vm: AppVm) {
    var mode by remember { mutableStateOf(0) } // 0 = login, 1 = register
    var name by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val b = vm.bn

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("\uD83C\uDF3E " + tr(b, "Digital Farming Assistant", "ডিজিটাল ফার্মিং অ্যাসিস্ট্যান্ট"), style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(4.dp))
        Text(tr(b, "Crop help, weather and a farmer community.", "ফসলের সাহায্য, আবহাওয়া এবং কৃষক সম্প্রদায়।"), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(20.dp))

        TabRow(selectedTabIndex = mode) {
            Tab(selected = mode == 0, onClick = { mode = 0; error = null }, text = { Text(tr(b, "Login", "লগইন")) })
            Tab(selected = mode == 1, onClick = { mode = 1; error = null }, text = { Text(tr(b, "Register", "নিবন্ধন")) })
        }
        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = name, onValueChange = { name = it }, singleLine = true,
            label = { Text(tr(b, "Name", "নাম")) }, modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = pass, onValueChange = { pass = it }, singleLine = true,
            label = { Text(tr(b, "Password", "পাসওয়ার্ড")) },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )
        if (error != null) {
            Spacer(Modifier.height(8.dp))
            Text(error ?: "", color = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = {
                error = if (mode == 0) vm.login(name, pass) else vm.register(name, pass)
            },
            enabled = name.isNotBlank() && pass.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (mode == 0) tr(b, "Login", "লগইন") else tr(b, "Create account", "অ্যাকাউন্ট তৈরি করুন"))
        }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = { vm.setLanguage(!b) }, modifier = Modifier.fillMaxWidth()) {
            Text(if (b) "English" else "বাংলা")
        }
        Spacer(Modifier.height(8.dp))
        Text(tr(b, "Admin demo login: admin / admin123", "অ্যাডমিন ডেমো: admin / admin123"), style = MaterialTheme.typography.bodySmall)
    }
}

// ---------------- Home / navigation ----------------
@Composable
fun HomeScaffold(vm: AppVm) {
    var tab by remember { mutableStateOf(0) }
    val b = vm.bn
    val tabs = listOf(
        Triple("\uD83C\uDF3E", tr(b, "Crop", "ফসল"), 0),
        Triple("\u26C5", tr(b, "Weather", "আবহাওয়া"), 1),
        Triple("\uD83D\uDCDA", tr(b, "Library", "তথ্য"), 2),
        Triple("\uD83D\uDCAC", tr(b, "Forum", "ফোরাম"), 3),
        Triple("\u2630", tr(b, "More", "আরও"), 4)
    )
    Scaffold(
        bottomBar = {
            NavigationBar {
                tabs.forEach { (icon, label, idx) ->
                    NavigationBarItem(
                        selected = tab == idx,
                        onClick = { tab = idx },
                        icon = { Text(icon) },
                        label = { Text(label) }
                    )
                }
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad).padding(12.dp)) {
            when (tab) {
                0 -> CropScreen(vm)
                1 -> WeatherScreen(vm)
                2 -> LibraryScreen(vm)
                3 -> ForumScreen(vm)
                else -> MoreScreen(vm)
            }
        }
    }
}

// ---------------- Crop health ----------------
@Composable
fun CropScreen(vm: AppVm) {
    val b = vm.bn
    val ctx = LocalContext.current
    var pendingUri by remember { mutableStateOf<Uri?>(null) }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) vm.submitUri(uri)
    }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val u = pendingUri
        if (success && u != null) vm.submitUri(u)
    }
    val cameraPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            val u = createCameraUri(ctx)
            pendingUri = u
            cameraLauncher.launch(u)
        } else {
            vm.say("Camera permission is needed to take a photo", "ছবি তোলার জন্য ক্যামেরা অনুমতি প্রয়োজন")
        }
    }

    Column(Modifier.fillMaxSize()) {
        Text(tr(b, "Crop Health Diagnosis", "ফসলের স্বাস্থ্য নির্ণয়"), style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(4.dp))
        Text(
            tr(b, "Take or upload a photo of an affected leaf to get guidance.", "আক্রান্ত পাতার ছবি তুলুন বা আপলোড করুন পরামর্শ পেতে।"),
            style = MaterialTheme.typography.bodyMedium
        )
        if (!vm.isOnline) {
            Spacer(Modifier.height(6.dp))
            AssistChip(onClick = {}, label = { Text(tr(b, "Offline — new requests will be queued", "অফলাইন — নতুন অনুরোধ অপেক্ষমাণ থাকবে")) })
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    val has = ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                    if (has) {
                        val u = createCameraUri(ctx)
                        pendingUri = u
                        cameraLauncher.launch(u)
                    } else {
                        cameraPermLauncher.launch(Manifest.permission.CAMERA)
                    }
                },
                modifier = Modifier.weight(1f)
            ) { Text(tr(b, "\uD83D\uDCF7 Take Photo", "\uD83D\uDCF7 ছবি তুলুন")) }
            OutlinedButton(onClick = { galleryLauncher.launch("image/*") }, modifier = Modifier.weight(1f)) {
                Text(tr(b, "\uD83D\uDDBC Gallery", "\uD83D\uDDBC গ্যালারি"))
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(tr(b, "History", "ইতিহাস"), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        val mine = vm.diags.filter { it.owner == vm.user?.username }
        if (mine.isEmpty()) {
            Text(tr(b, "No diagnoses yet.", "এখনো কোনো ডায়াগনোসিস নেই।"), style = MaterialTheme.typography.bodySmall)
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(mine, key = { it.id }) { d -> DiagnosisCard(vm, d) }
        }
    }
}

@Composable
fun DiagnosisCard(vm: AppVm, d: Diagnosis) {
    val b = vm.bn
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.Top) {
            val bmp = remember(d.imagePath) { loadBitmap(d.imagePath, 200) }
            if (bmp != null) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.size(72.dp),
                    contentScale = ContentScale.Crop
                )
                Spacer(Modifier.width(10.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(fmtTime(d.time), style = MaterialTheme.typography.bodySmall)
                if (!d.done) {
                    Text(tr(b, "\u23F3 Waiting to be processed", "\u23F3 প্রক্রিয়াকরণের অপেক্ষায়"), style = MaterialTheme.typography.titleSmall)
                } else {
                    Text(diagTitle(d.code, b), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(diagAdvice(d.code, b), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

// ---------------- Weather ----------------
@Composable
fun WeatherScreen(vm: AppVm) {
    val b = vm.bn
    val locLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        vm.onPermissionResult()
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text(tr(b, "Weather", "আবহাওয়া"), style = MaterialTheme.typography.titleLarge)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "\uD83D\uDCCD " + (if (vm.usingDefaultLocation) tr(b, "Default location (Dhaka)", "ডিফল্ট অবস্থান (ঢাকা)") else tr(b, "Your location", "আপনার অবস্থান")),
                Modifier.weight(1f)
            )
            TextButton(onClick = {
                locLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            }) { Text(tr(b, "Use my location", "আমার অবস্থান ব্যবহার করুন")) }
        }

        when (vm.weatherState) {
            "loading" -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            "error" -> Text(tr(b, "Could not load weather. Check your internet connection.", "আবহাওয়া লোড করা যায়নি। ইন্টারনেট সংযোগ পরীক্ষা করুন।"))
            "nodata" -> Text(tr(b, "You are offline and no weather is saved yet.", "আপনি অফলাইনে আছেন এবং এখনো কোনো আবহাওয়া তথ্য সংরক্ষিত নেই।"))
            else -> {}
        }

        val w = vm.weather
        if (w != null) {
            if (vm.weatherState == "cached") {
                Spacer(Modifier.height(4.dp))
                AssistChip(onClick = {}, label = { Text(tr(b, "Showing saved data from " + fmtTime(w.time), "সংরক্ষিত তথ্য দেখানো হচ্ছে: " + fmtTime(w.time))) })
            }
            Spacer(Modifier.height(8.dp))
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(num(w.temp, 1) + "\u00B0C", style = MaterialTheme.typography.displaySmall)
                    Text(weatherText(w.code, b))
                    Text(tr(b, "Wind: ", "বাতাস: ") + num(w.wind, 1) + " km/h", style = MaterialTheme.typography.bodySmall)
                }
            }
            val alerts = alertsFor(w)
            if (alerts.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text(tr(b, "Alerts", "সতর্কতা"), style = MaterialTheme.typography.titleMedium)
                alerts.forEach { a ->
                    Card(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                    ) {
                        Text(alertText(a.kind, a.date, a.value, b), Modifier.padding(10.dp))
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(tr(b, "5-day forecast", "৫ দিনের পূর্বাভাস"), style = MaterialTheme.typography.titleMedium)
            w.days.forEach { d ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Text(d.date, Modifier.weight(1f))
                    Text(weatherText(d.code, b), Modifier.weight(1.4f))
                    Text(num(d.max) + "\u00B0 / " + num(d.min) + "\u00B0", Modifier.weight(0.8f))
                }
            }
        }
    }
}

// ---------------- Crop library ----------------
@Composable
fun LibraryScreen(vm: AppVm) {
    val b = vm.bn
    var q by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize()) {
        Text(tr(b, "Crop Information Library", "ফসল তথ্য ভাণ্ডার"), style = MaterialTheme.typography.titleLarge)
        Text(tr(b, "Works offline.", "অফলাইনেও কাজ করে।"), style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = q, onValueChange = { q = it }, singleLine = true,
            label = { Text(tr(b, "Search crop, pest or disease", "ফসল, পোকা বা রোগ খুঁজুন")) },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        val results = LIBRARY.filter { it.matches(q) }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(results, key = { it.name.en }) { crop -> CropCard(crop, b) }
        }
    }
}

@Composable
fun CropCard(crop: CropInfo, b: Boolean) {
    var open by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(
                Modifier.fillMaxWidth().clickableRow { open = !open },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(crop.name.of(b), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(if (open) "\u25B2" else "\u25BC")
            }
            if (open) {
                Spacer(Modifier.height(6.dp))
                crop.sections(b).forEach { (_, label, text) ->
                    Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    Text(text, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(6.dp))
                }
            }
        }
    }
}

// ---------------- Forum ----------------
@Composable
fun ForumScreen(vm: AppVm) {
    val b = vm.bn
    var text by remember { mutableStateOf("") }
    var attach by remember { mutableStateOf<Uri?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> attach = uri }

    Column(Modifier.fillMaxSize()) {
        Text(tr(b, "Farmer Community Forum", "কৃষক সম্প্রদায় ফোরাম"), style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(10.dp)) {
                OutlinedTextField(
                    value = text, onValueChange = { text = it },
                    label = { Text(tr(b, "Ask a question or share a tip", "প্রশ্ন করুন বা পরামর্শ শেয়ার করুন")) },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { picker.launch("image/*") }) {
                        Text(if (attach != null) tr(b, "\u2705 Image attached", "\u2705 ছবি যুক্ত হয়েছে") else tr(b, "\uD83D\uDCCE Attach image", "\uD83D\uDCCE ছবি যুক্ত করুন"))
                    }
                    Spacer(Modifier.weight(1f))
                    Button(
                        enabled = text.isNotBlank(),
                        onClick = {
                            vm.createPost(text, attach)
                            text = ""
                            attach = null
                        }
                    ) { Text(tr(b, "Post", "পোস্ট করুন")) }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        val visible = vm.posts.filter { it.reportedBy.isEmpty() || vm.user?.isAdmin == true || it.author == vm.user?.username }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(visible, key = { it.id }) { p -> PostCard(vm, p) }
        }
    }
}

@Composable
fun PostCard(vm: AppVm, p: Post) {
    val b = vm.bn
    val me = vm.user?.username ?: ""
    var comment by remember { mutableStateOf("") }
    val liked = p.likedBy.contains(me)
    val reportedByMe = p.reportedBy.contains(me)

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(p.author, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(fmtTime(p.time), style = MaterialTheme.typography.bodySmall)
            }
            if (p.reportedBy.isNotEmpty() && vm.user?.isAdmin == true) {
                Text(tr(b, "\u26A0 Reported " + p.reportedBy.size + " time(s)", "\u26A0 " + p.reportedBy.size + " বার রিপোর্ট হয়েছে"), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(4.dp))
            Text(p.text)
            if (p.imagePath.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                val bmp = remember(p.imagePath) { loadBitmap(p.imagePath, 500) }
                if (bmp != null) {
                    Image(
                        bitmap = bmp.asImageBitmap(), contentDescription = null,
                        modifier = Modifier.fillMaxWidth().height(180.dp), contentScale = ContentScale.Crop
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { vm.toggleLike(p.id) }) {
                    Text((if (liked) "\uD83D\uDC9A " else "\uD83E\uDD0D ") + tr(b, "Like", "পছন্দ") + " (" + p.likedBy.size + ")")
                }
                TextButton(enabled = !reportedByMe, onClick = { vm.reportPost(p.id) }) {
                    Text(if (reportedByMe) tr(b, "Reported", "রিপোর্ট করা হয়েছে") else tr(b, "Report", "রিপোর্ট"))
                }
                if (p.author == me || vm.user?.isAdmin == true) {
                    TextButton(onClick = { vm.removePost(p.id) }) { Text(tr(b, "Delete", "মুছুন")) }
                }
                if (vm.user?.isAdmin == true && p.reportedBy.isNotEmpty()) {
                    TextButton(onClick = { vm.dismissReports(p.id) }) { Text(tr(b, "Dismiss", "খারিজ করুন")) }
                }
            }
            if (p.comments.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                p.comments.forEach { c -> Text("\uD83D\uDCAC " + c, style = MaterialTheme.typography.bodySmall) }
            }
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = comment, onValueChange = { comment = it }, singleLine = true,
                    label = { Text(tr(b, "Write a comment", "মন্তব্য লিখুন")) },
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    enabled = comment.isNotBlank(),
                    onClick = { vm.addComment(p.id, comment); comment = "" }
                ) { Text(tr(b, "Send", "পাঠান")) }
            }
        }
    }
}

// ---------------- More: profile, notifications, admin ----------------
@Composable
fun MoreScreen(vm: AppVm) {
    val b = vm.bn
    val ctx = LocalContext.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        val u = vm.user
        Text("\uD83D\uDC64 " + (u?.username ?: "") + (if (u?.isAdmin == true) " (Admin)" else ""), style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(10.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(tr(b, "Language: Bengali", "ভাষা: বাংলা"), Modifier.weight(1f))
            Switch(checked = b, onCheckedChange = { vm.setLanguage(it) })
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(tr(b, "Simulate offline mode", "অফলাইন মোড সিমুলেট করুন"), Modifier.weight(1f))
            Switch(checked = vm.simOffline, onCheckedChange = { vm.setSimOffline(it) })
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (vm.isOnline) tr(b, "Status: Online", "অবস্থা: অনলাইন") else tr(b, "Status: Offline", "অবস্থা: অফলাইন"),
                Modifier.weight(1f)
            )
            TextButton(onClick = { vm.setDeviceOnline(checkOnline(ctx)) }) { Text(tr(b, "Recheck", "পুনরায় পরীক্ষা")) }
        }

        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(tr(b, "Notifications", "নোটিফিকেশন"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = { vm.clearMyNotes() }) { Text(tr(b, "Clear", "মুছুন")) }
        }
        val myNotes = vm.notes.filter { it.owner == vm.user?.username }
        if (myNotes.isEmpty()) {
            Text(tr(b, "No notifications yet.", "এখনো কোনো নোটিফিকেশন নেই।"), style = MaterialTheme.typography.bodySmall)
        }
        myNotes.take(30).forEach { n ->
            val txt = noteText(n, b)
            if (txt.isNotBlank()) {
                Card(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                    Column(Modifier.padding(8.dp)) {
                        Text(txt, style = MaterialTheme.typography.bodySmall)
                        Text(fmtTime(n.time), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        if (vm.user?.isAdmin == true) {
            Spacer(Modifier.height(18.dp))
            Divider()
            Spacer(Modifier.height(10.dp))
            Text(tr(b, "Admin Dashboard", "অ্যাডমিন ড্যাশবোর্ড"), style = MaterialTheme.typography.titleLarge)
            Text(
                tr(b, "Users: ", "ব্যবহারকারী: ") + vm.accounts.size +
                        " | " + tr(b, "Posts: ", "পোস্ট: ") + vm.posts.size +
                        " | " + tr(b, "Reported: ", "রিপোর্ট হয়েছে: ") + vm.posts.count { it.reportedBy.isNotEmpty() }
            )

            Spacer(Modifier.height(10.dp))
            Text(tr(b, "User Management", "ব্যবহারকারী ব্যবস্থাপনা"), style = MaterialTheme.typography.titleMedium)
            vm.accounts.forEach { acc ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        acc.username + (if (acc.isAdmin) " \u2605" else "") + (if (acc.active) "" else " \u2014 " + tr(b, "deactivated", "নিষ্ক্রিয়")),
                        Modifier.weight(1f)
                    )
                    if (!acc.isAdmin) {
                        Switch(checked = acc.active, onCheckedChange = { vm.setActive(acc.username, it) })
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            Text(tr(b, "Reported Posts", "রিপোর্ট করা পোস্ট"), style = MaterialTheme.typography.titleMedium)
            val reported = vm.posts.filter { it.reportedBy.isNotEmpty() }
            if (reported.isEmpty()) {
                Text(tr(b, "None right now.", "এই মুহূর্তে কোনো রিপোর্ট নেই।"), style = MaterialTheme.typography.bodySmall)
            }
            reported.forEach { p ->
                Card(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                    Column(Modifier.padding(8.dp)) {
                        Text(p.author + ": " + p.text, style = MaterialTheme.typography.bodySmall)
                        Row {
                            TextButton(onClick = { vm.dismissReports(p.id) }) { Text(tr(b, "Dismiss", "খারিজ")) }
                            TextButton(onClick = { vm.removePost(p.id) }) { Text(tr(b, "Remove", "মুছুন")) }
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            Text(tr(b, "Security & Activity Logs", "নিরাপত্তা ও কার্যক্রম লগ"), style = MaterialTheme.typography.titleMedium)
            vm.logs.take(50).forEach { e ->
                Text("\u2022 " + fmtTime(e.time) + " \u2014 " + e.text, style = MaterialTheme.typography.bodySmall)
            }
        }

        Spacer(Modifier.height(20.dp))
        Button(onClick = { vm.logout() }, modifier = Modifier.fillMaxWidth()) {
            Text(tr(b, "Logout", "লগআউট"))
        }
        Spacer(Modifier.height(20.dp))
    }
}
