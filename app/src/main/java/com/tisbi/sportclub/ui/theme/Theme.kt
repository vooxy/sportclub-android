package com.tisbi.sportclub.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Палитра по ТЗ п. 4.2: тёмно-синий #000080, золотой #FFD700,
// бирюзовый #40E0D0, белый #FFFFFF, светло-серый #D3D3D3
val Navy = Color(0xFF000080)
val NavyLight = Color(0xFF3A3A9E)
val NavyContainer = Color(0xFFE4E4F5)
val Gold = Color(0xFFFFD700)
val GoldDark = Color(0xFFB89600)
val GoldContainer = Color(0xFFFFF3C4)
val Turquoise = Color(0xFF40E0D0)
val TurquoiseDark = Color(0xFF00A995)
val TurquoiseContainer = Color(0xFFD2F5F0)
val LightGray = Color(0xFFD3D3D3)
val DangerRed = Color(0xFFC62828)
val OkGreen = Color(0xFF2E7D32)

private val LightColors = lightColorScheme(
    primary = Navy,
    onPrimary = Color.White,
    primaryContainer = NavyContainer,
    onPrimaryContainer = Navy,
    secondary = GoldDark,
    onSecondary = Color.White,
    secondaryContainer = GoldContainer,
    onSecondaryContainer = Color(0xFF4A3B00),
    tertiary = TurquoiseDark,
    onTertiary = Color.White,
    tertiaryContainer = TurquoiseContainer,
    onTertiaryContainer = Color(0xFF003B33),
    background = Color.White,
    onBackground = Color(0xFF1A1A22),
    surface = Color.White,
    onSurface = Color(0xFF1A1A22),
    surfaceVariant = Color(0xFFF2F2F6),
    onSurfaceVariant = Color(0xFF55555F),
    error = DangerRed,
    outline = LightGray
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFBCC5FF),
    onPrimary = Color(0xFF001159),
    primaryContainer = Color(0xFF27348F),
    onPrimaryContainer = Color(0xFFE4E4F5),
    secondary = Color(0xFFFFE07A),
    onSecondary = Color(0xFF3F2F00),
    secondaryContainer = Color(0xFF5C4700),
    onSecondaryContainer = Color(0xFFFFF3C4),
    tertiary = Color(0xFF74F2E3),
    onTertiary = Color(0xFF003731),
    tertiaryContainer = Color(0xFF00504A),
    onTertiaryContainer = Color(0xFFD2F5F0),
    background = Color(0xFF121218),
    onBackground = Color(0xFFE4E1EC),
    surface = Color(0xFF1A1A22),
    onSurface = Color(0xFFE4E1EC),
    surfaceVariant = Color(0xFF2A2A33),
    onSurfaceVariant = Color(0xFFB9B9C3),
    error = Color(0xFFFFB4AB),
    outline = Color(0xFF55555F)
)

@Composable
fun SportClubTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content
    )
}
