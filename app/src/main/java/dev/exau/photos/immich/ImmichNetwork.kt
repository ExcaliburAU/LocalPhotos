package dev.exau.photos.immich

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

object ImmichNetwork {
    const val WIFI_ONLY_MESSAGE = "Backup uses Wi-Fi only"

    fun onWifi(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        if (cm.isActiveNetworkMetered) return false
        return hasWifiOrEthernet(cm)
    }

    @Suppress("DEPRECATION")
    private fun hasWifiOrEthernet(cm: ConnectivityManager): Boolean {
        val active = cm.getNetworkCapabilities(cm.activeNetwork)
        if (active != null && isWifiOrEthernet(active)) return true
        return cm.allNetworks.any { network ->
            val caps = cm.getNetworkCapabilities(network) ?: return@any false
            isWifiOrEthernet(caps)
        }
    }

    private fun isWifiOrEthernet(caps: NetworkCapabilities): Boolean {
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }
}
