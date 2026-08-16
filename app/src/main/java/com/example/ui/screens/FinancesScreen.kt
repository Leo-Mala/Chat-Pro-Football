package com.example.ui.screens

import com.example.data.GameCalendar
import com.example.ui.components.FinancialEvolutionChart
import com.example.ui.components.finances.*
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
fun FinanceTab(viewModel: GameViewModel) {
    val saveState by viewModel.gameSave.collectAsStateWithLifecycle()
    val roster by viewModel.playerRoster.collectAsStateWithLifecycle()
    val playerTeam by viewModel.playerTeam.collectAsStateWithLifecycle()

    val s = saveState
    if (s == null) return

    val totalWage = (roster.sumOf { it.salary } * 0.18).toLong()
    val expectedRevenue = if (s.sponsorWeeksRemaining > 0) {
        (300000.0 + (s.coachReputation * 15000.0)).toLong()
    } else {
        s.sponsorWeekly
    }

    val academyMaintenance = when (s.academyLevel) {
        2 -> 25000L
        3 -> 50000L
        else -> 10000L
    }
    val totalAcademyCost = academyMaintenance + s.academyWeeklyInvestment

    // Sócio-Torcedores calculations
    val socioRevenue = (s.socioTorcedoresCount * 45.0).toLong()

    // Bilheteria calculations
    val baseAttendanceRate = 0.4 + (s.coachReputation / 200.0)
    val priceFactor = (1.5 - ((s.ticketPrice - 30.0) * 0.012)).coerceIn(0.35, 1.5)
    val maxCap = maxOf(1000, s.stadiumCapacity)
    val minCap = minOf(1000, maxCap)
    val estAttendance = (s.stadiumCapacity * baseAttendanceRate * priceFactor).toInt().coerceIn(minCap, maxCap)
    val estTicketRevenue = (estAttendance * s.ticketPrice * 2.0).toLong()

    val division = playerTeam?.division ?: 1
    val overheadCost = when (division) {
        1 -> 500000L
        2 -> 150000L
        3 -> 50000L
        else -> 15000L
    }
    val cap = ((expectedRevenue + socioRevenue + (estTicketRevenue / 2)) * 0.65).toLong()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        var activeSubTab by remember { mutableStateOf("RECEITAS") }
        val finSubTabs = listOf(
            Pair("RECEITAS", "Receitas"),
            Pair("BALANCO", "Balanço & Folha"),
            Pair("ESTADIO", "Estádio & Banco")
        )

        // Sub-Tab Switcher
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(CardSurfaceDark, RoundedCornerShape(10.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            finSubTabs.forEach { (code, title) ->
                val isActive = activeSubTab == code
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isActive) TurfDeepGreen else Color.Transparent)
                        .clickable { activeSubTab = code }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        title,
                        fontSize = 11.sp,
                        color = if (isActive) AccentLime else Color.Gray,
                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        if (activeSubTab == "RECEITAS") {
            // Sponsorship Contract Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CardSurfaceDark),
                shape = RoundedCornerShape(12.dp)
            ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Handshake, contentDescription = null, tint = AccentGold, modifier = Modifier.size(20.dp))
                    Text("Contrato de Patrocínio Máster", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
                Spacer(modifier = Modifier.height(12.dp))

                if (s.sponsorWeeksRemaining > 0) {
                    // Active sponsor info
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(TurfDeepGreen.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                            .border(1.dp, TurfDeepGreen.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                            .padding(12.dp)
                    ) {
                        Column {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                  Text(s.sponsorName, color = AccentLime, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Surface(color = TurfDeepGreen, shape = RoundedCornerShape(4.dp)) {
                                    Text("ATIVO", color = AccentLime, fontWeight = FontWeight.Black, fontSize = 9.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Aporte Semanal:", color = Color.Gray, fontSize = 12.sp)
                                Text("R$ %,d".format(s.sponsorWeekly), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Vigência Restante:", color = Color.Gray, fontSize = 12.sp)
                                Text("${s.sponsorWeeksRemaining} semanas", color = AccentGold, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                } else {
                    // Choose a sponsor
                    Text("Escolha uma proposta comercial para assinar imediatamente:", color = Color.Gray, fontSize = 11.sp)
                    Spacer(modifier = Modifier.height(10.dp))

                    val baseSponsor = (300000.0 + (s.coachReputation * 15000.0)).toLong()
                    val offers = listOf(
                        Triple("Cervejaria \"Pilsen\"", Pair((baseSponsor * 0.9).toLong(), GameCalendar.WEEKS_PER_SEASON), 600000L),
                        Triple("Banco \"Econômico\"", Pair(baseSponsor, GameCalendar.WEEKS_PER_SEASON), 400000L),
                        Triple("Aero-Linhas \"Regional\"", Pair((baseSponsor * 0.8).toLong(), GameCalendar.WEEKS_PER_SEASON), 1000000L)
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        offers.forEach { (name, terms, bonus) ->
                            val weekly = terms.first
                            val weeks = terms.second
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Text("R$ %,d/sem".format(weekly), color = AccentLime, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                            Text("•", color = Color.Gray, fontSize = 11.sp)
                                            Text("$weeks sem", color = AccentGold, fontSize = 11.sp)
                                        }
                                        Text("Bônus Assinatura: R$ %,d".format(bonus), color = Color.Gray, fontSize = 10.sp)
                                    }
                                    Button(
                                        onClick = { viewModel.signSponsorContract(name, weekly, weeks, bonus) },
                                        colors = ButtonDefaults.buttonColors(containerColor = TurfDeepGreen, contentColor = AccentLime),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text("ASSINAR", fontSize = 10.sp, fontWeight = FontWeight.Black)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Sócio-Torcedores Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CardSurfaceDark),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.People, contentDescription = null, tint = AccentLime, modifier = Modifier.size(20.dp))
                    Text("Programa Sócio-Torcedor", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
                Spacer(modifier = Modifier.height(12.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(AccentLime.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                        .border(1.dp, AccentLime.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                        .padding(12.dp)
                ) {
                    Column {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Sócios Ativos:", color = Color.Gray, fontSize = 13.sp)
                            Text("%,d membros".format(s.socioTorcedoresCount), color = AccentLime, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Receita Semanal:", color = Color.Gray, fontSize = 13.sp)
                            Text("R$ %,d".format(socioRevenue), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "O número de sócios aumenta com o desempenho do seu clube! Vitórias em partidas geram novos sócios rapidamente, enquanto empates e derrotas causam evasão.",
                            color = Color.LightGray,
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )
                    }
                }
            }
        }
        } else if (activeSubTab == "BALANCO") {
        // Financial Evolution Visual Graph
        FinancialEvolutionChart(
            balanceHistory = listOf(
                (s.bankBalance * 0.85).toLong(),
                (s.bankBalance * 0.90).toLong(),
                (s.bankBalance * 0.88).toLong(),
                (s.bankBalance * 0.95).toLong(),
                s.bankBalance
            )
        )

        // Finance card breakdown
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CardSurfaceDark),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Demonstrativo Financeiro Semanal", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(12.dp))

                // Total Arrecadado (Receitas) Summary Row
                val totalRevenue = expectedRevenue + socioRevenue + (estTicketRevenue / 2)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(TurfDeepGreen.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("TOTAL ARRECADADO (RECEITAS)", color = AccentLime, fontSize = 11.sp, fontWeight = FontWeight.Black)
                        Text("Soma de patrocínio, sócios e bilheteria média", color = Color.Gray, fontSize = 9.sp)
                    }
                    Text("R$ %,d".format(totalRevenue), color = AccentLime, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Detail of Revenues
                Column(modifier = Modifier.padding(horizontal = 8.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("• Patrocínio Máster (+)", color = Color.Gray, fontSize = 12.sp)
                        Text("R$ %,d".format(expectedRevenue), color = Color.White, fontSize = 12.sp)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("• Sócios-Torcedores (+)", color = Color.Gray, fontSize = 12.sp)
                        Text("R$ %,d".format(socioRevenue), color = Color.White, fontSize = 12.sp)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("• Bilheteria Média Semanal (+)", color = Color.Gray, fontSize = 12.sp)
                        Text("R$ %,d".format(estTicketRevenue / 2), color = Color.White, fontSize = 12.sp)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("  └─ Bilheteria do Jogo em Casa", color = Color.Gray, fontSize = 11.sp)
                        Text("R$ %,d".format(estTicketRevenue), color = Color.Gray, fontSize = 11.sp)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = Color.White.copy(alpha = 0.05f))
                Spacer(modifier = Modifier.height(12.dp))

                // Total Gasto (Despesas) Summary Row
                val loanCost = (s.loanAmount * 0.002).toLong()
                val installmentCost = if (s.installmentWeeksRemaining > 0) s.installmentWeeklyDeduction else 0L
                val totalExpense = totalWage + totalAcademyCost + overheadCost + loanCost + installmentCost
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Red.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("TOTAL GASTO (DESPESAS)", color = Color.Red, fontSize = 11.sp, fontWeight = FontWeight.Black)
                        Text("Soma de salários, base, overhead, parcelas e juros", color = Color.Gray, fontSize = 9.sp)
                    }
                    Text("R$ %,d".format(totalExpense), color = Color.Red, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Detail of Expenses
                Column(modifier = Modifier.padding(horizontal = 8.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("• Despesa com Salários (-)", color = Color.Gray, fontSize = 12.sp)
                        Text("R$ %,d".format(totalWage), color = Color.White, fontSize = 12.sp)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("  └─ Limite de Folha da Divisão", color = Color.Gray, fontSize = 10.sp)
                        Text("R$ %,d".format(cap), color = Color.Gray, fontSize = 10.sp)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("• Despesa Categoria de Base (-)", color = Color.Gray, fontSize = 12.sp)
                        Text("R$ %,d".format(totalAcademyCost), color = Color.White, fontSize = 12.sp)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("• Custos Operacionais/Overhead (-)", color = Color.Gray, fontSize = 12.sp)
                        Text("R$ %,d".format(overheadCost), color = Color.White, fontSize = 12.sp)
                    }

                    if (s.installmentWeeksRemaining > 0) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("• Parcelas de Jogadores (${s.installmentWeeksRemaining} sem.) (-)", color = Color.Gray, fontSize = 12.sp)
                            Text("R$ %,d".format(installmentCost), color = Color.White, fontSize = 12.sp)
                        }
                    }

                    if (s.loanAmount > 0) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("• Juros de Empréstimo (-)", color = Color.Gray, fontSize = 12.sp)
                            Text("R$ %,d".format(loanCost), color = Color.White, fontSize = 12.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f))
                Spacer(modifier = Modifier.height(12.dp))

                // Estimate based on 1 home game every 2 weeks
                val weeklyResult = totalRevenue - totalExpense
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("RESULTADO SEMANAL MÉDIO EST.", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text(
                        text = "R$ %,d".format(weeklyResult),
                        color = if (weeklyResult >= 0) AccentLime else Color.Red,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }
        }
        } else if (activeSubTab == "ESTADIO") {
        // Stadium card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CardSurfaceDark),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Infraestrutura & Estádio", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(12.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Capacidade Atual:", color = Color.Gray, fontSize = 13.sp)
                    Text("%,d assentos".format(s.stadiumCapacity), color = MaterialTheme.colorScheme.onBackground, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Preço do Ingresso:", color = Color.Gray, fontSize = 13.sp)
                    Text("R$ %.2f".format(s.ticketPrice), color = MaterialTheme.colorScheme.onBackground, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(12.dp))
                
                // Adjust ticket price controls
                Text("Ajustar preço de ingresso:", color = Color.Gray, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    IconButton(
                        onClick = { viewModel.adjustTicketPrice(s.ticketPrice - 0.50) },
                        modifier = Modifier
                            .size(36.dp)
                            .background(Color.Gray.copy(alpha = 0.15f), RoundedCornerShape(18.dp))
                    ) {
                        Icon(Icons.Default.Remove, contentDescription = "Diminuir R$ 0.50", tint = Color.White, modifier = Modifier.size(18.dp))
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(36.dp)
                            .background(CardSurfaceDark, RoundedCornerShape(6.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "R$ %.2f".format(s.ticketPrice),
                            color = AccentLime,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    IconButton(
                        onClick = { viewModel.adjustTicketPrice(s.ticketPrice + 0.50) },
                        modifier = Modifier
                            .size(36.dp)
                            .background(Color.Gray.copy(alpha = 0.15f), RoundedCornerShape(18.dp))
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Aumentar R$ 0.50", tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { viewModel.expandStadium(1000) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = TurfDeepGreen, contentColor = Color.White)
                    ) {
                        Text("+1,000 assentos (R$ 250k)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = { viewModel.expandStadium(5000) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = TurfDeepGreen, contentColor = Color.White)
                    ) {
                        Text("+5,000 assentos (R$ 1.25M)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Loans Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CardSurfaceDark),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Empréstimos Bancários", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Caso as finanças fiquem no vermelho, faça um empréstimo. Taxa semanal de 0.2%", color = Color.Gray, fontSize = 11.sp)
                Spacer(modifier = Modifier.height(12.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Dívida Atual:", color = Color.Gray, fontSize = 13.sp)
                    Text("R$ %,d".format(s.loanAmount), color = if (s.loanAmount > 0) Color.Red else Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { viewModel.takeBankLoan(500000) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = TurfDeepGreen, contentColor = AccentLime)
                    ) {
                        Text("Pegar R$ 500k", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = { viewModel.repayBankLoan(500000) },
                        enabled = s.loanAmount > 0,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = TurfDeepGreen, contentColor = AccentGold)
                    ) {
                        Text("Pagar R$ 500k", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}
}


