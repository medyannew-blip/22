package com.tasker.app.ui.components

import android.text.format.DateFormat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.NextWeek
import androidx.compose.material.icons.outlined.Weekend
import androidx.compose.material.icons.outlined.WbTwilight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.tasker.app.R
import com.tasker.app.data.Gtd
import com.tasker.app.data.Task
import com.tasker.app.domain.NlpParser
import com.tasker.app.domain.epoch
import com.tasker.app.domain.minutes
import com.tasker.app.domain.today
import com.tasker.app.ui.AppData
import com.tasker.app.ui.MainViewModel
import java.time.DayOfWeek
import java.time.temporal.TemporalAdjusters

@Composable
fun DatePickerDialogM(initial: Long?, onPick: (Long) -> Unit, onDismiss: () -> Unit) {
    val state = rememberDatePickerState(initialSelectedDateMillis = (initial ?: today().epoch()) * 86_400_000L)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { onPick(Math.floorDiv(it, 86_400_000L)) }
                onDismiss()
            }) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    ) { DatePicker(state = state) }
}

@Composable
fun TimePickerDialogM(initial: Int?, onPick: (Int) -> Unit, onDismiss: () -> Unit, onClear: (() -> Unit)? = null) {
    val ctx = LocalContext.current
    val now = java.time.LocalTime.now()
    val state = rememberTimePickerState(
        initialHour = initial?.div(60) ?: (now.hour + 1).coerceAtMost(23),
        initialMinute = initial?.rem(60) ?: 0,
        is24Hour = DateFormat.is24HourFormat(ctx),
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onPick(state.hour * 60 + state.minute); onDismiss() }) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = {
            Row {
                if (onClear != null) TextButton(onClick = { onClear(); onDismiss() }) { Text(stringResource(R.string.clear)) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        },
        text = { TimePicker(state = state) },
    )
}

/** Quick date choices shared by the reschedule sheet and the task editor. */
@Composable
fun QuickDateOptions(onPick: (Long?) -> Unit, onCustom: () -> Unit) {
    val t = today()
    val weekend = if (t.dayOfWeek == DayOfWeek.SATURDAY) t else t.with(TemporalAdjusters.next(DayOfWeek.SATURDAY))
    val nextWeek = t.with(TemporalAdjusters.next(DayOfWeek.MONDAY))
    val options = listOf(
        Triple(Icons.Outlined.LightMode, stringResource(R.string.today), t.epoch()),
        Triple(Icons.Outlined.WbTwilight, stringResource(R.string.tomorrow), t.plusDays(1).epoch()),
        Triple(Icons.Outlined.Weekend, stringResource(R.string.this_weekend), weekend.epoch()),
        Triple(Icons.Outlined.NextWeek, stringResource(R.string.next_week), nextWeek.epoch()),
        Triple(Icons.Outlined.Inventory2, stringResource(R.string.someday_no_date), null),
    )
    Column {
        options.forEach { (icon, label, day) ->
            ListItem(
                headlineContent = { Text(label) },
                leadingContent = { Icon(icon, null) },
                trailingContent = { if (day != null && day > t.epoch() + 1) Text(dayLabel(day, false), color = MaterialTheme.colorScheme.outline) },
                modifier = Modifier.fillMaxWidth().clickable { onPick(day) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
        }
        ListItem(
            headlineContent = { Text(stringResource(R.string.pick_date)) },
            leadingContent = { Icon(Icons.Outlined.CalendarMonth, null) },
            modifier = Modifier.fillMaxWidth().clickable(onClick = onCustom),
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        )
    }
}

@Composable
fun RescheduleSheet(task: Task, onPick: (Long?) -> Unit, onDismiss: () -> Unit) {
    var custom by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
            Text(
                stringResource(R.string.reschedule),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            Text(
                task.title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                maxLines = 2,
            )
            QuickDateOptions(onPick = { onPick(it); onDismiss() }, onCustom = { custom = true })
        }
    }
    if (custom) DatePickerDialogM(task.date, onPick = { onPick(it); onDismiss() }, onDismiss = { custom = false })
}

/**
 * Quick add with natural language. Parsed attributes are previewed live as chips
 * so the user can see what Tasker understood before saving.
 */
@Composable
fun QuickAddSheet(vm: MainViewModel, data: AppData, initial: String, defaults: Task, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(initial) }
    var priority by remember { mutableStateOf(defaults.priority) }
    var projectId by remember { mutableStateOf(defaults.projectId) }
    var date by remember { mutableStateOf(defaults.date) }
    var dateMenu by remember { mutableStateOf(false) }
    var customDate by remember { mutableStateOf(false) }
    var projectMenu by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val parsed = remember(text) { if (text.isBlank()) null else NlpParser.parse(text, usDates = vm.usDates()) }
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    fun submit() {
        if (text.isBlank()) return
        vm.quickAdd(text, defaults.copy(priority = priority, projectId = projectId, date = date))
        text = ""
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, dragHandle = null) {
        Column(Modifier.imePadding().navigationBarsPadding().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.weight(1f).focusRequester(focus),
                    placeholder = { Text(stringResource(R.string.quick_add_hint)) },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                    textStyle = MaterialTheme.typography.titleMedium,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                )
                FilledIconButton(onClick = { submit() }, enabled = text.isNotBlank()) {
                    Icon(Icons.AutoMirrored.Rounded.Send, stringResource(R.string.add))
                }
            }
            // Live preview of what the parser understood
            if (parsed != null) {
                FlowRow(
                    Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    val chips = buildList {
                        parsed.date?.let { d ->
                            add("📅 " + dayLabelPlain(d.epoch(), vm) + (parsed.time?.let { " " + timeLabel(it.minutes()) } ?: "") +
                                (parsed.endTime?.let { "–" + timeLabel(it.minutes()) } ?: "") +
                                (parsed.endDate?.takeIf { it != parsed.date }?.let { " → " + dayLabelPlain(it.epoch(), vm) } ?: ""))
                        }
                        parsed.durationMin?.let { add("⏱ ${it}m") }
                        parsed.recurrence?.let { add("🔁 " + it.lowercase().replace(":", " ")) }
                        parsed.project?.let { add("📁 $it") }
                        parsed.tags.forEach { add(if (it.startsWith("@")) it else "#$it") }
                        parsed.priority?.let { add("⚑ P${4 - it}") }
                        if (parsed.top3) add("★ Top 3")
                        parsed.reminderOffsetMin?.let { add("🔔 −${it}m") }
                        parsed.reminderTime?.let { add("🔔 " + timeLabel(it.minutes())) }
                        parsed.gtd?.let { g -> add("◇ " + gtdName(g, vm)) }
                        parsed.waitingFor?.let { add("⏳ $it") }
                    }
                    chips.forEach { label ->
                        SuggestionChip(onClick = {}, label = { Text(label, style = MaterialTheme.typography.labelMedium) })
                    }
                }
            } else {
                Text(
                    stringResource(R.string.quick_add_example),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            HorizontalDivider(Modifier.padding(vertical = 6.dp), color = MaterialTheme.colorScheme.outlineVariant)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Column {
                    AssistChip(
                        onClick = { dateMenu = true },
                        label = { Text(date?.let { dayLabel(it) } ?: stringResource(R.string.no_date)) },
                        leadingIcon = { Icon(Icons.Outlined.CalendarMonth, null, Modifier.size(18.dp)) },
                    )
                    DropdownMenu(expanded = dateMenu, onDismissRequest = { dateMenu = false }) {
                        listOf(
                            stringResource(R.string.today) to today().epoch(),
                            stringResource(R.string.tomorrow) to today().epoch() + 1,
                            stringResource(R.string.next_week) to today().with(TemporalAdjusters.next(DayOfWeek.MONDAY)).epoch(),
                        ).forEach { (label, d) -> DropdownMenuItem(text = { Text(label) }, onClick = { date = d; dateMenu = false }) }
                        DropdownMenuItem(text = { Text(stringResource(R.string.no_date)) }, onClick = { date = null; dateMenu = false }, leadingIcon = { Icon(Icons.Outlined.EventBusy, null) })
                        DropdownMenuItem(text = { Text(stringResource(R.string.pick_date)) }, onClick = { customDate = true; dateMenu = false })
                    }
                }
                Column {
                    AssistChip(
                        onClick = { projectMenu = true },
                        label = { Text(projectId?.let { data.projectById[it]?.name } ?: stringResource(R.string.inbox), maxLines = 1) },
                        leadingIcon = { Icon(Icons.Outlined.Folder, null, Modifier.size(18.dp)) },
                    )
                    DropdownMenu(expanded = projectMenu, onDismissRequest = { projectMenu = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.inbox)) }, onClick = { projectId = null; projectMenu = false })
                        data.projects.filter { !it.archived }.forEach { p ->
                            DropdownMenuItem(
                                text = { Text(p.name) },
                                leadingIcon = { ColorDot(Color(p.color), 10.dp) },
                                onClick = { projectId = p.id; projectMenu = false },
                            )
                        }
                    }
                }
                IconButton(onClick = { priority = (priority + 1) % 4 }) {
                    Icon(Icons.Outlined.Flag, stringResource(R.string.priority), tint = priorityColor(priority))
                }
            }
            Spacer(Modifier.height(4.dp))
        }
    }
    if (customDate) DatePickerDialogM(date, onPick = { date = it }, onDismiss = { customDate = false })
}

private fun dayLabelPlain(epochDay: Long, vm: MainViewModel): String {
    val ctx = vm.getApplication<android.app.Application>()
    val t = today().epoch()
    return when (epochDay) {
        t -> ctx.getString(R.string.today)
        t + 1 -> ctx.getString(R.string.tomorrow)
        else -> java.time.LocalDate.ofEpochDay(epochDay).format(
            java.time.format.DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.MEDIUM)
        )
    }
}

fun gtdName(g: Int, vm: MainViewModel): String {
    val ctx = vm.getApplication<android.app.Application>()
    return ctx.getString(
        when (g) {
            Gtd.INBOX -> R.string.inbox
            Gtd.NEXT -> R.string.gtd_next
            Gtd.WAITING -> R.string.gtd_waiting
            Gtd.SOMEDAY -> R.string.gtd_someday
            else -> R.string.gtd_reference
        }
    )
}
