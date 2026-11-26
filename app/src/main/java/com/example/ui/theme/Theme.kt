package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val VaultDarkColorScheme = darkColorScheme(
    primary = EmeraldShield,
    onPrimary = ObsidianVoid,
    primaryContainer = EmeraldDeep,
    onPrimaryContainer = EmeraldGlow,
    secondary = SovereignGold,
    onSecondary = ObsidianVoid,
    secondaryContainer = SovereignGoldDeep,
    onSecondaryContainer = SovereignGoldSoft,
    tertiary = EmeraldGlow,
    onTertiary = ObsidianVoid,
    background = DeepVaultNavy,
    onBackground = IvoryText,
    surface = VaultSlate,
    onSurface = IvoryText,
    surfaceVariant = VaultSlateElevated,
    onSurfaceVariant = MutedSlateText,
    outline = VaultBorder,
    outlineVariant = Color(0xFF1D2C44),
    error = CrimsonLock,
    onError = Color.White,
    errorContainer = CrimsonDeep,
    onErrorContainer = Color(0xFFFCA5A5)
)

private val VaultLightColorScheme = lightColorScheme(
    primary = LightEmeraldPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD1FAE5),
    onPrimaryContainer = EmeraldDeep,
    secondary = LightGoldSecondary,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFEF3C7),
    onSecondaryContainer = SovereignGoldDeep,
    tertiary = EmeraldShield,
    onTertiary = Color.White,
    background = LightVaultBg,
    onBackground = LightObsidianText,
    surface = LightVaultSurface,
    onSurface = LightObsidianText,
    surfaceVariant = LightVaultSurfaceVariant,
    onSurfaceVariant = LightMutedText,
    outline = Color(0xFFCBD5E1),
    outlineVariant = Color(0xFFE2E8F0),
    error = CrimsonLock,
    onError = Color.White,
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = CrimsonDeep
)

val VaultShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun VaultLockTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) VaultDarkColorScheme else VaultLightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = VaultShapes,
        content = content
    )
}
