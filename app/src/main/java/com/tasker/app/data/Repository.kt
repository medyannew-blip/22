package com.tasker.app.data

import androidx.room.withTransaction
import com.tasker.app.domain.NlpParser
import com.tasker.app.domain.ParsedTask
import com.tasker.app.domain.Recurrence
import com.tasker.app.domain.epoch
import com.tasker.app.domain.millisOf
import com.tasker.app.domain.minutes
import com.tasker.app.domain.toDate
import com.tasker.app.domain.today
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * Single entry point for reading and writing data. Every local write bumps
 * [Syncable.updatedAt] monotonically and marks the row dirty so sync can upload it.
 */
class Repository(val db: TaskerDb) {
    val tasks: Flow<List<Task>> = db.tasks().observe()
    val subtasks: Flow<List<Subtask>> = db.subtasks().observe()
    val projects: Flow<List<Project>> = db.projects().observe()
    val folders: Flow<List<Folder>> = db.folders().observe()
    val tags: Flow<List<Tag>> = db.tags().observe()

    private val _changes = MutableSharedFlow<Unit>(extraBufferCapacity = 16)
    /** Emits after every local change (used to schedule alarms and sync). */
    val changes: SharedFlow<Unit> = _changes

    private fun stamp(previous: Long?): Long = maxOf(System.currentTimeMillis(), (previous ?: 0) + 1)

    // ---------- Tasks ----------

    suspend fun task(id: String): Task? = db.tasks().get(id)

    suspend fun saveTask(task: Task) {
        val old = db.tasks().get(task.id)
        db.tasks().upsert(listOf(task.copy(updatedAt = stamp(old?.updatedAt), dirty = true)))
        _changes.tryEmit(Unit)
    }

    suspend fun saveTasks(list: List<Task>) {
        if (list.isEmpty()) return
        db.withTransaction {
            val out = list.map { t -> t.copy(updatedAt = stamp(db.tasks().get(t.id)?.updatedAt), dirty = true) }
            db.tasks().upsert(out)
        }
        _changes.tryEmit(Unit)
    }

    suspend fun updateTask(id: String, transform: (Task) -> Task) {
        val t = db.tasks().get(id) ?: return
        saveTask(transform(t))
    }

    suspend fun deleteTask(id: String) {
        updateTask(id) { it.copy(deleted = true) }
        db.subtasks().forTask(id).forEach { saveSubtask(it.copy(deleted = true)) }
    }

    /** Toggle completion; recurring tasks roll forward to their next date instead. */
    suspend fun setDone(id: String, done: Boolean) {
        val t = db.tasks().get(id) ?: return
        if (done && t.recurrence != null) {
            val base = t.date?.toDate() ?: today()
            val next = Recurrence.next(t.recurrence, base)
            if (next != null) {
                val shift = next.epoch() - base.epoch()
                val shiftMs = shift * 86_400_000L
                saveTask(
                    t.copy(
                        date = next.epoch(),
                        endDate = t.endDate?.plus(shift),
                        reminders = t.reminders.map { it + shiftMs },
                        topDate = null,
                        status = Status.TODO,
                        done = false,
                    )
                )
                // Reset the checklist for the next occurrence
                db.subtasks().forTask(id).filter { it.done }.forEach { saveSubtask(it.copy(done = false)) }
                return
            }
        }
        saveTask(
            t.copy(
                done = done,
                completedAt = if (done) System.currentTimeMillis() else null,
                status = if (done) Status.DONE else if (t.status == Status.DONE) Status.TODO else t.status,
            )
        )
    }

    suspend fun reschedule(id: String, day: Long?, time: Int? = -1) {
        updateTask(id) { t ->
            val newTime = if (time == -1) t.time else time
            val shift = if (t.date != null && day != null) day - t.date else 0L
            val shiftMs = shift * 86_400_000L
            t.copy(
                date = day,
                time = if (day == null) null else newTime,
                endDate = if (day == null) null else t.endDate?.plus(shift),
                endTime = if (day == null) null else t.endTime,
                reminders = if (day == null) emptyList() else t.reminders.map { it + shiftMs }.filter { it > System.currentTimeMillis() },
                gtd = if (day != null && t.gtd == Gtd.INBOX) Gtd.NEXT else t.gtd,
            )
        }
    }

    /** Create a task from quick-add text using the natural language parser. */
    suspend fun quickAdd(
        text: String,
        defaults: Task = Task(),
        usDates: Boolean = true,
        defaultReminderMin: Int = 0,
    ): Task? {
        val parsed = NlpParser.parse(text, usDates = usDates)
        if (parsed.title.isBlank()) return null
        return createFromParsed(parsed, defaults, defaultReminderMin)
    }

    suspend fun createFromParsed(p: ParsedTask, defaults: Task, defaultReminderMin: Int = 0): Task {
        val tagIds = p.tags.map { ensureTag(it).id }
        val projectId = p.project?.let { ensureProject(it).id } ?: defaults.projectId
        val date = p.date?.epoch() ?: defaults.date
        val time = p.time?.minutes() ?: defaults.time
        val reminders = buildList {
            if (date != null && time != null) {
                val start = millisOf(date, time)
                p.reminderOffsetMin?.let { add(start - it * 60_000L) }
                if (p.reminderOffsetMin == null && p.reminderTime == null && defaultReminderMin > 0) add(start - defaultReminderMin * 60_000L)
            }
            if (date != null && p.reminderTime != null) add(millisOf(date, p.reminderTime.minutes()))
        }
        val minOrder = (db.tasks().all().filter { !it.deleted }.minOfOrNull { it.sortOrder } ?: 0.0) - 1.0
        val clarified = projectId != null || date != null || tagIds.isNotEmpty()
        val prio = p.priority ?: defaults.priority
        val task = defaults.copy(
            id = newId(),
            title = p.title,
            projectId = projectId,
            tags = (defaults.tags + tagIds).distinct(),
            date = date,
            time = time,
            endDate = p.endDate?.epoch() ?: defaults.endDate,
            endTime = p.endTime?.minutes() ?: defaults.endTime,
            durationMin = p.durationMin ?: defaults.durationMin,
            priority = prio,
            important = defaults.important || prio >= 3,
            urgent = defaults.urgent || (date != null && date <= today().epoch() + 1),
            recurrence = p.recurrence ?: defaults.recurrence,
            reminders = reminders,
            topDate = if (p.top3) today().epoch() else defaults.topDate,
            gtd = p.gtd ?: if (defaults.gtd != Gtd.INBOX) defaults.gtd else if (clarified) Gtd.NEXT else Gtd.INBOX,
            waitingFor = p.waitingFor ?: defaults.waitingFor,
            sortOrder = minOrder,
            createdAt = System.currentTimeMillis(),
        )
        saveTask(task)
        return task
    }

    // ---------- Subtasks ----------

    suspend fun saveSubtask(s: Subtask) {
        val old = db.subtasks().get(s.id)
        db.subtasks().upsert(listOf(s.copy(updatedAt = stamp(old?.updatedAt), dirty = true)))
        _changes.tryEmit(Unit)
    }

    // ---------- Projects / folders / tags ----------

    suspend fun saveProject(p: Project) {
        val old = db.projects().get(p.id)
        db.projects().upsert(listOf(p.copy(updatedAt = stamp(old?.updatedAt), dirty = true)))
        _changes.tryEmit(Unit)
    }

    suspend fun deleteProject(id: String, deleteTasks: Boolean) {
        val p = db.projects().get(id) ?: return
        saveProject(p.copy(deleted = true))
        val affected = db.tasks().all().filter { it.projectId == id && !it.deleted }
        saveTasks(affected.map { if (deleteTasks) it.copy(deleted = true) else it.copy(projectId = null) })
    }

    suspend fun saveFolder(f: Folder) {
        val old = db.folders().get(f.id)
        db.folders().upsert(listOf(f.copy(updatedAt = stamp(old?.updatedAt), dirty = true)))
        _changes.tryEmit(Unit)
    }

    suspend fun deleteFolder(id: String) {
        val f = db.folders().get(id) ?: return
        saveFolder(f.copy(deleted = true))
        db.projects().all().filter { it.folderId == id && !it.deleted }.forEach { saveProject(it.copy(folderId = null)) }
    }

    suspend fun saveTag(t: Tag) {
        val old = db.tags().get(t.id)
        db.tags().upsert(listOf(t.copy(updatedAt = stamp(old?.updatedAt), dirty = true)))
        _changes.tryEmit(Unit)
    }

    suspend fun deleteTag(id: String) {
        val t = db.tags().get(id) ?: return
        saveTag(t.copy(deleted = true))
        val affected = db.tasks().all().filter { id in it.tags && !it.deleted }
        saveTasks(affected.map { it.copy(tags = it.tags - id) })
    }

    suspend fun ensureTag(name: String): Tag {
        val clean = name.trim().removePrefix("#")
        db.tags().all().firstOrNull { !it.deleted && it.name.equals(clean, ignoreCase = true) }?.let { return it }
        val palette = TagPalette
        val tag = Tag(name = clean, color = palette[(clean.hashCode() and 0x7fffffff) % palette.size])
        saveTag(tag)
        return tag
    }

    suspend fun ensureProject(name: String): Project {
        val clean = name.trim()
        val all = db.projects().all().filter { !it.deleted }
        all.firstOrNull { it.name.equals(clean, ignoreCase = true) }?.let { return it }
        all.firstOrNull { it.name.startsWith(clean, ignoreCase = true) }?.let { return it }
        val p = Project(
            name = clean,
            color = ProjectPalette[(clean.hashCode() and 0x7fffffff) % ProjectPalette.size],
            sortOrder = (all.maxOfOrNull { it.sortOrder } ?: 0.0) + 1.0,
        )
        saveProject(p)
        return p
    }

    // ---------- Sync support ----------

    suspend fun snapshot(includeDeleted: Boolean = true): Snapshot = Snapshot(
        tasks = db.tasks().all().filter { includeDeleted || !it.deleted },
        subtasks = db.subtasks().all().filter { includeDeleted || !it.deleted },
        projects = db.projects().all().filter { includeDeleted || !it.deleted },
        folders = db.folders().all().filter { includeDeleted || !it.deleted },
        tags = db.tags().all().filter { includeDeleted || !it.deleted },
    )

    /**
     * Merge remote rows using last-writer-wins on [Syncable.updatedAt].
     * Returns the number of rows that changed locally.
     */
    suspend fun merge(remote: Snapshot): Int {
        var changed = 0
        db.withTransaction {
            fun <T : Syncable> pick(incoming: List<T>, local: Map<String, T>): List<T> =
                incoming.filter { r -> val l = local[r.id]; l == null || r.updatedAt > l.updatedAt }

            val t = pick(remote.tasks, db.tasks().all().associateBy { it.id }).map { it.copy(dirty = false) }
            val s = pick(remote.subtasks, db.subtasks().all().associateBy { it.id }).map { it.copy(dirty = false) }
            val p = pick(remote.projects, db.projects().all().associateBy { it.id }).map { it.copy(dirty = false) }
            val f = pick(remote.folders, db.folders().all().associateBy { it.id }).map { it.copy(dirty = false) }
            val g = pick(remote.tags, db.tags().all().associateBy { it.id }).map { it.copy(dirty = false) }
            db.tasks().upsert(t); db.subtasks().upsert(s); db.projects().upsert(p); db.folders().upsert(f); db.tags().upsert(g)
            changed = t.size + s.size + p.size + f.size + g.size
        }
        return changed
    }

    suspend fun dirty(): Snapshot = Snapshot(
        tasks = db.tasks().dirty(),
        subtasks = db.subtasks().dirty(),
        projects = db.projects().dirty(),
        folders = db.folders().dirty(),
        tags = db.tags().dirty(),
    )

    suspend fun markClean(s: Snapshot) {
        s.tasks.forEach { db.tasks().clean(it.id, it.updatedAt) }
        s.subtasks.forEach { db.subtasks().clean(it.id, it.updatedAt) }
        s.projects.forEach { db.projects().clean(it.id, it.updatedAt) }
        s.folders.forEach { db.folders().clean(it.id, it.updatedAt) }
        s.tags.forEach { db.tags().clean(it.id, it.updatedAt) }
    }

    /** Import a backup: treated like a remote merge, but the rows become dirty so they sync out. */
    suspend fun import(s: Snapshot): Int {
        val n = merge(s)
        db.withTransaction {
            db.tasks().upsert(db.tasks().all().map { it.copy(dirty = true) })
            db.projects().upsert(db.projects().all().map { it.copy(dirty = true) })
            db.subtasks().upsert(db.subtasks().all().map { it.copy(dirty = true) })
            db.folders().upsert(db.folders().all().map { it.copy(dirty = true) })
            db.tags().upsert(db.tags().all().map { it.copy(dirty = true) })
        }
        _changes.tryEmit(Unit)
        return n
    }

    fun notifyChanged() { _changes.tryEmit(Unit) }

    /** Seed a few example tasks on first run. */
    suspend fun seedIfEmpty(welcome: List<String>) {
        if (db.tasks().all().isNotEmpty() || db.projects().all().isNotEmpty()) return
        val folder = Folder(name = "Personal")
        saveFolder(folder)
        val home = Project(name = "Home", color = ProjectPalette[2], folderId = folder.id, sortOrder = 1.0)
        val work = Project(name = "Work", color = ProjectPalette[0], sortOrder = 2.0)
        saveProject(home); saveProject(work)
        welcome.forEachIndexed { i, text ->
            val parsed = NlpParser.parse(text)
            createFromParsed(parsed, Task(sortOrder = i.toDouble()))
        }
    }

    companion object {
        val ProjectPalette = listOf(
            0xFFE5484D, 0xFFF76B15, 0xFFFFC53D, 0xFF30A46C, 0xFF12A594,
            0xFF0090FF, 0xFF3E63DD, 0xFF8E4EC6, 0xFFD6409F, 0xFF8D8D8D,
        ).map { it.toInt() }
        val TagPalette = listOf(
            0xFF6E56CF, 0xFF0090FF, 0xFF12A594, 0xFF30A46C, 0xFFF76B15, 0xFFE5484D, 0xFFD6409F, 0xFF978365,
        ).map { it.toInt() }
    }
}
