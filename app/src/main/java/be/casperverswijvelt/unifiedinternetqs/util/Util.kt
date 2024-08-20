package be.casperverswijvelt.unifiedinternetqs.util

import android.Manifest
import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings
import android.service.quicksettings.TileService
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import android.telephony.TelephonyDisplayInfo
import android.telephony.TelephonyManager
import androidx.preference.PreferenceManager
import be.casperverswijvelt.unifiedinternetqs.BuildConfig
import be.casperverswijvelt.unifiedinternetqs.R
import be.casperverswijvelt.unifiedinternetqs.data.BITPreferences
import be.casperverswijvelt.unifiedinternetqs.data.ShellMethod
import be.casperverswijvelt.unifiedinternetqs.tiles.AirplaneModeTileService
import be.casperverswijvelt.unifiedinternetqs.tiles.InternetTileService
import be.casperverswijvelt.unifiedinternetqs.tiles.MobileDataTileService
import be.casperverswijvelt.unifiedinternetqs.tiles.WifiTileService
import be.casperverswijvelt.unifiedinternetqs.ui.MainActivity
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import java.lang.reflect.Method
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.UUID

const val TAG = "Util"

// Connectivity

fun getDataEnabled(context: Context): Boolean {

    var mobileDataEnabled = false

    (context.getSystemService(
        Context.CONNECTIVITY_SERVICE
    ) as? ConnectivityManager)?.let {
        // Get mobile data enabled state
        try {
            val cmClass = Class.forName(it.javaClass.name)
            val method: Method = cmClass.getDeclaredMethod("getMobileDataEnabled")
            method.isAccessible = true // Make the method callable
            // get the setting for "mobile data"
            mobileDataEnabled = method.invoke(it) as Boolean
        } catch (e: Exception) {
            // Empty
        }
    }
    return mobileDataEnabled
}

fun getWifiEnabled(context: Context): Boolean {

    return (context.getSystemService(Context.WIFI_SERVICE) as? WifiManager)
        ?.isWifiEnabled ?: false
}

fun getAirplaneModeEnabled(context: Context): Boolean {

    return Settings.Global.getInt(
        context.contentResolver,
        Settings.Global.AIRPLANE_MODE_ON,
        0
    ) != 0
}

fun getConnectedWifiSSID(
    context: Context,
    callback: ((String?) -> Unit)
) {

    if (hasShellAccess(context)) {
        executeShellCommandAsync(
            "dumpsys netstats | grep -E 'iface=wlan.*(networkId|wifiNetworkKey)'",
            context = context
        ) {
            val pattern = "(?<=(networkId|wifiNetworkKey)=\").*(?=\")".toRegex()
            it?.out?.forEach { wifiString ->
                pattern.find(wifiString)?.let { matchResult ->
                    callback(matchResult.value)
                    return@executeShellCommandAsync
                }
            }
            callback(null)
        }
    } else {
        callback(null)
    }
}

fun getCellularNetworkText(
    context: Context,
    telephonyDisplayInfo: TelephonyDisplayInfo?
): String {

    val info = ArrayList<String>()
    val tm =
        context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

    val subscriptionInfo = getDataSubscriptionInfo(context)
    // No data sim set or no read phone state permission
        ?: return context.getString(R.string.network_not_available)

    subscriptionInfo.displayName?.let {
        info.add(it.toString())
    }

    // TODO: Use signal strength of data SIM
    if (tm.signalStrength?.level == 0) {

        // No service
        return context.getString(R.string.no_service)
    }

    var connType: String? = telephonyDisplayInfo?.let {
        when (telephonyDisplayInfo.overrideNetworkType) {
            TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_LTE_CA -> "LTE+"
            TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_LTE_ADVANCED_PRO -> "5Ge"
            TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA -> "5G"
            TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_ADVANCED -> "5G+"
            else -> null
        }
    }

    // Fallback
    if (
        connType == null &&
        context.checkSelfPermission(
            Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED
    ) {
        connType = getNetworkClassString(tm.dataNetworkType)
    }

    connType?.let { info.add(it) }

    return info.joinToString(separator = ", ")
}

fun getDataSubscriptionInfo(context: Context): SubscriptionInfo? {

    if (
        context.checkSelfPermission(
            Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED
    ) {
        val sm =
            context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as
                    SubscriptionManager
        val subId = SubscriptionManager.getActiveDataSubscriptionId()

        if (subId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {

            return sm.getActiveSubscriptionInfo(subId)
        }
    }
    // No data sim set or no read phone state permission
    return null
}

private fun getNetworkClassString(networkType: Int): String? {

    return when (networkType) {
        TelephonyManager.NETWORK_TYPE_1xRTT,
        TelephonyManager.NETWORK_TYPE_GSM -> "2G-GSM"
        TelephonyManager.NETWORK_TYPE_GPRS -> "2G-GPRS"
        TelephonyManager.NETWORK_TYPE_EDGE -> "2G-EDGE"
        TelephonyManager.NETWORK_TYPE_CDMA -> "2G-CDMA"

        TelephonyManager.NETWORK_TYPE_EVDO_0,
        TelephonyManager.NETWORK_TYPE_EVDO_A,
        TelephonyManager.NETWORK_TYPE_EVDO_B -> "3G-EVDO"
        TelephonyManager.NETWORK_TYPE_EHRPD -> "3G-eHRPD"
        TelephonyManager.NETWORK_TYPE_HSUPA -> "3G-HSUPA"
        TelephonyManager.NETWORK_TYPE_HSDPA -> "3G-HSDPA"
        TelephonyManager.NETWORK_TYPE_HSPA -> "3G-HSPA"
        TelephonyManager.NETWORK_TYPE_HSPAP -> "3G-HSPA+"
        TelephonyManager.NETWORK_TYPE_UMTS -> "3G-UMTS"
        TelephonyManager.NETWORK_TYPE_TD_SCDMA -> "3G-SCDMA"
        TelephonyManager.NETWORK_TYPE_LTE -> "LTE"
        TelephonyManager.NETWORK_TYPE_IWLAN -> "4G"
        TelephonyManager.NETWORK_TYPE_NR -> "5G"
        else -> null
    }
}

// Shell access

fun getShellAccessRequiredDialog(context: Context): AlertDialogData {

    val intent = Intent(context, MainActivity::class.java)
    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK

    return AlertDialogData(
        titleResource = R.string.shell_access_required,
        messageResource = R.string.shell_access_not_set_up,
        positiveButtonResource = R.string.ok,
        onPositiveButtonClicked = {
            context.startActivity(intent)
        }
    )
}

data class AlertDialogData(
    val iconResource: Int? = null,
    val titleResource: Int,
    val messageResource: Int,
    val positiveButtonResource: Int,
    val onPositiveButtonClicked: () -> Unit = {}
)

fun AlertDialogData.toDialog(context: Context): Dialog {
    val dialog = AlertDialog.Builder(context)
        .setTitle(titleResource)
        .setMessage(messageResource)
        .setPositiveButton(positiveButtonResource) { _, _ ->
            onPositiveButtonClicked()
        }
        .setCancelable(true)
    iconResource?.let {
        dialog.setIcon(it)
    }
    return dialog.create()
}

fun executeShellCommand(command: String, context: Context): Shell.Result? {
    val preferences = BITPreferences(context)
    val shellMethod = runBlocking {
         preferences.getShellMethod.first()
    }

    when (shellMethod) {
        ShellMethod.ROOT -> {
            return Shell.cmd(command).exec()
        }
        ShellMethod.AUTO -> {
            if (Shell.isAppGrantedRoot() == true) {
                return Shell.cmd(command).exec()
            }
        }
    }

    return null
}

fun executeShellCommandAsync(
    command: String,
    context: Context,
    callback: ((Shell.Result?) -> Unit)? = {}
) {
    ExecutorServiceSingleton.getInstance().execute {
        val result = executeShellCommand(command, context)
        callback?.let { it(result) }
    }
}

fun hasShellAccess(context: Context): Boolean {

    val preferences = BITPreferences(context)
    val shellMethod = runBlocking {
        preferences.getShellMethod.first()
    }

    return when (shellMethod) {
        ShellMethod.ROOT -> {true}
        ShellMethod.AUTO -> {
            Shell.isAppGrantedRoot() == true
        }
    }
}

fun saveTileUsed(instance: TileService) {
    // TODO replace with datastore
    PreferenceManager.getDefaultSharedPreferences(instance)
        ?.edit()
        ?.putLong(instance.javaClass.name, System.currentTimeMillis())
        ?.apply()
}

fun getInstallId(context: Context): String = runBlocking {

    val preferences = BITPreferences(context)
    val existingId =  preferences.getInstallationId.firstOrNull()

    existingId ?: run {
        val uuid = UUID.randomUUID().toString()
        preferences.setInstallationId(uuid)
        uuid
    }
}

private fun <T> wasTileUsedInLastXHours(
    javaClass: Class<T>,
    sharedPref: SharedPreferences,
    hours: Int = 12
): Boolean {
    val timestamp: Long = try {
        sharedPref.getLong(javaClass.name, 0)
    } catch (e: java.lang.Exception) {
        0
    }
    val current = System.currentTimeMillis()
    val diff = current - timestamp
    val maxDiff = hoursToMs(hours.toLong())
    return diff <= maxDiff
}

private fun hoursToMs(hours: Long): Long {
    return hours * 60 * 60 * 1000
}
