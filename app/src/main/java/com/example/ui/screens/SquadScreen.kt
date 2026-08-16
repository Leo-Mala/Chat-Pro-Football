package com.example.ui.screens

import com.example.ui.viewmodel.*
import com.example.ui.components.squad.*
import com.example.ui.components.finances.*

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.example.ui.theme.*
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.example.data.PlayerDataParser
import com.example.data.ParsedJsonResult
import com.example.R
import com.example.data.*
import com.example.ui.viewmodel.GameViewModel
import com.example.ui.viewmodel.MatchViewModel
import com.example.ui.viewmodel.TransfersViewModel
import com.example.ui.viewmodel.SquadViewModel
import com.example.ui.viewmodel.SaveManagerViewModel
import coil.compose.rememberAsyncImagePainter


@Composable
fun SquadTab(viewModel: GameViewModel) {
    val roster by viewModel.playerRoster.collectAsStateWithLifecycle()
    val saveState by viewModel.gameSave.collectAsStateWithLifecycle()
    val currentSaveId by viewModel.currentSaveId.collectAsStateWithLifecycle()

    var activeSubTab by remember { mutableStateOf("PROFISSIONAL") }
    var selectedPlayerForDialog by remember { mutableStateOf<Player?>(null) }
    var showAcademyDialog by remember { mutableStateOf(false) }

    val s = saveState
    if (s == null) return

    Column(modifier = Modifier.fillMaxSize()) {
        // Toggle Buttons at the Top
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(CardSurfaceDark, RoundedCornerShape(8.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Button(
                onClick = { activeSubTab = "PROFISSIONAL" },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (activeSubTab == "PROFISSIONAL") TurfDeepGreen else Color.Transparent,
                    contentColor = if (activeSubTab == "PROFISSIONAL") AccentLime else Color.Gray
                ),
                contentPadding = PaddingValues(vertical = 8.dp),
                shape = RoundedCornerShape(6.dp)
            ) {
                Text("PROFISSIONAL (${roster.size})", fontWeight = FontWeight.Bold, fontSize = 11.sp)
            }

            Button(
                onClick = { activeSubTab = "BASE" },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (activeSubTab == "BASE") TurfDeepGreen else Color.Transparent,
                    contentColor = if (activeSubTab == "BASE") AccentLime else Color.Gray
                ),
                contentPadding = PaddingValues(vertical = 8.dp),
                shape = RoundedCornerShape(6.dp)
            ) {
                Text("BASE", fontWeight = FontWeight.Bold, fontSize = 11.sp)
            }

            Button(
                onClick = { activeSubTab = "CARDS_RETRO" },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (activeSubTab == "CARDS_RETRO") TurfDeepGreen else Color.Transparent,
                    contentColor = if (activeSubTab == "CARDS_RETRO") AccentLime else Color.Gray
                ),
                contentPadding = PaddingValues(vertical = 8.dp),
                shape = RoundedCornerShape(6.dp)
            ) {
                Text("CARDS RETRÔ", fontWeight = FontWeight.Bold, fontSize = 11.sp)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (activeSubTab == "CARDS_RETRO") {
            val playerViewModel: com.example.ui.viewmodel.PlayerViewModel = hiltViewModel()
            LaunchedEffect(currentSaveId) {
                val slotId = currentSaveId
                if (slotId != null) {
                    playerViewModel.setSlotId(slotId)
                } else {
                    playerViewModel.clearSlot()
                }
            }
            PlayerListScreen(
                viewModel = playerViewModel
            )
        } else if (activeSubTab == "PROFISSIONAL") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "ELENCO PRINCIPAL",
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )

                Button(
                    onClick = { showAcademyDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = TurfDeepGreen, contentColor = AccentLime),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("REVELAR DA BASE", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            val startersList = remember(roster) {
                roster.filter { it.isStarter }.sortedWith(compareBy({ when(it.position) { "GOL" -> 1; "LAT" -> 2; "ZAG" -> 3; "VOL" -> 4; "MEI" -> 5; else -> 6 } }, { -it.force }))
            }
            val reservesList = remember(roster) {
                roster.filter { !it.isStarter }.sortedWith(compareBy({ when(it.position) { "GOL" -> 1; "LAT" -> 2; "ZAG" -> 3; "VOL" -> 4; "MEI" -> 5; else -> 6 } }, { -it.force }))
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Text(
                        text = "TITULARES (${startersList.size}/11)",
                        color = AccentLime,
                        fontWeight = FontWeight.Black,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }

                items(startersList, key = { it.id }) { player ->
                    PlayerCard(player = player) { selectedPlayerForDialog = player }
                }

                if (reservesList.isNotEmpty()) {
                    item {
                        Text(
                            text = "RESERVAS (${reservesList.size})",
                            color = Color.LightGray,
                            fontWeight = FontWeight.Black,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                        )
                    }

                    items(reservesList, key = { it.id }) { player ->
                        PlayerCard(player = player) { selectedPlayerForDialog = player }
                    }
                }
            }
        } else {
            // CATEGORIA DE BASE VIEW
            val prospects = viewModel.parseProspects(s.academyProspects)

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header card with Academy Infrastructure
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = CardSurfaceDark),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column {
                                Text("Centro de Treinamento", color = AccentGold, fontWeight = FontWeight.Black, fontSize = 12.sp)
                                Text("NÍVEL ${s.academyLevel} DE INFRAESTRUTURA", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            }
                            Icon(Icons.Default.School, contentDescription = null, tint = AccentGold, modifier = Modifier.size(32.dp))
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = when (s.academyLevel) {
                                1 -> "Infraestrutura Básica. Capacidade para 2 jovens. Revelação com força modesta."
                                2 -> "Infraestrutura Moderna. Capacidade para 3 jovens. Força inicial e potencial aumentados."
                                else -> "Infraestrutura de Elite. Capacidade para 4 jovens. Alta probabilidade de revelar craques nacionais."
                            },
                            color = Color.Gray,
                            fontSize = 11.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        if (s.academyLevel < 3) {
                            val upgradeCost = if (s.academyLevel == 1) 500000L else 1200000L
                            Button(
                                onClick = { viewModel.upgradeAcademyLevel() },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = TurfDeepGreen, contentColor = AccentLime),
                                shape = RoundedCornerShape(6.dp),
                                enabled = s.bankBalance >= upgradeCost
                            ) {
                                Text("MELHORAR CT PARA NÍVEL ${s.academyLevel + 1} (R$ %,d)".format(upgradeCost), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(TurfDeepGreen.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                                    .padding(8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("INFRAESTRUTURA DE BASE MÁXIMA ALCANÇADA 🎉", color = AccentLime, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // Weekly Academy investment
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = CardSurfaceDark),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Aporte Semanal de Investimento", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text("Aumenta significativamente a evolução semanal dos atributos das promessas.", color = Color.Gray, fontSize = 11.sp)
                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val investments = listOf(10000L, 30000L, 70000L)
                            investments.forEach { inv ->
                                Button(
                                    onClick = { viewModel.adjustAcademyInvestment(inv) },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (s.academyWeeklyInvestment == inv) TurfDeepGreen else Color.Gray.copy(alpha = 0.1f),
                                        contentColor = if (s.academyWeeklyInvestment == inv) AccentLime else Color.White
                                    ),
                                    shape = RoundedCornerShape(6.dp),
                                    contentPadding = PaddingValues(vertical = 4.dp)
                                ) {
                                    Text("R$ %,d".format(inv), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                // Academy prospects list
                Text(
                    "JOVENS PROMESSAS EM FORMAÇÃO (${prospects.size})",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 8.dp)
                )

                if (prospects.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(CardSurfaceDark, RoundedCornerShape(12.dp))
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Nenhum jovem em treinamento. Aguardando observador scout trazer promessas...", color = Color.Gray, fontSize = 11.sp, textAlign = TextAlign.Center)
                    }
                } else {
                    prospects.forEach { prospect ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = CardSurfaceDark),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(AccentGold.copy(alpha = 0.2f), RoundedCornerShape(6.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(prospect.position, color = AccentGold, fontWeight = FontWeight.Black, fontSize = 11.sp)
                                }
                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(prospect.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text("Idade: ${prospect.age} anos • Potencial: ~${prospect.potential}", color = Color.Gray, fontSize = 11.sp)
                                    Text("Força Atual: ${prospect.force}", color = AccentLime, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Button(
                                        onClick = { viewModel.promoteAcademyProspect(prospect) },
                                        colors = ButtonDefaults.buttonColors(containerColor = TurfDeepGreen, contentColor = AccentLime),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        shape = RoundedCornerShape(4.dp),
                                        enabled = s.bankBalance >= 50000L && roster.size < 30
                                    ) {
                                        Text(if (roster.size >= 30) "ELENCO CHEIO" else "SUBIR (R$ 50k)", fontSize = 9.sp, fontWeight = FontWeight.Black)
                                    }

                                    Button(
                                        onClick = { viewModel.dismissAcademyProspect(prospect) },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red.copy(alpha = 0.15f), contentColor = Color.Red),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text("DISPENSAR", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        selectedPlayerForDialog?.let { p ->
            val updatedPlayer = roster.find { it.id == p.id } ?: p
            PlayerActionDialog(
                player = updatedPlayer,
                onDismiss = { selectedPlayerForDialog = null },
                onRenew = {
                    viewModel.renewContract(updatedPlayer, 52)
                    selectedPlayerForDialog = null
                },
                onSell = { paymentType ->
                    viewModel.sellPlayer(updatedPlayer, updatedPlayer.calculateMarketValue(), paymentType)
                    selectedPlayerForDialog = null
                },
                onToggleStarter = { isStarter ->
                    viewModel.setPlayerStarter(updatedPlayer.id, isStarter)
                    selectedPlayerForDialog = null
                }
            )
        }

        if (showAcademyDialog) {
            YouthAcademyDialog(
                rosterSize = roster.size,
                onDismiss = { showAcademyDialog = false },
                onInvest = {
                    viewModel.promoteYouthAcademy()
                    showAcademyDialog = false
                }
            )
        }
    }
}

