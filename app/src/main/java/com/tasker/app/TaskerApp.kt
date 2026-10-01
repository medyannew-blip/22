package com.tasker.app

import android.app.Application
import android.content.Context
import com.tasker.app.data.Repository
import com.tasker.app.data.SettingsStore
import com.tasker.app.data.TaskerDb
import com.tasker.app.notify.AlarmScheduler
import com.tasker.app.notify.Notifier
import com.tasker.app.sync.SyncManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch

class TaskerApp : Application() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    lateinit var db: TaskerDb
    lateinit var repo: Repository
    lateinit var settings: SettingsStore
    lateinit var alarms: AlarmScheduler
    lateinit var sync: SyncManager

    @OptIn(FlowPreview::class)
    override fun onCreate() {
        super.onCreate()
        db = TaskerDb.create(this)
        repo = Repository(db)
        settings = SettingsStore(this)
        alarms = AlarmScheduler(this)
        sync = SyncManager(this, repo, settings, scope)
        Notifier.createChannels(this)

        scope.launch {
            repo.seedIfEmpty(
                listOf(
                    getString(R.string.seed_1),
                    getString(R.string.seed_2),
                    getString(R.string.seed_3),
                    getString(R.string.seed_4),
                    getString(R.string.seed_5),
                )
            )
            alarms.rescheduleAll()
            runCatching { sync.schedulePeriodic() }
            sync.syncNow()
        }
        scope.launch {
            repo.changes.debounce(400).collect {
                alarms.rescheduleAll()
                sync.requestSync()
            }
        }
    }

    companion object {
        fun get(context: Context): TaskerApp = context.applicationContext as TaskerApp
    }
}
