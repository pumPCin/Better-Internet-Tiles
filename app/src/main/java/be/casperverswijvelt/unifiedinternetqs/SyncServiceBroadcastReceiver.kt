package be.casperverswijvelt.unifiedinternetqs

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class SyncServiceBroadcastReceiver: BroadcastReceiver() {
    companion object {
        const val TAG = "SyncBroadcastReceiver"
    }
    override fun onReceive(context: Context?, intent: Intent) {
        context?.startForegroundService(Intent(
            context,
            TileSyncService::class.java
        ))
    }
}