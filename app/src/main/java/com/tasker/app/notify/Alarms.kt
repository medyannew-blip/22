package com.tasker.app.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.tasker.app.TaskerApp
import com.tasker.app.data.Task
import com.tasker.app.domain.dueMillis
import com.tasker.app.domain.epoch
import com.tasker.app.domain.isOverdue
import com.tasker.app.domain.millisOf
import com.tasker.app.domain.today
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalTime

/**
 * Schedules exact alarms for reminders, "nearly due" alerts, overdue prompts and the
 * daily summary. All alarms are recomputed from the database whenever data changes.
 */
class AlarmScheduler(private val context: Context) {
    private val am = context.getSystemService(AlarmManager::class.java)
    private val prefs = context.getSharedPreferences("alarms", Context.MODE_PRIVATE)
    private val mutex = Mutex()

    private data class Event(val key: String, val at: Long, val taskId: String?, val kind: Int)

    fun canExact(): Boolean = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()

    suspend fun rescheduleAll() = mutex.withLock {
        val app = TaskerApp.get(context)
        val settings = app.settings.get()
        val now = System.currentTimeMillis()
        val tasks = app.repo.db.tasks().all().filter { !it.deleted && !it.done }
        val events = mutableListOf<Event>()
        for (t in tasks) {
            t.reminders.filter { it > now }.forEach { events += Event("${t.id}|0|$it", it, t.id, KIND_REMINDER) }
            val due = t.dueMillis() ?: continue
            val timed = t.time != null || t.endTime != null
            if (!timed) continue
            if (settings.nearlyDueAlerts) {
                val at = due - settings.nearlyDueLeadMin * 60_000L
                if (at > now) events += Event("${t.id}|1", at, t.id, KIND_NEARLY_DUE)
            }
            if (settings.overdueAlerts && due > now) events += Event("${t.id}|2", due + 60_000L, t.id, KIND_OVERDUE)
        }
        if (settings.morningSummary) {
            var at = millisOf(today().epoch(), settings.summaryHour * 60)
            if (at <= now) at = millisOf(today().epoch() + 1, settings.summaryHour * 60)
            events += Event("summary", at, null, KIND_SUMMARY)
        }
        val chosen = events.sortedBy { it.at }.take(MAX_ALARMS)
        val newKeys = chosen.map { it.key }.toSet()
        val oldKeys = prefs.getStringSet(KEY, emptySet()).orEmpty()
        (oldKeys - newKeys).forEach { cancel(it) }
        chosen.forEach { schedule(it) }
        prefs.edit().putStringSet(KEY, newKeys).apply()
    }

    private fun intentFor(key: String, taskId: String?, kind: Int) =
        Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_FIRE
            data = Uri.parse("tasker://alarm/" + Uri.encode(key))
            putExtra(AlarmReceiver.EXTRA_TASK, taskId)
            putExtra(AlarmReceiver.EXTRA_KIND, kind)
        }

    private fun schedule(e: Event) {
        val pi = PendingIntent.getBroadcast(
            context, 0, intentFor(e.key, e.taskId, e.kind),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        runCatching {
            if (canExact()) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, e.at, pi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, e.at, pi)
        }
    }

    private fun cancel(key: String) {
        val pi = PendingIntent.getBroadcast(
            context, 0, intentFor(key, null, 0),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        ) ?: return
        am.cancel(pi)
        pi.cancel()
    }

    companion object {
        const val KIND_REMINDER = 0
        const val KIND_NEARLY_DUE = 1
        const val KIND_OVERDUE = 2
        const val KIND_SUMMARY = 3
        private const val KEY = "scheduled"
        private const val MAX_ALARMS = 250
    }
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        scope.launch {
            try {
                handle(context.applicationContext, intent)
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun handle(context: Context, intent: Intent) {
        val app = TaskerApp.get(context)
        val repo = app.repo
        val taskId = intent.getStringExtra(EXTRA_TASK)
        val notif = intent.getIntExtra(EXTRA_NOTIF, 0)
        when (intent.action) {
            ACTION_FIRE -> {
                val kind = intent.getIntExtra(EXTRA_KIND, 0)
                if (kind == AlarmScheduler.KIND_SUMMARY) {
                    val all = repo.db.tasks().all().filter { !it.deleted && !it.done }
                    val t = today().epoch()
                    Notifier.summaryNotification(
                        context,
                        today = all.filter { it.date == t },
                        overdue = all.filter { (it.date != null && it.date < t) || it.isOverdue() }.filter { it.date != t },
                    )
                } else if (taskId != null) {
                    val task = repo.task(taskId)
                    if (task != null && !task.deleted && !task.done) {
                        if (kind != AlarmScheduler.KIND_OVERDUE || task.isOverdue()) Notifier.taskNotification(context, task, kind)
                    }
                }
                app.alarms.rescheduleAll()
            }
            ACTION_COMPLETE -> taskId?.let { repo.setDone(it, true) }
            ACTION_SNOOZE -> taskId?.let { id ->
                repo.updateTask(id) { it.copy(reminders = it.reminders + (System.currentTimeMillis() + 10 * 60_000L)) }
            }
            ACTION_PLUS_HOUR -> taskId?.let { id -> repo.updateTask(id) { plusHour(it) } }
            ACTION_TOMORROW -> taskId?.let { repo.reschedule(it, today().epoch() + 1) }
            ACTION_ALL_TODAY -> {
                val t = today().epoch()
                repo.db.tasks().all().filter { !it.deleted && !it.done && it.date != null && it.date < t }
                    .forEach { repo.reschedule(it.id, t) }
                Notifier.cancel(context, Notifier.SUMMARY_ID)
            }
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED, Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED, "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED",
            -> app.alarms.rescheduleAll()
        }
        if (notif != 0) Notifier.cancel(context, notif)
        if (intent.action != ACTION_FIRE) app.alarms.rescheduleAll()
    }

    private fun plusHour(t: Task): Task {
        val now = LocalTime.now()
        return if (t.date != null && t.time != null && !t.isOverdue()) {
            val m = t.time + 60
            if (m < 24 * 60) t.copy(time = m, endTime = t.endTime?.plus(60)?.coerceAtMost(24 * 60 - 1))
            else t.copy(date = t.date + 1, time = m - 24 * 60)
        } else {
            val target = now.plusHours(1)
            val day = if (target.isBefore(now)) today().epoch() + 1 else today().epoch()
            t.copy(date = day, time = target.hour * 60 + target.minute, endDate = null, endTime = null)
        }
    }

    companion object {
        const val ACTION_FIRE = "com.tasker.app.FIRE"
        const val ACTION_COMPLETE = "com.tasker.app.COMPLETE"
        const val ACTION_SNOOZE = "com.tasker.app.SNOOZE"
        const val ACTION_PLUS_HOUR = "com.tasker.app.PLUS_HOUR"
        const val ACTION_TOMORROW = "com.tasker.app.TOMORROW"
        const val ACTION_ALL_TODAY = "com.tasker.app.ALL_TODAY"
        const val EXTRA_TASK = "task"
        const val EXTRA_KIND = "kind"
        const val EXTRA_NOTIF = "notif"
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
