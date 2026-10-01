package com.tasker.app.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

val AppJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

class Converters {
    @TypeConverter fun fromStrings(v: List<String>): String = AppJson.encodeToString(v)
    @TypeConverter fun toStrings(v: String): List<String> =
        runCatching { AppJson.decodeFromString<List<String>>(v) }.getOrDefault(emptyList())
    @TypeConverter fun fromLongs(v: List<Long>): String = AppJson.encodeToString(v)
    @TypeConverter fun toLongs(v: String): List<Long> =
        runCatching { AppJson.decodeFromString<List<Long>>(v) }.getOrDefault(emptyList())
}

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks WHERE deleted = 0") fun observe(): Flow<List<Task>>
    @Query("SELECT * FROM tasks") suspend fun all(): List<Task>
    @Query("SELECT * FROM tasks WHERE id = :id") suspend fun get(id: String): Task?
    @Query("SELECT * FROM tasks WHERE dirty = 1") suspend fun dirty(): List<Task>
    @Query("UPDATE tasks SET dirty = 0 WHERE id = :id AND updatedAt = :updatedAt") suspend fun clean(id: String, updatedAt: Long)
    @Upsert suspend fun upsert(items: List<Task>)
}

@Dao
interface SubtaskDao {
    @Query("SELECT * FROM subtasks WHERE deleted = 0 ORDER BY sortOrder") fun observe(): Flow<List<Subtask>>
    @Query("SELECT * FROM subtasks") suspend fun all(): List<Subtask>
    @Query("SELECT * FROM subtasks WHERE id = :id") suspend fun get(id: String): Subtask?
    @Query("SELECT * FROM subtasks WHERE taskId = :taskId AND deleted = 0") suspend fun forTask(taskId: String): List<Subtask>
    @Query("SELECT * FROM subtasks WHERE dirty = 1") suspend fun dirty(): List<Subtask>
    @Query("UPDATE subtasks SET dirty = 0 WHERE id = :id AND updatedAt = :updatedAt") suspend fun clean(id: String, updatedAt: Long)
    @Upsert suspend fun upsert(items: List<Subtask>)
}

@Dao
interface ProjectDao {
    @Query("SELECT * FROM projects WHERE deleted = 0 ORDER BY sortOrder") fun observe(): Flow<List<Project>>
    @Query("SELECT * FROM projects") suspend fun all(): List<Project>
    @Query("SELECT * FROM projects WHERE id = :id") suspend fun get(id: String): Project?
    @Query("SELECT * FROM projects WHERE dirty = 1") suspend fun dirty(): List<Project>
    @Query("UPDATE projects SET dirty = 0 WHERE id = :id AND updatedAt = :updatedAt") suspend fun clean(id: String, updatedAt: Long)
    @Upsert suspend fun upsert(items: List<Project>)
}

@Dao
interface FolderDao {
    @Query("SELECT * FROM folders WHERE deleted = 0 ORDER BY sortOrder") fun observe(): Flow<List<Folder>>
    @Query("SELECT * FROM folders") suspend fun all(): List<Folder>
    @Query("SELECT * FROM folders WHERE id = :id") suspend fun get(id: String): Folder?
    @Query("SELECT * FROM folders WHERE dirty = 1") suspend fun dirty(): List<Folder>
    @Query("UPDATE folders SET dirty = 0 WHERE id = :id AND updatedAt = :updatedAt") suspend fun clean(id: String, updatedAt: Long)
    @Upsert suspend fun upsert(items: List<Folder>)
}

@Dao
interface TagDao {
    @Query("SELECT * FROM tags WHERE deleted = 0 ORDER BY name COLLATE NOCASE") fun observe(): Flow<List<Tag>>
    @Query("SELECT * FROM tags") suspend fun all(): List<Tag>
    @Query("SELECT * FROM tags WHERE id = :id") suspend fun get(id: String): Tag?
    @Query("SELECT * FROM tags WHERE dirty = 1") suspend fun dirty(): List<Tag>
    @Query("UPDATE tags SET dirty = 0 WHERE id = :id AND updatedAt = :updatedAt") suspend fun clean(id: String, updatedAt: Long)
    @Upsert suspend fun upsert(items: List<Tag>)
}

@Database(
    entities = [Task::class, Subtask::class, Project::class, Folder::class, Tag::class],
    version = 1,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class TaskerDb : RoomDatabase() {
    abstract fun tasks(): TaskDao
    abstract fun subtasks(): SubtaskDao
    abstract fun projects(): ProjectDao
    abstract fun folders(): FolderDao
    abstract fun tags(): TagDao

    companion object {
        fun create(context: Context, inMemory: Boolean = false): TaskerDb =
            if (inMemory) Room.inMemoryDatabaseBuilder(context, TaskerDb::class.java).build()
            else Room.databaseBuilder(context, TaskerDb::class.java, "tasker.db").build()
    }
}
