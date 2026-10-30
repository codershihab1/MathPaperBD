package bd.mathpaper

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// Brand colours come from the logo: teal paper + amber vertices, with a calm blue as the second accent.
private val Light = lightColorScheme(
    primary = Color(0xFF0B6E69), onPrimary = Color.White,
    primaryContainer = Color(0xFFC8EEEA), onPrimaryContainer = Color(0xFF00201E),
    secondary = Color(0xFF8A5A00), secondaryContainer = Color(0xFFFFDEA8), onSecondaryContainer = Color(0xFF2B1800),
    tertiary = Color(0xFF2F5D9B), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFD6E3FF), onTertiaryContainer = Color(0xFF001B3E),
    background = Color(0xFFF1F6F5), surface = Color(0xFFF1F6F5), onSurface = Color(0xFF171D1C),
    surfaceVariant = Color(0xFFDAE5E3), onSurfaceVariant = Color(0xFF3F4948),
    outline = Color(0xFF6F7978), outlineVariant = Color(0xFFBEC9C7),
    surfaceContainerLowest = Color(0xFFFFFFFF), surfaceContainerLow = Color(0xFFEBF2F1),
    surfaceContainer = Color(0xFFE5EDEC), surfaceContainerHigh = Color(0xFFDFE8E6), surfaceContainerHighest = Color(0xFFD9E2E1),
)
private val Dark = darkColorScheme(
    primary = Color(0xFF63D4CC), onPrimary = Color(0xFF003734),
    primaryContainer = Color(0xFF00504C), onPrimaryContainer = Color(0xFFC8EEEA),
    secondary = Color(0xFFFFBA3B), secondaryContainer = Color(0xFF633F00), onSecondaryContainer = Color(0xFFFFDEA8),
    tertiary = Color(0xFFA8C8FF), onTertiary = Color(0xFF00315F),
    tertiaryContainer = Color(0xFF124679), onTertiaryContainer = Color(0xFFD6E3FF),
    background = Color(0xFF0E1514), surface = Color(0xFF0E1514), onSurface = Color(0xFFDDE4E2),
    surfaceVariant = Color(0xFF3F4948), onSurfaceVariant = Color(0xFFBEC9C7),
    outline = Color(0xFF889391), outlineVariant = Color(0xFF3F4948),
    surfaceContainerLowest = Color(0xFF090F0F), surfaceContainerLow = Color(0xFF161D1C),
    surfaceContainer = Color(0xFF1A2120), surfaceContainerHigh = Color(0xFF242B2B), surfaceContainerHighest = Color(0xFF2F3636),
)

@Composable
fun AppTheme(content: @Composable () -> Unit) = MaterialTheme(
    colorScheme = if (isSystemInDarkTheme()) Dark else Light,
    shapes = Shapes(
        extraSmall = RoundedCornerShape(8.dp), small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(18.dp),
        large = RoundedCornerShape(26.dp), extraLarge = RoundedCornerShape(32.dp),
    ),
    content = content,
)
