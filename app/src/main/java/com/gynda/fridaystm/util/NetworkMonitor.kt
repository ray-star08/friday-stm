package com.gynda.fridaystm.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/**
 * Connectivity gate for the offline-first presensi flow, behind an interface
 * so repositories unit-test with a fake instead of `ConnectivityManager`.
 */
interface NetworkMonitor {
    /** `true` when the device currently has a validated internet path. */
    fun isOnline(): Boolean
}

/**
 * [NetworkMonitor] backed by `ConnectivityManager` (API 23+ path; minSdk is
 * 29, so no legacy fallback needed). Requires `ACCESS_NETWORK_STATE`
 * (normal permission, manifest-only). Holds the application context only.
 */
class AndroidNetworkMonitor(context: Context) : NetworkMonitor {

    private val appContext = context.applicationContext

    override fun isOnline(): Boolean {
        val manager = appContext.getSystemService(ConnectivityManager::class.java)
            ?: return false
        val network = manager.activeNetwork ?: return false
        val caps = manager.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}
