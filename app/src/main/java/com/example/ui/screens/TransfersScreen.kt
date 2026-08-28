package com.example.ui.screens

import com.example.ui.components.finances.*

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.*
import com.example.ui.components.transfers.PurchaseNegotiationDialog
import com.example.ui.theme.*
import com.example.ui.viewmodel.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun MarketTab(viewModel: GameViewModel) {
    val allPlayers by viewModel.allPlayers.collectAsStateWithLifecycle()
    val allTeams by viewModel.allTeams.collectAsStateWithLifecycle()
    val save by viewModel.gameSave.collectAsStateWithLifecycle()

    val searchPos by viewModel.transferSearchPos.collectAsStateWithLifecycle()
    val searchMinForce by viewModel.transferSearchMinForce.collectAsStateWithLifecycle()
    val searchMaxAge by viewModel.transferSearchMaxAge.collectAsStateWithLifecycle()
    val searchMaxPrice by viewModel.transferSearchMaxPrice.collectAsStateWithLifecycle()
    val searchSortBy by viewModel.transferSearchSortBy.collectAsStateWithLifecycle()
    val incomingOffers by viewModel.incomingOffers.collectAsStateWithLifecycle()

    var searchQuery by remember { mutableStateOf("") }
    var selectedPlayerForPurchase by remember { mutableStateOf<Player?>(null) }
    var selectedPlayerForScouting by remember { mutableStateOf<Player?>(null) }
    var currentSubTab by remember { mutableStateOf("MERCADO") } // "MERCADO", "OLHEIRO", "STAFF"

    val isWindowOpen = save?.let { GameCalendar.isTransferWindowOpen(it.currentSeason, it.currentWeek) } ?: false
    val currentDateStr = save?.let { GameCalendar.getLongFormattedDate(it.currentSeason, it.currentWeek) } ?: ""

    var availablePlayers by remember { mutableStateOf<List<Player>>(emptyList()) }
    LaunchedEffect(
        allPlayers,
        save?.playerTeamId,
        searchQuery,
        searchPos,
        searchMinForce,
        searchMaxAge,
        searchMaxPrice,
        searchSortBy
    ) {
        availablePlayers = withContext(Dispatchers.Default) {
            allPlayers.filter { player ->
                player.isTransferMarketCandidateFor(save?.playerTeamId) &&
                (searchQuery.isBlank() || player.name.contains(searchQuery, ignoreCase = true)) &&
                (searchPos == "TODOS" || player.position == searchPos) &&
                player.force >= searchMinForce &&
                player.age <= searchMaxAge &&
                (searchMaxPrice >= 500_000_000L || viewModel.getDynamicPlayerPrice(player) <= searchMaxPrice)
            }.let { filtered ->
                when (searchSortBy) {
                    "FORCA_DESC" -> filtered.sortedByDescending { it.force }
                    "FORCA_ASC" -> filtered.sortedBy { it.force }
                    "IDADE_ASC" -> filtered.sortedBy { it.age }
                    "IDADE_DESC" -> filtered.sortedByDescending { it.age }
                    "NOME" -> filtered.sortedBy { it.name }
                    "VALOR_ASC" -> filtered.sortedBy { viewModel.getDynamicPlayerPrice(it) }
                    "VALOR_DESC" -> filtered.sortedByDescending { viewModel.getDynamicPlayerPrice(it) }
                    else -> filtered
                }
            }.take(80)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(CardSurfaceDark)
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val subTabs = listOf(
                "MERCADO" to "MERCADO",
                "OLHEIRO" to "OLHEIRO",
                "STAFF" to "STAFF"
            )
            subTabs.forEach { tab ->
                val isSelected = currentSubTab == tab.first
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { currentSubTab = tab.first },
                    colors = CardDefaults.cardColors(containerColor = if (isSelected) AccentGold else Color.White.copy(alpha = 0.05f)),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Box(modifier = Modifier.padding(8.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(
                            text = tab.second,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            color = if (isSelected) TurfDeepGreen else Color.LightGray
                        )
                    }
                }
            }
        }

        if (currentSubTab == "MERCADO") {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("MERCADO DE ATLETAS", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Black, fontSize = 16.sp)
                    Text("Negocie contratações dinâmicas de jogadores sob as leis da IA de mercado", color = Color.Gray, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(currentDateStr, color = AccentGold, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        Surface(
                            color = if (isWindowOpen) TurfDeepGreen else Color.Red.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = if (isWindowOpen) "JANELA ABERTA 🟢" else "JANELA FECHADA 🔴",
                                color = if (isWindowOpen) AccentLime else Color.Red,
                                fontWeight = FontWeight.Black,
                                fontSize = 10.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                if (save?.activeNewsTitle != null) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color.Red.copy(alpha = 0.1f)),
                            border = BorderStroke(1.dp, Color.Red),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Warning, contentDescription = null, tint = Color.Red, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = save?.activeNewsTitle ?: "",
                                        color = Color.White,
                                        fontWeight = FontWeight.Black,
                                        fontSize = 12.sp
                                    )
                                }
                                Text(
                                    text = save?.activeNewsDesc ?: "",
                                    color = Color.LightGray,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }

                if (isWindowOpen && incomingOffers.isNotEmpty()) {
                    item {
                        Text("📩 PROPOSTAS RECEBIDAS PELO SEU ELENCO (${incomingOffers.size})", color = AccentLime, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                    items(incomingOffers, key = { it.id }) { offer ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, AccentLime.copy(alpha = 0.3f), RoundedCornerShape(8.dp)),
                            colors = CardDefaults.cardColors(containerColor = CardSurfaceDark),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        color = if (offer.offerType == "EMPRESTIMO") TurfDeepGreen else AccentGold.copy(alpha = 0.2f),
                                        shape = RoundedCornerShape(3.dp)
                                    ) {
                                        Text(
                                            text = offer.offerType,
                                            color = if (offer.offerType == "EMPRESTIMO") AccentLime else AccentGold,
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.Black,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                        )
                                    }
                                    Text("De: ${offer.buyerTeamName}", color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(offer.player.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("${offer.player.position} • FOR ${offer.player.force} • ${offer.player.age} anos", color = Color.LightGray, fontSize = 11.sp)
                                Spacer(modifier = Modifier.height(6.dp))

                                val label = if (offer.offerType == "EMPRESTIMO") "Taxa / Período:" else "Valor da Proposta:"
                                val valueStr = if (offer.offerType == "EMPRESTIMO") "R$ %,d (%d sem.)".format(offer.price, offer.durationWeeks) else "R$ %,d".format(offer.price)

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(label, color = Color.Gray, fontSize = 9.sp)
                                        Text(valueStr, color = AccentLime, fontSize = 13.sp, fontWeight = FontWeight.Black)
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Button(
                                            onClick = { viewModel.declineIncomingOffer(offer) },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color.Red.copy(alpha = 0.2f), contentColor = Color.Red),
                                            shape = RoundedCornerShape(4.dp),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                            modifier = Modifier.height(28.dp)
                                        ) {
                                            Text("RECUSAR", fontSize = 9.sp, fontWeight = FontWeight.Black)
                                        }
                                        Button(
                                            onClick = { viewModel.acceptIncomingOffer(offer) },
                                            colors = ButtonDefaults.buttonColors(containerColor = AccentLime, contentColor = TurfDeepGreen),
                                            shape = RoundedCornerShape(4.dp),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                            modifier = Modifier.height(28.dp)
                                        ) {
                                            Text("ACEITAR", fontSize = 9.sp, fontWeight = FontWeight.Black)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    var filtersExpanded by remember { mutableStateOf(false) }
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = CardSurfaceDark)
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Filtros de Busca", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                TextButton(onClick = { filtersExpanded = !filtersExpanded }) {
                                    Text(if (filtersExpanded) "RECOLHER" else "EXPANDIR", color = AccentGold, fontSize = 11.sp)
                                }
                            }

                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = { Text("Buscar por nome do atleta...", color = Color.Gray, fontSize = 11.sp) },
                                singleLine = true,
                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = AccentGold, modifier = Modifier.size(18.dp)) },
                                trailingIcon = {
                                    if (searchQuery.isNotEmpty()) {
                                        IconButton(onClick = { searchQuery = "" }) {
                                            Icon(Icons.Default.Clear, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(18.dp))
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = AccentGold,
                                    unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                                    focusedContainerColor = CardSurfaceDark,
                                    unfocusedContainerColor = CardSurfaceDark,
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                ),
                                shape = RoundedCornerShape(8.dp)
                            )

                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                val positions = listOf("TODOS", "GOL", "ZAG", "LAT", "VOL", "MEI", "ATA")
                                items(positions) { pos ->
                                    val isSelected = searchPos == pos
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = { viewModel.setTransferFilters(pos, searchMinForce, searchMaxAge, searchMaxPrice, searchSortBy) },
                                        label = { Text(pos, fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = AccentLime,
                                            selectedLabelColor = TurfDeepGreen,
                                            containerColor = Color.White.copy(alpha = 0.05f),
                                            labelColor = Color.LightGray
                                        )
                                    )
                                }
                            }

                            if (filtersExpanded) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Column {
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("Força Mínima (OVR):", color = Color.Gray, fontSize = 11.sp)
                                            Text("$searchMinForce", color = AccentGold, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                        }
                                        Slider(
                                            value = searchMinForce.toFloat(),
                                            onValueChange = { viewModel.setTransferFilters(searchPos, it.toInt(), searchMaxAge, searchMaxPrice, searchSortBy) },
                                            valueRange = 30f..99f,
                                            colors = SliderDefaults.colors(activeTrackColor = AccentLime, thumbColor = AccentGold),
                                            modifier = Modifier.height(24.dp)
                                        )
                                    }

                                    Column {
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("Idade Máxima:", color = Color.Gray, fontSize = 11.sp)
                                            Text("$searchMaxAge anos", color = AccentGold, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                        }
                                        Slider(
                                            value = searchMaxAge.toFloat(),
                                            onValueChange = { viewModel.setTransferFilters(searchPos, searchMinForce, it.toInt(), searchMaxPrice, searchSortBy) },
                                            valueRange = 15f..40f,
                                            colors = SliderDefaults.colors(activeTrackColor = AccentLime, thumbColor = AccentGold),
                                            modifier = Modifier.height(24.dp)
                                        )
                                    }

                                    Column {
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("Preço Máximo:", color = Color.Gray, fontSize = 11.sp)
                                            val priceLabel = if (searchMaxPrice >= 500_000_000L) "Sem Limite" else "R$ %,d".format(searchMaxPrice)
                                            Text(priceLabel, color = AccentLime, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                        }
                                        Slider(
                                            value = searchMaxPrice.toFloat().coerceIn(1_000_000f, 500_000_000f),
                                            onValueChange = { viewModel.setTransferFilters(searchPos, searchMinForce, searchMaxAge, it.toLong(), searchSortBy) },
                                            valueRange = 1_000_000f..500_000_000f,
                                            colors = SliderDefaults.colors(activeTrackColor = AccentLime, thumbColor = AccentGold),
                                            modifier = Modifier.height(24.dp)
                                        )
                                    }

                                    Column {
                                        Text("Ordenar por:", color = Color.Gray, fontSize = 11.sp)
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            val sortOptions = listOf("FORCA_DESC" to "Força (Dec)", "VALOR_ASC" to "Preço (Cres)", "VALOR_DESC" to "Preço (Dec)", "IDADE_ASC" to "Idade (Cres)")
                                            sortOptions.forEach { opt ->
                                                val isSelected = searchSortBy == opt.first
                                                Card(
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .clickable { viewModel.setTransferFilters(searchPos, searchMinForce, searchMaxAge, searchMaxPrice, opt.first) },
                                                    colors = CardDefaults.cardColors(containerColor = if (isSelected) AccentLime else Color.White.copy(alpha = 0.05f))
                                                ) {
                                                    Box(modifier = Modifier.padding(6.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                                                        Text(opt.second, fontSize = 8.sp, fontWeight = FontWeight.Bold, color = if (isSelected) TurfDeepGreen else Color.LightGray)
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

                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = CardSurfaceDark.copy(alpha = 0.6f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("JOGADOR / IDADE", color = Color.Gray, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.5f))
                            Text("FOR", color = Color.Gray, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.4f), textAlign = TextAlign.Center)
                            Text("VALOR", color = Color.Gray, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.0f), textAlign = TextAlign.Center)
                            Text("AÇÃO", color = Color.Gray, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.0f), textAlign = TextAlign.Center)
                        }
                    }
                }

                if (availablePlayers.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = CardSurfaceDark)
                        ) {
                            Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                                Text("Nenhum jogador encontrado com os filtros atuais.", color = Color.Gray, fontSize = 12.sp)
                            }
                        }
                    }
                } else {
                    items(availablePlayers, key = { it.id }) { player ->
                        val club = allTeams.find { it.id == player.teamId } ?: GlobalFootballSystem.getTeamByGlobalId(player.teamId)
                        val isGlobalReveal = save?.globalScoutRevealWeeksRemaining ?: 0 > 0
                        val scoutedLevel = player.scoutedLevel
                        val observedForce = player.getObservedForce(isGlobalReveal, player.teamId == save?.playerTeamId)

                        val dynamicPrice = viewModel.getDynamicPlayerPrice(player)

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { if (scoutedLevel >= 0) selectedPlayerForPurchase = player },
                            colors = CardDefaults.cardColors(containerColor = CardSurfaceDark),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1.5f)) {
                                    Text(player.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1)
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(player.position, color = AccentLime, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("•", color = Color.Gray, fontSize = 10.sp)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("${player.age}a", color = Color.LightGray, fontSize = 10.sp)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("•", color = Color.Gray, fontSize = 10.sp)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(club?.name ?: "Sem Clube", color = Color.Gray, fontSize = 10.sp, maxLines = 1)
                                    }
                                }

                                Text(
                                    text = observedForce,
                                    color = AccentGold,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Black,
                                    modifier = Modifier.weight(0.4f),
                                    textAlign = TextAlign.Center
                                )

                                Text(
                                    text = "R$ %,d".format(dynamicPrice),
                                    color = AccentLime,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1.0f),
                                    textAlign = TextAlign.Center
                                )

                                Box(modifier = Modifier.weight(1.0f), contentAlignment = Alignment.Center) {
                                    if (scoutedLevel <= 0 && !isGlobalReveal) {
                                        if (scoutedLevel < 0) {
                                            Text("OBSERVANDO", color = AccentGold, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                        } else {
                                            Button(
                                                onClick = { selectedPlayerForScouting = player },
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                                colors = ButtonDefaults.buttonColors(containerColor = AccentGold, contentColor = TurfDeepGreen),
                                                shape = RoundedCornerShape(4.dp),
                                                modifier = Modifier.height(26.dp)
                                            ) {
                                                Text("OBSERVAR", fontSize = 9.sp, fontWeight = FontWeight.Black)
                                            }
                                        }
                                    } else {
                                        Button(
                                            onClick = { if (isWindowOpen) selectedPlayerForPurchase = player },
                                            enabled = isWindowOpen,
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = AccentLime,
                                                contentColor = TurfDeepGreen,
                                                disabledContainerColor = Color.White.copy(alpha = 0.08f),
                                                disabledContentColor = Color.Gray
                                            ),
                                            shape = RoundedCornerShape(4.dp),
                                            modifier = Modifier.height(26.dp)
                                        ) {
                                            Text(if (isWindowOpen) "NEGOCIAR" else "FECHADA", fontSize = 9.sp, fontWeight = FontWeight.Black)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else if (currentSubTab == "OLHEIRO") {
            val watchlist by viewModel.watchlist.collectAsStateWithLifecycle()
            val watchedPlayers = allPlayers.filter { it.id in watchlist }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("OLHEIRO (ATLETAS MONITORADOS)", color = Color.White, fontWeight = FontWeight.Black, fontSize = 16.sp)
                    Text("Acompanhe em tempo real o desenvolvimento dos atletas sob monitoramento, mesmo que estejam em outros times.", color = Color.Gray, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(16.dp))
                }

                if (watchedPlayers.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.02f)),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
                        ) {
                            Column(
                                modifier = Modifier
                                    .padding(24.dp)
                                    .fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(Icons.Default.VisibilityOff, contentDescription = null, modifier = Modifier.size(48.dp), tint = Color.Gray)
                                Spacer(modifier = Modifier.height(12.dp))
                                Text("Nenhum atleta monitorado.", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Abra um atleta no mercado e clique em 'FICAR DE OLHO' para começar a monitorar o seu desenvolvimento.", color = Color.Gray, fontSize = 11.sp, textAlign = TextAlign.Center)
                            }
                        }
                    }
                } else {
                    items(watchedPlayers, key = { it.id }) { player ->
                        val currentTeam = allTeams.find { it.id == player.teamId }
                        val isGlobalReveal = save?.globalScoutRevealWeeksRemaining ?: 0 > 0
                        val observedForce = player.getObservedForce(isGlobalReveal, player.teamId == save?.playerTeamId)
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedPlayerForPurchase = player },
                            colors = CardDefaults.cardColors(containerColor = CardSurfaceDark),
                            border = BorderStroke(1.dp, AccentGold.copy(alpha = 0.2f))
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .background(AccentLime.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                                        .border(0.5.dp, AccentLime, RoundedCornerShape(8.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(player.position, color = AccentLime, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(player.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Text(
                                        text = "Time: ${currentTeam?.name ?: "Sem Clube"} • Força: $observedForce • Idade: ${player.age} anos",
                                        color = Color.LightGray,
                                        fontSize = 11.sp
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    val dynamicPrice = viewModel.getDynamicPlayerPrice(player)
                                    Text("R$ %,d".format(dynamicPrice), color = AccentGold, fontWeight = FontWeight.Black, fontSize = 12.sp)
                                    Text("Monitorado", color = Color(0xFF8E24AA), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        } else {
            Box(modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)) {
                StaffPanel(viewModel, save)
            }
        }
    }

    selectedPlayerForScouting?.let { p ->
        ScoutSelectionDialog(
            player = p,
            viewModel = viewModel,
            onDismiss = { selectedPlayerForScouting = null }
        )
    }

    selectedPlayerForPurchase?.let { p ->
        PurchaseNegotiationDialog(
            player = p,
            viewModel = viewModel,
            onDismiss = { selectedPlayerForPurchase = null }
        )
    }
}
