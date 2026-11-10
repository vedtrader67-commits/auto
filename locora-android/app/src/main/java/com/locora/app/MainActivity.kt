package com.locora.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

private val Ink = Color(0xFF10121B)
private val Panel = Color(0xFF1B1F2B)
private val Pink = Color(0xFFFF6FAE)
private val Muted = Color(0xFFADB3C2)

class MainActivity : ComponentActivity() {
    private val locationPermission = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            result[Manifest.permission.ACCESS_COARSE_LOCATION] == true) startLocationService()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { LocoraApp(onStartLocation = { requestLocation() }) }
    }

    private fun requestLocation() {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= 33) permissions += Manifest.permission.POST_NOTIFICATIONS
        locationPermission.launch(permissions.toTypedArray())
    }

    private fun startLocationService() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) return
        val intent = Intent(this, LocationTrackerService::class.java)
        ContextCompat.startForegroundService(this, intent)
    }
}

@Composable
fun LocoraApp(onStartLocation: () -> Unit) {
    var tab by remember { mutableStateOf("Home") }
    MaterialTheme(colorScheme = darkColorScheme(primary = Pink, background = Ink, surface = Panel)) {
        Surface(Modifier.fillMaxSize(), color = Ink) {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("LOCORA", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Black)
                        Text("just us two  ♥", color = Pink, fontSize = 12.sp)
                    }
                    Surface(color = Panel, shape = CircleShape) {
                        Text("PRIVATE", Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                            color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
                when (tab) {
                    "Home" -> HomeScreen(onStartLocation)
                    "Chat" -> ChatScreen()
                    "Activity" -> ActivityScreen()
                    else -> SettingsScreen()
                }
                Spacer(Modifier.weight(1f))
                NavigationBar(containerColor = Panel) {
                    listOf("Home", "Chat", "Activity", "Settings").forEach { item ->
                        NavigationBarItem(
                            selected = tab == item, onClick = { tab = item },
                            icon = {
                                Icon(when (item) {
                                    "Home" -> Icons.Default.Map
                                    "Chat" -> Icons.Default.Favorite
                                    "Activity" -> Icons.Default.PhoneAndroid
                                    else -> Icons.Default.Settings
                                }, contentDescription = item)
                            },
                            label = { Text(item) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Pink, selectedTextColor = Pink,
                                indicatorColor = Color(0xFF382437),
                                unselectedIconColor = Muted, unselectedTextColor = Muted
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeScreen(onStartLocation: () -> Unit) {
    LazyColumn(Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Surface(shape = RoundedCornerShape(24.dp), color = Panel, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.LocationOn, null, tint = Pink)
                        Spacer(Modifier.width(8.dp))
                        Text("OUR LIVE MAP", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                    Spacer(Modifier.height(12.dp))
                    Box(Modifier.fillMaxWidth().height(180.dp)
                        .background(Color(0xFF242B39), RoundedCornerShape(18.dp)),
                        contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Map, null, tint = Muted, modifier = Modifier.size(38.dp))
                            Text("Map will appear after cloud setup", color = Muted)
                            Text("GPS works offline; sharing needs internet", color = Muted, fontSize = 11.sp)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = onStartLocation,
                        colors = ButtonDefaults.buttonColors(containerColor = Pink)) {
                        Text("Enable GPS collection", color = Ink)
                    }
                    Text("New GPS points are cached on this phone. They cannot reach the other phone until internet returns.",
                        color = Muted, fontSize = 11.sp)
                }
            }
        }
        item { InfoCard("Battery sharing", "Battery percentage and charging status need paired cloud sync.", Icons.Default.BatteryFull) }
        item { InfoCard("Connection status", "Online, offline and last-seen status will use a realtime presence backend.", Icons.Default.Wifi) }
        item { InfoCard("Missing You", "Offline-only push notification will be enabled after secure pairing and push setup.", Icons.Default.Favorite) }
    }
}

@Composable
private fun ChatScreen() {
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Surface(color = Panel, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Private chat", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text("Cloud chat and secure pairing are not configured yet.", color = Muted, fontSize = 12.sp)
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.FavoriteBorder, null, tint = Pink, modifier = Modifier.size(36.dp))
                Text("Your conversation will live here", color = Muted)
                Text("Messages will be saved locally and synced after backend setup.", color = Muted, fontSize = 11.sp)
            }
        }
        var draft by remember { mutableStateOf("") }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(value = draft, onValueChange = { draft = it },
                modifier = Modifier.weight(1f), placeholder = { Text("Write a message...") }, singleLine = true)
            IconButton(onClick = { draft = "" }) { Icon(Icons.Default.Send, null, tint = Pink) }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun ActivityScreen() {
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("APP ACTIVITY", color = Color.White, fontWeight = FontWeight.Bold)
        Text("Last five opened apps will be available after each phone owner grants Android Usage Access.",
            color = Muted, fontSize = 13.sp)
        InfoCard("Mutual consent", "Each person controls whether their app activity is shared.", Icons.Default.Security)
        InfoCard("Recent apps", "Only app names and last-used times. No messages, screenshots or keystrokes.", Icons.Default.History)
    }
}

@Composable
private fun SettingsScreen() {
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("PRIVACY & SETTINGS", color = Color.White, fontWeight = FontWeight.Bold)
        InfoCard("Chat lock", "4-digit PIN, fingerprint option, auto-lock after one minute outside chat.", Icons.Default.Lock)
        InfoCard("Chat export", "TXT, JSON, PDF and ZIP export with media backup/restore.", Icons.Default.FileDownload)
        InfoCard("Permissions", "Location, notifications, background location and optional Usage Access.", Icons.Default.AdminPanelSettings)
        InfoCard("Pairing", "Private two-person pairing. No public profiles or discovery.", Icons.Default.Favorite)
    }
}

@Composable
private fun InfoCard(title: String, body: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Surface(color = Panel, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = Pink, modifier = Modifier.size(25.dp))
            Spacer(Modifier.width(13.dp))
            Column {
                Text(title, color = Color.White, fontWeight = FontWeight.SemiBold)
                Text(body, color = Muted, fontSize = 12.sp)
            }
        }
    }
}
