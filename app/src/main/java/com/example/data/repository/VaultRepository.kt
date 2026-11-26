package com.example.data.repository

import android.content.Context
import com.example.data.catalog.BankingAppCatalog
import com.example.data.catalog.DeviceAppScanner
import com.example.data.catalog.InstalledDeviceApp
import com.example.data.db.BlockedAppEntity
import com.example.data.db.SavingsDepositEntity
import com.example.data.db.SecurityEventEntity
import com.example.data.db.VaultDao
import com.example.data.db.VaultDatabase
import com.example.security.SecurityPrefs
import com.example.security.VaultSecurityConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

class VaultRepository(
    private val context: Context,
    private val dao: VaultDao,
    val securityPrefs: SecurityPrefs
) {
    val securityConfig: StateFlow<VaultSecurityConfig> = securityPrefs.configFlow
    val blockedApps: Flow<List<BlockedAppEntity>> = dao.observeBlockedApps()
    val activeBlockedPackageNames: Flow<List<String>> = dao.observeActiveBlockedPackageNames()
    val deposits: Flow<List<SavingsDepositEntity>> = dao.observeDeposits()
    val totalSavedCents: Flow<Long> = dao.observeTotalSavedCents()
    val securityEvents: Flow<List<SecurityEventEntity>> = dao.observeSecurityEvents()
    val totalBlockedAttempts: Flow<Int> = dao.observeTotalBlockedAttempts()

    suspend fun ensureSeeded() {
        dao.insertBlockedAppsIfMissing(BankingAppCatalog.toSeedEntities())
    }

    suspend fun setAppBlocked(packageName: String, displayName: String, blocked: Boolean) {
        dao.setAppBlocked(packageName, blocked)
        dao.insertSecurityEvent(
            SecurityEventEntity(
                eventType = if (blocked) EVENT_RULE_ENABLED else EVENT_RULE_DISABLED,
                title = if (blocked) "Blocked $displayName" else "Unblocked $displayName",
                detail = if (blocked) {
                    "Access restricted under 2-year savings lock."
                } else {
                    "Administrator removed restriction via PIN verification."
                },
                packageName = packageName
            )
        )
    }

    suspend fun addCustomBlockedApp(packageName: String, displayName: String, category: String) {
        val cleanPkg = packageName.trim()
        val cleanName = displayName.trim().ifBlank { cleanPkg }
        dao.upsertBlockedApp(
            BlockedAppEntity(
                packageName = cleanPkg,
                displayName = cleanName,
                category = category,
                isBlocked = true,
                isCustom = true
            )
        )
        dao.insertSecurityEvent(
            SecurityEventEntity(
                eventType = EVENT_RULE_ENABLED,
                title = "Added custom block: $cleanName",
                detail = "Package $cleanPkg added to active blocklist.",
                packageName = cleanPkg
            )
        )
    }

    suspend fun removeCustomBlockedApp(app: BlockedAppEntity) {
        dao.deleteCustomBlockedApp(app.packageName)
        dao.insertSecurityEvent(
            SecurityEventEntity(
                eventType = EVENT_RULE_DISABLED,
                title = "Removed custom app: ${app.displayName}",
                detail = "Package ${app.packageName} removed by Administrator.",
                packageName = app.packageName
            )
        )
    }

    suspend fun recordBlockedAttempt(packageName: String, appLabel: String) {
        dao.incrementBlockAttempts(packageName)
        dao.insertSecurityEvent(
            SecurityEventEntity(
                eventType = EVENT_BLOCK_INTERCEPTED,
                title = "Blocked launch: $appLabel",
                detail = "VaultLock shield intercepted $packageName.",
                packageName = packageName
            )
        )
    }

    suspend fun logSecurityEvent(eventType: String, title: String, detail: String, packageName: String? = null) {
        dao.insertSecurityEvent(
            SecurityEventEntity(
                eventType = eventType,
                title = title,
                detail = detail,
                packageName = packageName
            )
        )
    }

    suspend fun addSavingsDeposit(amountCents: Long, note: String, monthNumber: Int) {
        dao.insertDeposit(
            SavingsDepositEntity(
                amountCents = amountCents,
                note = note.trim().ifBlank { "Month $monthNumber savings deposit" },
                monthNumber = monthNumber.coerceIn(1, 24)
            )
        )
        dao.insertSecurityEvent(
            SecurityEventEntity(
                eventType = EVENT_SAVINGS_LOGGED,
                title = "Logged savings deposit",
                detail = "Added ${formatCents(amountCents)} toward Month $monthNumber milestone."
            )
        )
    }

    suspend fun deleteSavingsDeposit(id: Long) {
        dao.deleteDepositById(id)
    }

    suspend fun getActiveBlockedPackages(): Set<String> {
        return dao.getActiveBlockedPackageNames().toSet()
    }

    suspend fun getAppDisplayName(packageName: String): String {
        return dao.getBlockedApp(packageName)?.displayName
            ?: BankingAppCatalog.friendlySystemAppName(packageName)
            ?: BankingAppCatalog.getCatalogMatch(packageName)?.displayName
            ?: packageName
    }

    suspend fun scanInstalledApps(): Pair<Set<String>, List<InstalledDeviceApp>> = withContext(Dispatchers.IO) {
        val installedLaunchers = DeviceAppScanner.getInstalledLauncherApps(context)
        val installedSet = installedLaunchers.map { it.packageName }.toMutableSet()
        for (known in BankingAppCatalog.knownBankingApps) {
            if (DeviceAppScanner.isPackageInstalled(context, known.packageName)) {
                installedSet.add(known.packageName)
            }
        }
        installedSet to installedLaunchers
    }

    suspend fun autoDiscoverAndLockInstalledBanksAndMail(
        installedLaunchers: List<InstalledDeviceApp>
    ): Int = withContext(Dispatchers.IO) {
        val existing = dao.getActiveBlockedPackageNames().toSet()
        val discovered = installedLaunchers
            .filter { BankingAppCatalog.isLikelyBankOrMailApp(it.packageName, it.displayName) }
            .filter { dao.getBlockedApp(it.packageName) == null }
            .map { app ->
                val category = if (app.packageName.contains("yahoo", ignoreCase = true) ||
                    app.displayName.contains("yahoo", ignoreCase = true)
                ) {
                    BankingAppCatalog.CAT_MAIL
                } else {
                    BankingAppCatalog.CAT_BANK
                }
                BlockedAppEntity(
                    packageName = app.packageName,
                    displayName = app.displayName,
                    category = category,
                    isBlocked = true,
                    isCustom = false
                )
            }
        if (discovered.isNotEmpty()) {
            dao.insertBlockedAppsIfMissing(discovered)
            dao.insertSecurityEvent(
                SecurityEventEntity(
                    eventType = EVENT_RULE_ENABLED,
                    title = "Auto-Locked ${discovered.size} Installed App(s)",
                    detail = discovered.joinToString(", ") { it.displayName }
                )
            )
        }
        discovered.count { !existing.contains(it.packageName) }
    }

    /**
     * Triggered in real time when Android broadcasts ACTION_PACKAGE_ADDED / PACKAGE_REPLACED.
     * Detects Chime, Yahoo Mail, or banking apps on install and immediately locks them into Room
     * + writes an EVENT_INSTALL_BLOCKED audit log entry.
     */
    suspend fun handleNewlyInstalledPackage(
        packageName: String,
        resolvedLabel: String? = null
    ): BlockedAppEntity? = withContext(Dispatchers.IO) {
        if (packageName.isBlank() || packageName == context.packageName) return@withContext null

        val catalogMatch = BankingAppCatalog.getCatalogMatch(packageName)
        val existing = dao.getBlockedApp(packageName)

        val displayName = resolvedLabel?.takeIf { it.isNotBlank() }
            ?: run {
                try {
                    val pm = context.packageManager
                    val appInfo = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                        pm.getApplicationInfo(
                            packageName,
                            android.content.pm.PackageManager.ApplicationInfoFlags.of(0)
                        )
                    } else {
                        @Suppress("DEPRECATION")
                        pm.getApplicationInfo(packageName, 0)
                    }
                    pm.getApplicationLabel(appInfo).toString().takeIf { it.isNotBlank() }
                } catch (_: Exception) {
                    null
                }
            }
            ?: existing?.displayName
            ?: catalogMatch?.displayName
            ?: packageName

        val isRestrictedInstall = catalogMatch != null ||
            BankingAppCatalog.isLikelyBankOrMailApp(packageName, displayName) ||
            (existing?.isBlocked == true)

        if (!isRestrictedInstall) return@withContext null

        val category = existing?.category
            ?: catalogMatch?.category
            ?: if (packageName.contains("yahoo", ignoreCase = true) ||
                displayName.contains("yahoo", ignoreCase = true)
            ) {
                BankingAppCatalog.CAT_MAIL
            } else {
                BankingAppCatalog.CAT_BANK
            }

        val entity = BlockedAppEntity(
            packageName = packageName,
            displayName = displayName,
            category = category,
            isBlocked = true,
            isCustom = existing?.isCustom ?: false,
            blockAttemptsCount = existing?.blockAttemptsCount ?: 0
        )
        dao.upsertBlockedApp(entity)
        dao.insertSecurityEvent(
            SecurityEventEntity(
                eventType = EVENT_INSTALL_BLOCKED,
                title = "Forbidden Install Blocked & Uninstall Prompted: $displayName",
                detail = "Detected installation of $displayName ($packageName), locked it under VaultLock, and triggered system uninstallation.",
                packageName = packageName
            )
        )
        try {
            val uninstallIntent = android.content.Intent(
                android.content.Intent.ACTION_DELETE,
                android.net.Uri.parse("package:$packageName")
            ).apply {
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(uninstallIntent)
        } catch (_: Exception) {
        }
        entity
    }

    companion object {
        const val EVENT_BLOCK_INTERCEPTED = "BLOCK_INTERCEPTED"
        const val EVENT_TAMPER_ATTEMPT = "TAMPER_ATTEMPT"
        const val EVENT_INSTALL_BLOCKED = "INSTALL_BLOCKED"
        const val EVENT_WEB_BLOCKED = "WEB_BLOCKED"
        const val EVENT_RULE_ENABLED = "RULE_ENABLED"
        const val EVENT_RULE_DISABLED = "RULE_DISABLED"
        const val EVENT_ADMIN_OVERRIDE = "ADMIN_OVERRIDE"
        const val EVENT_PIN_CHANGED = "PIN_CHANGED"
        const val EVENT_SAVINGS_LOGGED = "SAVINGS_LOGGED"
        const val EVENT_LOCK_STARTED = "LOCK_STARTED"
        const val EVENT_ONE_TIME_PIN_USED = "ONE_TIME_PIN_USED"

        @Volatile
        private var INSTANCE: VaultRepository? = null

        fun getInstance(context: Context): VaultRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: VaultRepository(
                    context = context.applicationContext,
                    dao = VaultDatabase.getInstance(context).vaultDao(),
                    securityPrefs = SecurityPrefs(context)
                ).also { INSTANCE = it }
            }
        }

        fun formatCents(cents: Long): String {
            val dollars = cents / 100
            val rem = kotlin.math.abs(cents % 100)
            return "$${"%,d".format(dollars)}.${"%02d".format(rem)}"
        }
    }
}
