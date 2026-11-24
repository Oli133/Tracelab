package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val CadDarkColorScheme = darkColorScheme(
    primary = SolderEmerald,
    onPrimary = SolderEmeraldDark,
    primaryContainer = SolderEmeraldContainer,
    onPrimaryContainer = SolderEmeraldLight,
    secondary = CopperAmber,
    onSecondary = CopperAmberDark,
    secondaryContainer = CopperAmberContainer,
    onSecondaryContainer = CopperAmberLight,
    tertiary = TraceCyan,
    onTertiary = CadObsidian,
    tertiaryContainer = TraceCyanContainer,
    onTertiaryContainer = TraceCyanLight,
    background = CadObsidian,
    onBackground = SilkscreenWhite,
    surface = CadSurface,
    onSurface = SilkscreenWhite,
    surfaceVariant = CadSurfaceElevated,
    onSurfaceVariant = SilkscreenMuted,
    surfaceContainerLowest = CadObsidian,
    surfaceContainerLow = CadSurface,
    surfaceContainer = CadSurfaceElevated,
    surfaceContainerHigh = CadSurfaceHigh,
    outline = CadOutline,
    outlineVariant = CadGridMajor,
    error = DiagnosticRed,
    onError = SilkscreenWhite,
    errorContainer = DiagnosticRedContainer,
    onErrorContainer = DiagnosticRedLight
)

private val BlueprintLightColorScheme = lightColorScheme(
    primary = BlueprintTeal,
    onPrimary = BlueprintSurface,
    primaryContainer = BlueprintTealContainer,
    onPrimaryContainer = BlueprintInk,
    secondary = BlueprintCopper,
    onSecondary = BlueprintSurface,
    secondaryContainer = BlueprintCopperContainer,
    onSecondaryContainer = BlueprintInk,
    tertiary = BlueprintTraceBlue,
    onTertiary = BlueprintSurface,
    tertiaryContainer = BlueprintTraceBlueContainer,
    onTertiaryContainer = BlueprintInk,
    background = BlueprintPaper,
    onBackground = BlueprintInk,
    surface = BlueprintSurface,
    onSurface = BlueprintInk,
    surfaceVariant = BlueprintSurfaceVariant,
    onSurfaceVariant = BlueprintMuted,
    outline = BlueprintOutline,
    error = DiagnosticRed,
    onError = BlueprintSurface,
    errorContainer = DiagnosticRedLight,
    onErrorContainer = DiagnosticRedContainer
)

val TraceLabShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

@Composable
fun TraceLabTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) CadDarkColorScheme else BlueprintLightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = TraceLabTypography,
        shapes = TraceLabShapes,
        content = content
    )
}
