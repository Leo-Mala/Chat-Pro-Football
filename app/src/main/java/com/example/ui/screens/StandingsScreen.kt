package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.*
import com.example.ui.components.standings.TopScorersView
import com.example.ui.state.LeagueDivisionUi
import com.example.ui.theme.*
import com.example.ui.viewmodel.GameViewModel

class StandingRow(val teamName: String) {
    var pts = 0
    var gp = 0
    var w = 0
    var d = 0
    var l = 0
    var gf = 0
    var ga = 0
}

@Composable
fun StandingsTab(viewModel: GameViewModel) {
    val allTeams by viewModel.allTeams.collectAsStateWithLifecycle()
    val allPlayers by viewModel.seasonScorers.collectAsStateWithLifecycle()
    val allFixtures by viewModel.allFixtures.collectAsStateWithLifecycle()
    val selectedCountry by viewModel.selectedCountry.collectAsStateWithLifecycle()

    var selectedLeague by remember { mutableStateOf(LeagueDivisionUi.keyForDivision(1)) }
    var selectedSubTab by remember { mutableStateOf("grupos") }
    var selectedGroupLetter by remember { mutableStateOf("A") }
    var mainViewMode by remember { mutableStateOf("CLASSIFICACAO") }

    LaunchedEffect(selectedCountry) {
        selectedLeague = LeagueDivisionUi.keyForDivision(1)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(CardSurfaceDark, RoundedCornerShape(10.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            listOf(
                Pair("CLASSIFICACAO", "🏆 Classificação"),
                Pair("ARTILHARIA", "⚽ Artilharia")
            ).forEach { (mode, title) ->
                val isActive = mainViewMode == mode
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isActive) TurfDeepGreen else Color.Transparent)
                        .clickable { mainViewMode = mode }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        title,
                        fontSize = 12.sp,
                        color = if (isActive) AccentLime else Color.Gray,
                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))

        if (mainViewMode == "ARTILHARIA") {
            TopScorersView(allPlayers = allPlayers, allTeams = allTeams)
        } else {
            val hierarchy = remember(selectedCountry) { LeagueHierarchyLoader.getHierarchyForCountry(selectedCountry) }
            val confederation = remember(selectedCountry) {
                GlobalFootballSystem.getConfederationForCountry(selectedCountry)
            }
            val labels = remember(hierarchy, selectedCountry, confederation) {
                val list = mutableListOf<Pair<String, String>>()
                LeagueDivisionUi.tabsForHierarchy(hierarchy).forEach { tab ->
                    list.add(tab.key to tab.label)
                    if (selectedCountry == "Brasil" && tab.division == 3) {
                        list.add(Pair("SERIE_C_PH2_A", "Série C - Gp A"))
                        list.add(Pair("SERIE_C_PH2_B", "Série C - Gp B"))
                    }
                }

                list.add(Pair("COPA", DefaultData.getCompetitionName("COPA", selectedCountry)))
                listOf("CONTINENTAL_T1", "CONTINENTAL_T2", "CONTINENTAL_T3")
                    .filter { competitionType ->
                        ContinentalQualificationQuotaPolicy.isTierEnabled(
                            confederation = confederation,
                            competitionType = competitionType
                        )
                    }
                    .forEach { competitionType ->
                        list.add(
                            Pair(
                                competitionType,
                                DefaultData.getCompetitionName(competitionType, selectedCountry)
                            )
                        )
                    }
                list.add(Pair("WORLD_CUP", DefaultData.getCompetitionName("WORLD_CUP", selectedCountry)))
                list
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .background(CardSurfaceDark, RoundedCornerShape(8.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                labels.forEach { (code, label) ->
                    val isActive = selectedLeague == code
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isActive) TurfDeepGreen else Color.Transparent)
                            .clickable { selectedLeague = code }
                            .padding(vertical = 10.dp, horizontal = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            label,
                            fontSize = 11.sp,
                            color = if (isActive) Color.White else Color.Gray,
                            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            val isContinentalWithGroups = selectedLeague in listOf("CONTINENTAL_T1", "CONTINENTAL_T2", "WORLD_CUP")

            if (isContinentalWithGroups) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        Pair("grupos", "Fase de Grupos"),
                        Pair("eliminatoria", "Fase Final")
                    ).forEach { (subCode, title) ->
                        val isSubActive = selectedSubTab == subCode
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSubActive) TurfDeepGreen else CardSurfaceDark)
                                .clickable { selectedSubTab = subCode }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(title, color = if (isSubActive) Color.White else Color.Gray, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            val isKnockout = if (isContinentalWithGroups) {
                selectedSubTab == "eliminatoria"
            } else {
                selectedLeague in listOf("COPA", "CONTINENTAL_T3")
            }

            if (isContinentalWithGroups && selectedSubTab == "grupos") {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ('A'..'H').forEach { char ->
                        val letter = char.toString()
                        val isLetterActive = selectedGroupLetter == letter
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (isLetterActive) TurfDeepGreen else CardSurfaceDark)
                                .clickable { selectedGroupLetter = letter }
                                .padding(horizontal = 14.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Grupo $letter", color = if (isLetterActive) Color.White else Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                val groupCode = selectedLeague + "_GP_" + selectedGroupLetter
                val groupFixtures = allFixtures.filter { it.competitionType == groupCode }
                val groupTeamIds = groupFixtures.flatMap { listOf(it.homeTeamId, it.awayTeamId) }.distinct().filter { it != -1L && it != 0L }
                val groupTeams = groupTeamIds.map { id ->
                    allTeams.find { it.id == id } ?: GlobalFootballSystem.getVirtualTeam(id)
                }

                val groupStandings = remember(groupCode, allFixtures, groupTeams, confederation) {
                    val map = groupTeams.associateWith { StandingRow(it.name) }.toMutableMap()
                    val playedFixtures = groupFixtures.filter { it.isPlayed }

                    for (f in playedFixtures) {
                        val homeT = groupTeams.find { it.id == f.homeTeamId }
                        val awayT = groupTeams.find { it.id == f.awayTeamId }
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

                    val entriesById = map.entries.associateBy { it.key.id }
                    if (
                        confederation.equals("CONMEBOL", ignoreCase = true) &&
                        selectedLeague in setOf("CONTINENTAL_T1", "CONTINENTAL_T2")
                    ) {
                        ConmebolCompetitionSystem.calculateGroupRanking(groupFixtures)
                            .mapNotNull { teamId -> entriesById[teamId] }
                            .map { it.key to it.value }
                    } else {
                        map.entries.sortedWith(
                            compareByDescending<Map.Entry<Team, StandingRow>> { it.value.pts }
                                .thenByDescending { it.value.w }
                                .thenByDescending { it.value.gf - it.value.ga }
                                .thenByDescending { it.value.gf }
                        ).map { it.key to it.value }
                    }
                }

                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = CardSurfaceDark),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("#", modifier = Modifier.width(18.dp), color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Text("Clube", modifier = Modifier.weight(1f), color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Text("PTS", modifier = Modifier.width(32.dp), color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                                Text("J", modifier = Modifier.width(20.dp), color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                                Text("V", modifier = Modifier.width(20.dp), color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                                Text("E", modifier = Modifier.width(20.dp), color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                                Text("D", modifier = Modifier.width(20.dp), color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                                Text("GP", modifier = Modifier.width(22.dp), color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                                Text("GC", modifier = Modifier.width(22.dp), color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                                Text("SG", modifier = Modifier.width(24.dp), color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                            }
                        }
                    }

                    items(groupStandings.size) { index ->
                        val (team, row) = groupStandings[index]
                        val pos = index + 1
                        val isPlayer = team.isPlayerControlled
                        val isQualified = pos <= 2
                        val isLibertadoresThird =
                            confederation.equals("CONMEBOL", ignoreCase = true) &&
                                selectedLeague == "CONTINENTAL_T1" && pos == 3
                        val posColor = when {
                            isLibertadoresThird -> AccentGold
                            isQualified -> AccentLime
                            else -> Color.White
                        }

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = if (isPlayer) TurfDeepGreen.copy(alpha = 0.5f) else CardSurfaceDark),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "$pos",
                                    modifier = Modifier.width(18.dp),
                                    color = posColor,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                TeamBadge(logoUrl = team.logoUrl, teamName = team.name, size = 16.dp)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "${team.name} (${team.country.take(3).uppercase()})",
                                    modifier = Modifier.weight(1f),
                                    color = if (isPlayer) AccentLime else Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = if (isPlayer) FontWeight.Bold else FontWeight.Normal,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text("${row.pts}", modifier = Modifier.width(32.dp), color = MaterialTheme.colorScheme.onBackground, fontSize = 12.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
                                Text("${row.gp}", modifier = Modifier.width(20.dp), color = Color.Gray, fontSize = 11.sp, textAlign = TextAlign.Center)
                                Text("${row.w}", modifier = Modifier.width(20.dp), color = Color.Gray, fontSize = 11.sp, textAlign = TextAlign.Center)
                                Text("${row.d}", modifier = Modifier.width(20.dp), color = Color.Gray, fontSize = 11.sp, textAlign = TextAlign.Center)
                                Text("${row.l}", modifier = Modifier.width(20.dp), color = Color.Gray, fontSize = 11.sp, textAlign = TextAlign.Center)
                                Text("${row.gf}", modifier = Modifier.width(22.dp), color = Color.Gray, fontSize = 11.sp, textAlign = TextAlign.Center)
                                Text("${row.ga}", modifier = Modifier.width(22.dp), color = Color.Gray, fontSize = 11.sp, textAlign = TextAlign.Center)
                                Text("${row.gf - row.ga}", modifier = Modifier.width(24.dp), color = if (row.gf - row.ga >= 0) AccentLime else Color.Red, fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                            }
                        }
                    }

                    if (
                        confederation.equals("CONMEBOL", ignoreCase = true) &&
                        selectedLeague == "CONTINENTAL_T1"
                    ) {
                        item {
                            Text(
                                text = "1º e 2º avançam às oitavas • 3º (dourado) vai ao playoff da Sul-Americana",
                                color = Color.Gray,
                                fontSize = 10.sp,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                            )
                        }
                    }

                    item {
                        Text(
                            text = "Partidas do Grupo",
                            color = AccentGold,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    }

                    items(groupFixtures.size) { matchIdx ->
                        val f = groupFixtures[matchIdx]
                        val homeTeam = allTeams.find { it.id == f.homeTeamId } ?: GlobalFootballSystem.getVirtualTeam(f.homeTeamId)
                        val awayTeam = allTeams.find { it.id == f.awayTeamId } ?: GlobalFootballSystem.getVirtualTeam(f.awayTeamId)

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = CardSurfaceDark),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1.2f)
                                ) {
                                    TeamBadge(logoUrl = homeTeam.logoUrl, teamName = homeTeam.name, size = 24.dp)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        homeTeam.name,
                                        color = if (homeTeam.isPlayerControlled) AccentLime else MaterialTheme.colorScheme.onBackground,
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center,
                                    modifier = Modifier.weight(0.8f)
                                ) {
                                    if (f.isPlayed) {
                                        Text(
                                            "${f.homeScore} - ${f.awayScore}",
                                            color = Color.White,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    } else {
                                        Text(
                                            "VS (Sem ${f.week})",
                                            color = Color.Gray,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.End,
                                    modifier = Modifier.weight(1.2f)
                                ) {
                                    Text(
                                        awayTeam.name,
                                        color = if (awayTeam.isPlayerControlled) AccentLime else MaterialTheme.colorScheme.onBackground,
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    TeamBadge(logoUrl = awayTeam.logoUrl, teamName = awayTeam.name, size = 24.dp)
                                }
                            }
                        }
                    }
                }
            } else if (isKnockout) {
                val compFixtures = allFixtures.filter { it.competitionType == selectedLeague }
                if (compFixtures.isEmpty()) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text("Nenhuma partida agendada para este torneio.", color = Color.Gray, fontSize = 13.sp)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val grouped = compFixtures.groupBy { it.week }.toSortedMap()
                        grouped.forEach { (weekNum, matches) ->
                            val phaseTitle = competitionPhaseTitle(
                                selectedLeague = selectedLeague,
                                week = weekNum,
                                confederation = confederation
                            )

                            item {
                                Text(
                                    text = phaseTitle,
                                    color = AccentGold,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    modifier = Modifier.padding(vertical = 6.dp)
                                )
                            }
                            items(matches.size) { matchIdx ->
                                val f = matches[matchIdx]
                                val homeTeam = allTeams.find { it.id == f.homeTeamId } ?: GlobalFootballSystem.getVirtualTeam(f.homeTeamId)
                                val awayTeam = allTeams.find { it.id == f.awayTeamId } ?: GlobalFootballSystem.getVirtualTeam(f.awayTeamId)

                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = CardSurfaceDark),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.weight(1.2f)
                                            ) {
                                                TeamBadge(logoUrl = homeTeam.logoUrl, teamName = homeTeam.name, size = 24.dp)
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    homeTeam.name,
                                                    color = if (homeTeam.isPlayerControlled) AccentLime else MaterialTheme.colorScheme.onBackground,
                                                    fontSize = 12.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    fontWeight = if (homeTeam.isPlayerControlled) FontWeight.Bold else FontWeight.Normal
                                                )
                                            }

                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.Center,
                                                modifier = Modifier.weight(1.0f)
                                            ) {
                                                if (f.isPlayed) {
                                                    val scoreText = if (f.homePenalties != null && f.awayPenalties != null) {
                                                        "${f.homeScore} - ${f.awayScore} (${f.homePenalties}x${f.awayPenalties} pen)"
                                                    } else {
                                                        "${f.homeScore} - ${f.awayScore}"
                                                    }
                                                    Text(
                                                        scoreText,
                                                        color = Color.White,
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                } else {
                                                    Text(
                                                        "VS",
                                                        color = Color.Gray,
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }

                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.End,
                                                modifier = Modifier.weight(1.2f)
                                            ) {
                                                Text(
                                                    awayTeam.name,
                                                    color = if (awayTeam.isPlayerControlled) AccentLime else MaterialTheme.colorScheme.onBackground,
                                                    fontSize = 12.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    fontWeight = if (awayTeam.isPlayerControlled) FontWeight.Bold else FontWeight.Normal
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                TeamBadge(logoUrl = awayTeam.logoUrl, teamName = awayTeam.name, size = 24.dp)
                                            }
                                        }

                                        val isFinal = isCompetitionFinalWeek(
                                            selectedLeague = selectedLeague,
                                            week = weekNum,
                                            confederation = confederation
                                        )
                                        if (f.isPlayed && isFinal && f.homeScore != null && f.awayScore != null) {
                                            val winnerId = when {
                                                f.homeScore > f.awayScore -> f.homeTeamId
                                                f.awayScore > f.homeScore -> f.awayTeamId
                                                (f.homePenalties ?: 0) > (f.awayPenalties ?: 0) -> f.homeTeamId
                                                else -> f.awayTeamId
                                            }
                                            val champTeam = if (winnerId == homeTeam.id) homeTeam else awayTeam
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Surface(
                                                color = AccentGold.copy(alpha = 0.15f),
                                                shape = RoundedCornerShape(4.dp),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Text(
                                                    text = "🏆 CAMPEÃO: ${champTeam.name.uppercase()}",
                                                    color = AccentGold,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Black,
                                                    textAlign = TextAlign.Center,
                                                    modifier = Modifier.padding(vertical = 4.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                val selectedDivision = LeagueDivisionUi.divisionFromKey(selectedLeague)
                val specialTeamIds = if (
                    selectedLeague == "SERIE_C_PH2_A" || selectedLeague == "SERIE_C_PH2_B"
                ) {
                    allFixtures
                        .filter { it.competitionType == selectedLeague }
                        .flatMap { listOf(it.homeTeamId, it.awayTeamId) }
                        .distinct()
                        .toSet()
                } else {
                    emptySet()
                }

                val leagueTeams = allTeams.filter {
                    if (it.id <= 0L) return@filter false
                    if (specialTeamIds.isNotEmpty()) {
                        it.id in specialTeamIds
                    } else {
                        selectedDivision != null &&
                            it.country == selectedCountry &&
                            it.division == selectedDivision
                    }
                }

                val leagueTeamIds = remember(leagueTeams) { leagueTeams.map { it.id }.toSet() }
                val acceptedCompetitionTypes = remember(selectedLeague, selectedDivision) {
                    selectedDivision
                        ?.let { LeagueSeasonFormat.acceptedDetailedCompetitionTypes(it) }
                        ?: setOf(selectedLeague)
                }
                val leagueFixtures = remember(allFixtures, leagueTeamIds, acceptedCompetitionTypes) {
                    allFixtures.filter {
                        it.competitionType in acceptedCompetitionTypes &&
                            it.homeTeamId in leagueTeamIds &&
                            it.awayTeamId in leagueTeamIds
                    }
                }
                val groupIndexByTeamId = remember(leagueTeamIds, leagueFixtures) {
                    DetailedGroupTopology.groupIndexByTeamId(
                        teamIds = leagueTeamIds,
                        fixtures = leagueFixtures
                    )
                }
                val groupedDetailedFormat = groupIndexByTeamId.isNotEmpty()

                val standings = remember(
                    selectedLeague,
                    selectedDivision,
                    leagueFixtures,
                    leagueTeams,
                    groupedDetailedFormat
                ) {
                    val map = leagueTeams.associateWith { StandingRow(it.name) }.toMutableMap()
                    val playedFixtures = leagueFixtures.filter { it.isPlayed }

                    for (f in playedFixtures) {
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

                    val teamsById = leagueTeams.associateBy { it.id }
                    val sportingComparator = compareByDescending<Long> { teamId ->
                        teamsById[teamId]?.let { team -> map[team]?.pts } ?: 0
                    }.thenByDescending { teamId ->
                        teamsById[teamId]?.let { team -> map[team]?.w } ?: 0
                    }.thenByDescending { teamId ->
                        teamsById[teamId]?.let { team -> (map[team]?.gf ?: 0) - (map[team]?.ga ?: 0) } ?: 0
                    }.thenByDescending { teamId ->
                        teamsById[teamId]?.let { team -> map[team]?.gf } ?: 0
                    }.thenByDescending { teamId ->
                        teamsById[teamId]?.rating ?: Int.MIN_VALUE
                    }.thenBy { it }

                    val groupedOrder = if (groupedDetailedFormat) {
                        DetailedGroupTopology.rankByGroupPosition(
                            teamIds = leagueTeamIds,
                            fixtures = leagueFixtures,
                            sportingComparator = sportingComparator
                        )
                    } else {
                        null
                    }

                    groupedOrder?.mapNotNull { teamId ->
                        val team = teamsById[teamId] ?: return@mapNotNull null
                        val row = map[team] ?: return@mapNotNull null
                        team to row
                    } ?: map.entries.sortedWith(
                        compareByDescending<Map.Entry<Team, StandingRow>> { it.value.pts }
                            .thenByDescending { it.value.w }
                            .thenByDescending { it.value.gf - it.value.ga }
                            .thenByDescending { it.value.gf }
                    ).map { Pair(it.key, it.value) }
                }

                if (groupedDetailedFormat) {
                    val groupSummary = groupIndexByTeamId.values
                        .toSet()
                        .sorted()
                        .joinToString(" • ") { groupIndex ->
                            "Grupo ${DetailedGroupTopology.groupLabel(groupIndex)}"
                        }
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        color = TurfDeepGreen.copy(alpha = 0.22f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "$groupSummary\nClassificação geral por posição dentro do grupo: campeões primeiro, depois vice-campeões, terceiros e assim por diante.",
                            color = AccentLime,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CardSurfaceDark, RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                        .padding(horizontal = 8.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("#", modifier = Modifier.width(18.dp), color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    if (groupedDetailedFormat) {
                        Text("GR", modifier = Modifier.width(28.dp), color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    }
                    Text("Clube", modifier = Modifier.weight(1f), color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text("PTS", modifier = Modifier.width(32.dp), color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    Text("J", modifier = Modifier.width(20.dp), color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    Text("V", modifier = Modifier.width(20.dp), color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    Text("E", modifier = Modifier.width(20.dp), color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    Text("D", modifier = Modifier.width(20.dp), color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    Text("GP", modifier = Modifier.width(22.dp), color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    Text("GC", modifier = Modifier.width(22.dp), color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    Text("SG", modifier = Modifier.width(24.dp), color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                }

                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth()
                ) {
                    items(standings.size) { idx ->
                        val (team, sRow) = standings[idx]
                        val rank = idx + 1
                        val isPlayer = team.isPlayerControlled

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(if (isPlayer) TurfDeepGreen.copy(alpha = 0.5f) else Color.Transparent)
                                .padding(horizontal = 8.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "$rank",
                                modifier = Modifier.width(18.dp),
                                color = when (rank) {
                                    1 -> AccentGold
                                    2, 3, 4 -> AccentLime
                                    else -> Color.White
                                },
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            if (groupedDetailedFormat) {
                                val groupIndex = groupIndexByTeamId[team.id]
                                Text(
                                    text = groupIndex?.let { DetailedGroupTopology.groupLabel(it) } ?: "-",
                                    modifier = Modifier.width(28.dp),
                                    color = AccentGold,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )
                            }
                            Spacer(modifier = Modifier.width(2.dp))
                            TeamBadge(logoUrl = team.logoUrl, teamName = team.name, size = 16.dp)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                team.name,
                                modifier = Modifier.weight(1f),
                                color = if (isPlayer) AccentLime else Color.White,
                                fontSize = 12.sp,
                                fontWeight = if (isPlayer) FontWeight.Bold else FontWeight.Normal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text("${sRow.pts}", modifier = Modifier.width(32.dp), color = MaterialTheme.colorScheme.onBackground, fontSize = 12.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
                            Text("${sRow.gp}", modifier = Modifier.width(20.dp), color = Color.Gray, fontSize = 11.sp, textAlign = TextAlign.Center)
                            Text("${sRow.w}", modifier = Modifier.width(20.dp), color = Color.Gray, fontSize = 11.sp, textAlign = TextAlign.Center)
                            Text("${sRow.d}", modifier = Modifier.width(20.dp), color = Color.Gray, fontSize = 11.sp, textAlign = TextAlign.Center)
                            Text("${sRow.l}", modifier = Modifier.width(20.dp), color = Color.Gray, fontSize = 11.sp, textAlign = TextAlign.Center)
                            Text("${sRow.gf}", modifier = Modifier.width(22.dp), color = Color.Gray, fontSize = 11.sp, textAlign = TextAlign.Center)
                            Text("${sRow.ga}", modifier = Modifier.width(22.dp), color = Color.Gray, fontSize = 11.sp, textAlign = TextAlign.Center)
                            Text("${sRow.gf - sRow.ga}", modifier = Modifier.width(24.dp), color = if (sRow.gf - sRow.ga >= 0) AccentLime else Color.Red, fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.05f))
                    }
                }
            }
        }
    }
}
