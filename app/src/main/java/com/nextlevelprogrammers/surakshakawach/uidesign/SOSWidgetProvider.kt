package com.nextlevelprogrammers.surakshakawach.uidesign

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import android.widget.RemoteViews
import com.nextlevelprogrammers.surakshakawach.R
import com.nextlevelprogrammers.surakshakawach.broadcastReciever.SOSWidgetReciever

class SOSWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val prefs = context.getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        val userId = prefs.getString("user_id", null)

        for (appWidgetId in appWidgetIds) {
            val views = RemoteViews(context.packageName, R.layout.widget_sos_layout)

            if (userId != null)
            {
                val intent = Intent(context, SOSWidgetReciever::class.java).apply {
                    action = "com.nextlevelprogrammers.surakshakawach.SOS_ACTION"
                    putExtra("USER_ID", userId)
                }

                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    0,
                    intent,
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                    else PendingIntent.FLAG_UPDATE_CURRENT
                )
                Log.d("SOSWidgetProvider", "Sending intent to Broadcast receiver")
                views.setOnClickPendingIntent(R.id.viewFlipper, pendingIntent)
            }

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}

