package com.avax.alpr.guard.data.network

interface NetworkStatusProvider {
    fun isNetworkAvailable(): Boolean
}