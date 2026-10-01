package com.tasker.app

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tasker.app.data.Status
import com.tasker.app.domain.epoch
import com.tasker.app.domain.today
import com.tasker.app.ui.MainViewModel
import com.tasker.app.ui.Route
import com.tasker.app.ui.theme.Themes
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Renders every screen with seeded data to catch runtime crashes. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class ScreensSmokeTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private fun vm() = ViewModelProvider(rule.activity)[MainViewModel::class.java]

    private fun go(route: Route) {
        rule.runOnUiThread { vm().navigateRoot(route) }
        rule.waitForIdle()
    }

    @Test
    fun allScreensRender() {
        rule.waitUntil(15_000) { vm().data.value.tasks.size >= 5 }
        rule.onNodeWithTag("permanent-sidebar").assertDoesNotExist()
        val routes = listOf(
            Route.Today, Route.Inbox, Route.All, Route.Daily, Route.ThreeDay, Route.Weekly, Route.TeuxDeux,
            Route.Calendar, Route.Kanban, Route.Eisenhower, Route.Gtd, Route.Logbook, Route.Search,
            Route.Settings, Route.ThemeEditor, Route.SyncSettings, Route.Manage,
        )
        routes.forEach { go(it) }
        val data = vm().data.value
        go(Route.ProjectRoute(data.projects.first().id))
        data.tags.firstOrNull()?.let { go(Route.TagRoute(it.id)) }
        rule.runOnUiThread { vm().openTask(data.tasks.first().id) }
        rule.waitForIdle()

        // Quick add sheet with natural language preview
        rule.runOnUiThread { vm().showQuickAdd("Call mom tomorrow 5pm #family +Home !1 *") }
        rule.waitForIdle()
        rule.runOnUiThread { vm().hideQuickAdd() }
        rule.waitForIdle()

        // Every drop target type
        val id = data.tasks.first().id
        val d = today().epoch()
        listOf("day:$d", "untimed:$d", "slot:$d:600", "status:${Status.DOING}", "quad:1:0", "project:none", "gtd:1", "top3:$d", "nodate", "ctx:none")
            .forEach { key -> rule.runOnUiThread { vm().onDrop(id, key, emptyList(), 0) }; rule.waitForIdle() }

        // Every theme
        (Themes.presets.map { it.id } + Themes.CUSTOM_ID).forEach { themeId ->
            rule.runOnUiThread { vm().updateSettings { it.copy(themeId = themeId) } }
            rule.waitForIdle()
        }
        // RTL
        rule.runOnUiThread { vm().updateSettings { it.copy(forceRtl = true) } }
        go(Route.TeuxDeux)
        go(Route.Kanban)
    }
}

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w1280dp-h800dp-land")
class TabletSmokeTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun tabletLayoutRenders() {
        val vm = ViewModelProvider(rule.activity)[MainViewModel::class.java]
        rule.waitForIdle()
        rule.onNodeWithTag("permanent-sidebar").assertExists()
        listOf(Route.Today, Route.Weekly, Route.TeuxDeux, Route.Calendar, Route.Kanban, Route.ThreeDay).forEach { r ->
            rule.runOnUiThread { vm.navigateRoot(r) }
            rule.waitForIdle()
        }
    }
}
