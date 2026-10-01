package com.tasker.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.tasker.app.ui.MainViewModel
import com.tasker.app.ui.TaskerRoot
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handleIntent(intent)
        setContent { TaskerRoot(vm) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        val app = TaskerApp.get(this)
        lifecycleScope.launch {
            app.alarms.rescheduleAll()
            app.sync.syncNow()
        }
    }

    private fun handleIntent(intent: Intent?) {
        intent ?: return
        intent.getStringExtra(EXTRA_TASK)?.let { vm.openFromNotification(it, intent.getBooleanExtra(EXTRA_RESCHEDULE, false)) }
        when (intent.action) {
            ACTION_ADD -> vm.showQuickAdd("")
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)?.let { vm.showQuickAdd(it) }
        }
    }

    companion object {
        const val EXTRA_TASK = "open_task"
        const val EXTRA_RESCHEDULE = "reschedule"
        const val ACTION_ADD = "com.tasker.app.ADD_TASK"
    }
}
