package com.tasker.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tasker.app.R
import com.tasker.app.data.Task
import com.tasker.app.domain.isOverdue
import com.tasker.app.domain.toDate
import com.tasker.app.domain.toTime
import com.tasker.app.domain.today
import com.tasker.app.ui.AppData
import com.tasker.app.ui.theme.LocalTaskerColors
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun dayLabel(epochDay: Long, long: Boolean = false): String {
    val d = epochDay.toDate()
    val t = today()
    return when (d) {
        t -> stringResource(R.string.today)
        t.plusDays(1) -> stringResource(R.string.tomorrow)
        t.minusDays(1) -> stringResource(R.string.yesterday)
        else -> if (!long && d.isAfter(t) && d.isBefore(t.plusDays(7))) d.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
        else if (long) d.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL))
        else if (d.year == t.year) d.format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()))
        else d.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
    }
}

fun timeLabel(minutes: Int): String = minutes.toTime().format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))

fun weekdayShort(d: LocalDate): String = d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())

@Composable
fun priorityColor(p: Int): Color {
    val c = LocalTaskerColors.current
    return when (p) {
        3 -> c.priorityHigh
        2 -> c.priorityMedium
        1 -> c.priorityLow
        else -> MaterialTheme.colorScheme.outline
    }
}

@Composable
fun TaskCheck(done: Boolean, priority: Int, onToggle: () -> Unit, size: Dp = 22.dp) {
    val ring = priorityColor(priority)
    val fill by animateColorAsState(if (done) MaterialTheme.colorScheme.primary else Color.Transparent, label = "check")
    Box(
        Modifier
            .size(size + 18.dp)
            .clip(CircleShape)
            .clickable(onClick = onToggle),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(size)
                .clip(CircleShape)
                .background(fill)
                .border(1.6.dp, if (done) MaterialTheme.colorScheme.primary else ring, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (done) Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(size * 0.7f))
        }
    }
}

@Composable
fun MetaChip(text: String, icon: ImageVector? = null, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            Icon(icon, null, tint = color, modifier = Modifier.size(13.dp))
            Spacer(Modifier.width(3.dp))
        }
        Text(text, style = MaterialTheme.typography.labelMedium, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun ColorDot(color: Color, size: Dp = 8.dp) {
    Box(Modifier.size(size).clip(CircleShape).background(color))
}

/** The metadata line under a task title. */
@Composable
fun TaskMeta(task: Task, data: AppData, showDate: Boolean = true, showProject: Boolean = true) {
    val colors = LocalTaskerColors.current
    val subs = data.subtasksByTask[task.id].orEmpty()
    val project = task.projectId?.let { data.projectById[it] }
    val overdue = task.isOverdue()
    val items = buildList<@Composable () -> Unit> {
        if (showDate && task.date != null) add {
            val label = dayLabel(task.date) + (task.time?.let { " " + timeLabel(it) } ?: "") +
                (task.endDate?.takeIf { it != task.date }?.let { " → " + dayLabel(it) } ?: "") +
                (task.endTime?.takeIf { task.endDate == task.date || task.endDate == null }?.let { "–" + timeLabel(it) } ?: "")
            MetaChip(label, Icons.Outlined.AccessTime, if (overdue) colors.overdue else MaterialTheme.colorScheme.onSurfaceVariant)
        } else if (!showDate && task.time != null) add {
            MetaChip(timeLabel(task.time) + (task.endTime?.let { "–" + timeLabel(it) } ?: ""), Icons.Outlined.AccessTime,
                if (overdue) colors.overdue else MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (task.durationMin != null) add { MetaChip(durationLabel(task.durationMin)) }
        if (task.recurrence != null) add { MetaChip("", Icons.Outlined.Repeat) }
        if (task.reminders.any { it > System.currentTimeMillis() }) add { MetaChip("", Icons.Outlined.NotificationsNone) }
        if (subs.isNotEmpty()) add { MetaChip("${subs.count { it.done }}/${subs.size}", Icons.Outlined.Checklist) }
        if (task.notes.isNotBlank()) add { MetaChip("", Icons.AutoMirrored.Outlined.Notes) }
        if (task.waitingFor.isNotBlank()) add { MetaChip(task.waitingFor, Icons.Outlined.HourglassEmpty) }
        if (showProject && project != null) add {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ColorDot(Color(project.color), 7.dp)
                Spacer(Modifier.width(4.dp))
                Text(project.name, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
        task.tags.mapNotNull { data.tagById[it] }.forEach { tag ->
            add {
                Text(
                    if (tag.name.startsWith("@")) tag.name else "#" + tag.name,
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(tag.color),
                    maxLines = 1,
                )
            }
        }
    }
    if (items.isNotEmpty()) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            items.forEach { it() }
        }
    }
}

@Composable
fun durationLabel(min: Int): String = when {
    min % 60 == 0 -> stringResource(R.string.duration_h, min / 60)
    min > 60 -> stringResource(R.string.duration_hm, min / 60, min % 60)
    else -> stringResource(R.string.duration_m, min)
}

/** Minimal list row: checkbox, title, metadata, optional star. */
@Composable
fun TaskRow(
    task: Task,
    data: AppData,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    showDate: Boolean = true,
    showProject: Boolean = true,
    onStar: (() -> Unit)? = null,
    dense: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
) {
    val top = task.topDate == today().toEpochDay()
    Row(
        modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 4.dp, top = if (dense) 0.dp else 2.dp, bottom = if (dense) 0.dp else 2.dp),
        verticalAlignment = Alignment.Top,
    ) {
        TaskCheck(task.done, task.priority, onToggle, size = if (dense) 18.dp else 22.dp)
        Column(
            Modifier
                .weight(1f)
                .padding(top = if (dense) 9.dp else 10.dp, bottom = 8.dp, end = 4.dp)
        ) {
            Text(
                task.title,
                style = if (dense) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge,
                color = if (task.done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                textDecoration = if (task.done) TextDecoration.LineThrough else null,
                maxLines = if (dense) 2 else 3,
                overflow = TextOverflow.Ellipsis,
            )
            if (!dense) {
                Spacer(Modifier.height(2.dp))
                TaskMeta(task, data, showDate, showProject)
            }
        }
        trailing?.invoke()
        if (onStar != null) {
            IconButton(onClick = onStar) {
                Icon(
                    if (top) Icons.Rounded.Star else Icons.Rounded.StarOutline,
                    stringResource(R.string.top3),
                    tint = if (top) LocalTaskerColors.current.top else MaterialTheme.colorScheme.outlineVariant,
                )
            }
        }
    }
}

/** Card used on boards (Kanban, Eisenhower) and as the drag ghost. */
@Composable
fun TaskCard(task: Task, data: AppData, onToggle: () -> Unit, modifier: Modifier = Modifier, elevated: Boolean = false) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = if (LocalTaskerColors.current.dark) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainerLowest,
        tonalElevation = 0.dp,
        shadowElevation = if (elevated) 10.dp else 0.5.dp,
    ) {
        Row(Modifier.padding(end = 10.dp), verticalAlignment = Alignment.Top) {
            TaskCheck(task.done, task.priority, onToggle, size = 20.dp)
            Column(Modifier.padding(top = 9.dp, bottom = 10.dp).weight(1f)) {
                Text(
                    task.title,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    textDecoration = if (task.done) TextDecoration.LineThrough else null,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(3.dp))
                TaskMeta(task, data)
            }
        }
    }
}

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier, count: Int? = null, color: Color = MaterialTheme.colorScheme.onSurfaceVariant, trailing: @Composable (() -> Unit)? = null) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 8.dp, top = 18.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text.uppercase(Locale.getDefault()), style = MaterialTheme.typography.labelSmall, color = color)
        if (count != null && count > 0) {
            Spacer(Modifier.width(6.dp))
            Text("$count", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
        }
        Spacer(Modifier.weight(1f))
        trailing?.invoke()
    }
}

@Composable
fun EmptyState(text: String, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    Column(
        modifier.fillMaxWidth().padding(vertical = 40.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.size(48.dp))
            Spacer(Modifier.height(12.dp))
        }
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
    }
}

@Composable
fun DropHighlight(active: Boolean, modifier: Modifier = Modifier, shape: RoundedCornerShape = RoundedCornerShape(12.dp), content: @Composable () -> Unit) {
    val bg by animateColorAsState(
        if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f) else Color.Transparent, label = "drop",
    )
    Box(modifier.clip(shape).background(bg)) { content() }
}

val ListPadding = PaddingValues(bottom = 120.dp)

@Composable
fun FullSize(content: @Composable () -> Unit) = Box(Modifier.fillMaxSize()) { content() }
