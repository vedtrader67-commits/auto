package com.locora.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.messaging.FirebaseMessaging
import java.text.DateFormat
import java.util.Date
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
    private var signedInUser by mutableStateOf<FirebaseUser?>(null)
    private var activePairId by mutableStateOf("")
    private lateinit var fused: com.google.android.gms.location.FusedLocationProviderClient
    private val presenceHandler = Handler(Looper.getMainLooper())
    private val presenceHeartbeat = object : Runnable {
        override fun run() {
            if (activePairId.isNotBlank()) {
                CloudRepository.setPresence(this@MainActivity, activePairId)
                val battery = LocalStore.battery(this@MainActivity)
                CloudRepository.updateBattery(this@MainActivity, activePairId, battery.percent, battery.charging)
                presenceHandler.postDelayed(this, 30000L)
            }
        }
    }
    private val authListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        signedInUser = firebaseAuth.currentUser
        activePairId = if (firebaseAuth.currentUser == null) "" else CloudRepository.currentPairId(this)
    }

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
    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        fused = LocationServices.getFusedLocationProviderClient(this)
        signedInUser = FirebaseAuth.getInstance().currentUser
        activePairId = if (signedInUser == null) "" else CloudRepository.currentPairId(this)
        readCachedLocation()
        FirebaseAuth.getInstance().addAuthStateListener(authListener)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(
                primary = Pink, secondary = Green, background = Ink, surface = Panel,
                onPrimary = Ink, onBackground = Color.White, onSurface = Color.White
            )) {
                when {
                    signedInUser == null -> AccountScreen {
                        signedInUser = FirebaseAuth.getInstance().currentUser
                        activePairId = CloudRepository.currentPairId(this)
                    }
                    activePairId.isBlank() -> PairingScreen(
                        onPaired = {
                            activePairId = CloudRepository.currentPairId(this)
                            if (activePairId.isNotBlank()) {
                                CloudRepository.setPresence(this, activePairId)
                                if (hasLocationPermission()) startLocationService()
                            }
                        },
                        onSignOut = {
                            CloudRepository.leavePair(this) {
                                FirebaseAuth.getInstance().signOut()
                                signedInUser = null
                                activePairId = ""
                            }
                        }
                    )
                    else -> LocoraApp(
                        location = currentPoint,
                        pairId = activePairId,
                        onStartLocation = { requestLocation() },
                        onRefreshLocation = { refreshLocation() },
                        onBackgroundLocation = { requestBackgroundLocation() },
                        onUsageSettings = { startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) },
                        onSignOut = {
                            CloudRepository.leavePair(this) {
                                stopService(Intent(this, LocationTrackerService::class.java))
                                FirebaseAuth.getInstance().signOut()
                                signedInUser = null
                                activePairId = ""
                            }
                        }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (::fused.isInitialized && hasLocationPermission()) refreshLocation()
        presenceHandler.removeCallbacks(presenceHeartbeat)
        if (activePairId.isNotBlank()) presenceHandler.post(presenceHeartbeat)
    }

    override fun onPause() {
        presenceHandler.removeCallbacks(presenceHeartbeat)
        super.onPause()
    }

    override fun onDestroy() {
        presenceHandler.removeCallbacks(presenceHeartbeat)
        FirebaseAuth.getInstance().removeAuthStateListener(authListener)
        super.onDestroy()
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun requestLocation() {
        if (hasLocationPermission()) {
            startLocationService()
            refreshLocation()
            return
        }
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
        if (!hasLocationPermission()) return
        if (activePairId.isNotBlank()) CloudRepository.setLocationConsent(this, activePairId, true)
        try { ContextCompat.startForegroundService(this, Intent(this, LocationTrackerService::class.java)) }
        catch (_: Exception) { }
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
        if (activePairId.isNotBlank()) CloudRepository.publishLocation(this, activePairId, loc.latitude, loc.longitude)
    }
}

@Composable
private fun LocoraApp(
    location: Pair<Double, Double>?,
    pairId: String,
    onStartLocation: () -> Unit,
    onRefreshLocation: () -> Unit,
    onBackgroundLocation: () -> Unit,
    onUsageSettings: () -> Unit,
    onSignOut: () -> Unit
) {
    val context = LocalContext.current
    var tab by remember { mutableStateOf("Map") }
    var chatUnlocked by remember { mutableStateOf(false) }
    var unlockUntil by remember { mutableLongStateOf(0L) }
    var locations by remember { mutableStateOf(emptyList<CloudLocation>()) }
    var presences by remember { mutableStateOf(emptyList<CloudPresence>()) }
    var members by remember { mutableStateOf(emptyList<String>()) }
    var batteries by remember { mutableStateOf(emptyList<CloudBattery>()) }
    var missingMessage by remember { mutableStateOf("") }

    DisposableEffect(pairId) {
        CloudRepository.setPresence(context, pairId)
        FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
            CloudRepository.saveFcmToken(context, pairId, token)
        }
        val locationListener = CloudRepository.listenLocations(context, pairId) { locations = it }
        val presenceListener = CloudRepository.listenPresence(context, pairId) { presences = it }
        val memberListener = CloudRepository.listenMembers(context, pairId) { members = it }
        val batteryListener = CloudRepository.listenBattery(context, pairId) { batteries = it }
        val missingListener = CloudRepository.observeMissingYou(context, pairId) {
            missingMessage = "Your person sent a Missing You signal."
        }
        onDispose {
            CloudRepository.removeLocationsListener(context, pairId, locationListener)
            CloudRepository.removePresenceListener(context, pairId, presenceListener)
            CloudRepository.removeMembersListener(context, pairId, memberListener)
            CloudRepository.removeBatteryListener(context, pairId, batteryListener)
        }
    }

    val myUid = CloudRepository.currentUid().orEmpty()
    val partnerUid = members.firstOrNull { it != myUid }.orEmpty()
    val partnerPoint = locations.firstOrNull { it.uid == partnerUid }
    val partnerPresence = presences.firstOrNull { it.uid == partnerUid }
    val partnerLastSeen = partnerPresence?.updatedAt ?: 0L
    val partnerLocationUpdatedAt = partnerPoint?.updatedAt ?: 0L
    val partnerOnline = partnerPresence?.online == true &&
        partnerLastSeen > 0L && System.currentTimeMillis() - partnerLastSeen < 120000L
    val partnerBattery = batteries.firstOrNull { it.uid == partnerUid }

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
                Text("PAIRED · 2", Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
                    color = Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (tab) {
                "Map" -> MapScreen(
                    location, partnerPoint, partnerOnline,
                    partnerBattery?.percent ?: -1, partnerBattery?.charging ?: false,
                    partnerLastSeen,
                    partnerLocationUpdatedAt,
                    onStartLocation, onRefreshLocation,
                    onMissingYou = {
                        CloudRepository.sendMissingYou(context, pairId) { error ->
                            missingMessage = error ?: "Missing You notification accepted by Firebase."
                        }
                    },
                    missingMessage = missingMessage
                )
                "Chat" -> ChatScreen(chatUnlocked, { chatUnlocked = true }, pairId)
                "Activity" -> ActivityScreen(onUsageSettings, pairId)
                else -> SettingsScreen(onStartLocation, onBackgroundLocation, onUsageSettings, pairId, onSignOut)
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

@Composable
private fun MapScreen(
    location: Pair<Double, Double>?,
    partner: CloudLocation?,
    partnerOnline: Boolean,
    partnerBattery: Int,
    partnerCharging: Boolean,
    partnerLastSeen: Long,
    partnerLocationUpdatedAt: Long,
    onStartLocation: () -> Unit,
    onRefresh: () -> Unit,
    onMissingYou: () -> Unit,
    missingMessage: String
) {
    Column(Modifier.fillMaxSize().padding(horizontal = 14.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("OUR LIVE MAP", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Text("Private location sharing between your two phones", color = Muted, fontSize = 11.sp)
            }
            FilledTonalButton(onClick = onRefresh) {
                Icon(Icons.Default.MyLocation, null, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(5.dp))
                Text("Locate")
            }
        }
        Spacer(Modifier.height(10.dp))
        Surface(Modifier.weight(1f).fillMaxWidth(), shape = RoundedCornerShape(22.dp), color = Panel) {
            OpenStreetMap(location, partner)
        }
        Spacer(Modifier.height(10.dp))
        Surface(color = Panel, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.PersonPinCircle, null, tint = Pink)
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text("You", color = Color.White, fontWeight = FontWeight.SemiBold)
                        Text(if (location == null) "Waiting for GPS" else "Lat %.5f · Lon %.5f".format(location.first, location.second),
                            color = Muted, fontSize = 10.sp)
                    }
                    Icon(if (location == null) Icons.Default.GpsNotFixed else Icons.Default.CheckCircle, null,
                        tint = if (location == null) Muted else Green)
                }
                Divider(color = Color(0xFF303849))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Favorite, null, tint = Color(0xFF8FA9FF))
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Your person", color = Color.White, fontWeight = FontWeight.SemiBold)
                        Text(
                            when {
                                partner == null -> if (partnerOnline) "Online · waiting for location" else "Offline · last location not available"
                                else -> "Lat %.5f · Lon %.5f".format(partner.latitude, partner.longitude) +
                                    if (partnerLocationUpdatedAt > 0L) " · updated " +
                                        DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(partnerLocationUpdatedAt)) else ""
                            } + if (!partnerOnline && partnerLastSeen > 0L) " · last seen " +
                                DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(partnerLastSeen)) else "",
                            color = Muted, fontSize = 10.sp
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(if (partnerOnline) "ONLINE" else "OFFLINE", color = if (partnerOnline) Green else Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        if (partnerBattery >= 0) Text(partnerBattery.toString() + "%" + if (partnerCharging) " ⚡" else "", color = Muted, fontSize = 10.sp)
                    }
                }
                Button(
                    onClick = onStartLocation,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Pink)
                ) {
                    Icon(Icons.Default.GpsFixed, null, tint = Ink)
                    Spacer(Modifier.width(8.dp))
                    Text("Enable location collection", color = Ink, fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    onClick = onMissingYou,
                    enabled = !partnerOnline,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.FavoriteBorder, null, tint = Pink)
                    Spacer(Modifier.width(7.dp))
                    Text(if (partnerOnline) "Missing You (available when partner is offline)" else "Send Missing You")
                }
                if (missingMessage.isNotBlank()) Text(missingMessage, color = Pink, fontSize = 10.sp)
                Text("GPS is shared only after permission and the location-sharing action. A disconnected phone shows its last synced point and time.", color = Muted, fontSize = 9.sp)
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun OpenStreetMap(location: Pair<Double, Double>?, partner: CloudLocation?) {
    val context = LocalContext.current
    val mapView = remember {
        Configuration.getInstance().load(context, android.preference.PreferenceManager.getDefaultSharedPreferences(context))
        Configuration.getInstance().userAgentValue = context.packageName
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            setBuiltInZoomControls(true)
            controller.setZoom(5.0)
            controller.setCenter(if (location != null) GeoPoint(location.first, location.second)
                else if (partner != null) GeoPoint(partner.latitude, partner.longitude) else GeoPoint(22.0, 79.0))
        }
    }
    DisposableEffect(mapView) {
        mapView.onResume()
        onDispose { mapView.onPause(); mapView.onDetach() }
    }
    AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize(), update = { view ->
        val local = location
        if (local != null) {
            val marker = view.overlays.filterIsInstance<Marker>().firstOrNull { it.title == "You" }
            val point = GeoPoint(local.first, local.second)
            if (marker == null) view.overlays.add(Marker(view).apply {
                position = point
                title = "You"
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            }) else marker.position = point
        }
        if (partner != null) {
            val marker = view.overlays.filterIsInstance<Marker>().firstOrNull { it.title == "Your person" }
            val point = GeoPoint(partner.latitude, partner.longitude)
            if (marker == null) view.overlays.add(Marker(view).apply {
                position = point
                title = "Your person"
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            }) else marker.position = point
        }
        val localKey = local?.let { it.first.toString() + "," + it.second } ?: "none"
        val partnerKey = partner?.let { it.latitude.toString() + "," + it.longitude } ?: "none"
        val key = localKey + "|" + partnerKey
        if (view.tag as? String != key) {
            val focus = if (local != null) GeoPoint(local.first, local.second)
                else if (partner != null) GeoPoint(partner.latitude, partner.longitude) else GeoPoint(22.0, 79.0)
            view.controller.animateTo(focus)
            view.tag = key
        }
        view.invalidate()
    })
}
