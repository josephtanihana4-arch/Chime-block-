package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.blocker.BlockerMonitorService
import com.example.blocker.WebFilterVpnService
import com.example.data.catalog.BankingAppCatalog
import com.example.data.catalog.InstalledDeviceApp
import com.example.data.db.BlockedAppEntity
import com.example.data.db.SavingsDepositEntity
import com.example.data.db.SecurityEventEntity
import com.example.data.repository.VaultRepository
import com.example.security.SecurityPrefs
import com.example.security.SystemPermissionsHelper
import com.example.security.SystemProtectionStatus
import com.example.security.VaultSecurityConfig
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed interface PendingAdminAction {
    val promptTitle: String
    val promptSubtitle: String
    val consumesOneTimePin: Boolean

    data class ToggleAppBlock(
        val packageName: String,
        val displayName: String,
        val targetBlocked: Boolean
    ) : PendingAdminAction {
        override val consumesOneTimePin: Boolean = !targetBlocked
        override val promptTitle: String =
            if (targetBlocked) "Lock $displayName" else "Unlock $displayName"
        override val promptSubtitle: String =
            if (targetBlocked) {
                "Enter your 2-year PIN to lock $displayName."
            } else {
                "Enter your One-Time 2-Year PIN to unblock and permit uninstalling $displayName (1 use per 2-year term)."
            }
    }

    data class AddCustomApp(
        val packageName: String,
        val displayName: String,
        val category: String
    ) : PendingAdminAction {
        override val consumesOneTimePin: Boolean = false
        override val promptTitle: String = "Add $displayName to VaultLock"
        override val promptSubtitle: String =
            "Enter your PIN to restrict an additional application."
    }

    data class RemoveCustomApp(val app: BlockedAppEntity) : PendingAdminAction {
        override val consumesOneTimePin: Boolean = true
        override val promptTitle: String = "Remove ${app.displayName}"
        override val promptSubtitle: String =
            "Removing a blocked app consumes your One-Time PIN for this 2-year cycle."
    }

    data class SetProtectionActive(val active: Boolean) : PendingAdminAction {
        override val consumesOneTimePin: Boolean = !active
        override val promptTitle: String =
            if (active) "Activate Blocker Shield" else "Pause Blocker Shield"
        override val promptSubtitle: String =
            if (active) {
                "Enter PIN to engage real-time Chime, bank & Yahoo Mail protection."
            } else {
                "Warning: Pausing protection consumes your single One-Time PIN allowance for this 2-year cycle."
            }
    }

    data class SetTamperGuard(val enabled: Boolean) : PendingAdminAction {
        override val consumesOneTimePin: Boolean = !enabled
        override val promptTitle: String =
            if (enabled) "Enable Anti-Tamper Guard" else "Disable Anti-Tamper Guard"
        override val promptSubtitle: String =
            if (enabled) {
                "Enter PIN to re-enable Settings & Installer protection."
            } else {
                "Disabling Anti-Tamper Guard consumes your One-Time 2-Year PIN."
            }
    }

    data class SetInstallBlocker(val enabled: Boolean) : PendingAdminAction {
        override val consumesOneTimePin: Boolean = !enabled
        override val promptTitle: String =
            if (enabled) "Enable App Install Blocker" else "Disable App Install Blocker"
        override val promptSubtitle: String =
            if (enabled) {
                "Enter PIN to block Play Store & installers and auto-lock Chime/Yahoo/bank installs."
            } else {
                "Disabling App Install Blocker consumes your One-Time 2-Year PIN."
            }
    }

    data class SetAiStudioWebBlocker(val enabled: Boolean) : PendingAdminAction {
        override val consumesOneTimePin: Boolean = !enabled
        override val promptTitle: String =
            if (enabled) "Block AI Studio in Browsers" else "Allow AI Studio in Browsers"
        override val promptSubtitle: String =
            if (enabled) {
                "Enter PIN to block aistudio.google.com across Chrome and all browsers."
            } else {
                "Disabling the AI Studio Web Blocker consumes your One-Time 2-Year PIN."
            }
    }

    data class SetAutoRelockEveryTwoYears(val enabled: Boolean) : PendingAdminAction {
        override val consumesOneTimePin: Boolean = !enabled
        override val promptTitle: String =
            if (enabled) "Enable Auto-Lock Per 2 Years" else "Disable Auto-Lock Per 2 Years"
        override val promptSubtitle: String =
            if (enabled) {
                "Enter PIN so VaultLock automatically re-locks itself every 2 years."
            } else {
                "Disabling 2-year auto-relock consumes your One-Time 2-Year PIN."
            }
    }

    data class UpdateSavingsGoal(val newGoalCents: Long) : PendingAdminAction {
        override val consumesOneTimePin: Boolean = true
        override val promptTitle: String = "Update 2-Year Goal"
        override val promptSubtitle: String =
            "Changing your 2-year savings goal consumes your One-Time PIN for this cycle."
    }

    data class DeleteDeposit(val depositId: Long) : PendingAdminAction {
        override val consumesOneTimePin: Boolean = true
        override val promptTitle: String = "Delete Deposit Record"
        override val promptSubtitle: String =
            "Removing a savings record consumes your One-Time PIN for this cycle."
    }

    data object ExtendLockTwoYears : PendingAdminAction {
        override val consumesOneTimePin: Boolean = false
        override val promptTitle: String = "Extend 2-Year Commitment"
        override val promptSubtitle: String =
            "Enter PIN to lock in an additional 2 years."
    }

    data object DeactivateDeviceAdmin : PendingAdminAction {
        override val consumesOneTimePin: Boolean = true
        override val promptTitle: String = "Remove Uninstall Protection"
        override val promptSubtitle: String =
            "Deactivating Device Admin consumes your One-Time 2-Year PIN."
    }

    data object UnlockSettingsGraceWindow : PendingAdminAction {
        override val consumesOneTimePin: Boolean = true
        override val promptTitle: String = "Authorize Settings Access"
        override val promptSubtitle: String =
            "Authorizing a 45s Settings window consumes your One-Time PIN for this 2-year cycle."
    }

    data object StartNextTwoYearPinCycle : PendingAdminAction {
        override val consumesOneTimePin: Boolean = false
        override val promptTitle: String = "Verify Completed 2-Year PIN"
        override val promptSubtitle: String =
            "Enter your expiring PIN to initialize a new 2-year One-Time PIN."
    }
}

data class VaultUiState(
    val config: VaultSecurityConfig = VaultSecurityConfig(),
    val systemStatus: SystemProtectionStatus = SystemProtectionStatus(),
    val blockedApps: List<BlockedAppEntity> = emptyList(),
    val installedPackageNames: Set<String> = emptySet(),
    val installedDeviceApps: List<InstalledDeviceApp> = emptyList(),
    val deposits: List<SavingsDepositEntity> = emptyList(),
    val totalSavedCents: Long = 0L,
    val totalBlockedAttempts: Int = 0,
    val securityEvents: List<SecurityEventEntity> = emptyList(),
    val nowMillis: Long = System.currentTimeMillis()
)

class VaultViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext = application.applicationContext
    private val repository = VaultRepository.getInstance(appContext)

    private val _systemStatus = MutableStateFlow(SystemPermissionsHelper.getStatus(appContext))
    private val _installedPackageNames = MutableStateFlow<Set<String>>(emptySet())
    private val _installedDeviceApps = MutableStateFlow<List<InstalledDeviceApp>>(emptyList())
    private val _nowMillis = MutableStateFlow(System.currentTimeMillis())

    private val _pendingAction = MutableStateFlow<PendingAdminAction?>(null)
    val pendingAction: StateFlow<PendingAdminAction?> = _pendingAction.asStateFlow()

    private val _pinError = MutableStateFlow<String?>(null)
    val pinError: StateFlow<String?> = _pinError.asStateFlow()

    private val _showNewCyclePinDialog = MutableStateFlow(false)
    val showNewCyclePinDialog: StateFlow<Boolean> = _showNewCyclePinDialog.asStateFlow()

    private val _statusBanner = MutableStateFlow<String?>(null)
    val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

    private val deviceSnapshotFlow = combine(
        _systemStatus,
        _installedPackageNames,
        _installedDeviceApps,
        _nowMillis
    ) { sys, installedSet, installedList, now ->
        DeviceSnapshot(sys, installedSet, installedList, now)
    }

    private val vaultDataFlow = combine(
        repository.securityConfig,
        repository.blockedApps,
        repository.deposits,
        repository.totalSavedCents,
        repository.totalBlockedAttempts,
        repository.securityEvents
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        VaultDataSnapshot(
            config = values[0] as VaultSecurityConfig,
            blockedApps = values[1] as List<BlockedAppEntity>,
            deposits = values[2] as List<SavingsDepositEntity>,
            totalSavedCents = values[3] as Long,
            totalBlockedAttempts = values[4] as Int,
            securityEvents = values[5] as List<SecurityEventEntity>
        )
    }

    val uiState: StateFlow<VaultUiState> = combine(
        vaultDataFlow,
        deviceSnapshotFlow
    ) { data, dev ->
        VaultUiState(
            config = data.config,
            systemStatus = dev.systemStatus,
            blockedApps = data.blockedApps,
            installedPackageNames = dev.installedSet,
            installedDeviceApps = dev.installedList,
            deposits = data.deposits,
            totalSavedCents = data.totalSavedCents,
            totalBlockedAttempts = data.totalBlockedAttempts,
            securityEvents = data.securityEvents,
            nowMillis = dev.nowMillis
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = VaultUiState(
            config = repository.securityPrefs.getConfig(),
            systemStatus = SystemPermissionsHelper.getStatus(appContext)
        )
    )

    init {
        viewModelScope.launch {
            repository.ensureSeeded()
            refreshDeviceState()
            if (repository.securityPrefs.getConfig().isProtectionActive) {
                BlockerMonitorService.start(appContext)
            }
        }
        viewModelScope.launch {
            while (true) {
                val now = System.currentTimeMillis()
                val rolledCycle = repository.securityPrefs.checkAndRolloverTwoYearCycleIfNeeded(now)
                if (rolledCycle != null) {
                    repository.logSecurityEvent(
                        eventType = VaultRepository.EVENT_LOCK_STARTED,
                        title = "Auto-Locked for 2-Year Cycle #$rolledCycle",
                        detail = "VaultLock automatically re-locked itself for the next 2-year term."
                    )
                    BlockerMonitorService.start(appContext)
                }
                _nowMillis.value = now
                delay(1_000L)
            }
        }
    }

    fun refreshDeviceState() {
        _systemStatus.value = SystemPermissionsHelper.getStatus(appContext)
        val config = repository.securityPrefs.getConfig()
        if (config.isProtectionActive && !BlockerMonitorService.isRunning) {
            BlockerMonitorService.start(appContext)
        }
        if (config.isProtectionActive && config.isAiStudioWebBlockerEnabled && _systemStatus.value.vpnConsentGranted && !WebFilterVpnService.isRunning) {
            WebFilterVpnService.start(appContext)
        }
        viewModelScope.launch {
            val (installedSet, installedList) = repository.scanInstalledApps()
            _installedPackageNames.value = installedSet
            _installedDeviceApps.value = installedList
            repository.autoDiscoverAndLockInstalledBanksAndMail(installedList)
        }
    }

    fun scanInstalledBanksNow() {
        viewModelScope.launch {
            val (installedSet, installedList) = repository.scanInstalledApps()
            _installedPackageNames.value = installedSet
            _installedDeviceApps.value = installedList
            val newlyLocked = repository.autoDiscoverAndLockInstalledBanksAndMail(installedList)
            _statusBanner.value = if (newlyLocked > 0) {
                "Auto-locked $newlyLocked newly detected bank/mail app(s) on this device."
            } else {
                "Scanned ${installedList.size} device apps — Chime, Yahoo Mail & detected banks are locked."
            }
        }
    }

    fun completeOnboardingSetup(
        pin: String,
        savingsGoalCents: Long,
        includeChimeBanksAndYahoo: Boolean
    ) {
        viewModelScope.launch {
            repository.ensureSeeded()
            repository.securityPrefs.setAdminPin(pin)
            repository.securityPrefs.startTwoYearCommitment(savingsGoalCents)
            if (includeChimeBanksAndYahoo) {
                BankingAppCatalog.knownBankingApps.forEach { app ->
                    if (app.defaultBlocked) {
                        repository.setAppBlocked(app.packageName, app.displayName, true)
                    }
                }
            }
            repository.logSecurityEvent(
                eventType = VaultRepository.EVENT_LOCK_STARTED,
                title = "2-Year Savings Vault Armed",
                detail = "One-Time 2-Year PIN set and 2-year goal (${VaultRepository.formatCents(savingsGoalCents)}) initialized."
            )
            BlockerMonitorService.start(appContext)
            refreshDeviceState()
        }
    }

    fun requestAdminAction(action: PendingAdminAction) {
        val config = repository.securityPrefs.getConfig()
        val dateStr = if (config.lockEndMillis > 0L) {
            SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(config.lockEndMillis))
        } else {
            "cycle end"
        }

        if (action is PendingAdminAction.StartNextTwoYearPinCycle && !config.isTwoYearTermComplete) {
            _statusBanner.value =
                "One-Time PIN cannot be rotated until your 2-year lock completes on $dateStr."
            return
        }

        if (config.isOneTimePinConsumed && action !is PendingAdminAction.StartNextTwoYearPinCycle) {
            // Allow strictly tightening/re-arming protection even after the 1-time unlock was spent,
            // while sealing all unblocking, disabling, and modification actions until 2 years complete.
            if (!action.consumesOneTimePin) {
                viewModelScope.launch {
                    executeAuthorizedAction(action)
                }
                return
            }
            _statusBanner.value =
                "One-Time 2-Year PIN already used. All unlock & disable actions are sealed until $dateStr."
            return
        }

        _pinError.value = null
        _pendingAction.value = action
    }

    fun dismissPinChallenge() {
        _pinError.value = null
        _pendingAction.value = null
    }

    fun submitAdminPin(pin: String) {
        val action = _pendingAction.value ?: return
        when (val result = repository.securityPrefs.verifyAdminPin(pin, consumeOneTimePin = action.consumesOneTimePin)) {
            SecurityPrefs.PinCheckResult.Success -> {
                _pinError.value = null
                _pendingAction.value = null
                executeAuthorizedAction(action)
            }
            SecurityPrefs.PinCheckResult.OneTimePinAlreadyConsumed -> {
                _pinError.value =
                    "One-Time PIN already used for this 2-year term. Sealed until cycle completion."
            }
            is SecurityPrefs.PinCheckResult.Invalid -> {
                _pinError.value =
                    "Incorrect PIN (${result.remainingBeforeLockout} tries left before cooldown)."
            }
            is SecurityPrefs.PinCheckResult.LockedOut -> {
                _pinError.value =
                    "Security lockout active. Try again in ${result.remainingSeconds} seconds."
            }
        }
    }

    private fun executeAuthorizedAction(action: PendingAdminAction) {
        viewModelScope.launch {
            if (action.consumesOneTimePin) {
                repository.logSecurityEvent(
                    eventType = VaultRepository.EVENT_ONE_TIME_PIN_USED,
                    title = "One-Time 2-Year PIN Consumed",
                    detail = "Single PIN use for this 2-year cycle was consumed by: ${action.promptTitle}."
                )
            }

            when (action) {
                is PendingAdminAction.ToggleAppBlock -> {
                    repository.setAppBlocked(
                        action.packageName,
                        action.displayName,
                        action.targetBlocked
                    )
                    _statusBanner.value = if (action.targetBlocked) {
                        "${action.displayName} is now locked."
                    } else {
                        "${action.displayName} unlocked (1-Time 2-Year PIN consumed)."
                    }
                }

                is PendingAdminAction.AddCustomApp -> {
                    repository.addCustomBlockedApp(
                        action.packageName,
                        action.displayName,
                        action.category
                    )
                    _statusBanner.value = "Added ${action.displayName} to active blocklist."
                }

                is PendingAdminAction.RemoveCustomApp -> {
                    repository.removeCustomBlockedApp(action.app)
                    _statusBanner.value = "Removed ${action.app.displayName}."
                }

                is PendingAdminAction.SetProtectionActive -> {
                    repository.securityPrefs.setProtectionActive(action.active)
                    if (action.active) {
                        BlockerMonitorService.start(appContext)
                        if (repository.securityPrefs.getConfig().isAiStudioWebBlockerEnabled) {
                            WebFilterVpnService.start(appContext)
                        }
                        repository.logSecurityEvent(
                            eventType = VaultRepository.EVENT_LOCK_STARTED,
                            title = "Blocker Shield Activated",
                            detail = "Real-time Chime, banking & Yahoo Mail monitoring started."
                        )
                        _statusBanner.value = "VaultLock Shield is active."
                    } else {
                        BlockerMonitorService.stop(appContext)
                        WebFilterVpnService.stop(appContext)
                        repository.logSecurityEvent(
                            eventType = VaultRepository.EVENT_ADMIN_OVERRIDE,
                            title = "Blocker Shield Paused",
                            detail = "Protection paused via One-Time 2-Year PIN."
                        )
                        _statusBanner.value = "Protection paused (1-Time 2-Year PIN consumed)."
                    }
                }

                is PendingAdminAction.SetTamperGuard -> {
                    repository.securityPrefs.setTamperGuardEnabled(action.enabled)
                    repository.logSecurityEvent(
                        eventType = if (action.enabled) VaultRepository.EVENT_RULE_ENABLED else VaultRepository.EVENT_ADMIN_OVERRIDE,
                        title = if (action.enabled) "Anti-Tamper Shield Enabled" else "Anti-Tamper Shield Disabled",
                        detail = "Settings & Installer guard updated."
                    )
                    _statusBanner.value = if (action.enabled) {
                        "Anti-Tamper Shield enabled."
                    } else {
                        "Anti-Tamper Shield disabled (1-Time PIN consumed)."
                    }
                }

                is PendingAdminAction.SetInstallBlocker -> {
                    repository.securityPrefs.setInstallBlockerEnabled(action.enabled)
                    repository.logSecurityEvent(
                        eventType = if (action.enabled) VaultRepository.EVENT_RULE_ENABLED else VaultRepository.EVENT_ADMIN_OVERRIDE,
                        title = if (action.enabled) "App Install Blocker Enabled" else "App Install Blocker Disabled",
                        detail = "Play Store, Package Installer & live Chime/Yahoo/bank install guard updated."
                    )
                    _statusBanner.value = if (action.enabled) {
                        "App Install Blocker enabled — Play Store & bank installs blocked."
                    } else {
                        "App Install Blocker disabled (1-Time PIN consumed)."
                    }
                }

                is PendingAdminAction.SetAiStudioWebBlocker -> {
                    repository.securityPrefs.setAiStudioWebBlockerEnabled(action.enabled)
                    if (action.enabled && repository.securityPrefs.getConfig().isProtectionActive) {
                        WebFilterVpnService.start(appContext)
                    } else {
                        WebFilterVpnService.stop(appContext)
                    }
                    repository.logSecurityEvent(
                        eventType = if (action.enabled) VaultRepository.EVENT_RULE_ENABLED else VaultRepository.EVENT_ADMIN_OVERRIDE,
                        title = if (action.enabled) "AI Studio Web Blocker Enabled" else "AI Studio Web Blocker Disabled",
                        detail = "Browser DNS firewall for aistudio.google.com updated."
                    )
                    _statusBanner.value = if (action.enabled) {
                        "AI Studio Web Blocker active for Chrome & all browsers."
                    } else {
                        "AI Studio Web Blocker disabled (1-Time PIN consumed)."
                    }
                }

                is PendingAdminAction.SetAutoRelockEveryTwoYears -> {
                    repository.securityPrefs.setAutoRelockEveryTwoYears(action.enabled)
                    repository.logSecurityEvent(
                        eventType = if (action.enabled) VaultRepository.EVENT_LOCK_STARTED else VaultRepository.EVENT_ADMIN_OVERRIDE,
                        title = if (action.enabled) "Per-2-Year Auto-Lock Enabled" else "Per-2-Year Auto-Lock Disabled",
                        detail = "Recurring 2-year self-lock setting updated."
                    )
                    _statusBanner.value = if (action.enabled) {
                        "VaultLock will auto-lock itself every 2 years."
                    } else {
                        "Per-2-year auto-relock disabled (1-Time PIN consumed)."
                    }
                }

                is PendingAdminAction.UpdateSavingsGoal -> {
                    repository.securityPrefs.updateSavingsGoalCents(action.newGoalCents)
                    repository.logSecurityEvent(
                        eventType = VaultRepository.EVENT_SAVINGS_LOGGED,
                        title = "2-Year Goal Updated",
                        detail = "Target adjusted to ${VaultRepository.formatCents(action.newGoalCents)}."
                    )
                    _statusBanner.value = "2-year savings goal updated."
                }

                is PendingAdminAction.DeleteDeposit -> {
                    repository.deleteSavingsDeposit(action.depositId)
                    _statusBanner.value = "Deposit entry deleted."
                }

                PendingAdminAction.ExtendLockTwoYears -> {
                    repository.securityPrefs.extendLockByTwoYears()
                    BlockerMonitorService.start(appContext)
                    repository.logSecurityEvent(
                        eventType = VaultRepository.EVENT_LOCK_STARTED,
                        title = "Extended 2-Year Lock",
                        detail = "Administrator extended lock period by 2 additional years."
                    )
                    _statusBanner.value = "Lock extended by 2 years."
                }

                PendingAdminAction.DeactivateDeviceAdmin -> {
                    repository.securityPrefs.grantSettingsGraceWindow(45_000L)
                    SystemPermissionsHelper.deactivateDeviceAdmin(appContext)
                    repository.logSecurityEvent(
                        eventType = VaultRepository.EVENT_ADMIN_OVERRIDE,
                        title = "Uninstall Protection Released",
                        detail = "Device Administrator deactivated via One-Time 2-Year PIN."
                    )
                    refreshDeviceState()
                    _statusBanner.value = "Device Admin deactivated (1-Time PIN consumed)."
                }

                PendingAdminAction.UnlockSettingsGraceWindow -> {
                    repository.securityPrefs.grantSettingsGraceWindow(45_000L)
                    _statusBanner.value = "45s Settings window authorized (1-Time PIN consumed)."
                }

                PendingAdminAction.StartNextTwoYearPinCycle -> {
                    _showNewCyclePinDialog.value = true
                }
            }
        }
    }

    fun completeNewTwoYearCyclePin(newPin: String) {
        viewModelScope.launch {
            repository.securityPrefs.startNextTwoYearPinCycle(newPin)
            BlockerMonitorService.start(appContext)
            repository.logSecurityEvent(
                eventType = VaultRepository.EVENT_PIN_CHANGED,
                title = "New 2-Year One-Time PIN Armed",
                detail = "Fresh 2-year savings cycle and One-Time PIN initialized."
            )
            _showNewCyclePinDialog.value = false
            _statusBanner.value = "New 2-year One-Time PIN armed."
        }
    }

    fun dismissNewCyclePinDialog() {
        _showNewCyclePinDialog.value = false
    }

    fun logSavingsDeposit(amountCents: Long, note: String, monthNumber: Int) {
        viewModelScope.launch {
            repository.addSavingsDeposit(amountCents, note, monthNumber)
            _statusBanner.value = "Saved ${VaultRepository.formatCents(amountCents)} to Month $monthNumber!"
        }
    }

    fun onVpnConsentGranted() {
        val config = repository.securityPrefs.getConfig()
        if (config.isProtectionActive && config.isAiStudioWebBlockerEnabled) {
            WebFilterVpnService.start(appContext)
        }
        refreshDeviceState()
        _statusBanner.value = "AI Studio DNS Web Firewall active across all browsers."
    }

    fun grantSettingsGraceBeforePermissionLaunch() {
        val state = uiState.value
        if (state.systemStatus.readinessCount < 5 || !state.config.isProtectionActive) {
            repository.securityPrefs.grantSettingsGraceWindow(45_000L)
        }
    }

    fun clearStatusBanner() {
        _statusBanner.value = null
    }

    private data class DeviceSnapshot(
        val systemStatus: SystemProtectionStatus,
        val installedSet: Set<String>,
        val installedList: List<InstalledDeviceApp>,
        val nowMillis: Long
    )

    private data class VaultDataSnapshot(
        val config: VaultSecurityConfig,
        val blockedApps: List<BlockedAppEntity>,
        val deposits: List<SavingsDepositEntity>,
        val totalSavedCents: Long,
        val totalBlockedAttempts: Int,
        val securityEvents: List<SecurityEventEntity>
    )
}
