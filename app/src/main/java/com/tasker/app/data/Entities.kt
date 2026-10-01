package com.tasker.app.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import java.util.UUID

fun newId(): String = UUID.randomUUID().toString()

/** Kanban status columns. */
object Status {
    const val TODO = 0
    const val DOING = 1
    const val WAITING = 2
    const val DONE = 3
    val all = listOf(TODO, DOING, WAITING, DONE)
}

/** GTD lists. */
object Gtd {
    const val INBOX = 0
    const val NEXT = 1
    const val WAITING = 2
    const val SOMEDAY = 3
    const val REFERENCE = 4
    val all = listOf(INBOX, NEXT, WAITING, SOMEDAY, REFERENCE)
}

/**
 * Every synced entity carries [updatedAt] (last-writer-wins timestamp), a [deleted]
 * tombstone and a local-only [dirty] flag that marks it as needing an upload.
 */
interface Syncable {
    val id: String
    val updatedAt: Long
    val deleted: Boolean
    val dirty: Boolean
}

@Serializable
@Entity(tableName = "tasks", indices = [Index("projectId"), Index("date")])
data class Task(
    @PrimaryKey override val id: String = newId(),
    val title: String = "",
    val notes: String = "",
    val projectId: String? = null,
    val tags: List<String> = emptyList(),
    val done: Boolean = false,
    val completedAt: Long? = null,
    /** Scheduled (start) day as epoch day. Tasks show up on this day. */
    val date: Long? = null,
    /** Scheduled (start) time, minutes after midnight. */
    val time: Int? = null,
    /** Optional end / deadline. */
    val endDate: Long? = null,
    val endTime: Int? = null,
    /** Optional duration in minutes, an alternative to an explicit end. */
    val durationMin: Int? = null,
    /** Absolute reminder times (epoch millis). */
    val reminders: List<Long> = emptyList(),
    /** 0 none, 1 low, 2 medium, 3 high. */
    val priority: Int = 0,
    val important: Boolean = false,
    val urgent: Boolean = false,
    val status: Int = Status.TODO,
    val gtd: Int = Gtd.INBOX,
    val waitingFor: String = "",
    val recurrence: String? = null,
    /** Epoch day this task is one of the "top 3" priorities for. */
    val topDate: Long? = null,
    val sortOrder: Double = 0.0,
    val createdAt: Long = System.currentTimeMillis(),
    override val updatedAt: Long = System.currentTimeMillis(),
    override val deleted: Boolean = false,
    @Transient override val dirty: Boolean = true,
) : Syncable

@Serializable
@Entity(tableName = "subtasks", indices = [Index("taskId")])
data class Subtask(
    @PrimaryKey override val id: String = newId(),
    val taskId: String,
    val title: String,
    val done: Boolean = false,
    val sortOrder: Double = 0.0,
    override val updatedAt: Long = System.currentTimeMillis(),
    override val deleted: Boolean = false,
    @Transient override val dirty: Boolean = true,
) : Syncable

@Serializable
@Entity(tableName = "projects")
data class Project(
    @PrimaryKey override val id: String = newId(),
    val name: String,
    val color: Int = 0xFF5B8DEF.toInt(),
    val folderId: String? = null,
    val sortOrder: Double = 0.0,
    val archived: Boolean = false,
    override val updatedAt: Long = System.currentTimeMillis(),
    override val deleted: Boolean = false,
    @Transient override val dirty: Boolean = true,
) : Syncable

@Serializable
@Entity(tableName = "folders")
data class Folder(
    @PrimaryKey override val id: String = newId(),
    val name: String,
    val sortOrder: Double = 0.0,
    override val updatedAt: Long = System.currentTimeMillis(),
    override val deleted: Boolean = false,
    @Transient override val dirty: Boolean = true,
) : Syncable

@Serializable
@Entity(tableName = "tags")
data class Tag(
    @PrimaryKey override val id: String = newId(),
    val name: String,
    val color: Int = 0xFF8E8E93.toInt(),
    override val updatedAt: Long = System.currentTimeMillis(),
    override val deleted: Boolean = false,
    @Transient override val dirty: Boolean = true,
) : Syncable

/** Full snapshot of all data, used for file sync and backups. */
@Serializable
data class Snapshot(
    val version: Int = 1,
    val exportedAt: Long = System.currentTimeMillis(),
    val tasks: List<Task> = emptyList(),
    val subtasks: List<Subtask> = emptyList(),
    val projects: List<Project> = emptyList(),
    val folders: List<Folder> = emptyList(),
    val tags: List<Tag> = emptyList(),
) {
    operator fun plus(o: Snapshot) = Snapshot(
        version, exportedAt,
        tasks + o.tasks, subtasks + o.subtasks, projects + o.projects, folders + o.folders, tags + o.tags,
    )
}
