package com.tasker.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tasker.app.R
import com.tasker.app.data.Gtd
import com.tasker.app.data.Settings
import com.tasker.app.data.Task
import com.tasker.app.domain.blockMinutes
import com.tasker.app.domain.epoch
import com.tasker.app.domain.startOfWeek
import com.tasker.app.domain.toDate
import com.tasker.app.domain.today
import com.tasker.app.ui.AppData
import com.tasker.app.ui.MainViewModel
import com.tasker.app.ui.components.DragDropState
import com.tasker.app.ui.components.DropHighlight
import com.tasker.app.ui.components.EmptyState
import com.tasker.app.ui.components.ListPadding
import com.tasker.app.ui.components.SectionHeader
import com.tasker.app.ui.components.TaskCheck
import com.tasker.app.ui.components.TaskRow
import com.tasker.app.ui.components.dayLabel
import com.tasker.app.ui.components.dragAutoScroll
import com.tasker.app.ui.components.draggableTask
import com.tasker.app.ui.components.dropTarget
import com.tasker.app.ui.components.timeLabel
import com.tasker.app.ui.components.weekdayShort
import com.tasker.app.ui.theme.LocalTaskerColors
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun DateNavBar(label: String, onPrev: () -> Unit, onNext: () -> Unit, onToday: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        TextButton(onClick = onToday) { Text(stringResource(R.string.today)) }
        IconButton(onClick = onPrev) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, stringResource(R.string.previous)) }
        IconButton(onClick = onNext) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, stringResource(R.string.next)) }
    }
}

private fun tasksOn(data: AppData, day: Long, includeDone: Boolean = true): List<Task> =
    data.tasks.filter { it.date == day && (includeDone || !it.done) }

@Composable
private fun DayHeader(day: Long, modifier: Modifier = Modifier) {
    val d = day.toDate()
    val isToday = d == today()
    Column(modifier.padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            weekdayShort(d).uppercase(Locale.getDefault()),
            style = MaterialTheme.typography.labelSmall,
            color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
        )
        Box(
            Modifier.size(34.dp).clip(CircleShape).background(if (isToday) MaterialTheme.colorScheme.primary else Color.Transparent),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "${d.dayOfMonth}",
                style = MaterialTheme.typography.titleMedium,
                color = if (isToday) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/** Daily (days = 1) and 3-day (days = 3) time-grid views. */
@Composable
fun TimelineScreen(vm: MainViewModel, data: AppData, dnd: DragDropState, days: Int) {
    val start = vm.focusDay
    val dayList = (0 until days).map { start + it }
    val slotH = 26.dp
    val hourH = slotH * 2
    val labelW = 52.dp
    val density = LocalDensity.current
    val scroll = rememberScrollState()
    LaunchedEffect(Unit) { scroll.scrollTo(with(density) { (hourH * 7.5f).roundToPx() }) }

    val label = if (days == 1) dayLabel(start, long = true)
    else start.toDate().format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())) + " – " +
        (start + days - 1).toDate().format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()))

    Column(Modifier.fillMaxSize()) {
        DateNavBar(label, onPrev = { vm.focusDay -= days }, onNext = { vm.focusDay += days }, onToday = { vm.focusDay = today().epoch() })
        if (days > 1) Row {
            Spacer(Modifier.width(labelW))
            dayList.forEach { DayHeader(it, Modifier.weight(1f).clickable { vm.focusDay = it; vm.navigateRoot(com.tasker.app.ui.Route.Daily) }) }
        }
        // All-day / untimed tasks
        Row(Modifier.heightIn(max = 190.dp)) {
            Box(Modifier.width(labelW).padding(top = 10.dp), contentAlignment = Alignment.TopCenter) {
                Text(stringResource(R.string.all_day), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            }
            dayList.forEach { d ->
                val key = "untimed:$d"
                val untimed = tasksOn(data, d).filter { it.time == null }.sortedWith(compareBy({ it.done }, { it.sortOrder }))
                DropHighlight(dnd.hoverKey == key, Modifier.weight(1f)) {
                    Column(
                        Modifier.fillMaxWidth().heightIn(min = 44.dp).dropTarget(dnd, key)
                            .verticalScroll(rememberScrollState()).padding(2.dp)
                    ) {
                        untimed.forEach { task ->
                            if (days == 1) TaskRow(
                                task, data, onToggle = { vm.toggleDone(task) },
                                modifier = Modifier.draggableTask(dnd, task, key) { vm.openTask(task.id) },
                                showDate = false, onStar = { vm.toggleTop3(task) },
                            ) else MiniTask(task, vm, Modifier.draggableTask(dnd, task, key) { vm.openTask(task.id) })
                        }
                        if (untimed.isEmpty()) Text(
                            stringResource(R.string.drop_here), style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(12.dp),
                        )
                    }
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        // Time grid
        Row(Modifier.fillMaxSize().dragAutoScroll(dnd, scroll).verticalScroll(scroll)) {
            Column(Modifier.width(labelW)) {
                repeat(24) { h ->
                    Box(Modifier.height(hourH).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                        if (h > 0) Text(timeLabel(h * 60), fontSize = 10.sp, color = MaterialTheme.colorScheme.outline, modifier = Modifier.offset(y = (-7).dp))
                    }
                }
            }
            dayList.forEach { d ->
                VerticalDivider(Modifier.height(hourH * 24), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                BoxWithConstraints(Modifier.weight(1f).height(hourH * 24)) {
                    val colW = maxWidth
                    Column {
                        repeat(48) { slot ->
                            val key = "slot:$d:${slot * 30}"
                            Box(
                                Modifier.fillMaxWidth().height(slotH)
                                    .dropTarget(dnd, key)
                                    .background(if (dnd.hoverKey == key) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else Color.Transparent)
                                    .clickable { vm.showQuickAdd("", Task(date = d, time = slot * 30, gtd = Gtd.NEXT)) }
                            ) {
                                if (slot % 2 == 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                            }
                        }
                    }
                    TimedBlocks(tasksOn(data, d).filter { it.time != null }, d, slotH, colW, vm, data, dnd)
                    if (d == today().epoch()) {
                        val now = LocalTime.now()
                        val y = slotH * ((now.hour * 60 + now.minute) / 30f)
                        Box(Modifier.offset(y = y).fillMaxWidth().height(2.dp).background(LocalTaskerColors.current.overdue))
                        Box(Modifier.offset(x = (-4).dp, y = y - 3.dp).size(8.dp).clip(CircleShape).background(LocalTaskerColors.current.overdue))
                    }
                }
            }
        }
    }
}

@Composable
private fun TimedBlocks(tasks: List<Task>, day: Long, slotH: Dp, colW: Dp, vm: MainViewModel, data: AppData, dnd: DragDropState) {
    // Assign overlapping tasks to lanes
    val sorted = tasks.sortedBy { it.time }
    val lane = HashMap<String, Int>()
    val lanesInCluster = HashMap<String, Int>()
    var cluster = mutableListOf<Task>()
    var laneEnds = mutableListOf<Int>()
    var clusterEnd = -1
    fun flush() {
        cluster.forEach { lanesInCluster[it.id] = laneEnds.size.coerceAtLeast(1) }
        cluster = mutableListOf(); laneEnds = mutableListOf()
    }
    for (t in sorted) {
        val s = t.time!!
        val e = s + t.blockMinutes()
        if (s >= clusterEnd) { flush(); clusterEnd = e }
        val free = laneEnds.indexOfFirst { it <= s }
        val l = if (free >= 0) { laneEnds[free] = e; free } else { laneEnds += e; laneEnds.size - 1 }
        lane[t.id] = l
        cluster += t
        clusterEnd = maxOf(clusterEnd, e)
    }
    flush()
    sorted.forEach { t ->
        val lanes = lanesInCluster[t.id] ?: 1
        val w = colW / lanes
        val startMin: Int = t.time ?: 0
        val y = slotH * (startMin / 30f)
        val h = (slotH * (t.blockMinutes() / 30f)).coerceAtLeast(slotH)
        val color = t.projectId?.let { data.projectById[it]?.color }?.let { Color(it) } ?: MaterialTheme.colorScheme.primary
        Surface(
            modifier = Modifier
                .offset(x = w * (lane[t.id] ?: 0), y = y)
                .width(w)
                .height(h)
                .padding(horizontal = 2.dp, vertical = 1.dp)
                .draggableTask(dnd, t, "timed:$day") { vm.openTask(t.id) },
            shape = RoundedCornerShape(8.dp),
            color = color.copy(alpha = if (LocalTaskerColors.current.dark) 0.30f else 0.16f),
        ) {
            Row {
                Box(Modifier.width(3.dp).fillMaxHeight().background(color))
                Column(Modifier.padding(horizontal = 6.dp, vertical = 3.dp).alpha(if (t.done) 0.5f else 1f)) {
                    Text(
                        t.title, style = MaterialTheme.typography.labelLarge, maxLines = if (h > slotH * 2) 3 else 1,
                        overflow = TextOverflow.Ellipsis, textDecoration = if (t.done) TextDecoration.LineThrough else null,
                    )
                    if (h > slotH * 1.5f) Text(
                        timeLabel(startMin) + "–" + timeLabel((startMin + t.blockMinutes()).coerceAtMost(1439)),
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun MiniTask(task: Task, vm: MainViewModel, modifier: Modifier) {
    Row(
        modifier.fillMaxWidth().padding(vertical = 1.dp).clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(horizontal = 6.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            task.title, style = MaterialTheme.typography.labelMedium, maxLines = 2, overflow = TextOverflow.Ellipsis,
            textDecoration = if (task.done) TextDecoration.LineThrough else null,
            color = if (task.done) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface,
        )
    }
}

// ---------------- Weekly ----------------

@Composable
fun WeeklyScreen(vm: MainViewModel, s: Settings, data: AppData, dnd: DragDropState, wide: Boolean) {
    val weekStart = vm.focusDay.toDate().startOfWeek(s.weekStart).epoch()
    val days = (0 until 7).map { weekStart + it }
    val label = weekStart.toDate().format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())) + " – " +
        (weekStart + 6).toDate().format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault()))
    Column(Modifier.fillMaxSize()) {
        DateNavBar(label, onPrev = { vm.focusDay -= 7 }, onNext = { vm.focusDay += 7 }, onToday = { vm.focusDay = today().epoch() })
        if (wide) {
            Row(Modifier.fillMaxSize().padding(horizontal = 8.dp)) {
                days.forEach { d ->
                    val key = "day:$d"
                    val list = tasksOn(data, d).sortedWith(compareBy<Task>({ it.done }).then(dayOrder))
                    val state = rememberLazyListState()
                    DropHighlight(dnd.hoverKey == key, Modifier.weight(1f).fillMaxHeight()) {
                        LazyColumn(Modifier.fillMaxSize().dropTarget(dnd, key).dragAutoScroll(dnd, state), state = state, contentPadding = ListPadding) {
                            item { WeekColumnHeader(d, list.count { !it.done }) { vm.showQuickAdd("", Task(date = d, gtd = Gtd.NEXT)) } }
                            items(list, key = { it.id }) { t ->
                                TaskRow(t, data, { vm.toggleDone(t) }, Modifier.draggableTask(dnd, t, key) { vm.openTask(t.id) }, showDate = false, dense = true)
                            }
                        }
                    }
                }
            }
        } else {
            val state = rememberLazyListState()
            LazyColumn(Modifier.fillMaxSize().dragAutoScroll(dnd, state), state = state, contentPadding = ListPadding) {
                days.forEach { d ->
                    item(d) {
                        val key = "day:$d"
                        val list = tasksOn(data, d).sortedWith(compareBy<Task>({ it.done }).then(dayOrder))
                        DropHighlight(dnd.hoverKey == key, Modifier.padding(horizontal = 6.dp)) {
                            Column(Modifier.fillMaxWidth().dropTarget(dnd, key).padding(bottom = 6.dp)) {
                                WeekDayHeader(d, list.count { !it.done }) { vm.showQuickAdd("", Task(date = d, gtd = Gtd.NEXT)) }
                                list.forEach { t ->
                                    TaskRow(t, data, { vm.toggleDone(t) }, Modifier.draggableTask(dnd, t, key) { vm.openTask(t.id) }, showDate = false)
                                }
                                if (list.isEmpty()) Text(
                                    stringResource(R.string.drop_here), style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(start = 16.dp, bottom = 8.dp),
                                )
                                HorizontalDivider(Modifier.padding(horizontal = 10.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WeekColumnHeader(day: Long, count: Int, onAdd: () -> Unit) {
    val d = day.toDate()
    val isToday = d == today()
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onAdd).padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()).uppercase(Locale.getDefault()),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
            color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
        )
        Text(
            "${d.dayOfMonth}",
            style = MaterialTheme.typography.headlineSmall,
            color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
        Text(
            if (count > 0) "$count" else "+",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
        )
        HorizontalDivider(
            Modifier.padding(top = 6.dp, start = 8.dp, end = 8.dp),
            thickness = 2.dp,
            color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
        )
    }
}

@Composable
private fun WeekDayHeader(day: Long, count: Int, onAdd: () -> Unit) {
    val d = day.toDate()
    val isToday = d == today()
    Row(Modifier.fillMaxWidth().padding(start = 12.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            d.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault()),
            style = MaterialTheme.typography.titleMedium,
            color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.width(8.dp))
        Text(d.format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        if (count > 0) {
            Spacer(Modifier.width(8.dp))
            Text("$count", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
        }
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onAdd) { Icon(Icons.Outlined.Add, stringResource(R.string.add_task), tint = MaterialTheme.colorScheme.outline) }
    }
}

// ---------------- TeuxDeux-style planner ----------------

@Composable
fun InlineAdd(onAdd: (String) -> Unit, modifier: Modifier = Modifier) {
    var text by remember { mutableStateOf("") }
    BasicTextField(
        value = text,
        onValueChange = { text = it },
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { if (text.isNotBlank()) { onAdd(text); text = "" } }),
        decorationBox = { inner ->
            Box {
                if (text.isEmpty()) Text("+ " + stringResource(R.string.add_task), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outlineVariant)
                inner()
            }
        },
    )
}

@Composable
private fun PlannerItem(task: Task, vm: MainViewModel, modifier: Modifier) {
    Row(modifier.fillMaxWidth().padding(start = 2.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        TaskCheck(task.done, task.priority, { vm.toggleDone(task) }, size = 16.dp)
        Text(
            task.title,
            style = MaterialTheme.typography.bodyMedium,
            textDecoration = if (task.done) TextDecoration.LineThrough else null,
            color = if (task.done) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface,
            maxLines = 3, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(vertical = 6.dp),
        )
    }
    HorizontalDivider(Modifier.padding(horizontal = 10.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
}

@Composable
fun PlannerScreen(vm: MainViewModel, s: Settings, data: AppData, dnd: DragDropState, wide: Boolean) {
    val base = today().epoch() - 14
    val count = 180
    val dayState = rememberLazyListState(initialFirstVisibleItemIndex = (vm.focusDay - base).toInt().coerceIn(0, count - 1))
    val listState = rememberLazyListState()
    val colors = LocalTaskerColors.current
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val colW = if (wide) 230.dp else (maxWidth - 8.dp) / 2
        val totalH = maxHeight
        Column(Modifier.fillMaxSize()) {
            LazyRow(
                Modifier.fillMaxWidth().height(totalH * 0.6f).dragAutoScroll(dnd, dayState, vertical = false),
                state = dayState,
            ) {
                items(count, key = { base + it }) { i ->
                    val d = base + i
                    val date = d.toDate()
                    val key = "day:$d"
                    val list = tasksOn(data, d).sortedWith(compareBy<Task>({ it.done }).then(dayOrder))
                    val isToday = date == today()
                    val past = date.isBefore(today())
                    DropHighlight(dnd.hoverKey == key, Modifier.width(colW).fillMaxHeight(), RoundedCornerShape(0.dp)) {
                        Column(Modifier.fillMaxSize().dropTarget(dnd, key).alpha(if (past) 0.6f else 1f)) {
                            Column(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault()).uppercase(Locale.getDefault()),
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Black, letterSpacing = 1.5.sp),
                                    color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    date.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())).uppercase(Locale.getDefault()),
                                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline,
                                )
                            }
                            HorizontalDivider(Modifier.padding(horizontal = 10.dp), thickness = 2.dp, color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                                list.forEach { t -> PlannerItem(t, vm, Modifier.draggableTask(dnd, t, key) { vm.openTask(t.id) }) }
                                InlineAdd({ vm.quickAdd(it, Task(date = d, gtd = Gtd.NEXT)) })
                            }
                        }
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer).padding(horizontal = 12.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.someday_lists), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline, modifier = Modifier.weight(1f))
                IconButton(onClick = { vm.push(com.tasker.app.ui.Route.Manage) }) { Icon(Icons.Outlined.Add, stringResource(R.string.new_project), Modifier.size(18.dp)) }
            }
            val lists = listOf<Pair<String?, String>>(null to stringResource(R.string.inbox)) + data.projects.filter { !it.archived }.map { it.id to it.name }
            LazyRow(
                Modifier.fillMaxWidth().weight(1f).background(MaterialTheme.colorScheme.surfaceContainer).dragAutoScroll(dnd, listState, vertical = false),
                state = listState,
            ) {
                items(lists, key = { it.first ?: "none" }) { (pid, name) ->
                    val key = "project:${pid ?: "none"}"
                    val list = data.open.filter { it.date == null && it.projectId == pid && (pid != null || it.gtd != Gtd.REFERENCE) }
                    val color = pid?.let { data.projectById[it]?.color }?.let { Color(it) } ?: MaterialTheme.colorScheme.outline
                    DropHighlight(dnd.hoverKey == key, Modifier.width(colW).fillMaxHeight(), RoundedCornerShape(0.dp)) {
                        Column(Modifier.fillMaxSize().dropTarget(dnd, key)) {
                            Text(
                                name.uppercase(Locale.getDefault()),
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Black, letterSpacing = 1.2.sp),
                                color = color,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)
                                    .clickable(enabled = pid != null) { pid?.let { vm.navigateRoot(com.tasker.app.ui.Route.ProjectRoute(it)) } },
                            )
                            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                                list.forEach { t -> PlannerItem(t, vm, Modifier.draggableTask(dnd, t, key) { vm.openTask(t.id) }) }
                                InlineAdd({ vm.quickAdd(it, Task(projectId = pid, gtd = if (pid == null) Gtd.INBOX else Gtd.NEXT)) })
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------------- Calendar ----------------

@Composable
fun CalendarScreen(vm: MainViewModel, s: Settings, data: AppData, dnd: DragDropState) {
    var month by remember { mutableStateOf(YearMonth.from(vm.focusDay.toDate())) }
    val selected = vm.focusDay
    val first = month.atDay(1).startOfWeek(s.weekStart)
    val byDay = remember(data.tasks) { data.tasks.filter { it.date != null }.groupBy { it.date!! } }
    val colors = LocalTaskerColors.current
    val listState = rememberLazyListState()
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 700.dp
        val cellH = if (wide) 96.dp else 58.dp
        val grid: @Composable (Modifier) -> Unit = { mod ->
            Column(mod) {
                DateNavBar(
                    month.month.getDisplayName(TextStyle.FULL_STANDALONE, Locale.getDefault()) + " " + month.year,
                    onPrev = { month = month.minusMonths(1) }, onNext = { month = month.plusMonths(1) },
                    onToday = { month = YearMonth.now(); vm.focusDay = today().epoch() },
                )
                Row(Modifier.padding(horizontal = 6.dp)) {
                    repeat(7) { i ->
                        Text(
                            weekdayShort(first.plusDays(i.toLong())), style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline, modifier = Modifier.weight(1f).padding(vertical = 4.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                    }
                }
                repeat(6) { w ->
                    Row(Modifier.padding(horizontal = 6.dp)) {
                        repeat(7) { i ->
                            val date = first.plusDays((w * 7 + i).toLong())
                            val d = date.epoch()
                            val key = "day:$d"
                            val inMonth = YearMonth.from(date) == month
                            val dayTasks = byDay[d].orEmpty().filter { !it.done }
                            val isSel = d == selected
                            val isToday = date == today()
                            Column(
                                Modifier.weight(1f).height(cellH).padding(1.dp).clip(RoundedCornerShape(8.dp))
                                    .background(
                                        when {
                                            dnd.hoverKey == key -> MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                            isSel -> MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                                            else -> Color.Transparent
                                        }
                                    )
                                    .dropTarget(dnd, key)
                                    .clickable { vm.focusDay = d; if (!inMonth) month = YearMonth.from(date) }
                                    .alpha(if (inMonth) 1f else 0.4f)
                                    .padding(3.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Box(
                                    Modifier.size(24.dp).clip(CircleShape).background(if (isToday) MaterialTheme.colorScheme.primary else Color.Transparent),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        "${date.dayOfMonth}", style = MaterialTheme.typography.labelMedium,
                                        color = if (isToday) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                                if (wide) {
                                    dayTasks.take(3).forEach { t ->
                                        val c = t.projectId?.let { data.projectById[it]?.color }?.let { Color(it) } ?: MaterialTheme.colorScheme.primary
                                        Text(
                                            t.title, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.fillMaxWidth().padding(top = 1.dp).clip(RoundedCornerShape(3.dp))
                                                .background(c.copy(alpha = 0.18f)).padding(horizontal = 3.dp),
                                        )
                                    }
                                    if (dayTasks.size > 3) Text("+${dayTasks.size - 3}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                } else if (dayTasks.isNotEmpty()) {
                                    Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                        dayTasks.take(4).forEach { t ->
                                            val c = t.projectId?.let { data.projectById[it]?.color }?.let { Color(it) } ?: MaterialTheme.colorScheme.primary
                                            Box(Modifier.size(5.dp).clip(CircleShape).background(c))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        val dayList: @Composable (Modifier) -> Unit = { mod ->
            val key = "day:$selected"
            val list = byDay[selected].orEmpty().sortedWith(compareBy<Task>({ it.done }).then(dayOrder))
            LazyColumn(mod.dropTarget(dnd, key).dragAutoScroll(dnd, listState), state = listState, contentPadding = ListPadding) {
                item { SectionHeader(dayLabel(selected, long = true), count = list.count { !it.done }) }
                if (list.isEmpty()) item { EmptyState(stringResource(R.string.nothing_planned), icon = Icons.Outlined.EventAvailable) }
                items(list, key = { it.id }) { t ->
                    TaskRow(t, data, { vm.toggleDone(t) }, Modifier.draggableTask(dnd, t, key) { vm.openTask(t.id) }, showDate = false, onStar = { vm.toggleTop3(t) })
                }
            }
        }
        if (wide) Row(Modifier.fillMaxSize()) {
            grid(Modifier.weight(1.6f).verticalScroll(rememberScrollState()))
            VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            dayList(Modifier.weight(1f).fillMaxHeight())
        } else Column(Modifier.fillMaxSize()) {
            grid(Modifier)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            dayList(Modifier.weight(1f))
        }
    }
}
