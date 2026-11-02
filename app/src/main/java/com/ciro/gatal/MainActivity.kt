package com.ciro.gatal

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.CombinedVibration
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

data class HistoryItem(
    val id: String = UUID.randomUUID().toString(),
    val type: String, // "SUCCESS", "FAILED", "TOUCH_ONLY", "REFLEX_SCRATCH"
    val clickCount: Int = 0,
    val durationSeconds: Long = 0,
    val timestamp: Long = System.currentTimeMillis()
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    background = Color(0xFF0F172A),
                    surface = Color(0xFF1E293B),
                    primary = Color(0xFF38BDF8),
                    secondary = Color(0xFF34D399),
                    error = Color(0xFFF87171)
                )
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF0B0F19)
                ) {
                    CiroGatalApp()
                }
            }
        }
    }
}

fun triggerHaptic(context: Context, strong: Boolean = false) {
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            val vibrator = vibratorManager.defaultVibrator
            val effect = if (strong) {
                VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK)
            } else {
                VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
            }
            vibrator.vibrate(effect)
        } else {
            @Suppress("DEPRECATION")
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val effect = VibrationEffect.createOneShot(if (strong) 50 else 25, VibrationEffect.DEFAULT_AMPLITUDE)
                vibrator.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(if (strong) 50 else 25)
            }
        }
    } catch (_: Exception) {}
}

@Composable
fun CiroGatalApp() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("ciro_gatal_prefs", Context.MODE_PRIVATE) }
    
    var historyList by remember { mutableStateOf(loadHistory(prefs)) }
    var isSessionActive by remember { mutableStateOf(false) }
    var clickCount by remember { mutableStateOf(0) }
    var sessionStartTime by remember { mutableStateOf(0L) }
    var currentElapsedSeconds by remember { mutableStateOf(0L) }
    var clickPulse by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf(0) } // 0: Tracker, 1: Riwayat & Statistik

    // Timer effect saat sesi aktif
    LaunchedEffect(isSessionActive) {
        if (isSessionActive) {
            sessionStartTime = System.currentTimeMillis()
            while (isSessionActive) {
                currentElapsedSeconds = (System.currentTimeMillis() - sessionStartTime) / 1000
                delay(200)
            }
        } else {
            currentElapsedSeconds = 0L
        }
    }

    Scaffold(
        containerColor = Color(0xFF090D16),
        bottomBar = {
            NavigationBar(
                containerColor = Color(0xFF111827),
                tonalElevation = 8.dp
            ) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.TouchApp, contentDescription = "Tahan Gatal") },
                    label = { Text("Fighter") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFF38BDF8),
                        selectedTextColor = Color(0xFF38BDF8),
                        indicatorColor = Color(0xFF1E293B),
                        unselectedIconColor = Color(0xFF64748B),
                        unselectedTextColor = Color(0xFF64748B)
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.QueryStats, contentDescription = "Riwayat") },
                    label = { Text("Riwayat") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFF34D399),
                        selectedTextColor = Color(0xFF34D399),
                        indicatorColor = Color(0xFF1E293B),
                        unselectedIconColor = Color(0xFF64748B),
                        unselectedTextColor = Color(0xFF64748B)
                    )
                )
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (selectedTab == 0) {
                TrackerScreen(
                    isSessionActive = isSessionActive,
                    clickCount = clickCount,
                    durationSeconds = currentElapsedSeconds,
                    onStartSession = {
                        clickCount = 0
                        isSessionActive = true
                        triggerHaptic(context, true)
                    },
                    onClickTarget = {
                        clickCount++
                        triggerHaptic(context, false)
                        clickPulse = !clickPulse
                    },
                    onStopSuccess = {
                        val finalDuration = currentElapsedSeconds
                        val finalClicks = clickCount
                        isSessionActive = false
                        val newItem = HistoryItem(
                            type = "SUCCESS",
                            clickCount = finalClicks,
                            durationSeconds = finalDuration
                        )
                        historyList = listOf(newItem) + historyList
                        saveHistory(prefs, historyList)
                        triggerHaptic(context, true)
                    },
                    onStopFailed = {
                        val finalDuration = currentElapsedSeconds
                        val finalClicks = clickCount
                        isSessionActive = false
                        val newItem = HistoryItem(
                            type = "FAILED",
                            clickCount = finalClicks,
                            durationSeconds = finalDuration
                        )
                        historyList = listOf(newItem) + historyList
                        saveHistory(prefs, historyList)
                        triggerHaptic(context, true)
                    },
                    onRecordTouchOnly = {
                        val newItem = HistoryItem(
                            type = "TOUCH_ONLY",
                            clickCount = 0,
                            durationSeconds = 0
                        )
                        historyList = listOf(newItem) + historyList
                        saveHistory(prefs, historyList)
                        triggerHaptic(context, true)
                    },
                    onRecordReflexScratch = {
                        val newItem = HistoryItem(
                            type = "REFLEX_SCRATCH",
                            clickCount = 0,
                            durationSeconds = 0
                        )
                        historyList = listOf(newItem) + historyList
                        saveHistory(prefs, historyList)
                        triggerHaptic(context, true)
                    }
                )
            } else {
                HistoryScreen(
                    historyList = historyList,
                    onClearHistory = {
                        historyList = emptyList()
                        saveHistory(prefs, historyList)
                    }
                )
            }
        }
    }
}

@Composable
fun TrackerScreen(
    isSessionActive: Boolean,
    clickCount: Int,
    durationSeconds: Long,
    onStartSession: () -> Unit,
    onClickTarget: () -> Unit,
    onStopSuccess: () -> Unit,
    onStopFailed: () -> Unit,
    onRecordTouchOnly: () -> Unit,
    onRecordReflexScratch: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Banner Pesan Bijak Filosofis Tuan Ciro
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Visibility,
                        contentDescription = "Lihat Saja",
                        tint = Color(0xFF38BDF8),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "PRINSIP KESADARAN",
                        color = Color(0xFF38BDF8),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "\"Aku tahu kamu ingin menyamarkan warna gatal dengan menimpa rasa tersebut... Lebih baik jangan menimpa di atas gatal, tapi biarkan lewat hanya dengan melihatnya. Jangan sentuh apalagi menggaruk!\"",
                    color = Color(0xFFCBD5E1),
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        if (!isSessionActive) {
            // TAMPILAN IDLE SEBELUM MULAI
            Spacer(modifier = Modifier.weight(0.5f))

            Box(
                modifier = Modifier
                    .size(230.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            listOf(Color(0xFF0284C7), Color(0xFF0369A1), Color(0xFF0C4A6E))
                        )
                    )
                    .clickable { onStartSession() },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = "Mulai",
                        tint = Color.White,
                        modifier = Modifier.size(54.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "MULAI TAHAN\nGATAL",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Tekan tombol di atas saat gatal mulai menyerang.\nKetuk layar bebas untuk mengalihkan saraf!",
                color = Color(0xFF94A3B8),
                fontSize = 13.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.weight(1f))

            // Tombol Cepat Reflek Tanpa Sesi
            Text(
                text = "LOG REFLEK LANGSUNG (TANPA SESI)",
                color = Color(0xFF64748B),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onRecordTouchOnly,
                    modifier = Modifier.weight(1f).height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB45309)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.PanTool, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Reflek Nyentuh", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = onRecordReflexScratch,
                    modifier = Modifier.weight(1f).height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF991B1B)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Reflek Garuk", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        } else {
            // TAMPILAN SESI AKTIF (CLICKER ZONE)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("DURASI BERTAHAN", color = Color(0xFF94A3B8), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text(
                        text = String.format("%02d:%02d", durationSeconds / 60, durationSeconds % 60),
                        color = Color(0xFF38BDF8),
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Black
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text("TOTAL KETUKAN", color = Color(0xFF94A3B8), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text(
                        text = "$clickCount",
                        color = Color(0xFF34D399),
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }

            // Pesan Khusus Sesuai Arahan User Saat Diklik
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "🔥 \"Semakin gatal semakin kamu sering klik percaya gatalnya akan hilang\"",
                    color = Color(0xFFFBBF24),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(10.dp).fillMaxWidth()
                )
            }

            // AREA BESAR KLIK BEBAS (KLIK-KLIK TANPA ATURAN)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFF1E293B), Color(0xFF0F172A))
                        )
                    )
                    .border(2.dp, Color(0xFF38BDF8), RoundedCornerShape(24.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onClickTarget() },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.TouchApp,
                        contentDescription = "Ketuk",
                        tint = Color(0xFF38BDF8),
                        modifier = Modifier.size(70.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "KETUK DI SINI SEBANYAK-BANYAKNYA!",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Alihkan saraf jarimu ke layar HP\nJangan sentuh lukamu!",
                        color = Color(0xFF94A3B8),
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // DUA TOMBOL SELESAI SESI
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onStopSuccess,
                    modifier = Modifier.weight(1.2f).height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Gatal Hilang! (Stop)", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = onStopFailed,
                    modifier = Modifier.weight(1f).height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Default.Close, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Gagal Nahan", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun HistoryScreen(
    historyList: List<HistoryItem>,
    onClearHistory: () -> Unit
) {
    val successList = historyList.filter { it.type == "SUCCESS" }
    val failedList = historyList.filter { it.type == "FAILED" }
    val touchOnlyCount = historyList.count { it.type == "TOUCH_ONLY" }
    val reflexScratchCount = historyList.count { it.type == "REFLEX_SCRATCH" }

    val avgFailedDuration = if (failedList.isNotEmpty()) {
        failedList.map { it.durationSeconds }.average()
    } else {
        0.0
    }

    val totalSuccessClicks = successList.sumOf { it.clickCount }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "EVALUASI PERTAHANAN KULIT",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            if (historyList.isNotEmpty()) {
                IconButton(onClick = onClearHistory) {
                    Icon(Icons.Default.DeleteSweep, contentDescription = "Hapus", tint = Color(0xFF94A3B8))
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // KARTU 1: BERHASIL MENAHAN GATAL
        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF064E3B)),
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.EmojiEvents, contentDescription = null, tint = Color(0xFF34D399))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("1. Berhasil Menahan Gatal", color = Color(0xFF34D399), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "• Total Sesi Sukses: ${successList.size} kali\n• Total Pengalihan Klik: $totalSuccessClicks klik",
                    color = Color(0xFFD1FAE5),
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            }
        }

        // KARTU 2: SEWAKTU GAGAL
        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF450A0A)),
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Timer, contentDescription = null, tint = Color(0xFFF87171))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("2. Sewaktu Gagal Menahan", color = Color(0xFFF87171), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "• Total Gagal: ${failedList.size} kali\n• Rata-rata hanya mampu bertahan: ${String.format(Locale.US, "%.1f", avgFailedDuration)} detik",
                    color = Color(0xFFFEE2E2),
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            }
        }

        // KARTU 3: REFLEK TANPA TEKAN START (NYENTUH & GARUK)
        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF312E81)),
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.WarningAmber, contentDescription = null, tint = Color(0xFFA5B4FC))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("3. Reflek Langsung Tanpa Start", color = Color(0xFFA5B4FC), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "• Reflek Cuma Nyentuh/Meraba: $touchOnlyCount kali (Waspada pintu masuk garuk!)\n• Reflek Langsung Garuk: $reflexScratchCount kali",
                    color = Color(0xFFE0E7FF),
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            }
        }

        Text(
            text = "RIWAYAT SESI TERBARU",
            color = Color(0xFF64748B),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
        Spacer(modifier = Modifier.height(6.dp))

        if (historyList.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text("Belum ada riwayat tercatat. Mulai sesi pertahananmu!", color = Color(0xFF64748B), fontSize = 13.sp)
            }
        } else {
            val sdf = remember { SimpleDateFormat("HH:mm - dd/MM", Locale.getDefault()) }
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(historyList) { item ->
                    HistoryRowItem(item, sdf)
                }
            }
        }
    }
}

@Composable
fun HistoryRowItem(item: HistoryItem, sdf: SimpleDateFormat) {
    val (bgColor, titleColor, titleText, icon) = when (item.type) {
        "SUCCESS" -> Quadruple(Color(0xFF0F2E23), Color(0xFF34D399), "Berhasil Menahan Gatal", Icons.Default.CheckCircle)
        "FAILED" -> Quadruple(Color(0xFF2E1212), Color(0xFFF87171), "Gagal Menahan", Icons.Default.Cancel)
        "TOUCH_ONLY" -> Quadruple(Color(0xFF2E2412), Color(0xFFFBBF24), "Reflek Nyentuh Luka", Icons.Default.PanTool)
        else -> Quadruple(Color(0xFF24152E), Color(0xFFC084FC), "Reflek Garuk Langsung", Icons.Default.Warning)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = titleColor, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(titleText, color = titleColor, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                if (item.type == "SUCCESS" || item.type == "FAILED") {
                    Text(
                        "${item.clickCount} klik • Durasi: ${item.durationSeconds} detik",
                        color = Color(0xFFE2E8F0),
                        fontSize = 12.sp
                    )
                } else if (item.type == "TOUCH_ONLY") {
                    Text("Hanya meraba luka (ingat: cukup lihat saja!)", color = Color(0xFFE2E8F0), fontSize = 12.sp)
                } else {
                    Text("Tergaruk spontan tanpa persiapan", color = Color(0xFFE2E8F0), fontSize = 12.sp)
                }
            }
            Text(
                sdf.format(Date(item.timestamp)),
                color = Color(0xFF64748B),
                fontSize = 11.sp
            )
        }
    }
}

data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

fun saveHistory(prefs: android.content.SharedPreferences, list: List<HistoryItem>) {
    try {
        val array = JSONArray()
        list.take(100).forEach { item ->
            val obj = JSONObject()
            obj.put("id", item.id)
            obj.put("type", item.type)
            obj.put("clickCount", item.clickCount)
            obj.put("durationSeconds", item.durationSeconds)
            obj.put("timestamp", item.timestamp)
            array.put(obj)
        }
        prefs.edit().putString("history_json", array.toString()).apply()
    } catch (_: Exception) {}
}

fun loadHistory(prefs: android.content.SharedPreferences): List<HistoryItem> {
    val list = mutableListOf<HistoryItem>()
    try {
        val str = prefs.getString("history_json", null) ?: return emptyList()
        val array = JSONArray(str)
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            list.add(
                HistoryItem(
                    id = obj.optString("id", UUID.randomUUID().toString()),
                    type = obj.optString("type", "SUCCESS"),
                    clickCount = obj.optInt("clickCount", 0),
                    durationSeconds = obj.optLong("durationSeconds", 0L),
                    timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                )
            )
        }
    } catch (_: Exception) {}
    return list
}
