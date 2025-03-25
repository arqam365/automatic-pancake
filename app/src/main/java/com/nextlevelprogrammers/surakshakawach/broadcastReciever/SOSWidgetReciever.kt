package com.nextlevelprogrammers.surakshakawach.broadcastReciever

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import com.nextlevelprogrammers.surakshakawach.R
import com.nextlevelprogrammers.surakshakawach.service.SOSForegroundService
import com.nextlevelprogrammers.surakshakawach.uidesign.SOSWidgetProvider

class SOSWidgetReciever: BroadcastReceiver(){
    override fun onReceive(context: Context, intent: Intent?)
    {
        if (intent?.action == "com.nextlevelprogrammers.surakshakawach.SOS_ACTION")
        {
            val prefs = context.getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
            val sos_status= prefs.getBoolean("is_service_running", false )
            val userId = intent.getStringExtra("USER_ID")
            val serviceIntent = Intent(context, SOSForegroundService::class.java).apply {
                action = SOSForegroundService.Actions.START.toString()
                putExtra(SOSForegroundService.USER_ID_KEY, userId)
            }
            if(sos_status==false){
                ContextCompat.startForegroundService(context, serviceIntent)
                // 2️⃣ Update widget to show active SOS image via ViewFlipper
                val appWidgetManager = AppWidgetManager.getInstance(context)
                val widgetComponent = ComponentName(context, SOSWidgetProvider::class.java)
                val widgetIds = appWidgetManager.getAppWidgetIds(widgetComponent)
                for (widgetId in widgetIds) {
                    val views = RemoteViews(context.packageName, R.layout.widget_sos_layout)
                    // Flip to next image (active image)
                    views.showNext(R.id.viewFlipper)
                    appWidgetManager.updateAppWidget(widgetId, views)
                }
            }
        }
        if(intent?.action== "com.nextlevelprogrammers.surakshakawach.SOS_RESET_ACTION"){
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val widgetComponent = ComponentName(context, SOSWidgetProvider::class.java)
            val widgetIds = appWidgetManager.getAppWidgetIds(widgetComponent)
            for (widgetId in widgetIds) {
                val views = RemoteViews(context.packageName, R.layout.widget_sos_layout)
                views.setDisplayedChild(R.id.viewFlipper, 0) // Go back to first image (default)
                appWidgetManager.updateAppWidget(widgetId, views)
            }
        }
    }

}