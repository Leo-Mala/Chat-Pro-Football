package com.example.ui.components.squad

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.*
import com.example.ui.theme.*

@Composable
fun AttributeBarItem(name: String, value: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(name, fontSize = 11.sp, color = Color.LightGray, modifier = Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically) {
            val barColor = when {
                value >= 80 -> Color(0xFF00E676)
                value >= 70 -> AccentGold
                value >= 60 -> Color(0xFF29B6F6)
                else -> Color.Gray
            }
            Box(
                modifier = Modifier
                    .width(36.dp)
                    .height(5.dp)
                    .background(Color.DarkGray, RoundedCornerShape(3.dp))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth((value / 100f).coerceIn(0f, 1f))
                        .background(barColor, RoundedCornerShape(3.dp))
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            Text("$value", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = barColor, modifier = Modifier.width(20.dp))
        }
    }
}

@Composable
fun PlayerActionDialog(
    player: Player,
    onDismiss: () -> Unit,
    onRenew: () -> Unit,
    onSell: (paymentType: String) -> Unit,
    onToggleStarter: (Boolean) -> Unit
) {
    val posEnum = remember(player.position) { Posicao.fromCode(player.position) }
    val atributos = remember(player.atributosJson, player.force, player.position) { player.getAtributosObject() }
    val notaGeral = remember(posEnum, atributos) { CalculadoraNota.calcularNota(posEnum, atributos) }
    val valorMercado = remember(player.market_value, player) {
        if (player.market_value > 0) player.market_value else player.calculateMarketValue()
    }
    var selectedAttrTab by remember { mutableIntStateOf(0) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            colors = CardDefaults.cardColors(containerColor = CardSurfaceDark),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header Player Info Card
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .background(AccentLime, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(posEnum.code, fontWeight = FontWeight.Black, color = TurfDeepGreen, fontSize = 13.sp)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(player.name, color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text("${posEnum.displayName} • ${player.age} anos", color = Color.Gray, fontSize = 12.sp)
                            Text("Valor: R$ %,d".format(valorMercado), color = AccentGold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    // Nota Geral Badge Card
                    Card(
                        colors = CardDefaults.cardColors(containerColor = TurfDeepGreen),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("NOTA GERAL", color = Color.LightGray, fontSize = 8.sp, fontWeight = FontWeight.Black)
                            Text("$notaGeral", color = Color(0xFF00E676), fontSize = 22.sp, fontWeight = FontWeight.Black)
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f))

                // Contract & Season Stats Summary
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("Contrato: ${player.contractDurationWeeks} sem.", color = Color.Gray, fontSize = 11.sp)
                        Text("Salário: R$ %,d/sem".format(player.salary), color = Color.Gray, fontSize = 11.sp)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Jogos: ${player.careerApps}", color = Color.Gray, fontSize = 11.sp)
                        Text("Nota Média: %.2f".format(player.getAverageRating()), color = AccentLime, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // --- ATRIBUTOS COM ABAS ---
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Selector de Abas
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF1E242B), RoundedCornerShape(10.dp))
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val tabs = listOf("⚽ Técnicos", "🏋️ Físicos", "🧠 Mentais")
                        tabs.forEachIndexed { index, title ->
                            val isSelected = selectedAttrTab == index
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) TurfDeepGreen else Color.Transparent)
                                    .clickable { selectedAttrTab = index }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = title,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) AccentLime else Color.Gray,
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E242B)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            when (selectedAttrTab) {
                                0 -> {
                                    Text("⚽ ATRIBUTOS TÉCNICOS", color = AccentGold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    if (posEnum == Posicao.GOLEIRO) {
                                        AttributeBarItem("Reflexos", atributos.reflexos)
                                        AttributeBarItem("Pegada", atributos.pegada)
                                        AttributeBarItem("1 x 1", atributos.umContraUm)
                                        AttributeBarItem("Saída de Gol", atributos.saidaDeGol)
                                        AttributeBarItem("Lançamento", atributos.lancamento)
                                    }
                                    AttributeBarItem("Desarme", atributos.desarme)
                                    AttributeBarItem("Marcação", atributos.marcacao)
                                    AttributeBarItem("Cabeceio", atributos.cabeceio)
                                    AttributeBarItem("Passe Curto", atributos.passeCurto)
                                    AttributeBarItem("Cruzamento", atributos.cruzamento)
                                    AttributeBarItem("Drible", atributos.drible)
                                    AttributeBarItem("Passe", atributos.passe)
                                    AttributeBarItem("Primeiro Toque", atributos.primeiroToque)
                                    AttributeBarItem("Finalização", atributos.finalizacao)
                                    AttributeBarItem("Chute de Longe", atributos.chuteDeLonge)
                                    AttributeBarItem("Controle de Bola", atributos.controleBola)
                                }
                                1 -> {
                                    Text("🏋️ ATRIBUTOS FÍSICOS", color = Color(0xFFFF7043), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    AttributeBarItem("Agilidade", atributos.agilidade)
                                    AttributeBarItem("Impulsão", atributos.impulsao)
                                    AttributeBarItem("Força", atributos.forca)
                                    AttributeBarItem("Velocidade", atributos.velocidade)
                                    AttributeBarItem("Aceleração", atributos.aceleracao)
                                    AttributeBarItem("Resistência", atributos.resistencia)
                                }
                                2 -> {
                                    Text("🧠 ATRIBUTOS MENTAIS", color = Color(0xFF29B6F6), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    AttributeBarItem("Posicionamento", atributos.posicionamento)
                                    AttributeBarItem("Concentração", atributos.concentracao)
                                    AttributeBarItem("Sangue Frio", atributos.sangueFrio)
                                    AttributeBarItem("Antecipação", atributos.antecipacao)
                                    AttributeBarItem("Bravura", atributos.bravura)
                                    AttributeBarItem("Trabalho em Equipe", atributos.trabalhoEquipe)
                                    AttributeBarItem("Decisão", atributos.decisao)
                                    AttributeBarItem("Sem Bola", atributos.semBola)
                                    AttributeBarItem("Visão de Jogo", atributos.visaoJogo)
                                    AttributeBarItem("Criatividade", atributos.criatividade)
                                    AttributeBarItem("Agressividade", atributos.agressividade)
                                    AttributeBarItem("Liderança", atributos.lideranca)
                                    AttributeBarItem("Regularidade", atributos.regularidade)
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Escalado como Titular:", color = Color.Gray, fontSize = 13.sp)
                    Switch(
                        checked = player.isStarter,
                        onCheckedChange = { isChecked ->
                            onToggleStarter(isChecked)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = AccentLime,
                            checkedTrackColor = TurfDeepGreen
                        )
                    )
                }

                Button(
                    onClick = {
                        onRenew()
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = TurfDeepGreen, contentColor = AccentLime)
                ) {
                    Text("RENOVAR POR 1 ANO (Aumento de 10%)", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            onSell("VISTA")
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red.copy(alpha = 0.2f), contentColor = Color.Red)
                    ) {
                        Text("VENDER À VISTA", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                    Button(
                        onClick = {
                            onSell("PARCELADO")
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentGold.copy(alpha = 0.2f), contentColor = AccentGold)
                    ) {
                        Text("PARCELAR 3x", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                }

                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                ) {
                    Text("FECHAR", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun PlayerCard(player: Player, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = CardSurfaceDark),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(
            width = 1.dp,
            brush = Brush.horizontalGradient(
                colors = listOf(
                    when (player.position) {
                        "GOL" -> NeonGoldAccent.copy(alpha = 0.25f)
                        "ZAG", "LAT" -> NeonBlueAccent.copy(alpha = 0.25f)
                        "VOL", "MEI" -> NeonGreenAccent.copy(alpha = 0.25f)
                        else -> NeonPurpleAccent.copy(alpha = 0.25f)
                    },
                    Color.Transparent
                )
            )
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Position emblem with custom cyber styling
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        when (player.position) {
                            "GOL" -> NeonGoldAccent.copy(alpha = 0.12f)
                            "ZAG", "LAT" -> NeonBlueAccent.copy(alpha = 0.12f)
                            "VOL", "MEI" -> NeonGreenAccent.copy(alpha = 0.12f)
                            else -> NeonPurpleAccent.copy(alpha = 0.12f)
                        },
                        RoundedCornerShape(10.dp)
                    )
                    .border(
                        1.2.dp,
                        when (player.position) {
                            "GOL" -> NeonGoldAccent
                            "ZAG", "LAT" -> NeonBlueAccent
                            "VOL", "MEI" -> NeonGreenAccent
                            else -> NeonPurpleAccent
                        },
                        RoundedCornerShape(10.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    player.position,
                    fontWeight = FontWeight.Black,
                    fontSize = 11.sp,
                    color = when (player.position) {
                        "GOL" -> NeonGoldAccent
                        "ZAG", "LAT" -> NeonBlueAccent
                        "VOL", "MEI" -> NeonGreenAccent
                        else -> NeonPurpleAccent
                    }
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Player description
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        player.name,
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (player.isStarter) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            color = AccentLime.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, AccentLime.copy(alpha = 0.5f))
                        ) {
                            Text(
                                text = "TITULAR",
                                color = AccentLime,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    if (player.injuryWeeksRemaining > 0) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            color = NeonRedAccent.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, NeonRedAccent.copy(alpha = 0.4f))
                        ) {
                            Text(
                                text = "🩹 ${player.injuryWeeksRemaining}s",
                                color = NeonRedAccent,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    if (player.suspensionWeeksRemaining > 0) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            color = NeonRedAccent.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, NeonRedAccent.copy(alpha = 0.4f))
                        ) {
                            Text(
                                text = "🟥 SUSP",
                                color = NeonRedAccent,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    } else if (player.yellowCardsAccumulated > 0) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            color = NeonGoldAccent.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, NeonGoldAccent.copy(alpha = 0.4f))
                        ) {
                            Text(
                                text = "🟨".repeat(player.yellowCardsAccumulated),
                                color = NeonGoldAccent,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${player.age} anos", color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Salário: R$ %,d".format(player.salary), color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp, fontWeight = FontWeight.Medium)
                }
            }

            // Stats columns - styled to be clean and legible
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(44.dp)) {
                Text("FOR", color = Color.White.copy(alpha = 0.4f), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                Text("${player.force}", color = AccentGold, fontSize = 16.sp, fontWeight = FontWeight.Black)
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(44.dp)) {
                Text("ENE", color = Color.White.copy(alpha = 0.4f), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                Text("${player.energy}%", color = if (player.energy > 60) NeonGreenAccent else NeonRedAccent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(44.dp)) {
                Text("MOR", color = Color.White.copy(alpha = 0.4f), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                Text("${player.moral}", color = AccentLime, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun MonthlyEvolutionSummaryModal(
    summaryList: List<PlayerEvolutionResult>,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Resumo da Evolução Mensal",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(summaryList) { res ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (res.netChange >= 0)
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                            else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "${res.player.name} (${res.player.position})",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = if (res.netChange >= 0) "+${res.netChange.toInt()} OVR" else "${res.netChange.toInt()} OVR",
                                    fontWeight = FontWeight.Bold,
                                    color = if (res.netChange >= 0) Color(0xFF2E7D32) else Color.Red,
                                    fontSize = 14.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Força Atual: ${res.player.force} | Potencial: ${res.player.potential}",
                                fontSize = 12.sp,
                                color = Color.Gray
                            )
                            if (res.historyLogs.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Atributos: " + res.historyLogs.joinToString(", ") { "${it.atributo}: ${it.valorAntigo} ➔ ${it.valorNovo}" },
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                modifier = Modifier.testTag("dismiss_evolution_summary_button")
            ) {
                Text("OK")
            }
        }
    )
}
