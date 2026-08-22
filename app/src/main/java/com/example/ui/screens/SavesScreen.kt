package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.SaveSlotMetadata
import com.example.ui.theme.*
import com.example.ui.viewmodel.GameViewModel
import com.example.ui.viewmodel.selectSaveSlotSafely

@Composable
fun SavesScreen(viewModel: GameViewModel, onBack: () -> Unit) {
    val saveSlots by viewModel.saveSlots.collectAsStateWithLifecycle()
    SavesContent(
        saveSlots = saveSlots,
        onSelectSlot = viewModel::selectSaveSlotSafely,
        onDeleteSlot = viewModel::deleteSaveSlot,
        onBack = onBack
    )
}

/**
 * Renderização pura da seleção de carreiras. Mantém persistência/navegação no chamador e permite
 * regressão visual determinística dos estados vazio e preenchido sem instanciar Hilt/ViewModel.
 *
 * O shell inteiro é rolável. A lista possui no máximo os slots de carreira suportados pelo jogo,
 * portanto um Column simples evita nested scrolling e mantém cabeçalho + botão Voltar alcançáveis
 * em dispositivos baixos e com font scale de acessibilidade elevado.
 */
@Composable
fun SavesContent(
    saveSlots: List<SaveSlotMetadata>,
    onSelectSlot: (String) -> Unit,
    onDeleteSlot: (String) -> Unit,
    onBack: () -> Unit
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
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        Box(
            modifier = Modifier
                .size(80.dp)
                .background(AccentLime.copy(alpha = 0.05f), CircleShape)
                .border(2.dp, Brush.radialGradient(listOf(AccentLime, Color.Transparent)), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.SportsSoccer,
                contentDescription = "Bola de futebol",
                tint = AccentLime,
                modifier = Modifier.size(44.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "PRO FOOTBALL",
            fontSize = 36.sp,
            fontWeight = FontWeight.Black,
            fontFamily = FontFamily.Monospace,
            color = Color.White,
            textAlign = TextAlign.Center,
            letterSpacing = 4.sp,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Text(
            text = "PRO FOOTBALL MANAGER 2026",
            fontSize = 12.sp,
            color = AccentLime,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(36.dp))

        Text(
            text = "Selecione seu Perfil de Carreira",
            fontSize = 15.sp,
            color = Color.White.copy(alpha = 0.7f),
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .align(Alignment.Start)
                .padding(bottom = 12.dp)
        )

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            saveSlots.forEach { slot ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("save_slot_${slot.id}")
                        .clickable(enabled = !slot.recoveryRequired) { onSelectSlot(slot.id) },
                    colors = CardDefaults.cardColors(
                        containerColor = when {
                            slot.recoveryRequired -> CardSurfaceDark.copy(alpha = 0.7f)
                            slot.exists -> CardSurfaceDark.copy(alpha = 0.85f)
                            else -> CardSurfaceDark.copy(alpha = 0.4f)
                        }
                    ),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(
                        width = 1.2.dp,
                        brush = Brush.horizontalGradient(
                            colors = when {
                                slot.recoveryRequired -> listOf(NeonRedAccent.copy(alpha = 0.55f), Color.Transparent)
                                slot.exists -> listOf(AccentGold.copy(alpha = 0.4f), Color.Transparent)
                                else -> listOf(Color.White.copy(alpha = 0.12f), Color.Transparent)
                            }
                        )
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .background(
                                    when {
                                        slot.recoveryRequired -> NeonRedAccent.copy(alpha = 0.12f)
                                        slot.exists -> AccentGold.copy(alpha = 0.12f)
                                        else -> Color.White.copy(alpha = 0.05f)
                                    },
                                    RoundedCornerShape(14.dp)
                                )
                                .border(
                                    1.dp,
                                    when {
                                        slot.recoveryRequired -> NeonRedAccent.copy(alpha = 0.35f)
                                        slot.exists -> AccentGold.copy(alpha = 0.3f)
                                        else -> Color.White.copy(alpha = 0.1f)
                                    },
                                    RoundedCornerShape(14.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = when {
                                    slot.recoveryRequired -> Icons.Default.Warning
                                    slot.exists -> Icons.Default.AccountBox
                                    else -> Icons.Default.Add
                                },
                                contentDescription = when {
                                    slot.recoveryRequired -> "Carreira preservada aguardando recuperação"
                                    slot.exists -> "Carreira existente"
                                    else -> "Novo perfil"
                                },
                                tint = when {
                                    slot.recoveryRequired -> NeonRedAccent
                                    slot.exists -> AccentGold
                                    else -> Color.Gray.copy(alpha = 0.7f)
                                },
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "CARREIRA SLOT ${slot.id}".uppercase(),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = when {
                                    slot.recoveryRequired -> NeonRedAccent
                                    slot.exists -> AccentGold
                                    else -> Color.Gray
                                },
                                letterSpacing = 1.5.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            when {
                                slot.recoveryRequired -> {
                                    Text(
                                        text = "RECUPERAÇÃO NECESSÁRIA",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color.White
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = slot.recoveryMessage
                                            ?: "Os dados foram preservados. Novo jogo bloqueado neste slot.",
                                        fontSize = 12.sp,
                                        color = Color.White.copy(alpha = 0.7f),
                                        fontWeight = FontWeight.Medium
                                    )
                                    if (slot.coachName.isNotBlank() || slot.teamName.isNotBlank()) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "${slot.coachName} • ${slot.teamName}",
                                            fontSize = 11.sp,
                                            color = Color.White.copy(alpha = 0.5f)
                                        )
                                    }
                                }
                                slot.exists -> {
                                    Text(
                                        text = slot.coachName,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color.White
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "${slot.teamName} • Temp. ${slot.season} (Sem. ${slot.week})",
                                        fontSize = 12.sp,
                                        color = Color.White.copy(alpha = 0.6f),
                                        fontWeight = FontWeight.Medium
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "Saldo: R$ %,d".format(slot.balance),
                                        fontSize = 12.sp,
                                        color = AccentLime,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                else -> {
                                    Text(
                                        text = "Novo Perfil",
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White.copy(alpha = 0.4f)
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "Toque para iniciar do zero",
                                        fontSize = 12.sp,
                                        color = Color.White.copy(alpha = 0.3f)
                                    )
                                }
                            }
                        }

                        if (slot.exists) {
                            var showDeleteConfirm by remember(slot.id) { mutableStateOf(false) }

                            IconButton(
                                onClick = { showDeleteConfirm = true },
                                modifier = Modifier
                                    .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                                    .testTag("delete_slot_${slot.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Deletar carreira do slot ${slot.id}",
                                    tint = NeonRedAccent.copy(alpha = 0.8f)
                                )
                            }

                            if (showDeleteConfirm) {
                                AlertDialog(
                                    onDismissRequest = { showDeleteConfirm = false },
                                    title = {
                                        Text(
                                            "Apagar Slot de Carreira ${slot.id}?",
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    },
                                    text = {
                                        Text(
                                            "Isso apagará permanentemente todos os seus dados e conquistas. Deseja prosseguir?",
                                            color = Color.White.copy(alpha = 0.8f)
                                        )
                                    },
                                    containerColor = NeonMidnightSurface,
                                    confirmButton = {
                                        TextButton(
                                            onClick = {
                                                onDeleteSlot(slot.id)
                                                showDeleteConfirm = false
                                            }
                                        ) {
                                            Text("EXCLUIR", color = NeonRedAccent, fontWeight = FontWeight.Bold)
                                        }
                                    },
                                    dismissButton = {
                                        TextButton(onClick = { showDeleteConfirm = false }) {
                                            Text("CANCELAR", color = Color.Gray)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedButton(
            onClick = onBack,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .testTag("back_to_menu_button"),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
            border = BorderStroke(1.2.dp, Color.White.copy(alpha = 0.4f)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = Color.White.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                "VOLTAR AO MENU",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                letterSpacing = 0.5.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}
