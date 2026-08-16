package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.example.ui.theme.*
import com.example.ui.viewmodel.GameViewModel

@Composable
fun SavesScreen(viewModel: GameViewModel, onBack: () -> Unit) {
    val saveSlots by viewModel.saveSlots.collectAsStateWithLifecycle()

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
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        // Futuristic Glowing Icon Logo Container
        Box(
            modifier = Modifier
                .size(80.dp)
                .background(AccentLime.copy(alpha = 0.05f), CircleShape)
                .border(2.dp, Brush.radialGradient(listOf(AccentLime, Color.Transparent)), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.SportsSoccer,
                contentDescription = "Soccer Ball",
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
            letterSpacing = 2.sp
        )

        Spacer(modifier = Modifier.height(36.dp))

        // Bento grid header description
        Text(
            text = "Selecione seu Perfil de Carreira",
            fontSize = 15.sp,
            color = Color.White.copy(alpha = 0.7f),
            fontWeight = FontWeight.Medium,
            modifier = Modifier.align(Alignment.Start).padding(bottom = 12.dp)
        )

        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(saveSlots) { slot ->
                // Custom glassy card with neon outline
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.selectSaveSlot(slot.id) },
                    colors = CardDefaults.cardColors(
                        containerColor = if (slot.exists) CardSurfaceDark.copy(alpha = 0.85f) else CardSurfaceDark.copy(alpha = 0.4f)
                    ),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(
                        width = 1.2.dp,
                        brush = Brush.horizontalGradient(
                            colors = if (slot.exists) listOf(AccentGold.copy(alpha = 0.4f), Color.Transparent)
                            else listOf(Color.White.copy(alpha = 0.12f), Color.Transparent)
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
                        // Left status indicator
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .background(
                                    if (slot.exists) AccentGold.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.05f),
                                    RoundedCornerShape(14.dp)
                                )
                                .border(
                                    1.dp,
                                    if (slot.exists) AccentGold.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.1f),
                                    RoundedCornerShape(14.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (slot.exists) Icons.Default.AccountBox else Icons.Default.Add,
                                contentDescription = null,
                                tint = if (slot.exists) AccentGold else Color.Gray.copy(alpha = 0.7f),
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "CARREIRA SLOT ${slot.id}".uppercase(),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (slot.exists) AccentGold else Color.Gray,
                                letterSpacing = 1.5.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            if (slot.exists) {
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
                            } else {
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

                        if (slot.exists) {
                            var showDeleteConfirm by remember { mutableStateOf(false) }

                            IconButton(
                                onClick = { showDeleteConfirm = true },
                                modifier = Modifier.testTag("delete_slot_${slot.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Deletar Save",
                                    tint = NeonRedAccent.copy(alpha = 0.8f)
                                )
                            }

                            if (showDeleteConfirm) {
                                AlertDialog(
                                    onDismissRequest = { showDeleteConfirm = false },
                                    title = { Text("Apagar Slot de Carreira ${slot.id}?", fontWeight = FontWeight.Bold, color = Color.White) },
                                    text = { Text("Isso apagará permanentemente todos os seus dados e conquistas. Deseja prosseguir?", color = Color.White.copy(alpha = 0.8f)) },
                                    containerColor = NeonMidnightSurface,
                                    confirmButton = {
                                        TextButton(
                                            onClick = {
                                                viewModel.deleteSaveSlot(slot.id)
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
                .height(56.dp)
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
            Text("VOLTAR AO MENU", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White, letterSpacing = 0.5.sp)
        }
    }
}
