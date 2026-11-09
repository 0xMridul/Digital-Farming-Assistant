package com.example.digitalfarmingassistantv2

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File

// ======================================================================
// Colours and theme
// ======================================================================
val FarmGreenDark = Color(0xFF1B5E20)
val FarmGreen = Color(0xFF2E7D32)
val FarmLeaf = Color(0xFF66BB6A)
val FarmMint = Color(0xFFE8F5E9)
val FarmCream = Color(0xFFF4F8F1)
val FarmAmber = Color(0xFFF9A825)
val FarmRed = Color(0xFFC62828)
val SkyDark = Color(0xFF1565C0)
val SkyLight = Color(0xFF64B5F6)

@Composable
fun FarmTheme(content: @Composable () -> Unit) {
    val scheme = lightColorScheme(
        primary = FarmGreen,
        onPrimary = Color.White,
        primaryContainer = Color(0xFFC8E6C9),
        onPrimaryContainer = FarmGreenDark,
        secondary = Color(0xFF558B2F),
        onSecondary = Color.White,
        secondaryContainer = FarmMint,
        onSecondaryContainer = FarmGreenDark,
        tertiary = FarmAmber,
        background = FarmCream,
        onBackground = Color(0xFF1B1F1A),
        surface = Color.White,
        onSurface = Color(0xFF1B1F1A),
        surfaceVariant = Color(0xFFE3EDDC),
        onSurfaceVariant = Color(0xFF434A40),
        outline = Color(0xFF8A9486),
        error = FarmRed,
        errorContainer = Color(0xFFFFEBEE),
        onErrorContainer = Color(0xFF8E0000)
    )
    MaterialTheme(
        colorScheme = scheme,
        shapes = Shapes(
            small = RoundedCornerShape(10.dp),
            medium = RoundedCornerShape(14.dp),
            large = RoundedCornerShape(20.dp)
        ),
        content = content
    )
}

// ======================================================================
// Activity
// ======================================================================
class MainActivity : ComponentActivity() {
    private val vm: AppVm by viewModels()
    private var netCallback: ConnectivityManager.NetworkCallback? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        registerNetworkWatcher()
        setContent {
            FarmTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    RootApp(vm)
                }
            }
        }
    }

    private fun registerNetworkWatcher() {
        try {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val handler = Handler(Looper.getMainLooper())
            val cb = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    handler.post { vm.updateDeviceOnline(true) }
                }

                override fun onLost(network: Network) {
                    handler.post { vm.updateDeviceOnline(false) }
                }
            }
            cm.registerDefaultNetworkCallback(cb)
            netCallback = cb
        } catch (e: Exception) {
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        val cb = netCallback
        if (cb != null) {
            try {
                val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                cm.unregisterNetworkCallback(cb)
            } catch (e: Exception) {
            }
        }
    }
}

fun createCameraUri(ctx: Context): Uri {
    val dir = File(ctx.filesDir, "images")
    if (!dir.exists()) dir.mkdirs()
    val f = File(dir, "cam_" + System.nanoTime() + ".jpg")
    return FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", f)
}

// ======================================================================
// Reusable UI pieces
// ======================================================================
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    container: Color = Color.White,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = container),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(14.dp), content = content)
    }
}

@Composable
fun Pill(text: String, bg: Color, fg: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = fg, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = FarmGreenDark,
        modifier = Modifier.padding(top = 4.dp)
    )
}

@Composable
fun Avatar(name: String, size: Dp = 40.dp) {
    val initial = if (name.isNotEmpty()) name.substring(0, 1).uppercase() else "?"
    Box(
        Modifier.size(size).clip(CircleShape).background(FarmLeaf),
        contentAlignment = Alignment.Center
    ) {
        Text(initial, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
    }
}

@Composable
fun PageHeader(title: String, subtitle: String, onBack: (() -> Unit)? = null) {
    Box(
        Modifier
            .fillMaxWidth()
            .background(
                Brush.horizontalGradient(listOf(FarmGreenDark, FarmGreen)),
                RoundedCornerShape(bottomStart = 26.dp, bottomEnd = 26.dp)
            )
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) {
                Text(
                    "←",
                    color = Color.White,
                    fontSize = 28.sp,
                    modifier = Modifier.clickable(onClick = onBack).padding(end = 14.dp)
                )
            }
            Column {
                Text(title, color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(subtitle, color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
fun ActionTile(emoji: String, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                Modifier.size(52.dp).clip(CircleShape).background(FarmMint),
                contentAlignment = Alignment.Center
            ) { Text(emoji, fontSize = 26.sp) }
            Spacer(Modifier.height(8.dp))
            Text(label, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun StatTile(value: String, label: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = FarmMint),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = FarmGreenDark)
            Text(label, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
        }
    }
}

@Composable
fun EmptyState(emoji: String, text: String) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(emoji, fontSize = 40.sp)
        Spacer(Modifier.height(6.dp))
        Text(text, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

fun diagColors(code: String): Pair<Color, Color> = when (code) {
    "HEALTHY" -> Pair(Color(0xFFE8F5E9), Color(0xFF2E7D32))
    "EARLY" -> Pair(Color(0xFFFFF8E1), Color(0xFF8D6E00))
    "SEVERE" -> Pair(Color(0xFFFFEBEE), Color(0xFFC62828))
    else -> Pair(Color(0xFFECEFF1), Color(0xFF546E7A))
}

fun cropEmoji(name: String): String = when (name) {
    "Rice" -> "🌾"
    "Potato" -> "🥔"
    "Tomato" -> "🍅"
    "Jute" -> "🌿"
    else -> "🌱"
}

fun weatherEmptyText(state: String, b: Boolean): String = when (state) {
    "loading" -> tr(b, "Loading weather…", "আবহাওয়া লোড হচ্ছে…")
    "error" -> tr(b, "Could not load weather. Check your internet.", "আবহাওয়া লোড করা যায়নি। ইন্টারনেট পরীক্ষা করুন।")
    "nodata" -> tr(b, "Offline and no saved weather yet.", "অফলাইন, এখনো কোনো আবহাওয়া সংরক্ষিত নেই।")
    else -> tr(b, "Tap to load weather", "আবহাওয়া দেখতে ট্যাপ করুন")
}

// ======================================================================
// Root
// ======================================================================
@Composable
fun RootApp(vm: AppVm) {
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(vm.toast) {
        val msg = vm.toast
        if (msg != null) {
            snackbar.showSnackbar(msg)
            vm.toast = null
        }
    }

    Box(Modifier.fillMaxSize()) {
        if (vm.user == null) {
            AuthScreen(vm)
        } else {
            HomeScaffold(vm)
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 90.dp))
    }
}

// ======================================================================
// Login / Register
// ======================================================================
@Composable
fun AuthScreen(vm: AppVm) {
    var mode by remember { mutableStateOf(0) }
    var name by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val b = vm.bn

    Column(
        Modifier
            .fillMaxSize()
            .background(FarmCream)
            .imePadding()
            .verticalScroll(rememberScrollState())
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(listOf(FarmGreenDark, FarmGreen, FarmLeaf)),
                    RoundedCornerShape(bottomStart = 34.dp, bottomEnd = 34.dp)
                )
                .statusBarsPadding()
                .padding(24.dp)
        ) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Pill(
                        if (b) "English" else "বাংলা",
                        Color.White.copy(alpha = 0.22f),
                        Color.White,
                        Modifier.clickable { vm.setLanguage(!b) }
                    )
                }
                Spacer(Modifier.height(8.dp))
                Box(
                    Modifier.size(88.dp).clip(CircleShape).background(Color.White),
                    contentAlignment = Alignment.Center
                ) { Text("🌾", fontSize = 46.sp) }
                Spacer(Modifier.height(14.dp))
                Text(
                    tr(b, "Digital Farming Assistant", "ডিজিটাল ফার্মিং অ্যাসিস্ট্যান্ট"),
                    color = Color.White,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    tr(b, "Crop help, weather and a farmer community", "ফসলের সাহায্য, আবহাওয়া ও কৃষক সম্প্রদায়"),
                    color = Color.White.copy(alpha = 0.9f),
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(12.dp))
            }
        }

        Column(Modifier.padding(20.dp)) {
            AppCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill(
                        tr(b, "Login", "লগইন"),
                        if (mode == 0) FarmGreen else FarmMint,
                        if (mode == 0) Color.White else FarmGreenDark,
                        Modifier.weight(1f).clickable { mode = 0; error = null }
                    )
                    Pill(
                        tr(b, "Register", "নিবন্ধন"),
                        if (mode == 1) FarmGreen else FarmMint,
                        if (mode == 1) Color.White else FarmGreenDark,
                        Modifier.weight(1f).clickable { mode = 1; error = null }
                    )
                }
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    leadingIcon = { Text("👤") },
                    label = { Text(tr(b, "Name", "নাম")) },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = pass,
                    onValueChange = { pass = it },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    leadingIcon = { Text("🔒") },
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
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().height(50.dp)
                ) {
                    Text(
                        if (mode == 0) tr(b, "Login", "লগইন") else tr(b, "Create account", "অ্যাকাউন্ট তৈরি করুন"),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Text(
                tr(b, "Admin demo login: admin / admin123", "অ্যাডমিন ডেমো: admin / admin123"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }
    }
}

// ======================================================================
// Main shell with bottom navigation
// ======================================================================
@Composable
fun HomeScaffold(vm: AppVm) {
    var tab by remember { mutableStateOf(0) }
    var showWeather by remember { mutableStateOf(false) }
    val b = vm.bn

    BackHandler(enabled = showWeather) { showWeather = false }

    val tabs = listOf(
        Triple("🏠", tr(b, "Home", "হোম"), 0),
        Triple("🌾", tr(b, "Crop", "ফসল"), 1),
        Triple("📚", tr(b, "Library", "তথ্য"), 2),
        Triple("💬", tr(b, "Forum", "ফোরাম"), 3),
        Triple("👤", tr(b, "Profile", "প্রোফাইল"), 4)
    )

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
            if (showWeather) {
                WeatherScreen(vm) { showWeather = false }
            } else {
                when (tab) {
                    0 -> HomeTab(vm, { tab = it }, { showWeather = true })
                    1 -> CropScreen(vm)
                    2 -> LibraryScreen(vm)
                    3 -> ForumScreen(vm)
                    else -> MoreScreen(vm)
                }
            }
        }
        NavigationBar(containerColor = Color.White) {
            tabs.forEach { item ->
                NavigationBarItem(
                    selected = !showWeather && tab == item.third,
                    onClick = { showWeather = false; tab = item.third },
                    icon = { Text(item.first, fontSize = 22.sp) },
                    label = { Text(item.second, maxLines = 1, fontSize = 11.sp) }
                )
            }
        }
    }
}

// ======================================================================
// Home dashboard
// ======================================================================
@Composable
fun WeatherHero(vm: AppVm, modifier: Modifier = Modifier) {
    val b = vm.bn
    val w = vm.weather
    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Brush.linearGradient(listOf(SkyDark, SkyLight)))
            .padding(18.dp)
    ) {
        if (w != null) {
            val label = weatherText(w.code, b)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "📍 " + (if (vm.usingDefaultLocation) tr(b, "Dhaka (default)", "ঢাকা (ডিফল্ট)") else tr(b, "Your location", "আপনার অবস্থান")),
                        color = Color.White.copy(alpha = 0.9f),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(num(w.temp, 0) + "°C", color = Color.White, fontSize = 46.sp, fontWeight = FontWeight.Bold)
                    Text(label.substringAfter(" "), color = Color.White, style = MaterialTheme.typography.titleMedium)
                    Text(
                        tr(b, "Wind ", "বাতাস ") + num(w.wind, 0) + " km/h",
                        color = Color.White.copy(alpha = 0.9f),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Text(label.substringBefore(" "), fontSize = 60.sp)
            }
        } else {
            Text(weatherEmptyText(vm.weatherState, b), color = Color.White, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
fun HomeTab(vm: AppVm, onTab: (Int) -> Unit, onWeather: () -> Unit) {
    val b = vm.bn
    val me = vm.user?.username ?: ""
    val w = vm.weather
    val alerts = if (w != null) alertsFor(w) else emptyList()
    val mine = vm.diags.filter { it.owner == me }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        PageHeader(
            tr(b, "Hello, ", "হ্যালো, ") + me + " 👋",
            tr(b, "Let's take care of your crops today", "আজ আপনার ফসলের যত্ন নিই")
        )
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (vm.isOnline) {
                Pill(tr(b, "🟢 Online", "🟢 অনলাইন"), FarmMint, FarmGreenDark)
            } else {
                Pill(tr(b, "🔴 Offline — diagnoses will be queued", "🔴 অফলাইন — ডায়াগনোসিস অপেক্ষমাণ থাকবে"), Color(0xFFFFEBEE), FarmRed)
            }

            WeatherHero(vm, Modifier.clickable { onWeather() })

            if (alerts.isNotEmpty()) {
                val a = alerts[0]
                AppCard(container = Color(0xFFFFF3E0)) {
                    Text(alertText(a.kind, a.date, a.value, b), color = Color(0xFF8D4B00))
                }
            }

            SectionTitle(tr(b, "Quick actions", "দ্রুত কাজ"))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ActionTile("📷", tr(b, "Diagnose crop", "ফসলের রোগ নির্ণয়"), Modifier.weight(1f)) { onTab(1) }
                ActionTile("📚", tr(b, "Crop library", "ফসল তথ্য"), Modifier.weight(1f)) { onTab(2) }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ActionTile("⛅", tr(b, "Weather", "আবহাওয়া"), Modifier.weight(1f)) { onWeather() }
                ActionTile("💬", tr(b, "Community", "কমিউনিটি"), Modifier.weight(1f)) { onTab(3) }
            }

            SectionTitle(tr(b, "Latest diagnosis", "সর্বশেষ ডায়াগনোসিস"))
            if (mine.isEmpty()) {
                AppCard {
                    EmptyState("🌱", tr(b, "No diagnosis yet. Take a photo of a leaf to start.", "এখনো কোনো ডায়াগনোসিস নেই। শুরু করতে পাতার ছবি তুলুন।"))
                }
            } else {
                DiagnosisCard(vm, mine[0])
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

// ======================================================================
// Crop health
// ======================================================================
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

    val mine = vm.diags.filter { it.owner == vm.user?.username }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        PageHeader(
            tr(b, "Crop Health", "ফসলের স্বাস্থ্য"),
            tr(b, "Photograph an affected leaf to get advice", "আক্রান্ত পাতার ছবি তুলে পরামর্শ নিন")
        )
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (!vm.isOnline) {
                AppCard(container = Color(0xFFFFF8E1)) {
                    Text(
                        tr(b, "📴 You are offline. New photos are saved and checked automatically when internet returns.",
                            "📴 আপনি অফলাইনে আছেন। নতুন ছবি সংরক্ষিত থাকবে এবং ইন্টারনেট এলে স্বয়ংক্রিয়ভাবে পরীক্ষা হবে।")
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
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
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f).height(54.dp)
                ) { Text(tr(b, "📷 Take photo", "📷 ছবি তুলুন"), fontWeight = FontWeight.Bold) }
                OutlinedButton(
                    onClick = { galleryLauncher.launch("image/*") },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f).height(54.dp)
                ) { Text(tr(b, "🖼 Gallery", "🖼 গ্যালারি"), fontWeight = FontWeight.Bold) }
            }

            SectionTitle(tr(b, "History", "ইতিহাস"))
            if (mine.isEmpty()) {
                AppCard {
                    EmptyState("🍃", tr(b, "No diagnoses yet.", "এখনো কোনো ডায়াগনোসিস নেই।"))
                }
            }
            mine.forEach { d -> DiagnosisCard(vm, d) }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
fun DiagnosisCard(vm: AppVm, d: Diagnosis) {
    val b = vm.bn
    AppCard {
        Row(verticalAlignment = Alignment.Top) {
            val bmp = remember(d.imagePath) { loadBitmap(d.imagePath, 300) }
            if (bmp != null) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.size(84.dp).clip(RoundedCornerShape(14.dp)),
                    contentScale = ContentScale.Crop
                )
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(fmtTime(d.time), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                if (!d.done) {
                    Pill(
                        if (vm.isOnline) tr(b, "⏳ Analysing…", "⏳ বিশ্লেষণ চলছে…") else tr(b, "⏳ Queued — waiting for internet", "⏳ অপেক্ষমাণ — ইন্টারনেটের অপেক্ষায়"),
                        Color(0xFFFFF8E1),
                        Color(0xFF8D6E00)
                    )
                } else {
                    val colors = diagColors(d.code)
                    Pill(diagTitle(d.code, b), colors.first, colors.second)
                }
            }
        }
        if (d.done) {
            Spacer(Modifier.height(10.dp))
            Text(diagAdvice(d.code, b), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(6.dp))
            Text(
                tr(b, "Prototype colour-based estimate, not a laboratory diagnosis.", "এটি রঙভিত্তিক প্রোটোটাইপ অনুমান, ল্যাব ডায়াগনোসিস নয়।"),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ======================================================================
// Weather
// ======================================================================
@Composable
fun WeatherScreen(vm: AppVm, onBack: () -> Unit) {
    val b = vm.bn
    val locLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        vm.onPermissionResult()
    }
    val w = vm.weather

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        PageHeader(
            tr(b, "Weather", "আবহাওয়া"),
            tr(b, "Forecast and farming alerts", "পূর্বাভাস ও কৃষি সতর্কতা"),
            onBack
        )
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            WeatherHero(vm)

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = {
                        locLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f)
                ) { Text(tr(b, "📍 My location", "📍 আমার অবস্থান")) }
                OutlinedButton(
                    onClick = { vm.refreshWeather() },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f)
                ) { Text(tr(b, "🔄 Refresh", "🔄 রিফ্রেশ")) }
            }

            if (vm.weatherState == "loading") {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            }

            if (w != null) {
                if (vm.weatherState == "cached") {
                    Pill(
                        tr(b, "Saved data from " + fmtTime(w.time), "সংরক্ষিত তথ্য: " + fmtTime(w.time)),
                        FarmMint,
                        FarmGreenDark
                    )
                }
                val alerts = alertsFor(w)
                SectionTitle(tr(b, "Alerts", "সতর্কতা"))
                if (alerts.isEmpty()) {
                    AppCard(container = FarmMint) {
                        Text(tr(b, "✅ No weather alerts. Conditions look fine for farm work.", "✅ কোনো সতর্কতা নেই। কৃষিকাজের জন্য আবহাওয়া ভালো।"))
                    }
                } else {
                    alerts.forEach { a ->
                        AppCard(container = Color(0xFFFFF3E0)) {
                            Text(alertText(a.kind, a.date, a.value, b), color = Color(0xFF8D4B00))
                        }
                    }
                }

                SectionTitle(tr(b, "5-day forecast", "৫ দিনের পূর্বাভাস"))
                AppCard {
                    w.days.forEach { d ->
                        val label = weatherText(d.code, b)
                        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(label.substringBefore(" "), fontSize = 26.sp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(d.date, fontWeight = FontWeight.SemiBold)
                                Text(
                                    label.substringAfter(" ") + "  •  💧 " + num(d.rain, 1) + " mm",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(num(d.max) + "° / " + num(d.min) + "°", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

// ======================================================================
// Crop library
// ======================================================================
@Composable
fun LibraryScreen(vm: AppVm) {
    val b = vm.bn
    var q by remember { mutableStateOf("") }
    var cat by remember { mutableStateOf(0) }
    val cats = listOf(
        tr(b, "All", "সব"),
        tr(b, "Growing", "চাষ"),
        tr(b, "Planting", "রোপণ"),
        tr(b, "Fertilizer", "সার"),
        tr(b, "Pests", "পোকা"),
        tr(b, "Diseases", "রোগ"),
        tr(b, "Tips", "পরামর্শ")
    )
    val results = LIBRARY.filter { it.matches(q) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        PageHeader(
            tr(b, "Crop Library", "ফসল তথ্য ভাণ্ডার"),
            tr(b, "Available offline", "অফলাইনেও পাওয়া যায়")
        )
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = q,
                onValueChange = { q = it },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                leadingIcon = { Text("🔍") },
                label = { Text(tr(b, "Search crop, pest or disease", "ফসল, পোকা বা রোগ খুঁজুন")) },
                modifier = Modifier.fillMaxWidth()
            )
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                cats.forEachIndexed { i, label ->
                    Pill(
                        label,
                        if (cat == i) FarmGreen else FarmMint,
                        if (cat == i) Color.White else FarmGreenDark,
                        Modifier.clickable { cat = i }
                    )
                }
            }
            if (results.isEmpty()) {
                AppCard { EmptyState("🔎", tr(b, "Nothing found. Try another word.", "কিছু পাওয়া যায়নি। অন্য শব্দ চেষ্টা করুন।")) }
            }
            results.forEach { crop -> CropCard(crop, b, cat) }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
fun CropCard(crop: CropInfo, b: Boolean, cat: Int) {
    var open by remember(cat) { mutableStateOf(cat != 0) }
    AppCard(Modifier.clickable { open = !open }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(50.dp).clip(CircleShape).background(FarmMint),
                contentAlignment = Alignment.Center
            ) { Text(cropEmoji(crop.name.en), fontSize = 26.sp) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(crop.name.of(b), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    tr(b, "Tap to see details", "বিস্তারিত দেখতে ট্যাপ করুন"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(if (open) "▲" else "▼", color = FarmGreen)
        }
        if (open) {
            Spacer(Modifier.height(10.dp))
            crop.sections(b).filter { cat == 0 || it.first == cat }.forEach { (_, label, text) ->
                Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = FarmGreen)
                Text(text, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

// ======================================================================
// Forum
// ======================================================================
@Composable
fun ForumScreen(vm: AppVm) {
    val b = vm.bn
    var text by remember { mutableStateOf("") }
    var attach by remember { mutableStateOf<Uri?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> attach = uri }
    val isAdmin = vm.user?.isAdmin == true
    val visible = vm.posts.filter { it.reportedBy.isEmpty() || isAdmin || it.author == vm.user?.username }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        PageHeader(
            tr(b, "Community", "কমিউনিটি"),
            tr(b, "Ask, share and learn with other farmers", "অন্য কৃষকদের সাথে প্রশ্ন, শেয়ার ও শিখুন")
        )
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            AppCard {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    shape = RoundedCornerShape(14.dp),
                    label = { Text(tr(b, "Ask a question or share a tip", "প্রশ্ন করুন বা পরামর্শ শেয়ার করুন")) },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { picker.launch("image/*") }) {
                        Text(
                            if (attach != null) tr(b, "✅ Image attached", "✅ ছবি যুক্ত হয়েছে")
                            else tr(b, "📎 Add image", "📎 ছবি যুক্ত করুন")
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Button(
                        enabled = text.isNotBlank(),
                        shape = RoundedCornerShape(12.dp),
                        onClick = {
                            vm.createPost(text, attach)
                            text = ""
                            attach = null
                        }
                    ) { Text(tr(b, "Post", "পোস্ট"), fontWeight = FontWeight.Bold) }
                }
            }
            if (visible.isEmpty()) {
                AppCard { EmptyState("💬", tr(b, "No posts yet. Be the first to ask!", "এখনো কোনো পোস্ট নেই। প্রথম প্রশ্নটি আপনিই করুন!")) }
            }
            visible.forEach { p -> PostCard(vm, p) }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
fun PostCard(vm: AppVm, p: Post) {
    val b = vm.bn
    val me = vm.user?.username ?: ""
    val isAdmin = vm.user?.isAdmin == true
    var comment by remember { mutableStateOf("") }
    val liked = p.likedBy.contains(me)
    val reportedByMe = p.reportedBy.contains(me)

    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(p.author)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(p.author, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(fmtTime(p.time), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (p.reportedBy.isNotEmpty() && isAdmin) {
                Pill("⚠ " + p.reportedBy.size, MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.error)
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(p.text, style = MaterialTheme.typography.bodyLarge)
        if (p.imagePath.isNotEmpty()) {
            val bmp = remember(p.imagePath) { loadBitmap(p.imagePath, 700) }
            if (bmp != null) {
                Spacer(Modifier.height(8.dp))
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxWidth().height(190.dp).clip(RoundedCornerShape(14.dp)),
                    contentScale = ContentScale.Crop
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { vm.toggleLike(p.id) }) {
                Text(
                    (if (liked) "💚 " else "🤍 ") + tr(b, "Like", "পছন্দ") + " (" + p.likedBy.size + ")",
                    fontWeight = if (liked) FontWeight.Bold else FontWeight.Normal
                )
            }
            TextButton(enabled = !reportedByMe, onClick = { vm.reportPost(p.id) }) {
                Text(if (reportedByMe) tr(b, "Reported", "রিপোর্ট হয়েছে") else tr(b, "Report", "রিপোর্ট"))
            }
            if (p.author == me || isAdmin) {
                TextButton(onClick = { vm.removePost(p.id) }) { Text(tr(b, "Delete", "মুছুন")) }
            }
            if (isAdmin && p.reportedBy.isNotEmpty()) {
                TextButton(onClick = { vm.dismissReports(p.id) }) { Text(tr(b, "Dismiss", "খারিজ")) }
            }
        }
        if (p.comments.isNotEmpty()) {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(FarmCream).padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                p.comments.forEach { c -> Text("💬 " + c, style = MaterialTheme.typography.bodySmall) }
            }
            Spacer(Modifier.height(6.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = comment,
                onValueChange = { comment = it },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                label = { Text(tr(b, "Write a comment", "মন্তব্য লিখুন")) },
                modifier = Modifier.weight(1f)
            )
            TextButton(
                enabled = comment.isNotBlank(),
                onClick = { vm.addComment(p.id, comment); comment = "" }
            ) { Text(tr(b, "Send", "পাঠান"), fontWeight = FontWeight.Bold) }
        }
    }
}

// ======================================================================
// Profile, notifications, admin
// ======================================================================
@Composable
fun MoreScreen(vm: AppVm) {
    val b = vm.bn
    val ctx = LocalContext.current
    val u = vm.user
    val isAdmin = u?.isAdmin == true
    val myNotes = vm.notes.filter { it.owner == u?.username }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        PageHeader(
            tr(b, "Profile", "প্রোফাইল"),
            tr(b, "Settings, notifications and tools", "সেটিংস, নোটিফিকেশন ও টুলস")
        )
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            AppCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar(u?.username ?: "", 54.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(u?.username ?: "", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Pill(
                            if (isAdmin) tr(b, "Administrator", "অ্যাডমিন") else tr(b, "Farmer", "কৃষক"),
                            FarmMint,
                            FarmGreenDark
                        )
                    }
                }
            }

            SectionTitle(tr(b, "Settings", "সেটিংস"))
            AppCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(tr(b, "🌐 Bengali language", "🌐 বাংলা ভাষা"), Modifier.weight(1f))
                    Switch(checked = b, onCheckedChange = { vm.setLanguage(it) })
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(tr(b, "📴 Simulate offline mode", "📴 অফলাইন মোড সিমুলেট করুন"), Modifier.weight(1f))
                    Switch(checked = vm.simOffline, onCheckedChange = { vm.updateSimOffline(it) })
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (vm.isOnline) tr(b, "🟢 Status: Online", "🟢 অবস্থা: অনলাইন") else tr(b, "🔴 Status: Offline", "🔴 অবস্থা: অফলাইন"),
                        Modifier.weight(1f)
                    )
                    TextButton(onClick = { vm.updateDeviceOnline(checkOnline(ctx)) }) {
                        Text(tr(b, "Recheck", "পুনরায় পরীক্ষা"))
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { SectionTitle(tr(b, "Notifications", "নোটিফিকেশন")) }
                TextButton(onClick = { vm.clearMyNotes() }) { Text(tr(b, "Clear all", "সব মুছুন")) }
            }
            if (myNotes.isEmpty()) {
                AppCard { EmptyState("🔔", tr(b, "No notifications yet.", "এখনো কোনো নোটিফিকেশন নেই।")) }
            }
            myNotes.take(30).forEach { n ->
                val txt = noteText(n, b)
                if (txt.isNotBlank()) {
                    AppCard(container = Color.White) {
                        Text(txt, style = MaterialTheme.typography.bodyMedium)
                        Text(fmtTime(n.time), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            if (isAdmin) {
                SectionTitle(tr(b, "Admin dashboard", "অ্যাডমিন ড্যাশবোর্ড"))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile(vm.accounts.size.toString(), tr(b, "Users", "ব্যবহারকারী"), Modifier.weight(1f))
                    StatTile(vm.posts.size.toString(), tr(b, "Posts", "পোস্ট"), Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile(vm.posts.count { it.reportedBy.isNotEmpty() }.toString(), tr(b, "Reported", "রিপোর্ট"), Modifier.weight(1f))
                    StatTile(vm.diags.count { !it.done }.toString(), tr(b, "Pending scans", "অপেক্ষমাণ স্ক্যান"), Modifier.weight(1f))
                }

                SectionTitle(tr(b, "User management", "ব্যবহারকারী ব্যবস্থাপনা"))
                AppCard {
                    vm.accounts.forEach { acc ->
                        Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Avatar(acc.username, 34.dp)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(acc.username + (if (acc.isAdmin) " ★" else ""), fontWeight = FontWeight.SemiBold)
                                Text(
                                    if (acc.active) tr(b, "Active", "সক্রিয়") else tr(b, "Deactivated", "নিষ্ক্রিয়"),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (acc.active) FarmGreen else FarmRed
                                )
                            }
                            if (!acc.isAdmin) {
                                Switch(checked = acc.active, onCheckedChange = { vm.setActive(acc.username, it) })
                            }
                        }
                    }
                }

                SectionTitle(tr(b, "Reported posts", "রিপোর্ট করা পোস্ট"))
                val reported = vm.posts.filter { it.reportedBy.isNotEmpty() }
                if (reported.isEmpty()) {
                    AppCard { EmptyState("✅", tr(b, "No reported posts.", "কোনো রিপোর্ট করা পোস্ট নেই।")) }
                }
                reported.forEach { p ->
                    AppCard {
                        Text(p.author + ": " + p.text, style = MaterialTheme.typography.bodyMedium)
                        Row {
                            TextButton(onClick = { vm.dismissReports(p.id) }) { Text(tr(b, "Dismiss", "খারিজ")) }
                            TextButton(onClick = { vm.removePost(p.id) }) { Text(tr(b, "Remove", "মুছুন")) }
                        }
                    }
                }

                SectionTitle(tr(b, "Security & activity log", "নিরাপত্তা ও কার্যক্রম লগ"))
                AppCard {
                    if (vm.logs.isEmpty()) {
                        Text(tr(b, "No activity yet.", "এখনো কোনো কার্যক্রম নেই।"))
                    }
                    vm.logs.take(40).forEach { e ->
                        Text("• " + fmtTime(e.time) + " — " + e.text, style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(3.dp))
                    }
                }
            }

            OutlinedButton(
                onClick = { vm.logout() },
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = FarmRed),
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) { Text(tr(b, "Logout", "লগআউট"), fontWeight = FontWeight.Bold) }
            Spacer(Modifier.height(12.dp))
        }
    }
}