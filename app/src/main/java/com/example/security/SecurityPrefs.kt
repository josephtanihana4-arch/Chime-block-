package com.example.security

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Calendar

data class VaultSecurityConfig(
    val isPinConfigured: Boolean = false,
    val isProtectionActive: Boolean = false,
    val isTamperGuardEnabled: Boolean = true,
    val isInstallBlockerEnabled: Boolean = true,
    val isAiStudioWebBlockerEnabled: Boolean = true,
    val autoRelockEveryTwoYears: Boolean = true,
    val lockCycleNumber: Int = 1,
    val lockStartMillis: Long = 0L,
    val lockEndMillis: Long = 0L,
    val savingsGoalCents: Long = 1_200_000L, // $12,000 default 2-year goal ($500/mo)
    val failedPinAttempts: Int = 0,
    val lockoutUntilMillis: Long = 0L,
    val settingsGraceUntilMillis: Long = 0L,
    val oneTimePinUsedAtMillis: Long = 0L
) {
    val hasActiveTwoYearLock: Boolean
        get() = lockStartMillis > 0L && lockEndMillis > System.currentTimeMillis()

    val isTwoYearTermComplete: Boolean
        get() = lockEndMillis > 0L && System.currentTimeMillis() >= lockEndMillis

    val isOneTimePinConsumed: Boolean
        get() = oneTimePinUsedAtMillis > 0L && lockStartMillis > 0L &&
            oneTimePinUsedAtMillis >= lockStartMillis && !isTwoYearTermComplete

    val remainingPinUsesInCycle: Int
        get() = if (isPinConfigured && !isOneTimePinConsumed) 1 else 0
}

/**
 * Persists salted PBKDF2 One-Time PIN credentials (1 use per 2-year cycle),
 * recurring 2-year self-lock timestamps, anti-tamper flags, and brute-force lockout timers.
 */
class SecurityPrefs(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _configFlow = MutableStateFlow(readConfig())
    val configFlow: StateFlow<VaultSecurityConfig> = _configFlow.asStateFlow()

    fun getConfig(): VaultSecurityConfig = readConfig()

    fun isPinConfigured(): Boolean {
        return !prefs.getString(KEY_PIN_HASH, null).isNullOrBlank() &&
            !prefs.getString(KEY_PIN_SALT, null).isNullOrBlank()
    }

    fun setAdminPin(pin: String) {
        val hashed = PinHasher.hashPin(pin)
        prefs.edit()
            .putString(KEY_PIN_HASH, hashed.hashBase64)
            .putString(KEY_PIN_SALT, hashed.saltBase64)
            .putInt(KEY_FAILED_ATTEMPTS, 0)
            .putLong(KEY_LOCKOUT_UNTIL, 0L)
            .putLong(KEY_ONE_TIME_PIN_USED_AT, 0L)
            .apply()
        emitUpdate()
    }

    sealed interface PinCheckResult {
        data object Success : PinCheckResult
        data object OneTimePinAlreadyConsumed : PinCheckResult
        data class Invalid(val remainingBeforeLockout: Int) : PinCheckResult
        data class LockedOut(val remainingSeconds: Long) : PinCheckResult
    }

    fun verifyAdminPin(pin: String, consumeOneTimePin: Boolean = false): PinCheckResult {
        val config = readConfig()
        if (config.isOneTimePinConsumed) {
            return PinCheckResult.OneTimePinAlreadyConsumed
        }

        val now = System.currentTimeMillis()
        val lockoutUntil = prefs.getLong(KEY_LOCKOUT_UNTIL, 0L)
        if (lockoutUntil > now) {
            val seconds = ((lockoutUntil - now) / 1000L).coerceAtLeast(1L)
            return PinCheckResult.LockedOut(seconds)
        }

        val expectedHash = prefs.getString(KEY_PIN_HASH, null).orEmpty()
        val expectedSalt = prefs.getString(KEY_PIN_SALT, null).orEmpty()
        if (PinHasher.verifyPin(pin, expectedHash, expectedSalt)) {
            val editor = prefs.edit()
                .putInt(KEY_FAILED_ATTEMPTS, 0)
                .putLong(KEY_LOCKOUT_UNTIL, 0L)
            if (consumeOneTimePin) {
                editor.putLong(KEY_ONE_TIME_PIN_USED_AT, now)
            }
            editor.apply()
            emitUpdate()
            return PinCheckResult.Success
        }

        val newAttempts = prefs.getInt(KEY_FAILED_ATTEMPTS, 0) + 1
        var newLockout = 0L
        if (newAttempts >= MAX_ATTEMPTS_BEFORE_COOLDOWN) {
            val tier = (newAttempts - MAX_ATTEMPTS_BEFORE_COOLDOWN).coerceAtMost(4)
            val cooldownMs = 30_000L shl tier
            newLockout = now + cooldownMs
        }
        prefs.edit()
            .putInt(KEY_FAILED_ATTEMPTS, newAttempts)
            .putLong(KEY_LOCKOUT_UNTIL, newLockout)
            .apply()
        emitUpdate()

        return if (newLockout > now) {
            PinCheckResult.LockedOut((newLockout - now) / 1000L)
        } else {
            PinCheckResult.Invalid((MAX_ATTEMPTS_BEFORE_COOLDOWN - newAttempts).coerceAtLeast(0))
        }
    }

    fun startTwoYearCommitment(
        savingsGoalCents: Long,
        startTimeMillis: Long = System.currentTimeMillis()
    ) {
        val cal = Calendar.getInstance().apply {
            timeInMillis = startTimeMillis
            add(Calendar.YEAR, 2)
        }
        prefs.edit()
            .putLong(KEY_LOCK_START, startTimeMillis)
            .putLong(KEY_LOCK_END, cal.timeInMillis)
            .putLong(KEY_SAVINGS_GOAL_CENTS, savingsGoalCents.coerceAtLeast(100L))
            .putBoolean(KEY_PROTECTION_ACTIVE, true)
            .putBoolean(KEY_TAMPER_GUARD, true)
            .putBoolean(KEY_INSTALL_BLOCKER, true)
            .putBoolean(KEY_AI_STUDIO_WEB_BLOCKER, true)
            .putBoolean(KEY_AUTO_RELOCK_2Y, true)
            .putInt(KEY_LOCK_CYCLE_NUMBER, 1)
            .putLong(KEY_ONE_TIME_PIN_USED_AT, 0L)
            .apply()
        emitUpdate()
    }

    /**
     * Automatically re-locks VaultLock into the next 2-year cycle when the current
     * 2-year term expires and autoRelockEveryTwoYears is enabled.
     * Returns the new cycle number if a rollover occurred, or null otherwise.
     */
    fun checkAndRolloverTwoYearCycleIfNeeded(nowMillis: Long = System.currentTimeMillis()): Int? {
        val config = readConfig()
        if (!config.isPinConfigured || !config.autoRelockEveryTwoYears) return null
        if (config.lockEndMillis <= 0L || nowMillis < config.lockEndMillis) return null

        var cycleStart = config.lockEndMillis
        var cycleEnd = addTwoYears(cycleStart)
        var cycleNum = config.lockCycleNumber + 1
        while (cycleEnd <= nowMillis) {
            cycleStart = cycleEnd
            cycleEnd = addTwoYears(cycleStart)
            cycleNum += 1
        }

        prefs.edit()
            .putLong(KEY_LOCK_START, cycleStart)
            .putLong(KEY_LOCK_END, cycleEnd)
            .putInt(KEY_LOCK_CYCLE_NUMBER, cycleNum)
            .putBoolean(KEY_PROTECTION_ACTIVE, true)
            .putBoolean(KEY_TAMPER_GUARD, true)
            .putBoolean(KEY_INSTALL_BLOCKER, true)
            .putBoolean(KEY_AI_STUDIO_WEB_BLOCKER, true)
            .putLong(KEY_ONE_TIME_PIN_USED_AT, 0L)
            .apply()
        emitUpdate()
        return cycleNum
    }

    fun startNextTwoYearPinCycle(newPin: String, startTimeMillis: Long = System.currentTimeMillis()) {
        val hashed = PinHasher.hashPin(newPin)
        val currentCycle = prefs.getInt(KEY_LOCK_CYCLE_NUMBER, 1)
        val cal = Calendar.getInstance().apply {
            timeInMillis = startTimeMillis
            add(Calendar.YEAR, 2)
        }
        prefs.edit()
            .putString(KEY_PIN_HASH, hashed.hashBase64)
            .putString(KEY_PIN_SALT, hashed.saltBase64)
            .putLong(KEY_LOCK_START, startTimeMillis)
            .putLong(KEY_LOCK_END, cal.timeInMillis)
            .putInt(KEY_LOCK_CYCLE_NUMBER, currentCycle + 1)
            .putBoolean(KEY_PROTECTION_ACTIVE, true)
            .putBoolean(KEY_TAMPER_GUARD, true)
            .putBoolean(KEY_INSTALL_BLOCKER, true)
            .putBoolean(KEY_AI_STUDIO_WEB_BLOCKER, true)
            .putInt(KEY_FAILED_ATTEMPTS, 0)
            .putLong(KEY_LOCKOUT_UNTIL, 0L)
            .putLong(KEY_ONE_TIME_PIN_USED_AT, 0L)
            .apply()
        emitUpdate()
    }

    fun updateSavingsGoalCents(goalCents: Long) {
        prefs.edit()
            .putLong(KEY_SAVINGS_GOAL_CENTS, goalCents.coerceAtLeast(100L))
            .apply()
        emitUpdate()
    }

    fun setProtectionActive(active: Boolean) {
        prefs.edit().putBoolean(KEY_PROTECTION_ACTIVE, active).apply()
        emitUpdate()
    }

    fun setTamperGuardEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_TAMPER_GUARD, enabled).apply()
        emitUpdate()
    }

    fun setInstallBlockerEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_INSTALL_BLOCKER, enabled).apply()
        emitUpdate()
    }

    fun setAiStudioWebBlockerEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AI_STUDIO_WEB_BLOCKER, enabled).apply()
        emitUpdate()
    }

    fun setAutoRelockEveryTwoYears(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_RELOCK_2Y, enabled).apply()
        emitUpdate()
    }

    fun extendLockByTwoYears() {
        val now = System.currentTimeMillis()
        val base = maxOf(now, prefs.getLong(KEY_LOCK_END, now))
        val newEnd = addTwoYears(base)
        if (prefs.getLong(KEY_LOCK_START, 0L) == 0L) {
            prefs.edit().putLong(KEY_LOCK_START, now).apply()
        }
        prefs.edit()
            .putLong(KEY_LOCK_END, newEnd)
            .putBoolean(KEY_PROTECTION_ACTIVE, true)
            .apply()
        emitUpdate()
    }

    fun grantSettingsGraceWindow(durationMillis: Long = 45_000L) {
        prefs.edit()
            .putLong(KEY_SETTINGS_GRACE_UNTIL, System.currentTimeMillis() + durationMillis)
            .apply()
        emitUpdate()
    }

    fun isInSettingsGraceWindow(): Boolean {
        return prefs.getLong(KEY_SETTINGS_GRACE_UNTIL, 0L) > System.currentTimeMillis()
    }

    fun grantTempAppOverride(packageName: String, durationMillis: Long = 90_000L) {
        prefs.edit()
            .putString(KEY_TEMP_OVERRIDE_PKG, packageName)
            .putLong(KEY_TEMP_OVERRIDE_UNTIL, System.currentTimeMillis() + durationMillis)
            .apply()
    }

    fun isTempAppOverrideActive(packageName: String): Boolean {
        val savedPkg = prefs.getString(KEY_TEMP_OVERRIDE_PKG, null) ?: return false
        val until = prefs.getLong(KEY_TEMP_OVERRIDE_UNTIL, 0L)
        return savedPkg == packageName && until > System.currentTimeMillis()
    }

    private fun addTwoYears(timeMillis: Long): Long {
        return Calendar.getInstance().apply {
            this.timeInMillis = timeMillis
            add(Calendar.YEAR, 2)
        }.timeInMillis
    }

    private fun emitUpdate() {
        _configFlow.value = readConfig()
    }

    private fun readConfig(): VaultSecurityConfig {
        return VaultSecurityConfig(
            isPinConfigured = isPinConfigured(),
            isProtectionActive = prefs.getBoolean(KEY_PROTECTION_ACTIVE, false),
            isTamperGuardEnabled = prefs.getBoolean(KEY_TAMPER_GUARD, true),
            isInstallBlockerEnabled = prefs.getBoolean(KEY_INSTALL_BLOCKER, true),
            isAiStudioWebBlockerEnabled = prefs.getBoolean(KEY_AI_STUDIO_WEB_BLOCKER, true),
            autoRelockEveryTwoYears = prefs.getBoolean(KEY_AUTO_RELOCK_2Y, true),
            lockCycleNumber = prefs.getInt(KEY_LOCK_CYCLE_NUMBER, 1),
            lockStartMillis = prefs.getLong(KEY_LOCK_START, 0L),
            lockEndMillis = prefs.getLong(KEY_LOCK_END, 0L),
            savingsGoalCents = prefs.getLong(KEY_SAVINGS_GOAL_CENTS, 1_200_000L),
            failedPinAttempts = prefs.getInt(KEY_FAILED_ATTEMPTS, 0),
            lockoutUntilMillis = prefs.getLong(KEY_LOCKOUT_UNTIL, 0L),
            settingsGraceUntilMillis = prefs.getLong(KEY_SETTINGS_GRACE_UNTIL, 0L),
            oneTimePinUsedAtMillis = prefs.getLong(KEY_ONE_TIME_PIN_USED_AT, 0L)
        )
    }

    companion object {
        private const val PREFS_NAME = "vaultlock_security_prefs"
        private const val KEY_PIN_HASH = "admin_pin_hash"
        private const val KEY_PIN_SALT = "admin_pin_salt"
        private const val KEY_PROTECTION_ACTIVE = "protection_active"
        private const val KEY_TAMPER_GUARD = "tamper_guard_enabled"
        private const val KEY_INSTALL_BLOCKER = "install_blocker_enabled"
        private const val KEY_AI_STUDIO_WEB_BLOCKER = "ai_studio_web_blocker_enabled"
        private const val KEY_AUTO_RELOCK_2Y = "auto_relock_every_2_years"
        private const val KEY_LOCK_CYCLE_NUMBER = "lock_cycle_number"
        private const val KEY_LOCK_START = "lock_start_millis"
        private const val KEY_LOCK_END = "lock_end_millis"
        private const val KEY_SAVINGS_GOAL_CENTS = "savings_goal_cents"
        private const val KEY_FAILED_ATTEMPTS = "failed_pin_attempts"
        private const val KEY_LOCKOUT_UNTIL = "lockout_until_millis"
        private const val KEY_SETTINGS_GRACE_UNTIL = "settings_grace_until_millis"
        private const val KEY_TEMP_OVERRIDE_PKG = "temp_override_pkg"
        private const val KEY_TEMP_OVERRIDE_UNTIL = "temp_override_until"
        private const val KEY_ONE_TIME_PIN_USED_AT = "one_time_pin_used_at_millis"
        private const val MAX_ATTEMPTS_BEFORE_COOLDOWN = 5
    }
}
