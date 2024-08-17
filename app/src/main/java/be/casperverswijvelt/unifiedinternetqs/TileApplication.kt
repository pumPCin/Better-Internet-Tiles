package be.casperverswijvelt.unifiedinternetqs

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import be.casperverswijvelt.unifiedinternetqs.data.BITPreferences
import be.casperverswijvelt.unifiedinternetqs.data.ShellMethod
import be.casperverswijvelt.unifiedinternetqs.util.ExecutorServiceSingleton
import be.casperverswijvelt.unifiedinternetqs.util.getInstallId
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class TileApplication : Application() {

    companion object {
        const val CHANNEL_ID = "tileSyncServiceChannel"
        const val CHANNEL_NAME = "Tile Synchronization service"
        const val TAG = "TileApplication"
    }

    override fun onCreate() {
        super.onCreate()

        ExecutorServiceSingleton.getInstance()

        createNotificationChannel()

        val preferences = BITPreferences(this)
        runBlocking {
            when (preferences.getShellMethod.first()) {
                ShellMethod.ROOT -> {
                    Shell.getShell {
                    }
                }

                ShellMethod.AUTO -> {
                    // Mode AUTO is when user has not explicitly set a
                    Shell.getShell {

                        if (Shell.isAppGrantedRoot() == true) {
                            runBlocking {
                                preferences.setShellMethod(ShellMethod.ROOT)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun createNotificationChannel() {
        val serviceChannel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_DEFAULT
        )
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(serviceChannel)
    }
}