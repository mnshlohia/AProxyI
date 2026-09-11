package com.networkinspector.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.networkinspector.NetworkInspector

/**
 * BroadcastReceiver to handle clearing requests from notification action.
 */
internal class ClearRequestsReceiver : BroadcastReceiver() {
    
    override fun onReceive(context: Context, intent: Intent?) {
        NetworkInspector.clearAll()
    }
}




