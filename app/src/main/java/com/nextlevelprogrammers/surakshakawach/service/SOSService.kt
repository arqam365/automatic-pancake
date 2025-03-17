package com.nextlevelprogrammers.surakshakawach.service

import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.nextlevelprogrammers.surakshakawach.R
import com.nextlevelprogrammers.surakshakawach.data.remote.ApiService
import com.nextlevelprogrammers.surakshakawach.model.SOSManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch


class SOSForegroundService: Service(){
    private lateinit var sosManager: SOSManager
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var userId: String = ""

    companion object{
        const val USER_ID_KEY= "user_id"
    }
    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action){
            Actions.START.toString() -> {
                userId=intent.getStringExtra(USER_ID_KEY)?: ""
                start()
            }
            Actions.STOP.toString() -> stopSelf()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        Log.d("OnDestroy", "Service Stopped")
        super.onDestroy()
    }
    @RequiresApi(Build.VERSION_CODES.O)
    private fun start(){
        val notification= NotificationCompat.Builder(this, "running_channel")
            .setSmallIcon(R.drawable.logo)
            .setContentTitle("SOS is Active")
            .setContentText("Your location is being shared")
            .build()
        sosManager= SOSManager(this,userId, ApiService(),serviceScope)
        startForeground(1, notification )
        serviceScope.launch {
            sosManager.startSOS { success ->
                if (success) {
                    Log.d("SOSService", "✅ SOS Started Successfully in Foreground Service")
                } else {
                    Log.e("SOSService", "❌ Failed to start SOS")
                }
            }
        }
    }


    enum class Actions {
        START,STOP
    }
}