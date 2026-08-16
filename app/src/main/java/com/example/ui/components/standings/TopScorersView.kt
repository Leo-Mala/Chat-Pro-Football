package com.example.ui.components.standings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SportsSoccer
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
import com.example.data.Player
import com.example.data.Team
import com.example.ui.screens.TeamBadge
import com.example.ui.theme.*

@Composable
fun TopScorersView(allPlayers: List<Player>, allTeams: List<Team>) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedPosFilter by remember { mutableStateOf("TODOS") }

    val filteredScorers = remember(allPlayers, searchQuery, selectedPosFilter) {
        allPlayers
            .filter { it.careerGoals > 0 }
            .filter { player ->
                if (searchQuery.isBlank()) true
                else {
                    val teamName = allTeams.find { it.id == player.teamId }?.name ?: ""
                    player.name.contains(searchQuery, ignoreCase = true) || teamName.contains(searchQuery, ignoreCase = true)
                }
            }
            .filter { player ->
                when (selectedPosFilter) {
                    "ATA" -> player.position in listOf("ST", "CF", "RW", "LW", "ATA", "PTE", "PTD")
                    "MEI" -> player.position in listOf("CAM", "CM", "CDM", "LM", "RM", "MEI", "VOL")
                    "DEF" -> player.position in listOf("CB", "LB", "RB", "LWB", "RWB", "ZAG", "LD", "LE")
                    else -> true
                }
            }
            .sortedByDescending { it.careerGoals }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Search & Filter Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Buscar jogador ou clube...", color = Color.Gray, fontSize = 12.sp) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = AccentLime) },
            trailingIcon = if (searchQuery.isNotEmpty()) {
                { IconButton(onClick = { searchQuery = "" }) { Icon(Icons.Default.Clear, contentDescription = null, tint = Color.Gray) } }
            } else null,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = TurfDeepGreen,
                unfocusedBorderColor = CardSurfaceDark,
                focusedContainerColor = CardSurfaceDark,
                unfocusedContainerColor = CardSurfaceDark,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            shape = RoundedCornerShape(10.dp),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Position Filter Pills
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf("TODOS" to "Todas Posições", "ATA" to "Atacantes", "MEI" to "Meias", "DEF" to "Defensores").forEach { (code, label) ->
                val isActive = selectedPosFilter == code
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isActive) TurfDeepGreen else CardSurfaceDark)
                        .clickable { selectedPosFilter = code }
                        .padding(vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        label,
                        color = if (isActive) AccentLime else Color.Gray,
                        fontSize = 11.sp,
                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (filteredScorers.isEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                colors = CardDefaults.cardColors(containerColor = CardSurfaceDark),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.SportsSoccer, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        if (searchQuery.isNotEmpty()) "Nenhum artilheiro encontrado para '$searchQuery'."
                        else "Nenhum gol registrado na temporada até o momento.",
                        color = Color.Gray,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Top 3 Podium if not searching
                if (searchQuery.isEmpty() && selectedPosFilter == "TODOS" && filteredScorers.size >= 3) {
                    item {
                        TopScorersPodium(top3 = filteredScorers.take(3), allTeams = allTeams)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            "CLASSIFICAÇÃO GERAL DE ARTILHEIROS",
                            color = MaterialTheme.colorScheme.onBackground,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }
                }

                itemsIndexed(filteredScorers) { index, player ->
                    val team = allTeams.find { it.id == player.teamId }
                    val teamName = team?.name ?: "Sem Clube"
                    val isTop3 = index < 3 && searchQuery.isEmpty() && selectedPosFilter == "TODOS"

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isTop3) TurfDeepGreen.copy(alpha = 0.2f) else CardSurfaceDark
                        ),
                        shape = RoundedCornerShape(10.dp),
                        border = if (isTop3) BorderStroke(1.dp, AccentGold.copy(alpha = 0.4f)) else null
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Rank Number / Badge
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when (index) {
                                            0 -> AccentGold
                                            1 -> Color(0xFFC0C0C0)
                                            2 -> Color(0xFFCD7F32)
                                            else -> CardSurfaceDark
                                        }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "#${index + 1}",
                                    color = if (index < 3) Color.Black else Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            // Team Badge
                            if (team != null) {
                                TeamBadge(logoUrl = team.logoUrl, teamName = team.name, size = 28.dp)
                                Spacer(modifier = Modifier.width(10.dp))
                            }

                            // Player Name & Info
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    player.name,
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        player.position,
                                        color = AccentLime,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text("•", color = Color.Gray, fontSize = 10.sp)
                                    Text(
                                        teamName,
                                        color = Color.Gray,
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text("•", color = Color.Gray, fontSize = 10.sp)
                                    Text(
                                        "FORÇA ${player.force}",
                                        color = Color.LightGray,
                                        fontSize = 11.sp
                                    )
                                }
                            }

                            // Goals Count Badge
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(TurfDeepGreen)
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("⚽", fontSize = 12.sp)
                                    Text(
                                        "${player.careerGoals}",
                                        color = AccentGold,
                                        fontWeight = FontWeight.Black,
                                        fontSize = 14.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TopScorersPodium(top3: List<Player>, allTeams: List<Team>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardSurfaceDark),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, AccentGold.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Default.EmojiEvents, contentDescription = null, tint = AccentGold, modifier = Modifier.size(18.dp))
                Text("PÓDIO DE ARTILHEIROS", color = AccentGold, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.Bottom
            ) {
                // 2nd Place
                if (top3.size > 1) {
                    PodiumCard(player = top3[1], rank = 2, badgeColor = Color(0xFFC0C0C0), allTeams = allTeams, modifier = Modifier.weight(1f))
                }
                // 1st Place
                if (top3.isNotEmpty()) {
                    PodiumCard(player = top3[0], rank = 1, badgeColor = AccentGold, allTeams = allTeams, modifier = Modifier.weight(1.1f))
                }
                // 3rd Place
                if (top3.size > 2) {
                    PodiumCard(player = top3[2], rank = 3, badgeColor = Color(0xFFCD7F32), allTeams = allTeams, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
fun PodiumCard(player: Player, rank: Int, badgeColor: Color, allTeams: List<Team>, modifier: Modifier = Modifier) {
    val team = allTeams.find { it.id == player.teamId }
    Column(
        modifier = modifier.padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(if (rank == 1) 32.dp else 26.dp)
                .clip(CircleShape)
                .background(badgeColor),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "#$rank",
                color = Color.Black,
                fontWeight = FontWeight.Black,
                fontSize = if (rank == 1) 13.sp else 11.sp
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        if (team != null) {
            TeamBadge(logoUrl = team.logoUrl, teamName = team.name, size = if (rank == 1) 28.dp else 22.dp)
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            player.name,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = if (rank == 1) 12.sp else 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
        Text(
            "${player.careerGoals} gols",
            color = AccentLime,
            fontWeight = FontWeight.Black,
            fontSize = if (rank == 1) 12.sp else 10.sp
        )
    }
}
