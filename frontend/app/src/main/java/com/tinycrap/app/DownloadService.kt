package com.tinycrap.app

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.tinycrap.app.data.Notifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Keeps the process alive and shows progress while downloads run in the background.
 * The real work happens in [com.tinycrap.app.data.DownloadManager]; this only mirrors it in a notification.
 */
class DownloadService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var watcher: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val app = application as TinyCrapApp
        if (intent?.action == ACTION_CANCEL_ALL) app.downloads.cancelAll()

        val initial: Notification = app.notifier.progress(app.downloads.tasks.value, app.settings.state.value.showProgress)
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0
        try {
            ServiceCompat.startForeground(this, Notifier.NOTIFICATION_ID, initial, type)
        } catch (e: Exception) {
            stopSelf()          // the system refused a foreground service; downloads still run while the app is open
            return START_NOT_STICKY
        }

        if (watcher?.isActive != true) {
            watcher = scope.launch {
                app.downloads.tasks.collect { tasks ->
                    if (tasks.none { it.active }) {
                        stopSelf()
                    } else {
                        val n = app.notifier.progress(tasks, app.settings.state.value.showProgress)
                        app.notifier.notifyForeground(this@DownloadService, n)
                    }
                }
            }
        }
        return START_NOT_STICKY
    }

    /** Android 15 stops long data-sync services; end cleanly instead of crashing. */
    override fun onTimeout(startId: Int, fgsType: Int) {
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_CANCEL_ALL = "com.tinycrap.app.CANCEL_ALL"

        fun start(context: Context) {
            try {
                ContextCompat.startForegroundService(context, Intent(context, DownloadService::class.java))
            } catch (_: Exception) {
                // Not allowed right now (for example the app is in the background); the download keeps going anyway.
            }
        }
    }
}
