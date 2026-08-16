package com.example.ui.screens

import com.example.ui.viewmodel.*

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.RetroPlayerCard
import com.example.ui.theme.*
import com.example.ui.viewmodel.PlayerListUiState
import com.example.ui.viewmodel.PlayerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerListScreen(
    viewModel: PlayerViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedPosition by viewModel.selectedPosition.collectAsStateWithLifecycle()

    val positions = listOf("TODOS", "GOL", "ZAG", "LAT", "VOL", "MEI", "ATA")

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(NeonMidnightBg)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Title & Header Action Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "ELENCO ROOM (RETRÔ)",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = "Jogadores persistidos no banco de dados local",
                    color = Color.LightGray,
                    fontSize = 12.sp
                )
            }
        }

        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { viewModel.onSearchQueryChanged(it) },
            placeholder = { Text("Buscar por nome ou nacionalidade...", color = Color.Gray, fontSize = 13.sp) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = AccentLime) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { viewModel.onSearchQueryChanged("") }) {
                        Icon(Icons.Default.Clear, contentDescription = "Limpar busca", tint = Color.Gray)
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("player_search_input"),
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AccentLime,
                unfocusedBorderColor = Color.Gray.copy(alpha = 0.4f),
                focusedContainerColor = CardSurfaceDark,
                unfocusedContainerColor = CardSurfaceDark,
                cursorColor = AccentLime
            )
        )

        // Filter Position Chips Row
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(positions) { pos ->
                val isSelected = (pos == "TODOS" && selectedPosition == null) || (pos == selectedPosition)
                FilterChip(
                    selected = isSelected,
                    onClick = {
                        if (pos == "TODOS") {
                            viewModel.onPositionSelected(null)
                        } else {
                            viewModel.onPositionSelected(if (isSelected) null else pos)
                        }
                    },
                    label = {
                        Text(
                            text = pos,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) Color.Black else Color.White
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AccentLime,
                        containerColor = CardSurfaceDark
                    ),
                    modifier = Modifier.testTag("filter_chip_$pos")
                )
            }
        }

        // Content Area: LazyColumn with Players
        when (val state = uiState) {
            is PlayerListUiState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = AccentLime)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Carregando jogadores do Room...", color = Color.LightGray, fontSize = 12.sp)
                    }
                }
            }

            is PlayerListUiState.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Erro: ${state.message}",
                        color = Color.Red,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            is PlayerListUiState.Success -> {
                if (state.filteredPlayers.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.PersonOff,
                                contentDescription = null,
                                tint = Color.Gray,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Nenhum jogador encontrado com os filtros atuais.",
                                color = Color.Gray,
                                fontSize = 14.sp
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .testTag("player_list_lazy_column"),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(bottom = 16.dp)
                    ) {
                        items(
                            items = state.filteredPlayers,
                            key = { player -> player.id }
                        ) { player ->
                            RetroPlayerCard(
                                player = player
                            )
                        }
                    }
                }
            }
        }
    }
}
