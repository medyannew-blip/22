package com.tasker.app.sync

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.Timestamp
import java.util.Date
import com.tasker.app.TaskerApp
import com.tasker.app.data.AppJson
import com.tasker.app.data.Folder
import com.tasker.app.data.Project
import com.tasker.app.data.Repository
import com.tasker.app.data.SettingsStore
import com.tasker.app.data.Snapshot
import com.tasker.app.data.Subtask
import com.tasker.app.data.SyncMode
import com.tasker.app.data.Tag
import com.tasker.app.data.Task
import com.tasker.app.notify.Notifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import java.util.concurrent.TimeUnit

/**
 * Keeps the local database in sync with other devices.
 *
 * Two transports are supported:
 *  - FILE: a single JSON file the user picks through the system file picker. Put it in
 *    Google Drive, Dropbox, OneDrive, Nextcloud or a Syncthing folder and pick the same
 *    file on every device.
 *  - FIREBASE: realtime sync through the user's own Firebase project (Firestore + email login).
 *
 * Both use per-row last-writer-wins merging with tombstones, so offline edits on
 * several devices merge cleanly.
 */
class SyncManager(
    private val context: Context,
    private val repo: Repository,
    private val settings: SettingsStore,
    private val scope: CoroutineScope,
) {
    private val mutex = Mutex()
    private val _state = MutableStateFlow(SyncState())
    val state: StateFlow<SyncState> = _state
    private var debounceJob: Job? = null
    private var listeners: List<ListenerRegistration> = emptyList()
    private var listeningUid: String? = null

    data class SyncState(val running: Boolean = false, val message: String = "")

    /** Called after local edits: sync shortly after the user stops typing. */
    fun requestSync() {
        debounceJob?.cancel()
        debounceJob = scope.launch {
            delay(2500)
            syncNow()
        }
    }

    suspend fun syncNow(): Result<Int> = mutex.withLock {
        val s = settings.get()
        if (s.syncMode == SyncMode.OFF) return Result.success(0)
        _state.value = SyncState(running = true)
        val result = runCatching {
            withContext(Dispatchers.IO) {
                when (s.syncMode) {
                    SyncMode.FILE -> syncFile(Uri.parse(s.syncFileUri))
                    SyncMode.FIREBASE -> syncFirebase()
                    else -> 0
                }
            }
        }
        result.onSuccess { changed ->
            settings.update { it.copy(lastSync = System.currentTimeMillis(), lastSyncError = "") }
            if (changed > 0) afterRemoteChanges(changed)
        }.onFailure { e ->
            settings.update { it.copy(lastSyncError = e.message ?: e.javaClass.simpleName) }
        }
        _state.value = SyncState(running = false, message = result.exceptionOrNull()?.message ?: "")
        result
    }

    private suspend fun afterRemoteChanges(changed: Int) {
        TaskerApp.get(context).alarms.rescheduleAll()
        val inBackground = !ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        if (inBackground && settings.get().notifyOnSync) Notifier.syncNotification(context, changed)
    }

    // ---------------- File sync ----------------

    private suspend fun syncFile(uri: Uri): Int {
        require(uri.toString().isNotEmpty()) { "No sync file selected" }
        val cr = context.contentResolver
        val text = cr.openInputStream(uri)?.use { it.readBytes().decodeToString() }.orEmpty()
        val remote = if (text.isBlank()) Snapshot() else AppJson.decodeFromString<Snapshot>(text)
        val changed = repo.merge(remote)
        val local = repo.snapshot()
        val out = AppJson.encodeToString(local)
        val stream = runCatching { cr.openOutputStream(uri, "wt") }.getOrNull() ?: cr.openOutputStream(uri, "w")
        requireNotNull(stream) { "Cannot write sync file" }.use { it.write(out.encodeToByteArray()) }
        repo.markClean(local)
        return changed
    }

    // ---------------- Firebase ----------------

    private suspend fun firebaseApp(): FirebaseApp {
        val s = settings.get()
        require(s.firebaseApiKey.isNotBlank() && s.firebaseAppId.isNotBlank() && s.firebaseProjectId.isNotBlank()) {
            "Firebase is not configured"
        }
        FirebaseApp.getApps(context).firstOrNull { it.name == APP_NAME }?.let { existing ->
            val o = existing.options
            if (o.apiKey == s.firebaseApiKey && o.applicationId == s.firebaseAppId && o.projectId == s.firebaseProjectId) return existing
            stopListening()
            existing.delete()
        }
        val options = FirebaseOptions.Builder()
            .setApiKey(s.firebaseApiKey.trim())
            .setApplicationId(s.firebaseAppId.trim())
            .setProjectId(s.firebaseProjectId.trim())
            .build()
        return FirebaseApp.initializeApp(context, options, APP_NAME)
    }

    suspend fun firebaseSignIn(email: String, password: String, create: Boolean): Result<String> = runCatching {
        val auth = FirebaseAuth.getInstance(firebaseApp())
        val res = if (create) auth.createUserWithEmailAndPassword(email.trim(), password).await()
        else auth.signInWithEmailAndPassword(email.trim(), password).await()
        settings.update { it.copy(firebaseEmail = email.trim(), syncMode = SyncMode.FIREBASE, firebaseWatermark = 0) }
        res.user?.uid ?: error("Sign-in failed")
    }

    suspend fun firebaseSignOut() {
        runCatching {
            stopListening()
            FirebaseAuth.getInstance(firebaseApp()).signOut()
        }
        settings.update { it.copy(firebaseEmail = "", syncMode = SyncMode.OFF) }
    }

    fun firebaseUserEmail(): String? = runCatching {
        FirebaseApp.getApps(context).firstOrNull { it.name == APP_NAME }?.let { FirebaseAuth.getInstance(it).currentUser?.email }
    }.getOrNull()

    private suspend fun syncFirebase(): Int {
        val app = firebaseApp()
        val user = FirebaseAuth.getInstance(app).currentUser ?: error("Sign in to Firebase first")
        val fs = FirebaseFirestore.getInstance(app)
        val root = fs.collection("users").document(user.uid)

        // Pull everything the server stamped after our watermark
        val since = settings.get().firebaseWatermark
        var maxSeen = since
        var remote = Snapshot()
        for (col in COLLECTIONS) {
            val docs = root.collection(col).whereGreaterThan("syncedAt", Timestamp(Date(since))).get().await().documents
            docs.forEach { d -> d.getTimestamp("syncedAt")?.toDate()?.time?.let { if (it > maxSeen) maxSeen = it } }
            remote = remote.plus(decode(col, docs.mapNotNull { it.getString("data") }))
        }
        val changed = repo.merge(remote)

        // Push local dirty rows
        val dirty = repo.dirty()
        val writes = buildList {
            dirty.tasks.forEach { add(Triple("tasks", it.id, AppJson.encodeToString(it) to it.updatedAt)) }
            dirty.subtasks.forEach { add(Triple("subtasks", it.id, AppJson.encodeToString(it) to it.updatedAt)) }
            dirty.projects.forEach { add(Triple("projects", it.id, AppJson.encodeToString(it) to it.updatedAt)) }
            dirty.folders.forEach { add(Triple("folders", it.id, AppJson.encodeToString(it) to it.updatedAt)) }
            dirty.tags.forEach { add(Triple("tags", it.id, AppJson.encodeToString(it) to it.updatedAt)) }
        }
        writes.chunked(400).forEach { chunk ->
            val batch = fs.batch()
            chunk.forEach { (col, id, payload) ->
                batch.set(
                    root.collection(col).document(id),
                    mapOf("data" to payload.first, "updatedAt" to payload.second, "syncedAt" to FieldValue.serverTimestamp()),
                    SetOptions.merge(),
                )
            }
            batch.commit().await()
        }
        repo.markClean(dirty)
        settings.update { it.copy(firebaseWatermark = maxSeen) }
        startListening(root.id, root, maxSeen)
        return changed
    }

    private fun startListening(uid: String, root: DocumentReference, since: Long) {
        if (listeningUid == uid) return
        stopListening()
        listeningUid = uid
        listeners = COLLECTIONS.map { col ->
            root.collection(col).whereGreaterThan("syncedAt", Timestamp(Date(since))).addSnapshotListener { snap, err ->
                if (err != null || snap == null || snap.metadata.hasPendingWrites()) return@addSnapshotListener
                val docs = snap.documentChanges.mapNotNull { it.document.getString("data") }
                if (docs.isEmpty()) return@addSnapshotListener
                scope.launch(Dispatchers.IO) {
                    val incoming = decode(col, docs)
                    val changed = mutex.withLock { repo.merge(incoming) }
                    if (changed > 0) afterRemoteChanges(changed)
                }
            }
        }
    }

    fun stopListening() {
        listeners.forEach { it.remove() }
        listeners = emptyList()
        listeningUid = null
    }

    private fun decode(col: String, docs: List<String>): Snapshot = when (col) {
        "tasks" -> Snapshot(tasks = docs.mapNotNull { runCatching { AppJson.decodeFromString<Task>(it) }.getOrNull() })
        "subtasks" -> Snapshot(subtasks = docs.mapNotNull { runCatching { AppJson.decodeFromString<Subtask>(it) }.getOrNull() })
        "projects" -> Snapshot(projects = docs.mapNotNull { runCatching { AppJson.decodeFromString<Project>(it) }.getOrNull() })
        "folders" -> Snapshot(folders = docs.mapNotNull { runCatching { AppJson.decodeFromString<Folder>(it) }.getOrNull() })
        else -> Snapshot(tags = docs.mapNotNull { runCatching { AppJson.decodeFromString<Tag>(it) }.getOrNull() })
    }

    fun schedulePeriodic() {
        val req = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork("tasker-sync", ExistingPeriodicWorkPolicy.KEEP, req)
    }

    companion object {
        const val APP_NAME = "tasker-sync"
        val COLLECTIONS = listOf("tasks", "subtasks", "projects", "folders", "tags")
    }
}

class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = TaskerApp.get(applicationContext)
        app.sync.syncNow()
        app.alarms.rescheduleAll()
        return Result.success()
    }
}
