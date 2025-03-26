package com.nextlevelprogrammers.surakshakawach.broadcastReciever

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import com.nextlevelprogrammers.surakshakawach.R
import com.nextlevelprogrammers.surakshakawach.service.SOSForegroundService
import com.nextlevelprogrammers.surakshakawach.uidesign.SOSWidgetProvider

class SOSWidgetReciever : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return

        when (action) {
            //Action triggered by the sos widget to start SOS
            "com.nextlevelprogrammers.surakshakawach.SOS_ACTION" ->
                {
                    Log.d("SOSWidgetReciever", "Intent Recieved-StartSOS")
                val prefs = context.getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
                val sosStatus = prefs.getBoolean("is_service_running", false)
                val userId = intent.getStringExtra("USER_ID")

                if (!sosStatus) {
                    val serviceIntent = Intent(context, SOSForegroundService::class.java).apply {
                        this.action = SOSForegroundService.Actions.START.toString()
                        putExtra(SOSForegroundService.USER_ID_KEY, userId)
                    }
                    ContextCompat.startForegroundService(context, serviceIntent)

                    // Update widget to show active SOS image via ViewFlipper
                    val appWidgetManager = AppWidgetManager.getInstance(context)
                    val widgetComponent = ComponentName(context, SOSWidgetProvider::class.java)
                    val widgetIds = appWidgetManager.getAppWidgetIds(widgetComponent)
                    for (widgetId in widgetIds) {
                        val views = RemoteViews(context.packageName, R.layout.widget_sos_layout)
                        views.showNext(R.id.viewFlipper)
                        appWidgetManager.updateAppWidget(widgetId, views)
                    }
                }
            }

            //Action triggered when service is stopped on StopService
            "com.nextlevelprogrammers.surakshakawach.SOS_RESET_ACTION" ->
                {
                    Log.d("SOSWidgetReciever", "Intent Received-StopSOS")

                val appWidgetManager = AppWidgetManager.getInstance(context)
                val widgetComponent = ComponentName(context, SOSWidgetProvider::class.java)
                val widgetIds = appWidgetManager.getAppWidgetIds(widgetComponent)
                for (widgetId in widgetIds) {
                    val views = RemoteViews(context.packageName, R.layout.widget_sos_layout)
                    views.setDisplayedChild(R.id.viewFlipper, 0)
                    appWidgetManager.updateAppWidget(widgetId, views)
                }
            }

            //Action when the SOS is triggered from the app activity
            "com.nextlevelprogrammers.surakshakawach.SOS_Service.SOS_Started" ->
            {
                Log.d("SOSWidgetReciever", "Intent Received-StartSOSFromApp")
                val appWidgetManager = AppWidgetManager.getInstance(context)
                val widgetComponent = ComponentName(context, SOSWidgetProvider::class.java)
                val widgetIds = appWidgetManager.getAppWidgetIds(widgetComponent)
                for (widgetId in widgetIds) {
                    val views = RemoteViews(context.packageName, R.layout.widget_sos_layout)
                    views.setDisplayedChild(R.id.viewFlipper, 1)
                    appWidgetManager.updateAppWidget(widgetId, views)
                }
            }
        }
    }
}
