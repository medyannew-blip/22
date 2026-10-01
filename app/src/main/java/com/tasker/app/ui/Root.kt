package com.tasker.app.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CalendarViewDay
import androidx.compose.material.icons.outlined.CalendarViewWeek
import androidx.compose.material.icons.outlined.CheckCircleOutline
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Tag
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.ViewColumn
import androidx.compose.material.icons.outlined.ViewKanban
import androidx.compose.material.icons.outlined.ViewWeek
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.tasker.app.R
import com.tasker.app.data.Gtd
import com.tasker.app.data.Settings
import com.tasker.app.domain.epoch
import com.tasker.app.domain.isOverdue
import com.tasker.app.domain.today
import com.tasker.app.ui.components.ColorDot
import com.tasker.app.ui.components.DragDropHost
import com.tasker.app.ui.components.QuickAddSheet
import com.tasker.app.ui.components.RescheduleSheet
import com.tasker.app.ui.components.TaskCard
import com.tasker.app.ui.components.rememberDragDropState
import com.tasker.app.ui.screens.CalendarScreen
import com.tasker.app.ui.screens.EisenhowerScreen
import com.tasker.app.ui.screens.GtdScreen
import com.tasker.app.ui.screens.KanbanScreen
import com.tasker.app.ui.screens.ListKind
import com.tasker.app.ui.screens.LogbookScreen
import com.tasker.app.ui.screens.ManageScreen
import com.tasker.app.ui.screens.PlannerScreen
import com.tasker.app.ui.screens.SearchScreen
import com.tasker.app.ui.screens.SettingsScreen
import com.tasker.app.ui.screens.SyncSettingsScreen
import com.tasker.app.ui.screens.TaskDetailScreen
import com.tasker.app.ui.screens.TaskListScreen
import com.tasker.app.ui.screens.ThemeEditorScreen
import com.tasker.app.ui.screens.TimelineScreen
import com.tasker.app.ui.screens.TodayScreen
import com.tasker.app.ui.screens.WeeklyScreen
import com.tasker.app.ui.theme.Themes
import com.tasker.app.ui.theme.TaskerTheme
import kotlinx.coroutines.launch

@Composable
fun TaskerRoot(vm: MainViewModel) {
    val settings by vm.settings.collectAsState()
    val s = settings ?: return
    LaunchedEffect(Unit) { vm.initStart(s) }
    val spec = Themes.resolve(s)
    TaskerTheme(spec) {
        val direction = if (s.forceRtl) LayoutDirection.Rtl else LocalLayoutDirection.current
        CompositionLocalProvider(LocalLayoutDirection provides direction) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                NotificationPermission(vm, s)
                AppShell(vm, s)
            }
        }
    }
}

@Composable
private fun NotificationPermission(vm: MainViewModel, s: Settings) {
    val ctx = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        vm.updateSettings { it.copy(onboarded = true) }
    }
    LaunchedEffect(s.onboarded) {
        if (!s.onboarded) {
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            else vm.updateSettings { it.copy(onboarded = true) }
        }
    }
}

@Composable
private fun AppShell(vm: MainViewModel, s: Settings) {
    val data by vm.data.collectAsState()
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val dnd = rememberDragDropState()
    dnd.onDrop = vm::onDrop

    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }

    BackHandler(enabled = drawer.isOpen || vm.stack.size > 1) {
        if (drawer.isOpen) scope.launch { drawer.close() } else vm.back()
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 840.dp
        val onNavigate: (Route) -> Unit = { r ->
            vm.navigateRoot(r)
            scope.launch { drawer.close() }
        }
        val content: @Composable () -> Unit = {
            MainScaffold(vm, s, data, wide, snackbar, dnd, onMenu = { scope.launch { drawer.open() } })
        }
        if (wide) {
            Row(Modifier.fillMaxSize()) {
                Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.width(290.dp).fillMaxHeight().testTag("permanent-sidebar")) {
                    Sidebar(vm, data, onNavigate)
                }
                VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Box(Modifier.weight(1f)) { content() }
            }
        } else {
            ModalNavigationDrawer(
                drawerState = drawer,
                gesturesEnabled = !dnd.isDragging,
                drawerContent = {
                    ModalDrawerSheet(
                        drawerContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.width(300.dp),
                        windowInsets = WindowInsets(0),
                    ) { Sidebar(vm, data, onNavigate) }
                },
            ) { content() }
        }
    }

    vm.quickAddText?.let { text ->
        QuickAddSheet(vm, data, text, vm.quickAddDefaults, onDismiss = { vm.hideQuickAdd() })
    }
    vm.rescheduleTask?.let { id ->
        data.taskById[id]?.let { task ->
            RescheduleSheet(task, onPick = { day -> vm.reschedule(id, day) }, onDismiss = { vm.rescheduleTask = null })
        }
    }
}

@Composable
fun routeTitle(route: Route, data: AppData): String = when (route) {
    Route.Today -> stringResource(R.string.today)
    Route.Inbox -> stringResource(R.string.inbox)
    Route.All -> stringResource(R.string.all_tasks)
    Route.Daily -> stringResource(R.string.view_daily)
    Route.ThreeDay -> stringResource(R.string.view_three_day)
    Route.Weekly -> stringResource(R.string.view_weekly)
    Route.TeuxDeux -> stringResource(R.string.view_planner)
    Route.Calendar -> stringResource(R.string.view_calendar)
    Route.Kanban -> stringResource(R.string.view_kanban)
    Route.Eisenhower -> stringResource(R.string.view_eisenhower)
    Route.Gtd -> stringResource(R.string.view_gtd)
    Route.Logbook -> stringResource(R.string.logbook)
    Route.Search -> stringResource(R.string.search)
    Route.Settings -> stringResource(R.string.settings)
    Route.ThemeEditor -> stringResource(R.string.custom_theme)
    Route.SyncSettings -> stringResource(R.string.sync)
    Route.Manage -> stringResource(R.string.manage_lists)
    is Route.ProjectRoute -> data.projectById[route.id]?.name ?: ""
    is Route.TagRoute -> data.tagById[route.id]?.name?.let { if (it.startsWith("@")) it else "#$it" } ?: ""
    is Route.TaskDetail -> ""
}

@Composable
private fun MainScaffold(
    vm: MainViewModel,
    s: Settings,
    data: AppData,
    wide: Boolean,
    snackbar: SnackbarHostState,
    dnd: com.tasker.app.ui.components.DragDropState,
    onMenu: () -> Unit,
) {
    val route = vm.current
    val ctx = LocalContext.current
    val syncState by vm.sync.state.collectAsState()
    val showFab = route !is Route.TaskDetail && route !in listOf(Route.Settings, Route.ThemeEditor, Route.SyncSettings, Route.Manage, Route.Logbook)
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        routeTitle(route, data),
                        style = MaterialTheme.typography.headlineSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    if (vm.stack.size > 1) {
                        IconButton(onClick = { vm.back() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back)) }
                    } else if (!wide) {
                        IconButton(onClick = onMenu) { Icon(Icons.Rounded.Menu, stringResource(R.string.menu)) }
                    }
                },
                actions = {
                    if (route is Route.TaskDetail) {
                        val task = data.taskById[route.id]
                        if (task != null) {
                            IconButton(onClick = {
                                val send = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, buildString {
                                        append(task.title)
                                        if (task.notes.isNotBlank()) append("\n\n").append(task.notes)
                                        data.subtasksByTask[task.id].orEmpty().forEach { append("\n").append(if (it.done) "☑ " else "☐ ").append(it.title) }
                                    })
                                }
                                ctx.startActivity(Intent.createChooser(send, null))
                            }) { Icon(Icons.Outlined.Share, stringResource(R.string.share)) }
                            IconButton(onClick = { vm.delete(task.id); vm.back() }) {
                                Icon(Icons.Outlined.DeleteOutline, stringResource(R.string.delete))
                            }
                        }
                    } else if (route !in listOf(Route.Settings, Route.ThemeEditor, Route.SyncSettings, Route.Search)) {
                        if (s.syncMode != 0) {
                            IconButton(onClick = { vm.syncNow() }) {
                                if (syncState.running) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                                else Icon(Icons.Outlined.Sync, stringResource(R.string.sync_now))
                            }
                        }
                        IconButton(onClick = { vm.push(Route.Search) }) { Icon(Icons.Outlined.Search, stringResource(R.string.search)) }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        floatingActionButton = {
            if (showFab) {
                FloatingActionButton(
                    onClick = { vm.showQuickAdd("", vm.defaultsForCurrent()) },
                    shape = CircleShape,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ) { Icon(Icons.Rounded.Add, stringResource(R.string.add_task)) }
            }
        },
    ) { padding ->
        DragDropHost(
            dnd,
            Modifier.padding(padding).fillMaxSize(),
            ghost = { t -> TaskCard(t, data, onToggle = {}, elevated = true) },
        ) {
            AnimatedContent(route, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "route") { r ->
                RouteContent(r, vm, s, data, dnd, wide)
            }
        }
    }
}

@Composable
private fun RouteContent(
    r: Route,
    vm: MainViewModel,
    s: Settings,
    data: AppData,
    dnd: com.tasker.app.ui.components.DragDropState,
    wide: Boolean,
) {
    when (r) {
        Route.Today -> TodayScreen(vm, data, dnd)
        Route.Inbox -> TaskListScreen(vm, data, dnd, ListKind.Inbox)
        Route.All -> TaskListScreen(vm, data, dnd, ListKind.All)
        is Route.ProjectRoute -> TaskListScreen(vm, data, dnd, ListKind.Project(r.id))
        is Route.TagRoute -> TaskListScreen(vm, data, dnd, ListKind.TagList(r.id))
        Route.Daily -> TimelineScreen(vm, data, dnd, days = 1)
        Route.ThreeDay -> TimelineScreen(vm, data, dnd, days = 3)
        Route.Weekly -> WeeklyScreen(vm, s, data, dnd, wide)
        Route.TeuxDeux -> PlannerScreen(vm, s, data, dnd, wide)
        Route.Calendar -> CalendarScreen(vm, s, data, dnd)
        Route.Kanban -> KanbanScreen(vm, data, dnd)
        Route.Eisenhower -> EisenhowerScreen(vm, data, dnd)
        Route.Gtd -> GtdScreen(vm, data, dnd)
        Route.Logbook -> LogbookScreen(vm, data)
        Route.Search -> SearchScreen(vm, data)
        Route.Settings -> SettingsScreen(vm, s)
        Route.ThemeEditor -> ThemeEditorScreen(vm, s)
        Route.SyncSettings -> SyncSettingsScreen(vm, s)
        Route.Manage -> ManageScreen(vm, data)
        is Route.TaskDetail -> TaskDetailScreen(vm, data, r.id)
    }
}

// ---------------- Sidebar ----------------

@Composable
private fun Sidebar(vm: MainViewModel, data: AppData, onNavigate: (Route) -> Unit) {
    val current = vm.stack.firstOrNull()
    val t = today().epoch()
    val todayCount = data.open.count { (it.date != null && it.date <= t) || it.isOverdue() }
    val inboxCount = data.open.count { it.gtd == Gtd.INBOX }
    val expanded = remember { mutableStateMapOf<String, Boolean>() }
    Column(
        Modifier
            .fillMaxHeight()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(Modifier.padding(start = 12.dp, top = 12.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(30.dp).clip(RoundedCornerShape(9.dp)).background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Outlined.CheckCircleOutline, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(20.dp)) }
            Spacer(Modifier.width(10.dp))
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
        }
        SideItem(Icons.Outlined.LightMode, stringResource(R.string.today), current == Route.Today, todayCount) { onNavigate(Route.Today) }
        SideItem(Icons.Outlined.Inbox, stringResource(R.string.inbox), current == Route.Inbox, inboxCount) { onNavigate(Route.Inbox) }
        SideItem(Icons.AutoMirrored.Outlined.ViewList, stringResource(R.string.all_tasks), current == Route.All) { onNavigate(Route.All) }

        SideLabel(stringResource(R.string.views))
        SideItem(Icons.Outlined.CalendarViewDay, stringResource(R.string.view_daily), current == Route.Daily) { onNavigate(Route.Daily) }
        SideItem(Icons.Outlined.ViewColumn, stringResource(R.string.view_three_day), current == Route.ThreeDay) { onNavigate(Route.ThreeDay) }
        SideItem(Icons.Outlined.CalendarViewWeek, stringResource(R.string.view_weekly), current == Route.Weekly) { onNavigate(Route.Weekly) }
        SideItem(Icons.Outlined.ViewWeek, stringResource(R.string.view_planner), current == Route.TeuxDeux) { onNavigate(Route.TeuxDeux) }
        SideItem(Icons.Outlined.CalendarMonth, stringResource(R.string.view_calendar), current == Route.Calendar) { onNavigate(Route.Calendar) }
        SideItem(Icons.Outlined.ViewKanban, stringResource(R.string.view_kanban), current == Route.Kanban) { onNavigate(Route.Kanban) }
        SideItem(Icons.Outlined.GridView, stringResource(R.string.view_eisenhower), current == Route.Eisenhower) { onNavigate(Route.Eisenhower) }
        SideItem(Icons.Outlined.Lightbulb, stringResource(R.string.view_gtd), current == Route.Gtd) { onNavigate(Route.Gtd) }

        SideLabel(stringResource(R.string.projects)) {
            IconButton(onClick = { onNavigate(Route.Manage) }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Outlined.Add, stringResource(R.string.manage_lists), Modifier.size(18.dp))
            }
        }
        val projects = data.projects.filter { !it.archived }
        data.folders.forEach { folder ->
            val open = expanded[folder.id] ?: true
            SideItem(
                Icons.Outlined.Folder, folder.name, false,
                trailingIcon = if (open) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
            ) { expanded[folder.id] = !open }
            if (open) projects.filter { it.folderId == folder.id }.forEach { p ->
                val count = data.open.count { it.projectId == p.id }
                SideItem(null, p.name, current == Route.ProjectRoute(p.id), count, dot = Color(p.color), indent = true) {
                    onNavigate(Route.ProjectRoute(p.id))
                }
            }
        }
        projects.filter { p -> p.folderId == null || data.folders.none { it.id == p.folderId } }.forEach { p ->
            val count = data.open.count { it.projectId == p.id }
            SideItem(null, p.name, current == Route.ProjectRoute(p.id), count, dot = Color(p.color)) { onNavigate(Route.ProjectRoute(p.id)) }
        }

        if (data.tags.isNotEmpty()) {
            SideLabel(stringResource(R.string.tags))
            data.tags.forEach { tag ->
                SideItem(Icons.Outlined.Tag, tag.name.removePrefix("#"), current == Route.TagRoute(tag.id), iconTint = Color(tag.color)) {
                    onNavigate(Route.TagRoute(tag.id))
                }
            }
        }
        HorizontalDivider(Modifier.padding(vertical = 10.dp), color = MaterialTheme.colorScheme.outlineVariant)
        SideItem(Icons.Outlined.CheckCircleOutline, stringResource(R.string.logbook), current == Route.Logbook) { onNavigate(Route.Logbook) }
        SideItem(Icons.Outlined.Tune, stringResource(R.string.manage_lists), current == Route.Manage) { onNavigate(Route.Manage) }
        SideItem(Icons.Outlined.Settings, stringResource(R.string.settings), current == Route.Settings) { onNavigate(Route.Settings) }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SideLabel(text: String, trailing: @Composable (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(start = 14.dp, top = 18.dp, bottom = 4.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline, modifier = Modifier.weight(1f))
        trailing?.invoke()
    }
}

@Composable
private fun SideItem(
    icon: ImageVector?,
    label: String,
    selected: Boolean,
    count: Int = 0,
    dot: Color? = null,
    indent: Boolean = false,
    iconTint: Color? = null,
    trailingIcon: ImageVector? = null,
    onClick: () -> Unit,
) {
    val bg = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent
    val fg = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
    Row(
        Modifier
            .fillMaxWidth()
            .height(42.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(start = if (indent) 30.dp else 12.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        when {
            dot != null -> Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) { ColorDot(dot, 10.dp) }
            icon != null -> Icon(icon, null, tint = iconTint ?: fg.copy(alpha = 0.8f), modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(14.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal), color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (count > 0) Text("$count", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
        if (trailingIcon != null) Icon(trailingIcon, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.outline)
    }
}
