package com.example.blocker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.catalog.BankingAppCatalog
import com.example.data.catalog.DeviceAppScanner
import com.example.data.repository.VaultRepository
import com.example.security.SystemPermissionsHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class BlockerMonitorService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var monitorJob: Job? = null
    private lateinit var repository: VaultRepository
    private var installReceiver: PackageInstallReceiver? = null

    @Volatile
    private var cachedBlockedPackages: Set<String> = emptySet()

    private var lastHandledEventTimestamp: Long = 0L
    private var currentForegroundPkg: String? = null
    private var lastLaunchMillis: Long = 0L
    private var lastLoggedLaunchMillis: Long = 0L
    private var lastBlockedLaunchPkg: String? = null

    override fun onCreate() {
        super.onCreate()
        repository = VaultRepository.getInstance(this)
        ensureNotificationChannel()
        registerDynamicInstallReceiver()
    }

    private fun registerDynamicInstallReceiver() {
        if (installReceiver != null) return
        val receiver = PackageInstallReceiver()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(
            this,
            receiver,
            filter,
            ContextCompat.RECEIVER_EXPORTED
        )
        installReceiver = receiver
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopMonitoringAndSelf()
            return START_NOT_STICKY
        }

        val config = repository.securityPrefs.getConfig()
        if (!config.isProtectionActive) {
            stopMonitoringAndSelf()
            return START_NOT_STICKY
        }

        isRunning = true
        startAsForeground(buildNotification(isReady = SystemPermissionsHelper.hasUsageStatsPermission(this)))
        startMonitoringLoop()
        return START_STICKY
    }

    private fun startAsForeground(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun startMonitoringLoop() {
        if (monitorJob?.isActive == true) return
        monitorJob = serviceScope.launch {
            launch {
                repository.activeBlockedPackageNames.collect { list ->
                    cachedBlockedPackages = list.toSet()
                }
            }

            cachedBlockedPackages = repository.getActiveBlockedPackages()
            var lastReadyState = SystemPermissionsHelper.hasUsageStatsPermission(this@BlockerMonitorService)
            var scanTickCount = 0

            while (isActive) {
                val rolledCycle = repository.securityPrefs.checkAndRolloverTwoYearCycleIfNeeded()
                if (rolledCycle != null) {
                    repository.logSecurityEvent(
                        eventType = VaultRepository.EVENT_LOCK_STARTED,
                        title = "Auto-Locked for 2-Year Cycle #$rolledCycle",
                        detail = "VaultLock automatically re-engaged its 2-year self-lock and issued a fresh 1-Time PIN cycle."
                    )
                }

                val config = repository.securityPrefs.getConfig()
                if (!config.isProtectionActive) {
                    WebFilterVpnService.stop(this@BlockerMonitorService)
                    stopMonitoringAndSelf()
                    break
                }

                if (config.isAiStudioWebBlockerEnabled && SystemPermissionsHelper.hasVpnPermission(this@BlockerMonitorService)) {
                    if (!WebFilterVpnService.isRunning) {
                        WebFilterVpnService.start(this@BlockerMonitorService)
                    }
                } else if (WebFilterVpnService.isRunning) {
                    WebFilterVpnService.stop(this@BlockerMonitorService)
                }

                if (scanTickCount % INSTALL_SCAN_EVERY_TICKS == 0) {
                    val installedApps = DeviceAppScanner.getInstalledLauncherApps(this@BlockerMonitorService)
                    repository.autoDiscoverAndLockInstalledBanksAndMail(installedApps)
                }
                scanTickCount++

                val hasUsage = SystemPermissionsHelper.hasUsageStatsPermission(this@BlockerMonitorService)
                if (hasUsage != lastReadyState) {
                    lastReadyState = hasUsage
                    val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                    nm?.notify(NOTIFICATION_ID, buildNotification(isReady = hasUsage))
                }

                if (hasUsage) {
                    checkForegroundUsageEvents(
                        isTamperGuardEnabled = config.isTamperGuardEnabled,
                        isInstallBlockerEnabled = config.isInstallBlockerEnabled,
                        isAiStudioWebBlockerEnabled = config.isAiStudioWebBlockerEnabled
                    )
                }

                delay(POLL_INTERVAL_MS)
            }
        }
    }

    private suspend fun checkForegroundUsageEvents(
        isTamperGuardEnabled: Boolean,
        isInstallBlockerEnabled: Boolean,
        isAiStudioWebBlockerEnabled: Boolean
    ) {
        val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return
        val now = System.currentTimeMillis()
        val windowStart = if (lastHandledEventTimestamp > 0L) {
            (lastHandledEventTimestamp - 200L).coerceAtLeast(now - 10_000L)
        } else {
            now - 10_000L
        }

        val events = usageStatsManager.queryEvents(windowStart, now)
        val event = UsageEvents.Event()
        var sawNewEvent = false

        if (events != null) {
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                if (event.timeStamp <= lastHandledEventTimestamp) continue

                val isForegroundResume = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    event.eventType == UsageEvents.Event.ACTIVITY_RESUMED
                } else {
                    @Suppress("DEPRECATION")
                    event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND
                }

                val isForegroundPause = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    event.eventType == UsageEvents.Event.ACTIVITY_PAUSED
                } else {
                    @Suppress("DEPRECATION")
                    event.eventType == UsageEvents.Event.MOVE_TO_BACKGROUND
                }

                if (isForegroundResume) {
                    val pkg = event.packageName
                    val cls = event.className?.lowercase().orEmpty()
                    val isUpdateClass = cls.contains("update") || cls.contains("fota") || cls.contains("software") || cls.contains("systemupdate")
                    if (isUpdateClass) {
                        currentForegroundPkg = null
                    } else {
                        currentForegroundPkg = pkg
                    }
                    lastHandledEventTimestamp = event.timeStamp
                    sawNewEvent = true
                } else if (isForegroundPause && currentForegroundPkg == event.packageName) {
                    currentForegroundPkg = null
                    lastHandledEventTimestamp = event.timeStamp
                    sawNewEvent = true
                }
            }
        }

        // Fallback to recent UsageStats if no event transition occurred in this poll
        if (!sawNewEvent && currentForegroundPkg == null) {
            val stats = usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                now - 15_000L,
                now
            )
            val recent = stats?.maxByOrNull { it.lastTimeUsed }
            if (recent != null && (now - recent.lastTimeUsed) < 3_500L) {
                currentForegroundPkg = recent.packageName
            }
        }

        val targetPkg = currentForegroundPkg ?: return
        if (targetPkg == packageName) return

        // Allow Android OS updates and Samsung OneUI software updates ("letting person update in phone")
        val isSystemUpdate = BankingAppCatalog.systemUpdatePackages.contains(targetPkg) ||
            targetPkg.contains("updater", ignoreCase = true) ||
            targetPkg.contains("fota", ignoreCase = true) ||
            targetPkg.contains("softwareupdate", ignoreCase = true)

        if (isSystemUpdate) {
            return
        }

        val prefs = repository.securityPrefs
        val isBlockedTarget = cachedBlockedPackages.contains(targetPkg) && !prefs.isTempAppOverrideActive(targetPkg)
        val hasAdminGrace = prefs.isInSettingsGraceWindow() || prefs.isTempAppOverrideActive(targetPkg)
        val isInstallGuardTarget = isInstallBlockerEnabled &&
            BankingAppCatalog.installGuardPackages.contains(targetPkg) &&
            !hasAdminGrace
        val isTamperProtectedSettings = isTamperGuardEnabled &&
            BankingAppCatalog.protectedSystemPackages.contains(targetPkg) &&
            !hasAdminGrace
        // If AI Studio Web Blocker is enabled but VPN DNS firewall is disconnected/ungranted,
        // guard all browsers (Chrome, Samsung Internet, Firefox, Edge, Brave, etc.) to stop bypass.
        val isBrowserBypassGuard = isAiStudioWebBlockerEnabled &&
            !WebFilterVpnService.isRunning &&
            BankingAppCatalog.isBrowserApp(targetPkg) &&
            !hasAdminGrace

        if (!isBlockedTarget && !isInstallGuardTarget && !isTamperProtectedSettings && !isBrowserBypassGuard) return

        val nowElapsed = System.currentTimeMillis()
        if (lastBlockedLaunchPkg == targetPkg && (nowElapsed - lastLaunchMillis) < RELAUNCH_DEBOUNCE_MS) {
            return
        }

        val shouldLogEvent = lastBlockedLaunchPkg != targetPkg ||
            (nowElapsed - lastLoggedLaunchMillis) >= LOG_DEBOUNCE_MS

        lastBlockedLaunchPkg = targetPkg
        lastLaunchMillis = nowElapsed

        val displayName = repository.getAppDisplayName(targetPkg)
        if (shouldLogEvent) {
            lastLoggedLaunchMillis = nowElapsed
            when {
                isBrowserBypassGuard -> {
                    repository.logSecurityEvent(
                        eventType = VaultRepository.EVENT_WEB_BLOCKED,
                        title = "Browser Guarded (AI Studio Lock)",
                        detail = "Blocked $displayName because AI Studio VPN DNS Shield is not running.",
                        packageName = targetPkg
                    )
                }
                isInstallGuardTarget -> {
                    repository.logSecurityEvent(
                        eventType = VaultRepository.EVENT_INSTALL_BLOCKED,
                        title = "Blocked App Install Source",
                        detail = "Blocked opening $displayName to prevent installing Chime, Yahoo Mail, or banking apps.",
                        packageName = targetPkg
                    )
                }
                isTamperProtectedSettings -> {
                    repository.logSecurityEvent(
                        eventType = VaultRepository.EVENT_TAMPER_ATTEMPT,
                        title = "Anti-Tamper Shield Triggered",
                        detail = "Blocked unauthorized access to $displayName while 2-year lock is active.",
                        packageName = targetPkg
                    )
                }
                else -> {
                    repository.recordBlockedAttempt(targetPkg, displayName)
                }
            }
        }

        launchBlockShield(
            blockedPackage = targetPkg,
            blockedAppName = displayName,
            isTamperShield = isTamperProtectedSettings,
            isInstallGuard = isInstallGuardTarget,
            isWebShield = isBrowserBypassGuard
        )
    }

    private fun launchBlockShield(
        blockedPackage: String,
        blockedAppName: String,
        isTamperShield: Boolean,
        isInstallGuard: Boolean,
        isWebShield: Boolean = false
    ) {
        val intent = Intent(this, BlockedOverlayActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
            putExtra(BlockedOverlayActivity.EXTRA_BLOCKED_PACKAGE, blockedPackage)
            putExtra(BlockedOverlayActivity.EXTRA_BLOCKED_APP_NAME, blockedAppName)
            putExtra(BlockedOverlayActivity.EXTRA_IS_TAMPER_SHIELD, isTamperShield)
            putExtra(BlockedOverlayActivity.EXTRA_IS_INSTALL_GUARD, isInstallGuard)
            putExtra(BlockedOverlayActivity.EXTRA_IS_WEB_SHIELD, isWebShield)
        }

        if (SystemPermissionsHelper.hasOverlayPermission(this)) {
            startActivity(intent)
        } else {
            // Fallback high-priority alert if Display Over Other Apps hasn't been enabled yet
            val pendingIntent = PendingIntent.getActivity(
                this,
                blockedPackage.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val alert = NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("$blockedAppName is Locked by VaultLock")
                .setContentText("Enable 'Display over other apps' in VaultLock to auto-project the lock shield.")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build()
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.notify(ALERT_NOTIFICATION_ID, alert)
            try {
                startActivity(intent)
            } catch (_: SecurityException) {
                // Background launch blocked until user grants SYSTEM_ALERT_WINDOW
            }
        }
    }

    private fun stopMonitoringAndSelf() {
        isRunning = false
        monitorJob?.cancel()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    private fun buildNotification(isReady: Boolean): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (isReady) {
            "VaultLock Active Protection"
        } else {
            "VaultLock: Usage Access Needed"
        }
        val text = if (isReady) {
            "Chime, banking apps & Yahoo Mail are actively blocked for your 2-year savings lock."
        } else {
            "Tap to grant Usage Access so VaultLock can detect and block Chime, banks & Yahoo Mail."
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(openAppIntent)
            .build()
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_desc)
                setShowBadge(false)
            }
            nm.createNotificationChannel(channel)

            val alertChannel = NotificationChannel(
                ALERT_CHANNEL_ID,
                "VaultLock Block Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts when a restricted banking or Yahoo Mail app is opened."
            }
            nm.createNotificationChannel(alertChannel)
        }
    }

    override fun onDestroy() {
        isRunning = false
        installReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (_: IllegalArgumentException) {
            }
            installReceiver = null
        }
        monitorJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL_ID = "vaultlock_guard_channel"
        private const val ALERT_CHANNEL_ID = "vaultlock_block_alerts"
        private const val NOTIFICATION_ID = 4105
        private const val ALERT_NOTIFICATION_ID = 4106
        private const val POLL_INTERVAL_MS = 250L
        private const val INSTALL_SCAN_EVERY_TICKS = 20
        private const val RELAUNCH_DEBOUNCE_MS = 750L
        private const val LOG_DEBOUNCE_MS = 4_000L
        private const val ACTION_STOP = "com.example.blocker.ACTION_STOP"

        @Volatile
        var isRunning: Boolean = false
            private set

        fun start(context: Context) {
            val intent = Intent(context, BlockerMonitorService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, BlockerMonitorService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}
