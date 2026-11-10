package com.locora.app

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Process
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

data class ChatMessage(
    val id: String,
    val body: String,
    val createdAt: Long,
    val type: String = "text",
    val attachment: String = ""
)

data class RecentApp(val label: String, val packageName: String, val lastUsed: Long)
data class BatteryInfo(val percent: Int, val charging: Boolean)

object LocalStore {
    private const val PREFS = "locora_local"
    private const val MSG_KEY = "local_chat_messages"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun messages(context: Context): MutableList<ChatMessage> {
        val result = mutableListOf<ChatMessage>()
        val array = try { JSONArray(prefs(context).getString(MSG_KEY, "[]")) }
                    catch (_: Exception) { JSONArray() }
        for (i in 0 until array.length()) {
            val o = array.optJSONObject(i) ?: continue
            result += ChatMessage(
                id = o.optString("id", i.toString()),
                body = o.optString("body"),
                createdAt = o.optLong("createdAt"),
                type = o.optString("type", "text"),
                attachment = o.optString("attachment")
            )
        }
        return result
    }

    fun addMessage(context: Context, body: String, type: String = "text", attachment: String = ""): ChatMessage {
        val message = ChatMessage(
            id = System.currentTimeMillis().toString() + "-" + SecureRandom().nextInt(100000),
            body = body,
            createdAt = System.currentTimeMillis(),
            type = type,
            attachment = attachment
        )
        val list = messages(context)
        list += message
        val array = JSONArray()
        list.forEach { item ->
            array.put(JSONObject().put("id", item.id).put("body", item.body)
                .put("createdAt", item.createdAt).put("type", item.type)
                .put("attachment", item.attachment))
        }
        prefs(context).edit().putString(MSG_KEY, array.toString()).apply()
        return message
    }

    fun hasPin(context: Context): Boolean = prefs(context).contains("chat_pin_hash")

    fun setPin(context: Context, pin: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val hash = derive(pin, salt)
        prefs(context).edit()
            .putString("chat_pin_salt", Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString("chat_pin_hash", Base64.encodeToString(hash, Base64.NO_WRAP))
            .apply()
    }

    fun verifyPin(context: Context, pin: String): Boolean {
        val p = prefs(context)
        val saltText = p.getString("chat_pin_salt", null) ?: return false
        val hashText = p.getString("chat_pin_hash", null) ?: return false
        return try {
            val expected = Base64.decode(hashText, Base64.NO_WRAP)
            val actual = derive(pin, Base64.decode(saltText, Base64.NO_WRAP))
            java.security.MessageDigest.isEqual(expected, actual)
        } catch (_: Exception) { false }
    }

    private fun derive(pin: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, 120000, 256)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally { spec.clearPassword() }
    }

    fun recentApps(context: Context): List<RecentApp> {
        val ops = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = try {
            ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        } catch (_: Exception) { AppOpsManager.MODE_ERRORED }
        if (mode != AppOpsManager.MODE_ALLOWED) return emptyList()

        val manager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        val stats = manager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - 7L * 24 * 60 * 60 * 1000, now)
        return stats.orEmpty()
            .filter { it.packageName != context.packageName && it.lastTimeUsed > 0 }
            .groupBy { it.packageName }
            .mapNotNull { (packageName, entries) ->
                val last = entries.maxOfOrNull { it.lastTimeUsed } ?: return@mapNotNull null
                try {
                    val app = context.packageManager.getApplicationInfo(packageName, 0)
                    if (context.packageManager.getLaunchIntentForPackage(packageName) == null) return@mapNotNull null
                    RecentApp(context.packageManager.getApplicationLabel(app).toString(), packageName, last)
                } catch (_: Exception) { null }
            }
            .sortedByDescending { it.lastUsed }
            .take(5)
    }

    fun battery(context: Context): BatteryInfo {
        val intent = context.registerReceiver(null, android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = intent?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, 100) ?: 100
        val status = intent?.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1) ?: -1
        val percent = if (level >= 0 && scale > 0) (level * 100 / scale).coerceIn(0, 100) else -1
        val charging = status == android.os.BatteryManager.BATTERY_STATUS_CHARGING ||
            status == android.os.BatteryManager.BATTERY_STATUS_FULL
        return BatteryInfo(percent, charging)
    }

    fun shareExport(context: Context, json: Boolean) {
        val list = messages(context)
        val text = if (json) {
            val array = JSONArray()
            list.forEach {
                array.put(JSONObject().put("id", it.id).put("body", it.body)
                    .put("createdAt", it.createdAt).put("type", it.type).put("attachment", it.attachment))
            }
            array.toString(2)
        } else {
            list.joinToString("\n") {
                val time = java.text.DateFormat.getDateTimeInstance().format(java.util.Date(it.createdAt))
                "[$time] You: " + when (it.type) {
                    "image" -> "Photo attached: " + it.attachment
                    "voice" -> "Voice message: " + it.attachment
                    else -> it.body
                }
            }
        }
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, if (json) "LOCORA chat backup (JSON)" else "LOCORA chat export")
            putExtra(Intent.EXTRA_TEXT, text.ifBlank { "No messages yet." })
        }
        context.startActivity(Intent.createChooser(send, "Export LOCORA chat"))
    }
}
