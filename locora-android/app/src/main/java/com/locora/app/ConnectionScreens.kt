package com.locora.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Login
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val ConnectBg = Color(0xFF0D1018)
private val ConnectPanel = Color(0xFF191F2B)
private val ConnectPink = Color(0xFFFF78B5)
private val ConnectMuted = Color(0xFFADB6C7)

@Composable
fun AccountScreen(onSignedIn: () -> Unit) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var create by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }

    Surface(Modifier.fillMaxSize(), color = ConnectBg) {
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(Icons.Default.Favorite, null, tint = ConnectPink, modifier = Modifier.size(52.dp))
            Spacer(Modifier.height(10.dp))
            Text("LOCORA", color = Color.White, fontWeight = FontWeight.Black, fontSize = 30.sp)
            Text("A private space for two.", color = ConnectPink, fontSize = 13.sp)
            Spacer(Modifier.height(22.dp))
            Card(colors = CardDefaults.cardColors(containerColor = ConnectPanel), shape = RoundedCornerShape(22.dp)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(if (create) "Create your account" else "Welcome back", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                    Text("Each person uses their own email and password. Never share your password or PIN.", color = ConnectMuted, fontSize = 11.sp)
                    OutlinedTextField(value = email, onValueChange = { email = it.trim() }, label = { Text("Email") }, singleLine = true, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
                    OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("Password (6+ characters)") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
                    if (error.isNotBlank()) Text(error, color = Color(0xFFFF9494), fontSize = 11.sp)
                    Button(
                        onClick = {
                            error = ""
                            if (!email.contains("@") || password.length < 6) {
                                error = "Enter a valid email and a password with at least 6 characters."
                            } else {
                                busy = true
                                val finish: (String?) -> Unit = { message ->
                                    busy = false
                                    if (message == null) onSignedIn() else error = message
                                }
                                if (create) CloudRepository.register(email, password, finish)
                                else CloudRepository.signIn(email, password, finish)
                            }
                        },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = ConnectPink)
                    ) {
                        Icon(Icons.Default.Login, null, tint = ConnectBg)
                        Spacer(Modifier.width(8.dp))
                        Text(if (busy) "Connecting..." else if (create) "Create account" else "Sign in", color = ConnectBg, fontWeight = FontWeight.Bold)
                    }
                    TextButton(onClick = { create = !create; error = "" }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (create) "I already have an account" else "New to LOCORA? Create an account", color = ConnectPink)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Text("Private by design · Both people control their own sharing", color = ConnectMuted, fontSize = 10.sp)
        }
    }
}

@Composable
fun PairingScreen(onPaired: () -> Unit, onSignOut: () -> Unit) {
    val context = LocalContext.current
    var url by remember { mutableStateOf(CloudRepository.databaseUrl(context)) }
    var code by remember { mutableStateOf("") }
    var invite by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    var urlSaved by remember { mutableStateOf(url.isNotBlank()) }
    var publicId by remember { mutableStateOf("") }
    var discoverable by remember { mutableStateOf(false) }
    var directory by remember { mutableStateOf(emptyList<DirectoryProfile>()) }
    var incoming by remember { mutableStateOf(emptyList<PairConnectionRequest>()) }
    var sent by remember { mutableStateOf(emptyList<PairConnectionRequest>()) }
    var searchId by remember { mutableStateOf("") }
    var discoveryMessage by remember { mutableStateOf("") }
    var requestBusyUid by remember { mutableStateOf("") }
    var acceptingUid by remember { mutableStateOf("") }
    var joiningAcceptedInvite by remember { mutableStateOf(false) }

    DisposableEffect(context) {
        CloudRepository.ensurePublicId(context) { id, message ->
            if (message == null) publicId = id.orEmpty() else discoveryMessage = message
        }
        CloudRepository.getDiscoverable(context) { discoverable = it }
        val directoryListener = CloudRepository.listenDirectory(context) { directory = it }
        val incomingListener = CloudRepository.listenIncomingRequests(context) { incoming = it }
        val sentListener = CloudRepository.listenSentRequests(context) { sent = it }
        onDispose {
            CloudRepository.removeDirectoryListener(context, directoryListener)
            CloudRepository.removeIncomingRequestsListener(context, incomingListener)
            CloudRepository.removeSentRequestsListener(context, sentListener)
        }
    }

    LaunchedEffect(sent) {
        val accepted = sent.firstOrNull { it.status == "accepted" && it.inviteCode.isNotBlank() }
        if (accepted != null && !joiningAcceptedInvite) {
            joiningAcceptedInvite = true
            discoveryMessage = "Request accepted. Connecting your accounts..."
            CloudRepository.joinPair(context, accepted.inviteCode) { message ->
                if (message == null) onPaired()
                else { discoveryMessage = message; joiningAcceptedInvite = false }
            }
        }
    }

    Surface(Modifier.fillMaxSize(), color = ConnectBg) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Favorite, null, tint = ConnectPink, modifier = Modifier.size(32.dp))
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("Connect your phones", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 21.sp)
                    Text("Signed in as " + CloudRepository.currentEmail(), color = ConnectMuted, fontSize = 11.sp)
                }
            }
            Card(colors = CardDefaults.cardColors(containerColor = ConnectPanel), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Cloud, null, tint = ConnectPink)
                        Spacer(Modifier.width(7.dp))
                        Text("Realtime Database", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                    Text("Both phones use the same Firebase Realtime Database. This setting is saved on this phone.", color = ConnectMuted, fontSize = 11.sp)
                    OutlinedTextField(value = url, onValueChange = { url = it; urlSaved = false }, modifier = Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("https://your-project-default-rtdb....firebasedatabase.app") })
                    Button(
                        onClick = {
                            busy = true; error = ""
                            CloudRepository.saveDatabaseUrl(context, url) { message ->
                                busy = false
                                if (message == null) { urlSaved = true; status = "Database connected." } else error = message
                            }
                        },
                        enabled = !busy && url.isNotBlank(), modifier = Modifier.fillMaxWidth()
                    ) { Text(if (busy) "Checking..." else "Save and check database") }
                    if (urlSaved) Text(status.ifBlank { "Database URL saved on this phone." }, color = Color(0xFF67D6A2), fontSize = 11.sp)
                }
            }
            Card(colors = CardDefaults.cardColors(containerColor = ConnectPanel), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text("Your discovery ID", color = Color.White, fontWeight = FontWeight.Bold)
                    Text(if (publicId.isBlank()) "Creating your private ID..." else publicId, color = ConnectPink, fontSize = 23.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                    Text("Only this random ID is shown. Your email and location are not listed in discovery.", color = ConnectMuted, fontSize = 10.sp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Show me in Discover", color = Color.White, fontWeight = FontWeight.SemiBold)
                            Text("Other signed-in users can see your ID and request to connect.", color = ConnectMuted, fontSize = 10.sp)
                        }
                        Switch(
                            checked = discoverable,
                            enabled = publicId.isNotBlank(),
                            onCheckedChange = { enabled ->
                                discoverable = enabled
                                CloudRepository.setDiscoverable(context, enabled) { message ->
                                    if (message != null) { discoverable = !enabled; discoveryMessage = message }
                                }
                            }
                        )
                    }
                    Text("Discoverable IDs made: " + directory.size, color = ConnectMuted, fontSize = 11.sp)
                }
            }
            Card(colors = CardDefaults.cardColors(containerColor = ConnectPanel), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text("Discover and request to connect", color = Color.White, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = searchId, onValueChange = { searchId = it.uppercase() },
                        label = { Text("Search discovery IDs") }, singleLine = true, modifier = Modifier.fillMaxWidth()
                    )
                    val visibleProfiles = directory.filter { profile ->
                        profile.uid != CloudRepository.currentUid() && profile.publicId.contains(searchId.trim(), ignoreCase = true)
                    }.take(30)
                    if (visibleProfiles.isEmpty()) {
                        Text("No discoverable IDs match yet. Someone must enable Show me in Discover on their phone.", color = ConnectMuted, fontSize = 11.sp)
                    } else {
                        visibleProfiles.forEach { profile ->
                            val existingRequest = sent.firstOrNull { it.uid == profile.uid }
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(profile.publicId, color = Color.White, fontWeight = FontWeight.SemiBold)
                                    Text(if (profile.paired) "Already connected" else "Available to request", color = ConnectMuted, fontSize = 10.sp)
                                }
                                val requestLabel = when {
                                    profile.paired -> "Unavailable"
                                    existingRequest?.status == "pending" -> "Requested"
                                    existingRequest?.status == "accepted" -> "Accepted"
                                    existingRequest?.status == "declined" -> "Declined"
                                    else -> "Request"
                                }
                                OutlinedButton(
                                    onClick = {
                                        requestBusyUid = profile.uid; discoveryMessage = ""
                                        CloudRepository.sendConnectionRequest(context, profile.uid, profile.publicId) { message ->
                                            requestBusyUid = ""
                                            discoveryMessage = message ?: "Connection request sent."
                                        }
                                    },
                                    enabled = !profile.paired && existingRequest == null && requestBusyUid.isBlank()
                                ) { Text(if (requestBusyUid == profile.uid) "Sending..." else requestLabel) }
                            }
                            Divider(color = Color(0xFF303847))
                        }
                    }
                }
            }
            if (incoming.isNotEmpty()) {
                Card(colors = CardDefaults.cardColors(containerColor = ConnectPanel), shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        Text("Incoming connection requests (" + incoming.count { it.status == "pending" } + ")", color = Color.White, fontWeight = FontWeight.Bold)
                        incoming.filter { it.status == "pending" }.forEach { request ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(request.publicId, color = ConnectPink, fontWeight = FontWeight.SemiBold)
                                    Text("Wants to connect with you", color = ConnectMuted, fontSize = 10.sp)
                                }
                                TextButton(
                                    onClick = {
                                        acceptingUid = request.uid
                                        CloudRepository.respondToConnectionRequest(context, request.uid, "declined") { message ->
                                            acceptingUid = ""
                                            discoveryMessage = message ?: "Request declined."
                                        }
                                    }, enabled = acceptingUid.isBlank()
                                ) { Text("Decline", color = ConnectMuted) }
                                Button(
                                    onClick = {
                                        acceptingUid = request.uid; discoveryMessage = ""
                                        CloudRepository.createPair(context) { inviteCode, createError ->
                                            if (createError != null || inviteCode.isNullOrBlank()) {
                                                acceptingUid = ""; discoveryMessage = createError ?: "Could not create invite."
                                            } else {
                                                CloudRepository.respondToConnectionRequest(context, request.uid, "accepted", inviteCode) { responseError ->
                                                    acceptingUid = ""
                                                    if (responseError == null) onPaired() else discoveryMessage = responseError
                                                }
                                            }
                                        }
                                    }, enabled = acceptingUid.isBlank() && urlSaved
                                ) { Text(if (acceptingUid == request.uid) "Connecting..." else "Accept") }
                            }
                        }
                    }
                }
            }
            if (sent.isNotEmpty()) {
                Text("SENT REQUESTS", color = ConnectPink, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                sent.take(20).forEach { request ->
                    Text(request.publicId + " · " + request.status, color = ConnectMuted, fontSize = 11.sp)
                }
            }
            if (discoveryMessage.isNotBlank()) Text(discoveryMessage, color = if (discoveryMessage.contains("sent") || discoveryMessage.contains("accepted")) Color(0xFF67D6A2) else Color(0xFFFF9494), fontSize = 11.sp)
            Card(colors = CardDefaults.cardColors(containerColor = ConnectPanel), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text("Create a one-time invite", color = Color.White, fontWeight = FontWeight.Bold)
                    Text("You can still connect directly with a 10-character invite code. It expires after 24 hours and can be used once.", color = ConnectMuted, fontSize = 11.sp)
                    Button(
                        onClick = {
                            busy = true; error = ""; invite = ""
                            CloudRepository.createPair(context) { result, message ->
                                busy = false
                                if (message != null) error = message else { invite = result.orEmpty(); status = "Invite ready. Share it with the other person." }
                            }
                        }, enabled = urlSaved && !busy, modifier = Modifier.fillMaxWidth()
                    ) { Text("Create invite code") }
                    if (invite.isNotBlank()) {
                        Surface(color = Color(0xFF30253B), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("ONE-TIME INVITE", color = ConnectMuted, fontSize = 10.sp)
                                Text(invite, color = ConnectPink, fontSize = 25.sp, fontWeight = FontWeight.Black, letterSpacing = 3.sp)
                            }
                        }
                        Text("Enter this code on the other phone. Do not post it publicly.", color = ConnectMuted, fontSize = 10.sp)
                        Button(onClick = onPaired, modifier = Modifier.fillMaxWidth()) { Text("Continue to LOCORA") }
                    }
                }
            }
            Card(colors = CardDefaults.cardColors(containerColor = ConnectPanel), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Key, null, tint = ConnectPink)
                        Spacer(Modifier.width(7.dp))
                        Text("Join with an invite code", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                    OutlinedTextField(value = code, onValueChange = { code = it.uppercase().filter(Char::isLetterOrDigit).take(10) }, label = { Text("10-character invite code") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Button(
                        onClick = {
                            busy = true; error = ""
                            CloudRepository.joinPair(context, code) { message ->
                                busy = false
                                if (message == null) onPaired() else error = message
                            }
                        },
                        enabled = urlSaved && code.length == 10 && !busy, modifier = Modifier.fillMaxWidth()
                    ) { Text("Join private pair") }
                }
            }
            if (error.isNotBlank()) Text(error, color = Color(0xFFFF9494), fontSize = 11.sp)
            TextButton(onClick = onSignOut, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Sign out", color = ConnectMuted) }
            Text("After both people connect, Map, Chat, Activity and Settings become available. Location and app activity remain controlled by each phone owner.", color = ConnectMuted, fontSize = 10.sp)
        }
    }
}
