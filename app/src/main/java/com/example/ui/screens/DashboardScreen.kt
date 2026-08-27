package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.*
import com.example.ui.components.dashboard.DashboardTab
import com.example.ui.components.squad.*
import com.example.ui.theme.*
import com.example.ui.viewmodel.GameViewModel

data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

@Composable
fun CareerDashboardScreen(viewModel: GameViewModel) {
    val gameSave by viewModel.gameSave.collectAsStateWithLifecycle()
    val playerTeam by viewModel.playerTeam.collectAsStateWithLifecycle()
    val playerRoster by viewModel.playerRoster.collectAsStateWithLifecycle()
    val selectedCountry by viewModel.selectedCountry.collectAsStateWithLifecycle()
    val nextFixture by viewModel.playerNextFixture.collectAsStateWithLifecycle()

    val hierarchy = remember(selectedCountry) { LeagueHierarchyLoader.getHierarchyForCountry(selectedCountry) }
    val divisionName = remember(hierarchy, playerTeam, nextFixture, selectedCountry) {
        nextFixture?.let { fix ->
            DefaultData.getCompetitionName(fix.competitionType, selectedCountry)
        } ?: run {
            val divLevel = playerTeam?.division ?: 1
            hierarchy.divisions.find { it.divisionLevel == divLevel }?.name ?: "Série A"
        }
    }

    var activeTabIndex by remember { mutableIntStateOf(0) }

    val tabs = listOf(
        Quadruple(0, "Painel", Icons.Default.Home, "dashboard_tab"),
        Quadruple(1, "Elenco", Icons.Default.Groups, "squad_tab"),
        Quadruple(2, "Tática", Icons.Default.Assessment, "tactics_tab"),
        Quadruple(3, "Treino", Icons.Default.Build, "training_tab"),
        Quadruple(4, "Mercado", Icons.Default.Storefront, "market_tab"),
        Quadruple(5, "Finanças", Icons.Default.AttachMoney, "finance_tab"),
        Quadruple(6, "Tabelas", Icons.Default.Leaderboard, "standings_tab"),
        Quadruple(7, "Técnico", Icons.Default.AccountBox, "coach_tab"),
        Quadruple(8, "Galeria", Icons.Default.EmojiEvents, "history_tab")
    )

    val saveStatus by viewModel.saveStatus.collectAsStateWithLifecycle()
    val evolutionSummary by viewModel.monthlyEvolutionSummary.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(NeonMidnightBg, NeonMidnightSurface)
                            )
                        )
                        .statusBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(AccentLime.copy(alpha = 0.1f), CircleShape)
                                    .border(1.5.dp, AccentLime.copy(alpha = 0.3f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Person,
                                    contentDescription = null,
                                    tint = AccentLime,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = gameSave?.coachName ?: "Técnico",
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.Star,
                                        contentDescription = null,
                                        tint = AccentGold,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Reputação: ${gameSave?.coachReputation}/100",
                                        color = Color.White.copy(alpha = 0.6f),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }

                        Surface(
                            color = NeonGreenAccent.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, NeonGreenAccent.copy(alpha = 0.3f))
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Icon(
                                    Icons.Default.AttachMoney,
                                    contentDescription = null,
                                    tint = NeonGreenAccent,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                Text(
                                    text = "R$ %,d".format(gameSave?.bankBalance ?: 0L),
                                    color = NeonGreenAccent,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 13.sp
                                )
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(CardSurfaceDark.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                            .border(1.5.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Shield,
                                contentDescription = null,
                                tint = AccentLime,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "${playerTeam?.name ?: "Sem Clube"} • $divisionName",
                                color = Color.White.copy(alpha = 0.9f),
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }

                        val calDate = gameSave?.let { GameCalendar.getFormattedDate(it.currentSeason, it.currentWeek) } ?: ""
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.CalendarToday,
                                contentDescription = null,
                                tint = AccentGold,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = calDate.uppercase(),
                                color = AccentGold,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }
                }
            },
            bottomBar = {
                Column(modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 1.dp)
                    ScrollableTabRow(
                        selectedTabIndex = activeTabIndex,
                        containerColor = NeonMidnightSurface,
                        contentColor = NeonCyanAccent,
                        edgePadding = 12.dp
                    ) {
                        tabs.forEach { (index, title, icon, tag) ->
                            Tab(
                                selected = activeTabIndex == index,
                                onClick = { activeTabIndex = index },
                                modifier = Modifier.testTag(tag),
                                text = { Text(title, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                                icon = { Icon(icon, contentDescription = title, modifier = Modifier.size(18.dp)) },
                                selectedContentColor = NeonCyanAccent,
                                unselectedContentColor = TextMutedBlue
                            )
                        }
                    }
                }
            },
            containerColor = MaterialTheme.colorScheme.background
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp)
            ) {
                AnimatedContent(
                    targetState = activeTabIndex,
                    transitionSpec = {
                        fadeIn(animationSpec = androidx.compose.animation.core.tween(200)) togetherWith fadeOut(animationSpec = androidx.compose.animation.core.tween(200))
                    },
                    label = "tab_transition",
                    modifier = Modifier.fillMaxSize()
                ) { targetIndex ->
                    when (targetIndex) {
                        0 -> DashboardTab(
                            viewModel = viewModel,
                            onNavigateToSquad = { activeTabIndex = 1 },
                            onNavigateToTactics = { activeTabIndex = 2 }
                        )
                        1 -> SquadTab(viewModel)
                        2 -> TacticsTab(viewModel)
                        3 -> TrainingScreen(
                            userTeam = playerTeam,
                            players = playerRoster,
                            onUpdateTrainingFocus = { p, focus -> viewModel.updatePlayerTrainingFocus(p, focus) },
                            onUpgradeCT = { viewModel.upgradeTrainingCenter() },
                            onBack = { activeTabIndex = 0 }
                        )
                        4 -> MarketTab(viewModel)
                        5 -> FinanceTab(viewModel)
                        6 -> StandingsTab(viewModel)
                        7 -> CoachTab(viewModel)
                        8 -> HistoryTab(viewModel)
                    }
                }
            }
        }

        evolutionSummary?.let { summaryList ->
            MonthlyEvolutionSummaryModal(
                summaryList = summaryList,
                onDismiss = { viewModel.dismissMonthlyEvolutionSummary() }
            )
        }

        saveStatus?.let { message ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 100.dp, start = 24.dp, end = 24.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    tonalElevation = 6.dp,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .testTag("save_status_notification")
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Save,
                            contentDescription = "Salvar",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = message,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}
