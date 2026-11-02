package com.ciro.gatal

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*

object TelegramBackupManager {
    const val BOT_TOKEN = "9190592895:AAE_HT_68nvR5p1ku_V7_08pRmo3c3QWP9I"
    const val CHAT_ID = "1936436999"

    data class TelegramBackupMessage(
        val messageId: Long,
        val date: Long,
        val fileName: String,
        val fileId: String,
        val recordCount: Int,
        val formattedDate: String
    )

    /**
     * Upload backup JSON langsung ke chat Telegram Tuan Ciro via sendDocument
     */
    suspend fun uploadBackupToTelegram(jsonContent: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val boundary = "==Boundary_${System.currentTimeMillis()}=="
            val url = URL("https://api.telegram.org/bot$BOT_TOKEN/sendDocument")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 15000
            conn.readTimeout = 20000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            conn.setRequestProperty("User-Agent", "CiroGatal-App")

            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "ciro_gatal_backup_$timeStamp.json"
            val fileBytes = jsonContent.toByteArray(Charsets.UTF_8)
            val count = try { JSONArray(jsonContent).length() } catch (_: Exception) { 0 }

            val caption = "🛡️ <b>CADANGAN DATA CIRO GATAL</b> 📦\n" +
                    "━━━━━━━━━━━━━━━━━━━━\n" +
                    "• Total Riwayat: <b>$count data</b>\n" +
                    "• Waktu Cadang: <i>${SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date())}</i>\n" +
                    "• Status: Berhasil tersimpan aman di Cloud Telegram!"

            conn.outputStream.use { os ->
                // Field chat_id
                os.write("--$boundary\r\n".toByteArray())
                os.write("Content-Disposition: form-data; name=\"chat_id\"\r\n\r\n".toByteArray())
                os.write("$CHAT_ID\r\n".toByteArray())

                // Field caption
                os.write("--$boundary\r\n".toByteArray())
                os.write("Content-Disposition: form-data; name=\"caption\"\r\n\r\n".toByteArray())
                os.write("$caption\r\n".toByteArray())

                // Field parse_mode
                os.write("--$boundary\r\n".toByteArray())
                os.write("Content-Disposition: form-data; name=\"parse_mode\"\r\n\r\n".toByteArray())
                os.write("HTML\r\n".toByteArray())

                // Field document
                os.write("--$boundary\r\n".toByteArray())
                os.write("Content-Disposition: form-data; name=\"document\"; filename=\"$fileName\"\r\n".toByteArray())
                os.write("Content-Type: application/json\r\n\r\n".toByteArray())
                os.write(fileBytes)
                os.write("\r\n".toByteArray())

                // End
                os.write("--$boundary--\r\n".toByteArray())
                os.flush()
            }

            val code = conn.responseCode
            val responseText = if (code in 200..299) {
                conn.inputStream.bufferedReader().use { it.readText() }
            } else {
                conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
            }
            conn.disconnect()

            val jsonResp = JSONObject(responseText)
            return@withContext jsonResp.optBoolean("ok", false)
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext false
        }
    }

    /**
     * Cari berkas backup ciro_gatal_backup_*.json dari getUpdates Telegram Bot
     */
    suspend fun fetchLatestBackupsFromTelegram(): List<TelegramBackupMessage> = withContext(Dispatchers.IO) {
        val result = mutableListOf<TelegramBackupMessage>()
        try {
            val url = URL("https://api.telegram.org/bot$BOT_TOKEN/getUpdates?offset=-20&limit=20")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            conn.setRequestProperty("User-Agent", "CiroGatal-App")

            if (conn.responseCode == 200) {
                val text = conn.inputStream.bufferedReader().use { it.readText() }
                val root = JSONObject(text)
                if (root.optBoolean("ok", false)) {
                    val updates = root.optJSONArray("result") ?: JSONArray()
                    val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
                    for (i in 0 until updates.length()) {
                        val u = updates.getJSONObject(i)
                        val msg = u.optJSONObject("message") 
                            ?: u.optJSONObject("channel_post") 
                            ?: u.optJSONObject("edited_message") 
                            ?: continue

                        val doc = msg.optJSONObject("document") ?: continue
                        val fileName = doc.optString("file_name", "")
                        if (fileName.startsWith("ciro_gatal_backup") && fileName.endsWith(".json")) {
                            val fileId = doc.getString("file_id")
                            val date = msg.optLong("date", 0L) * 1000L
                            val messageId = msg.optLong("message_id", 0L)

                            result.add(
                                TelegramBackupMessage(
                                    messageId = messageId,
                                    date = date,
                                    fileName = fileName,
                                    fileId = fileId,
                                    recordCount = 0,
                                    formattedDate = sdf.format(Date(date))
                                )
                            )
                        }
                    }
                }
            }
            conn.disconnect()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext result.reversed() // Yang terbaru di atas
    }

    /**
     * Download isi file JSON dari Telegram dengan file_id
     */
    suspend fun downloadBackupContentByFileId(fileId: String): String? = withContext(Dispatchers.IO) {
        try {
            // 1. Get file path
            val getFileUrl = URL("https://api.telegram.org/bot$BOT_TOKEN/getFile?file_id=$fileId")
            val conn1 = getFileUrl.openConnection() as HttpURLConnection
            conn1.connectTimeout = 7000
            conn1.readTimeout = 7000
            val text1 = conn1.inputStream.bufferedReader().use { it.readText() }
            conn1.disconnect()

            val json1 = JSONObject(text1)
            if (!json1.optBoolean("ok", false)) return@withContext null
            val filePath = json1.getJSONObject("result").getString("file_path")

            // 2. Download content
            val dlUrl = URL("https://api.telegram.org/file/bot$BOT_TOKEN/$filePath")
            val conn2 = dlUrl.openConnection() as HttpURLConnection
            conn2.connectTimeout = 10000
            conn2.readTimeout = 15000
            val content = conn2.inputStream.bufferedReader().use { it.readText() }
            conn2.disconnect()
            return@withContext content
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext null
        }
    }
}
