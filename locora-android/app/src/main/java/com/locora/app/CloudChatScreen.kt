package com.locora.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.util.Base64
import android.widget.ImageView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.firebase.database.ValueEventListener
import java.io.File
import java.text.DateFormat
import java.util.Date

private val CloudChatPanel = Color(0xFF191F2B)
private val CloudChatPink = Color(0xFFFF78B5)
private val CloudChatMuted = Color(0xFFADB6C7)

@Composable
fun CloudChatScreen(pairId: String) {
    val context = LocalContext.current
    val messages = remember { mutableStateListOf<CloudMessage>() }
    val listState = rememberLazyListState()
    var draft by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var recording by remember { mutableStateOf(false) }
    var recorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var recordFile by remember { mutableStateOf<File?>(null) }
    var sending by remember { mutableStateOf(false) }
    var partnerUid by remember { mutableStateOf("") }
    var partnerTyping by remember { mutableStateOf(false) }
    val myUid = CloudRepository.currentUid().orEmpty()

    DisposableEffect(pairId) {
        val listener: ValueEventListener = CloudRepository.listenMessages(context, pairId) { data ->
            messages.clear()
            messages.addAll(data)
            data.forEach { LocalStore.addSyncedMessage(context, it) }
        }
        val membersListener = CloudRepository.listenMembers(context, pairId) { ids ->
            partnerUid = ids.firstOrNull { it != myUid }.orEmpty()
        }
        onDispose {
            CloudRepository.removeMessagesListener(context, pairId, listener)
            CloudRepository.removeMembersListener(context, pairId, membersListener)
            CloudRepository.setTyping(context, pairId, false)
            try { if (recording) recorder?.stop() } catch (_: Exception) {}
            try { recorder?.release() } catch (_: Exception) {}
        }
    }

    DisposableEffect(pairId, partnerUid) {
        if (partnerUid.isBlank()) {
            partnerTyping = false
            onDispose { }
        } else {
            val typingListener = CloudRepository.listenTyping(context, pairId, partnerUid) { partnerTyping = it }
            onDispose { CloudRepository.removeTypingListener(context, pairId, partnerUid, typingListener) }
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    fun startRecording() {
        try {
            val file = File(context.cacheDir, "locora_cloud_voice_" + System.currentTimeMillis() + ".m4a")
            @Suppress("DEPRECATION")
            val mediaRecorder = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(context) else MediaRecorder()
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            mediaRecorder.setAudioEncodingBitRate(24000)
            mediaRecorder.setAudioSamplingRate(22050)
            mediaRecorder.setOutputFile(file.absolutePath)
            mediaRecorder.prepare()
            mediaRecorder.start()
            recorder = mediaRecorder
            recordFile = file
            recording = true
            error = ""
        } catch (e: Exception) {
            error = e.message ?: "Could not start recording."
            try { recorder?.release() } catch (_: Exception) {}
            recorder = null
            recording = false
        }
    }

    val audioPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startRecording() else error = "Microphone permission is needed for voice messages."
    }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            sending = true
            val encoded = CloudRepository.encodePhoto(context, uri)
            if (encoded == null) {
                sending = false
                error = "Photo is too large to sync. Choose a smaller image."
            } else {
                CloudRepository.sendMessage(context, pairId, "image", "Photo", encoded, "image/jpeg") { message ->
                    sending = false
                    error = message.orEmpty()
                }
            }
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 14.dp)) {
        Surface(color = CloudChatPanel, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(color = Color(0xFF40253B), shape = RoundedCornerShape(50), modifier = Modifier.size(42.dp)) {
                    Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Favorite, null, tint = CloudChatPink) }
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Our private chat", color = Color.White, fontWeight = FontWeight.Bold)
                    Text("Live sync · end-to-end app account access", color = CloudChatMuted, fontSize = 10.sp)
                }
            }
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
                            Icon(Icons.Default.Favorite, null, tint = CloudChatPink, modifier = Modifier.size(34.dp))
                            Text("Your conversation starts here", color = CloudChatMuted)
                            Text("Messages sync between your paired phones.", color = CloudChatMuted, fontSize = 11.sp)
                        }
                    }
                }
            }
            items(messages, key = { it.id }) { msg ->
                val mine = msg.senderUid == CloudRepository.currentUid()
                Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
                    Surface(
                        color = if (mine) Color(0xFF38283A) else Color(0xFF202C3B),
                        shape = RoundedCornerShape(17.dp),
                        modifier = Modifier.widthIn(max = 310.dp)
                    ) {
                        Column(Modifier.padding(horizontal = 12.dp, vertical = 9.dp)) {
                            when (msg.type) {
                                "image" -> {
                                    Text(if (mine) "Photo" else "Photo from your person", color = Color.White, fontWeight = FontWeight.SemiBold)
                                    CloudImage(msg.attachment)
                                }
                                "voice" -> {
                                    Text("Voice message", color = Color.White, fontWeight = FontWeight.SemiBold)
                                    TextButton(onClick = {
                                        try {
                                            val bytes = Base64.decode(msg.attachment, Base64.NO_WRAP)
                                            val file = File(context.cacheDir, "locora_play_" + msg.id + ".m4a")
                                            file.writeBytes(bytes)
                                            val player = MediaPlayer.create(context, Uri.fromFile(file))
                                            player?.setOnCompletionListener { it.release(); file.delete() }
                                            player?.start()
                                        } catch (_: Exception) { error = "Could not play this voice message." }
                                    }) { Text("▶ Play voice note", color = CloudChatPink) }
                                }
                                else -> Text(msg.body, color = Color.White)
                            }
                            Row(Modifier.align(Alignment.End), verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(msg.createdAt)),
                                    color = CloudChatMuted, fontSize = 9.sp
                                )
                                if (mine && partnerUid.isNotBlank()) {
                                    Spacer(Modifier.width(5.dp))
                                    ReadReceiptLabel(pairId, msg.id, partnerUid)
                                }
                            }
                        }
                    }
                }
            }
        }
        if (partnerTyping) Text("Your person is typing…", color = CloudChatPink, fontSize = 10.sp)
        if (error.isNotBlank()) Text(error, color = Color(0xFFFF8A8A), fontSize = 10.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { photoPicker.launch(arrayOf("image/*")) }, enabled = !sending) {
                Icon(Icons.Default.AttachFile, "Attach photo", tint = CloudChatPink)
            }
            IconButton(onClick = {
                if (recording) {
                    val file = recordFile
                    try { recorder?.stop() } catch (_: Exception) {}
                    try { recorder?.release() } catch (_: Exception) {}
                    recorder = null
                    recording = false
                    if (file != null && file.exists()) {
                        val encoded = CloudRepository.encodeAudio(file.readBytes())
                        if (encoded == null) error = "Voice note is too large. Record a shorter one."
                        else {
                            sending = true
                            CloudRepository.sendMessage(context, pairId, "voice", "Voice note", encoded, "audio/mp4") { message ->
                                sending = false
                                error = message.orEmpty()
                            }
                        }
                        file.delete()
                    }
                    recordFile = null
                } else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                    startRecording()
                } else audioPermission.launch(Manifest.permission.RECORD_AUDIO)
            }) {
                Icon(if (recording) Icons.Default.StopCircle else Icons.Default.Mic,
                    "Record voice message", tint = if (recording) Color.Red else CloudChatPink)
            }
            OutlinedTextField(
                value = draft,
                onValueChange = {
                    draft = it
                    CloudRepository.setTyping(context, pairId, it.isNotBlank())
                },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Message...") },
                singleLine = true,
                shape = RoundedCornerShape(22.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
            )
            IconButton(
                onClick = {
                    val content = draft.trim()
                    if (content.isNotBlank() && !sending) {
                        sending = true
                        CloudRepository.sendMessage(context, pairId, "text", content) { message ->
                            sending = false
                            if (message == null) {
                                draft = ""
                                CloudRepository.setTyping(context, pairId, false)
                                error = ""
                            } else error = message
                        }
                    }
                },
                enabled = draft.isNotBlank() && !sending
            ) {
                Icon(Icons.Default.Send, "Send message", tint = if (draft.isNotBlank()) CloudChatPink else CloudChatMuted)
            }
        }
        if (recording) Text("Recording voice message… tap stop to send.", color = CloudChatPink, fontSize = 10.sp)
        if (sending) Text("Syncing message…", color = CloudChatMuted, fontSize = 9.sp)
        Spacer(Modifier.height(6.dp))
    }
}


@Composable
private fun ReadReceiptLabel(pairId: String, messageId: String, partnerUid: String) {
    val context = LocalContext.current
    var read by remember(messageId, partnerUid) { mutableStateOf(false) }
    DisposableEffect(pairId, messageId, partnerUid) {
        val listener = CloudRepository.listenReadReceipt(context, pairId, messageId, partnerUid) { read = it }
        onDispose { CloudRepository.removeReadReceiptListener(context, pairId, messageId, partnerUid, listener) }
    }
    Text(if (read) "✓✓ Read" else "✓ Sent", color = if (read) CloudChatPink else CloudChatMuted, fontSize = 9.sp)
}

@Composable
private fun CloudImage(encoded: String) {
    val bitmap = remember(encoded) {
        try {
            val bytes = Base64.decode(encoded, Base64.NO_WRAP)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (_: Exception) { null }
    }
    if (bitmap != null) {
        AndroidView(
            factory = { ImageView(it).apply { adjustViewBounds = true; scaleType = ImageView.ScaleType.CENTER_CROP } },
            update = { it.setImageBitmap(bitmap) },
            modifier = Modifier.sizeIn(maxWidth = 250.dp, maxHeight = 210.dp)
        )
    } else Text("Image unavailable", color = CloudChatMuted, fontSize = 11.sp)
}
