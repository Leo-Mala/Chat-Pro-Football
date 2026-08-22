package com.example.ui.components.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.*
import com.example.ui.components.bounceClick
import com.example.ui.components.pulseGlow
import com.example.ui.screens.TeamBadge
import com.example.ui.theme.*
import com.example.ui.viewmodel.*

@Composable
fun DashboardTab(
    viewModel: GameViewModel,
    onNavigateToSquad: () -> Unit,
    onNavigateToTactics: () -> Unit
) {
    val saveState by viewModel.gameSave.collectAsStateWithLifecycle()
    val nextFixture by viewModel.playerNextFixture.collectAsStateWithLifecycle()
    val playerTeam by viewModel.playerTeam.collectAsStateWithLifecycle()
    val leagueTeams by viewModel.playerLeagueTeams.collectAsStateWithLifecycle()

    val roster by viewModel.playerRoster.collectAsStateWithLifecycle()
    val leagueFixtures by viewModel.playerLeagueFixtures.collectAsStateWithLifecycle()
    val dashboardSeasonFixtures by viewModel.dashboardSeasonFixtures.collectAsStateWithLifecycle()
    val dashboardSeasonTeams by viewModel.dashboardSeasonTeams.collectAsStateWithLifecycle()
    val nextOpponentTeam by viewModel.nextOpponentTeam.collectAsStateWithLifecycle()
    val formation by viewModel.playerFormation.collectAsStateWithLifecycle()
    val style by viewModel.playerStyle.collectAsStateWithLifecycle()
    val selectedCountry by viewModel.selectedCountry.collectAsStateWithLifecycle()
    val transactionHistory by viewModel.transactionHistory.collectAsStateWithLifecycle()

    val isSimulatingSeason by viewModel.isSimulatingSeason.collectAsStateWithLifecycle()
    val simWeek by viewModel.simulationCurrentWeek.collectAsStateWithLifecycle()
    val simCompName by viewModel.simulationCompetitionName.collectAsStateWithLifecycle()
    val simMatchInfo by viewModel.simulationMatchInfo.collectAsStateWithLifecycle()
    val simLogs by viewModel.simulationLogs.collectAsStateWithLifecycle()

    val s = saveState
    val pTeam = playerTeam
    if (pTeam == null || s == null) return

    val playerTeamPosition = remember(leagueFixtures, leagueTeams, pTeam) {
        val map = leagueTeams.associateWith { StandingRow(it.name) }.toMutableMap()

        for (f in leagueFixtures) {
            val homeT = leagueTeams.find { it.id == f.homeTeamId }
            val awayT = leagueTeams.find { it.id == f.awayTeamId }
            val hG = f.homeScore ?: 0
            val aG = f.awayScore ?: 0

            if (homeT != null && awayT != null) {
                val hRow = map[homeT] ?: continue
                val aRow = map[awayT] ?: continue

                hRow.gf += hG
                hRow.ga += aG
                aRow.gf += aG
                aRow.ga += hG

                hRow.gp += 1
                aRow.gp += 1

                when {
                    hG > aG -> {
                        hRow.pts += 3
                        hRow.w += 1
                        aRow.l += 1
                    }
                    aG > hG -> {
                        aRow.pts += 3
                        aRow.w += 1
                        hRow.l += 1
                    }
                    else -> {
                        hRow.pts += 1
                        aRow.pts += 1
                        hRow.d += 1
                        aRow.d += 1
                    }
                }
            }
        }

        val sorted = map.entries.sortedWith(
            compareByDescending<Map.Entry<Team, StandingRow>> { it.value.pts }
                .thenByDescending { it.value.w }
                .thenByDescending { it.value.gf - it.value.ga }
                .thenByDescending { it.value.gf }
        ).map { it.key }
        
        val idx = sorted.indexOfFirst { it.id == pTeam.id }
        if (idx != -1) idx + 1 else 1
    }

    val averageEnergy = remember(roster) {
        if (roster.isNotEmpty()) roster.sumOf { it.energy } / roster.size else 100
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (isSimulatingSeason) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(28.dp),
                border = BorderStroke(1.dp, AccentLime.copy(alpha = 0.6f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "SIMULAÇÃO EM ANDAMENTO ⚡",
                            color = AccentLime,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp
                        )
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = AccentLime,
                            strokeWidth = 2.dp
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Semana $simWeek de 45",
                        color = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 18.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = simCompName,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = simMatchInfo,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else if (nextFixture != null) {
            val fix = nextFixture!!
            val isHome = fix.homeTeamId == s.playerTeamId
            val opponentId = if (isHome) fix.awayTeamId else fix.homeTeamId
            val dbOpponent = nextOpponentTeam?.takeIf { it.id == opponentId }
            val virtualOpponent = if (dbOpponent == null) GlobalFootballSystem.getVirtualTeam(opponentId) else null
            val opponentName = dbOpponent?.name ?: virtualOpponent?.name ?: "CPU"
            val opponentRating = dbOpponent?.rating ?: virtualOpponent?.rating ?: 50
            val opponentLogo = dbOpponent?.logoUrl ?: DefaultData.getLogoForTeam(opponentName, selectedCountry)

            val diff = opponentRating - pTeam.rating
            val (diffLabel, diffBg, diffText) = when {
                diff > 5 -> Triple("DIFÍCIL", PolishSoftPinkBg, PolishSoftPinkText)
                diff < -5 -> Triple("FÁCIL", Color(0xFFE8F5E9), Color(0xFF2E7D32))
                else -> Triple("MÉDIO", Color(0xFFFFF3E0), Color(0xFFE65100))
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(28.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "PRÓXIMO COMPROMISSO",
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp
                        )

                        Surface(
                            color = diffBg,
                            shape = RoundedCornerShape(4.dp),
                        ) {
                            Text(
                                text = diffLabel,
                                color = diffText,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.weight(1f)
                        ) {
                            TeamBadge(
                                teamName = pTeam.name,
                                logoUrl = pTeam.logoUrl,
                                size = 56.dp
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                pTeam.name,
                                color = MaterialTheme.colorScheme.onBackground,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                "FORÇA: ${pTeam.rating}",
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "VS",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.onBackground,
                                fontFamily = FontFamily.Monospace
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (isHome) pTeam.stadiumName.uppercase() else "FORA DE CASA",
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                        }

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.weight(1f)
                        ) {
                            TeamBadge(
                                teamName = opponentName,
                                logoUrl = opponentLogo,
                                size = 56.dp
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                opponentName,
                                color = MaterialTheme.colorScheme.onBackground,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                "FORÇA: $opponentRating",
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Estádio", fontSize = 10.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
                            Text(
                                if (isHome) pTeam.stadiumName else dbOpponent?.stadiumName ?: "Estádio Olímpico",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onBackground,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Campeonato", fontSize = 10.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
                            Text(
                                DefaultData.getCompetitionName(fix.competitionType, selectedCountry),
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Button(
                        onClick = { viewModel.startLiveMatch(fix) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .pulseGlow()
                            .bounceClick()
                            .testTag("play_match_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        shape = CircleShape
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayCircle,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "INICIAR PARTIDA",
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        } else {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(28.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.Bed, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("SEM JOGO NESTA SEMANA", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("Seu clube está de folga nesta rodada do calendário.", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f), fontSize = 13.sp, textAlign = TextAlign.Center)
                    Spacer(modifier = Modifier.height(20.dp))
                    Button(
                        onClick = { viewModel.skipLiveMatch() },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary),
                        shape = CircleShape
                    ) {
                        Text(
                            text = "AVANÇAR CALENDÁRIO",
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "SIMULADOR DE TEMPORADA",
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Simula as rodadas do calendário de forma acelerada.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            fontSize = 11.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (isSimulatingSeason) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, AccentLime.copy(alpha = 0.5f))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Simulando Semana $simWeek de 45...",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = AccentLime
                                )
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = AccentLime,
                                    strokeWidth = 2.dp
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Campeonato: $simCompName",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Resultado: $simMatchInfo",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            if (simLogs.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Histórico Recente:",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp,
                                    color = Color.Gray
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Column {
                                    simLogs.take(3).forEach { log ->
                                        Text(
                                            text = log,
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = { viewModel.stopSeasonSimulation() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red, contentColor = Color.White),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .testTag("stop_season_simulation_button"),
                        shape = CircleShape
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("PARAR SIMULAÇÃO", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                } else {
                    Button(
                        onClick = { viewModel.startSeasonSimulation() },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentLime, contentColor = TurfDeepGreen),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .testTag("start_season_simulation_button"),
                        shape = CircleShape
                    ) {
                        Icon(Icons.Default.FastForward, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("SIMULAR TEMPORADA", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "POSIÇÃO ATUAL",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "${playerTeamPosition}º",
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        val hierarchy = remember(selectedCountry) { LeagueHierarchyLoader.getHierarchyForCountry(selectedCountry) }
                        val divName = remember(hierarchy, pTeam) {
                            hierarchy.divisions.find { it.divisionLevel == pTeam.division }?.name ?: "Série A"
                        }
                        Text(
                            text = if (playerTeamPosition <= 4) "▲ G4" else divName,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (playerTeamPosition <= 4) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }
                    Text(
                        text = if (playerTeamPosition <= 4) "Zona de Acesso" else "Meio da Tabela",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }

            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "ENERGIA MÉDIA",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    LinearProgressIndicator(
                        progress = averageEnergy.toFloat() / 100f,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(CircleShape),
                        color = if (averageEnergy > 75) Color(0xFF2E7D32) else if (averageEnergy > 55) Color(0xFFE65100) else Color.Red,
                        trackColor = Color.White.copy(alpha = 0.4f)
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "${averageEnergy}% - ${if (averageEnergy > 75) "Elenco Pronto" else if (averageEnergy > 55) "Elenco Cansado" else "Exausto"}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        DashboardNewsFeedSection(
            save = s,
            playerTeam = pTeam,
            allTeams = dashboardSeasonTeams,
            allFixtures = dashboardSeasonFixtures,
            transactions = transactionHistory
        )

        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "TÁTICA ATUAL",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                letterSpacing = 1.sp
            )
            
            Spacer(modifier = Modifier.height(8.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CardSurfaceDark),
                border = BorderStroke(
                    width = 1.2.dp,
                    brush = Brush.horizontalGradient(listOf(PolishSoftBlueBorder.copy(alpha = 0.4f), Color.Transparent))
                ),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.clickable { onNavigateToTactics() }) {
                            Text(
                                text = "ESQUEMA",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "$formation • $style",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .background(PolishPurpleContainer, CircleShape)
                                    .clickable { onNavigateToTactics() },
                                contentAlignment = Alignment.Center
                            ) {
                                Text("T", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = PolishOnPurpleContainer)
                            }
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .background(PolishPurpleContainer, CircleShape)
                                    .clickable { onNavigateToSquad() },
                                contentAlignment = Alignment.Center
                            ) {
                                Text("M", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = PolishOnPurpleContainer)
                            }
                        }
                    }
                }
            }
        }
    }
}
