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

private val SportsNeonColorScheme =
  darkColorScheme(
    primary = EafcDeepTealButton,     // Botões principais radiantes (#00C3E3)
    secondary = EafcButtonGlowBorder,  // Brilho Ciano Teal LED (#00F5FF)
    tertiary = EafcSportsYellow,      // Amarelo Esportivo (#FFD700)
    background = EafcDarkPetrolBg,    // Azul Petróleo Escuro (#002236 / #003B5C)
    surface = EafcCardSurface,        // Card/Container Petrol (#002D45)
    onPrimary = EafcTextWhite,         // Texto branco puro em alto contraste (#FFFFFF)
    onSecondary = EafcTextWhite,
    onBackground = EafcTextWhite,
    onSurface = EafcTextWhite,
    surfaceVariant = EafcCardSurface, // Card surface
    onSurfaceVariant = EafcSubtextMuted,
    outline = EafcCardBorder          // Bordas com leve brilho LED (#006279)
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = true, // Default to true for premium dark sports manager aesthetic
  dynamicColor: Boolean = false, // Disable dynamic colors to preserve brand aesthetic
  content: @Composable () -> Unit,
) {
  val colorScheme = SportsNeonColorScheme

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
