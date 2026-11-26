package com.example.security

import android.Manifest
import android.app.AppOpsManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.os.Process
import android.provider.Settings
import androidx.core.content.ContextCompat

data class SystemProtectionStatus(
    val usageStatsGranted: Boolean = false,
    val overlayGranted: Boolean = false,
    val deviceAdminActive: Boolean = false,
    val notificationsGranted: Boolean = true,
    val vpnConsentGranted: Boolean = false
) {
    val isCoreBlockingReady: Boolean
        get() = usageStatsGranted && overlayGranted

    val readinessCount: Int
        get() = listOf(
            usageStatsGranted,
            overlayGranted,
            deviceAdminActive,
            notificationsGranted,
            vpnConsentGranted
        ).count { it }
}

object SystemPermissionsHelper {

    fun getStatus(context: Context): SystemProtectionStatus {
        return SystemProtectionStatus(
            usageStatsGranted = hasUsageStatsPermission(context),
            overlayGranted = hasOverlayPermission(context),
            deviceAdminActive = isDeviceAdminActive(context),
            notificationsGranted = hasNotificationPermission(context),
            vpnConsentGranted = hasVpnPermission(context)
        )
    }

    fun hasUsageStatsPermission(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
            ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun hasOverlayPermission(context: Context): Boolean {
        return Settings.canDrawOverlays(context)
    }

    fun isDeviceAdminActive(context: Context): Boolean {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
            ?: return false
        return dpm.isAdminActive(deviceAdminComponent(context))
    }

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun deviceAdminComponent(context: Context): ComponentName {
        return ComponentName(context, BlockerDeviceAdminReceiver::class.java)
    }

    fun createUsageAccessIntent(): Intent {
        return Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun createOverlayPermissionIntent(context: Context): Intent {
        return Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}")
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun createDeviceAdminIntent(context: Context): Intent {
        return Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, deviceAdminComponent(context))
            putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "Activates uninstall protection so VaultLock cannot be removed or bypassed without Administrator PIN verification."
            )
        }
    }

    fun createAppDetailsIntent(context: Context): Intent {
        return Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:${context.packageName}")
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun hasVpnPermission(context: Context): Boolean {
        return try {
            VpnService.prepare(context) == null
        } catch (_: Exception) {
            false
        }
    }

    fun createVpnPermissionIntent(context: Context): Intent? {
        return try {
            VpnService.prepare(context)
        } catch (_: Exception) {
            null
        }
    }

    fun deactivateDeviceAdmin(context: Context) {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
            ?: return
        val component = deviceAdminComponent(context)
        if (dpm.isAdminActive(component)) {
            dpm.removeActiveAdmin(component)
        }
    }
}
