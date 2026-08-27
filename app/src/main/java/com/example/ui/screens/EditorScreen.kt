package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.GlobalFootballSystem
import com.example.data.Player
import com.example.data.Team
import com.example.ui.components.editor.*
import com.example.ui.theme.*
import com.example.ui.viewmodel.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeamAndPlayerEditorScreen(
    viewModel: GameViewModel,
    onBack: () -> Unit
) {
    // Ensure save slot is active for accessing database
    LaunchedEffect(Unit) {
        viewModel.ensureSaveActiveForEditor()
    }

    val allTeams by viewModel.allTeams.collectAsStateWithLifecycle()
    val allPlayers by viewModel.allPlayers.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableIntStateOf(0) } // 0: Times, 1: Jogadores

    // Team Editor Filters & Dialog State
    var teamSearchQuery by remember { mutableStateOf("") }
    var selectedTeamCountryFilter by remember { mutableStateOf("Todos") }
    var selectedTeamDivisionFilter by remember { mutableIntStateOf(0) } // 0 = Todos
    var editingTeam by remember { mutableStateOf<Team?>(null) }
    var isCreatingTeam by remember { mutableStateOf(false) }
    var teamToDelete by remember { mutableStateOf<Team?>(null) }

    // Player Editor Filters & Dialog State
    var playerSearchQuery by remember { mutableStateOf("") }
    var selectedPlayerTeamFilter by remember { mutableStateOf<Long?>(null) } // null = Todos
    var selectedPositionFilter by remember { mutableStateOf("Todas") }
    var editingPlayer by remember { mutableStateOf<Player?>(null) }
    var isCreatingPlayer by remember { mutableStateOf(false) }
    var playerToDelete by remember { mutableStateOf<Player?>(null) }
    var playerToTransfer by remember { mutableStateOf<Player?>(null) }

    val countries = remember(allTeams) {
        listOf("Todos") + (GlobalFootballSystem.keys + allTeams.map { it.country }).distinct().sorted()
    }

    LaunchedEffect(selectedPlayerTeamFilter) {
        selectedPlayerTeamFilter?.let { teamId ->
            viewModel.ensureRosterForTeam(teamId)
        }
    }

    LaunchedEffect(editingTeam) {
        editingTeam?.let { team ->
            if (team.id != 0L) {
                viewModel.ensureRosterForTeam(team.id)
            }
        }
    }

    val teamMap = remember(allTeams) {
        allTeams.associateBy { it.id }
    }

    val positions = listOf("Todas", "GOL", "ZAG", "LAT", "VOL", "MEI", "ATA")

    val filteredTeams = remember(allTeams, teamSearchQuery, selectedTeamCountryFilter, selectedTeamDivisionFilter) {
        var seq = allTeams.asSequence()
        if (selectedTeamCountryFilter != "Todos") {
            seq = seq.filter { it.country.equals(selectedTeamCountryFilter, ignoreCase = true) }
        }
        if (selectedTeamDivisionFilter != 0) {
            seq = seq.filter { it.division == selectedTeamDivisionFilter }
        }
        if (teamSearchQuery.isNotBlank()) {
            val q = teamSearchQuery.trim()
            seq = seq.filter {
                it.name.contains(q, ignoreCase = true) || it.city.contains(q, ignoreCase = true)
            }
        }
        seq.toList()
    }

    val filteredPlayers = remember(allPlayers, selectedTab, playerSearchQuery, selectedPlayerTeamFilter, selectedPositionFilter) {
        if (selectedTab != 1) {
            emptyList()
        } else {
            var seq = allPlayers.asSequence()
            if (selectedPlayerTeamFilter != null) {
                seq = seq.filter { it.teamId == selectedPlayerTeamFilter }
            }
            if (selectedPositionFilter != "Todas") {
                seq = seq.filter { it.position.equals(selectedPositionFilter, ignoreCase = true) }
            }
            if (playerSearchQuery.isNotBlank()) {
                val q = playerSearchQuery.trim()
                seq = seq.filter { it.name.contains(q, ignoreCase = true) }
            }
            if (selectedPlayerTeamFilter == null && playerSearchQuery.isBlank()) {
                seq = seq.take(100)
            } else {
                seq = seq.take(300)
            }
            seq.toList()
        }
    }

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
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("back_from_editor_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Voltar",
                            tint = Color.White
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "EDITOR TÉCNICO",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White
                        )
                        Text(
                            text = "Edite times, nomes, forças, salários e elencos",
                            fontSize = 11.sp,
                            color = AccentLime,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Tab Switcher (Times vs Jogadores)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CardSurfaceDark, RoundedCornerShape(12.dp))
                        .padding(4.dp)
                ) {
                    TabButton(
                        text = "🛡️ TIMES (${filteredTeams.size})",
                        isSelected = selectedTab == 0,
                        modifier = Modifier.weight(1f),
                        onClick = { selectedTab = 0 }
                    )
                    TabButton(
                        text = "⚽ JOGADORES (${if (selectedPlayerTeamFilter != null) filteredPlayers.size else allPlayers.size})",
                        isSelected = selectedTab == 1,
                        modifier = Modifier.weight(1f),
                        onClick = { selectedTab = 1 }
                    )
                }
            }
        },
        containerColor = TurfPitchDark
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (selectedTab == 0) {
                // TIMES TAB
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp)
                ) {
                    Spacer(modifier = Modifier.height(8.dp))

                    // Search & Action Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = teamSearchQuery,
                            onValueChange = { teamSearchQuery = it },
                            placeholder = { Text("Buscar time ou cidade...", color = Color.Gray, fontSize = 13.sp) },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = AccentLime) },
                            trailingIcon = {
                                if (teamSearchQuery.isNotEmpty()) {
                                    IconButton(onClick = { teamSearchQuery = "" }) {
                                        Icon(Icons.Default.Clear, contentDescription = null, tint = Color.Gray)
                                    }
                                }
                            },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = CardSurfaceDark,
                                unfocusedContainerColor = CardSurfaceDark,
                                focusedBorderColor = AccentLime,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            )
                        )

                        Button(
                            onClick = {
                                editingTeam = Team(
                                    id = 0L,
                                    name = "",
                                    city = "São Paulo",
                                    state = "SP",
                                    country = if (selectedTeamCountryFilter != "Todos") selectedTeamCountryFilter else "Brasil",
                                    division = 1,
                                    rating = 60,
                                    stadiumName = "Estádio Municipal"
                                )
                                isCreatingTeam = true
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = AccentLime, contentColor = TurfPitchDark),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.height(52.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("NOVO", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Country Filter Chips
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(countries) { country ->
                            FilterChip(
                                selected = selectedTeamCountryFilter == country,
                                onClick = { selectedTeamCountryFilter = country },
                                label = { Text(country, fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = AccentLime,
                                    selectedLabelColor = TurfPitchDark,
                                    containerColor = CardSurfaceDark,
                                    labelColor = Color.White
                                )
                            )
                        }
                    }

                    // Division Filter Chips
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        listOf(0 to "Todas Div.", 1 to "Série A", 2 to "Série B", 3 to "Série C", 4 to "Série D").forEach { (div, label) ->
                            FilterChip(
                                selected = selectedTeamDivisionFilter == div,
                                onClick = { selectedTeamDivisionFilter = div },
                                label = { Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = AccentGold,
                                    selectedLabelColor = TurfPitchDark,
                                    containerColor = CardSurfaceDark,
                                    labelColor = Color.White
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Teams List
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 24.dp)
                    ) {
                        items(filteredTeams, key = { it.id }) { team ->
                            TeamEditorCard(
                                team = team,
                                onEdit = { editingTeam = team; isCreatingTeam = false },
                                onDelete = { teamToDelete = team },
                                onViewSquad = {
                                    selectedPlayerTeamFilter = team.id
                                    selectedTab = 1
                                }
                            )
                        }
                    }
                }
            } else {
                // JOGADORES TAB
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp)
                ) {
                    Spacer(modifier = Modifier.height(8.dp))

                    // Selected Team Banner Header (if filtered by a specific team)
                    val currentSelectedTeam = remember(selectedPlayerTeamFilter, allTeams) {
                        if (selectedPlayerTeamFilter != null) allTeams.find { it.id == selectedPlayerTeamFilter } else null
                    }

                    if (currentSelectedTeam != null) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                            colors = CardDefaults.cardColors(containerColor = CardSurfaceDark),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, AccentLime)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    TeamBadge(
                                        teamName = currentSelectedTeam.name,
                                        logoUrl = currentSelectedTeam.logoUrl,
                                        size = 46.dp,
                                        colorHex = currentSelectedTeam.colorHex
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = currentSelectedTeam.name,
                                                fontSize = 18.sp,
                                                fontWeight = FontWeight.Black,
                                                color = Color.White,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f, fill = false)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Surface(
                                                color = AccentGold.copy(alpha = 0.2f),
                                                shape = RoundedCornerShape(6.dp)
                                            ) {
                                                Text(
                                                    text = "Série ${('A' + currentSelectedTeam.division - 1)}",
                                                    color = AccentGold,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                    maxLines = 1,
                                                    softWrap = false
                                                )
                                            }
                                        }
                                        Text(
                                            text = "Elenco (${filteredPlayers.size} jogadores) • ${currentSelectedTeam.city}, ${currentSelectedTeam.state}",
                                            fontSize = 11.sp,
                                            color = AccentLime,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                    IconButton(onClick = { selectedPlayerTeamFilter = null }) {
                                        Icon(Icons.Default.Close, contentDescription = "Limpar Seleção", tint = Color.Gray)
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            editingPlayer = Player(
                                                id = 0L,
                                                teamId = currentSelectedTeam.id,
                                                name = "",
                                                age = 22,
                                                nationality = "Brasil",
                                                position = "MEI",
                                                force = 65,
                                                salary = 15000L,
                                                finishing = 65,
                                                passing = 65,
                                                pace = 65,
                                                strength = 65,
                                                vision = 65,
                                                defense = 65
                                            )
                                            isCreatingPlayer = true
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = AccentLime, contentColor = TurfPitchDark),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("CRIAR JOGADOR", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            editingTeam = currentSelectedTeam
                                            isCreatingTeam = false
                                        },
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f)),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("EDITAR TIME", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }

                    // Search & Action Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = playerSearchQuery,
                            onValueChange = { playerSearchQuery = it },
                            placeholder = { Text("Buscar nome do jogador...", color = Color.Gray, fontSize = 13.sp) },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = AccentLime) },
                            trailingIcon = {
                                if (playerSearchQuery.isNotEmpty()) {
                                    IconButton(onClick = { playerSearchQuery = "" }) {
                                        Icon(Icons.Default.Clear, contentDescription = null, tint = Color.Gray)
                                    }
                                }
                            },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = CardSurfaceDark,
                                unfocusedContainerColor = CardSurfaceDark,
                                focusedBorderColor = AccentLime,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            )
                        )

                        if (currentSelectedTeam == null) {
                            Button(
                                onClick = {
                                    val targetTeamId = selectedPlayerTeamFilter ?: allTeams.firstOrNull()?.id ?: 1L
                                    editingPlayer = Player(
                                        id = 0L,
                                        teamId = targetTeamId,
                                        name = "",
                                        age = 22,
                                        nationality = "Brasil",
                                        position = "MEI",
                                        force = 65,
                                        salary = 15000L,
                                        finishing = 65,
                                        passing = 65,
                                        pace = 65,
                                        strength = 65,
                                        vision = 65,
                                        defense = 65
                                    )
                                    isCreatingPlayer = true
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = AccentLime, contentColor = TurfPitchDark),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.height(52.dp)
                            ) {
                                Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("NOVO", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Team Filter Bar
                    if (currentSelectedTeam == null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Exibindo: Todos os Jogadores",
                                fontSize = 12.sp,
                                color = AccentLime,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Position Chips
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        items(positions) { pos ->
                            FilterChip(
                                selected = selectedPositionFilter == pos,
                                onClick = { selectedPositionFilter = pos },
                                label = { Text(pos, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = getPositionBadgeColor(pos),
                                    selectedLabelColor = Color.White,
                                    containerColor = CardSurfaceDark,
                                    labelColor = Color.White
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Players List
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 24.dp)
                    ) {
                        items(filteredPlayers, key = { it.id }) { player ->
                            val playerTeam = teamMap[player.teamId]
                            PlayerEditorCard(
                                player = player,
                                teamName = playerTeam?.name ?: "Sem Time",
                                onEdit = { editingPlayer = player; isCreatingPlayer = false },
                                onTransfer = { playerToTransfer = player },
                                onDelete = { playerToDelete = player }
                            )
                        }
                    }
                }
            }
        }
    }

    // EDIT/CREATE TEAM DIALOG
    editingTeam?.let { team ->
        EditTeamDialog(
            team = team,
            isNew = isCreatingTeam,
            onDismiss = { editingTeam = null },
            onSave = { updatedTeam ->
                viewModel.saveTeamFromEditor(updatedTeam)
                editingTeam = null
            },
            onEditSquad = {
                selectedPlayerTeamFilter = team.id
                selectedTab = 1
            }
        )
    }

    // DELETE TEAM CONFIRMATION
    teamToDelete?.let { team ->
        AlertDialog(
            onDismissRequest = { teamToDelete = null },
            title = { Text("Excluir Time '${team.name}'?", color = Color.White, fontWeight = FontWeight.Bold) },
            text = { Text("Esta ação removerá o time e todos os seus jogadores do banco de dados. Confirma?", color = Color.White.copy(alpha = 0.8f)) },
            containerColor = NeonMidnightSurface,
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteTeamFromEditor(team.id)
                        teamToDelete = null
                    }
                ) {
                    Text("EXCLUIR", color = NeonRedAccent, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { teamToDelete = null }) {
                    Text("CANCELAR", color = Color.Gray)
                }
            }
        )
    }

    // EDIT/CREATE PLAYER DIALOG
    editingPlayer?.let { player ->
        EditPlayerDialog(
            player = player,
            allTeams = allTeams,
            isNew = isCreatingPlayer,
            onDismiss = { editingPlayer = null },
            onSave = { updatedPlayer ->
                viewModel.savePlayerFromEditor(updatedPlayer) { saved ->
                    if (saved) editingPlayer = null
                }
            }
        )
    }

    // DELETE PLAYER CONFIRMATION
    playerToDelete?.let { player ->
        AlertDialog(
            onDismissRequest = { playerToDelete = null },
            title = { Text("Excluir Jogador '${player.name}'?", color = Color.White, fontWeight = FontWeight.Bold) },
            text = { Text("Esta ação removerá o jogador permanentemente. Confirma?", color = Color.White.copy(alpha = 0.8f)) },
            containerColor = NeonMidnightSurface,
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deletePlayerFromEditor(player.id)
                        playerToDelete = null
                    }
                ) {
                    Text("EXCLUIR", color = NeonRedAccent, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { playerToDelete = null }) {
                    Text("CANCELAR", color = Color.Gray)
                }
            }
        )
    }

    // TRANSFER PLAYER DIALOG
    playerToTransfer?.let { player ->
        TransferPlayerDialog(
            player = player,
            allTeams = allTeams,
            onDismiss = { playerToTransfer = null },
            onConfirm = { newTeamId ->
                viewModel.transferPlayerFromEditor(player.id, newTeamId)
                playerToTransfer = null
            }
        )
    }
}
