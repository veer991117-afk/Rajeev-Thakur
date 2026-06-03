package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

import androidx.compose.ui.graphics.Color

private val PikachuDarkColorScheme = darkColorScheme(
    primary = PikachuYellow,
    onPrimary = Color.Black,
    secondary = PikachuOrange,
    onSecondary = Color.Black,
    tertiary = ElectricGlow,
    background = MatteBlack,
    surface = CharcoalDark,
    onBackground = Color.White,
    onSurface = Color.White
)

private val PikachuLightColorScheme = lightColorScheme(
    primary = PikachuYellow,
    onPrimary = Color.Black,
    secondary = PikachuOrange,
    onSecondary = Color.Black,
    tertiary = ElectricGlow,
    background = Color.White,
    surface = SoftGray,
    onBackground = Color(0xFF212121),
    onSurface = Color(0xFF212121)
)

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  dynamicColor: Boolean = false, // Disable dynamic colors to keep themed Pikachu aesthetic persistent
  content: @Composable () -> Unit,
) {
  val colorScheme = if (darkTheme) PikachuDarkColorScheme else PikachuLightColorScheme

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
