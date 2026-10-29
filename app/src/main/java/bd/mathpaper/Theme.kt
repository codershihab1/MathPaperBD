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

// Brand colours come from the logo: teal paper + amber vertices.
private val Light = lightColorScheme(
    primary = Color(0xFF0B6E69), onPrimary = Color.White,
    primaryContainer = Color(0xFFC8EEEA), onPrimaryContainer = Color(0xFF00201E),
    secondary = Color(0xFF8A5A00), secondaryContainer = Color(0xFFFFDEA8), onSecondaryContainer = Color(0xFF2B1800),
    background = Color(0xFFF5FAF9), surface = Color(0xFFF5FAF9), onSurface = Color(0xFF171D1C),
    surfaceVariant = Color(0xFFDAE5E3), onSurfaceVariant = Color(0xFF3F4948),
)
private val Dark = darkColorScheme(
    primary = Color(0xFF63D4CC), onPrimary = Color(0xFF003734),
    primaryContainer = Color(0xFF00504C), onPrimaryContainer = Color(0xFFC8EEEA),
    secondary = Color(0xFFFFBA3B), secondaryContainer = Color(0xFF633F00), onSecondaryContainer = Color(0xFFFFDEA8),
    background = Color(0xFF0E1514), surface = Color(0xFF0E1514), onSurface = Color(0xFFDDE4E2),
    surfaceVariant = Color(0xFF3F4948), onSurfaceVariant = Color(0xFFBEC9C7),
)

@Composable
fun AppTheme(content: @Composable () -> Unit) = MaterialTheme(
    colorScheme = if (isSystemInDarkTheme()) Dark else Light,
    shapes = Shapes(small = RoundedCornerShape(10.dp), medium = RoundedCornerShape(16.dp), large = RoundedCornerShape(24.dp), extraLarge = RoundedCornerShape(28.dp)),
    content = content,
)
