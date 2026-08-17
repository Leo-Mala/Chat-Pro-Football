package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.rememberAsyncImagePainter
import com.example.data.*
import com.example.ui.state.LeagueDivisionUi
import com.example.ui.theme.*
import com.example.ui.viewmodel.GameViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeamSelectionScreen(
    viewModel: GameViewModel,
    coachName: String,
    onBack: () -> Unit
) {
    var nameInput by remember { mutableStateOf(coachName) }
    val teams by viewModel.allTeams.collectAsStateWithLifecycle()
    val selectedTeamId by viewModel.selectedTeamId.collectAsStateWithLifecycle()
    val isStartingNewGame by viewModel.isStartingNewGame.collectAsStateWithLifecycle()

    var activeTab by remember { mutableIntStateOf(1) }
    val selectedCountry by viewModel.selectedCountry.collectAsStateWithLifecycle()
    var selectedContinent by remember { mutableStateOf("América do Sul") }

    // Synchronize continent and reset division when country changes
    LaunchedEffect(selectedCountry) {
        val conf = GlobalFootballSystem.countries.find { it.name == selectedCountry }?.confederation
        if (conf != null) {
            selectedContinent = when (conf) {
                "CONMEBOL" -> "América do Sul"
                "UEFA" -> "Europa"
                "CONCACAF" -> "América do Norte"
                "CAF" -> "África"
                "AFC" -> "Ásia"
                "OFC" -> "Oceania"
                else -> "América do Sul"
            }
        }
        activeTab = 1
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Escolha seu Clube", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = TurfDeepGreen,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        },
        containerColor = TurfPitchDark
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            // Coach Name input card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CardSurfaceDark),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Nome do Técnico:",
                        color = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { nameInput = it },
                        modifier = Modifier.fillMaxWidth().testTag("coach_name_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedTextColor = Color.White,
                            focusedTextColor = Color.White,
                            focusedBorderColor = AccentLime,
                            unfocusedBorderColor = Color.Gray
                        ),
                        placeholder = { Text("Insira o seu nome", color = Color.Gray) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 1. Choose Continent Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CardSurfaceDark),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        "1. Selecione o Continente:",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(listOf(
                            Pair("América do Sul", "🌎 América do Sul"),
                            Pair("Europa", "🇪🇺 Europa"),
                            Pair("América do Norte", "🇺🇸 Amér. do Norte"),
                            Pair("África", "🌍 África"),
                            Pair("Ásia", "🌏 Ásia"),
                            Pair("Oceania", "🏝️ Oceania")
                        )) { (code, label) ->
                            val isSelected = selectedContinent == code
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(if (isSelected) AccentLime else Color.White.copy(alpha = 0.05f))
                                    .border(
                                        width = 1.dp,
                                        color = if (isSelected) AccentLime else Color.White.copy(alpha = 0.15f),
                                        shape = RoundedCornerShape(20.dp)
                                    )
                                    .clickable {
                                        selectedContinent = code
                                        val conf = when (code) {
                                            "América do Sul" -> "CONMEBOL"
                                            "Europa" -> "UEFA"
                                            "América do Norte" -> "CONCACAF"
                                            "África" -> "CAF"
                                            "Ásia" -> "AFC"
                                            "Oceania" -> "OFC"
                                            else -> "CONMEBOL"
                                        }
                                        val firstCountry = GlobalFootballSystem.countries.firstOrNull { it.confederation == conf }
                                        if (firstCountry != null) {
                                            viewModel.selectCountry(firstCountry.name)
                                        }
                                    }
                                    .padding(horizontal = 14.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = label,
                                    color = if (isSelected) Color.White else Color.White.copy(alpha = 0.75f),
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            val availableCountries = remember(selectedContinent) {
                val conf = when (selectedContinent) {
                    "América do Sul" -> "CONMEBOL"
                    "Europa" -> "UEFA"
                    "América do Norte" -> "CONCACAF"
                    "África" -> "CAF"
                    "Ásia" -> "AFC"
                    "Oceania" -> "OFC"
                    else -> "CONMEBOL"
                }
                GlobalFootballSystem.countries.filter { it.confederation == conf }
            }

            // 2. Choose Country Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CardSurfaceDark),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        "2. Selecione o País:",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    if (availableCountries.isEmpty()) {
                        Text(
                            "Nenhum país disponível neste continente.",
                            color = Color.Gray,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    } else {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(availableCountries) { country ->
                                val isSelected = selectedCountry == country.name
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(if (isSelected) AccentLime else Color.White.copy(alpha = 0.05f))
                                        .border(
                                            width = 1.dp,
                                            color = if (isSelected) AccentLime else Color.White.copy(alpha = 0.15f),
                                            shape = RoundedCornerShape(20.dp)
                                        )
                                        .clickable {
                                            viewModel.selectCountry(country.name)
                                        }
                                        .padding(horizontal = 14.dp, vertical = 8.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = country.flag,
                                            fontSize = 14.sp,
                                            modifier = Modifier.padding(end = 6.dp)
                                        )
                                        Text(
                                            text = country.name,
                                            color = if (isSelected) Color.White else Color.White.copy(alpha = 0.75f),
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            val hierarchy = remember(selectedCountry) { LeagueHierarchyLoader.getHierarchyForCountry(selectedCountry) }
            val divTabs = remember(hierarchy) { LeagueDivisionUi.tabsForHierarchy(hierarchy) }

            // A lista é horizontal para continuar acessível quando o país possuir 5+ níveis.
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CardSurfaceDark, RoundedCornerShape(8.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(divTabs, key = { it.division }) { tab ->
                    val isActive = activeTab == tab.division
                    Box(
                        modifier = Modifier
                            .widthIn(min = 92.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isActive) TurfDeepGreen else Color.Transparent)
                            .clickable { activeTab = tab.division }
                            .testTag("division_tab_${tab.division}")
                            .padding(horizontal = 10.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = null,
                                tint = if (isActive) AccentLime else Color.Gray,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                tab.label,
                                fontSize = 11.sp,
                                color = if (isActive) Color.White else Color.Gray,
                                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Teams list with fallback resolution
            val filteredTeams = remember(teams, selectedCountry, activeTab) {
                val dbMatches = teams.filter {
                    it.country.equals(selectedCountry, ignoreCase = true) && it.division == activeTab
                }
                if (dbMatches.isNotEmpty()) {
                    dbMatches
                } else {
                    val templates = DefaultData.getTeamsForCountry(selectedCountry).filter { it.division == activeTab }
                    templates.map { t ->
                        val globalId = GlobalFootballSystem.getGlobalId(selectedCountry, t.name)
                        Team(
                            id = globalId,
                            name = t.name,
                            city = t.city,
                            state = t.state,
                            country = selectedCountry,
                            division = t.division,
                            rating = t.rating,
                            stadiumName = t.stadium,
                            logoUrl = DefaultData.getLogoForTeam(t.name, selectedCountry),
                            isPlayerControlled = false
                        )
                    }
                }
            }

            if (filteredTeams.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Icon(
                            Icons.Default.SportsSoccer,
                            contentDescription = null,
                            tint = Color.Gray,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            "Nenhum clube encontrado nesta divisão.",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = { viewModel.selectCountry(selectedCountry) },
                            colors = ButtonDefaults.buttonColors(containerColor = AccentLime)
                        ) {
                            Text("Recarregar Clubes", color = TurfDeepGreen, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredTeams) { team ->
                        val isSelected = selectedTeamId == team.id
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.selectTeamForNewGame(team.id) }
                                .border(
                                    width = if (isSelected) 2.dp else 0.dp,
                                    color = if (isSelected) AccentLime else Color.Transparent,
                                    shape = RoundedCornerShape(12.dp)
                                ),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) TurfDeepGreen else CardSurfaceDark
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Emblem placeholder or Url image
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (team.logoUrl != null) {
                                        val resolvedLogo = remember(team.logoUrl) { resolveLogoUrl(team.logoUrl) }
                                        Image(
                                            painter = rememberAsyncImagePainter(resolvedLogo),
                                            contentDescription = team.name,
                                            modifier = Modifier.size(36.dp)
                                        )
                                    } else {
                                        Icon(
                                            Icons.Default.SportsSoccer,
                                            contentDescription = null,
                                            tint = AccentLime,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(16.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = team.name,
                                        color = MaterialTheme.colorScheme.onBackground,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp
                                    )
                                    Text(
                                        text = "${team.city} - ${team.state}",
                                        color = Color.Gray,
                                        fontSize = 12.sp
                                    )
                                }

                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "FORÇA",
                                        color = Color.Gray,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "${team.rating}",
                                        color = AccentGold,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Bottom CTA
            Button(
                onClick = {
                    selectedTeamId?.let { id ->
                        viewModel.startNewGame(nameInput.trim().ifEmpty { "Técnico" }, id)
                    }
                },
                enabled = selectedTeamId != null && nameInput.isNotEmpty() && !isStartingNewGame,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .testTag("start_career_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentLime,
                    contentColor = TurfDeepGreen
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (isStartingNewGame) {
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = TurfDeepGreen,
                            strokeWidth = 2.5.dp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("INICIANDO CARREIRA...", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                } else {
                    Text("INICIAR CARREIRA COMO TÉCNICO", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }

            if (isStartingNewGame) {
                Dialog(onDismissRequest = {}) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = CardSurfaceDark),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(color = AccentLime)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                "Criando sua carreira...",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                "Gerando elencos e calendário da temporada",
                                color = Color.Gray,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
