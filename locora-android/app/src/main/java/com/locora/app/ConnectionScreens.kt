package com.locora.app

import androidx.compose.foundation.layout.*
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

    Surface(Modifier.fillMaxSize(), color = ConnectBg) {
        Column(
            Modifier.fillMaxSize().padding(20.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Favorite, null, tint = ConnectPink, modifier = Modifier.size(32.dp))
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("Connect your two phones", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 21.sp)
                    Text("Signed in as " + CloudRepository.currentEmail(), color = ConnectMuted, fontSize = 11.sp)
                }
            }
            Spacer(Modifier.height(16.dp))
            Card(colors = CardDefaults.cardColors(containerColor = ConnectPanel), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Cloud, null, tint = ConnectPink)
                        Spacer(Modifier.width(7.dp))
                        Text("Realtime Database URL", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                    Text("Create Realtime Database in Firebase Console, then paste its exact URL here. Both phones must use the same URL.", color = ConnectMuted, fontSize = 11.sp)
                    OutlinedTextField(value = url, onValueChange = { url = it; urlSaved = false }, modifier = Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("https://your-project-default-rtdb....firebasedatabase.app") })
                    Button(
                        onClick = {
                            busy = true
                            error = ""
                            CloudRepository.saveDatabaseUrl(context, url) { message ->
                                busy = false
                                if (message == null) { urlSaved = true; status = "Database connected." }
                                else error = message
                            }
                        },
                        enabled = !busy && url.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(if (busy) "Checking..." else "Save and check database") }
                    if (urlSaved) Text(status.ifBlank { "Database URL saved on this phone." }, color = Color(0xFF67D6A2), fontSize = 11.sp)
                }
            }
            Spacer(Modifier.height(12.dp))
            Card(colors = CardDefaults.cardColors(containerColor = ConnectPanel), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text("Create a private pair", color = Color.White, fontWeight = FontWeight.Bold)
                    Text("Create an invite code and send it to the other person. It expires after 24 hours and can be used once.", color = ConnectMuted, fontSize = 11.sp)
                    Button(
                        onClick = {
                            busy = true; error = ""; invite = ""
                            CloudRepository.createPair(context) { result, message ->
                                busy = false
                                if (message != null) error = message else { invite = result.orEmpty(); status = "Invite ready. Share it with your partner." }
                            }
                        },
                        enabled = urlSaved && !busy,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Create invite code") }
                    if (invite.isNotBlank()) {
                        Surface(color = Color(0xFF30253B), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("ONE-TIME INVITE", color = ConnectMuted, fontSize = 10.sp)
                                Text(invite, color = ConnectPink, fontSize = 25.sp, fontWeight = FontWeight.Black, letterSpacing = 3.sp)
                            }
                        }
                        Text("Enter this code on your partner's phone. Do not post it publicly.", color = ConnectMuted, fontSize = 10.sp)
                        Button(onClick = onPaired, modifier = Modifier.fillMaxWidth()) { Text("Continue to LOCORA") }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
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
                        enabled = urlSaved && code.length == 10 && !busy,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Join private pair") }
                }
            }
            if (error.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(error, color = Color(0xFFFF9494), fontSize = 11.sp)
            }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onSignOut, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Sign out", color = ConnectMuted) }
        }
    }
}
