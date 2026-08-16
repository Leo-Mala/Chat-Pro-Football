package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// EAFC Manager Neo Theme Palette
// Fundo principal: Azul petróleo escuro — #003B5C / #002236
val EafcDarkPetrolBg = Color(0xFF002236)
val EafcMainBg = Color(0xFF003B5C)

// Gradiente médio: Azul teal — #005F7F
val EafcMidGradientTeal = Color(0xFF005F7F)

// Realce inferior: Ciano teal — #007A8A
val EafcCyanTealGlow = Color(0xFF007A8A)

// Botões principais: Azul teal radiante e luminoso com alto brilho neon — #00C3E3 / #00F5FF
val EafcDeepTealButton = Color(0xFF00C3E3)
val EafcButtonGlowBorder = Color(0xFF00F5FF)

// Texto e ícones: Branco puro — #FFFFFF
val EafcTextWhite = Color(0xFFFFFFFF)
val EafcSubtextMuted = Color(0xFF90B0C0)

// Destaques secundários: Verde neon suave — #00FFB3
val EafcNeonGreen = Color(0xFF00FFB3)

// Alertas e energia: Amarelo esportivo — #FFD700
val EafcSportsYellow = Color(0xFFFFD700)

// Cards e superfícies
val EafcCardSurface = Color(0xFF002D45)
val EafcCardBorder = Color(0xFF006279)
val EafcAlertRed = Color(0xFFFF3D00)

// Aliases mapped to EAFC Manager Neo Theme
val NeonMidnightBg = EafcDarkPetrolBg
val NeonMidnightSurface = EafcCardSurface
val NeonMidnightCard = EafcCardSurface
val NeonCyanAccent = EafcDeepTealButton        // Botões principais (#006C80)
val NeonBlueAccent = EafcCyanTealGlow          // Realce ciano teal (#007A8A)
val NeonGoldAccent = EafcSportsYellow          // Amarelo esportivo (#FFD700)
val NeonGreenAccent = EafcNeonGreen            // Verde neon suave (#00FFB3)
val NeonRedAccent = EafcAlertRed
val NeonPurpleAccent = EafcCyanTealGlow

// Crisp High-Contrast Typography & Card Borders
val TextWhitePrimary = EafcTextWhite           // #FFFFFF
val TextMutedBlue = EafcSubtextMuted           // #90B0C0
val BorderNavyMuted = EafcCardBorder           // #006279

// Direct mappings for all screens
val TurfDeepGreen = EafcCardSurface            // #002D45
val TurfPitchDark = EafcDarkPetrolBg           // #002236
val AccentLime = EafcDeepTealButton            // #006C80 (Azul teal profundo)
val AccentGold = EafcSportsYellow              // #FFD700 (Amarelo esportivo)
val CardSurfaceDark = EafcCardSurface          // #002D45

// Soft status pill background and text colors
val PolishSoftPinkBg = Color(0x28007A8A)
val PolishSoftPinkText = EafcCyanTealGlow
val PolishSoftBlueBg = Color(0x28006C80)
val PolishSoftBlueBorder = EafcDeepTealButton
val PolishSoftBlueText = EafcNeonGreen
val PolishSoftRedBg = Color(0x28FF3D00)
val PolishSoftRedBorder = EafcAlertRed
val PolishSoftRedText = EafcAlertRed
val PolishPurpleContainer = Color(0x2800FFB3)
val PolishOnPurpleContainer = EafcNeonGreen


