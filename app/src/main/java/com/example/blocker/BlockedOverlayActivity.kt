package com.example.blocker

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.data.repository.VaultRepository
import com.example.security.SecurityPrefs
import com.example.ui.components.PinChallengeDialog
import com.example.ui.theme.CrimsonLock
import com.example.ui.theme.DeepVaultNavy
import com.example.ui.theme.EmeraldShield
import com.example.ui.theme.ObsidianVoid
import com.example.ui.theme.SovereignGold
import com.example.ui.theme.VaultLockTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BlockedOverlayActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val blockedPackage = intent.getStringExtra(EXTRA_BLOCKED_PACKAGE).orEmpty()
        val blockedAppName = intent.getStringExtra(EXTRA_BLOCKED_APP_NAME) ?: "Protected App"
        val isTamperShield = intent.getBooleanExtra(EXTRA_IS_TAMPER_SHIELD, false)
        val isInstallGuard = intent.getBooleanExtra(EXTRA_IS_INSTALL_GUARD, false)
        val isWebShield = intent.getBooleanExtra(EXTRA_IS_WEB_SHIELD, false)

        val repository = VaultRepository.getInstance(this)

        setContent {
            VaultLockTheme(darkTheme = true) {
                BackHandler {
                    navigateToDeviceHome()
                }
                BlockedShieldScreen(
                    blockedAppName = blockedAppName,
                    blockedPackage = blockedPackage,
                    isTamperShield = isTamperShield,
                    isInstallGuard = isInstallGuard,
                    isWebShield = isWebShield,
                    repository = repository,
                    onGoHome = { navigateToDeviceHome() },
                    onAdminAuthorized = {
                        if (isTamperShield || isInstallGuard) {
                            repository.securityPrefs.grantSettingsGraceWindow(45_000L)
                        }
                        if (blockedPackage.isNotBlank()) {
                            repository.securityPrefs.grantTempAppOverride(blockedPackage, 90_000L)
                        }
                        finish()
                    }
                )
            }
        }
    }

    private fun navigateToDeviceHome() {
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(homeIntent)
        finishAndRemoveTask()
    }

    companion object {
        const val EXTRA_BLOCKED_PACKAGE = "extra_blocked_package"
        const val EXTRA_BLOCKED_APP_NAME = "extra_blocked_app_name"
        const val EXTRA_IS_TAMPER_SHIELD = "extra_is_tamper_shield"
        const val EXTRA_IS_INSTALL_GUARD = "extra_is_install_guard"
        const val EXTRA_IS_WEB_SHIELD = "extra_is_web_shield"
    }
}

@Composable
private fun BlockedShieldScreen(
    blockedAppName: String,
    blockedPackage: String,
    isTamperShield: Boolean,
    isInstallGuard: Boolean,
    isWebShield: Boolean,
    repository: VaultRepository,
    onGoHome: () -> Unit,
    onAdminAuthorized: () -> Unit
) {
    val config = remember { repository.securityPrefs.getConfig() }
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var showPinDialog by remember { mutableStateOf(false) }
    var pinError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val dateFormatter = remember { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()) }

    LaunchedEffect(Unit) {
        while (true) {
            nowMillis = System.currentTimeMillis()
            delay(1_000L)
        }
    }

    val remainingMs = (config.lockEndMillis - nowMillis).coerceAtLeast(0L)
    val daysLeft = remainingMs / (1000L * 60 * 60 * 24)
    val hoursLeft = (remainingMs / (1000L * 60 * 60)) % 24
    val minsLeft = (remainingMs / (1000L * 60)) % 60

    val isHighAlert = isTamperShield || isInstallGuard || isWebShield
    val accentColor = if (isHighAlert) CrimsonLock else SovereignGold
    val isPinUsedUp = config.isOneTimePinConsumed

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        ObsidianVoid,
                        DeepVaultNavy,
                        if (isHighAlert) Color(0xFF220B12) else Color(0xFF0A1D1B)
                    )
                )
            )
            .systemBarsPadding()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 520.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = accentColor.copy(alpha = 0.14f),
                border = BorderStroke(1.5.dp, accentColor.copy(alpha = 0.55f)),
                modifier = Modifier.size(92.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (isHighAlert) Icons.Default.WarningAmber else Icons.Default.Lock,
                        contentDescription = "Blocked by VaultLock",
                        tint = accentColor,
                        modifier = Modifier.size(44.dp)
                    )
                }
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = when {
                        isWebShield -> "AI STUDIO WEB SHIELD"
                        isInstallGuard && !isTamperShield -> "APP INSTALL BLOCKED"
                        isTamperShield -> "ANTI-TAMPER & INSTALL SHIELD"
                        else -> "RESTRICTED ACCESS LOCKED"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = accentColor,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = blockedAppName,
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = when {
                        isWebShield ->
                            "Google AI Studio (aistudio.google.com) is blocked across Chrome and all device browsers. Keep VaultLock Web Shield VPN active to browse safely."
                        isInstallGuard && isTamperShield ->
                            "Package Installers & Android Settings are locked during your 2-year commitment to stop bypass or installing Chime, Yahoo Mail, or bank apps."
                        isInstallGuard ->
                            "VaultLock blocks app stores and newly detected Chime, Yahoo Mail, and banking app installations during your 2-year lock."
                        isTamperShield ->
                            "System Settings (including Factory Reset & Master Clear) are locked during your 2-year savings commitment to prevent removal or bypass."
                        else ->
                            "Access to this banking or recovery email app is blocked while your 2-year savings vault is locked."
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }

            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = EmeraldShield,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "2-YEAR SAVINGS COMMITMENT",
                            style = MaterialTheme.typography.labelMedium,
                            color = EmeraldShield
                        )
                    }
                    Text(
                        text = if (remainingMs > 0L) {
                            "${daysLeft}d ${hoursLeft}h ${minsLeft}m remaining"
                        } else {
                            "2-Year Goal target ${VaultRepository.formatCents(config.savingsGoalCents)}"
                        },
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (config.lockEndMillis > 0L) {
                        Text(
                            text = "Locked until ${dateFormatter.format(Date(config.lockEndMillis))}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (blockedPackage.isNotBlank()) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = blockedPackage,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            Button(
                onClick = onGoHome,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("overlay_go_home_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = EmeraldShield,
                    contentColor = ObsidianVoid
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Home,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.size(8.dp))
                Text(
                    text = "Return to Safe Home",
                    style = MaterialTheme.typography.labelLarge
                )
            }

            if (isPinUsedUp) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = CrimsonLock.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, CrimsonLock.copy(alpha = 0.45f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "One-Time 2-Year PIN already used. Overrides are sealed until ${dateFormatter.format(Date(config.lockEndMillis))}.",
                        style = MaterialTheme.typography.bodySmall,
                        color = CrimsonLock,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            } else {
                OutlinedButton(
                    onClick = {
                        pinError = null
                        showPinDialog = true
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("overlay_admin_override_button"),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Key,
                        contentDescription = null,
                        tint = SovereignGold,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(
                        text = "Use One-Time 2-Year PIN Override",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }

    if (showPinDialog) {
        PinChallengeDialog(
            title = if (isTamperShield) "Unlock Settings (1-Time PIN)" else "Override Block (1-Time PIN)",
            subtitle = if (isTamperShield) {
                "Using your One-Time PIN grants 45s of Settings access and consumes your single PIN allowance for this 2-year period."
            } else {
                "Using your One-Time PIN grants 90s access to $blockedAppName and consumes your single PIN allowance for this 2-year period."
            },
            oneTimeWarning = "SINGLE-USE PER 2 YEARS: Consuming this PIN seals overrides for the rest of the 2-year lock.",
            errorMessage = pinError,
            onDismiss = { showPinDialog = false },
            onPinSubmitted = { pin ->
                when (val result = repository.securityPrefs.verifyAdminPin(pin, consumeOneTimePin = true)) {
                    SecurityPrefs.PinCheckResult.Success -> {
                        scope.launch {
                            repository.logSecurityEvent(
                                eventType = VaultRepository.EVENT_ONE_TIME_PIN_USED,
                                title = "One-Time 2-Year PIN Consumed",
                                detail = "PIN verified and consumed for temporary access to $blockedAppName.",
                                packageName = blockedPackage
                            )
                        }
                        showPinDialog = false
                        onAdminAuthorized()
                    }
                    SecurityPrefs.PinCheckResult.OneTimePinAlreadyConsumed -> {
                        pinError = "One-Time PIN already consumed for this 2-year cycle."
                    }
                    is SecurityPrefs.PinCheckResult.Invalid -> {
                        pinError = "Incorrect PIN. ${result.remainingBeforeLockout} attempts before cooldown."
                    }
                    is SecurityPrefs.PinCheckResult.LockedOut -> {
                        pinError = "Too many failed attempts. Locked for ${result.remainingSeconds}s."
                    }
                }
            }
        )
    }
}
