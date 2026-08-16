package com.example.ui.screens

import com.example.ui.components.PositionEvolutionChart
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
fun HistoryTab(viewModel: GameViewModel) {
    val historicalRecords by viewModel.historicalRecords.collectAsStateWithLifecycle()
    val clubLegends by viewModel.clubLegends.collectAsStateWithLifecycle()
    val playerTeam by viewModel.playerTeam.collectAsStateWithLifecycle()
    val selectedCountry by viewModel.selectedCountry.collectAsStateWithLifecycle()

    var selectedSubTab by remember { mutableStateOf(0) } // 0 = Troféus, 1 = Histórico, 2 = Hall da Fama

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "GALERIA DE HONRA E CONQUISTAS",
            color = Color.White,
            fontWeight = FontWeight.Black,
            fontSize = 16.sp,
            letterSpacing = 0.5.sp
        )
        Spacer(modifier = Modifier.height(14.dp))

        // Sub Tabs Selector
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(CardSurfaceDark, RoundedCornerShape(10.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            val subTabs = listOf(
                Pair(0, "Troféus"),
                Pair(1, "Histórico da Liga"),
                Pair(2, "Hall da Fama")
            )
            subTabs.forEach { (index, label) ->
                val isActive = selectedSubTab == index
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isActive) TurfDeepGreen else Color.Transparent)
                        .clickable { selectedSubTab = index }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        color = if (isActive) Color.White else Color.Gray,
                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 12.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        when (selectedSubTab) {
            0 -> {
                // Trophy Room - Dynamic based on country
                val teamName = playerTeam?.name ?: ""

                val titleCont1 = DefaultData.getCompetitionName("CONTINENTAL_T1", selectedCountry)
                val titleCont2 = DefaultData.getCompetitionName("CONTINENTAL_T2", selectedCountry)
                val titleCopa = DefaultData.getCompetitionName("COPA", selectedCountry)
                val titleMundial = DefaultData.getCompetitionName("WORLD_CUP", selectedCountry)
                val titleSerieA = DefaultData.getCompetitionName("SERIE_A", selectedCountry)
                val titleSerieB = DefaultData.getCompetitionName("SERIE_B", selectedCountry)
                val titleSerieC = DefaultData.getCompetitionName("SERIE_C", selectedCountry)
                val titleSerieD = DefaultData.getCompetitionName("SERIE_D", selectedCountry)

                val cont1Titles = historicalRecords.count { it.championTeamName == teamName && (it.competitionName == titleCont1 || it.competitionName.contains("Libertadores") || it.competitionName.contains("Champions") || it.competitionName.contains("CONCACAF") || it.competitionName.contains("CAF") || it.competitionName.contains("AFC")) }
                val cont2Titles = historicalRecords.count { it.championTeamName == teamName && (it.competitionName == titleCont2 || it.competitionName.contains("Sudamericana") || it.competitionName.contains("Europa") || it.competitionName.contains("Conference")) }
                val copaTitles = historicalRecords.count { it.championTeamName == teamName && (it.competitionName == titleCopa || it.competitionName.contains("Copa") || it.competitionName.contains("Cup") || it.competitionName.contains("Taça")) }
                val mundialTitles = historicalRecords.count { it.championTeamName == teamName && (it.competitionName == titleMundial || it.competitionName.contains("Mundial") || it.competitionName.contains("World")) }
                val serieATitles = historicalRecords.count { it.championTeamName == teamName && (it.competitionName == titleSerieA || it.competitionName.contains("Série A") || it.competitionName.contains("1ª") || it.competitionName.contains("Premier") || it.competitionName.contains("La Liga") || it.competitionName.contains("Liga")) }
                val serieBTitles = historicalRecords.count { it.championTeamName == teamName && (it.competitionName == titleSerieB || it.competitionName.contains("Série B") || it.competitionName.contains("2ª") || it.competitionName.contains("Championship")) }
                val serieCTitles = historicalRecords.count { it.championTeamName == teamName && (it.competitionName == titleSerieC || it.competitionName.contains("Série C") || it.competitionName.contains("3ª") || it.competitionName.contains("League One")) }
                val serieDTitles = historicalRecords.count { it.championTeamName == teamName && (it.competitionName == titleSerieD || it.competitionName.contains("Série D") || it.competitionName.contains("4ª") || it.competitionName.contains("League Two")) }

                val totalTitles = cont1Titles + cont2Titles + copaTitles + mundialTitles + serieATitles + serieBTitles + serieCTitles + serieDTitles

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Surface(
                            color = CardSurfaceDark,
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Default.EmojiEvents,
                                    contentDescription = null,
                                    tint = AccentGold,
                                    modifier = Modifier.size(52.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "SALA DE TROFÉUS DO ${teamName.uppercase()}",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Conquiste ligas e copas para se tornar uma lenda no futebol.",
                                    color = Color.LightGray,
                                    fontSize = 12.sp,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceAround
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(text = "$totalTitles", color = AccentGold, fontWeight = FontWeight.Black, fontSize = 24.sp)
                                        Text(text = "TÍTULOS", color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        val runCount = historicalRecords.count { it.runnerUpTeamName == teamName }
                                        Text(text = "$runCount", color = NeonCyanAccent, fontWeight = FontWeight.Black, fontSize = 24.sp)
                                        Text(text = "VICE-CAMPEONATOS", color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }

                    item {
                        Text(
                            text = "SUAS CONQUISTAS COLECIONADAS",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                        )
                    }

                    val trophies = listOf(
                        Triple(titleCont1, "A Glória Eterna! O torneio continental mais prestigiado.", cont1Titles),
                        Triple(titleCont2, "A grande conquista continental secundária.", cont2Titles),
                        Triple(titleCopa, "O torneio nacional eliminatório e mais democrático do país.", copaTitles),
                        Triple(titleMundial, "O topo absoluto do futebol mundial.", mundialTitles),
                        Triple(titleSerieA, "O ápice do futebol nacional. O campeonato de maior prestígio da divisão principal.", serieATitles),
                        Triple(titleSerieB, "A competitiva divisão de acesso para a elite.", serieBTitles),
                        Triple(titleSerieC, "Uma divisão desafiadora e cheia de rivalidades.", serieCTitles),
                        Triple(titleSerieD, "A base da escalada nacional rumo à glória.", serieDTitles)
                    )

                    items(trophies) { (title, desc, count) ->
                        val hasTrophy = count > 0
                        Surface(
                            color = if (hasTrophy) CardSurfaceDark else CardSurfaceDark.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(
                                1.dp,
                                if (hasTrophy) AccentGold.copy(alpha = 0.4f) else Color.White.copy(alpha = 0.05f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .background(
                                            if (hasTrophy) AccentGold.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.05f),
                                            CircleShape
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.EmojiEvents,
                                        contentDescription = null,
                                        tint = if (hasTrophy) AccentGold else Color.Gray.copy(alpha = 0.6f),
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = title,
                                            color = if (hasTrophy) Color.White else Color.Gray,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp
                                        )
                                        if (hasTrophy) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Surface(
                                                color = AccentGold.copy(alpha = 0.2f),
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(
                                                    text = "x$count",
                                                    color = AccentGold,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = desc,
                                        color = if (hasTrophy) Color.LightGray else Color.Gray.copy(alpha = 0.8f),
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            1 -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    PositionEvolutionChart(
                        rankings = listOf(10, 8, 6, 5, 3, 2, 1, 1, 2, 1)
                    )

                    if (historicalRecords.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Default.History,
                                    contentDescription = null,
                                    tint = Color.Gray.copy(alpha = 0.5f),
                                    modifier = Modifier.size(56.dp)
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "Nenhum histórico registrado ainda",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "O histórico oficial das ligas começará a ser arquivado no final desta temporada (na semana 45).",
                                    color = Color.Gray,
                                    fontSize = 11.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    } else {
                        historicalRecords.forEach { record ->
                            Surface(
                                color = CardSurfaceDark,
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = record.competitionName.uppercase(),
                                            color = AccentLime,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp
                                        )
                                        Text(
                                            text = "Temporada ${record.season}",
                                            color = Color.Gray,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                                    ) {
                                        // Champion Column
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("CAMPEÃO", color = Color.Gray, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    Icons.Default.Star,
                                                    contentDescription = null,
                                                    tint = AccentGold,
                                                    modifier = Modifier.size(12.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = record.championTeamName,
                                                    color = Color.White,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }

                                        // Runner up Column
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("VICE-CAMPEÃO", color = Color.Gray, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = record.runnerUpTeamName,
                                                color = Color.LightGray,
                                                fontWeight = FontWeight.Medium,
                                                fontSize = 13.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }

                                    HorizontalDivider(
                                        color = Color.White.copy(alpha = 0.05f),
                                        thickness = 1.dp,
                                        modifier = Modifier.padding(vertical = 10.dp)
                                    )

                                    // Top Scorer details
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Default.SportsSoccer,
                                            contentDescription = null,
                                            tint = NeonGreenAccent,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Column {
                                            Text(
                                                text = "ARTILHEIRO: ${record.topScorerName}",
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp
                                            )
                                            Text(
                                                text = "${record.topScorerTeam} • ${record.topScorerGoals} gols",
                                                color = Color.Gray,
                                                fontSize = 11.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            2 -> {
                // Hall of Fame / Club Legends
                if (clubLegends.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.Groups,
                                contentDescription = null,
                                tint = Color.Gray.copy(alpha = 0.5f),
                                modifier = Modifier.size(56.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Nenhuma lenda eternizada",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Quando jogadores fiéis ao seu clube completarem 42 anos e se aposentarem, seu legado e conquistas serão guardados para sempre aqui.",
                                color = Color.Gray,
                                fontSize = 11.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(clubLegends) { legend ->
                            Surface(
                                color = CardSurfaceDark,
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, AccentGold.copy(alpha = 0.25f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .background(AccentGold.copy(alpha = 0.12f), CircleShape)
                                            .border(1.dp, AccentGold.copy(alpha = 0.4f), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = legend.position,
                                            color = AccentGold,
                                            fontWeight = FontWeight.Black,
                                            fontSize = 12.sp
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(14.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = legend.playerName,
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "Partidas: ${legend.apps}",
                                                color = Color.LightGray,
                                                fontSize = 11.sp
                                            )
                                            Text(
                                                text = "Gols: ${legend.goals}",
                                                color = AccentLime,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
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
    }
}

