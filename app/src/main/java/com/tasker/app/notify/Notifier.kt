package com.tasker.app.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.tasker.app.MainActivity
import com.tasker.app.R
import com.tasker.app.data.Task
import com.tasker.app.domain.toDate
import com.tasker.app.domain.toTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

object Notifier {
    const val CH_REMINDERS = "reminders"
    const val CH_DUE = "due"
    const val CH_OVERDUE = "overdue"
    const val CH_SUMMARY = "summary"
    const val CH_SYNC = "sync"

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannels(
            listOf(
                NotificationChannel(CH_REMINDERS, context.getString(R.string.channel_reminders), NotificationManager.IMPORTANCE_HIGH),
                NotificationChannel(CH_DUE, context.getString(R.string.channel_due), NotificationManager.IMPORTANCE_HIGH),
                NotificationChannel(CH_OVERDUE, context.getString(R.string.channel_overdue), NotificationManager.IMPORTANCE_DEFAULT),
                NotificationChannel(CH_SUMMARY, context.getString(R.string.channel_summary), NotificationManager.IMPORTANCE_DEFAULT),
                NotificationChannel(CH_SYNC, context.getString(R.string.channel_sync), NotificationManager.IMPORTANCE_LOW),
            )
        )
    }

    private fun canPost(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun notificationId(taskId: String, kind: Int): Int = (taskId.hashCode() * 31 + kind)

    private fun openIntent(context: Context, taskId: String?, reschedule: Boolean = false): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_TASK, taskId)
            putExtra(MainActivity.EXTRA_RESCHEDULE, reschedule)
        }
        return PendingIntent.getActivity(
            context, (taskId ?: "main").hashCode() + if (reschedule) 1 else 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun action(context: Context, taskId: String, action: String, notifId: Int): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            this.action = action
            putExtra(AlarmReceiver.EXTRA_TASK, taskId)
            putExtra(AlarmReceiver.EXTRA_NOTIF, notifId)
        }
        return PendingIntent.getBroadcast(
            context, (taskId + action).hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun whenText(task: Task): String {
        val d = task.date?.toDate() ?: return ""
        val date = d.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
        val t = task.time?.toTime()?.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
        return if (t != null) "$date · $t" else date
    }

    /** kind: 0 reminder, 1 nearly due, 2 overdue */
    fun taskNotification(context: Context, task: Task, kind: Int) {
        if (!canPost(context)) return
        val id = notificationId(task.id, kind)
        val (channel, title) = when (kind) {
            0 -> CH_REMINDERS to context.getString(R.string.notif_reminder, task.title)
            1 -> CH_DUE to context.getString(R.string.notif_nearly_due, task.title)
            else -> CH_OVERDUE to context.getString(R.string.notif_overdue, task.title)
        }
        val body = when (kind) {
            2 -> context.getString(R.string.notif_overdue_body)
            else -> whenText(task).ifEmpty { task.notes.take(120) }
        }
        val b = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setCategory(if (kind == 0) NotificationCompat.CATEGORY_REMINDER else NotificationCompat.CATEGORY_EVENT)
            .setPriority(if (kind == 2) NotificationCompat.PRIORITY_DEFAULT else NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(openIntent(context, task.id))
            .addAction(0, context.getString(R.string.action_done), action(context, task.id, AlarmReceiver.ACTION_COMPLETE, id))
        if (kind == 2) {
            b.addAction(0, context.getString(R.string.action_tomorrow), action(context, task.id, AlarmReceiver.ACTION_TOMORROW, id))
            b.addAction(0, context.getString(R.string.action_reschedule), openIntent(context, task.id, reschedule = true))
        } else {
            b.addAction(0, context.getString(R.string.action_snooze), action(context, task.id, AlarmReceiver.ACTION_SNOOZE, id))
            b.addAction(0, context.getString(R.string.action_plus_hour), action(context, task.id, AlarmReceiver.ACTION_PLUS_HOUR, id))
        }
        runCatching { NotificationManagerCompat.from(context).notify(id, b.build()) }
    }

    fun summaryNotification(context: Context, today: List<Task>, overdue: List<Task>) {
        if (!canPost(context) || (today.isEmpty() && overdue.isEmpty())) return
        val title = context.getString(R.string.notif_summary_title, today.size)
        val lines = (overdue.map { "⚠ " + it.title } + today.map { "• " + it.title }).take(8)
        val style = NotificationCompat.InboxStyle().also { s -> lines.forEach { s.addLine(it) } }
        val b = NotificationCompat.Builder(context, CH_SUMMARY)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(if (overdue.isNotEmpty()) context.getString(R.string.notif_summary_overdue, overdue.size) else lines.firstOrNull())
            .setStyle(style)
            .setAutoCancel(true)
            .setContentIntent(openIntent(context, null))
        if (overdue.isNotEmpty()) {
            val intent = Intent(context, AlarmReceiver::class.java).apply { action = AlarmReceiver.ACTION_ALL_TODAY }
            b.addAction(
                0, context.getString(R.string.action_move_overdue_today),
                PendingIntent.getBroadcast(context, 77, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE),
            )
        }
        runCatching { NotificationManagerCompat.from(context).notify(SUMMARY_ID, b.build()) }
    }

    fun syncNotification(context: Context, changed: Int) {
        if (!canPost(context)) return
        val b = NotificationCompat.Builder(context, CH_SYNC)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notif_sync_title))
            .setContentText(context.resources.getQuantityString(R.plurals.notif_sync_body, changed, changed))
            .setAutoCancel(true)
            .setContentIntent(openIntent(context, null))
        runCatching { NotificationManagerCompat.from(context).notify(SYNC_ID, b.build()) }
    }

    fun cancel(context: Context, id: Int) = NotificationManagerCompat.from(context).cancel(id)

    const val SUMMARY_ID = 1001
    const val SYNC_ID = 1002
}
