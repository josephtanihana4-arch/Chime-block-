package com.example

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import android.app.Activity
import com.example.security.SystemPermissionsHelper
import com.example.ui.VaultViewModel
import com.example.ui.components.ADMIN_PIN_LENGTH
import com.example.ui.components.NumericPinPad
import com.example.ui.components.PinChallengeDialog
import com.example.ui.components.PinDotsIndicator
import com.example.ui.screens.BlockedAppsScreen
import com.example.ui.screens.OnboardingSetupScreen
import com.example.ui.screens.SecurityCenterScreen
import com.example.ui.screens.VaultDashboardScreen
import com.example.ui.theme.CrimsonLock
import com.example.ui.theme.EmeraldShield
import com.example.ui.theme.SovereignGold
import com.example.ui.theme.VaultLockTheme

const val ROUTE_VAULT = "vault_dashboard"
const val ROUTE_BLOCKLIST = "blocked_apps"
const val ROUTE_SECURITY = "security_center"

private data class VaultNavItem(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VaultLockTheme(darkTheme = true) {
                VaultLockApp()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultLockApp(
    viewModel: VaultViewModel = viewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val pendingAction by viewModel.pendingAction.collectAsStateWithLifecycle()
    val pinError by viewModel.pinError.collectAsStateWithLifecycle()
    val showNewCyclePinDialog by viewModel.showNewCyclePinDialog.collectAsStateWithLifecycle()
    val statusBanner by viewModel.statusBanner.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(statusBanner) {
        val message = statusBanner ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.clearStatusBanner()
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshDeviceState()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        viewModel.refreshDeviceState()
    }

    val deviceAdminLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.refreshDeviceState()
    }

    val vpnPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK || SystemPermissionsHelper.hasVpnPermission(context)) {
            viewModel.onVpnConsentGranted()
        } else {
            viewModel.refreshDeviceState()
        }
    }

    val openUsageAccess = {
        viewModel.grantSettingsGraceBeforePermissionLaunch()
        context.startActivity(SystemPermissionsHelper.createUsageAccessIntent())
    }

    val openOverlayPermission = {
        viewModel.grantSettingsGraceBeforePermissionLaunch()
        context.startActivity(SystemPermissionsHelper.createOverlayPermissionIntent(context))
    }

    val activateDeviceAdmin = {
        viewModel.grantSettingsGraceBeforePermissionLaunch()
        deviceAdminLauncher.launch(SystemPermissionsHelper.createDeviceAdminIntent(context))
    }

    val requestNotificationPermission = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val enableWebVpn = {
        val vpnIntent = SystemPermissionsHelper.createVpnPermissionIntent(context)
        if (vpnIntent != null) {
            vpnPermissionLauncher.launch(vpnIntent)
        } else {
            viewModel.onVpnConsentGranted()
        }
    }

    if (!uiState.config.isPinConfigured) {
        OnboardingSetupScreen(
            onSetupComplete = { pin, goalCents, includeCoreBanks ->
                viewModel.completeOnboardingSetup(pin, goalCents, includeCoreBanks)
            }
        )
        return
    }

    val navItems = remember {
        listOf(
            VaultNavItem(
                route = ROUTE_VAULT,
                label = "Vault",
                selectedIcon = Icons.Filled.Lock,
                unselectedIcon = Icons.Outlined.Lock,
                testTag = "nav_vault"
            ),
            VaultNavItem(
                route = ROUTE_BLOCKLIST,
                label = "Banks",
                selectedIcon = Icons.Filled.AccountBalance,
                unselectedIcon = Icons.Outlined.AccountBalance,
                testTag = "nav_blocklist"
            ),
            VaultNavItem(
                route = ROUTE_SECURITY,
                label = "Security",
                selectedIcon = Icons.Filled.Security,
                unselectedIcon = Icons.Outlined.Security,
                testTag = "nav_security"
            )
        )
    }

    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: ROUTE_VAULT

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavigationRail = maxWidth >= 680.dp

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            contentWindowInsets = WindowInsets.safeDrawing,
            containerColor = MaterialTheme.colorScheme.background,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = EmeraldShield,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "VaultLock",
                                style = MaterialTheme.typography.titleLarge
                            )
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            },
            bottomBar = {
                if (!useNavigationRail) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        tonalElevation = 4.dp
                    ) {
                        navItems.forEach { item ->
                            val selected = currentRoute == item.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    navController.navigate(item.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                        contentDescription = item.label
                                    )
                                },
                                label = { Text(item.label) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = EmeraldShield,
                                    selectedTextColor = EmeraldShield,
                                    indicatorColor = EmeraldShield.copy(alpha = 0.16f)
                                ),
                                modifier = Modifier.testTag(item.testTag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useNavigationRail) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        navItems.forEach { item ->
                            val selected = currentRoute == item.route
                            NavigationRailItem(
                                selected = selected,
                                onClick = {
                                    navController.navigate(item.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                        contentDescription = item.label
                                    )
                                },
                                label = { Text(item.label) },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = EmeraldShield,
                                    selectedTextColor = EmeraldShield,
                                    indicatorColor = EmeraldShield.copy(alpha = 0.16f)
                                ),
                                modifier = Modifier.testTag(item.testTag)
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.TopCenter
                ) {
                    NavHost(
                        navController = navController,
                        startDestination = ROUTE_VAULT,
                        modifier = Modifier
                            .fillMaxSize()
                            .widthIn(max = 840.dp)
                    ) {
                        composable(ROUTE_VAULT) {
                            VaultDashboardScreen(
                                uiState = uiState,
                                onGrantUsageAccess = openUsageAccess,
                                onGrantOverlayAccess = openOverlayPermission,
                                onActivateDeviceAdmin = activateDeviceAdmin,
                                onRequestNotificationPermission = requestNotificationPermission,
                                onEnableWebVpn = enableWebVpn,
                                onLogDeposit = { cents, note, month ->
                                    viewModel.logSavingsDeposit(cents, note, month)
                                },
                                onRequestAdminAction = { action ->
                                    viewModel.requestAdminAction(action)
                                }
                            )
                        }
                        composable(ROUTE_BLOCKLIST) {
                            BlockedAppsScreen(
                                uiState = uiState,
                                onGrantUsageAccess = openUsageAccess,
                                onGrantOverlayAccess = openOverlayPermission,
                                onScanInstalledBanks = { viewModel.scanInstalledBanksNow() },
                                onRequestAdminAction = { action ->
                                    viewModel.requestAdminAction(action)
                                }
                            )
                        }
                        composable(ROUTE_SECURITY) {
                            SecurityCenterScreen(
                                uiState = uiState,
                                onActivateDeviceAdmin = activateDeviceAdmin,
                                onRequestAdminAction = { action ->
                                    viewModel.requestAdminAction(action)
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    pendingAction?.let { action ->
        PinChallengeDialog(
            title = action.promptTitle,
            subtitle = action.promptSubtitle,
            oneTimeWarning = if (action.consumesOneTimePin) {
                "SINGLE-USE PER 2 YEARS: Confirming consumes your only PIN use until the 2-year term completes."
            } else {
                null
            },
            errorMessage = pinError,
            onDismiss = { viewModel.dismissPinChallenge() },
            onPinSubmitted = { pin -> viewModel.submitAdminPin(pin) }
        )
    }

    if (showNewCyclePinDialog) {
        ChangePinModal(
            onDismiss = { viewModel.dismissNewCyclePinDialog() },
            onNewPinConfirmed = { newPin -> viewModel.completeNewTwoYearCyclePin(newPin) }
        )
    }
}

@Composable
private fun ChangePinModal(
    onDismiss: () -> Unit,
    onNewPinConfirmed: (String) -> Unit
) {
    var firstPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var isConfirmStep by remember { mutableStateOf(false) }
    var mismatchError by remember { mutableStateOf<String?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .padding(20.dp)
                .fillMaxWidth()
                .widthIn(max = 420.dp),
            shape = RoundedCornerShape(26.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = if (isConfirmStep) "Confirm New PIN" else "Enter New 6-Digit PIN",
                    style = MaterialTheme.typography.headlineSmall
                )
                Text(
                    text = if (isConfirmStep) {
                        "Re-enter your new Administrator PIN."
                    } else {
                        "Choose a new 6-digit Administrator PIN."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                PinDotsIndicator(
                    pinLength = ADMIN_PIN_LENGTH,
                    filledCount = if (isConfirmStep) confirmPin.length else firstPin.length,
                    isError = mismatchError != null
                )
                if (mismatchError != null) {
                    Text(
                        text = mismatchError.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = CrimsonLock
                    )
                }
                NumericPinPad(
                    onDigit = { digit ->
                        mismatchError = null
                        if (!isConfirmStep) {
                            if (firstPin.length < ADMIN_PIN_LENGTH) {
                                firstPin += digit
                                if (firstPin.length == ADMIN_PIN_LENGTH) {
                                    isConfirmStep = true
                                }
                            }
                        } else {
                            if (confirmPin.length < ADMIN_PIN_LENGTH) {
                                confirmPin += digit
                                if (confirmPin.length == ADMIN_PIN_LENGTH) {
                                    if (confirmPin == firstPin) {
                                        onNewPinConfirmed(confirmPin)
                                    } else {
                                        mismatchError = "PINs did not match. Enter new PIN again."
                                        firstPin = ""
                                        confirmPin = ""
                                        isConfirmStep = false
                                    }
                                }
                            }
                        }
                    },
                    onBackspace = {
                        if (isConfirmStep) {
                            if (confirmPin.isNotEmpty()) confirmPin = confirmPin.dropLast(1)
                        } else {
                            if (firstPin.isNotEmpty()) firstPin = firstPin.dropLast(1)
                        }
                    },
                    onClear = {
                        if (isConfirmStep) confirmPin = "" else firstPin = ""
                    }
                )
                TextButton(onClick = onDismiss) {
                    Text("Cancel", color = SovereignGold)
                }
            }
        }
    }
}
