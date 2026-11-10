package com.locora.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.widget.ImageView
import androidx.fragment.app.FragmentActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import java.io.File
import java.util.concurrent.Executor
import java.text.DateFormat
import java.util.Date

private val ChatPanel = Color(0xFF191F2B)
private val ChatPink = Color(0xFFFF78B5)
private val ChatMuted = Color(0xFFADB6C7)

@Composable
fun ChatScreen(unlocked: Boolean, onUnlock: () -> Unit, pairId: String = "") {
    val context = LocalContext.current
    if (!unlocked) {
        PinGate(onUnlock)
        return
    }
    if (pairId.isNotBlank()) {
        CloudChatScreen(pairId)
        return
    }

    val messages = remember { mutableStateListOf<ChatMessage>().apply { addAll(LocalStore.messages(context)) } }
    var draft by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    var recording by remember { mutableStateOf(false) }
    var recorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var recordFile by remember { mutableStateOf<File?>(null) }

    fun beginRecording() {
        try {
            val file = File(context.cacheDir, "locora_voice_" + System.currentTimeMillis() + ".m4a")
            @Suppress("DEPRECATION")
            val mr = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(context) else MediaRecorder()
            mr.setAudioSource(MediaRecorder.AudioSource.MIC)
            mr.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            mr.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            mr.setOutputFile(file.absolutePath)
            mr.prepare()
            mr.start()
            recorder = mr
            recordFile = file
            recording = true
        } catch (_: Exception) {
            try { recorder?.release() } catch (_: Exception) { }
            recorder = null
            recording = false
        }
    }

    val audioPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) beginRecording()
    }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            try { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            catch (_: Exception) { }
            messages.add(LocalStore.addMessage(context, "Photo attached", "image", uri.toString()))
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            if (recording) try { recorder?.stop() } catch (_: Exception) { }
            try { recorder?.release() } catch (_: Exception) { }
            recorder = null
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 14.dp)) {
        Surface(color = ChatPanel, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(color = Color(0xFF40253B), shape = RoundedCornerShape(50), modifier = Modifier.size(42.dp)) {
                    Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Favorite, null, tint = ChatPink) }
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Our private chat", color = Color.White, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    Text("Saved on this phone · PIN protected", color = ChatMuted, fontSize = 10.sp)
                }
                Icon(Icons.Default.Lock, null, tint = Color(0xFF67D6A2))
            }
        }
        Spacer(Modifier.height(8.dp))
        Surface(color = Color(0xFF30251F), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
            Text(
                "Local mode: messages save here. They are not delivered to the other phone until secure pairing is connected.",
                color = Color(0xFFFFD5AE), fontSize = 10.sp, modifier = Modifier.padding(10.dp)
            )
        }
        Spacer(Modifier.height(8.dp))
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            state = listState,
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (messages.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.FavoriteBorder, null, tint = ChatPink, modifier = Modifier.size(34.dp))
                            Text("Your conversation starts here", color = ChatMuted)
                            Text("Send a message, photo or voice note.", color = ChatMuted, fontSize = 11.sp)
                        }
                    }
                }
            }
            items(messages, key = { it.id }) { message ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Surface(color = Color(0xFF38283A), shape = RoundedCornerShape(17.dp), modifier = Modifier.widthIn(max = 310.dp)) {
                        Column(Modifier.padding(horizontal = 12.dp, vertical = 9.dp)) {
                            when (message.type) {
                                "image" -> {
                                    Text("Photo", color = Color.White, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                                    AndroidView(
                                        factory = { ImageView(it).apply { adjustViewBounds = true; scaleType = ImageView.ScaleType.CENTER_CROP } },
                                        update = { view -> try { view.setImageURI(Uri.parse(message.attachment)) } catch (_: Exception) { } },
                                        modifier = Modifier.sizeIn(maxWidth = 250.dp, maxHeight = 190.dp)
                                    )
                                }
                                "voice" -> {
                                    Text("Voice note", color = Color.White, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                                    TextButton(onClick = {
                                        try {
                                            val player = MediaPlayer.create(context, Uri.fromFile(File(message.attachment)))
                                            player?.setOnCompletionListener { it.release() }
                                            player?.start()
                                        } catch (_: Exception) { }
                                    }) { Text("▶ Play voice note", color = ChatPink) }
                                }
                                else -> Text(message.body, color = Color.White)
                            }
                            Text(DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(message.createdAt)),
                                color = ChatMuted, fontSize = 9.sp, modifier = Modifier.align(Alignment.End))
                        }
                    }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { photoPicker.launch(arrayOf("image/*")) }) {
                Icon(Icons.Default.AttachFile, "Attach photo", tint = ChatPink)
            }
            IconButton(onClick = {
                if (recording) {
                    val file = recordFile
                    try { recorder?.stop() } catch (_: Exception) { }
                    try { recorder?.release() } catch (_: Exception) { }
                    recorder = null
                    recording = false
                    if (file != null && file.exists() && file.length() > 0) {
                        messages.add(LocalStore.addMessage(context, "Voice note", "voice", file.absolutePath))
                    }
                    recordFile = null
                } else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                    beginRecording()
                } else {
                    audioPermission.launch(Manifest.permission.RECORD_AUDIO)
                }
            }) {
                Icon(if (recording) Icons.Default.StopCircle else Icons.Default.Mic,
                    "Record voice note", tint = if (recording) Color.Red else ChatPink)
            }
            OutlinedTextField(
                value = draft, onValueChange = { draft = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Message...") },
                singleLine = true,
                shape = RoundedCornerShape(22.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
            )
            IconButton(onClick = {
                if (draft.isNotBlank()) {
                    messages.add(LocalStore.addMessage(context, draft.trim()))
                    draft = ""
                }
            }, enabled = draft.isNotBlank()) {
                Icon(Icons.Default.Send, "Send message", tint = if (draft.isNotBlank()) ChatPink else ChatMuted)
            }
        }
        if (recording) Text("Recording... tap stop to save your voice note.", color = ChatPink, fontSize = 10.sp)
        Spacer(Modifier.height(6.dp))
    }
}

@Composable
private fun PinGate(onUnlock: () -> Unit) {
    val context = LocalContext.current
    val hasPin = remember { LocalStore.hasPin(context) }
    var biometricEnabled by remember { mutableStateOf(LocalStore.biometricEnabled(context)) }
    var first by remember { mutableStateOf("") }
    var second by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var confirming by remember { mutableStateOf(false) }

    fun unlockWithBiometric() {
        val activity = context as? FragmentActivity
        if (activity == null) { error = "Biometric unlock is unavailable here."; return }
        val manager = BiometricManager.from(context)
        if (manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) != BiometricManager.BIOMETRIC_SUCCESS) {
            error = "Set up a fingerprint in Android Settings first."
            return
        }
        val executor: Executor = ContextCompat.getMainExecutor(context)
        val prompt = BiometricPrompt(activity, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onUnlock()
            }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                error = errString.toString()
            }
            override fun onAuthenticationFailed() {
                error = "Fingerprint not recognized. Try again or use your PIN."
            }
        })
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock LOCORA chat")
            .setSubtitle("Verify your fingerprint")
            .setNegativeButtonText("Use PIN")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .build()
        prompt.authenticate(info)
    }

    Column(
        Modifier.fillMaxSize().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.Lock, null, tint = ChatPink, modifier = Modifier.size(42.dp))
        Spacer(Modifier.height(12.dp))
        Text(if (hasPin) "Unlock our chat" else "Create your chat PIN",
            color = Color.White, fontSize = 21.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
        Text(if (hasPin) "Enter your 4-digit private PIN." else "Set a 4-digit PIN stored as a salted hash on this device.",
            color = ChatMuted, fontSize = 12.sp)
        Spacer(Modifier.height(18.dp))
        OutlinedTextField(
            value = first,
            onValueChange = { if (it.length <= 4 && it.all(Char::isDigit)) first = it },
            label = { Text(if (hasPin) "4-digit PIN" else if (confirming) "New PIN" else "Create PIN") },
            singleLine = true, visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
        )
        if (!hasPin && confirming) {
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = second,
                onValueChange = { if (it.length <= 4 && it.all(Char::isDigit)) second = it },
                label = { Text("Confirm PIN") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
            )
        }
        if (hasPin && biometricEnabled) {
            OutlinedButton(onClick = { unlockWithBiometric() }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Fingerprint, null, tint = ChatPink)
                Spacer(Modifier.width(7.dp))
                Text("Unlock with fingerprint")
            }
        }
        if (error.isNotBlank()) Text(error, color = Color(0xFFFF8A8A), fontSize = 12.sp)
        Spacer(Modifier.height(12.dp))
        Button(onClick = {
            error = ""
            if (hasPin) {
                if (first.length == 4 && LocalStore.verifyPin(context, first)) onUnlock()
                else error = "Incorrect PIN."
            } else if (!confirming) {
                if (first.length == 4) confirming = true else error = "Enter exactly 4 digits."
            } else {
                if (first.length != 4 || second.length != 4) error = "Both entries must be 4 digits."
                else if (first != second) { error = "PINs do not match."; second = "" }
                else { LocalStore.setPin(context, first); onUnlock() }
            }
        }, colors = ButtonDefaults.buttonColors(containerColor = ChatPink)) {
            Text(if (hasPin) "Unlock chat" else if (confirming) "Save PIN" else "Continue", color = Color(0xFF160F19))
        }
    }
}
