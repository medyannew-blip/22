package com.tasker.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tasker.app.R
import com.tasker.app.data.Gtd
import com.tasker.app.data.Status
import com.tasker.app.data.Task
import com.tasker.app.domain.epoch
import com.tasker.app.domain.today
import com.tasker.app.ui.AppData
import com.tasker.app.ui.MainViewModel
import com.tasker.app.ui.Route
import com.tasker.app.ui.components.ColorDot
import com.tasker.app.ui.components.DatePickerDialogM
import com.tasker.app.ui.components.DragDropState
import com.tasker.app.ui.components.DropHighlight
import com.tasker.app.ui.components.EmptyState
import com.tasker.app.ui.components.ListPadding
import com.tasker.app.ui.components.SectionHeader
import com.tasker.app.ui.components.TaskCard
import com.tasker.app.ui.components.TaskRow
import com.tasker.app.ui.components.dayLabel
import com.tasker.app.ui.components.dragAutoScroll
import com.tasker.app.ui.components.draggableTask
import com.tasker.app.ui.components.dropTarget
import com.tasker.app.ui.theme.LocalTaskerColors

// ---------------- Kanban ----------------

@Composable
fun statusName(st: Int): String = stringResource(
    when (st) {
        Status.TODO -> R.string.status_todo
        Status.DOING -> R.string.status_doing
        Status.WAITING -> R.string.status_waiting
        else -> R.string.status_done
    }
)

@Composable
fun KanbanScreen(vm: MainViewModel, data: AppData, dnd: DragDropState) {
    var filter by rememberSaveable { mutableStateOf<String?>(null) }
    val rowState = rememberLazyListState()
    val recent = System.currentTimeMillis() - 14L * 86_400_000L
    val statusColors = listOf(
        MaterialTheme.colorScheme.outline, MaterialTheme.colorScheme.primary,
        LocalTaskerColors.current.priorityMedium, Color(0xFF30A46C),
    )
    Column(Modifier.fillMaxSize()) {
        LazyRow(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            item { FilterChip(selected = filter == null, onClick = { filter = null }, label = { Text(stringResource(R.string.all_projects)) }) }
            items(data.projects.filter { !it.archived }, key = { it.id }) { p ->
                FilterChip(
                    selected = filter == p.id, onClick = { filter = if (filter == p.id) null else p.id },
                    label = { Text(p.name) }, leadingIcon = { ColorDot(Color(p.color)) },
                )
            }
        }
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val colW = if (maxWidth > 900.dp) (maxWidth - 40.dp) / 4 else 290.dp
            LazyRow(
                Modifier.fillMaxSize().dragAutoScroll(dnd, rowState, vertical = false),
                state = rowState,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(Status.all, key = { it }) { st ->
                    val key = "status:$st"
                    val list = data.tasks
                        .filter { filter == null || it.projectId == filter }
                        .filter {
                            if (st == Status.DONE) it.done && (it.completedAt ?: 0) > recent
                            else !it.done && (it.status == st || (st == Status.TODO && it.status == Status.DONE))
                        }
                        .sortedBy { it.sortOrder }
                    val colState = rememberLazyListState()
                    Column(
                        Modifier.width(colW).fillMaxHeight().clip(RoundedCornerShape(16.dp))
                            .background(
                                if (dnd.hoverKey == key) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                else MaterialTheme.colorScheme.surfaceContainer
                            )
                            .dropTarget(dnd, key)
                    ) {
                        Row(Modifier.padding(start = 14.dp, end = 4.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            ColorDot(statusColors[st], 9.dp)
                            Spacer(Modifier.width(8.dp))
                            Text(statusName(st), style = MaterialTheme.typography.titleSmall)
                            Spacer(Modifier.width(6.dp))
                            Text("${list.size}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
                            Spacer(Modifier.weight(1f))
                            if (st != Status.DONE) IconButton(onClick = { vm.showQuickAdd("", Task(status = st, projectId = filter, gtd = Gtd.NEXT)) }) {
                                Icon(Icons.Outlined.Add, stringResource(R.string.add_task), tint = MaterialTheme.colorScheme.outline)
                            }
                        }
                        LazyColumn(
                            Modifier.fillMaxSize().dragAutoScroll(dnd, colState),
                            state = colState,
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 8.dp, end = 8.dp, bottom = 96.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            items(list, key = { it.id }) { t ->
                                TaskCard(t, data, { vm.toggleDone(t) }, Modifier.animateItem().draggableTask(dnd, t, key) { vm.openTask(t.id) })
                            }
                            if (list.isEmpty()) item { EmptyState(stringResource(R.string.drop_here)) }
                        }
                    }
                }
            }
        }
    }
}

// ---------------- Eisenhower ----------------

@Composable
fun EisenhowerScreen(vm: MainViewModel, data: AppData, dnd: DragDropState) {
    val c = LocalTaskerColors.current
    val quads = listOf(
        Triple(true to true, R.string.eis_do, c.priorityHigh),
        Triple(true to false, R.string.eis_schedule, c.priorityLow),
        Triple(false to true, R.string.eis_delegate, c.priorityMedium),
        Triple(false to false, R.string.eis_eliminate, MaterialTheme.colorScheme.outline),
    )
    val subtitles = listOf(R.string.eis_do_sub, R.string.eis_schedule_sub, R.string.eis_delegate_sub, R.string.eis_eliminate_sub)
    Column(Modifier.fillMaxSize().padding(8.dp)) {
        Row(Modifier.padding(start = 8.dp, bottom = 4.dp)) {
            Text(stringResource(R.string.eis_urgent), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Text(stringResource(R.string.eis_not_urgent), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
        // Rows: important (do, schedule) / not important (delegate, eliminate). Columns: urgent / not urgent.
        listOf(listOf(0, 1), listOf(2, 3)).forEach { row ->
            Row(Modifier.weight(1f)) {
                row.forEach { qi ->
                    val (flags, title, color) = quads[qi]
                    val key = "quad:${if (flags.first) 1 else 0}:${if (flags.second) 1 else 0}"
                    val list = data.open.filter { it.important == flags.first && it.urgent == flags.second }
                    val state = rememberLazyListState()
                    Column(
                        Modifier.weight(1f).fillMaxHeight().padding(4.dp).clip(RoundedCornerShape(16.dp))
                            .background(if (dnd.hoverKey == key) color.copy(alpha = 0.22f) else color.copy(alpha = if (c.dark) 0.12f else 0.07f))
                            .dropTarget(dnd, key)
                    ) {
                        Row(Modifier.padding(start = 12.dp, top = 10.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(title), style = MaterialTheme.typography.titleSmall, color = color, maxLines = 1)
                                Text(stringResource(subtitles[qi]), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline, maxLines = 2)
                            }
                            Text("${list.size}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
                            IconButton(onClick = { vm.showQuickAdd("", Task(important = flags.first, urgent = flags.second, gtd = Gtd.NEXT)) }, modifier = Modifier.size(36.dp)) {
                                Icon(Icons.Outlined.Add, stringResource(R.string.add_task), tint = MaterialTheme.colorScheme.outline)
                            }
                        }
                        LazyColumn(Modifier.fillMaxSize().dragAutoScroll(dnd, state), state = state) {
                            items(list.sortedBy { it.sortOrder }, key = { it.id }) { t ->
                                TaskRow(t, data, { vm.toggleDone(t) }, Modifier.draggableTask(dnd, t, key) { vm.openTask(t.id) }, dense = true)
                            }
                        }
                    }
                }
            }
        }
        Text(
            stringResource(R.string.eis_important) + " ↑   ·   " + stringResource(R.string.eis_not_important) + " ↓",
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.fillMaxWidth().padding(4.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

// ---------------- GTD ----------------

@Composable
fun GtdScreen(vm: MainViewModel, data: AppData, dnd: DragDropState) {
    var tab by rememberSaveable { mutableStateOf(0) }
    val tabs = listOf(
        R.string.inbox to "gtd:${Gtd.INBOX}",
        R.string.gtd_next to "gtd:${Gtd.NEXT}",
        R.string.gtd_waiting to "gtd:${Gtd.WAITING}",
        R.string.gtd_scheduled to "tab:scheduled",
        R.string.gtd_someday to "gtd:${Gtd.SOMEDAY}",
        R.string.gtd_reference to "gtd:${Gtd.REFERENCE}",
        R.string.projects to "tab:projects",
        R.string.gtd_review to "tab:review",
    )
    var clarify by remember { mutableStateOf<Task?>(null) }
    val t = today().epoch()
    Column(Modifier.fillMaxSize()) {
        PrimaryScrollableTabRow(selectedTabIndex = tab, edgePadding = 8.dp, containerColor = MaterialTheme.colorScheme.background) {
            tabs.forEachIndexed { i, (label, key) ->
                val count = when (i) {
                    0 -> data.open.count { it.gtd == Gtd.INBOX }
                    1 -> data.open.count { it.gtd == Gtd.NEXT }
                    2 -> data.open.count { it.gtd == Gtd.WAITING }
                    else -> 0
                }
                Tab(
                    selected = tab == i, onClick = { tab = i },
                    unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = if (key.startsWith("gtd:")) Modifier.dropTarget(dnd, key)
                        .background(if (dnd.hoverKey == key) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent) else Modifier,
                    text = { Text(stringResource(label) + if (count > 0) " $count" else "", maxLines = 1) },
                )
            }
        }
        val listState = rememberLazyListState()
        val listKey = tabs[tab].second.let { if (it.startsWith("gtd:")) it else "list:$it" }
        LazyColumn(Modifier.fillMaxSize().dropTarget(dnd, listKey).dragAutoScroll(dnd, listState), state = listState, contentPadding = ListPadding) {
            when (tab) {
                0 -> {
                    val inbox = data.open.filter { it.gtd == Gtd.INBOX }
                    item {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.inbox_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                            if (inbox.isNotEmpty()) TextButton(onClick = { clarify = inbox.first() }) { Text(stringResource(R.string.clarify)) }
                        }
                    }
                    if (inbox.isEmpty()) item { EmptyState(stringResource(R.string.inbox_empty)) }
                    items(inbox, key = { it.id }) { task -> GtdRow(task, vm, data, dnd, listKey) }
                }
                1 -> {
                    val next = data.open.filter { it.gtd == Gtd.NEXT }
                    val contexts = data.tags.filter { it.name.startsWith("@") }
                    contexts.forEach { ctx ->
                        val list = next.filter { ctx.id in it.tags }
                        item("ctx" + ctx.id) {
                            val key = "ctx:${ctx.id}"
                            DropHighlight(dnd.hoverKey == key) {
                                Column(Modifier.fillMaxWidth().dropTarget(dnd, key)) {
                                    SectionHeader(ctx.name, count = list.size, color = Color(ctx.color))
                                    list.forEach { task -> GtdRow(task, vm, data, dnd, key) }
                                }
                            }
                        }
                    }
                    val noCtx = next.filter { task -> contexts.none { it.id in task.tags } }
                    item("noctx") {
                        val key = "ctx:none"
                        DropHighlight(dnd.hoverKey == key) {
                            Column(Modifier.fillMaxWidth().dropTarget(dnd, key)) {
                                SectionHeader(stringResource(R.string.gtd_no_context), count = noCtx.size)
                                noCtx.forEach { task -> GtdRow(task, vm, data, dnd, key) }
                                if (contexts.isEmpty()) Text(
                                    stringResource(R.string.gtd_context_hint), style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(16.dp),
                                )
                            }
                        }
                    }
                }
                2 -> {
                    val waiting = data.open.filter { it.gtd == Gtd.WAITING || it.status == Status.WAITING }
                    if (waiting.isEmpty()) item { EmptyState(stringResource(R.string.list_empty)) }
                    items(waiting, key = { it.id }) { task -> GtdRow(task, vm, data, dnd, listKey) }
                }
                3 -> {
                    val scheduled = data.open.filter { it.date != null && it.date >= t && it.gtd != Gtd.SOMEDAY }.sortedWith(compareBy<Task>({ it.date }).then(dayOrder))
                    scheduled.groupBy { it.date!! }.forEach { (day, list) ->
                        item("s$day") { SectionHeader(dayLabel(day, long = true), count = list.size) }
                        items(list, key = { it.id }) { task -> GtdRow(task, vm, data, dnd, "day:$day") }
                    }
                    if (scheduled.isEmpty()) item { EmptyState(stringResource(R.string.nothing_planned)) }
                }
                4, 5 -> {
                    val g = if (tab == 4) Gtd.SOMEDAY else Gtd.REFERENCE
                    val list = data.open.filter { it.gtd == g }
                    if (list.isEmpty()) item { EmptyState(stringResource(R.string.list_empty)) }
                    items(list, key = { it.id }) { task -> GtdRow(task, vm, data, dnd, listKey) }
                }
                6 -> {
                    items(data.projects.filter { !it.archived }, key = { it.id }) { p ->
                        val open = data.open.filter { it.projectId == p.id }
                        val nextCount = open.count { it.gtd == Gtd.NEXT || (it.date != null && it.date <= t + 7) }
                        Row(
                            Modifier.fillMaxWidth().clickable { vm.navigateRoot(Route.ProjectRoute(p.id)) }.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ColorDot(Color(p.color), 10.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(p.name, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    stringResource(R.string.gtd_project_stats, open.size, nextCount),
                                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline,
                                )
                            }
                            if (open.isNotEmpty() && nextCount == 0 || open.isEmpty()) {
                                Icon(Icons.Outlined.WarningAmber, stringResource(R.string.gtd_project_needs_next), tint = LocalTaskerColors.current.priorityMedium)
                            }
                        }
                    }
                }
                7 -> item { WeeklyReview(vm, data) }
            }
        }
    }
    clarify?.let { task ->
        ClarifyDialog(vm, data, task, onNext = {
            clarify = data.open.filter { it.gtd == Gtd.INBOX && it.id != task.id }.firstOrNull()
        }, onDismiss = { clarify = null })
    }
}

@Composable
private fun GtdRow(task: Task, vm: MainViewModel, data: AppData, dnd: DragDropState, key: String) {
    TaskRow(task, data, { vm.toggleDone(task) }, Modifier.draggableTask(dnd, task, key) { vm.openTask(task.id) }, onStar = { vm.toggleTop3(task) })
}

@Composable
private fun WeeklyReview(vm: MainViewModel, data: AppData) {
    val steps = listOf(
        R.string.review_1, R.string.review_2, R.string.review_3, R.string.review_4,
        R.string.review_5, R.string.review_6, R.string.review_7,
    )
    val checked = remember { mutableStateListOf<Int>() }
    Column(Modifier.padding(16.dp)) {
        Text(stringResource(R.string.gtd_review_intro), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        steps.forEachIndexed { i, s ->
            Row(Modifier.fillMaxWidth().clickable { if (i in checked) checked.remove(i) else checked.add(i) }, verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = i in checked, onCheckedChange = { if (it) checked.add(i) else checked.remove(i) })
                Text(stringResource(s), style = MaterialTheme.typography.bodyMedium)
            }
        }
        Spacer(Modifier.height(12.dp))
        val stale = data.open.count { it.gtd == Gtd.INBOX }
        if (stale > 0) Text(stringResource(R.string.review_inbox_left, stale), color = LocalTaskerColors.current.priorityMedium, style = MaterialTheme.typography.bodySmall)
        if (checked.size == steps.size) Text(stringResource(R.string.review_done), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleSmall)
    }
}

/** GTD "clarify" flow for an inbox item. */
@Composable
fun ClarifyDialog(vm: MainViewModel, data: AppData, task: Task, onNext: () -> Unit, onDismiss: () -> Unit) {
    var step by remember(task.id) { mutableStateOf(0) }
    var waitingFor by remember(task.id) { mutableStateOf("") }
    var pickDate by remember(task.id) { mutableStateOf(false) }
    var projectId by remember(task.id) { mutableStateOf(task.projectId) }
    fun finish(f: (Task) -> Task) {
        vm.update(task.id, f)
        onNext()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(task.title, maxLines = 3, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val q = when (step) {
                    0 -> R.string.clarify_actionable
                    1 -> R.string.clarify_not_actionable
                    2 -> R.string.clarify_two_minutes
                    3 -> R.string.clarify_who
                    else -> R.string.clarify_next
                }
                Text(stringResource(q), style = MaterialTheme.typography.titleSmall)
                when (step) {
                    0 -> {
                        ChoiceButton(stringResource(R.string.yes)) { step = 2 }
                        ChoiceButton(stringResource(R.string.no)) { step = 1 }
                    }
                    1 -> {
                        ChoiceButton(stringResource(R.string.clarify_trash)) { vm.delete(task.id); onNext() }
                        ChoiceButton(stringResource(R.string.gtd_someday)) { finish { it.copy(gtd = Gtd.SOMEDAY) } }
                        ChoiceButton(stringResource(R.string.gtd_reference)) { finish { it.copy(gtd = Gtd.REFERENCE) } }
                    }
                    2 -> {
                        ChoiceButton(stringResource(R.string.clarify_do_now)) { vm.toggleDone(task); onNext() }
                        ChoiceButton(stringResource(R.string.clarify_delegate)) { step = 3 }
                        ChoiceButton(stringResource(R.string.clarify_defer)) { pickDate = true }
                        ChoiceButton(stringResource(R.string.clarify_next_action)) { step = 4 }
                    }
                    3 -> {
                        OutlinedTextField(waitingFor, { waitingFor = it }, label = { Text(stringResource(R.string.waiting_for)) }, singleLine = true)
                        ChoiceButton(stringResource(R.string.ok)) {
                            finish { it.copy(gtd = Gtd.WAITING, status = Status.WAITING, waitingFor = waitingFor.trim()) }
                        }
                    }
                    else -> {
                        Text(stringResource(R.string.project), style = MaterialTheme.typography.labelMedium)
                        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(selected = projectId == null, onClick = { projectId = null }, label = { Text(stringResource(R.string.none)) })
                        }
                        data.projects.filter { !it.archived }.chunked(2).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                row.forEach { p ->
                                    FilterChip(
                                        selected = projectId == p.id, onClick = { projectId = p.id },
                                        label = { Text(p.name, maxLines = 1) }, leadingIcon = { ColorDot(Color(p.color)) },
                                    )
                                }
                            }
                        }
                        ChoiceButton(stringResource(R.string.ok)) { finish { it.copy(gtd = Gtd.NEXT, projectId = projectId) } }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
        dismissButton = { if (step > 0) TextButton(onClick = { step = 0 }) { Text(stringResource(R.string.back)) } },
    )
    if (pickDate) DatePickerDialogM(task.date, onPick = { d -> finish { it.copy(date = d, gtd = Gtd.NEXT) } }, onDismiss = { pickDate = false })
}

@Composable
private fun ChoiceButton(text: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
        Text(text, fontWeight = FontWeight.Medium)
    }
}
