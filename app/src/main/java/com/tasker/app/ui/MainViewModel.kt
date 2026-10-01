package com.tasker.app.ui

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tasker.app.R
import com.tasker.app.TaskerApp
import com.tasker.app.data.AppJson
import com.tasker.app.data.Folder
import com.tasker.app.data.Gtd
import com.tasker.app.data.Project
import com.tasker.app.data.Settings
import com.tasker.app.data.Snapshot
import com.tasker.app.data.Status
import com.tasker.app.data.Subtask
import com.tasker.app.data.Tag
import com.tasker.app.data.Task
import com.tasker.app.domain.epoch
import com.tasker.app.domain.orderBetween
import com.tasker.app.domain.today
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import java.util.Locale

sealed interface Route {
    data object Today : Route
    data object Inbox : Route
    data object All : Route
    data object Daily : Route
    data object ThreeDay : Route
    data object Weekly : Route
    data object TeuxDeux : Route
    data object Calendar : Route
    data object Kanban : Route
    data object Eisenhower : Route
    data object Gtd : Route
    data object Logbook : Route
    data object Search : Route
    data object Settings : Route
    data object ThemeEditor : Route
    data object SyncSettings : Route
    data object Manage : Route
    data class ProjectRoute(val id: String) : Route
    data class TagRoute(val id: String) : Route
    data class TaskDetail(val id: String) : Route

    companion object {
        fun fromKey(key: String): Route = when (key) {
            "inbox" -> Inbox
            "daily" -> Daily
            "three" -> ThreeDay
            "weekly" -> Weekly
            "teuxdeux" -> TeuxDeux
            "calendar" -> Calendar
            "kanban" -> Kanban
            "eisenhower" -> Eisenhower
            "gtd" -> Gtd
            "all" -> All
            "logbook" -> Logbook
            "search" -> Search
            "settings" -> Settings
            "sync" -> SyncSettings
            "theme" -> ThemeEditor
            "manage" -> Manage
            else -> Today
        }
    }
}

data class AppData(
    val tasks: List<Task> = emptyList(),
    val subtasks: List<Subtask> = emptyList(),
    val projects: List<Project> = emptyList(),
    val folders: List<Folder> = emptyList(),
    val tags: List<Tag> = emptyList(),
) {
    val taskById by lazy { tasks.associateBy { it.id } }
    val projectById by lazy { projects.associateBy { it.id } }
    val tagById by lazy { tags.associateBy { it.id } }
    val subtasksByTask by lazy { subtasks.groupBy { it.taskId } }
    val open by lazy { tasks.filter { !it.done }.sortedBy { it.sortOrder } }
}

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val tasker = app as TaskerApp
    val repo = tasker.repo
    val sync = tasker.sync
    val alarms = tasker.alarms

    val settings: StateFlow<Settings?> = tasker.settings.flow.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val data: StateFlow<AppData> = combine(repo.tasks, repo.subtasks, repo.projects, repo.folders, repo.tags) { t, s, p, f, g ->
        AppData(t, s, p, f, g)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, AppData())

    val stack = mutableStateListOf<Route>()
    val current: Route get() = stack.lastOrNull() ?: Route.Today
    private var initialized = false

    var quickAddText by mutableStateOf<String?>(null)
        private set
    var quickAddDefaults by mutableStateOf(Task())
        private set
    var rescheduleTask by mutableStateOf<String?>(null)
    val messages = MutableSharedFlow<String>(extraBufferCapacity = 8)

    /** Selected day for day-based views, shared so switching views keeps context. */
    var focusDay by mutableStateOf(today().epoch())

    fun initStart(s: Settings) {
        if (initialized) return
        initialized = true
        if (stack.isEmpty()) stack.add(Route.fromKey(s.startScreen))
    }

    fun navigateRoot(route: Route) {
        stack.clear()
        stack.add(route)
    }

    fun push(route: Route) {
        if (stack.lastOrNull() != route) stack.add(route)
    }

    fun back(): Boolean {
        if (stack.size <= 1) return false
        stack.removeAt(stack.lastIndex)
        return true
    }

    fun openTask(id: String) = push(Route.TaskDetail(id))

    fun openFromNotification(id: String, reschedule: Boolean) {
        if (stack.isEmpty()) stack.add(Route.Today)
        if (reschedule) rescheduleTask = id else push(Route.TaskDetail(id))
    }

    fun showQuickAdd(initial: String, defaults: Task = Task()) {
        quickAddDefaults = defaults
        quickAddText = initial
    }

    fun hideQuickAdd() {
        quickAddText = null
    }

    /** Default fields for tasks created from the current screen. */
    fun defaultsForCurrent(): Task = when (val r = current) {
        is Route.ProjectRoute -> Task(projectId = r.id, gtd = Gtd.NEXT)
        is Route.TagRoute -> Task(tags = listOf(r.id), gtd = Gtd.NEXT)
        Route.Today -> Task(date = today().epoch(), gtd = Gtd.NEXT)
        Route.Daily, Route.ThreeDay, Route.Weekly, Route.TeuxDeux, Route.Calendar -> Task(date = focusDay, gtd = Gtd.NEXT)
        else -> Task()
    }

    fun usDates(): Boolean = Locale.getDefault().country.equals("US", ignoreCase = true)

    fun quickAdd(text: String, defaults: Task = quickAddDefaults, onDone: (Task?) -> Unit = {}) {
        viewModelScope.launch {
            val s = settings.value ?: Settings()
            val t = repo.quickAdd(text, defaults, usDates(), s.defaultReminderMin)
            onDone(t)
        }
    }

    fun toggleDone(task: Task) = viewModelScope.launch { repo.setDone(task.id, !task.done) }
    fun save(task: Task) = viewModelScope.launch { repo.saveTask(task) }
    fun update(id: String, f: (Task) -> Task) = viewModelScope.launch { repo.updateTask(id, f) }
    fun delete(id: String) = viewModelScope.launch {
        repo.deleteTask(id)
        messages.tryEmit(getApplication<Application>().getString(R.string.msg_deleted))
    }
    fun reschedule(id: String, day: Long?, time: Int? = -1) = viewModelScope.launch { repo.reschedule(id, day, time) }
    fun saveSubtask(s: Subtask) = viewModelScope.launch { repo.saveSubtask(s) }
    fun saveProject(p: Project) = viewModelScope.launch { repo.saveProject(p) }
    fun deleteProject(id: String, withTasks: Boolean) = viewModelScope.launch { repo.deleteProject(id, withTasks) }
    fun saveFolder(f: Folder) = viewModelScope.launch { repo.saveFolder(f) }
    fun deleteFolder(id: String) = viewModelScope.launch { repo.deleteFolder(id) }
    fun saveTag(t: Tag) = viewModelScope.launch { repo.saveTag(t) }
    fun deleteTag(id: String) = viewModelScope.launch { repo.deleteTag(id) }
    fun createTag(name: String, onCreated: (Tag) -> Unit) = viewModelScope.launch { onCreated(repo.ensureTag(name)) }

    fun updateSettings(f: (Settings) -> Settings) = viewModelScope.launch { tasker.settings.update(f) }

    fun rescheduleAllOverdueToToday() = viewModelScope.launch {
        val t = today().epoch()
        data.value.tasks.filter { !it.done && it.date != null && it.date < t }.forEach { repo.reschedule(it.id, t) }
    }

    fun toggleTop3(task: Task) {
        val t = today().epoch()
        if (task.topDate == t) {
            update(task.id) { it.copy(topDate = null) }
        } else {
            val count = data.value.tasks.count { it.topDate == t && !it.done && it.id != task.id }
            if (count >= 3) {
                messages.tryEmit(getApplication<Application>().getString(R.string.msg_top3_full))
            } else update(task.id) { it.copy(topDate = t, date = it.date ?: t) }
        }
    }

    /**
     * Applies a drag & drop. Target keys:
     *  day:<epoch>  untimed:<epoch>  slot:<epoch>:<minute>  status:<n>  quad:<imp>:<urg>
     *  project:<id|none>  gtd:<n>  top3:<epoch>  nodate  list:<any>  ctx:<tagId|none>
     */
    fun onDrop(taskId: String, key: String, ordered: List<String>, index: Int) {
        val d = data.value
        val task = d.taskById[taskId] ?: return
        val before = ordered.getOrNull(index - 1)?.let { d.taskById[it]?.sortOrder }
        val after = ordered.getOrNull(index)?.let { d.taskById[it]?.sortOrder }
        val order = orderBetween(before, after)
        val parts = key.split(":")
        viewModelScope.launch {
            var t = task.copy(sortOrder = order)
            when (parts[0]) {
                "day" -> t = shiftTo(t, parts[1].toLong(), keepTime = true)
                "untimed" -> t = shiftTo(t, parts[1].toLong(), keepTime = false)
                "slot" -> {
                    t = shiftTo(t, parts[1].toLong(), keepTime = true)
                    val m = parts[2].toInt()
                    val length = t.endTime?.let { e -> t.time?.let { s -> e - s } }
                    t = t.copy(time = m, endTime = if (length != null && length > 0 && t.endDate == t.date) (m + length).coerceAtMost(1439) else t.endTime)
                }
                "status" -> {
                    val st = parts[1].toInt()
                    t = t.copy(status = st, done = st == Status.DONE, completedAt = if (st == Status.DONE) System.currentTimeMillis() else null)
                    if (st == Status.DONE && task.recurrence != null) {
                        repo.saveTask(t.copy(status = task.status, done = false))
                        repo.setDone(t.id, true)
                        return@launch
                    }
                    if (st == Status.WAITING) t = t.copy(gtd = Gtd.WAITING)
                }
                "quad" -> t = t.copy(important = parts[1] == "1", urgent = parts[2] == "1")
                "project" -> t = t.copy(projectId = parts[1].takeIf { it != "none" }, date = null, time = null, endDate = null, endTime = null)
                "gtd" -> t = t.copy(gtd = parts[1].toInt())
                "top3" -> {
                    val day = parts[1].toLong()
                    val count = d.tasks.count { it.topDate == day && !it.done && it.id != t.id }
                    if (task.topDate != day && count >= 3) {
                        messages.tryEmit(getApplication<Application>().getString(R.string.msg_top3_full))
                        return@launch
                    }
                    t = t.copy(topDate = day, date = t.date ?: day)
                }
                "nodate" -> t = t.copy(date = null, time = null, endDate = null, endTime = null, reminders = emptyList())
                "ctx" -> {
                    val contexts = t.tags.filter { d.tagById[it]?.name?.startsWith("@") == true }
                    t = t.copy(tags = (t.tags - contexts.toSet()) + listOfNotNull(parts[1].takeIf { it != "none" }), gtd = Gtd.NEXT)
                }
            }
            repo.saveTask(t)
        }
    }

    private fun shiftTo(t: Task, day: Long, keepTime: Boolean): Task {
        val shift = t.date?.let { day - it } ?: 0L
        return t.copy(
            date = day,
            time = if (keepTime) t.time else null,
            endDate = t.endDate?.plus(shift),
            endTime = if (keepTime) t.endTime else null,
            reminders = t.reminders.map { it + shift * 86_400_000L },
            gtd = if (t.gtd == Gtd.INBOX) Gtd.NEXT else t.gtd,
        )
    }

    fun syncNow() = viewModelScope.launch {
        val r = sync.syncNow()
        val ctx = getApplication<Application>()
        messages.tryEmit(r.fold({ ctx.getString(R.string.msg_synced) }, { ctx.getString(R.string.msg_sync_failed, it.message ?: "") }))
    }

    fun exportTo(uri: Uri) = viewModelScope.launch {
        val ctx = getApplication<Application>()
        runCatching {
            val json = AppJson.encodeToString(repo.snapshot(includeDeleted = false))
            ctx.contentResolver.openOutputStream(uri, "wt")?.use { it.write(json.encodeToByteArray()) }
        }.onSuccess { messages.tryEmit(ctx.getString(R.string.msg_exported)) }
            .onFailure { messages.tryEmit(it.message ?: "Error") }
    }

    fun importFrom(uri: Uri) = viewModelScope.launch {
        val ctx = getApplication<Application>()
        runCatching {
            val text = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }.orEmpty()
            repo.import(AppJson.decodeFromString<Snapshot>(text))
        }.onSuccess { messages.tryEmit(ctx.getString(R.string.msg_imported, it)) }
            .onFailure { messages.tryEmit(it.message ?: "Error") }
    }

    fun firebaseAuth(email: String, password: String, create: Boolean) = viewModelScope.launch {
        val ctx = getApplication<Application>()
        sync.firebaseSignIn(email, password, create)
            .onSuccess { messages.tryEmit(ctx.getString(R.string.msg_signed_in)); sync.syncNow() }
            .onFailure { messages.tryEmit(it.message ?: "Error") }
    }

    fun firebaseSignOut() = viewModelScope.launch { sync.firebaseSignOut() }
}
