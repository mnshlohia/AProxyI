package com.aproxyi.sample

import android.app.Application
import com.aproxyi.AProxyI

class SampleApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Compiles against both artifacts. In release this resolves to an empty
        // method in :library-no-op.
        AProxyI.init(this)
    }
}
