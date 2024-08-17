package org.androdevlinux.utxo.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import kotlinx.coroutines.runBlocking
import java.util.concurrent.TimeUnit

fun scheduleWidgetUpdate(context: Context) {
    val glanceAppWidgetManager = GlanceAppWidgetManager(context)
    val glanceIds = runBlocking { glanceAppWidgetManager.getGlanceIds(TrackerWidget::class.java) }

    if (glanceIds.isNotEmpty()) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (alarmManager.canScheduleExactAlarms()) {
                scheduleExactAlarm(context, alarmManager)
            } else {
                requestExactAlarmPermission(context)
            }
        } else {
            scheduleExactAlarm(context, alarmManager)
        }
    } else {
        // No widget is currently added to the home screen, so no need to schedule updates
        Log.d("Widget", "No widget added, skipping alarm scheduling.")
    }
}


private fun scheduleExactAlarm(context: Context, alarmManager: AlarmManager) {
    val intent = Intent(context, PriceTrackerWidgetReceiver::class.java).apply {
        action = "UPDATE_WIDGET_DATA"
    }
    val pendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE // Add the FLAG_IMMUTABLE flag here
    )

    alarmManager.setRepeating(
        AlarmManager.RTC_WAKEUP,
        System.currentTimeMillis(),
        TimeUnit.SECONDS.toMillis(15),
        pendingIntent
    )
}

fun requestExactAlarmPermission(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        // Show a message to the user
        Toast.makeText(context, "Please allow exact alarms for this app to work properly.", Toast.LENGTH_LONG).show()

        // Start the activity to request exact alarm permission
        val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
            data = Uri.parse("package:" + context.packageName)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    } else {
        // Handle behavior for devices running API levels below 31
        Toast.makeText(context, "Exact alarms are not required on this device version.", Toast.LENGTH_LONG).show()
    }
}

class UpdateWidgetWorker(appContext: Context, workerParams: WorkerParameters) : Worker(appContext, workerParams) {
    override fun doWork(): Result {
        return try {
            val glanceAppWidgetManager = GlanceAppWidgetManager(applicationContext)
            val glanceIds = runBlocking { glanceAppWidgetManager.getGlanceIds(TrackerWidget::class.java) }

            if (glanceIds.isNotEmpty()) {
                // Run the suspend function in a blocking context
                runBlocking {
                    fetchAndCacheData(applicationContext)
                }

                // Trigger widget update for all instances of the widget
                TrackerWidget.updateAllWidgets(applicationContext)

                Result.success()
            } else {
                // No widget is currently added to the home screen
                Result.failure()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure()
        }
    }
}
