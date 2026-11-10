package com.locora.app

import android.content.Context
import android.util.Base64
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.ByteArrayOutputStream
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.google.android.gms.tasks.Tasks
import java.security.SecureRandom

data class CloudMessage(
    val id: String,
    val senderUid: String,
    val type: String,
    val body: String,
    val attachment: String,
    val mimeType: String,
    val createdAt: Long
)

data class CloudLocation(
    val uid: String,
    val latitude: Double,
    val longitude: Double,
    val updatedAt: Long
)

data class CloudPresence(val uid: String, val online: Boolean, val updatedAt: Long)
data class CloudBattery(val uid: String, val percent: Int, val charging: Boolean, val updatedAt: Long)

object CloudRepository {
    private const val PREFS = "locora_local"
    private const val PAIR_KEY = "cloud_pair_id"
    private const val DB_URL_KEY = "cloud_database_url"
    private val random = SecureRandom()
    @Volatile private var cachedDb: FirebaseDatabase? = null
    private val auth: FirebaseAuth get() = FirebaseAuth.getInstance()

    fun currentUid(): String? = auth.currentUser?.uid
    fun currentEmail(): String = auth.currentUser?.email.orEmpty()
    fun currentPairId(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(PAIR_KEY, "").orEmpty()

    fun databaseUrl(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(DB_URL_KEY, "").orEmpty()

    fun saveDatabaseUrl(context: Context, url: String, done: (String?) -> Unit) {
        val normalized = url.trim().trimEnd('/')
        if (!normalized.startsWith("https://") || !normalized.contains("firebasedatabase.app") &&
            !normalized.contains("firebaseio.com")) {
            done("Paste the exact Realtime Database URL shown in Firebase Console.")
            return
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(DB_URL_KEY, normalized).apply()
        synchronized(this) { cachedDb = null }
        try {
            database(context).reference.child(".info/connected").get()
                .addOnSuccessListener { done(null) }
                .addOnFailureListener { done(it.message ?: "Could not reach Realtime Database.") }
        } catch (e: Exception) {
            done(e.message ?: "Could not initialize Realtime Database.")
        }
    }

    private fun database(context: Context): FirebaseDatabase {
        cachedDb?.let { return it }
        synchronized(this) {
            cachedDb?.let { return it }
            val url = databaseUrl(context)
            val instance = if (url.isNotBlank()) FirebaseDatabase.getInstance(url) else FirebaseDatabase.getInstance()
            try { instance.setPersistenceEnabled(true) } catch (_: Exception) { }
            cachedDb = instance
            return instance
        }
    }

    private fun root(context: Context): DatabaseReference = database(context).reference

    fun signIn(email: String, password: String, done: (String?) -> Unit) {
        auth.signInWithEmailAndPassword(email.trim(), password)
            .addOnSuccessListener { done(null) }
            .addOnFailureListener { done(it.message ?: "Sign in failed.") }
    }

    fun register(email: String, password: String, done: (String?) -> Unit) {
        auth.createUserWithEmailAndPassword(email.trim(), password)
            .addOnSuccessListener { done(null) }
            .addOnFailureListener { done(it.message ?: "Account creation failed.") }
    }

    fun signOut() = auth.signOut()

    fun createPair(context: Context, done: (String?, String?) -> Unit) {
        val uid = currentUid()
        if (uid == null) { done(null, "Sign in first."); return }
        val pairId = root(context).child("pairs").push().key
        if (pairId.isNullOrBlank()) { done(null, "Could not create a pair ID."); return }
        val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val code = buildString { repeat(10) { append(alphabet[random.nextInt(alphabet.length)]) } }
        val now = System.currentTimeMillis()
        val meta = mapOf(
            "createdBy" to uid,
            "createdAt" to now,
            "inviteCode" to code,
            "membersLimit" to 2
        )
        val pairRef = root(context).child("pairs").child(pairId)
        pairRef.child("meta").setValue(meta)
            .addOnSuccessListener {
                pairRef.child("members").child(uid).setValue(
                    mapOf("uid" to uid, "email" to currentEmail(), "role" to "owner", "joinedAt" to now)
                ).addOnSuccessListener {
                    root(context).child("pairInvites").child(code).setValue(
                        mapOf(
                            "pairId" to pairId,
                            "createdBy" to uid,
                            "createdAt" to now,
                            "expiresAt" to now + 24L * 60L * 60L * 1000L,
                            "usedBy" to ""
                        )
                    ).addOnSuccessListener {
                        savePair(context, pairId)
                        done(code, null)
                    }.addOnFailureListener { done(null, it.message ?: "Could not create invite.") }
                }.addOnFailureListener { done(null, it.message ?: "Could not create your pair membership.") }
            }.addOnFailureListener { done(null, it.message ?: "Could not create pair.") }
    }

    fun joinPair(context: Context, rawCode: String, done: (String?) -> Unit) {
        val uid = currentUid()
        if (uid == null) { done("Sign in first."); return }
        val code = rawCode.uppercase().filter { it.isLetterOrDigit() }
        if (code.length != 10) { done("Invite code must contain 10 characters."); return }
        val inviteRef = root(context).child("pairInvites").child(code)
        inviteRef.get().addOnSuccessListener { snap ->
            if (!snap.exists()) { done("Invite not found. Check the code."); return@addOnSuccessListener }
            val pairId = snap.child("pairId").getValue(String::class.java).orEmpty()
            val createdBy = snap.child("createdBy").getValue(String::class.java).orEmpty()
            val expiry = snap.child("expiresAt").getValue(Long::class.java) ?: 0L
            val usedBy = snap.child("usedBy").getValue(String::class.java).orEmpty()
            if (pairId.isBlank() || createdBy == uid || expiry < System.currentTimeMillis() || usedBy.isNotBlank()) {
                done("This invite is expired or already used.")
                return@addOnSuccessListener
            }
            inviteRef.runTransaction(object : Transaction.Handler {
                override fun doTransaction(currentData: MutableData): Transaction.Result {
                    val used = currentData.child("usedBy").getValue(String::class.java).orEmpty()
                    val expiryNow = currentData.child("expiresAt").getValue(Long::class.java) ?: 0L
                    if (!currentData.exists() || used.isNotBlank() || expiryNow < System.currentTimeMillis()) {
                        return Transaction.abort()
                    }
                    currentData.child("usedBy").value = uid
                    return Transaction.success(currentData)
                }
                override fun onComplete(error: DatabaseError?, committed: Boolean, snapshot: DataSnapshot?) {
                    if (error != null || !committed) {
                        done(error?.message ?: "Invite was already claimed.")
                        return
                    }
                    root(context).child("pairs").child(pairId).child("members").child(uid)
                        .setValue(
                            mapOf(
                                "uid" to uid,
                                "email" to currentEmail(),
                                "role" to "partner",
                                "joinedAt" to System.currentTimeMillis(),
                                "inviteCode" to code
                            )
                        ).addOnSuccessListener {
                            savePair(context, pairId)
                            done(null)
                        }.addOnFailureListener { done(it.message ?: "Could not join this pair.") }
                }
            })
        }.addOnFailureListener { done(it.message ?: "Could not check invite.") }
    }

    private fun savePair(context: Context, pairId: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(PAIR_KEY, pairId).apply()
    }

    fun leavePair(context: Context, done: () -> Unit = {}) {
        val pairId = currentPairId(context)
        val uid = currentUid()
        if (pairId.isBlank() || uid == null) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(PAIR_KEY).apply()
            done()
            return
        }
        val pair = root(context).child("pairs").child(pairId)
        val userNodes = listOf("locations", "presence", "battery", "activity", "activityConsent", "locationConsent", "typing")
        val tasks = userNodes.map { pair.child(it).child(uid).removeValue() }
        Tasks.whenAllComplete(tasks).addOnCompleteListener {
            pair.child("members").child(uid).removeValue().addOnCompleteListener {
                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(PAIR_KEY).apply()
                done()
            }
        }
    }

    fun setPresence(context: Context, pairId: String) {
        val uid = currentUid() ?: return
        val ref = root(context).child("pairs").child(pairId).child("presence").child(uid)
        val offline = mapOf("state" to "offline", "updatedAt" to ServerValue.TIMESTAMP)
        try {
            ref.onDisconnect().setValue(offline)
            ref.setValue(mapOf("state" to "online", "updatedAt" to ServerValue.TIMESTAMP))
        } catch (_: Exception) { }
    }

    fun publishLocation(context: Context, pairId: String, latitude: Double, longitude: Double) {
        val uid = currentUid() ?: return
        val consent = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("cloud_location_consent", false)
        if (!consent) return
        root(context).child("pairs").child(pairId).child("locations").child(uid).setValue(
            mapOf("latitude" to latitude, "longitude" to longitude, "updatedAt" to ServerValue.TIMESTAMP)
        )
    }

    fun setLocationConsent(context: Context, pairId: String, enabled: Boolean) {
        val uid = currentUid() ?: return
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean("cloud_location_consent", enabled).apply()
        root(context).child("pairs").child(pairId).child("locationConsent").child(uid).setValue(enabled)
        if (!enabled) root(context).child("pairs").child(pairId).child("locations").child(uid).removeValue()
    }


    fun listenMembers(context: Context, pairId: String, callback: (List<String>) -> Unit): ValueEventListener {
        val ref = root(context).child("pairs").child(pairId).child("members")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                callback(snapshot.children.mapNotNull { it.key })
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        ref.addValueEventListener(listener)
        return listener
    }

    fun removeMembersListener(context: Context, pairId: String, listener: ValueEventListener) {
        root(context).child("pairs").child(pairId).child("members").removeEventListener(listener)
    }

    fun listenLocations(context: Context, pairId: String, callback: (List<CloudLocation>) -> Unit): ValueEventListener {
        val ref = root(context).child("pairs").child(pairId).child("locations")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val result = snapshot.children.mapNotNull { child ->
                    val lat = child.child("latitude").getValue(Double::class.java) ?: return@mapNotNull null
                    val lon = child.child("longitude").getValue(Double::class.java) ?: return@mapNotNull null
                    CloudLocation(child.key.orEmpty(), lat, lon,
                        child.child("updatedAt").getValue(Long::class.java) ?: 0L)
                }
                callback(result)
            }
            override fun onCancelled(error: DatabaseError) { }
        }
        ref.addValueEventListener(listener)
        return listener
    }



    fun listenBattery(context: Context, pairId: String, callback: (List<CloudBattery>) -> Unit): ValueEventListener {
        val ref = root(context).child("pairs").child(pairId).child("battery")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                callback(snapshot.children.map { child ->
                    CloudBattery(
                        child.key.orEmpty(),
                        child.child("percent").getValue(Int::class.java) ?: -1,
                        child.child("charging").getValue(Boolean::class.java) ?: false,
                        child.child("updatedAt").getValue(Long::class.java) ?: 0L
                    )
                })
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        ref.addValueEventListener(listener)
        return listener
    }

    fun removeBatteryListener(context: Context, pairId: String, listener: ValueEventListener) {
        root(context).child("pairs").child(pairId).child("battery").removeEventListener(listener)
    }

    fun listenPresence(context: Context, pairId: String, callback: (List<CloudPresence>) -> Unit): ValueEventListener {
        val ref = root(context).child("pairs").child(pairId).child("presence")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                callback(snapshot.children.map { child ->
                    CloudPresence(
                        child.key.orEmpty(),
                        child.child("state").getValue(String::class.java) == "online",
                        child.child("updatedAt").getValue(Long::class.java) ?: 0L
                    )
                })
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        ref.addValueEventListener(listener)
        return listener
    }

    fun removePresenceListener(context: Context, pairId: String, listener: ValueEventListener) {
        root(context).child("pairs").child(pairId).child("presence").removeEventListener(listener)
    }

    fun removeLocationsListener(context: Context, pairId: String, listener: ValueEventListener) {
        root(context).child("pairs").child(pairId).child("locations").removeEventListener(listener)
    }

    fun encodePhoto(context: Context, uri: Uri): String? {
        return try {
            val original = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
                ?: return null
            val maxSide = 1280
            val ratio = minOf(1f, maxSide.toFloat() / maxOf(original.width, original.height))
            val scaled = if (ratio < 1f) Bitmap.createScaledBitmap(
                original, (original.width * ratio).toInt().coerceAtLeast(1),
                (original.height * ratio).toInt().coerceAtLeast(1), true
            ) else original
            var quality = 76
            var bytes: ByteArray
            do {
                val stream = ByteArrayOutputStream()
                scaled.compress(Bitmap.CompressFormat.JPEG, quality, stream)
                bytes = stream.toByteArray()
                quality -= 8
            } while (bytes.size > 650_000 && quality >= 44)
            if (bytes.size > 800_000) null else Base64.encodeToString(bytes, Base64.NO_WRAP)
        } catch (_: Exception) { null }
    }

    fun encodeAudio(fileBytes: ByteArray): String? {
        if (fileBytes.isEmpty() || fileBytes.size > 700_000) return null
        return Base64.encodeToString(fileBytes, Base64.NO_WRAP)
    }

    fun updateBattery(context: Context, pairId: String, percent: Int, charging: Boolean) {
        val uid = currentUid() ?: return
        root(context).child("pairs").child(pairId).child("battery").child(uid).setValue(
            mapOf("percent" to percent, "charging" to charging, "updatedAt" to ServerValue.TIMESTAMP)
        )
    }

    fun listenMessages(context: Context, pairId: String, callback: (List<CloudMessage>) -> Unit): ValueEventListener {
        val ref = root(context).child("pairs").child(pairId).child("messages").orderByChild("createdAt").limitToLast(300)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = snapshot.children.mapNotNull { item ->
                    val sender = item.child("senderUid").getValue(String::class.java) ?: return@mapNotNull null
                    CloudMessage(
                        item.key.orEmpty(), sender,
                        item.child("type").getValue(String::class.java) ?: "text",
                        item.child("body").getValue(String::class.java).orEmpty(),
                        item.child("attachment").getValue(String::class.java).orEmpty(),
                        item.child("mimeType").getValue(String::class.java).orEmpty(),
                        item.child("createdAt").getValue(Long::class.java) ?: 0L
                    )
                }.sortedBy { it.createdAt }
                callback(list)
                val uid = currentUid()
                if (uid != null) list.filter { it.senderUid != uid }.forEach { markRead(context, pairId, it.id) }
            }
            override fun onCancelled(error: DatabaseError) { }
        }
        ref.addValueEventListener(listener)
        return listener
    }

    fun removeMessagesListener(context: Context, pairId: String, listener: ValueEventListener) {
        root(context).child("pairs").child(pairId).child("messages").orderByChild("createdAt")
            .limitToLast(300).removeEventListener(listener)
    }

    fun sendMessage(context: Context, pairId: String, type: String, body: String, attachment: String = "", mime: String = "", done: (String?) -> Unit) {
        val uid = currentUid()
        if (uid == null) { done("Sign in first."); return }
        val ref = root(context).child("pairs").child(pairId).child("messages").push()
        val data = mapOf(
            "senderUid" to uid,
            "type" to type,
            "body" to body,
            "attachment" to attachment,
            "mimeType" to mime,
            "createdAt" to ServerValue.TIMESTAMP
        )
        ref.setValue(data).addOnSuccessListener { done(null) }
            .addOnFailureListener { done(it.message ?: "Message failed to send.") }
    }

    fun markRead(context: Context, pairId: String, messageId: String) {
        val uid = currentUid() ?: return
        root(context).child("pairs").child(pairId).child("receipts").child(messageId).child(uid)
            .setValue(ServerValue.TIMESTAMP)
    }

    fun setTyping(context: Context, pairId: String, typing: Boolean) {
        val uid = currentUid() ?: return
        root(context).child("pairs").child(pairId).child("typing").child(uid).setValue(
            mapOf("typing" to typing, "updatedAt" to ServerValue.TIMESTAMP)
        )
    }

    fun setActivityConsent(context: Context, pairId: String, enabled: Boolean) {
        val uid = currentUid() ?: return
        root(context).child("pairs").child(pairId).child("activityConsent").child(uid).setValue(enabled)
    }

    fun publishActivity(context: Context, pairId: String, apps: List<RecentApp>) {
        val uid = currentUid() ?: return
        val enabled = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean("share_my_activity", false)
        if (!enabled) return
        val entries = apps.take(5).map {
            mapOf("label" to it.label, "packageName" to it.packageName, "lastUsed" to it.lastUsed)
        }
        root(context).child("pairs").child(pairId).child("activity").child(uid).setValue(entries)
    }

    fun sendMissingYou(context: Context, pairId: String, done: (String?) -> Unit) {
        val uid = currentUid()
        if (uid == null) { done("Sign in first."); return }
        val ref = root(context).child("pairs").child(pairId).child("missingYou").push()
        ref.setValue(mapOf("senderUid" to uid, "createdAt" to ServerValue.TIMESTAMP))
            .addOnSuccessListener { done(null) }
            .addOnFailureListener { done(it.message ?: "Could not send.") }
    }

    fun observeMissingYou(context: Context, pairId: String, callback: (String) -> Unit): ChildEventListener {
        val uid = currentUid().orEmpty()
        val ref = root(context).child("pairs").child(pairId).child("missingYou")
        val listener = object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                val sender = snapshot.child("senderUid").getValue(String::class.java).orEmpty()
                if (sender.isNotBlank() && sender != uid) callback(sender)
            }
            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onChildRemoved(snapshot: DataSnapshot) {}
            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onCancelled(error: DatabaseError) {}
        }
        ref.addChildEventListener(listener)
        return listener
    }
}
