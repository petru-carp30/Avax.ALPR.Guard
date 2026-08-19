package com.avax.alpr.guard.data.local

enum class AccessLogSyncState {
    Pending,
    Synced,
    Conflict,
    Rejected
}