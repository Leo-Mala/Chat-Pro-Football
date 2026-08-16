package com.example.ui.components.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.*
import com.example.ui.theme.*

data class DashboardNewsItem(
    val id: String,
    val title: String,
    val category: String, // "RESULTADOS", "TRANSFERÊNCIAS", "CLUBE"
    val description: String,
    val icon: ImageVector,
    val tag: String,
    val isHot: Boolean = false
)

@Composable
fun DashboardNewsFeedSection(
    save: GameSave,
    playerTeam: Team,
    allTeams: List<Team>,
    allFixtures: List<Fixture>,
    transactions: List<TransactionRecord>
) {
    var selectedFilter by remember { mutableStateOf("TODAS") }

    val newsList = remember(save, playerTeam, allTeams, allFixtures, transactions) {
        val items = mutableListOf<DashboardNewsItem>()

        // 1. Club specific news (if active)
        if (!save.activeNewsTitle.isNullOrBlank()) {
            items.add(
                DashboardNewsItem(
                    id = "club_news",
                    title = save.activeNewsTitle,
                    category = "CLUBE",
                    description = save.activeNewsDesc ?: "Atualização interna no clube.",
                    icon = Icons.Default.Campaign,
                    tag = "Notícia do Clube",
                    isHot = true
                )
            )
        }

        // 2. Round Results Summary
        val playedFixtures = allFixtures.filter { it.isPlayed }
        val maxWeek = playedFixtures.maxOfOrNull { it.week }

        if (maxWeek != null) {
            val roundFixtures = playedFixtures.filter { it.week == maxWeek }

            // Player team's fixture
            val playerFix = roundFixtures.find { it.homeTeamId == playerTeam.id || it.awayTeamId == playerTeam.id }
            if (playerFix != null) {
                val isHome = playerFix.homeTeamId == playerTeam.id
                val myScore = if (isHome) playerFix.homeScore ?: 0 else playerFix.awayScore ?: 0
                val oppScore = if (isHome) playerFix.awayScore ?: 0 else playerFix.homeScore ?: 0
                val oppId = if (isHome) playerFix.awayTeamId else playerFix.homeTeamId
                val oppName = allTeams.find { it.id == oppId }?.name ?: "Adversário"

                val title = when {
                    myScore > oppScore -> "VITÓRIA EXPRESSIVA DO ${playerTeam.name.uppercase()}!"
                    myScore < oppScore -> "TROPEÇO DO ${playerTeam.name.uppercase()} NA RODADA"
                    else -> "EMPATE EQUILIBRADO DO ${playerTeam.name.uppercase()}"
                }
                val desc = "${playerTeam.name} $myScore x $oppScore $oppName na ${maxWeek}ª rodada."

                items.add(
                    DashboardNewsItem(
                        id = "player_result_$maxWeek",
                        title = title,
                        category = "RESULTADOS",
                        description = desc,
                        icon = Icons.Default.SportsSoccer,
                        tag = "Rodada $maxWeek",
                        isHot = true
                    )
                )
            }

            // Top highlight match of the round
            val topMatch = roundFixtures.filter { it != playerFix }.maxByOrNull { (it.homeScore ?: 0) + (it.awayScore ?: 0) }
            if (topMatch != null) {
                val hName = allTeams.find { it.id == topMatch.homeTeamId }?.name ?: GlobalFootballSystem.getVirtualTeam(topMatch.homeTeamId).name
                val aName = allTeams.find { it.id == topMatch.awayTeamId }?.name ?: GlobalFootballSystem.getVirtualTeam(topMatch.awayTeamId).name
                val hG = topMatch.homeScore ?: 0
                val aG = topMatch.awayScore ?: 0

                items.add(
                    DashboardNewsItem(
                        id = "top_match_$maxWeek",
                        title = "DESTAQUE DA ${maxWeek}ª RODADA",
                        category = "RESULTADOS",
                        description = "$hName $hG x $aG $aName — confronto movimentado agitou os torcedores.",
                        icon = Icons.Default.SportsSoccer,
                        tag = "Rodada $maxWeek",
                        isHot = (hG + aG) >= 4
                    )
                )
            }

            // Round goals summary
            val totalGoals = roundFixtures.sumOf { (it.homeScore ?: 0) + (it.awayScore ?: 0) }
            items.add(
                DashboardNewsItem(
                    id = "round_summary_$maxWeek",
                    title = "BALANÇO DA ${maxWeek}ª RODADA",
                    category = "RESULTADOS",
                    description = "A ${maxWeek}ª rodada encerrou com $totalGoals gols anotados nos gramados.",
                    icon = Icons.Default.Assessment,
                    tag = "Rodada $maxWeek"
                )
            )
        } else {
            // Pre-season / Round 1 upcoming
            items.add(
                DashboardNewsItem(
                    id = "preseason_news",
                    title = "PRÉ-TEMPORADA E LARGADA DO CAMPEONATO",
                    category = "RESULTADOS",
                    description = "As equipes alinham a formação tática para a 1ª rodada. A expectativa é alta para a estreia!",
                    icon = Icons.Default.SportsSoccer,
                    tag = "Temporada ${save.currentSeason}",
                    isHot = true
                )
            )
        }

        // 3. Blockbuster Market Transfers
        val transferRecords = transactions.filter { it.type in listOf("COMPRA", "VENDA", "EMPRESTIMO_TOMAR") }
            .sortedByDescending { it.amount }

        if (transferRecords.isNotEmpty()) {
            val topTransfers = transferRecords.take(3)
            topTransfers.forEachIndexed { index, tx ->
                val isHighValue = tx.amount >= 3_000_000L
                val title = if (isHighValue) "TRANSFERÊNCIA BOMBÁSTICA NO MERCADO 💥" else "NEGOCIAÇÃO CONFIRMADA 📝"
                val valueFormatted = "R$ %,d".format(tx.amount)
                val desc = "${tx.description} — valor acertado de $valueFormatted."

                items.add(
                    DashboardNewsItem(
                        id = "tx_${tx.id}_$index",
                        title = title,
                        category = "TRANSFERÊNCIAS",
                        description = desc,
                        icon = Icons.Default.SwapHoriz,
                        tag = "Semana ${tx.week}",
                        isHot = isHighValue
                    )
                )
            }
        }

        // Supplementary market news
        val divName = when (playerTeam.division) {
            1 -> "Série A"
            2 -> "Série B"
            3 -> "Série C"
            else -> "Série D"
        }
        items.add(
            DashboardNewsItem(
                id = "market_gen_1",
                title = "JANELA DE TRANSFERÊNCIAS AQUECIDA 🔥",
                category = "TRANSFERÊNCIAS",
                description = "Clubes da $divName intensificam buscas por reforços e sondagens no mercado da bola.",
                icon = Icons.Default.Storefront,
                tag = "Mercado"
            )
        )

        items
    }

    val filteredNews = remember(newsList, selectedFilter) {
        when (selectedFilter) {
            "RESULTADOS" -> newsList.filter { it.category == "RESULTADOS" }
            "TRANSFERÊNCIAS" -> newsList.filter { it.category == "TRANSFERÊNCIAS" }
            else -> newsList
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .background(AccentGold.copy(alpha = 0.2f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Newspaper,
                        contentDescription = null,
                        tint = AccentGold,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Text(
                    text = "FEED DE NOTÍCIAS & MERCADO",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    letterSpacing = 1.sp
                )
            }

            Surface(
                color = AccentGold.copy(alpha = 0.15f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "${filteredNews.size} NOTÍCIAS",
                    color = AccentGold,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }

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
                // Filter Tabs
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf("TODAS", "RESULTADOS", "TRANSFERÊNCIAS").forEach { filter ->
                        val isSelected = selectedFilter == filter
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) TurfDeepGreen else Color.Transparent)
                                .clickable { selectedFilter = filter }
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = filter,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) AccentLime else Color.Gray
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                if (filteredNews.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Nenhuma notícia nesta categoria no momento.",
                            color = Color.Gray,
                            fontSize = 12.sp
                        )
                    }
                } else {
                    filteredNews.forEachIndexed { index, news ->
                        if (index > 0) {
                            HorizontalDivider(
                                color = Color.White.copy(alpha = 0.08f),
                                modifier = Modifier.padding(vertical = 10.dp)
                            )
                        }

                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    val (catBg, catTxt) = when (news.category) {
                                        "RESULTADOS" -> Pair(Color(0xFFE8F5E9), Color(0xFF2E7D32))
                                        "TRANSFERÊNCIAS" -> Pair(Color(0xFFFFF3E0), Color(0xFFE65100))
                                        else -> Pair(PolishPurpleContainer, PolishOnPurpleContainer)
                                    }

                                    Surface(
                                        color = catBg,
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = news.category,
                                            color = catTxt,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Black,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }

                                    Text(
                                        text = "• ${news.tag}",
                                        color = Color.Gray,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }

                                if (news.isHot) {
                                    Surface(
                                        color = Color(0xFFFFEBEE),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = "🔥 DESTAQUE",
                                            color = Color(0xFFC62828),
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Black,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = news.icon,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .size(20.dp)
                                        .padding(top = 2.dp)
                                )

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = news.title,
                                        color = MaterialTheme.colorScheme.onBackground,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        lineHeight = 16.sp
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = news.description,
                                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                                        fontSize = 11.sp,
                                        lineHeight = 15.sp
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
