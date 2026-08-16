package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.PersonOff
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.example.ui.viewmodel.GameViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerListScreen(
    viewModel: GameViewModel,
    modifier: Modifier = Modifier
) {
    val allPlayers by viewModel.allPlayers.collectAsStateWithLifecycle()
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedPosition by rememberSaveable { mutableStateOf<String?>(null) }

    val filteredPlayers = remember(allPlayers, searchQuery, selectedPosition) {
        allPlayers.filter { player ->
            val matchesQuery = searchQuery.isBlank() ||
                player.name.contains(searchQuery, ignoreCase = true) ||
                player.nationality.contains(searchQuery, ignoreCase = true)
            val matchesPosition = selectedPosition == null ||
                player.position.equals(selectedPosition, ignoreCase = true)
            matchesQuery && matchesPosition
        }
    }

    val positions = listOf("TODOS", "GOL", "ZAG", "LAT", "VOL", "MEI", "ATA")

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(NeonMidnightBg)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
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
                    text = "Jogadores persistidos no save ativo",
                    color = Color.LightGray,
                    fontSize = 12.sp
                )
            }
        }

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = {
                Text(
                    "Buscar por nome ou nacionalidade...",
                    color = Color.Gray,
                    fontSize = 13.sp
                )
            },
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = null, tint = AccentLime)
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
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

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(positions) { position ->
                val isSelected =
                    (position == "TODOS" && selectedPosition == null) || position == selectedPosition
                FilterChip(
                    selected = isSelected,
                    onClick = {
                        selectedPosition = when {
                            position == "TODOS" -> null
                            isSelected -> null
                            else -> position
                        }
                    },
                    label = {
                        Text(
                            text = position,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) Color.Black else Color.White
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AccentLime,
                        containerColor = CardSurfaceDark
                    ),
                    modifier = Modifier.testTag("filter_chip_$position")
                )
            }
        }

        if (filteredPlayers.isEmpty()) {
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
                        text = if (allPlayers.isEmpty()) {
                            "Nenhum jogador persistido no save ativo."
                        } else {
                            "Nenhum jogador encontrado com os filtros atuais."
                        },
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
                    items = filteredPlayers,
                    key = { player -> player.id }
                ) { player ->
                    RetroPlayerCard(player = player)
                }
            }
        }
    }
}
