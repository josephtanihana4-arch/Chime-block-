package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PublicOff
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.db.SavingsDepositEntity
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
fun VaultDashboardScreen(
    uiState: VaultUiState,
    onGrantUsageAccess: () -> Unit,
    onGrantOverlayAccess: () -> Unit,
    onActivateDeviceAdmin: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onEnableWebVpn: () -> Unit,
    onLogDeposit: (amountCents: Long, note: String, monthNumber: Int) -> Unit,
    onRequestAdminAction: (PendingAdminAction) -> Unit,
    modifier: Modifier = Modifier
) {
    var showAddDepositDialog by remember { mutableStateOf(false) }
    var showEditGoalDialog by remember { mutableStateOf(false) }

    val config = uiState.config
    val sys = uiState.systemStatus
    val totalWindowMs = (config.lockEndMillis - config.lockStartMillis).coerceAtLeast(1L)
    val elapsedMs = (uiState.nowMillis - config.lockStartMillis).coerceIn(0L, totalWindowMs)
    val remainingMs = (config.lockEndMillis - uiState.nowMillis).coerceAtLeast(0L)
    val timeProgress = (elapsedMs.toFloat() / totalWindowMs.toFloat()).coerceIn(0f, 1f)

    val daysLeft = remainingMs / (1000L * 60 * 60 * 24)
    val hoursLeft = (remainingMs / (1000L * 60 * 60)) % 24
    val minsLeft = (remainingMs / (1000L * 60)) % 60
    val secsLeft = (remainingMs / 1000L) % 60

    val activeBlockedCount = uiState.blockedApps.count { it.isBlocked }
    val savingsProgress = (uiState.totalSavedCents.toFloat() / config.savingsGoalCents.coerceAtLeast(1L).toFloat())
        .coerceIn(0f, 1f)
    val currentMonthIndex = ((elapsedMs / (1000L * 60 * 60 * 24 * 30L)).toInt() + 1).coerceIn(1, 24)

    val dateFormatter = remember { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Hero 2-Year Vault Card
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(26.dp))
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_vault_banner),
                    contentDescription = "2-Year Savings Vault Header",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize()
                )
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    ObsidianVoid.copy(alpha = 0.55f),
                                    ObsidianVoid.copy(alpha = 0.94f)
                                )
                            )
                        )
                )

                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val isFullyArmed = config.isProtectionActive && sys.isCoreBlockingReady && sys.deviceAdminActive
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = when {
                                isFullyArmed -> EmeraldShield.copy(alpha = 0.18f)
                                config.isProtectionActive -> SovereignGold.copy(alpha = 0.18f)
                                else -> CrimsonLock.copy(alpha = 0.18f)
                            },
                            border = BorderStroke(
                                1.dp,
                                when {
                                    isFullyArmed -> EmeraldShield
                                    config.isProtectionActive -> SovereignGold
                                    else -> CrimsonLock
                                }
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = if (isFullyArmed) Icons.Default.Shield else Icons.Default.WarningAmber,
                                    contentDescription = null,
                                    tint = when {
                                        isFullyArmed -> EmeraldShield
                                        config.isProtectionActive -> SovereignGold
                                        else -> CrimsonLock
                                    },
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = when {
                                        isFullyArmed -> "ARMORED & LOCKED"
                                        config.isProtectionActive -> "FINISH OS PERMISSIONS"
                                        else -> "SHIELD PAUSED"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = Color.White.copy(alpha = 0.08f)
                        ) {
                            Text(
                                text = "Cycle #${config.lockCycleNumber} • M$currentMonthIndex/24",
                                style = MaterialTheme.typography.labelMedium,
                                color = SovereignGold,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = if (config.autoRelockEveryTwoYears) {
                                "SELF-LOCKED • AUTO-RELOCKS PER 2 YEARS"
                            } else {
                                "2-YEAR SAVINGS LOCK COUNTDOWN"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = if (remainingMs > 0L) {
                                "${daysLeft}d  ${"%02d".format(hoursLeft)}h  ${"%02d".format(minsLeft)}m  ${"%02d".format(secsLeft)}s"
                            } else {
                                "2-Year Commitment Complete"
                            },
                            style = MaterialTheme.typography.displaySmall,
                            color = Color.White
                        )
                        if (config.lockEndMillis > 0L) {
                            Text(
                                text = if (config.autoRelockEveryTwoYears) {
                                    "Cycle #${config.lockCycleNumber} ends ${dateFormatter.format(Date(config.lockEndMillis))} (auto-renews 2y)"
                                } else {
                                    "Unlocks on ${dateFormatter.format(Date(config.lockEndMillis))}"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    LinearProgressIndicator(
                        progress = { timeProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(999.dp)),
                        color = EmeraldShield,
                        trackColor = Color.White.copy(alpha = 0.14f),
                        strokeCap = StrokeCap.Round
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        StatMiniPill(
                            label = "Locked Apps",
                            value = "$activeBlockedCount apps",
                            accent = EmeraldShield,
                            modifier = Modifier.weight(1f)
                        )
                        StatMiniPill(
                            label = "One-Time PIN",
                            value = if (config.isOneTimePinConsumed) "0/1 left" else "1/1 ready",
                            accent = if (config.isOneTimePinConsumed) CrimsonLock else SovereignGold,
                            modifier = Modifier.weight(1f)
                        )
                        StatMiniPill(
                            label = "Stopped",
                            value = "${uiState.totalBlockedAttempts}x",
                            accent = SovereignGold,
                            modifier = Modifier.weight(0.85f)
                        )
                    }
                }
            }
        }

        // Real Android OS Enforcement Readiness Card
        item {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(
                    1.dp,
                    if (sys.readinessCount == 5) EmeraldShield.copy(alpha = 0.4f) else SovereignGold.copy(alpha = 0.5f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Device Enforcement Guard",
                                style = MaterialTheme.typography.titleLarge
                            )
                            Text(
                                text = "${sys.readinessCount}/5 OS security layers active",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = if (sys.readinessCount == 5) EmeraldShield else SovereignGold
                        )
                    }

                    SystemPermissionRow(
                        icon = Icons.Default.QueryStats,
                        title = "Usage Access (Chime, Banks & Yahoo Mail)",
                        subtitle = "Real-time foreground detection for restricted apps.",
                        isGranted = sys.usageStatsGranted,
                        actionLabel = "Grant",
                        testTag = "perm_usage_stats_button",
                        onAction = onGrantUsageAccess
                    )

                    SystemPermissionRow(
                        icon = Icons.Default.Layers,
                        title = "Display Over Other Apps",
                        subtitle = "Projects the instant lock shield over blocked apps.",
                        isGranted = sys.overlayGranted,
                        actionLabel = "Enable",
                        testTag = "perm_overlay_button",
                        onAction = onGrantOverlayAccess
                    )

                    SystemPermissionRow(
                        icon = Icons.Default.AdminPanelSettings,
                        title = "Device Admin (Uninstall Lock)",
                        subtitle = "Prevents standard app removal during the 2-year term.",
                        isGranted = sys.deviceAdminActive,
                        actionLabel = "Activate",
                        testTag = "perm_device_admin_button",
                        onAction = onActivateDeviceAdmin
                    )

                    SystemPermissionRow(
                        icon = Icons.Default.NotificationsActive,
                        title = "Background Guard Notification",
                        subtitle = "Keeps the foreground blocker service active across sessions.",
                        isGranted = sys.notificationsGranted,
                        actionLabel = "Allow",
                        testTag = "perm_notifications_button",
                        onAction = onRequestNotificationPermission
                    )

                    SystemPermissionRow(
                        icon = Icons.Default.PublicOff,
                        title = "AI Studio Web Firewall (VPN DNS)",
                        subtitle = "Blocks aistudio.google.com in Chrome and every browser on the phone.",
                        isGranted = sys.vpnConsentGranted,
                        actionLabel = "Enable",
                        testTag = "perm_vpn_shield_button",
                        onAction = onEnableWebVpn
                    )
                }
            }
        }

        // 2-Year Savings Goal & Quarterly Milestones Card
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
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Savings,
                                contentDescription = null,
                                tint = SovereignGold
                            )
                            Text(
                                text = "2-Year Savings Tracker",
                                style = MaterialTheme.typography.titleLarge
                            )
                        }
                        IconButton(
                            onClick = { showEditGoalDialog = true },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("edit_savings_goal_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit savings goal (uses 1-Time PIN)",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Column {
                            Text(
                                text = VaultRepository.formatCents(uiState.totalSavedCents),
                                style = MaterialTheme.typography.displaySmall,
                                color = EmeraldShield
                            )
                            Text(
                                text = "saved of ${VaultRepository.formatCents(config.savingsGoalCents)} 2-year goal",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = "${(savingsProgress * 100).toInt()}%",
                            style = MaterialTheme.typography.headlineSmall,
                            color = SovereignGold
                        )
                    }

                    LinearProgressIndicator(
                        progress = { savingsProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(RoundedCornerShape(999.dp)),
                        color = SovereignGold,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        strokeCap = StrokeCap.Round
                    )

                    // 8 Quarters across 2 Years (Q1-Q4 Year 1, Q5-Q8 Year 2)
                    val quarterlyTargetCents = (config.savingsGoalCents / 8L).coerceAtLeast(1L)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (yearRow in 0..1) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                for (q in 1..4) {
                                    val quarterIndex = yearRow * 4 + q
                                    val startMonth = (quarterIndex - 1) * 3 + 1
                                    val endMonth = quarterIndex * 3
                                    val quarterSaved = uiState.deposits
                                        .filter { it.monthNumber in startMonth..endMonth }
                                        .sumOf { it.amountCents }
                                    val hit = quarterSaved >= quarterlyTargetCents
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (hit) EmeraldShield.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceVariant,
                                        border = BorderStroke(
                                            1.dp,
                                            if (hit) EmeraldShield else MaterialTheme.colorScheme.outlineVariant
                                        ),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(2.dp)
                                        ) {
                                            Text(
                                                text = "Y${yearRow + 1} Q$q",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (hit) EmeraldShield else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                text = "$${quarterSaved / 100}",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Button(
                        onClick = { showAddDepositDialog = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("log_savings_deposit_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = EmeraldShield,
                            contentColor = ObsidianVoid
                        ),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Log Savings Deposit",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }

        item {
            Text(
                text = "2-YEAR SAVINGS LEDGER (${uiState.deposits.size})",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        if (uiState.deposits.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Savings,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(32.dp)
                        )
                        Text(
                            text = "No savings entries yet",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Log each amount you keep unspent while Chime, banking apps, and Yahoo Mail are locked.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(uiState.deposits, key = { it.id }) { deposit ->
                DepositLedgerRow(
                    deposit = deposit,
                    dateFormatter = dateFormatter,
                    onRequestDelete = {
                        onRequestAdminAction(PendingAdminAction.DeleteDeposit(deposit.id))
                    }
                )
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }

    if (showAddDepositDialog) {
        AddDepositDialog(
            defaultMonth = currentMonthIndex,
            onDismiss = { showAddDepositDialog = false },
            onConfirm = { amountCents, note, month ->
                showAddDepositDialog = false
                onLogDeposit(amountCents, note, month)
            }
        )
    }

    if (showEditGoalDialog) {
        EditGoalDialog(
            currentGoalDollars = config.savingsGoalCents / 100L,
            onDismiss = { showEditGoalDialog = false },
            onConfirm = { newDollars ->
                showEditGoalDialog = false
                onRequestAdminAction(PendingAdminAction.UpdateSavingsGoal(newDollars * 100L))
            }
        )
    }
}

@Composable
private fun StatMiniPill(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color.White.copy(alpha = 0.07f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = accent
            )
        }
    }
}

@Composable
private fun SystemPermissionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    isGranted: Boolean,
    actionLabel: String,
    testTag: String,
    onAction: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = (if (isGranted) EmeraldShield else SovereignGold).copy(alpha = 0.15f),
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (isGranted) Icons.Default.CheckCircle else icon,
                        contentDescription = null,
                        tint = if (isGranted) EmeraldShield else SovereignGold,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (isGranted) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = EmeraldShield.copy(alpha = 0.16f)
                ) {
                    Text(
                        text = "ACTIVE",
                        style = MaterialTheme.typography.labelSmall,
                        color = EmeraldShield,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            } else {
                OutlinedButton(
                    onClick = onAction,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    border = BorderStroke(1.dp, SovereignGold),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag(testTag)
                ) {
                    Text(
                        text = actionLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = SovereignGold
                    )
                }
            }
        }
    }
}

@Composable
private fun DepositLedgerRow(
    deposit: SavingsDepositEntity,
    dateFormatter: SimpleDateFormat,
    onRequestDelete: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = EmeraldShield.copy(alpha = 0.15f)
            ) {
                Text(
                    text = "M${deposit.monthNumber}",
                    style = MaterialTheme.typography.labelMedium,
                    color = EmeraldShield,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = deposit.note,
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = dateFormatter.format(Date(deposit.createdAt)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = "+${VaultRepository.formatCents(deposit.amountCents)}",
                style = MaterialTheme.typography.titleMedium,
                color = EmeraldShield
            )
            IconButton(
                onClick = onRequestDelete,
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = "Delete deposit (uses 1-Time PIN)",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun AddDepositDialog(
    defaultMonth: Int,
    onDismiss: () -> Unit,
    onConfirm: (amountCents: Long, note: String, monthNumber: Int) -> Unit
) {
    var dollarsText by remember { mutableStateOf("") }
    var noteText by remember { mutableStateOf("") }
    var selectedMonth by remember { mutableIntStateOf(defaultMonth.coerceIn(1, 24)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Log Savings Deposit") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = dollarsText,
                    onValueChange = { dollarsText = it.filter { ch -> ch.isDigit() || ch == '.' } },
                    label = { Text("Amount Saved (USD)") },
                    prefix = { Text("$") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("deposit_amount_input")
                )
                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    label = { Text("Note (e.g., Skipped Chime transfer)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "Target Month (1–24)",
                    style = MaterialTheme.typography.labelMedium
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items((1..24).toList()) { m ->
                        FilterChip(
                            selected = selectedMonth == m,
                            onClick = { selectedMonth = m },
                            label = { Text("M$m") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = EmeraldShield.copy(alpha = 0.2f),
                                selectedLabelColor = EmeraldShield
                            )
                        )
                    }
                }
            }
        },
        confirmButton = {
            val parsedCents = ((dollarsText.toDoubleOrNull() ?: 0.0) * 100.0).toLong()
            Button(
                onClick = { onConfirm(parsedCents, noteText, selectedMonth) },
                enabled = parsedCents > 0L,
                modifier = Modifier.testTag("deposit_confirm_button")
            ) {
                Text("Save Entry")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun EditGoalDialog(
    currentGoalDollars: Long,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit
) {
    var goalText by remember { mutableStateOf(currentGoalDollars.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Adjust 2-Year Goal") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Changing your 2-year target consumes your single One-Time PIN allowance for this 2-year cycle.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = goalText,
                    onValueChange = { goalText = it.filter { ch -> ch.isDigit() }.take(7) },
                    label = { Text("New 2-Year Goal (USD)") },
                    prefix = { Text("$") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            val dollars = goalText.toLongOrNull() ?: 0L
            Button(
                onClick = { onConfirm(dollars) },
                enabled = dollars > 0L
            ) {
                Text("Use 1-Time PIN & Update")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
