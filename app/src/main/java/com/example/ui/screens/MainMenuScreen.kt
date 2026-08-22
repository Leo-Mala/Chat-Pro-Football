package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.components.bounceClick
import com.example.ui.components.pulseGlow
import com.example.ui.theme.*
import com.example.ui.viewmodel.GameViewModel

/**
 * Wrapper compatível com a navegação atual. O menu não depende de estado de carreira, portanto o
 * conteúdo visual fica separado do ViewModel para permitir preview/screenshot test determinístico
 * e evitar uma coleta de GameSave que não era usada para renderização.
 */
@Composable
@Suppress("UNUSED_PARAMETER")
fun MainMenuScreen(
    viewModel: GameViewModel,
    onNewGame: () -> Unit,
    onOpenSaves: () -> Unit,
    onOpenEditor: () -> Unit
) {
    MainMenuContent(
        onNewGame = onNewGame,
        onOpenSaves = onOpenSaves,
        onOpenEditor = onOpenEditor
    )
}

@Composable
fun MainMenuContent(
    onNewGame: () -> Unit,
    onOpenSaves: () -> Unit,
    onOpenEditor: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(TurfDeepGreen, TurfPitchDark)
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .size(110.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(AccentLime.copy(alpha = 0.25f), Color.Transparent)
                    ),
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_launcher_foreground),
                contentDescription = "Escudo Pro Football",
                modifier = Modifier.size(96.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "PRO FOOTBALL",
            fontSize = 38.sp,
            fontWeight = FontWeight.Black,
            fontFamily = FontFamily.Monospace,
            color = Color.White,
            textAlign = TextAlign.Center,
            letterSpacing = 4.sp
        )

        Text(
            text = "PRO FOOTBALL MANAGER 2026",
            fontSize = 12.sp,
            color = AccentLime,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 150.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = CardSurfaceDark),
            border = BorderStroke(1.2.dp, Brush.verticalGradient(listOf(Color.White.copy(0.12f), Color.Transparent))),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Box(modifier = Modifier.fillMaxWidth().heightIn(min = 150.dp)) {
                Image(
                    painter = painterResource(id = R.drawable.stadium_banner_1783311196063),
                    contentDescription = null,
                    modifier = Modifier.matchParentSize(),
                    contentScale = ContentScale.Crop
                )
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                            )
                        )
                )

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "GERENCIE O SEU TIME RUMO À GLÓRIA",
                        color = AccentLime,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Monte seu elenco. Defina táticas. Conquiste o país!",
                        color = Color.White.copy(alpha = 0.85f),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onNewGame,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .pulseGlow()
                .bounceClick()
                .testTag("new_game_button"),
            colors = ButtonDefaults.buttonColors(containerColor = AccentLime, contentColor = TurfPitchDark),
            shape = RoundedCornerShape(16.dp),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp, pressedElevation = 1.dp)
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Text("NOVO JOGO (CARREIRA)", fontSize = 16.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
        }

        Spacer(modifier = Modifier.height(14.dp))

        Button(
            onClick = onOpenSaves,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .bounceClick()
                .testTag("open_saves_button"),
            colors = ButtonDefaults.buttonColors(
                containerColor = AccentGold,
                contentColor = TurfPitchDark
            ),
            shape = RoundedCornerShape(16.dp),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp, pressedElevation = 1.dp)
        ) {
            Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "ACESSAR SAVES / SLOTS",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp,
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        Button(
            onClick = onOpenEditor,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .bounceClick()
                .testTag("open_editor_button"),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF1E88E5),
                contentColor = Color.White
            ),
            shape = RoundedCornerShape(16.dp),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp, pressedElevation = 1.dp)
        ) {
            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "EDITOR TÉCNICO",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp,
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}
