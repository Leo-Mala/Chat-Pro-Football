package com.example.ui.screens

import com.example.ui.components.tactics.TacticalControlDialog
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
fun LiveMatchScreen(viewModel: GameViewModel) {
    val minute by viewModel.matchMinute.collectAsStateWithLifecycle()
    val homeScore by viewModel.matchHomeScore.collectAsStateWithLifecycle()
    val awayScore by viewModel.matchAwayScore.collectAsStateWithLifecycle()
    val events by viewModel.matchEvents.collectAsStateWithLifecycle()
    val matchState by viewModel.matchState.collectAsStateWithLifecycle()
    val saveState by viewModel.gameSave.collectAsStateWithLifecycle()
    val selectedCountry by viewModel.selectedCountry.collectAsStateWithLifecycle()

    val liveHomeFormation by viewModel.liveHomeFormation.collectAsStateWithLifecycle()
    val liveHomeStyle by viewModel.liveHomeStyle.collectAsStateWithLifecycle()
    val liveMatchSpeed by viewModel.liveMatchSpeed.collectAsStateWithLifecycle()
    val liveTacticalFeedback by viewModel.liveTacticalFeedback.collectAsStateWithLifecycle()

    var showTacticalDialog by remember { mutableStateOf(false) }

    val hTeam = viewModel.liveMatchHomeTeam
    val aTeam = viewModel.liveMatchAwayTeam

    Box(modifier = Modifier.fillMaxSize().background(TurfPitchDark)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
        // Ticking Scoreboard
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = TurfDeepGreen),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "PARTIDA EM ANDAMENTO",
                    color = AccentGold,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Home Team
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f)
                    ) {
                        TeamBadge(
                            teamName = hTeam?.name ?: "Home",
                            logoUrl = hTeam?.logoUrl,
                            size = 48.dp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = hTeam?.name ?: "Home",
                            color = MaterialTheme.colorScheme.onBackground,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Score with Minute
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "$homeScore",
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onBackground,
                            fontFamily = FontFamily.Monospace
                        )
                        Box(
                            modifier = Modifier
                                .background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "$minute'",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = AccentLime,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Text(
                            text = "$awayScore",
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onBackground,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    // Away Team
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f)
                    ) {
                        TeamBadge(
                            teamName = aTeam?.name ?: "Away",
                            logoUrl = aTeam?.logoUrl,
                            size = 48.dp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = aTeam?.name ?: "Away",
                            color = MaterialTheme.colorScheme.onBackground,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // Info notice for substitutions
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(8.dp))
                .padding(vertical = 8.dp, horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = AccentGold,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "SUBSTITUIÇÕES AUTOMÁTICAS: O auxiliar técnico faz as trocas com base no cansaço e desempenho.",
                color = Color.LightGray,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            )
        }

        // Active Tactics Summary Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White.copy(alpha = 0.03f), RoundedCornerShape(8.dp))
                .border(0.5.dp, AccentLime.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                .padding(vertical = 10.dp, horizontal = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("ESTRATÉGIA ATIVA", color = AccentLime, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Spacer(modifier = Modifier.height(2.dp))
                Text("$liveHomeFormation • ${liveHomeStyle.uppercase()}", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Black)
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf("Tempo Real", "2x", "4x", "8x").forEach { speedItem ->
                    val isSelected = (liveMatchSpeed == speedItem)
                    val label = if (speedItem == "Tempo Real") "TR" else speedItem
                    Box(
                        modifier = Modifier
                            .size(width = 32.dp, height = 28.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                if (isSelected) AccentGold else Color.White.copy(alpha = 0.05f)
                            )
                            .border(
                                width = 1.dp,
                                color = if (isSelected) AccentGold else Color.White.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(6.dp)
                            )
                            .clickable {
                                viewModel.changeLiveMatchSpeed(speedItem)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            color = if (isSelected) TurfDeepGreen else Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Live Tactical Feedback Banner
        AnimatedVisibility(
            visible = liveTacticalFeedback != null,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            liveTacticalFeedback?.let { feedback ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(AccentLime, RoundedCornerShape(8.dp))
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = feedback,
                        color = TurfDeepGreen,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }
        }

        var selectedLiveTab by remember { mutableStateOf("LANCES") }
        LaunchedEffect(matchState) {
            if (matchState == GameViewModel.MatchState.FINISHED) {
                selectedLiveTab = "RELATORIO"
            } else {
                selectedLiveTab = "LANCES"
            }
        }

        // Live Ticker / Report Tabs
        if (matchState == GameViewModel.MatchState.FINISHED) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = { selectedLiveTab = "LANCES" },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (selectedLiveTab == "LANCES") AccentLime else Color.White.copy(alpha = 0.1f),
                        contentColor = if (selectedLiveTab == "LANCES") TurfDeepGreen else Color.White
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("LANCES", fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = { selectedLiveTab = "RELATORIO" },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (selectedLiveTab == "RELATORIO") AccentLime else Color.White.copy(alpha = 0.1f),
                        contentColor = if (selectedLiveTab == "RELATORIO") TurfDeepGreen else Color.White
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("RELATÓRIO PÓS-JOGO", fontWeight = FontWeight.Bold)
                }
            }
        } else {
            Text(
                text = "HISTÓRICO DE LANCES",
                color = Color.Gray,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            colors = CardDefaults.cardColors(containerColor = CardSurfaceDark),
            shape = RoundedCornerShape(12.dp)
        ) {
            if (selectedLiveTab == "LANCES") {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    reverseLayout = true // keeps latest events at top
                ) {
                    items(events.reversed(), key = { "${it.minute}_${it.description.hashCode()}" }) { event ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${event.minute}'",
                                color = AccentLime,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                modifier = Modifier.width(32.dp),
                                fontFamily = FontFamily.Monospace
                            )

                            Icon(
                                imageVector = when (event.type) {
                                    "GOAL" -> Icons.Default.SportsSoccer
                                    "CARD_YELLOW" -> Icons.Default.Warning
                                    "CARD_RED" -> Icons.Default.Cancel
                                    "SUBSTITUTION" -> Icons.Default.Autorenew
                                    "INJURY" -> Icons.Default.Healing
                                    else -> Icons.Default.Announcement
                                },
                                contentDescription = null,
                                tint = when (event.type) {
                                    "GOAL" -> AccentGold
                                    "CARD_YELLOW" -> Color.Yellow
                                    "CARD_RED" -> Color.Red
                                    "SUBSTITUTION" -> AccentLime
                                    "INJURY" -> Color(0xFFFF5252)
                                    else -> Color.LightGray
                                },
                                modifier = Modifier.size(16.dp)
                            )

                            Spacer(modifier = Modifier.width(10.dp))

                            Text(
                                text = event.description,
                                color = MaterialTheme.colorScheme.onBackground,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            } else {
                val save = saveState
                val hTeamLocal = hTeam
                val aTeamLocal = aTeam
                val isHome = hTeamLocal?.isPlayerControlled == true
                val coachRep = save?.coachReputation ?: 30
                val ticketPrice = if (isHome) (save?.ticketPrice ?: 25.0) else 25.0
                val baseAttendanceRate = 0.4 + (coachRep / 200.0)
                val priceFactor = (1.5 - ((ticketPrice - 30.0) * 0.012)).coerceIn(0.35, 1.5)
                val stadiumCapacity = if (isHome) (save?.stadiumCapacity ?: 10000) else 15000
                val maxCap2 = maxOf(1000, stadiumCapacity)
                val minCap2 = minOf(1000, maxCap2)
                val attendanceValue = (stadiumCapacity * baseAttendanceRate * priceFactor).toInt().coerceIn(minCap2, maxCap2)
                val revenueValue = (attendanceValue * ticketPrice * 2.0).toLong()

                val goals = events.filter { it.type == "GOAL" }
                val cards = events.filter { it.type == "CARD_YELLOW" || it.type == "CARD_RED" }
                val substitutions = events.filter { it.type == "SUBSTITUTION" }
                val injuries = events.filter { it.type == "INJURY" }

                val compName = if (viewModel.liveMatchFixture != null) DefaultData.getCompetitionName(viewModel.liveMatchFixture!!.competitionType, selectedCountry) else "Campeonato"
                val roundLabel = if (viewModel.liveMatchFixture != null) "Rodada ${viewModel.liveMatchFixture!!.week}" else ""
                val dateLabel = if (saveState != null) "Ano ${saveState!!.currentSeason}" else "2026"

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // 1. Info Header Row (Competition, Round, Date)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Default.EmojiEvents, contentDescription = null, tint = AccentGold, modifier = Modifier.size(16.dp))
                            Text(compName.uppercase(), color = AccentGold, fontSize = 11.sp, fontWeight = FontWeight.Black)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Default.CalendarToday, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(16.dp))
                            Text("$roundLabel • $dateLabel", color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

                    // 2. Scoreboard / Scorers Block
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("AUTORES DOS GOLS", color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        if (goals.isEmpty()) {
                            Text("Nenhum gol na partida.", color = Color.LightGray, fontSize = 12.sp, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                goals.forEach { g ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.SportsSoccer, contentDescription = null, tint = AccentGold, modifier = Modifier.size(14.dp))
                                        Text(
                                            text = "${g.minute}' - ${g.description}",
                                            color = Color.White,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

                    // 3. Público e Renda Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.04f)),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.1f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceAround
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Icon(Icons.Default.People, contentDescription = null, tint = AccentLime, modifier = Modifier.size(16.dp))
                                    Text("PÚBLICO", color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("%,d torcedores".format(attendanceValue), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Black)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Icon(Icons.Default.AttachMoney, contentDescription = null, tint = AccentGold, modifier = Modifier.size(16.dp))
                                    Text("RENDA BRUTA", color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("R$ %,d".format(revenueValue), color = AccentLime, fontSize = 14.sp, fontWeight = FontWeight.Black)
                            }
                        }
                    }

                    HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

                    // 4. Statistics Block
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("ESTATÍSTICAS COMPARATIVAS", color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        
                        val homeShots = 5 + homeScore * 3 + kotlin.random.Random(events.size).nextInt(1, 6)
                        val awayShots = 4 + awayScore * 3 + kotlin.random.Random(events.size + 1).nextInt(1, 6)
                        val homePossession = 40 + (homeScore - awayScore) * 3 + kotlin.random.Random(events.size + 2).nextInt(1, 10)
                        val homePossessionCoerced = homePossession.coerceIn(35, 65)
                        val awayPossessionCoerced = 100 - homePossessionCoerced

                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            StatBar(label = "Posse de Bola", hVal = "$homePossessionCoerced%", aVal = "$awayPossessionCoerced%", hProgress = homePossessionCoerced / 100f)
                            val totalShots = (homeShots + awayShots).coerceAtLeast(1)
                            StatBar(label = "Finalizações", hVal = "$homeShots", aVal = "$awayShots", hProgress = homeShots.toFloat() / totalShots)
                            val hYellows = cards.count { it.type == "CARD_YELLOW" && it.isHomeEvent }
                            val aYellows = cards.count { it.type == "CARD_YELLOW" && !it.isHomeEvent }
                            val totalYellows = (hYellows + aYellows).coerceAtLeast(1)
                            StatBar(label = "Cartões Amarelos", hVal = "$hYellows", aVal = "$aYellows", hProgress = hYellows.toFloat() / totalYellows)
                            val hReds = cards.count { it.type == "CARD_RED" && it.isHomeEvent }
                            val aReds = cards.count { it.type == "CARD_RED" && !it.isHomeEvent }
                            val totalReds = (hReds + aReds).coerceAtLeast(1)
                            StatBar(label = "Cartões Vermelhos", hVal = "$hReds", aVal = "$aReds", hProgress = hReds.toFloat() / totalReds)
                        }
                    }

                    HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

                    // 5. Substituições, Lesões, Cartões
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("OCORRÊNCIAS", color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold)

                        if (substitutions.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("Substituições:", color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                substitutions.forEach { sub ->
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Icon(Icons.Default.Autorenew, contentDescription = null, tint = AccentLime, modifier = Modifier.size(12.dp))
                                        Text("${sub.minute}' - ${sub.description}", color = Color.LightGray, fontSize = 12.sp)
                                    }
                                }
                            }
                        }

                        if (cards.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("Cartões Aplicados:", color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                cards.forEach { card ->
                                    val isYellow = card.type == "CARD_YELLOW"
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Icon(
                                            imageVector = if (isYellow) Icons.Default.Warning else Icons.Default.Cancel,
                                            contentDescription = null,
                                            tint = if (isYellow) Color.Yellow else Color.Red,
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Text("${card.minute}' - ${card.description}", color = Color.LightGray, fontSize = 12.sp)
                                    }
                                }
                            }
                        }

                        if (injuries.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("Lesões na Partida:", color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                injuries.forEach { inj ->
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Icon(Icons.Default.Healing, contentDescription = null, tint = Color.Red, modifier = Modifier.size(12.dp))
                                        Text("${inj.minute}' - ${inj.description}", color = Color.LightGray, fontSize = 12.sp)
                                    }
                                }
                            }
                        }

                        if (substitutions.isEmpty() && cards.isEmpty() && injuries.isEmpty()) {
                            Text("Nenhuma outra ocorrência digna de nota.", color = Color.LightGray, fontSize = 12.sp, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                        }
                    }
                }
            }
        }

        // Intercept back button/gesture to prevent app closing/crashing
        androidx.activity.compose.BackHandler {
            if (matchState == GameViewModel.MatchState.FINISHED) {
                viewModel.exitLiveMatch()
            } else {
                viewModel.pauseLiveMatch()
            }
        }

        // Control Panel
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (matchState == GameViewModel.MatchState.FINISHED) {
                Button(
                    onClick = { viewModel.exitLiveMatch() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentGold, contentColor = TurfDeepGreen)
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("VOLTAR À CENTRAL", fontWeight = FontWeight.Black)
                }
            } else {
                if (matchState == GameViewModel.MatchState.PLAYING) {
                    Button(
                        onClick = { viewModel.pauseLiveMatch() },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = TurfDeepGreen, contentColor = Color.White)
                    ) {
                        Icon(Icons.Default.Pause, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("PAUSAR", fontWeight = FontWeight.Bold)
                    }
                } else if (matchState == GameViewModel.MatchState.PAUSED) {
                    Button(
                        onClick = { viewModel.resumeLiveMatch() },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = TurfDeepGreen, contentColor = Color.White)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("RETOMAR", fontWeight = FontWeight.Bold)
                    }
                }

                // Dedicated live tactical panel button
                Button(
                    onClick = { showTacticalDialog = true },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = CardSurfaceDark, contentColor = AccentGold)
                ) {
                    Icon(Icons.Default.Settings, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("TÁTICA", fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = { viewModel.skipLiveMatch() },
                    modifier = Modifier.weight(1.2f),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentLime, contentColor = TurfDeepGreen)
                ) {
                    Icon(Icons.Default.SkipNext, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("PULAR PARTIDA", fontWeight = FontWeight.Black)
                }
            }
        }

        if (showTacticalDialog) {
            TacticalControlDialog(
                viewModel = viewModel,
                onDismiss = { showTacticalDialog = false }
            )
        }
    }
}
}


@Composable
fun StatBar(label: String, hVal: String, aVal: String, hProgress: Float) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(hVal, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Text(label, color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            Text(aVal, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(2.dp))
        ) {
            val coercedH = hProgress.coerceIn(0.01f, 0.99f)
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(coercedH)
                    .background(AccentLime, RoundedCornerShape(topStart = 2.dp, bottomStart = 2.dp))
            )
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(1f - coercedH)
                    .background(AccentGold, RoundedCornerShape(topEnd = 2.dp, bottomEnd = 2.dp))
            )
        }
    }
}