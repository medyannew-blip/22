package com.tasker.app

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tasker.app.data.Repository
import com.tasker.app.data.Snapshot
import com.tasker.app.data.Task
import com.tasker.app.data.TaskerDb
import com.tasker.app.domain.epoch
import com.tasker.app.domain.today
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class RepositoryTest {
    private fun repo() = Repository(TaskerDb.create(ApplicationProvider.getApplicationContext(), inMemory = true))

    @Test
    fun lastWriterWinsMerge() = runBlocking {
        val r = repo()
        val t = Task(title = "local")
        r.saveTask(t)
        val local = r.task(t.id)!!
        // Older remote edit loses
        assertEquals(0, r.merge(Snapshot(tasks = listOf(local.copy(title = "old", updatedAt = local.updatedAt - 10)))))
        assertEquals("local", r.task(t.id)!!.title)
        // Newer remote edit wins and is not dirty
        assertEquals(1, r.merge(Snapshot(tasks = listOf(local.copy(title = "remote", updatedAt = local.updatedAt + 10)))))
        val merged = r.task(t.id)!!
        assertEquals("remote", merged.title)
        assertFalse(merged.dirty)
        // Local edits become dirty and are cleaned after upload
        r.saveTask(merged.copy(title = "edited"))
        val dirty = r.dirty()
        assertEquals(1, dirty.tasks.size)
        r.markClean(dirty)
        assertEquals(0, r.dirty().tasks.size)
    }

    @Test
    fun recurringTaskRollsForward() = runBlocking {
        val r = repo()
        val day = today().epoch()
        val t = Task(title = "water plants", date = day, recurrence = "DAILY")
        r.saveTask(t)
        r.setDone(t.id, true)
        val after = r.task(t.id)!!
        assertFalse(after.done)
        assertEquals(day + 1, after.date)
    }

    @Test
    fun quickAddCreatesTagsAndProjects() = runBlocking {
        val r = repo()
        val t = r.quickAdd("Call mom tomorrow at 5pm #family +Home !1")!!
        assertEquals("Call mom", t.title)
        assertEquals(today().epoch() + 1, t.date)
        assertEquals(17 * 60, t.time)
        assertEquals(3, t.priority)
        assertTrue(t.important)
        assertEquals("family", r.db.tags().get(t.tags.single())!!.name)
        assertEquals("Home", r.db.projects().get(t.projectId!!)!!.name)
    }
}
