package be.casperverswijvelt.unifiedinternetqs

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Network
import android.os.IBinder
import android.service.quicksettings.TileService
import android.telephony.ServiceState
import be.casperverswijvelt.unifiedinternetqs.listeners.CellularChangeListener
import be.casperverswijvelt.unifiedinternetqs.listeners.NetworkChangeType
import be.casperverswijvelt.unifiedinternetqs.listeners.WifiChangeListener
import be.casperverswijvelt.unifiedinternetqs.tilebehaviour.AirplaneModeTileBehaviour
import be.casperverswijvelt.unifiedinternetqs.tilebehaviour.InternetTileBehaviour
import be.casperverswijvelt.unifiedinternetqs.tilebehaviour.MobileDataTileBehaviour
import be.casperverswijvelt.unifiedinternetqs.tilebehaviour.TileBehaviour
import be.casperverswijvelt.unifiedinternetqs.tilebehaviour.WifiTileBehaviour
import be.casperverswijvelt.unifiedinternetqs.tiles.AirplaneModeTileService
import be.casperverswijvelt.unifiedinternetqs.tiles.InternetTileService
import be.casperverswijvelt.unifiedinternetqs.tiles.MobileDataTileService
import be.casperverswijvelt.unifiedinternetqs.tiles.WifiTileService
import be.casperverswijvelt.unifiedinternetqs.ui.MainActivity
import be.casperverswijvelt.unifiedinternetqs.util.getConnectedWifiSSID


class TileSyncService: Service() {

    companion object {
        const val TAG = "TileSyncService"

        private var instance: TileSyncService? = null
        val isRunning
            get() = instance != null

        private var latestAvailableWifiNetwork: Network? = null
        var wifiConnected = false
        var wifiSSID: String? = null

        var isTurningOnData = false
        var isTurningOffData = false
        var serviceState: ServiceState? = null

        var isTurningOnWifi = false
        var isTurningOffWifi = false

        var isTurningOnAirplaneMode = false
        var isTurningOffAirplaneMode = false

        private val behaviourListeners = arrayListOf<TileBehaviour>()

        fun addBehaviourListener(tileBehaviour: TileBehaviour) {
            behaviourListeners.add(tileBehaviour)
        }
        fun removeBehaviourListener(tileBehaviour: TileBehaviour) {
            behaviourListeners.remove(tileBehaviour)
        }
    }

    private val wifiChangeListener: WifiChangeListener = WifiChangeListener { type, network ->
        when(type) {
            NetworkChangeType.NETWORK_LOST -> {
                // If the network that is lost is not the latest
                //  network that became available, we are still
                //  connected.
                wifiConnected = latestAvailableWifiNetwork != network
            }
            NetworkChangeType.NETWORK_AVAILABLE -> {
                latestAvailableWifiNetwork = network
                wifiConnected = true
            }
            else -> {}
        }

        val setSSID: (ssid: String?) -> Unit = {
            wifiSSID = it
            updateWifiTile()
            updateInternetTile()
        }

        if (wifiConnected) {
            getConnectedWifiSSID(applicationContext) { setSSID(it) }
        } else {
            setSSID(null)
        }
    }
    private val cellularChangeListener: CellularChangeListener = CellularChangeListener { type, data ->
        when(type) {
            NetworkChangeType.SERVICE_STATE -> {
                (data?.firstOrNull() as? ServiceState)?.let {
                    serviceState = it
                }
            }
            else -> {}
        }
        updateMobileDataTile()
        updateInternetTile()
    }
    private val airplaneModeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_AIRPLANE_MODE_CHANGED) {
                updateMobileDataTile()
                updateInternetTile()
                updateAirplaneModeTile()
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0, notificationIntent, PendingIntent.FLAG_IMMUTABLE
        )
        val notification: Notification =
            Notification.Builder(this, TileApplication.CHANNEL_ID)
                .setContentTitle(resources.getString(R.string.hide_service_title))
                .setContentText(resources.getString(R.string.hide_service_description))
                .setSmallIcon(R.drawable.baseline_net)
                .setContentIntent(pendingIntent)
                .build()
        val mNotificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            TileApplication.CHANNEL_ID,
            TileApplication.CHANNEL_NAME,
            NotificationManager.IMPORTANCE_DEFAULT
        )
        mNotificationManager.createNotificationChannel(channel)
        Notification.Builder(this, TileApplication.CHANNEL_ID)
        startForeground(1, notification)
        return START_NOT_STICKY
    }

    override fun onCreate() {
        super.onCreate()

        instance = this

        // Wi-Fi
        wifiChangeListener.startListening(applicationContext)

        // Cellular
        cellularChangeListener.startListening(applicationContext)
        registerReceiver(
            airplaneModeReceiver,
            IntentFilter(Intent.ACTION_AIRPLANE_MODE_CHANGED)
        )

        updateAllTiles()
    }

    override fun onDestroy() {
        super.onDestroy()

        instance = null

        wifiChangeListener.stopListening(applicationContext)
        cellularChangeListener.stopListening(applicationContext)
        unregisterReceiver(airplaneModeReceiver)

        updateAllTiles()
    }

    private fun updateAllTiles() {
        updateWifiTile()
        updateMobileDataTile()
        updateInternetTile()
        updateAirplaneModeTile()
    }

    private fun updateWifiTile() {
        requestListeningState(WifiTileService::class.java)
        requestTileBehaviourUpdate(WifiTileBehaviour::class.java)
    }

    private fun updateMobileDataTile() {
        requestListeningState(MobileDataTileService::class.java)
        requestTileBehaviourUpdate(MobileDataTileBehaviour::class.java)
    }

    private fun updateInternetTile () {
        requestListeningState(InternetTileService::class.java)
        requestTileBehaviourUpdate(InternetTileBehaviour::class.java)
    }

    private fun updateAirplaneModeTile() {
        requestListeningState(AirplaneModeTileService::class.java)
        requestTileBehaviourUpdate(AirplaneModeTileBehaviour::class.java)
    }

    private fun <T>requestListeningState(cls: Class<T>) {
        TileService.requestListeningState(
            applicationContext,
            ComponentName(applicationContext, cls)
        )
    }

    private fun <T>requestTileBehaviourUpdate(cls: Class<T>) {
        behaviourListeners.forEach {
            if (it.javaClass == cls) it.updateTile()
        }
    }
}