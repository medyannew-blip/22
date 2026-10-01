package com.tasker.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircleOutline
import androidx.compose.material.icons.outlined.EventRepeat
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tasker.app.R
import com.tasker.app.data.Gtd
import com.tasker.app.data.Task
import com.tasker.app.domain.epoch
import com.tasker.app.domain.isOverdue
import com.tasker.app.domain.toDate
import com.tasker.app.domain.today
import com.tasker.app.ui.AppData
import com.tasker.app.ui.MainViewModel
import com.tasker.app.ui.components.DragDropState
import com.tasker.app.ui.components.DropHighlight
import com.tasker.app.ui.components.EmptyState
import com.tasker.app.ui.components.ListPadding
import com.tasker.app.ui.components.SectionHeader
import com.tasker.app.ui.components.TaskRow
import com.tasker.app.ui.components.dayLabel
import com.tasker.app.ui.components.dragAutoScroll
import com.tasker.app.ui.components.draggableTask
import com.tasker.app.ui.components.dropTarget
import com.tasker.app.ui.theme.LocalTaskerColors
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Sorting used by day lists: timed tasks first by time, then manual order. */
val dayOrder = compareBy<Task>({ it.time ?: Int.MAX_VALUE }, { it.sortOrder })

@Composable
fun TodayScreen(vm: MainViewModel, data: AppData, dnd: DragDropState) {
    val t = today().epoch()
    val colors = LocalTaskerColors.current
    val top = data.open.filter { it.topDate == t }.take(3)
    val topIds = top.map { it.id }.toSet()
    val overdue = data.open.filter { it.id !in topIds && it.date != null && it.date < t }
    val todays = data.open.filter { it.id !in topIds && it.date == t }.sortedWith(dayOrder)
    val doneToday = data.tasks.filter { it.done && it.completedAt != null && it.completedAt.toLocalDay() == t }
    var showDone by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    LazyColumn(
        Modifier.fillMaxSize().dragAutoScroll(dnd, listState),
        state = listState,
        contentPadding = ListPadding,
    ) {
        item("header") {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
                Text(
                    today().format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val total = todays.size + top.size + doneToday.size
                if (total > 0) {
                    Spacer(Modifier.height(10.dp))
                    LinearProgressIndicator(
                        progress = { doneToday.size.toFloat() / total },
                        modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.done_of_total, doneToday.size, total),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            }
        }
        // Top 3 priorities
        item("top3") {
            val key = "top3:$t"
            DropHighlight(dnd.hoverKey == key, Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .dropTarget(dnd, key)
                        .clip(RoundedCornerShape(16.dp))
                        .background(colors.top.copy(alpha = if (colors.dark) 0.10f else 0.08f))
                        .padding(vertical = 6.dp)
                ) {
                    Text(
                        stringResource(R.string.top3_title),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp),
                    )
                    top.forEachIndexed { i, task ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "${i + 1}",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = colors.top,
                                modifier = Modifier.padding(start = 16.dp).width(14.dp),
                            )
                            TaskRow(
                                task, data, onToggle = { vm.toggleDone(task) },
                                modifier = Modifier.weight(1f).draggableTask(dnd, task, key) { vm.openTask(task.id) },
                                showDate = task.date != t,
                                onStar = { vm.toggleTop3(task) },
                            )
                        }
                    }
                    repeat(3 - top.size) { i ->
                        Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("${top.size + i + 1}", style = MaterialTheme.typography.titleMedium, color = colors.top.copy(alpha = 0.5f), modifier = Modifier.width(14.dp))
                            Spacer(Modifier.width(12.dp))
                            Box(
                                Modifier
                                    .weight(1f)
                                    .height(36.dp)
                                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp)),
                                contentAlignment = Alignment.CenterStart,
                            ) {
                                Text(
                                    stringResource(R.string.top3_empty),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.padding(horizontal = 12.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
        if (overdue.isNotEmpty()) {
            item("overdue") {
                val key = "list:overdue"
                Column(Modifier.dropTarget(dnd, key)) {
                    SectionHeader(
                        stringResource(R.string.overdue), count = overdue.size, color = colors.overdue,
                        trailing = {
                            TextButton(onClick = { vm.rescheduleAllOverdueToToday() }) { Text(stringResource(R.string.move_to_today)) }
                        },
                    )
                    overdue.sortedBy { it.date }.forEach { task ->
                        TaskRow(
                            task, data, onToggle = { vm.toggleDone(task) },
                            modifier = Modifier.draggableTask(dnd, task, key) { vm.openTask(task.id) },
                            trailing = {
                                IconButton(onClick = { vm.rescheduleTask = task.id }) {
                                    Icon(Icons.Outlined.EventRepeat, stringResource(R.string.reschedule), tint = MaterialTheme.colorScheme.outline)
                                }
                            },
                        )
                    }
                }
            }
        }
        item("today") {
            val key = "day:$t"
            DropHighlight(dnd.hoverKey == key) {
                Column(Modifier.fillMaxWidth().dropTarget(dnd, "day:$t")) {
                    SectionHeader(stringResource(R.string.today), count = todays.size)
                    if (todays.isEmpty()) {
                        EmptyState(stringResource(R.string.today_empty), icon = Icons.Outlined.WbSunny)
                    }
                    todays.forEach { task ->
                        TaskRow(
                            task, data, onToggle = { vm.toggleDone(task) },
                            modifier = Modifier.draggableTask(dnd, task, "day:$t") { vm.openTask(task.id) },
                            showDate = false,
                            onStar = { vm.toggleTop3(task) },
                        )
                    }
                }
            }
        }
        if (doneToday.isNotEmpty()) {
            item("done-header") {
                SectionHeader(
                    stringResource(R.string.completed), count = doneToday.size,
                    trailing = {
                        IconButton(onClick = { showDone = !showDone }) {
                            Icon(if (showDone) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null)
                        }
                    },
                )
            }
            if (showDone) items(doneToday, key = { "d" + it.id }) { task ->
                TaskRow(task, data, onToggle = { vm.toggleDone(task) }, modifier = Modifier.draggableTask(dnd, task, "done") { vm.openTask(task.id) })
            }
        }
    }
}

fun Long.toLocalDay(): Long = java.time.Instant.ofEpochMilli(this).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toEpochDay()

sealed interface ListKind {
    data object Inbox : ListKind
    data object All : ListKind
    data class Project(val id: String) : ListKind
    data class TagList(val id: String) : ListKind
}

@Composable
fun TaskListScreen(vm: MainViewModel, data: AppData, dnd: DragDropState, kind: ListKind) {
    val tasks = when (kind) {
        ListKind.Inbox -> data.tasks.filter { it.gtd == Gtd.INBOX }
        ListKind.All -> data.tasks
        is ListKind.Project -> data.tasks.filter { it.projectId == kind.id }
        is ListKind.TagList -> data.tasks.filter { kind.id in it.tags }
    }
    val open = tasks.filter { !it.done }.sortedBy { it.sortOrder }
    val done = tasks.filter { it.done }.sortedByDescending { it.completedAt ?: 0 }
    var showDone by remember { mutableStateOf(false) }
    var clarify by remember { mutableStateOf<Task?>(null) }
    val key = "list:" + kind.toString()
    val listState = rememberLazyListState()

    LazyColumn(
        Modifier.fillMaxSize().dropTarget(dnd, key).dragAutoScroll(dnd, listState),
        state = listState,
        contentPadding = ListPadding,
    ) {
        if (kind is ListKind.Project) {
            item("progress") {
                val total = open.size + done.size
                val project = data.projectById[kind.id]
                Column(Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
                    project?.folderId?.let { fid -> data.folders.firstOrNull { it.id == fid } }?.let {
                        Text(it.name, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
                    }
                    if (total > 0) {
                        Spacer(Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { done.size.toFloat() / total },
                            modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                            color = project?.let { Color(it.color) } ?: MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        )
                    }
                    if (open.isNotEmpty() && open.none { it.gtd == Gtd.NEXT || it.date != null }) {
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.gtd_project_needs_next), style = MaterialTheme.typography.bodySmall, color = LocalTaskerColors.current.priorityMedium)
                    }
                }
            }
        }
        if (kind == ListKind.Inbox && open.isNotEmpty()) {
            item("clarify") {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                        .padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.inbox_hint), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    TextButton(onClick = { clarify = open.first() }) { Text(stringResource(R.string.clarify)) }
                }
            }
        }
        if (open.isEmpty()) item("empty") {
            EmptyState(
                stringResource(if (kind == ListKind.Inbox) R.string.inbox_empty else R.string.list_empty),
                icon = if (kind == ListKind.Inbox) Icons.Outlined.Inbox else Icons.Outlined.CheckCircleOutline,
            )
        }
        items(open, key = { it.id }) { task ->
            TaskRow(
                task, data, onToggle = { vm.toggleDone(task) },
                modifier = Modifier.animateItem().draggableTask(dnd, task, key) { vm.openTask(task.id) },
                showProject = kind !is ListKind.Project,
                onStar = { vm.toggleTop3(task) },
            )
        }
        if (done.isNotEmpty()) {
            item("done-header") {
                SectionHeader(
                    stringResource(R.string.completed), count = done.size,
                    trailing = {
                        IconButton(onClick = { showDone = !showDone }) {
                            Icon(if (showDone) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null)
                        }
                    },
                )
            }
            if (showDone) items(done.take(200), key = { it.id }) { task ->
                TaskRow(task, data, onToggle = { vm.toggleDone(task) }, modifier = Modifier.draggableTask(dnd, task, "done") { vm.openTask(task.id) })
            }
        }
    }
    clarify?.let { task ->
        ClarifyDialog(vm, data, task, onNext = {
            val remaining = data.tasks.filter { it.gtd == Gtd.INBOX && !it.done && it.id != task.id }.sortedBy { it.sortOrder }
            clarify = remaining.firstOrNull()
        }, onDismiss = { clarify = null })
    }
}

@Composable
fun LogbookScreen(vm: MainViewModel, data: AppData) {
    val done = data.tasks.filter { it.done }.sortedByDescending { it.completedAt ?: 0 }
    val groups = done.groupBy { it.completedAt?.toLocalDay() ?: 0L }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = ListPadding) {
        if (done.isEmpty()) item { EmptyState(stringResource(R.string.logbook_empty), icon = Icons.Outlined.CheckCircleOutline) }
        groups.forEach { (day, list) ->
            item("h$day") { SectionHeader(dayLabel(day, long = true), count = list.size) }
            items(list, key = { it.id }) { task ->
                TaskRow(task, data, onToggle = { vm.toggleDone(task) }, modifier = Modifier.clickableTask { vm.openTask(task.id) })
            }
        }
    }
}

@Composable
fun SearchScreen(vm: MainViewModel, data: AppData) {
    var q by remember { mutableStateOf("") }
    val query = q.trim()
    val results = if (query.length < 1) emptyList() else data.tasks.filter { t ->
        t.title.contains(query, true) || t.notes.contains(query, true) ||
            t.tags.any { data.tagById[it]?.name?.contains(query.removePrefix("#"), true) == true } ||
            (t.projectId?.let { data.projectById[it]?.name?.contains(query, true) } == true) ||
            data.subtasksByTask[t.id].orEmpty().any { it.title.contains(query, true) }
    }.sortedWith(compareBy({ it.done }, { it.date ?: Long.MAX_VALUE }))
    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = q, onValueChange = { q = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = { Text(stringResource(R.string.search_hint)) },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
        )
        LazyColumn(Modifier.fillMaxSize(), contentPadding = ListPadding) {
            items(results, key = { it.id }) { task ->
                TaskRow(task, data, onToggle = { vm.toggleDone(task) }, modifier = Modifier.clickableTask { vm.openTask(task.id) })
            }
            if (query.isNotEmpty() && results.isEmpty()) item { EmptyState(stringResource(R.string.no_results)) }
        }
    }
}

fun Modifier.clickableTask(onClick: () -> Unit): Modifier = this.clickable(onClick = onClick)
