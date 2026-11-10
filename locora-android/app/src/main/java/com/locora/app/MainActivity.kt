package com.locora.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

private val Ink = Color(0xFF0D1018)
private val Panel = Color(0xFF191F2B)
private val Pink = Color(0xFFFF78B5)
private val Muted = Color(0xFFADB6C7)
private val Green = Color(0xFF67D6A2)

class MainActivity : ComponentActivity() {
    private var currentPoint by mutableStateOf<Pair<Double, Double>?>(null)
    private lateinit var fused: com.google.android.gms.location.FusedLocationProviderClient

    private val locationPermission = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            result[Manifest.permission.ACCESS_COARSE_LOCATION] == true) {
            startLocationService()
            refreshLocation()
        }
    }
    private val backgroundPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        fused = LocationServices.getFusedLocationProviderClient(this)
        readCachedLocation()
        setContent {
            LocoraApp(
                location = currentPoint,
                onStartLocation = { requestLocation() },
                onRefreshLocation = { refreshLocation() },
                onBackgroundLocation = { requestBackgroundLocation() },
                onUsageSettings = { startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
            )
        }
    }

    override fun onResume() {
        super.onResume()
        if (::fused.isInitialized && hasLocationPermission()) refreshLocation()
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun requestLocation() {
        val list = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= 33) list += Manifest.permission.POST_NOTIFICATIONS
        locationPermission.launch(list.toTypedArray())
    }

    private fun requestBackgroundLocation() {
        if (Build.VERSION.SDK_INT >= 29 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            backgroundPermission.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }
    }

    private fun startLocationService() {
        if (hasLocationPermission()) {
            try { ContextCompat.startForegroundService(this, Intent(this, LocationTrackerService::class.java)) }
            catch (_: Exception) { }
        }
    }

    private fun readCachedLocation() {
        val p = getSharedPreferences("locora_local", Context.MODE_PRIVATE)
        val lat = p.getString("last_lat", null)?.toDoubleOrNull()
        val lon = p.getString("last_lon", null)?.toDoubleOrNull()
        if (lat != null && lon != null) currentPoint = lat to lon
    }

    private fun refreshLocation() {
        if (!hasLocationPermission()) return
        try {
            fused.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, CancellationTokenSource().token)
                .addOnSuccessListener { loc ->
                    if (loc != null) saveLocation(loc)
                    else fused.lastLocation.addOnSuccessListener { last -> if (last != null) saveLocation(last) }
                }
                .addOnFailureListener {
                    fused.lastLocation.addOnSuccessListener { last -> if (last != null) saveLocation(last) }
                }
        } catch (_: SecurityException) { }
    }

    private fun saveLocation(loc: Location) {
        currentPoint = loc.latitude to loc.longitude
        getSharedPreferences("locora_local", Context.MODE_PRIVATE).edit()
            .putString("last_lat", loc.latitude.toString())
            .putString("last_lon", loc.longitude.toString())
            .putLong("last_location_at", System.currentTimeMillis())
            .apply()
    }
}

@Composable
private fun LocoraApp(
    location: Pair<Double, Double>?,
    onStartLocation: () -> Unit,
    onRefreshLocation: () -> Unit,
    onBackgroundLocation: () -> Unit,
    onUsageSettings: () -> Unit
) {
    var tab by remember { mutableStateOf("Map") }
    var chatUnlocked by remember { mutableStateOf(false) }
    var unlockUntil by remember { mutableLongStateOf(0L) }

    MaterialTheme(colorScheme = darkColorScheme(
        primary = Pink, secondary = Green, background = Ink, surface = Panel,
        onPrimary = Ink, onBackground = Color.White, onSurface = Color.White
    )) {
        Surface(Modifier.fillMaxSize(), color = Ink) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("LOCORA", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Black)
                        Text("your private little world  ♥", color = Pink, fontSize = 12.sp)
                    }
                    Surface(color = Panel, shape = CircleShape) {
                        Text("2 PEOPLE ONLY", Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
                            color = Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when (tab) {
                        "Map" -> MapScreen(location, onStartLocation, onRefreshLocation)
                        "Chat" -> ChatScreen(chatUnlocked) { chatUnlocked = true }
                        "Activity" -> ActivityScreen(onUsageSettings)
                        else -> SettingsScreen(onStartLocation, onBackgroundLocation, onUsageSettings)
                    }
                }
                NavigationBar(containerColor = Panel, tonalElevation = 0.dp) {
                    listOf("Map", "Chat", "Activity", "Settings").forEach { item ->
                        NavigationBarItem(
                            selected = tab == item,
                            onClick = {
                                val now = System.currentTimeMillis()
                                if (tab == "Chat" && item != "Chat" && chatUnlocked) unlockUntil = now + 60_000L
                                if (item == "Chat" && now > unlockUntil) chatUnlocked = false
                                tab = item
                            },
                            icon = {
                                Icon(when (item) {
                                    "Map" -> Icons.Default.Map
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
private fun MapScreen(location: Pair<Double, Double>?, onStartLocation: () -> Unit, onRefresh: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 14.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("OUR MAP", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Text("Your phone's location, on a real map", color = Muted, fontSize = 11.sp)
            }
            FilledTonalButton(onClick = onRefresh) {
                Icon(Icons.Default.MyLocation, null, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(5.dp))
                Text("Locate")
            }
        }
        Spacer(Modifier.height(10.dp))
        Surface(Modifier.weight(1f).fillMaxWidth(), shape = RoundedCornerShape(22.dp), color = Panel) {
            OpenStreetMap(location)
        }
        Spacer(Modifier.height(10.dp))
        Surface(color = Panel, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.LocationOn, null, tint = Pink)
                    Spacer(Modifier.width(7.dp))
                    Column(Modifier.weight(1f)) {
                        Text(if (location == null) "Waiting for GPS fix" else "Your current location", color = Color.White, fontWeight = FontWeight.SemiBold)
                        Text(
                            if (location == null) "Tap Locate or enable location access."
                            else "Lat " + "%.5f".format(location.first) + "  ·  Lon " + "%.5f".format(location.second),
                            color = Muted, fontSize = 11.sp
                        )
                    }
                    if (location != null) Icon(Icons.Default.CheckCircle, null, tint = Green)
                }
                Button(onClick = onStartLocation, modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Pink)) {
                    Icon(Icons.Default.GpsFixed, null, tint = Ink)
                    Spacer(Modifier.width(8.dp))
                    Text("Enable location collection", color = Ink, fontWeight = FontWeight.Bold)
                }
                Text("Map tiles © OpenStreetMap. The marker is local to this phone until private pairing and cloud sync are connected.", color = Muted, fontSize = 10.sp)
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun OpenStreetMap(location: Pair<Double, Double>?) {
    val context = LocalContext.current
    val mapView = remember {
        Configuration.getInstance().load(context, android.preference.PreferenceManager.getDefaultSharedPreferences(context))
        Configuration.getInstance().userAgentValue = context.packageName
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            setBuiltInZoomControls(true)
            controller.setZoom(if (location == null) 4.5 else 15.0)
            controller.setCenter(if (location == null) GeoPoint(22.0, 79.0) else GeoPoint(location.first, location.second))
        }
    }
    DisposableEffect(mapView) {
        mapView.onResume()
        onDispose {
            mapView.onPause()
            mapView.onDetach()
        }
    }
    AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize(), update = { view ->
        if (location != null) {
            val marker = view.overlays.filterIsInstance<Marker>().firstOrNull { it.title == "You" }
            if (marker == null) {
                view.overlays.add(Marker(view).apply {
                    position = GeoPoint(location.first, location.second)
                    title = "You"
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                })
            } else marker.position = GeoPoint(location.first, location.second)

            val key = location.first.toString() + "," + location.second.toString()
            if (view.tag as? String != key) {
                view.controller.animateTo(GeoPoint(location.first, location.second))
                view.tag = key
            }
            view.invalidate()
        }
    })
}
