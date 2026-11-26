package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.components.ADMIN_PIN_LENGTH
import com.example.ui.components.NumericPinPad
import com.example.ui.components.PinDotsIndicator
import com.example.ui.theme.CrimsonLock
import com.example.ui.theme.DeepVaultNavy
import com.example.ui.theme.EmeraldShield
import com.example.ui.theme.ObsidianVoid
import com.example.ui.theme.SovereignGold

@Composable
fun OnboardingSetupScreen(
    onSetupComplete: (pin: String, savingsGoalCents: Long, includeCoreBanks: Boolean) -> Unit
) {
    var step by remember { mutableIntStateOf(0) }
    var goalDollarsText by remember { mutableStateOf("12000") }
    var includeCoreBanks by remember { mutableStateOf(true) }
    var firstPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf<String?>(null) }

    val presetGoals = listOf("6000", "12000", "24000", "50000")

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(ObsidianVoid, DeepVaultNavy)
                )
            )
            .systemBarsPadding(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 560.dp)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(170.dp)
                    .clip(RoundedCornerShape(24.dp))
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_vault_banner),
                    contentDescription = "VaultLock Security Vault",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.2f),
                                    ObsidianVoid.copy(alpha = 0.92f)
                                )
                            )
                        )
                )
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = EmeraldShield,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "STEP ${step + 1} OF 3",
                            style = MaterialTheme.typography.labelMedium,
                            color = EmeraldShield
                        )
                    }
                    Text(
                        text = when (step) {
                            0 -> "2-Year Savings Vault"
                            1 -> "Set One-Time 2-Year PIN"
                            else -> "Confirm One-Time PIN"
                        },
                        style = MaterialTheme.typography.headlineMedium,
                        color = Color.White
                    )
                }
            }

            when (step) {
                0 -> {
                    Surface(
                        shape = RoundedCornerShape(22.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
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
                                    text = "Set Your 2-Year Savings Target",
                                    style = MaterialTheme.typography.titleLarge
                                )
                            }
                            Text(
                                text = "VaultLock blocks Chime Finance, Yahoo Mail, and banking/payment apps for 2 full years (24 months) so withdrawals and password-reset bypasses stay locked.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                presetGoals.forEach { preset ->
                                    val selected = goalDollarsText == preset
                                    FilterChip(
                                        selected = selected,
                                        onClick = { goalDollarsText = preset },
                                        label = { Text("$${preset.toInt() / 1000}k") },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = EmeraldShield.copy(alpha = 0.2f),
                                            selectedLabelColor = EmeraldShield
                                        ),
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }

                            OutlinedTextField(
                                value = goalDollarsText,
                                onValueChange = { input ->
                                    goalDollarsText = input.filter { it.isDigit() }.take(7)
                                },
                                label = { Text("2-Year Target Amount (USD)") },
                                prefix = { Text("$") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = EmeraldShield,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("onboarding_goal_input")
                            )

                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = includeCoreBanks,
                                        onCheckedChange = { includeCoreBanks = it },
                                        colors = CheckboxDefaults.colors(checkedColor = EmeraldShield)
                                    )
                                    Column(modifier = Modifier.padding(start = 6.dp)) {
                                        Text(
                                            text = "Pre-lock Chime, Yahoo Mail & Major Banks",
                                            style = MaterialTheme.typography.titleSmall
                                        )
                                        Text(
                                            text = "Blocks Chime Finance, Yahoo Mail, Cash App, Venmo, Chase & 15 more apps.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            Button(
                                onClick = {
                                    pinError = null
                                    step = 1
                                },
                                enabled = (goalDollarsText.toLongOrNull() ?: 0L) > 0L,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(54.dp)
                                    .testTag("onboarding_next_button"),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = EmeraldShield,
                                    contentColor = ObsidianVoid
                                ),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Text(
                                    text = "Continue to One-Time PIN",
                                    style = MaterialTheme.typography.labelLarge
                                )
                                Spacer(modifier = Modifier.size(8.dp))
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = null
                                )
                            }
                        }
                    }
                }

                1 -> {
                    Surface(
                        shape = RoundedCornerShape(22.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(onClick = { step = 0 }) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Back"
                                    )
                                }
                                Text(
                                    text = "Set 6-Digit One-Time PIN",
                                    style = MaterialTheme.typography.titleLarge
                                )
                            }
                            Text(
                                text = "One-Time PIN Policy: You get 1 single PIN use per 2-year lock term for overrides, unblocking, or disabling protection. Once used, changes lock until 2 years expire.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            PinDotsIndicator(
                                pinLength = ADMIN_PIN_LENGTH,
                                filledCount = firstPin.length
                            )
                            NumericPinPad(
                                onDigit = { digit ->
                                    if (firstPin.length < ADMIN_PIN_LENGTH) {
                                        val next = firstPin + digit
                                        firstPin = next
                                        if (next.length == ADMIN_PIN_LENGTH) {
                                            confirmPin = ""
                                            pinError = null
                                            step = 2
                                        }
                                    }
                                },
                                onBackspace = {
                                    if (firstPin.isNotEmpty()) firstPin = firstPin.dropLast(1)
                                },
                                onClear = { firstPin = "" }
                            )
                        }
                    }
                }

                else -> {
                    Surface(
                        shape = RoundedCornerShape(22.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(onClick = {
                                    firstPin = ""
                                    confirmPin = ""
                                    pinError = null
                                    step = 1
                                }) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Back"
                                    )
                                }
                                Text(
                                    text = "Verify & Seal 2-Year Vault",
                                    style = MaterialTheme.typography.titleLarge
                                )
                            }
                            Text(
                                text = "Re-enter your 6-digit One-Time PIN to arm the 2-year Chime, Yahoo Mail & banking lock.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            PinDotsIndicator(
                                pinLength = ADMIN_PIN_LENGTH,
                                filledCount = confirmPin.length,
                                isError = pinError != null
                            )
                            if (pinError != null) {
                                Text(
                                    text = pinError.orEmpty(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = CrimsonLock
                                )
                            }
                            NumericPinPad(
                                onDigit = { digit ->
                                    pinError = null
                                    if (confirmPin.length < ADMIN_PIN_LENGTH) {
                                        val next = confirmPin + digit
                                        confirmPin = next
                                        if (next.length == ADMIN_PIN_LENGTH) {
                                            if (next == firstPin) {
                                                val dollars = (goalDollarsText.toLongOrNull() ?: 12000L).coerceAtLeast(1L)
                                                onSetupComplete(next, dollars * 100L, includeCoreBanks)
                                            } else {
                                                pinError = "PINs did not match. Try again."
                                                confirmPin = ""
                                            }
                                        }
                                    }
                                },
                                onBackspace = {
                                    if (confirmPin.isNotEmpty()) confirmPin = confirmPin.dropLast(1)
                                },
                                onClear = { confirmPin = "" }
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = EmeraldShield,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "PBKDF2WithHmacSHA256 • 1 Use Per 2 Years",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = EmeraldShield.copy(alpha = 0.1f),
                border = BorderStroke(1.dp, EmeraldShield.copy(alpha = 0.25f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = EmeraldShield,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Uses real Android Usage Access, Overlay Shield, and Device Admin uninstall protection — no simulator.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
