package com.ciro.gatal

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.widget.Toast
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

data class HistoryItem(
    val id: String = UUID.randomUUID().toString(),
    val type: String, // "SUCCESS", "FAILED", "TOUCH_ONLY", "REFLEX_SCRATCH", "LOOK_WOUND_OK", "LOOK_WOUND_FAILED"
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
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("ciro_gatal_prefs", Context.MODE_PRIVATE) }

    var historyList by remember { mutableStateOf(loadHistory(prefs)) }
    var lastLookTime by remember { mutableStateOf(prefs.getLong("last_look_time", 0L)) }
    var currentTime by remember { mutableStateOf(System.currentTimeMillis()) }

    var isSessionActive by remember { mutableStateOf(false) }
    var clickCount by remember { mutableStateOf(0) }
    var sessionStartTime by remember { mutableStateOf(0L) }
    var currentElapsedSeconds by remember { mutableStateOf(0L) }
    var selectedTab by remember { mutableStateOf(0) } // 0: Fighter, 1: Riwayat, 2: Analisa

    // Dialog State Konfirmasi Cadangkan & Restore
    var showBackupConfirmDialog by remember { mutableStateOf(false) }
    var showRestoreSelectDialog by remember { mutableStateOf(false) }
    var showRestoreConfirmDialog by remember { mutableStateOf(false) }
    var isOperatingCloud by remember { mutableStateOf(false) }
    var cloudBackupList by remember { mutableStateOf<List<TelegramBackupManager.TelegramBackupMessage>>(emptyList()) }
    var pendingRestoreContent by remember { mutableStateOf<String?>(null) }
    var pendingRestoreItemCount by remember { mutableStateOf(0) }

    // Heartbeat waktu per detik untuk hitung mundur cooldown Bandwidth Melihat
    LaunchedEffect(Unit) {
        while (true) {
            currentTime = System.currentTimeMillis()
            delay(1000)
        }
    }

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
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Default.Insights, contentDescription = "Analisa") },
                    label = { Text("Analisa") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFFFBBF24),
                        selectedTextColor = Color(0xFFFBBF24),
                        indicatorColor = Color(0xFF1E293B),
                        unselectedIconColor = Color(0xFF64748B),
                        unselectedTextColor = Color(0xFF64748B)
                    )
                )
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when (selectedTab) {
                0 -> TrackerScreen(
                    isSessionActive = isSessionActive,
                    clickCount = clickCount,
                    durationSeconds = currentElapsedSeconds,
                    lastLookTime = lastLookTime,
                    currentTime = currentTime,
                    onStartSession = {
                        clickCount = 0
                        isSessionActive = true
                        triggerHaptic(context, true)
                    },
                    onClickTarget = {
                        clickCount++
                        triggerHaptic(context, false)
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
                    onRecordLookWound = {
                        val now = System.currentTimeMillis()
                        val diffSeconds = (now - lastLookTime) / 1000
                        val isOverBudget = lastLookTime > 0 && diffSeconds < 900 // kurang dari 15 menit
                        
                        val newItem = if (isOverBudget) {
                            HistoryItem(
                                type = "LOOK_WOUND_FAILED",
                                durationSeconds = diffSeconds,
                                timestamp = now
                            )
                        } else {
                            HistoryItem(
                                type = "LOOK_WOUND_OK",
                                durationSeconds = if (lastLookTime == 0L) 900 else diffSeconds,
                                timestamp = now
                            )
                        }

                        lastLookTime = now
                        prefs.edit().putLong("last_look_time", now).apply()
                        historyList = listOf(newItem) + historyList
                        saveHistory(prefs, historyList)
                        triggerHaptic(context, true)

                        if (isOverBudget) {
                            Toast.makeText(context, "⚠️ Bandwidth Jebol! Belum 15 menit sudah melihat luka lagi. Mental bisa drop!", Toast.LENGTH_LONG).show()
                        } else {
                            Toast.makeText(context, "✅ Kuota melihat aman (sudah lewat 15 menit). Tetap tenang!", Toast.LENGTH_SHORT).show()
                        }
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
                1 -> HistoryScreen(
                    historyList = historyList,
                    onClearHistory = {
                        historyList = emptyList()
                        saveHistory(prefs, historyList)
                    },
                    onRequestBackup = {
                        showBackupConfirmDialog = true
                    },
                    onRequestRestore = {
                        isOperatingCloud = true
                        scope.launch {
                            val list = TelegramBackupManager.fetchLatestBackupsFromTelegram()
                            isOperatingCloud = false
                            if (list.isEmpty()) {
                                Toast.makeText(context, "Belum ada berkas cadangan ditemukan di Telegram!", Toast.LENGTH_LONG).show()
                            } else {
                                cloudBackupList = list
                                showRestoreSelectDialog = true
                            }
                        }
                    }
                )
                2 -> AnalysisScreen(historyList = historyList)
            }

            // DIALOG 1: KONFIRMASI SEBELUM CADANGKAN DATA
            if (showBackupConfirmDialog) {
                AlertDialog(
                    onDismissRequest = { showBackupConfirmDialog = false },
                    icon = { Icon(Icons.Default.CloudUpload, contentDescription = null, tint = Color(0xFF38BDF8)) },
                    title = { Text("Cadangkan Data ke Telegram?", fontWeight = FontWeight.Bold) },
                    text = {
                        Text(
                            "Total ${historyList.size} catatan riwayat tahan gatal akan dikirim dan disimpan dengan aman ke Telegram Tuan Ciro sebagai berkas cadangan JSON.\n\nLanjutkan pencadangan sekarang?",
                            color = Color(0xFFCBD5E1),
                            fontSize = 13.sp
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                showBackupConfirmDialog = false
                                isOperatingCloud = true
                                scope.launch {
                                    val jsonStr = exportHistoryToJson(historyList)
                                    val ok = TelegramBackupManager.uploadBackupToTelegram(jsonStr)
                                    isOperatingCloud = false
                                    if (ok) {
                                        Toast.makeText(context, "✅ Berhasil dicadangkan ke Telegram!", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "❌ Gagal mengunggah ke Telegram. Cek koneksi!", Toast.LENGTH_LONG).show()
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7))
                        ) {
                            Text("Ya, Cadangkan")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showBackupConfirmDialog = false }) {
                            Text("Batal", color = Color(0xFF94A3B8))
                        }
                    },
                    containerColor = Color(0xFF1E293B)
                )
            }

            // DIALOG 2: PILIH VERSI CADANGAN DARI TELEGRAM
            if (showRestoreSelectDialog) {
                AlertDialog(
                    onDismissRequest = { showRestoreSelectDialog = false },
                    icon = { Icon(Icons.Default.CloudDownload, contentDescription = null, tint = Color(0xFF34D399)) },
                    title = { Text("Pilih Cadangan Telegram", fontWeight = FontWeight.Bold) },
                    text = {
                        Column {
                            Text(
                                "Ditemukan berkas cadangan di riwayat bot Telegram. Pilih salah satu untuk dipulihkan:",
                                color = Color(0xFFCBD5E1),
                                fontSize = 13.sp,
                                modifier = Modifier.padding(bottom = 10.dp)
                            )
                            LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp)) {
                                items(cloudBackupList) { item ->
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp)
                                            .clickable {
                                                showRestoreSelectDialog = false
                                                isOperatingCloud = true
                                                scope.launch {
                                                    val raw = TelegramBackupManager.downloadBackupContentByFileId(item.fileId)
                                                    isOperatingCloud = false
                                                    if (!raw.isNullOrBlank()) {
                                                        val count = try { JSONArray(raw).length() } catch (_: Exception) { 0 }
                                                        pendingRestoreContent = raw
                                                        pendingRestoreItemCount = count
                                                        showRestoreConfirmDialog = true
                                                    } else {
                                                        Toast.makeText(context, "Gagal mengunduh berkas dari Telegram!", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            },
                                        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A))
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp).fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Default.InsertDriveFile, contentDescription = null, tint = Color(0xFF38BDF8))
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(item.fileName, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                                Text(item.formattedDate, color = Color(0xFF94A3B8), fontSize = 11.sp)
                                            }
                                            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color(0xFF64748B))
                                        }
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showRestoreSelectDialog = false }) {
                            Text("Tutup", color = Color(0xFF94A3B8))
                        }
                    },
                    containerColor = Color(0xFF1E293B)
                )
            }

            // DIALOG 3: KONFIRMASI MUTLAK SEBELUM RESTORE DATA DITIMPA
            if (showRestoreConfirmDialog) {
                AlertDialog(
                    onDismissRequest = { showRestoreConfirmDialog = false },
                    icon = { Icon(Icons.Default.WarningAmber, contentDescription = null, tint = Color(0xFFFBBF24)) },
                    title = { Text("Konfirmasi Pulihkan Data?", fontWeight = FontWeight.Bold) },
                    text = {
                        Text(
                            "⚠️ PERHATIAN:\n\nBerkas cadangan berisi $pendingRestoreItemCount riwayat akan dimuat ke aplikasi.\n\nApakah Tuan Ciro ingin MENGGABUNGKAN dengan data lokal saat ini, atau MENIMPA (Ganti Total)?",
                            color = Color(0xFFE2E8F0),
                            fontSize = 13.sp,
                            lineHeight = 18.sp
                        )
                    },
                    confirmButton = {
                        Row {
                            TextButton(
                                onClick = {
                                    showRestoreConfirmDialog = false
                                    pendingRestoreContent?.let { raw ->
                                        val imported = parseHistoryFromJson(raw)
                                        // GABUNGKAN
                                        val combined = (imported + historyList).distinctBy { it.id }
                                        historyList = combined
                                        saveHistory(prefs, combined)
                                        Toast.makeText(context, "✅ Berhasil digabungkan! Total: ${combined.size} data", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            ) {
                                Text("Gabungkan", color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    showRestoreConfirmDialog = false
                                    pendingRestoreContent?.let { raw ->
                                        val imported = parseHistoryFromJson(raw)
                                        // TIMPA
                                        historyList = imported
                                        saveHistory(prefs, imported)
                                        Toast.makeText(context, "✅ Data berhasil ditimpa dari cadangan!", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                            ) {
                                Text("Timpa Total")
                            }
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showRestoreConfirmDialog = false }) {
                            Text("Batal", color = Color(0xFF94A3B8))
                        }
                    },
                    containerColor = Color(0xFF1E293B)
                )
            }

            // LOADING CLOUD OVERLAY
            if (isOperatingCloud) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.6f)),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(color = Color(0xFF38BDF8))
                            Spacer(modifier = Modifier.height(14.dp))
                            Text("Menghubungkan ke Telegram Cloud...", color = Color.White, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TrackerScreen(
    isSessionActive: Boolean,
    clickCount: Int,
    durationSeconds: Long,
    lastLookTime: Long,
    currentTime: Long,
    onStartSession: () -> Unit,
    onClickTarget: () -> Unit,
    onStopSuccess: () -> Unit,
    onStopFailed: () -> Unit,
    onRecordLookWound: () -> Unit,
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
                .padding(bottom = 10.dp),
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

        // KARTU BANDWIDTH MELIHAT LUKA (JEDA MINIMAL 15 MENIT)
        val elapsedSinceLook = if (lastLookTime > 0) (currentTime - lastLookTime) / 1000 else 9999L
        val cooldownSeconds = if (elapsedSinceLook < 900) 900 - elapsedSinceLook else 0L
        val canLookSafely = cooldownSeconds == 0L

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (canLookSafely) Color(0xFF132E27) else Color(0xFF3B1D11)
            ),
            shape = RoundedCornerShape(14.dp)
        ) {
            Row(
                modifier = Modifier.padding(12.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (canLookSafely) Icons.Default.CheckCircle else Icons.Default.HourglassTop,
                            contentDescription = null,
                            tint = if (canLookSafely) Color(0xFF34D399) else Color(0xFFFBBF24),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "BANDWIDTH MELIHAT LUKA",
                            color = if (canLookSafely) Color(0xFF34D399) else Color(0xFFFBBF24),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (canLookSafely) {
                            "Status Aman: Boleh intip luka sekilas (jeda 15m terpenuhi)"
                        } else {
                            "Tahan! Cooldown sisa: ${String.format("%02d:%02d", cooldownSeconds / 60, cooldownSeconds % 60)} (Sering lihat bikin mental drop!)"
                        },
                        color = Color(0xFFE2E8F0),
                        fontSize = 11.sp
                    )
                }

                Button(
                    onClick = onRecordLookWound,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (canLookSafely) Color(0xFF059669) else Color(0xFFB45309)
                    ),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.RemoveRedEye, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Intip Luka", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        if (!isSessionActive) {
            // TAMPILAN IDLE SEBELUM MULAI
            Spacer(modifier = Modifier.weight(0.5f))

            Box(
                modifier = Modifier
                    .size(210.dp)
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
                        modifier = Modifier.size(50.dp)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "MULAI TAHAN\nGATAL",
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Black,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "Tekan tombol di atas saat gatal mulai menyerang.\nKetuk layar bebas untuk mengalihkan saraf!",
                color = Color(0xFF94A3B8),
                fontSize = 12.sp,
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
                    modifier = Modifier.weight(1f).height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB45309)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.PanTool, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Reflek Nyentuh", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = onRecordReflexScratch,
                    modifier = Modifier.weight(1f).height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF991B1B)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Reflek Garuk", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
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
    onClearHistory: () -> Unit,
    onRequestBackup: () -> Unit,
    onRequestRestore: () -> Unit
) {
    val successList = historyList.filter { it.type == "SUCCESS" }
    val failedList = historyList.filter { it.type == "FAILED" }
    val touchOnlyCount = historyList.count { it.type == "TOUCH_ONLY" }
    val reflexScratchCount = historyList.count { it.type == "REFLEX_SCRATCH" }
    val lookOkCount = historyList.count { it.type == "LOOK_WOUND_OK" }
    val lookFailedCount = historyList.count { it.type == "LOOK_WOUND_FAILED" }

    val avgSuccessDuration = if (successList.isNotEmpty()) {
        successList.map { it.durationSeconds }.average()
    } else {
        0.0
    }

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

        Spacer(modifier = Modifier.height(8.dp))

        // TOMBOL CADANGKAN & PULIHKAN DATA TELEGRAM CLOUD
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onRequestBackup,
                modifier = Modifier.weight(1f).height(44.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF38BDF8)),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF0284C7))
            ) {
                Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Cadangkan", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }

            OutlinedButton(
                onClick = onRequestRestore,
                modifier = Modifier.weight(1f).height(44.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF34D399)),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF059669))
            ) {
                Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Pulihkan", fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
                    text = "• Total Sesi Sukses: ${successList.size} kali\n" +
                            "• Total Pengalihan Klik: $totalSuccessClicks klik\n" +
                            "• Rata-rata durasi kamu menahan gatal: ${String.format(Locale.US, "%.1f", avgSuccessDuration)} detik",
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

        // KARTU 3: EVALUASI BANDWIDTH MELIHAT LUKA (MINIMAL 15 MENIT)
        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Visibility, contentDescription = null, tint = Color(0xFF38BDF8))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("3. Bandwidth Melihat Luka (Jeda 15 Menit)", color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "• Disiplin Kuota (Jeda ≥ 15 Menit): $lookOkCount kali\n" +
                            "• Pelanggaran Bandwidth (< 15 Menit): $lookFailedCount kali (Bikin mental drop!)",
                    color = Color(0xFFE2E8F0),
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            }
        }

        // KARTU 4: REFLEK TANPA TEKAN START (NYENTUH & GARUK)
        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF312E81)),
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.WarningAmber, contentDescription = null, tint = Color(0xFFA5B4FC))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("4. Reflek Langsung Tanpa Start", color = Color(0xFFA5B4FC), fontWeight = FontWeight.Bold, fontSize = 14.sp)
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
fun AnalysisScreen(historyList: List<HistoryItem>) {
    val successList = historyList.filter { it.type == "SUCCESS" }
    val failedList = historyList.filter { it.type == "FAILED" }
    val touchOnlyCount = historyList.count { it.type == "TOUCH_ONLY" }
    val reflexScratchCount = historyList.count { it.type == "REFLEX_SCRATCH" }
    val lookOkCount = historyList.count { it.type == "LOOK_WOUND_OK" }
    val lookFailedCount = historyList.count { it.type == "LOOK_WOUND_FAILED" }

    // 1. SKOR WILLPOWER KULIT (0 - 100)
    val totalUrges = successList.size + failedList.size + reflexScratchCount
    val score = if (totalUrges == 0 && touchOnlyCount == 0 && lookFailedCount == 0) {
        100
    } else {
        val winWeight = successList.size * 25
        val lookOkWeight = lookOkCount * 10
        val penalty = (failedList.size * 20) + (reflexScratchCount * 30) + (touchOnlyCount * 15) + (lookFailedCount * 15)
        val raw = 50 + winWeight + lookOkWeight - penalty
        raw.coerceIn(0, 100)
    }

    val (badgeTitle, badgeColor, badgeAdvice) = when {
        score >= 85 -> Triple("ZEN MASTER (Kebal Godaan)", Color(0xFF34D399), "Mentalmu sangat kokoh! Disiplin ini akan mempercepat pengeringan luka 2x lipat.")
        score >= 65 -> Triple("PEJUANG TANGGUH", Color(0xFF38BDF8), "Pertahanan bagus. Waspadai jam santai di mana tangan suka melayang ke luka tanpa sadar.")
        score >= 45 -> Triple("ZONA WASPADA", Color(0xFFFBBF24), "Jari masih sering gemas ingin meraba. Ingat: Sekali meraba, gerbang garukan langsung terbuka!")
        else -> Triple("DARURAT (Borgol Tangan!)", Color(0xFFF87171), "Reflek garukan terlalu dominan. Kompres es batu atau genggam benda padat saat gatal datang!")
    }

    // 2. EFISIENSI KETUKAN (CPS)
    val avgCpsSuccess = if (successList.isNotEmpty()) {
        val validSessions = successList.filter { it.durationSeconds > 0 }
        if (validSessions.isNotEmpty()) {
            validSessions.map { it.clickCount.toDouble() / it.durationSeconds.toDouble() }.average()
        } else 0.0
    } else 0.0

    // 3. JAM PALING RAWAN GATAL (PEAK HOURS)
    val hourCounts = IntArray(24)
    historyList.forEach { item ->
        val cal = Calendar.getInstance().apply { timeInMillis = item.timestamp }
        val h = cal.get(Calendar.HOUR_OF_DAY)
        hourCounts[h]++
    }
    var peakHour = -1
    var maxCountInHour = 0
    for (h in 0..23) {
        if (hourCounts[h] > maxCountInHour) {
            maxCountInHour = hourCounts[h]
            peakHour = h
        }
    }
    val peakHourText = if (peakHour >= 0 && maxCountInHour > 0) {
        String.format("%02d:00 - %02d:00 (%d serangan)", peakHour, (peakHour + 1) % 24, maxCountInHour)
    } else {
        "Belum cukup data jam"
    }

    // 4. KORELASI JEBAKAN MERABA
    val touchPenaltyRatio = if (touchOnlyCount + reflexScratchCount > 0) {
        val totalLapses = touchOnlyCount + reflexScratchCount
        (touchOnlyCount.toDouble() / totalLapses.toDouble()) * 100.0
    } else {
        0.0
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "DIAGNOSTIK & ANALISA POLA",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Pelatih mental digital untuk mempercepat kesembuhan kulit",
                color = Color(0xFF94A3B8),
                fontSize = 12.sp
            )
        }

        // KARTU 1: WILLPOWER METER & BADGE
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("SKOR KETAHANAN KULIT", color = Color(0xFF94A3B8), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text(
                            text = "$score / 100",
                            color = badgeColor,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Black
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    LinearProgressIndicator(
                        progress = { score / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(RoundedCornerShape(5.dp)),
                        color = badgeColor,
                        trackColor = Color(0xFF0F172A)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Verified, contentDescription = null, tint = badgeColor, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(badgeTitle, color = badgeColor, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(badgeAdvice, color = Color(0xFFCBD5E1), fontSize = 12.sp, lineHeight = 17.sp)
                }
            }
        }

        // KARTU 2: JAM RAWAN GATAL & POLA BIOLOGIS
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Alarm, contentDescription = null, tint = Color(0xFFFBBF24), modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("JAM RAWAN GATAL (PEAK HOURS)", color = Color(0xFFFBBF24), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Jam Paling Rentan Serangan:",
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp
                    )
                    Text(
                        text = peakHourText,
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "💡 Tips Taktis: Menjelang jam rawan ini, pastikan tangan sibuk atau siapkan kain basah dingin sebelum rasa gatal memancing jari secara reflek.",
                        color = Color(0xFFCBD5E1),
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                }
            }
        }

        // KARTU 3: EFEKTIVITAS KETUKAN (CPS)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Speed, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("EFEKTIVITAS DISTRAKSI SARAF (CPS)", color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Rata-rata Ritme Klik Sukses:", color = Color(0xFF94A3B8), fontSize = 11.sp)
                            Text(
                                text = "${String.format(Locale.US, "%.1f", avgCpsSuccess)} klik / detik",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Neurosains Gate Control membuktikan: ketukan cepat dan beruntun di layar HP membanjiri reseptor saraf sensorik sehingga sinyal gatal di otak langsung terblokir lebih cepat!",
                        color = Color(0xFFCBD5E1),
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                }
            }
        }

        // KARTU 4: DIAGNOSIS "JEBAKAN MERABA"
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF2D1829)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Psychology, contentDescription = null, tint = Color(0xFFE879F9), modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("DIAGNOSIS JEBAKAN MERABA", color = Color(0xFFE879F9), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "• Reflek Cuma Meraba: $touchOnlyCount kali\n" +
                                "• Reflek Langsung Garuk: $reflexScratchCount kali\n" +
                                "• Pelanggaran Bandwidth Melihat: $lookFailedCount kali",
                        color = Color(0xFFF5D0FE),
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Peringatan Absolut: Otak sering menipu dengan dalih 'cuma mau pegang/raba doang'. Begitu jari menyentuh kulit, tekanan mikro akan melepaskan histamin baru dan langsung berujung garukan. Pegang teguh aturan: Cuma Boleh Melihat!",
                        color = Color(0xFFCBD5E1),
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
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
        "LOOK_WOUND_OK" -> Quadruple(Color(0xFF132E27), Color(0xFF38BDF8), "Intip Luka (Sesuai Kuota)", Icons.Default.Visibility)
        "LOOK_WOUND_FAILED" -> Quadruple(Color(0xFF3B1D11), Color(0xFFF97316), "Intip Luka (Terlalu Sering <15m)", Icons.Default.VisibilityOff)
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
                when (item.type) {
                    "SUCCESS", "FAILED" -> {
                        Text(
                            "${item.clickCount} klik • Durasi: ${item.durationSeconds} detik",
                            color = Color(0xFFE2E8F0),
                            fontSize = 12.sp
                        )
                    }
                    "LOOK_WOUND_OK" -> {
                        Text("Disiplin jeda ${item.durationSeconds / 60} menit sejak intipan sebelumnya", color = Color(0xFFE2E8F0), fontSize = 12.sp)
                    }
                    "LOOK_WOUND_FAILED" -> {
                        Text("Baru jalan ${item.durationSeconds / 60} menit sudah diintip! Mental rentan down", color = Color(0xFFE2E8F0), fontSize = 12.sp)
                    }
                    "TOUCH_ONLY" -> {
                        Text("Hanya meraba luka (ingat: cukup lihat saja!)", color = Color(0xFFE2E8F0), fontSize = 12.sp)
                    }
                    else -> {
                        Text("Tergaruk spontan tanpa persiapan", color = Color(0xFFE2E8F0), fontSize = 12.sp)
                    }
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

fun exportHistoryToJson(list: List<HistoryItem>): String {
    val array = JSONArray()
    list.forEach { item ->
        val obj = JSONObject()
        obj.put("id", item.id)
        obj.put("type", item.type)
        obj.put("clickCount", item.clickCount)
        obj.put("durationSeconds", item.durationSeconds)
        obj.put("timestamp", item.timestamp)
        array.put(obj)
    }
    return array.toString(2)
}

fun parseHistoryFromJson(jsonStr: String): List<HistoryItem> {
    val list = mutableListOf<HistoryItem>()
    try {
        val array = JSONArray(jsonStr)
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

fun saveHistory(prefs: android.content.SharedPreferences, list: List<HistoryItem>) {
    try {
        val str = exportHistoryToJson(list)
        prefs.edit().putString("history_json", str).apply()
    } catch (_: Exception) {}
}

fun loadHistory(prefs: android.content.SharedPreferences): List<HistoryItem> {
    val str = prefs.getString("history_json", null) ?: return emptyList()
    return parseHistoryFromJson(str)
}
