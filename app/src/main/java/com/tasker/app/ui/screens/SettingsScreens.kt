package com.tasker.app.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import com.tasker.app.BuildConfig
import com.tasker.app.R
import com.tasker.app.data.CustomTheme
import com.tasker.app.data.Folder
import com.tasker.app.data.Project
import com.tasker.app.data.Repository
import com.tasker.app.data.Settings
import com.tasker.app.data.SyncMode
import com.tasker.app.data.Tag
import com.tasker.app.ui.AppData
import com.tasker.app.ui.MainViewModel
import com.tasker.app.ui.Route
import com.tasker.app.ui.components.ColorDot
import com.tasker.app.ui.components.SectionHeader
import com.tasker.app.ui.theme.ThemeSpec
import com.tasker.app.ui.theme.Themes
import com.tasker.app.ui.theme.colorScheme
import java.text.DateFormat
import java.util.Date

@Composable
private fun SettingSwitch(title: String, subtitle: String? = null, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = subtitle?.let { { Text(it) } },
        trailingContent = { Switch(checked = checked, onCheckedChange = onChange) },
        modifier = Modifier.clickable { onChange(!checked) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

@Composable
private fun SettingLink(title: String, subtitle: String? = null, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = subtitle?.let { { Text(it) } },
        trailingContent = { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null) },
        modifier = Modifier.clickable(onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

@Composable
private fun <T> SettingChoice(title: String, options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        ListItem(
            headlineContent = { Text(title) },
            supportingContent = { Text(options.firstOrNull { it.first == selected }?.second ?: "") },
            modifier = Modifier.clickable { open = true },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        )
        DropdownMenu(open, { open = false }) {
            options.forEach { (v, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = { onSelect(v); open = false },
                    leadingIcon = { if (v == selected) Icon(Icons.Outlined.Check, null) },
                )
            }
        }
    }
}

@Composable
fun ThemeSwatch(spec: ThemeSpec, selected: Boolean, onClick: () -> Unit, label: String = spec.name) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(84.dp).clickable(onClick = onClick).padding(4.dp)) {
        Box(
            Modifier.size(width = 76.dp, height = 56.dp).clip(RoundedCornerShape(14.dp)).background(spec.background)
                .border(if (selected) 2.5.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
                .padding(8.dp)
        ) {
            Column {
                Box(Modifier.size(width = 40.dp, height = 6.dp).clip(RoundedCornerShape(3.dp)).background(spec.text.copy(alpha = 0.85f)))
                Spacer(Modifier.height(5.dp))
                Box(Modifier.size(width = 28.dp, height = 5.dp).clip(RoundedCornerShape(3.dp)).background(spec.text.copy(alpha = 0.4f)))
                Spacer(Modifier.height(5.dp))
                Row {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(spec.primary))
                    Spacer(Modifier.width(4.dp))
                    Box(Modifier.size(10.dp).clip(CircleShape).background(spec.accent))
                    Spacer(Modifier.width(4.dp))
                    Box(Modifier.size(10.dp).clip(CircleShape).background(spec.surface))
                }
            }
            if (selected) Icon(Icons.Outlined.Check, null, tint = spec.primary, modifier = Modifier.align(Alignment.TopEnd).size(16.dp))
        }
        Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
fun SettingsScreen(vm: MainViewModel, s: Settings) {
    val ctx = LocalContext.current
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> uri?.let { vm.exportTo(it) } }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { vm.importFrom(it) } }
    val alarms = vm.alarms

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 48.dp)) {
        SectionHeader(stringResource(R.string.appearance))
        Text(stringResource(R.string.light_themes), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(start = 16.dp, top = 4.dp))
        FlowRow(Modifier.padding(horizontal = 10.dp)) {
            Themes.presets.filter { !it.dark }.forEach { spec ->
                ThemeSwatch(spec, !s.followSystem && s.themeId == spec.id, { vm.updateSettings { it.copy(themeId = spec.id, followSystem = false) } })
            }
        }
        Text(stringResource(R.string.dark_themes), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(start = 16.dp, top = 8.dp))
        FlowRow(Modifier.padding(horizontal = 10.dp)) {
            Themes.presets.filter { it.dark }.forEach { spec ->
                ThemeSwatch(spec, !s.followSystem && s.themeId == spec.id, { vm.updateSettings { it.copy(themeId = spec.id, followSystem = false) } })
            }
            ThemeSwatch(Themes.custom(s.customTheme), !s.followSystem && s.themeId == Themes.CUSTOM_ID, {
                vm.updateSettings { it.copy(themeId = Themes.CUSTOM_ID, followSystem = false) }
            }, label = stringResource(R.string.custom_theme))
        }
        SettingLink(stringResource(R.string.custom_theme), stringResource(R.string.custom_theme_sub)) { vm.push(Route.ThemeEditor) }
        SettingSwitch(stringResource(R.string.follow_system), stringResource(R.string.follow_system_sub), s.followSystem) { v -> vm.updateSettings { it.copy(followSystem = v) } }
        if (s.followSystem) {
            val all = Themes.presets.map { it.id to it.name } + (Themes.CUSTOM_ID to stringResource(R.string.custom_theme))
            SettingChoice(stringResource(R.string.light_theme), all, s.lightThemeId) { v -> vm.updateSettings { it.copy(lightThemeId = v) } }
            SettingChoice(stringResource(R.string.dark_theme), all, s.darkThemeId) { v -> vm.updateSettings { it.copy(darkThemeId = v) } }
        }

        SectionHeader(stringResource(R.string.language_region))
        SettingChoice(
            stringResource(R.string.language),
            listOf("" to stringResource(R.string.lang_system), "en" to "English", "ar" to "العربية", "he" to "עברית", "fa" to "فارسی"),
            s.language,
        ) { tag ->
            vm.updateSettings { it.copy(language = tag) }
            AppCompatDelegate.setApplicationLocales(if (tag.isEmpty()) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(tag))
        }
        SettingSwitch(stringResource(R.string.force_rtl), stringResource(R.string.force_rtl_sub), s.forceRtl) { v -> vm.updateSettings { it.copy(forceRtl = v) } }
        SettingChoice(
            stringResource(R.string.week_starts),
            listOf(6 to stringResource(R.string.saturday), 7 to stringResource(R.string.sunday), 1 to stringResource(R.string.monday)),
            s.weekStart,
        ) { v -> vm.updateSettings { it.copy(weekStart = v) } }
        SettingChoice(
            stringResource(R.string.start_screen),
            listOf(
                "today" to stringResource(R.string.today), "inbox" to stringResource(R.string.inbox), "daily" to stringResource(R.string.view_daily),
                "three" to stringResource(R.string.view_three_day), "weekly" to stringResource(R.string.view_weekly),
                "teuxdeux" to stringResource(R.string.view_planner), "calendar" to stringResource(R.string.view_calendar),
                "kanban" to stringResource(R.string.view_kanban), "eisenhower" to stringResource(R.string.view_eisenhower), "gtd" to stringResource(R.string.view_gtd),
            ),
            s.startScreen,
        ) { v -> vm.updateSettings { it.copy(startScreen = v) } }

        SectionHeader(stringResource(R.string.notifications))
        SettingSwitch(stringResource(R.string.nearly_due_alerts), stringResource(R.string.nearly_due_alerts_sub), s.nearlyDueAlerts) { v -> vm.updateSettings { it.copy(nearlyDueAlerts = v) } }
        if (s.nearlyDueAlerts) {
            SettingChoice(
                stringResource(R.string.alert_before),
                listOf(5, 10, 15, 30, 60, 120, 240).map { it to stringResource(R.string.minutes_before, it) },
                s.nearlyDueLeadMin,
            ) { v -> vm.updateSettings { it.copy(nearlyDueLeadMin = v) } }
        }
        SettingSwitch(stringResource(R.string.overdue_alerts), stringResource(R.string.overdue_alerts_sub), s.overdueAlerts) { v -> vm.updateSettings { it.copy(overdueAlerts = v) } }
        SettingSwitch(stringResource(R.string.morning_summary), stringResource(R.string.morning_summary_sub), s.morningSummary) { v -> vm.updateSettings { it.copy(morningSummary = v) } }
        if (s.morningSummary) {
            SettingChoice(
                stringResource(R.string.summary_time),
                (5..11).map { it to com.tasker.app.ui.components.timeLabel(it * 60) },
                s.summaryHour,
            ) { v -> vm.updateSettings { it.copy(summaryHour = v) } }
        }
        SettingChoice(
            stringResource(R.string.default_reminder),
            listOf(0 to stringResource(R.string.none), 5 to stringResource(R.string.minutes_before, 5), 10 to stringResource(R.string.minutes_before, 10),
                30 to stringResource(R.string.minutes_before, 30), 60 to stringResource(R.string.minutes_before, 60)),
            s.defaultReminderMin,
        ) { v -> vm.updateSettings { it.copy(defaultReminderMin = v) } }
        if (Build.VERSION.SDK_INT >= 31 && !alarms.canExact()) {
            SettingLink(stringResource(R.string.exact_alarms), stringResource(R.string.exact_alarms_sub)) {
                runCatching { ctx.startActivity(Intent(AndroidSettings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + ctx.packageName))) }
            }
        }
        SettingLink(stringResource(R.string.notification_settings)) {
            runCatching {
                ctx.startActivity(Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(AndroidSettings.EXTRA_APP_PACKAGE, ctx.packageName))
            }
        }

        SectionHeader(stringResource(R.string.sync_and_data))
        SettingLink(
            stringResource(R.string.sync),
            when (s.syncMode) {
                SyncMode.FILE -> stringResource(R.string.sync_mode_file)
                SyncMode.FIREBASE -> stringResource(R.string.sync_mode_firebase)
                else -> stringResource(R.string.sync_off)
            },
        ) { vm.push(Route.SyncSettings) }
        SettingLink(stringResource(R.string.export_backup)) { exportLauncher.launch("tasker-backup.json") }
        SettingLink(stringResource(R.string.import_backup)) { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) }
        SettingSwitch(stringResource(R.string.show_completed), null, s.showCompleted) { v -> vm.updateSettings { it.copy(showCompleted = v) } }

        SectionHeader(stringResource(R.string.about))
        ListItem(
            headlineContent = { Text(stringResource(R.string.app_name) + " " + BuildConfig.VERSION_NAME) },
            supportingContent = { Text(stringResource(R.string.about_sub)) },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        )
    }
}

// ---------------- Theme editor ----------------

@Composable
fun ThemeEditorScreen(vm: MainViewModel, s: Settings) {
    var t by remember { mutableStateOf(s.customTheme) }
    var editing by remember { mutableStateOf<String?>(null) }
    val spec = Themes.custom(t)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        // Preview
        val scheme = spec.colorScheme()
        Surface(shape = RoundedCornerShape(20.dp), color = scheme.background, border = androidx.compose.foundation.BorderStroke(1.dp, scheme.outlineVariant)) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Text(stringResource(R.string.today), style = MaterialTheme.typography.headlineSmall, color = scheme.onBackground)
                Spacer(Modifier.height(10.dp))
                listOf(stringResource(R.string.preview_task_1), stringResource(R.string.preview_task_2)).forEachIndexed { i, label ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 6.dp)) {
                        Box(
                            Modifier.size(20.dp).clip(CircleShape).background(if (i == 1) scheme.primary else Color.Transparent)
                                .border(1.5.dp, scheme.primary, CircleShape)
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(label, color = if (i == 1) scheme.onSurfaceVariant else scheme.onBackground)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.clip(RoundedCornerShape(10.dp)).background(scheme.surfaceContainer).padding(horizontal = 12.dp, vertical = 8.dp)) {
                        Text("#tag", color = spec.accent)
                    }
                    Box(Modifier.clip(CircleShape).background(scheme.primary).padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Text("+", color = scheme.onPrimary, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(t.name, { t = t.copy(name = it) }, label = { Text(stringResource(R.string.theme_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        SettingSwitch(stringResource(R.string.dark_theme), null, t.dark) { t = t.copy(dark = it) }
        Text(stringResource(R.string.start_from_preset), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Themes.presets.forEach { p ->
                FilterChip(selected = false, onClick = {
                    t = CustomTheme(t.name, p.dark, p.primary.toArgb(), p.background.toArgb(), p.surface.toArgb(), p.text.toArgb(), p.accent.toArgb())
                }, label = { Text(p.name) }, leadingIcon = { ColorDot(p.primary, 10.dp) })
            }
        }
        Spacer(Modifier.height(8.dp))
        listOf(
            "primary" to R.string.color_primary, "accent" to R.string.color_accent, "background" to R.string.color_background,
            "surface" to R.string.color_surface, "text" to R.string.color_text,
        ).forEach { (key, label) ->
            val c = Color(
                when (key) {
                    "primary" -> t.primary; "accent" -> t.accent; "background" -> t.background; "surface" -> t.surface; else -> t.text
                }
            )
            ListItem(
                headlineContent = { Text(stringResource(label)) },
                supportingContent = { Text("#" + Integer.toHexString(c.toArgb()).uppercase().takeLast(6)) },
                leadingContent = { Box(Modifier.size(32.dp).clip(CircleShape).background(c).border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)) },
                trailingContent = { Icon(Icons.Outlined.Edit, null) },
                modifier = Modifier.clickable { editing = key },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
        }
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = { vm.updateSettings { it.copy(customTheme = t, themeId = Themes.CUSTOM_ID, followSystem = false) }; vm.back() },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.use_theme)) }
    }
    editing?.let { key ->
        val initial = Color(
            when (key) {
                "primary" -> t.primary; "accent" -> t.accent; "background" -> t.background; "surface" -> t.surface; else -> t.text
            }
        )
        ColorPickerDialog(initial, onDismiss = { editing = null }) { c ->
            val v = c.toArgb()
            t = when (key) {
                "primary" -> t.copy(primary = v)
                "accent" -> t.copy(accent = v)
                "background" -> t.copy(background = v)
                "surface" -> t.copy(surface = v)
                else -> t.copy(text = v)
            }
        }
    }
}

@Composable
fun ColorPickerDialog(initial: Color, onDismiss: () -> Unit, onPick: (Color) -> Unit) {
    val hsv = remember {
        FloatArray(3).also { android.graphics.Color.colorToHSV(initial.toArgb(), it) }
    }
    var h by remember { mutableFloatStateOf(hsv[0]) }
    var sat by remember { mutableFloatStateOf(hsv[1]) }
    var v by remember { mutableFloatStateOf(hsv[2]) }
    var hex by remember { mutableStateOf("") }
    val color = Color(android.graphics.Color.HSVToColor(floatArrayOf(h, sat, v)))
    val palette = listOf(
        0xFFFFFFFF, 0xFFF7F3EC, 0xFFF2F7FC, 0xFF1C1C1E, 0xFF000000, 0xFF161618, 0xFF0F1724, 0xFF282A36,
        0xFFE5484D, 0xFFF76B15, 0xFFFFC53D, 0xFF30A46C, 0xFF12A594, 0xFF0090FF, 0xFF3E63DD, 0xFF8E4EC6, 0xFFD6409F,
    ).map { Color(it) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { onPick(color); onDismiss() }) { Text(stringResource(R.string.ok)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
        text = {
            Column {
                Box(Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(14.dp)).background(color))
                Spacer(Modifier.height(10.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    palette.forEach { c ->
                        Box(
                            Modifier.size(28.dp).clip(CircleShape).background(c).border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                                .clickable {
                                    val out = FloatArray(3); android.graphics.Color.colorToHSV(c.toArgb(), out)
                                    h = out[0]; sat = out[1]; v = out[2]
                                }
                        )
                    }
                }
                Text(stringResource(R.string.hue), style = MaterialTheme.typography.labelSmall)
                Slider(h, { h = it }, valueRange = 0f..360f)
                Text(stringResource(R.string.saturation), style = MaterialTheme.typography.labelSmall)
                Slider(sat, { sat = it })
                Text(stringResource(R.string.brightness), style = MaterialTheme.typography.labelSmall)
                Slider(v, { v = it })
                OutlinedTextField(
                    hex, { input ->
                        hex = input
                        runCatching { android.graphics.Color.parseColor(if (input.startsWith("#")) input else "#$input") }.getOrNull()?.let { argb ->
                            val out = FloatArray(3); android.graphics.Color.colorToHSV(argb, out)
                            h = out[0]; sat = out[1]; v = out[2]
                        }
                    },
                    label = { Text("HEX") }, singleLine = true, placeholder = { Text("#3B82F6") },
                )
            }
        },
    )
}

// ---------------- Sync settings ----------------

@Composable
fun SyncSettingsScreen(vm: MainViewModel, s: Settings) {
    val ctx = LocalContext.current
    val syncState by vm.sync.state.collectAsState()
    fun usePicked(uri: Uri?) {
        uri ?: return
        runCatching {
            ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }
        vm.updateSettings { it.copy(syncFileUri = uri.toString(), syncMode = SyncMode.FILE) }
        vm.syncNow()
    }
    val createFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { usePicked(it) }
    val openFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { usePicked(it) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 48.dp)) {
        Text(
            stringResource(R.string.sync_intro), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp),
        )
        listOf(
            SyncMode.OFF to R.string.sync_off,
            SyncMode.FILE to R.string.sync_mode_file,
            SyncMode.FIREBASE to R.string.sync_mode_firebase,
        ).forEach { (mode, label) ->
            Row(
                Modifier.fillMaxWidth().clickable { vm.updateSettings { it.copy(syncMode = mode) } }.padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = s.syncMode == mode, onClick = { vm.updateSettings { it.copy(syncMode = mode) } })
                Text(stringResource(label))
            }
        }
        HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)

        when (s.syncMode) {
            SyncMode.FILE -> Column(Modifier.padding(horizontal = 16.dp)) {
                Text(stringResource(R.string.sync_file_help), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                if (s.syncFileUri.isNotEmpty()) {
                    Text(
                        stringResource(R.string.sync_file_current, Uri.parse(s.syncFileUri).lastPathSegment ?: s.syncFileUri),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline,
                    )
                    Spacer(Modifier.height(8.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { createFile.launch("tasker-sync.json") }) { Text(stringResource(R.string.sync_file_create)) }
                    OutlinedButton(onClick = { openFile.launch(arrayOf("application/json", "text/plain", "application/octet-stream", "*/*")) }) {
                        Text(stringResource(R.string.sync_file_open))
                    }
                }
            }
            SyncMode.FIREBASE -> FirebaseSection(vm, s)
            else -> {}
        }

        if (s.syncMode != SyncMode.OFF) {
            HorizontalDivider(Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)
            SettingSwitch(stringResource(R.string.notify_on_sync), stringResource(R.string.notify_on_sync_sub), s.notifyOnSync) { v -> vm.updateSettings { it.copy(notifyOnSync = v) } }
            ListItem(
                headlineContent = { Text(stringResource(R.string.last_sync)) },
                supportingContent = {
                    Text(
                        if (s.lastSync == 0L) stringResource(R.string.never)
                        else DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(s.lastSync)),
                    )
                },
                trailingContent = {
                    Button(onClick = { vm.syncNow() }, enabled = !syncState.running) { Text(stringResource(R.string.sync_now)) }
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
            if (s.lastSyncError.isNotEmpty()) Text(
                s.lastSyncError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
    }
}

@Composable
private fun FirebaseSection(vm: MainViewModel, s: Settings) {
    var apiKey by remember { mutableStateOf(s.firebaseApiKey) }
    var appId by remember { mutableStateOf(s.firebaseAppId) }
    var projectId by remember { mutableStateOf(s.firebaseProjectId) }
    var email by remember { mutableStateOf(s.firebaseEmail) }
    var password by remember { mutableStateOf("") }
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.firebase_help), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(projectId, { projectId = it.trim() }, label = { Text(stringResource(R.string.firebase_project_id)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(apiKey, { apiKey = it.trim() }, label = { Text(stringResource(R.string.firebase_api_key)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(appId, { appId = it.trim() }, label = { Text(stringResource(R.string.firebase_app_id)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedButton(onClick = {
            vm.updateSettings { it.copy(firebaseApiKey = apiKey, firebaseAppId = appId, firebaseProjectId = projectId) }
        }) { Text(stringResource(R.string.save)) }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        val signedIn = vm.sync.firebaseUserEmail()
        if (signedIn != null) {
            Text(stringResource(R.string.signed_in_as, signedIn), style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick = { vm.firebaseSignOut() }) { Text(stringResource(R.string.sign_out)) }
        } else {
            OutlinedTextField(email, { email = it }, label = { Text(stringResource(R.string.email)) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
            OutlinedTextField(password, { password = it }, label = { Text(stringResource(R.string.password)) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    vm.updateSettings { it.copy(firebaseApiKey = apiKey, firebaseAppId = appId, firebaseProjectId = projectId) }
                    vm.firebaseAuth(email, password, create = false)
                }, enabled = email.isNotBlank() && password.length >= 6) { Text(stringResource(R.string.sign_in)) }
                OutlinedButton(onClick = {
                    vm.updateSettings { it.copy(firebaseApiKey = apiKey, firebaseAppId = appId, firebaseProjectId = projectId) }
                    vm.firebaseAuth(email, password, create = true)
                }, enabled = email.isNotBlank() && password.length >= 6) { Text(stringResource(R.string.create_account)) }
            }
        }
    }
}

// ---------------- Manage folders, projects and tags ----------------

@Composable
fun ManageScreen(vm: MainViewModel, data: AppData) {
    var editProject by remember { mutableStateOf<Project?>(null) }
    var editFolder by remember { mutableStateOf<Folder?>(null) }
    var editTag by remember { mutableStateOf<Tag?>(null) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 48.dp)) {
        SectionHeader(stringResource(R.string.folders), trailing = {
            TextButton(onClick = { editFolder = Folder(name = "", sortOrder = (data.folders.maxOfOrNull { it.sortOrder } ?: 0.0) + 1) }) { Text("+ " + stringResource(R.string.new_folder)) }
        })
        data.folders.forEach { f ->
            ListItem(
                headlineContent = { Text(f.name) },
                supportingContent = { Text(stringResource(R.string.n_projects, data.projects.count { it.folderId == f.id })) },
                modifier = Modifier.clickable { editFolder = f },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
        }
        SectionHeader(stringResource(R.string.projects), trailing = {
            TextButton(onClick = {
                editProject = Project(name = "", color = Repository.ProjectPalette[data.projects.size % Repository.ProjectPalette.size],
                    sortOrder = (data.projects.maxOfOrNull { it.sortOrder } ?: 0.0) + 1)
            }) { Text("+ " + stringResource(R.string.new_project)) }
        })
        data.projects.forEach { p ->
            ListItem(
                headlineContent = { Text(p.name + if (p.archived) " · " + stringResource(R.string.archived) else "") },
                supportingContent = {
                    Text(
                        (p.folderId?.let { fid -> data.folders.firstOrNull { it.id == fid }?.name }?.let { "$it · " } ?: "") +
                            stringResource(R.string.n_tasks, data.open.count { it.projectId == p.id })
                    )
                },
                leadingContent = { ColorDot(Color(p.color), 14.dp) },
                modifier = Modifier.clickable { editProject = p },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
        }
        SectionHeader(stringResource(R.string.tags), trailing = {
            TextButton(onClick = { editTag = Tag(name = "", color = Repository.TagPalette[data.tags.size % Repository.TagPalette.size]) }) { Text("+ " + stringResource(R.string.new_tag)) }
        })
        Text(stringResource(R.string.tags_help), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(horizontal = 16.dp))
        data.tags.forEach { t ->
            ListItem(
                headlineContent = { Text(if (t.name.startsWith("@")) t.name else "#" + t.name, color = Color(t.color)) },
                supportingContent = { Text(stringResource(R.string.n_tasks, data.open.count { t.id in it.tags })) },
                modifier = Modifier.clickable { editTag = t },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
        }
    }

    editFolder?.let { f ->
        NameDialog(
            title = stringResource(if (f.name.isEmpty()) R.string.new_folder else R.string.edit_folder),
            initial = f.name,
            onDismiss = { editFolder = null },
            onDelete = if (data.folders.any { it.id == f.id }) ({ vm.deleteFolder(f.id); editFolder = null }) else null,
            onSave = { name -> vm.saveFolder(f.copy(name = name)); editFolder = null },
        )
    }
    editTag?.let { t ->
        NameDialog(
            title = stringResource(if (t.name.isEmpty()) R.string.new_tag else R.string.edit_tag),
            initial = t.name, colors = Repository.TagPalette, color = t.color,
            onDismiss = { editTag = null },
            onDelete = if (data.tags.any { it.id == t.id }) ({ vm.deleteTag(t.id); editTag = null }) else null,
            onSave = { name, color -> vm.saveTag(t.copy(name = name.removePrefix("#").replace(" ", "_"), color = color ?: t.color)); editTag = null },
        )
    }
    editProject?.let { p ->
        var folderId by remember(p.id) { mutableStateOf(p.folderId) }
        var archived by remember(p.id) { mutableStateOf(p.archived) }
        var confirmDelete by remember(p.id) { mutableStateOf(false) }
        NameDialog(
            title = stringResource(if (p.name.isEmpty()) R.string.new_project else R.string.edit_project),
            initial = p.name, colors = Repository.ProjectPalette, color = p.color,
            onDismiss = { editProject = null },
            onDelete = if (data.projects.any { it.id == p.id }) ({ confirmDelete = true }) else null,
            extra = {
                Text(stringResource(R.string.folder), style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = folderId == null, onClick = { folderId = null }, label = { Text(stringResource(R.string.none)) })
                    data.folders.forEach { f -> FilterChip(selected = folderId == f.id, onClick = { folderId = f.id }, label = { Text(f.name) }) }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.archived), modifier = Modifier.weight(1f))
                    Switch(checked = archived, onCheckedChange = { archived = it })
                }
            },
            onSave = { name, color -> vm.saveProject(p.copy(name = name, color = color ?: p.color, folderId = folderId, archived = archived)); editProject = null },
        )
        if (confirmDelete) AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete_project)) },
            text = { Text(stringResource(R.string.delete_project_body)) },
            confirmButton = { TextButton(onClick = { vm.deleteProject(p.id, true); confirmDelete = false; editProject = null }) { Text(stringResource(R.string.delete_with_tasks)) } },
            dismissButton = { TextButton(onClick = { vm.deleteProject(p.id, false); confirmDelete = false; editProject = null }) { Text(stringResource(R.string.keep_tasks)) } },
        )
    }
}

@Composable
private fun NameDialog(
    title: String,
    initial: String,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)?,
    onSave: (String) -> Unit,
) = NameDialog(title, initial, emptyList(), null, onDismiss, onDelete, {}, { n, _ -> onSave(n) })

@Composable
private fun NameDialog(
    title: String,
    initial: String,
    colors: List<Int>,
    color: Int?,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)?,
    extra: @Composable () -> Unit = {},
    onSave: (String, Int?) -> Unit,
) {
    var name by remember { mutableStateOf(initial) }
    var selColor by remember { mutableStateOf(color) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                if (colors.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    colors.forEach { c ->
                        Box(
                            Modifier.size(30.dp).clip(CircleShape).background(Color(c))
                                .border(if (selColor == c) 3.dp else 0.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                .clickable { selColor = c }
                        )
                    }
                }
                extra()
            }
        },
        confirmButton = { TextButton(onClick = { if (name.isNotBlank()) onSave(name.trim(), selColor) }, enabled = name.isNotBlank()) { Text(stringResource(R.string.save)) } },
        dismissButton = {
            Row {
                if (onDelete != null) TextButton(onClick = onDelete) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        },
    )
}
