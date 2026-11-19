package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val BioDarkColorScheme = darkColorScheme(
    primary = BioCyan,
    onPrimary = ObsidianDeep,
    primaryContainer = Color(0xFF0C3A56),
    onPrimaryContainer = Color(0xFFBAE6FD),
    secondary = BioTeal,
    onSecondary = ObsidianDeep,
    secondaryContainer = Color(0xFF0B3D3B),
    onSecondaryContainer = Color(0xFF99F6E4),
    tertiary = BioCoral,
    onTertiary = ObsidianDeep,
    tertiaryContainer = Color(0xFF4C1425),
    onTertiaryContainer = Color(0xFFFECDD3),
    background = ObsidianDeep,
    onBackground = TextPrimaryDark,
    surface = ObsidianSurface,
    onSurface = TextPrimaryDark,
    surfaceVariant = ObsidianElevated,
    onSurfaceVariant = TextSecondaryDark,
    outline = ObsidianCardBorder,
    outlineVariant = Color(0xFF17334C)
)

private val BioLightColorScheme = lightColorScheme(
    primary = ClinicalPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0F2FE),
    onPrimaryContainer = Color(0xFF0C4A6E),
    secondary = ClinicalSecondary,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCCFBF1),
    onSecondaryContainer = Color(0xFF115E59),
    tertiary = ClinicalTertiary,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFE4E6),
    onTertiaryContainer = Color(0xFF881337),
    background = ClinicalLightBg,
    onBackground = TextPrimaryLight,
    surface = ClinicalSurface,
    onSurface = TextPrimaryLight,
    surfaceVariant = ClinicalSurfaceVariant,
    onSurfaceVariant = TextSecondaryLight,
    outline = Color(0xFFCBD5E1),
    outlineVariant = Color(0xFFE2E8F0)
)

val BioAtlasShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun BioAtlasTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) BioDarkColorScheme else BioLightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = BioAtlasTypography,
        shapes = BioAtlasShapes,
        content = content
    )
}
