package com.example.ui.screens

import com.example.ui.viewmodel.*

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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.example.data.PlayerDataParser
import com.example.data.ParsedJsonResult
import com.example.R
import com.example.data.*
import com.example.ui.viewmodel.GameViewModel
import coil.compose.rememberAsyncImagePainter


@Composable
fun CoachTab(viewModel: GameViewModel) {
    val saveState by viewModel.gameSave.collectAsStateWithLifecycle()
    val offers by viewModel.coachOffers.collectAsStateWithLifecycle()
    val allPlayers by viewModel.allPlayers.collectAsStateWithLifecycle()
    val allFixtures by viewModel.allFixtures.collectAsStateWithLifecycle()
    val allTeams by viewModel.allTeams.collectAsStateWithLifecycle()

    var showEditorDialog by remember { mutableStateOf(false) }

    if (showEditorDialog) {
        Dialog(
            onDismissRequest = { showEditorDialog = false },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(modifier = Modifier.fillMaxSize()) {
                TeamAndPlayerEditorScreen(
                    viewModel = viewModel,
                    onBack = { showEditorDialog = false }
                )
            }
        }
    }

    val s = saveState
    if (s == null) return

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        var activeSubTab by remember { mutableStateOf("PERFIL") }
        val coachSubTabs = listOf(
            Pair("PERFIL", "Perfil & Carreira"),
            Pair("OFERTAS", "Propostas (${offers.size})"),
            Pair("SISTEMA", "Sistema & Editor")
        )

        // Sub-Tab Switcher
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(CardSurfaceDark, RoundedCornerShape(10.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            coachSubTabs.forEach { (code, title) ->
                val isActive = activeSubTab == code
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isActive) TurfDeepGreen else Color.Transparent)
                        .clickable { activeSubTab = code }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        title,
                        fontSize = 11.sp,
                        color = if (isActive) AccentLime else Color.Gray,
                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        if (activeSubTab == "PERFIL") {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CardSurfaceDark),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Perfil do Treinador", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(12.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Nome:", color = Color.Gray, fontSize = 13.sp)
                    Text(s.coachName, color = MaterialTheme.colorScheme.onBackground, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Reputação:", color = Color.Gray, fontSize = 13.sp)
                    Text("${s.coachReputation}/100", color = AccentGold, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Career Progress Dashboard Card
        val pTeamId = s.playerTeamId
        val playerFixtures = allFixtures.filter { it.isPlayed && (it.homeTeamId == pTeamId || it.awayTeamId == pTeamId) }
        
        var seasonWins = 0
        var seasonDraws = 0
        var seasonLosses = 0
        var seasonGS = 0
        var seasonGC = 0
        
        for (f in playerFixtures) {
            val isHome = f.homeTeamId == pTeamId
            val myScore = if (isHome) f.homeScore ?: 0 else f.awayScore ?: 0
            val oppScore = if (isHome) f.awayScore ?: 0 else f.homeScore ?: 0
            
            seasonGS += myScore
            seasonGC += oppScore
            
            when {
                myScore > oppScore -> seasonWins++
                myScore < oppScore -> seasonLosses++
                else -> seasonDraws++
            }
        }
        
        val wins = s.careerWins + seasonWins
        val draws = s.careerDraws + seasonDraws
        val losses = s.careerLosses + seasonLosses
        val totalMatches = s.careerMatches + playerFixtures.size
        val goalsScored = s.careerGoalsScored + seasonGS
        val goalsConceded = s.careerGoalsConceded + seasonGC
        val winRate = if (totalMatches > 0) (wins.toDouble() / totalMatches * 100).toInt() else 0

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CardSurfaceDark),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Desempenho da Carreira", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(12.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Total de Jogos:", color = Color.Gray, fontSize = 13.sp)
                    Text("$totalMatches", color = MaterialTheme.colorScheme.onBackground, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Vitórias:", color = Color.Gray, fontSize = 13.sp)
                    Text("$wins", color = Color(0xFF81C784), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Empates:", color = Color.Gray, fontSize = 13.sp)
                    Text("$draws", color = Color(0xFFFFD54F), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Derrotas:", color = Color.Gray, fontSize = 13.sp)
                    Text("$losses", color = Color(0xFFE57373), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Aproveitamento:", color = Color.Gray, fontSize = 13.sp)
                    Text("$winRate%", color = AccentGold, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Gols Marcados / Sofridos:", color = Color.Gray, fontSize = 13.sp)
                    Text("$goalsScored / $goalsConceded (SG: ${goalsScored - goalsConceded})", color = MaterialTheme.colorScheme.onBackground, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        } else if (activeSubTab == "OFERTAS") {
        // Propostas de Trabalho (Job Offers)
        Text("PROPOSTAS DE TRABALHO RECEBIDAS", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        if (offers.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CardSurfaceDark)
            ) {
                Box(modifier = Modifier.padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(
                        "Nenhuma proposta no momento. Vença mais partidas para atrair o interesse de outros clubes!",
                        color = Color.Gray,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            offers.forEach { offer ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = CardSurfaceDark),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(offer.teamName, color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text("R$ %,d/sem".format(offer.weeklySalary), color = AccentLime, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(offer.description, color = Color.Gray, fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = { viewModel.acceptCoachOffer(offer) },
                            colors = ButtonDefaults.buttonColors(containerColor = AccentLime, contentColor = TurfDeepGreen),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("ACEITAR PROPOSTA E ASSUMIR CLUBE", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        } else if (activeSubTab == "SISTEMA") {
        // Editor Técnico Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CardSurfaceDark),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null, tint = AccentLime)
                    Text("Editor Técnico", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "Edite nomes de times, escudos, cidades, salários, posições e atributos dos jogadores em tempo real.",
                    color = Color.Gray,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = { showEditorDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E88E5), contentColor = Color.White),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("ABRIR EDITOR TÉCNICO", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }

        // Top Scorers Section
        Text("ARTILHARIA DA TEMPORADA", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        val topScorers = remember(allPlayers) {
            allPlayers.filter { it.gols > 0 }.sortedByDescending { it.gols }.take(5)
        }

        if (topScorers.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CardSurfaceDark)
            ) {
                Box(modifier = Modifier.padding(16.dp), contentAlignment = Alignment.Center) {
                    Text("Nenhum gol marcado na temporada ainda.", color = Color.Gray, fontSize = 12.sp)
                }
            }
        } else {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CardSurfaceDark)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    topScorers.forEachIndexed { index, player ->
                        val team = allTeams.find { it.id == player.teamId }
                        val teamName = team?.name ?: "Sem Clube"
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "${index+1}. ${player.name} (${player.position}) - $teamName",
                                color = MaterialTheme.colorScheme.onBackground,
                                fontSize = 13.sp
                            )
                            Text(
                                "${player.gols} Gols",
                                color = AccentGold,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        }

        // Opções da Carreira Section
        Spacer(modifier = Modifier.height(8.dp))
        Text("GERENCIAR CARREIRA", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CardSurfaceDark),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                var showRestartConfirm by remember { mutableStateOf(false) }
                var showExitConfirm by remember { mutableStateOf(false) }

                Button(
                    onClick = { showRestartConfirm = true },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD81B60), contentColor = Color.White),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("RECOMEÇAR TEMPORADA ATUAL", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedButton(
                    onClick = { showExitConfirm = true },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    border = BorderStroke(1.5.dp, Color.White),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.ExitToApp, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("SALVAR E SAIR DA CARREIRA", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }

                if (showRestartConfirm) {
                    AlertDialog(
                        onDismissRequest = { showRestartConfirm = false },
                        title = { Text("Recomeçar temporada?", fontWeight = FontWeight.Bold) },
                        text = { Text("Isso reiniciará as semanas para a Semana 1, gerará novas tabelas de jogos e restaurará a energia de todos os atletas, mas mantendo seu clube, finanças e técnico. Deseja prosseguir?") },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    viewModel.restartCurrentSeason()
                                    showRestartConfirm = false
                                }
                            ) {
                                Text("RECOMEÇAR", color = Color(0xFFD81B60), fontWeight = FontWeight.Bold)
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showRestartConfirm = false }) {
                                Text("CANCELAR", color = Color.Gray)
                            }
                        }
                    )
                }

                if (showExitConfirm) {
                    AlertDialog(
                        onDismissRequest = { showExitConfirm = false },
                        title = { Text("Salvar e sair?", fontWeight = FontWeight.Bold) },
                        text = { Text("Seu progresso atual foi salvo no banco de dados. Deseja sair desta carreira e retornar ao menu principal?") },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    viewModel.exitToSavesMenu()
                                    showExitConfirm = false
                                }
                            ) {
                                Text("SIM, SAIR", color = AccentLime, fontWeight = FontWeight.Bold)
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showExitConfirm = false }) {
                                Text("CONTINUAR JOGANDO", color = Color.Gray)
                            }
                        }
                    )
                }
            }
        }

        // --- SISTEMA DE SALVAMENTO E INTEGRIDADE ---
        Spacer(modifier = Modifier.height(8.dp))
        Text("SISTEMA DE SALVAMENTO E INTEGRIDADE", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CardSurfaceDark),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Save Manual Button
                Button(
                    onClick = { viewModel.saveGame(manual = true) },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentLime, contentColor = TurfDeepGreen),
                    modifier = Modifier.fillMaxWidth().testTag("save_manual_button"),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("SALVAR JOGO MANUALMENTE", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(16.dp))

                // Autosave Toggle
                val autoSaveEnabled by viewModel.autoSaveEnabled.collectAsStateWithLifecycle()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Salvamento Automático",
                            color = MaterialTheme.colorScheme.onBackground,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Text(
                            text = "Salva após cada rodada e negociações concluídas.",
                            color = Color.Gray,
                            fontSize = 11.sp
                        )
                    }
                    Switch(
                        checked = autoSaveEnabled,
                        onCheckedChange = { viewModel.setAutoSaveEnabled(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = TurfDeepGreen,
                            checkedTrackColor = AccentLime,
                            uncheckedThumbColor = Color.Gray,
                            uncheckedTrackColor = Color.DarkGray
                        ),
                        modifier = Modifier.testTag("autosave_toggle_switch")
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(16.dp))

                // Infinite Stamina Toggle
                val infiniteStaminaEnabled by viewModel.infiniteStaminaEnabled.collectAsStateWithLifecycle()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Atletas não Cansam (Estamina Infinita)",
                            color = MaterialTheme.colorScheme.onBackground,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Text(
                            text = "Mantém a energia de todos os atletas do seu elenco em 100%.",
                            color = Color.Gray,
                            fontSize = 11.sp
                        )
                    }
                    Switch(
                        checked = infiniteStaminaEnabled,
                        onCheckedChange = { viewModel.setInfiniteStaminaEnabled(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = TurfDeepGreen,
                            checkedTrackColor = AccentLime,
                            uncheckedThumbColor = Color.Gray,
                            uncheckedTrackColor = Color.DarkGray
                        ),
                        modifier = Modifier.testTag("infinite_stamina_toggle_switch")
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(16.dp))

                // Escalação Automática Toggle
                val autoLineupEnabled by viewModel.autoLineupEnabled.collectAsStateWithLifecycle()
                val gameSaveState by viewModel.gameSave.collectAsStateWithLifecycle()
                val playerTeamId = gameSaveState?.playerTeamId ?: 0L

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Escalação Automática",
                            color = MaterialTheme.colorScheme.onBackground,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Text(
                            text = "Escala automaticamente os melhores e mais descansados atletas de acordo com a tática escolhida.",
                            color = Color.Gray,
                            fontSize = 11.sp
                        )
                    }
                    Switch(
                        checked = autoLineupEnabled,
                        onCheckedChange = { viewModel.setAutoLineupEnabled(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = TurfDeepGreen,
                            checkedTrackColor = AccentLime,
                            uncheckedThumbColor = Color.Gray,
                            uncheckedTrackColor = Color.DarkGray
                        ),
                        modifier = Modifier.testTag("autolineup_toggle_switch")
                    )
                }

                if (playerTeamId != 0L) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { viewModel.autoLineup(playerTeamId, showToast = true) },
                        colors = ButtonDefaults.buttonColors(containerColor = TurfDeepGreen, contentColor = Color.White),
                        modifier = Modifier.fillMaxWidth().testTag("trigger_autolineup_button"),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.SportsSoccer, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("ESCALAR AGORA AUTOMATICAMENTE", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(16.dp))

                // Simular Temporada Panel
                val isSimulatingSeason by viewModel.isSimulatingSeason.collectAsStateWithLifecycle()
                val simWeek by viewModel.simulationCurrentWeek.collectAsStateWithLifecycle()
                val simCompName by viewModel.simulationCompetitionName.collectAsStateWithLifecycle()
                val simMatchInfo by viewModel.simulationMatchInfo.collectAsStateWithLifecycle()
                val simLogs by viewModel.simulationLogs.collectAsStateWithLifecycle()

                Text(
                    text = "Simular Temporada Completa",
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Simula as rodadas do calendário de forma acelerada até o final da temporada.",
                    color = Color.Gray,
                    fontSize = 11.sp
                )
                Spacer(modifier = Modifier.height(12.dp))

                if (isSimulatingSeason) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, AccentLime.copy(alpha = 0.5f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Simulando Semana $simWeek de 45...",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = AccentLime
                                )
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = AccentLime,
                                    strokeWidth = 2.dp
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Campeonato: $simCompName",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Resultado: $simMatchInfo",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            if (simLogs.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "Histórico Recente:",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = Color.Gray
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Column {
                                    simLogs.take(5).forEach { log ->
                                        Text(
                                            text = log,
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { viewModel.stopSeasonSimulation() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red, contentColor = Color.White),
                        modifier = Modifier.fillMaxWidth().testTag("stop_season_simulation_button"),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("PARAR SIMULAÇÃO", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                } else {
                    Button(
                        onClick = { viewModel.startSeasonSimulation() },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentLime, contentColor = TurfDeepGreen),
                        modifier = Modifier.fillMaxWidth().testTag("start_season_simulation_button"),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.FastForward, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("SIMULAR TEMPORADA", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(16.dp))

                // Database Validation Panel
                val validationResult by viewModel.validationResult.collectAsStateWithLifecycle()
                Text(
                    text = "Validação de Integridade de Dados",
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Verifica se há times sem estádio, elencos incompletos ou atletas órfãos.",
                    color = Color.Gray,
                    fontSize = 11.sp
                )
                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = { viewModel.validateDatabase() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8E24AA), contentColor = Color.White),
                    modifier = Modifier.fillMaxWidth().testTag("validate_database_button"),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Verified, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("VERIFICAR INTEGRIDADE", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }

                validationResult?.let { result ->
                    Spacer(modifier = Modifier.height(16.dp))
                    Surface(
                        color = if (result.isValid()) Color(0xFF1B5E20).copy(alpha = 0.2f) else Color(0xFFB71C1C).copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, if (result.isValid()) Color(0xFF4CAF50) else Color(0xFFF44336)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (result.isValid()) Icons.Default.CheckCircle else Icons.Default.Error,
                                    contentDescription = null,
                                    tint = if (result.isValid()) Color(0xFF4CAF50) else Color(0xFFF44336),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (result.isValid()) "Banco de dados íntegro e verificado!" else "Inconsistências Encontradas!",
                                    color = if (result.isValid()) Color(0xFF81C784) else Color(0xFFE57373),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                            
                            if (!result.isValid()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Erros que serão reparados automaticamente pelo corretor dinâmico:", color = Color.Gray, fontSize = 11.sp)
                                
                                if (result.teamsWithoutCompleteRoster.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text("• Times sem elenco completo (mínimo 11 atletas):", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                    Text(result.teamsWithoutCompleteRoster.joinToString(", "), color = Color.LightGray, fontSize = 10.sp)
                                }
                                if (result.teamsWithoutStadium.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text("• Times sem estádio definido:", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                    Text(result.teamsWithoutStadium.joinToString(", "), color = Color.LightGray, fontSize = 10.sp)
                                }
                                if (result.teamsWithoutCityOrCountry.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text("• Times sem cidade ou país definido:", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                    Text(result.teamsWithoutCityOrCountry.joinToString(", "), color = Color.LightGray, fontSize = 10.sp)
                                }
                                if (result.playersWithoutForce.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text("• Atletas sem força individual definida:", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                    Text(result.playersWithoutForce.joinToString(", "), color = Color.LightGray, fontSize = 10.sp)
                                }
                                if (result.playersWithoutTeam.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text("• Atletas órfãos (sem clube):", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                    Text(result.playersWithoutTeam.joinToString(", "), color = Color.LightGray, fontSize = 10.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
        }
    }
}

