package be.casperverswijvelt.unifiedinternetqs.tilebehaviour

import android.content.Context
import android.graphics.drawable.Icon
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.telephony.ServiceState
import be.casperverswijvelt.unifiedinternetqs.R
import be.casperverswijvelt.unifiedinternetqs.TileSyncService
import be.casperverswijvelt.unifiedinternetqs.settings.ISetting
import be.casperverswijvelt.unifiedinternetqs.tiles.InternetTileService
import be.casperverswijvelt.unifiedinternetqs.util.AlertDialogData
import be.casperverswijvelt.unifiedinternetqs.util.executeShellCommandAsync
import be.casperverswijvelt.unifiedinternetqs.util.getCellularNetworkText
import be.casperverswijvelt.unifiedinternetqs.util.getDataEnabled
import be.casperverswijvelt.unifiedinternetqs.util.getWifiEnabled
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class InternetTileBehaviour(
    context: Context,
    showDialog: (AlertDialogData) -> Unit,
    unlockAndRun: (Runnable) -> Unit = { it.run() }
): TileBehaviour(context, showDialog, unlockAndRun) {

    companion object {
        private const val TAG = "InternetDataTileBehaviour"
    }

    override val type: TileType
        get() = TileType.Internet
    override val tileName: String
        get() = resources.getString(R.string.internet)
    override val defaultIcon: Icon
        get() = Icon.createWithResource(
            context,
            R.drawable.baseline_net
        )

    @Suppress("UNCHECKED_CAST")
    override val tileServiceClass: Class<TileService>
        get() = InternetTileService::class.java as Class<TileService>

    override val tileState: TileState
        get() {
            val tile = TileState()
            val dataEnabled = getDataEnabled(context)
            val wifiEnabled = getWifiEnabled(context)

            when {
                (TileSyncService.isTurningOnWifi || wifiEnabled) && !TileSyncService.isTurningOnData -> {

                    if (wifiEnabled) {
                        TileSyncService.isTurningOnWifi = false
                    }

                    tile.label = resources.getString(R.string.internet)
                    tile.state = Tile.STATE_ACTIVE
                    tile.icon = R.drawable.baseline_net

                    tile.subtitle = when {
                        TileSyncService.isTurningOnWifi -> resources.getString(R.string.turning_on)
                        TileSyncService.wifiConnected -> resources.getString(R.string.connected)
                        else -> resources.getString(R.string.not_connected)
                    }

                }
                TileSyncService.isTurningOnData || dataEnabled -> {

                    if (dataEnabled) {
                        TileSyncService.isTurningOnData = false
                    }

                    tile.label = resources.getString(R.string.internet)
                    tile.state = Tile.STATE_ACTIVE
                    tile.icon = R.drawable.baseline_net
                    if (
                        TileSyncService.serviceState?.let {
                            it.state != ServiceState.STATE_IN_SERVICE
                        } == true
                    ) {
                        tile.subtitle = resources.getString(R.string.not_connected)
                    } else {
                        tile.subtitle = resources.getString(R.string.connected)
                    }
                }
                else -> {

                    tile.label = resources.getString(R.string.internet)
                    tile.state = Tile.STATE_INACTIVE
                    tile.icon = R.drawable.baseline_net
                    tile.subtitle = resources.getString(R.string.off)
                }
            }

            return tile
        }
    override val onLongClickIntentAction: String
        get() {
            return when {
                getDataEnabled(context) -> {
                    Settings.ACTION_NETWORK_OPERATOR_SETTINGS
                }
                getWifiEnabled(context) -> {
                    Settings.ACTION_WIFI_SETTINGS
                }
                else -> {
                    Settings.ACTION_WIRELESS_SETTINGS
                }
            }
        }

    override fun onClick() {

        if (!checkShellAccess()) return

        if (requiresUnlock) {
            unlockAndRun { cycleInternet() }
        } else {
            cycleInternet()
        }
    }

    private fun cycleInternet() {

        // Cycle trough internet connection modes:
        //  If Wi-Fi is enabled -> disable Wi-Fi and enable mobile data
        //  If mobile data is enabled -> disable mobile data and enable Wi-Fi
        //  Else -> enable Wi-Fi

        val dataEnabled = getDataEnabled(context)
        val wifiEnabled = getWifiEnabled(context)

        TileSyncService.isTurningOnData = false
        TileSyncService.isTurningOnWifi = false

        when {
            wifiEnabled -> {
                executeShellCommandAsync("svc wifi disable", context)

                TileSyncService.isTurningOnData = true
                executeShellCommandAsync("svc data enable", context) {
                    if (it?.isSuccess != true) {
                        TileSyncService.isTurningOnData = false
                    }
                    updateTile()
                }
            }
            dataEnabled -> {
                executeShellCommandAsync("svc data disable", context)

                TileSyncService.isTurningOnWifi = true
                executeShellCommandAsync("svc wifi enable", context) {
                    if (it?.isSuccess != true) {
                        TileSyncService.isTurningOnWifi = false
                    }
                    updateTile()
                }
            }
            else -> {
                TileSyncService.isTurningOnWifi = true
                executeShellCommandAsync("svc wifi enable", context) {
                    if (it?.isSuccess != true) {
                        TileSyncService.isTurningOnWifi = false
                    }
                    updateTile()
                }
            }
        }
        updateTile()
    }
}