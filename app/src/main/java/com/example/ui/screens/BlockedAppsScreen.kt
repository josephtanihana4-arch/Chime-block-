package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.catalog.BankingAppCatalog
import com.example.data.catalog.InstalledDeviceApp
import com.example.data.db.BlockedAppEntity
import com.example.ui.PendingAdminAction
import com.example.ui.VaultUiState
import com.example.ui.theme.EmeraldShield
import com.example.ui.theme.ObsidianVoid
import com.example.ui.theme.SovereignGold

@Composable
fun BlockedAppsScreen(
    uiState: VaultUiState,
    onGrantUsageAccess: () -> Unit,
    onGrantOverlayAccess: () -> Unit,
    onScanInstalledBanks: () -> Unit,
    onRequestAdminAction: (PendingAdminAction) -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("All") }
    var showDevicePickerDialog by remember { mutableStateOf(false) }
    var showCustomPkgDialog by remember { mutableStateOf(false) }

    val filters = listOf(
        "All",
        "Installed",
        BankingAppCatalog.CAT_NEOBANK,
        BankingAppCatalog.CAT_MAIL,
        BankingAppCatalog.CAT_BANK,
        BankingAppCatalog.CAT_P2P,
        BankingAppCatalog.CAT_TRADING
    )

    val filteredApps = remember(uiState.blockedApps, uiState.installedPackageNames, searchQuery, selectedFilter) {
        uiState.blockedApps.filter { app ->
            val matchesQuery = searchQuery.isBlank() ||
                app.displayName.contains(searchQuery, ignoreCase = true) ||
                app.packageName.contains(searchQuery, ignoreCase = true)

            val matchesFilter = when (selectedFilter) {
                "All" -> true
                "Installed" -> uiState.installedPackageNames.contains(app.packageName)
                else -> app.category == selectedFilter
            }
            matchesQuery && matchesFilter
        }
    }

    val isEnforcementReady = uiState.config.isProtectionActive && uiState.systemStatus.isCoreBlockingReady

    Column(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = (if (isEnforcementReady) EmeraldShield else SovereignGold).copy(alpha = 0.12f),
                    border = BorderStroke(
                        1.dp,
                        if (isEnforcementReady) EmeraldShield.copy(alpha = 0.45f) else SovereignGold.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = if (isEnforcementReady) {
                                "REAL-TIME BLOCKER ENFORCING"
                            } else {
                                "GRANT OS PERMISSIONS TO ENFORCE BLOCKLIST"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isEnforcementReady) EmeraldShield else SovereignGold
                        )
                        Text(
                            text = if (isEnforcementReady) {
                                "Usage Access & Overlay Shield are active. Opening Chime, Yahoo Mail, banks, or app installers is blocked, and any newly installed bank/mail app is detected & locked automatically."
                            } else {
                                "VaultLock requires Android Usage Access and Display Over Other Apps to intercept Chime, Yahoo Mail, banking apps, and app installers in real time."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (!uiState.systemStatus.usageStatsGranted || !uiState.systemStatus.overlayGranted) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (!uiState.systemStatus.usageStatsGranted) {
                                    OutlinedButton(
                                        onClick = onGrantUsageAccess,
                                        border = BorderStroke(1.dp, SovereignGold),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Usage Access", color = SovereignGold)
                                    }
                                }
                                if (!uiState.systemStatus.overlayGranted) {
                                    OutlinedButton(
                                        onClick = onGrantOverlayAccess,
                                        border = BorderStroke(1.dp, SovereignGold),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Overlay Shield", color = SovereignGold)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AccountBalance,
                                contentDescription = null,
                                tint = EmeraldShield
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Chime, Banking & Yahoo Mail Guard",
                                    style = MaterialTheme.typography.titleLarge
                                )
                                Text(
                                    text = if (uiState.config.isOneTimePinConsumed) {
                                        "One-Time 2-Year PIN consumed — unblocking and uninstalling is sealed until cycle ends."
                                    } else {
                                        "Unlocking an app with your One-Time PIN allows unblocking and uninstalling Chime, banking apps, or Yahoo Mail (1 allowance per 2-year term)."
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = { showDevicePickerDialog = true },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("pick_installed_app_button"),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = EmeraldShield,
                                    contentColor = ObsidianVoid
                                ),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Apps,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Installed Apps", style = MaterialTheme.typography.labelLarge)
                            }

                            OutlinedButton(
                                onClick = { showCustomPkgDialog = true },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("add_custom_package_button"),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = null,
                                    tint = SovereignGold,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    "Custom ID",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        OutlinedButton(
                            onClick = onScanInstalledBanks,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .testTag("scan_device_banks_button"),
                            border = BorderStroke(1.dp, EmeraldShield.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text(
                                text = "Auto-Detect & Lock Installed Banks / Yahoo",
                                style = MaterialTheme.typography.labelLarge,
                                color = EmeraldShield
                            )
                        }

                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Search Chime, Yahoo Mail, Chase, package ID...") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "Search banking apps"
                                )
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("search_blocked_apps_input")
                        )
                    }
                }
            }

            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filters) { filter ->
                        FilterChip(
                            selected = selectedFilter == filter,
                            onClick = { selectedFilter = filter },
                            label = { Text(filter) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = EmeraldShield.copy(alpha = 0.2f),
                                selectedLabelColor = EmeraldShield
                            )
                        )
                    }
                }
            }

            if (filteredApps.isEmpty()) {
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
                            Text(
                                text = "No matching apps",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "Try clearing the filter or add any installed app from your device.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(filteredApps, key = { it.packageName }) { app ->
                    val isInstalled = uiState.installedPackageNames.contains(app.packageName)
                    BlockedAppRow(
                        app = app,
                        isInstalled = isInstalled,
                        onToggleRequested = {
                            onRequestAdminAction(
                                PendingAdminAction.ToggleAppBlock(
                                    packageName = app.packageName,
                                    displayName = app.displayName,
                                    targetBlocked = !app.isBlocked
                                )
                            )
                        },
                        onRemoveRequested = {
                            onRequestAdminAction(PendingAdminAction.RemoveCustomApp(app))
                        }
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }

    if (showDevicePickerDialog) {
        val existingPkgs = uiState.blockedApps.map { it.packageName }.toSet()
        InstalledAppsPickerDialog(
            installedApps = uiState.installedDeviceApps,
            alreadyListedPackages = existingPkgs,
            onDismiss = { showDevicePickerDialog = false },
            onSelectApp = { deviceApp ->
                showDevicePickerDialog = false
                onRequestAdminAction(
                    PendingAdminAction.AddCustomApp(
                        packageName = deviceApp.packageName,
                        displayName = deviceApp.displayName,
                        category = BankingAppCatalog.CAT_CUSTOM
                    )
                )
            }
        )
    }

    if (showCustomPkgDialog) {
        AddCustomPackageDialog(
            onDismiss = { showCustomPkgDialog = false },
            onConfirm = { pkg, name ->
                showCustomPkgDialog = false
                onRequestAdminAction(
                    PendingAdminAction.AddCustomApp(
                        packageName = pkg,
                        displayName = name,
                        category = BankingAppCatalog.CAT_CUSTOM
                    )
                )
            }
        )
    }
}

@Composable
private fun BlockedAppRow(
    app: BlockedAppEntity,
    isInstalled: Boolean,
    onToggleRequested: () -> Unit,
    onRemoveRequested: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            if (app.isBlocked) EmeraldShield.copy(alpha = 0.35f) else MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("blocked_app_card_${app.packageName}")
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = (if (app.isBlocked) EmeraldShield else MaterialTheme.colorScheme.surfaceVariant)
                    .copy(alpha = if (app.isBlocked) 0.16f else 1f),
                modifier = Modifier.size(44.dp)
            ) {
                Icon(
                    imageVector = if (app.isBlocked) Icons.Default.Lock else Icons.Default.LockOpen,
                    contentDescription = null,
                    tint = if (app.isBlocked) EmeraldShield else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(10.dp)
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = app.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (isInstalled) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = EmeraldShield.copy(alpha = 0.18f)
                        ) {
                            Text(
                                text = "INSTALLED",
                                style = MaterialTheme.typography.labelSmall,
                                color = EmeraldShield,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Text(
                    text = app.packageName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = app.category,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (app.blockAttemptsCount > 0) {
                        Text(
                            text = "• ${app.blockAttemptsCount} blocked",
                            style = MaterialTheme.typography.bodySmall,
                            color = SovereignGold
                        )
                    }
                }
            }

            if (app.isCustom) {
                IconButton(
                    onClick = onRemoveRequested,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Remove custom app (requires PIN)",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Switch(
                checked = app.isBlocked,
                onCheckedChange = { onToggleRequested() },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = ObsidianVoid,
                    checkedTrackColor = EmeraldShield
                ),
                modifier = Modifier.testTag("toggle_app_${app.packageName}")
            )
        }
    }
}

@Composable
private fun InstalledAppsPickerDialog(
    installedApps: List<InstalledDeviceApp>,
    alreadyListedPackages: Set<String>,
    onDismiss: () -> Unit,
    onSelectApp: (InstalledDeviceApp) -> Unit
) {
    var filterText by remember { mutableStateOf("") }
    val available = remember(installedApps, alreadyListedPackages, filterText) {
        installedApps.filter { app ->
            !alreadyListedPackages.contains(app.packageName) &&
                (filterText.isBlank() ||
                    app.displayName.contains(filterText, ignoreCase = true) ||
                    app.packageName.contains(filterText, ignoreCase = true))
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Installed Device App") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = filterText,
                    onValueChange = { filterText = it },
                    placeholder = { Text("Filter installed apps...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (available.isEmpty()) {
                        item {
                            Text(
                                text = "No additional launchable apps found matching your filter.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 12.dp)
                            )
                        }
                    } else {
                        items(available, key = { it.packageName }) { app ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelectApp(app) }
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = app.displayName,
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    Text(
                                        text = app.packageName,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
private fun AddCustomPackageDialog(
    onDismiss: () -> Unit,
    onConfirm: (packageName: String, displayName: String) -> Unit
) {
    var packageName by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Custom Bank Package") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Enter an Android package ID (for example: com.onedebit.chime) and verify your Administrator PIN to lock it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    label = { Text("App Name (e.g., Regional Credit Union)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = packageName,
                    onValueChange = { packageName = it.trim() },
                    label = { Text("Package ID (e.g., com.bank.mobile)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(packageName, displayName) },
                enabled = packageName.contains(".") && packageName.length >= 5
            ) {
                Text("Verify PIN & Block")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
