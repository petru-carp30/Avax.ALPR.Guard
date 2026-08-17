package com.avax.alpr.guard

import android.app.Application

class GuardApplication : Application() {
    val container by lazy { AppContainer(this) }
}