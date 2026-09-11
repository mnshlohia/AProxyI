package com.networkinspector.sample

import android.app.Application
import com.networkinspector.NetworkInspector

class SampleApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Compiles against both artifacts. In release this resolves to an empty
        // method in :library-no-op.
        NetworkInspector.init(this)
    }
}
