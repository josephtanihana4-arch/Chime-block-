package com.example.security

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import com.example.blocker.BlockerMonitorService
import com.example.data.repository.VaultRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class BlockerDeviceAdminReceiver : DeviceAdminReceiver() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        val pendingResult = goAsync()
        scope.launch {
            try {
                val repo = VaultRepository.getInstance(context)
                repo.logSecurityEvent(
                    eventType = VaultRepository.EVENT_LOCK_STARTED,
                    title = "Uninstall Protection Enabled",
                    detail = "Device Administrator guard is active. Standard app removal is blocked."
                )
                if (repo.securityPrefs.getConfig().isProtectionActive) {
                    BlockerMonitorService.start(context)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        val pendingResult = goAsync()
        scope.launch {
            try {
                val repo = VaultRepository.getInstance(context)
                repo.logSecurityEvent(
                    eventType = VaultRepository.EVENT_TAMPER_ATTEMPT,
                    title = "Device Admin Deactivation Attempted",
                    detail = "Someone attempted to disable VaultLock uninstall protection from Android Settings."
                )
            } finally {
                pendingResult.finish()
            }
        }
        return "VaultLock Protection is locked for your 2-year savings commitment. One-Time Administrator PIN authentication inside VaultLock is required before disabling protection."
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        val pendingResult = goAsync()
        scope.launch {
            try {
                val repo = VaultRepository.getInstance(context)
                repo.logSecurityEvent(
                    eventType = VaultRepository.EVENT_TAMPER_ATTEMPT,
                    title = "Uninstall Protection Deactivated",
                    detail = "Device Administrator permission was removed."
                )
            } finally {
                pendingResult.finish()
            }
        }
    }
}
