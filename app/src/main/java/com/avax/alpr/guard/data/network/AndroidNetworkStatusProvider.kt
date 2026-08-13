package com.avax.alpr.guard.data.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

class AndroidNetworkStatusProvider(context: Context) : NetworkStatusProvider {

    private val connectivityManager = context.getSystemService(ConnectivityManager::class.java)

    override fun isNetworkAvailable(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}