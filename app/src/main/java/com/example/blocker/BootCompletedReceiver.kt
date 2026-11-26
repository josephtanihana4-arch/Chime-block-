package com.example.blocker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.security.SecurityPrefs

class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            val config = SecurityPrefs(context).getConfig()
            if (config.isProtectionActive) {
                BlockerMonitorService.start(context)
                if (config.isAiStudioWebBlockerEnabled) {
                    WebFilterVpnService.start(context)
                }
            }
        }
    }
}
