package com.example.blocker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.R
import com.example.data.repository.VaultRepository
import com.example.security.SystemPermissionsHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Listens in real time for ACTION_PACKAGE_ADDED / ACTION_PACKAGE_REPLACED broadcasts
 * to immediately detect when Chime, Yahoo Mail, or any banking app is installed on the device,
 * add/lock it in the active blocklist, record an audit event, and project the lock shield.
 */
class PackageInstallReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_PACKAGE_ADDED && action != Intent.ACTION_PACKAGE_REPLACED) {
            return
        }

        // Allow app updates ("letting person update app softwares")
        val isReplacing = action == Intent.ACTION_PACKAGE_REPLACED || intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)
        if (isReplacing) {
            return
        }

        val installedPackage = intent.data?.schemeSpecificPart ?: return
        if (installedPackage == context.packageName) return

        val pendingResult = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val repository = VaultRepository.getInstance(appContext)
                repository.ensureSeeded()
                val config = repository.securityPrefs.getConfig()
                val blockedApp = repository.handleNewlyInstalledPackage(installedPackage)

                if (blockedApp != null && config.isProtectionActive) {
                    BlockerMonitorService.start(appContext)
                    postInstallBlockedNotification(
                        context = appContext,
                        appName = blockedApp.displayName,
                        packageName = blockedApp.packageName
                    )
                    if (SystemPermissionsHelper.hasOverlayPermission(appContext)) {
                        val shieldIntent = Intent(appContext, BlockedOverlayActivity::class.java).apply {
                            addFlags(
                                Intent.FLAG_ACTIVITY_NEW_TASK or
                                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                                    Intent.FLAG_ACTIVITY_SINGLE_TOP
                            )
                            putExtra(BlockedOverlayActivity.EXTRA_BLOCKED_PACKAGE, blockedApp.packageName)
                            putExtra(BlockedOverlayActivity.EXTRA_BLOCKED_APP_NAME, blockedApp.displayName)
                            putExtra(BlockedOverlayActivity.EXTRA_IS_TAMPER_SHIELD, false)
                            putExtra(BlockedOverlayActivity.EXTRA_IS_INSTALL_GUARD, true)
                        }
                        try {
                            appContext.startActivity(shieldIntent)
                        } catch (_: SecurityException) {
                            // Ignore if background activity start is denied; notification + service loop enforce lock
                        }
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun postInstallBlockedNotification(
        context: Context,
        appName: String,
        packageName: String
    ) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                ALERT_CHANNEL_ID,
                "VaultLock Block Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts when restricted banking apps, Yahoo Mail, or new installs are blocked."
            }
            nm.createNotificationChannel(channel)
        }

        val shieldIntent = Intent(context, BlockedOverlayActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(BlockedOverlayActivity.EXTRA_BLOCKED_PACKAGE, packageName)
            putExtra(BlockedOverlayActivity.EXTRA_BLOCKED_APP_NAME, appName)
            putExtra(BlockedOverlayActivity.EXTRA_IS_INSTALL_GUARD, true)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            packageName.hashCode(),
            shieldIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, ALERT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Detected & Blocked: $appName")
            .setContentText("$appName ($packageName) was automatically locked under your 2-year VaultLock.")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "VaultLock detected installation of $appName ($packageName) and immediately locked it for your 2-year savings commitment."
                )
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        nm.notify(ALERT_NOTIFICATION_BASE + (packageName.hashCode() and 0xFF), notification)
    }

    companion object {
        private const val ALERT_CHANNEL_ID = "vaultlock_block_alerts"
        private const val ALERT_NOTIFICATION_BASE = 4200
    }
}
