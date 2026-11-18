package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val NeonDarkColorScheme = darkColorScheme(
    primary = ElectricCyan,
    onPrimary = DeepObsidian,
    primaryContainer = DeepCyan,
    onPrimaryContainer = ElectricCyan,
    secondary = HyperMagenta,
    onSecondary = CrispIce,
    secondaryContainer = DeepMagenta,
    onSecondaryContainer = HyperMagenta,
    tertiary = LaserAmber,
    onTertiary = DeepObsidian,
    background = DeepObsidian,
    onBackground = CrispIce,
    surface = MidnightSlate,
    onSurface = CrispIce,
    surfaceVariant = ElevatedNavy,
    onSurfaceVariant = MutedSteel,
    outline = GridLineSlate,
    error = PlasmaRed,
    onError = CrispIce
)

private val NeonShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun NeonDashTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = NeonDarkColorScheme,
        typography = NeonDashTypography,
        shapes = NeonShapes,
        content = content
    )
}
