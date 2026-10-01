package com.tasker.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Tag
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.ViewKanban
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.tasker.app.R
import com.tasker.app.data.Gtd
import com.tasker.app.data.Status
import com.tasker.app.data.Subtask
import com.tasker.app.data.Task
import com.tasker.app.domain.epoch
import com.tasker.app.domain.millisOf
import com.tasker.app.domain.orderBetween
import com.tasker.app.domain.toLocalDateTime
import com.tasker.app.domain.today
import com.tasker.app.ui.AppData
import com.tasker.app.ui.MainViewModel
import com.tasker.app.ui.components.ColorDot
import com.tasker.app.ui.components.DatePickerDialogM
import com.tasker.app.ui.components.TaskCheck
import com.tasker.app.ui.components.TimePickerDialogM
import com.tasker.app.ui.components.dayLabel
import com.tasker.app.ui.components.durationLabel
import com.tasker.app.ui.components.priorityColor
import com.tasker.app.ui.components.timeLabel
import com.tasker.app.ui.theme.LocalTaskerColors
import kotlinx.coroutines.delay
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun TaskDetailScreen(vm: MainViewModel, data: AppData, id: String) {
    val task = data.taskById[id]
    if (task == null) {
        Text(stringResource(R.string.task_not_found), modifier = Modifier.padding(24.dp))
        return
    }
    val subtasks = data.subtasksByTask[id].orEmpty().sortedBy { it.sortOrder }

    var title by remember(id) { mutableStateOf(task.title) }
    var notes by remember(id) { mutableStateOf(task.notes) }
    val latestTitle by rememberUpdatedState(title)
    val latestNotes by rememberUpdatedState(notes)
    LaunchedEffect(title, notes) {
        delay(400)
        if (title != task.title || notes != task.notes) vm.update(id) { it.copy(title = title.ifBlank { it.title }, notes = notes) }
    }
    DisposableEffect(id) {
        onDispose { vm.update(id) { if (it.title != latestTitle || it.notes != latestNotes) it.copy(title = latestTitle.ifBlank { it.title }, notes = latestNotes) else it } }
    }

    var dialog by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(bottom = 80.dp)
    ) {
        // Title
        Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.Top) {
            TaskCheck(task.done, task.priority, { vm.toggleDone(task) }, size = 26.dp)
            BasicTextField(
                value = title,
                onValueChange = { title = it.replace("\n", " ") },
                modifier = Modifier.weight(1f).padding(top = 10.dp, end = 8.dp),
                textStyle = MaterialTheme.typography.headlineSmall.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                    textDecoration = if (task.done) TextDecoration.LineThrough else null,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
            )
            val top = task.topDate == today().epoch()
            IconButton(onClick = { vm.toggleTop3(task) }) {
                Icon(if (top) Icons.Rounded.Star else Icons.Rounded.StarOutline, stringResource(R.string.top3), tint = if (top) LocalTaskerColors.current.top else MaterialTheme.colorScheme.outline)
            }
        }
        Spacer(Modifier.height(8.dp))

        // ---- When ----
        DetailRow(Icons.Outlined.CalendarMonth, stringResource(R.string.start)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AssistChip(onClick = { dialog = "date" }, label = { Text(task.date?.let { dayLabel(it) } ?: stringResource(R.string.no_date)) })
                if (task.date != null) {
                    AssistChip(
                        onClick = { dialog = "time" },
                        label = { Text(task.time?.let { timeLabel(it) } ?: stringResource(R.string.add_time)) },
                        leadingIcon = { Icon(Icons.Outlined.AccessTime, null, Modifier.size(16.dp)) },
                    )
                    IconButton(onClick = { vm.reschedule(id, null) }) { Icon(Icons.Outlined.Close, stringResource(R.string.clear), Modifier.size(18.dp)) }
                }
            }
        }
        if (task.date != null) {
            val mode = when {
                task.endDate != null -> 1
                task.durationMin != null -> 2
                else -> 0
            }
            DetailRow(Icons.Outlined.Timer, stringResource(R.string.end_or_duration)) {
                Column {
                    SingleChoiceSegmentedButtonRow(Modifier.widthIn(max = 360.dp)) {
                        listOf(R.string.none, R.string.end_date, R.string.duration).forEachIndexed { i, label ->
                            SegmentedButton(
                                selected = mode == i,
                                onClick = {
                                    when (i) {
                                        0 -> vm.update(id) { it.copy(endDate = null, endTime = null, durationMin = null) }
                                        1 -> vm.update(id) { it.copy(endDate = it.date, endTime = it.time?.plus(60)?.coerceAtMost(1439), durationMin = null) }
                                        2 -> vm.update(id) { it.copy(endDate = null, endTime = null, durationMin = 60) }
                                    }
                                },
                                shape = SegmentedButtonDefaults.itemShape(i, 3),
                            ) { Text(stringResource(label), maxLines = 1) }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    if (mode == 1) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        AssistChip(onClick = { dialog = "enddate" }, label = { Text(task.endDate?.let { dayLabel(it) } ?: "") })
                        AssistChip(
                            onClick = { dialog = "endtime" },
                            label = { Text(task.endTime?.let { timeLabel(it) } ?: stringResource(R.string.add_time)) },
                            leadingIcon = { Icon(Icons.Outlined.AccessTime, null, Modifier.size(16.dp)) },
                        )
                    }
                    if (mode == 2) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(15, 30, 45, 60, 90, 120, 180, 240, 480).forEach { m ->
                            FilterChip(selected = task.durationMin == m, onClick = { vm.update(id) { it.copy(durationMin = m) } }, label = { Text(durationLabel(m)) })
                        }
                    }
                }
            }
        }

        // ---- Repeat ----
        DetailRow(Icons.Outlined.Repeat, stringResource(R.string.repeat)) {
            var open by remember { mutableStateOf(false) }
            val options = listOf(
                null to R.string.repeat_none, "DAILY" to R.string.repeat_daily, "WEEKDAYS" to R.string.repeat_weekdays,
                "WEEKLY" to R.string.repeat_weekly, "WEEKS:2" to R.string.repeat_biweekly, "MONTHLY" to R.string.repeat_monthly,
                "YEARLY" to R.string.repeat_yearly,
            )
            Column {
                AssistChip(onClick = { open = true }, label = {
                    Text(options.firstOrNull { it.first == task.recurrence }?.let { stringResource(it.second) } ?: (task.recurrence ?: ""))
                })
                DropdownMenu(open, { open = false }) {
                    options.forEach { (rule, label) ->
                        DropdownMenuItem(text = { Text(stringResource(label)) }, onClick = {
                            vm.update(id) { it.copy(recurrence = rule, date = if (rule != null) it.date ?: today().epoch() else it.date) }
                            open = false
                        })
                    }
                }
            }
        }

        // ---- Reminders ----
        DetailRow(Icons.Outlined.NotificationsNone, stringResource(R.string.reminders)) {
            Column {
                val now = System.currentTimeMillis()
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    task.reminders.sorted().forEach { r ->
                        val dt = r.toLocalDateTime()
                        InputChip(
                            selected = false,
                            onClick = { vm.update(id) { it.copy(reminders = it.reminders - r) } },
                            label = {
                                Text(
                                    dayLabel(dt.toLocalDate().epoch()) + " " + dt.toLocalTime().format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)),
                                    textDecoration = if (r < now) TextDecoration.LineThrough else null,
                                )
                            },
                            trailingIcon = { Icon(Icons.Outlined.Close, stringResource(R.string.delete), Modifier.size(16.dp)) },
                        )
                    }
                }
                var open by remember { mutableStateOf(false) }
                TextButton(onClick = { open = true }) { Text("+ " + stringResource(R.string.add_reminder)) }
                DropdownMenu(open, { open = false }) {
                    val start = if (task.date != null && task.time != null) millisOf(task.date, task.time) else null
                    if (start != null) {
                        listOf(0 to R.string.rem_at_start, 10 to R.string.rem_10m, 30 to R.string.rem_30m, 60 to R.string.rem_1h, 1440 to R.string.rem_1d).forEach { (m, label) ->
                            DropdownMenuItem(text = { Text(stringResource(label)) }, onClick = {
                                vm.update(id) { it.copy(reminders = (it.reminders + (start - m * 60_000L)).distinct()) }
                                open = false
                            })
                        }
                    }
                    DropdownMenuItem(text = { Text(stringResource(R.string.rem_in_1h)) }, onClick = {
                        vm.update(id) { it.copy(reminders = it.reminders + (System.currentTimeMillis() + 3_600_000L)) }
                        open = false
                    })
                    DropdownMenuItem(text = { Text(stringResource(R.string.rem_custom)) }, onClick = { dialog = "remdate"; open = false })
                }
            }
        }

        HorizontalDivider(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), color = MaterialTheme.colorScheme.outlineVariant)

        // ---- Priority ----
        DetailRow(Icons.Outlined.Flag, stringResource(R.string.priority)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(0 to R.string.prio_none, 1 to R.string.prio_low, 2 to R.string.prio_medium, 3 to R.string.prio_high).forEach { (p, label) ->
                    FilterChip(
                        selected = task.priority == p,
                        onClick = { vm.update(id) { it.copy(priority = p, important = if (p == 3) true else it.important) } },
                        label = { Text(stringResource(label)) },
                        leadingIcon = { Icon(Icons.Outlined.Flag, null, tint = priorityColor(p), modifier = Modifier.size(16.dp)) },
                    )
                }
            }
        }

        // ---- Project ----
        DetailRow(Icons.Outlined.Folder, stringResource(R.string.project)) {
            var open by remember { mutableStateOf(false) }
            Column {
                val p = task.projectId?.let { data.projectById[it] }
                AssistChip(
                    onClick = { open = true },
                    label = { Text(p?.name ?: stringResource(R.string.none)) },
                    leadingIcon = { if (p != null) ColorDot(Color(p.color), 10.dp) },
                )
                DropdownMenu(open, { open = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.none)) }, onClick = { vm.update(id) { it.copy(projectId = null) }; open = false })
                    data.projects.filter { !it.archived }.forEach { pr ->
                        DropdownMenuItem(
                            text = { Text(pr.name) }, leadingIcon = { ColorDot(Color(pr.color), 10.dp) },
                            onClick = { vm.update(id) { it.copy(projectId = pr.id, gtd = if (it.gtd == Gtd.INBOX) Gtd.NEXT else it.gtd) }; open = false },
                        )
                    }
                }
            }
        }

        // ---- Tags ----
        DetailRow(Icons.Outlined.Tag, stringResource(R.string.tags)) {
            Column {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    data.tags.forEach { tag ->
                        val sel = tag.id in task.tags
                        FilterChip(
                            selected = sel,
                            onClick = { vm.update(id) { it.copy(tags = if (sel) it.tags - tag.id else it.tags + tag.id) } },
                            label = { Text(if (tag.name.startsWith("@")) tag.name else "#" + tag.name, color = if (sel) Color.Unspecified else Color(tag.color)) },
                        )
                    }
                }
                var newTag by remember { mutableStateOf("") }
                BasicTextField(
                    value = newTag,
                    onValueChange = { newTag = it.replace(" ", "_") },
                    singleLine = true,
                    modifier = Modifier.padding(vertical = 8.dp).fillMaxWidth(),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        val name = newTag.trim()
                        if (name.isNotEmpty()) vm.createTag(name) { tag -> vm.update(id) { it.copy(tags = (it.tags + tag.id).distinct()) } }
                        newTag = ""
                    }),
                    decorationBox = { inner ->
                        if (newTag.isEmpty()) Text(stringResource(R.string.new_tag_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
                        inner()
                    },
                )
            }
        }

        // ---- GTD ----
        DetailRow(Icons.Outlined.Lightbulb, stringResource(R.string.view_gtd)) {
            Column {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        Gtd.INBOX to R.string.inbox, Gtd.NEXT to R.string.gtd_next, Gtd.WAITING to R.string.gtd_waiting,
                        Gtd.SOMEDAY to R.string.gtd_someday, Gtd.REFERENCE to R.string.gtd_reference,
                    ).forEach { (g, label) ->
                        FilterChip(selected = task.gtd == g, onClick = { vm.update(id) { it.copy(gtd = g) } }, label = { Text(stringResource(label)) })
                    }
                }
                if (task.gtd == Gtd.WAITING || task.status == Status.WAITING) {
                    var who by remember(id) { mutableStateOf(task.waitingFor) }
                    OutlinedTextField(
                        who, { who = it; vm.update(id) { t -> t.copy(waitingFor = who) } },
                        label = { Text(stringResource(R.string.waiting_for)) }, singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp, end = 16.dp),
                    )
                }
            }
        }

        // ---- Kanban status ----
        DetailRow(Icons.Outlined.ViewKanban, stringResource(R.string.view_kanban)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Status.all.forEach { st ->
                    FilterChip(
                        selected = (if (task.done) Status.DONE else task.status) == st,
                        onClick = {
                            if (st == Status.DONE) { if (!task.done) vm.toggleDone(task) }
                            else vm.update(id) { it.copy(status = st, done = false, completedAt = null, gtd = if (st == Status.WAITING) Gtd.WAITING else it.gtd) }
                        },
                        label = { Text(statusName(st)) },
                    )
                }
            }
        }

        // ---- Eisenhower ----
        DetailRow(Icons.Outlined.GridView, stringResource(R.string.view_eisenhower)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = task.important, onClick = { vm.update(id) { it.copy(important = !it.important) } }, label = { Text(stringResource(R.string.eis_important)) })
                FilterChip(selected = task.urgent, onClick = { vm.update(id) { it.copy(urgent = !it.urgent) } }, label = { Text(stringResource(R.string.eis_urgent)) })
            }
        }

        HorizontalDivider(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), color = MaterialTheme.colorScheme.outlineVariant)

        // ---- Subtasks ----
        DetailRow(Icons.Outlined.Checklist, stringResource(R.string.subtasks)) {
            Column {
                subtasks.forEach { st -> SubtaskRow(st, vm) }
                var newSub by remember { mutableStateOf("") }
                BasicTextField(
                    value = newSub,
                    onValueChange = { newSub = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        if (newSub.isNotBlank()) {
                            vm.saveSubtask(Subtask(taskId = id, title = newSub.trim(), sortOrder = orderBetween(subtasks.lastOrNull()?.sortOrder, null)))
                            newSub = ""
                        }
                    }),
                    decorationBox = { inner ->
                        if (newSub.isEmpty()) Text("+ " + stringResource(R.string.add_subtask), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
                        inner()
                    },
                )
            }
        }

        // ---- Notes ----
        DetailRow(Icons.AutoMirrored.Outlined.Notes, stringResource(R.string.notes)) {
            BasicTextField(
                value = notes,
                onValueChange = { notes = it },
                modifier = Modifier.fillMaxWidth().padding(end = 16.dp, top = 10.dp, bottom = 10.dp),
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                decorationBox = { inner ->
                    if (notes.isEmpty()) Text(stringResource(R.string.notes_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
                    inner()
                },
            )
        }

        Text(
            stringResource(R.string.created_on, java.time.Instant.ofEpochMilli(task.createdAt).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                .format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))),
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
        )
    }

    when (dialog) {
        "date" -> DatePickerDialogM(task.date, onPick = { d -> vm.reschedule(id, d) }, onDismiss = { dialog = null })
        "time" -> TimePickerDialogM(task.time, onPick = { m ->
            vm.update(id) { t ->
                val old = t.time
                val shiftMs = if (old != null && t.date != null) (m - old) * 60_000L else 0L
                t.copy(time = m, reminders = t.reminders.map { it + shiftMs })
            }
        }, onDismiss = { dialog = null }, onClear = { vm.update(id) { it.copy(time = null, endTime = null) } })
        "enddate" -> DatePickerDialogM(task.endDate ?: task.date, onPick = { d -> vm.update(id) { it.copy(endDate = maxOf(d, it.date ?: d)) } }, onDismiss = { dialog = null })
        "endtime" -> TimePickerDialogM(task.endTime, onPick = { m -> vm.update(id) { it.copy(endTime = m) } }, onDismiss = { dialog = null }, onClear = { vm.update(id) { it.copy(endTime = null) } })
        "remdate" -> DatePickerDialogM(task.date ?: today().epoch(), onPick = { d -> dialog = "remtime:$d" }, onDismiss = { if (dialog == "remdate") dialog = null })
        else -> dialog?.takeIf { it.startsWith("remtime:") }?.let { dd ->
            val day = dd.removePrefix("remtime:").toLong()
            TimePickerDialogM(task.time, onPick = { m -> vm.update(id) { it.copy(reminders = (it.reminders + millisOf(day, m)).distinct()) } }, onDismiss = { dialog = null })
        }
    }
}

@Composable
private fun SubtaskRow(st: Subtask, vm: MainViewModel) {
    var text by remember(st.id) { mutableStateOf(st.title) }
    LaunchedEffect(text) {
        delay(500)
        if (text != st.title && text.isNotBlank()) vm.saveSubtask(st.copy(title = text))
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        TaskCheck(st.done, 0, { vm.saveSubtask(st.copy(done = !st.done)) }, size = 18.dp)
        BasicTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.weight(1f),
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                color = if (st.done) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface,
                textDecoration = if (st.done) TextDecoration.LineThrough else null,
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        )
        IconButton(onClick = { vm.saveSubtask(st.copy(deleted = true)) }) {
            Icon(Icons.Outlined.Close, stringResource(R.string.delete), Modifier.size(16.dp), tint = MaterialTheme.colorScheme.outline)
        }
    }
}

@Composable
private fun DetailRow(icon: ImageVector, label: String, content: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 18.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.Top) {
        Icon(icon, label, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(top = 14.dp).size(20.dp))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(top = 4.dp))
            content()
        }
    }
}
