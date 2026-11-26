package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.GppGood
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreTime
import androidx.compose.material.icons.filled.PublicOff
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SettingsApplications
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.db.SecurityEventEntity
import com.example.data.repository.VaultRepository
import com.example.ui.PendingAdminAction
import com.example.ui.VaultUiState
import com.example.ui.theme.CrimsonLock
import com.example.ui.theme.EmeraldShield
import com.example.ui.theme.ObsidianVoid
import com.example.ui.theme.SovereignGold
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SecurityCenterScreen(
    uiState: VaultUiState,
    onActivateDeviceAdmin: () -> Unit,
    onRequestAdminAction: (PendingAdminAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val config = uiState.config
    val sys = uiState.systemStatus
    val eventDateFormatter = remember { SimpleDateFormat("MMM dd • HH:mm:ss", Locale.getDefault()) }
    val cycleDateFormatter = remember { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // One-Time PIN per 2-Year Cycle Status Card
        item {
            val pinConsumed = config.isOneTimePinConsumed
            val accent = if (pinConsumed) CrimsonLock else SovereignGold
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, accent.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Key,
                                contentDescription = null,
                                tint = accent
                            )
                            Text(
                                text = "One-Time PIN • Cycle #${config.lockCycleNumber}",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = accent.copy(alpha = 0.16f)
                        ) {
                            Text(
                                text = "${config.remainingPinUsesInCycle}/1 LEFT",
                                style = MaterialTheme.typography.labelSmall,
                                color = accent,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                    Text(
                        text = when {
                            config.isTwoYearTermComplete ->
                                "Your 2-year commitment is complete. You may now initialize a new 2-year One-Time PIN."
                            pinConsumed ->
                                "One-Time PIN consumed on ${cycleDateFormatter.format(Date(config.oneTimePinUsedAtMillis))}. All unlock, pause, and disable controls are sealed until ${cycleDateFormatter.format(Date(config.lockEndMillis))}."
                            else ->
                                "Your PIN is valid for 1 single unlock/override/disable action per 2-year term (until ${cycleDateFormatter.format(Date(config.lockEndMillis))})."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = EmeraldShield
                        )
                        Column {
                            Text(
                                text = "Administrator Security Controls",
                                style = MaterialTheme.typography.titleLarge
                            )
                            Text(
                                text = "Disabling or pausing protection consumes your 1-Time 2-Year PIN.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    SecurityToggleRow(
                        icon = Icons.Default.Shield,
                        title = "Active Blocker Shield",
                        subtitle = if (config.isProtectionActive) {
                            "Foreground monitor is blocking Chime, banking apps & Yahoo Mail."
                        } else {
                            "Shield is paused. Restricted apps are currently accessible."
                        },
                        checked = config.isProtectionActive,
                        testTag = "switch_protection_active",
                        onToggle = {
                            onRequestAdminAction(
                                PendingAdminAction.SetProtectionActive(!config.isProtectionActive)
                            )
                        }
                    )

                    SecurityToggleRow(
                        icon = Icons.Default.GppGood,
                        title = "Anti-Tamper Settings Shield",
                        subtitle = "Blocks Android Settings & Package Installer during the 2-year lock to prevent bypass.",
                        checked = config.isTamperGuardEnabled,
                        testTag = "switch_tamper_guard",
                        onToggle = {
                            onRequestAdminAction(
                                PendingAdminAction.SetTamperGuard(!config.isTamperGuardEnabled)
                            )
                        }
                    )

                    SecurityToggleRow(
                        icon = Icons.Default.InstallMobile,
                        title = "Block Installing Chime / Yahoo / Banks",
                        subtitle = "Blocks Play Store, Galaxy Store & APK installers and detects + locks new Chime, Yahoo Mail, or bank installs.",
                        checked = config.isInstallBlockerEnabled,
                        testTag = "switch_install_blocker",
                        onToggle = {
                            onRequestAdminAction(
                                PendingAdminAction.SetInstallBlocker(!config.isInstallBlockerEnabled)
                            )
                        }
                    )

                    SecurityToggleRow(
                        icon = Icons.Default.PublicOff,
                        title = "Block AI Studio in Any Browser",
                        subtitle = "Filters aistudio.google.com via on-device VPN DNS firewall across Chrome, Samsung Internet, Firefox & all browsers.",
                        checked = config.isAiStudioWebBlockerEnabled,
                        testTag = "switch_ai_studio_web_blocker",
                        onToggle = {
                            onRequestAdminAction(
                                PendingAdminAction.SetAiStudioWebBlocker(!config.isAiStudioWebBlockerEnabled)
                            )
                        }
                    )

                    SecurityToggleRow(
                        icon = Icons.Default.MoreTime,
                        title = "Auto-Lock Itself Per 2 Years",
                        subtitle = "Automatically re-arms VaultLock into the next 2-year cycle when the current 2-year term completes.",
                        checked = config.autoRelockEveryTwoYears,
                        testTag = "switch_auto_relock_2y",
                        onToggle = {
                            onRequestAdminAction(
                                PendingAdminAction.SetAutoRelockEveryTwoYears(!config.autoRelockEveryTwoYears)
                            )
                        }
                    )
                }
            }
        }

        // Uninstall Protection Card
        item {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(
                    1.dp,
                    if (sys.deviceAdminActive) EmeraldShield.copy(alpha = 0.45f) else CrimsonLock.copy(alpha = 0.45f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AdminPanelSettings,
                            contentDescription = null,
                            tint = if (sys.deviceAdminActive) EmeraldShield else CrimsonLock
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Uninstall Protection (Device Admin)",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = if (sys.deviceAdminActive) {
                                    "Active: Standard app uninstall is blocked by Android DevicePolicyManager."
                                } else {
                                    "Inactive: Activate Device Admin so VaultLock cannot be easily uninstalled."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (sys.deviceAdminActive) {
                        OutlinedButton(
                            onClick = {
                                onRequestAdminAction(PendingAdminAction.DeactivateDeviceAdmin)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("deactivate_device_admin_button"),
                            border = BorderStroke(1.dp, CrimsonLock.copy(alpha = 0.6f)),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = CrimsonLock,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Deactivate Uninstall Protection (1-Time PIN)",
                                color = CrimsonLock,
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    } else {
                        Button(
                            onClick = onActivateDeviceAdmin,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("activate_device_admin_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = EmeraldShield,
                                contentColor = ObsidianVoid
                            ),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text(
                                text = "Enable Device Admin Uninstall Guard",
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            }
        }

        // Management Actions Card
        item {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "2-Year Cycle & PIN Tasks",
                        style = MaterialTheme.typography.titleMedium
                    )

                    OutlinedButton(
                        onClick = { onRequestAdminAction(PendingAdminAction.StartNextTwoYearPinCycle) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("change_admin_pin_button"),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Key,
                            contentDescription = null,
                            tint = if (config.isTwoYearTermComplete) EmeraldShield else SovereignGold,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (config.isTwoYearTermComplete) {
                                "Issue Next 2-Year One-Time PIN"
                            } else {
                                "PIN Locked Until 2-Year Cycle Ends"
                            },
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    OutlinedButton(
                        onClick = { onRequestAdminAction(PendingAdminAction.UnlockSettingsGraceWindow) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("unlock_settings_window_button"),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SettingsApplications,
                            contentDescription = null,
                            tint = EmeraldShield,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Authorize 45s Settings Window (1-Time PIN)",
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    OutlinedButton(
                        onClick = { onRequestAdminAction(PendingAdminAction.ExtendLockTwoYears) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("extend_lock_button"),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreTime,
                            contentDescription = null,
                            tint = SovereignGold,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Extend Lock +2 Years", color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }

        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.History,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "SECURITY & TAMPER AUDIT LOG (${uiState.securityEvents.size})",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (uiState.securityEvents.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Security audit events will appear here when VaultLock intercepts Chime, banking apps, Yahoo Mail, or tamper attempts.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(18.dp)
                    )
                }
            }
        } else {
            items(uiState.securityEvents, key = { it.id }) { event ->
                SecurityAuditRow(event = event, dateFormatter = eventDateFormatter)
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

@Composable
private fun SecurityToggleRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    testTag: String,
    onToggle: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (checked) EmeraldShield else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = checked,
                onCheckedChange = { onToggle() },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = ObsidianVoid,
                    checkedTrackColor = EmeraldShield
                ),
                modifier = Modifier.testTag(testTag)
            )
        }
    }
}

@Composable
private fun SecurityAuditRow(
    event: SecurityEventEntity,
    dateFormatter: SimpleDateFormat
) {
    val accent: Color = when (event.eventType) {
        VaultRepository.EVENT_TAMPER_ATTEMPT,
        VaultRepository.EVENT_INSTALL_BLOCKED,
        VaultRepository.EVENT_WEB_BLOCKED,
        VaultRepository.EVENT_ONE_TIME_PIN_USED -> CrimsonLock
        VaultRepository.EVENT_BLOCK_INTERCEPTED,
        VaultRepository.EVENT_ADMIN_OVERRIDE -> SovereignGold
        else -> EmeraldShield
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = if (event.eventType == VaultRepository.EVENT_TAMPER_ATTEMPT ||
                    event.eventType == VaultRepository.EVENT_INSTALL_BLOCKED ||
                    event.eventType == VaultRepository.EVENT_WEB_BLOCKED ||
                    event.eventType == VaultRepository.EVENT_ONE_TIME_PIN_USED
                ) {
                    Icons.Default.WarningAmber
                } else {
                    Icons.Default.Shield
                },
                contentDescription = null,
                tint = accent,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(20.dp)
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = event.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = event.detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = dateFormatter.format(Date(event.createdAt)),
                    style = MaterialTheme.typography.labelSmall,
                    color = accent
                )
            }
        }
    }
}
