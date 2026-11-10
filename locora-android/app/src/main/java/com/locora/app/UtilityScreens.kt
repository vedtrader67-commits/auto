package com.locora.app

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.DateFormat
import java.util.Date

private val ActivityPanel = Color(0xFF191F2B)
private val ActivityPink = Color(0xFFFF78B5)
private val ActivityMuted = Color(0xFFADB6C7)

@Composable
fun ActivityScreen(onUsageSettings: () -> Unit, pairId: String) {
    val context = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    var share by remember {
        mutableStateOf(context.getSharedPreferences("locora_local", Context.MODE_PRIVATE)
            .getBoolean("share_my_activity", false))
    }
    val recent = remember(refresh) { LocalStore.recentApps(context) }
    LaunchedEffect(refresh, share, pairId) {
        if (share && pairId.isNotBlank()) CloudRepository.publishActivity(context, pairId, recent)
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("APP ACTIVITY", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Surface(color = ActivityPanel, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(15.dp)) {
                Text("Share your last 5 apps", color = Color.White, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(3.dp))
                Text("Only app names and last-used times. No messages, screenshots or keystrokes.", color = ActivityMuted, fontSize = 11.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("I consent to sharing this activity", color = Color.White, modifier = Modifier.weight(1f), fontSize = 12.sp)
                    Switch(checked = share, onCheckedChange = {
                        share = it
                        context.getSharedPreferences("locora_local", Context.MODE_PRIVATE)
                            .edit().putBoolean("share_my_activity", it).apply()
                        CloudRepository.setActivityConsent(context, pairId, it)
                    })
                }
                Button(onClick = onUsageSettings, modifier = Modifier.fillMaxWidth()) {
                    Text("Open Android Usage Access")
                }
                OutlinedButton(onClick = { refresh++ }, modifier = Modifier.fillMaxWidth()) {
                    Text("Refresh recent apps")
                }
            }
        }
        Text("RECENTLY USED", color = ActivityPink, fontWeight = FontWeight.Bold, fontSize = 11.sp)
        if (recent.isEmpty()) {
            Text("No usage data available. Grant LOCORA Usage Access in Android settings, then tap Refresh recent apps.",
                color = ActivityMuted, fontSize = 12.sp)
        } else {
            recent.forEach { app ->
                Surface(color = ActivityPanel, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Apps, null, tint = ActivityPink)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(app.label, color = Color.White, fontWeight = FontWeight.SemiBold)
                            Text(app.packageName, color = ActivityMuted, fontSize = 9.sp)
                        }
                        Text(DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                            .format(Date(app.lastUsed)), color = ActivityMuted, fontSize = 9.sp)
                    }
                }
            }
        }
        Text("Activity sharing stays off until you give consent. Remote sharing starts only after secure pairing is connected.",
            color = ActivityMuted, fontSize = 10.sp)
    }
}

@Composable
fun SettingsScreen(
    onStartLocation: () -> Unit,
    onBackgroundLocation: () -> Unit,
    onUsageSettings: () -> Unit,
    pairId: String,
    onSignOut: () -> Unit
) {
    val context = LocalContext.current
    var refreshBattery by remember { mutableIntStateOf(0) }
    val battery = remember(refreshBattery) { LocalStore.battery(context) }
    var notifierUrl by remember { mutableStateOf(CloudRepository.pushServerUrl(context)) }
    var notifierStatus by remember { mutableStateOf("") }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("PRIVACY & SETTINGS", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Surface(color = ActivityPanel, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.BatteryFull, null, tint = ActivityPink, modifier = Modifier.size(25.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("This phone's battery", color = Color.White, fontWeight = FontWeight.SemiBold)
                    Text(if (battery.percent >= 0) battery.percent.toString() + "% · " +
                        (if (battery.charging) "Charging" else "Not charging") else "Unavailable",
                        color = ActivityMuted, fontSize = 11.sp)
                }
                IconButton(onClick = { refreshBattery++ }) { Icon(Icons.Default.Refresh, "Refresh battery", tint = ActivityPink) }
            }
        }
        InfoCard("Location collection", "Start visible background GPS collection. For better background reliability, also allow location all the time in Android App Info.")
        OutlinedButton(onClick = onStartLocation, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.GpsFixed, null)
            Spacer(Modifier.width(6.dp))
            Text("Start location collection")
        }
        OutlinedButton(onClick = onBackgroundLocation, modifier = Modifier.fillMaxWidth()) {
            Text("Request background location access")
        }
        InfoCard("Usage Access", "Grant Android Usage Access before recent-app activity can be read.")
        OutlinedButton(onClick = onUsageSettings, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Apps, null)
            Spacer(Modifier.width(6.dp))
            Text("Open Usage Access settings")
        }
        InfoCard("Chat lock", "A four-digit PIN protects chat on this device. The PIN is stored as a salted PBKDF2 hash.")
        Text("MISSING YOU PUSH SERVICE", color = ActivityPink, fontWeight = FontWeight.Bold, fontSize = 11.sp)
        Surface(color = ActivityPanel, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text("Notification server URL", color = Color.White, fontWeight = FontWeight.SemiBold)
                Text("Deploy the included Render service, then paste its HTTPS URL here. The server sends offline alerts securely without putting admin credentials in the APK.", color = ActivityMuted, fontSize = 10.sp)
                OutlinedTextField(
                    value = notifierUrl,
                    onValueChange = { notifierUrl = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("https://your-locora-service.onrender.com") }
                )
                OutlinedButton(
                    onClick = { notifierStatus = CloudRepository.savePushServerUrl(context, notifierUrl) ?: "Notification server URL saved." },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Save notification server") }
                if (notifierStatus.isNotBlank()) Text(notifierStatus, color = if (notifierStatus.contains("saved")) Color(0xFF67D6A2) else Color(0xFFFF9494), fontSize = 10.sp)
            }
        }
        Text("CONNECTED PRIVATE PAIR", color = ActivityPink, fontWeight = FontWeight.Bold, fontSize = 11.sp)
        Surface(color = ActivityPanel, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("Signed in as " + CloudRepository.currentEmail(), color = Color.White, fontWeight = FontWeight.SemiBold)
                Text("Pair ID: " + pairId.take(8) + "…", color = ActivityMuted, fontSize = 10.sp)
                Text("Location, presence, battery, chat and consented activity sync through your Firebase Realtime Database.", color = ActivityMuted, fontSize = 11.sp)
                OutlinedButton(onClick = onSignOut, modifier = Modifier.fillMaxWidth()) { Text("Sign out of LOCORA") }
            }
        }
        Text("CHAT BACKUP", color = ActivityPink, fontWeight = FontWeight.Bold, fontSize = 11.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { LocalStore.shareExport(context, false) }, modifier = Modifier.weight(1f)) {
                Text("Export TXT")
            }
            OutlinedButton(onClick = { LocalStore.shareExport(context, true) }, modifier = Modifier.weight(1f)) {
                Text("Export JSON")
            }
        }
        Surface(color = ActivityPanel, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("PAIR WITH YOUR PERSON", color = Color.White, fontWeight = FontWeight.Bold)
                Text(
                    "Two-phone message delivery, mutual live location, battery sharing, online status, and Missing You push alerts need a private realtime backend and secure pairing. No backend credentials are connected yet, so those remote features cannot work in this build.",
                    color = ActivityMuted, fontSize = 11.sp
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CloudOff, null, tint = ActivityPink)
                    Spacer(Modifier.width(6.dp))
                    Text("Not paired · Local-only mode", color = ActivityPink, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                }
            }
        }
        Text("LOCORA does not hide tracking. Both people control and consent to their own sharing permissions.",
            color = ActivityMuted, fontSize = 10.sp)
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun InfoCard(title: String, body: String) {
    Surface(color = ActivityPanel, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Security, null, tint = ActivityPink, modifier = Modifier.size(25.dp))
            Spacer(Modifier.width(12.dp))
            Column {
                Text(title, color = Color.White, fontWeight = FontWeight.SemiBold)
                Text(body, color = ActivityMuted, fontSize = 11.sp)
            }
        }
    }
}
