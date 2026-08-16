package com.example.ui.components.tactics

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.theme.*
import com.example.ui.viewmodel.GameViewModel
import com.example.ui.viewmodel.substitutePlayer
import com.example.data.*

@Composable
fun TacticalControlDialog(
    viewModel: GameViewModel,
    onDismiss: () -> Unit
) {
    val liveHomeFormation by viewModel.liveHomeFormation.collectAsStateWithLifecycle()
    val liveHomeStyle by viewModel.liveHomeStyle.collectAsStateWithLifecycle()
    val liveMatchSpeed by viewModel.liveMatchSpeed.collectAsStateWithLifecycle()

    var activeTab by remember { mutableStateOf("FORMAÇÃO") }

    Dialog(
        onDismissRequest = onDismiss
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f),
            colors = CardDefaults.cardColors(containerColor = TurfPitchDark),
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(2.dp, AccentGold)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "GERENCIADOR TÁTICO",
                        color = AccentGold,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val tabs = listOf("FORMAÇÃO", "MENTALIDADE", "TROCAS", "OPÇÕES")
                    tabs.forEach { tab ->
                        Button(
                            onClick = { activeTab = tab },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (activeTab == tab) AccentLime else CardSurfaceDark,
                                contentColor = if (activeTab == tab) TurfDeepGreen else Color.White
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(vertical = 8.dp)
                        ) {
                            Text(tab, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    when (activeTab) {
                        "TROCAS" -> {
                            val starters = viewModel.liveMatchHomePlayers
                            val allPlayers by viewModel.playerRoster.collectAsStateWithLifecycle()
                            val bench = allPlayers.filter { p -> starters.none { s -> s.id == p.id } }

                            var selectedStarter by remember { mutableStateOf<com.example.data.Player?>(null) }
                            var selectedBench by remember { mutableStateOf<com.example.data.Player?>(null) }

                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = "Substituições ao Vivo na Partida",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )

                                Text("1. Selecione o Titular para sair:", color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    starters.forEach { p ->
                                        val isSel = selectedStarter?.id == p.id
                                        Card(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { selectedStarter = p },
                                            colors = CardDefaults.cardColors(
                                                containerColor = if (isSel) AccentGold.copy(alpha = 0.2f) else CardSurfaceDark
                                            ),
                                            border = BorderStroke(1.dp, if (isSel) AccentGold else Color.White.copy(alpha = 0.05f)),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(10.dp).fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text("${p.position} - ${p.name}", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                                Text("Força: ${p.force} | Energ: ${p.energy}%", color = AccentLime, fontSize = 11.sp)
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))
                                Text("2. Selecione o Reserva para entrar:", color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    if (bench.isEmpty()) {
                                        Text("Nenhum reserva disponível no banco.", color = Color.Gray, fontSize = 11.sp)
                                    } else {
                                        bench.forEach { p ->
                                            val isSel = selectedBench?.id == p.id
                                            Card(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable { selectedBench = p },
                                                colors = CardDefaults.cardColors(
                                                    containerColor = if (isSel) AccentLime.copy(alpha = 0.2f) else CardSurfaceDark
                                                ),
                                                border = BorderStroke(1.dp, if (isSel) AccentLime else Color.White.copy(alpha = 0.05f)),
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(10.dp).fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text("${p.position} - ${p.name}", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                                    Text("Força: ${p.force} | Energ: ${p.energy}%", color = AccentGold, fontSize = 11.sp)
                                                }
                                            }
                                        }
                                    }
                                }

                                Button(
                                    onClick = {
                                        val pOut = selectedStarter
                                        val pIn = selectedBench
                                        if (pOut != null && pIn != null) {
                                            viewModel.substitutePlayer(pOut, pIn)
                                            selectedStarter = null
                                            selectedBench = null
                                        }
                                    },
                                    enabled = selectedStarter != null && selectedBench != null,
                                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = AccentGold, contentColor = TurfDeepGreen)
                                ) {
                                    Text("REALIZAR SUBSTITUIÇÃO", fontWeight = FontWeight.Black)
                                }
                            }
                        }
                        "FORMAÇÃO" -> {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = "Formação Ativa: $liveHomeFormation",
                                    color = Color.LightGray,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .aspectRatio(1.3f)
                                            .background(TurfDeepGreen, shape = RoundedCornerShape(12.dp))
                                            .padding(8.dp)
                                    ) {
                                        Canvas(modifier = Modifier.fillMaxSize()) {
                                            val w = size.width
                                            val h = size.height
                                            
                                            drawRect(
                                                color = Color.White.copy(alpha = 0.25f),
                                                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
                                            )
                                            
                                            drawLine(
                                                color = Color.White.copy(alpha = 0.25f),
                                                start = androidx.compose.ui.geometry.Offset(0f, h / 2),
                                                end = androidx.compose.ui.geometry.Offset(w, h / 2),
                                                strokeWidth = 2.dp.toPx()
                                            )
                                            
                                            drawCircle(
                                                color = Color.White.copy(alpha = 0.25f),
                                                radius = (w * 0.12f).coerceAtMost(h * 0.12f),
                                                center = androidx.compose.ui.geometry.Offset(w / 2, h / 2),
                                                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
                                            )
                                            
                                            drawRect(
                                                color = Color.White.copy(alpha = 0.25f),
                                                topLeft = androidx.compose.ui.geometry.Offset(w * 0.25f, 0f),
                                                size = androidx.compose.ui.geometry.Size(w * 0.5f, h * 0.16f),
                                                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
                                            )
                                            
                                            drawRect(
                                                color = Color.White.copy(alpha = 0.25f),
                                                topLeft = androidx.compose.ui.geometry.Offset(w * 0.25f, h * 0.84f),
                                                size = androidx.compose.ui.geometry.Size(w * 0.5f, h * 0.16f),
                                                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
                                            )
                                        }

                                        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                                            val w = maxWidth
                                            val h = maxHeight
                                            
                                            val homePlayers = viewModel.liveMatchHomePlayers
                                            val goalkeeper = homePlayers.find { it.position == "GOL" } ?: homePlayers.lastOrNull()
                                            val outfieldPlayers = homePlayers.filter { it != goalkeeper }

                                            val slots = getFormationSlots(liveHomeFormation)
                                            val mapped = mapPlayersToFormation(outfieldPlayers, slots)
                                            val adjustedMapped = resolveMiniPitchOverlaps(mapped)

                                            if (goalkeeper != null) {
                                                Box(
                                                    modifier = Modifier
                                                        .offset(x = w * 0.5f - 18.dp, y = h * 0.90f - 18.dp)
                                                        .size(width = 36.dp, height = 40.dp),
                                                    contentAlignment = Alignment.TopCenter
                                                ) {
                                                    Column(
                                                        horizontalAlignment = Alignment.CenterHorizontally
                                                    ) {
                                                        Box(
                                                            modifier = Modifier
                                                                .size(24.dp)
                                                                .background(Color(0xFFFFA726), CircleShape)
                                                                .border(1.dp, Color.White, CircleShape),
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            Text(
                                                                text = goalkeeper.name.split(" ").lastOrNull()?.take(3)?.uppercase() ?: goalkeeper.name.take(3),
                                                                color = Color.White,
                                                                fontSize = 7.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                maxLines = 1
                                                            )
                                                        }
                                                        Spacer(modifier = Modifier.height(1.dp))
                                                        Text(
                                                            text = "GOL",
                                                            color = Color.White,
                                                            fontSize = 7.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            modifier = Modifier
                                                                .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(2.dp))
                                                                .padding(horizontal = 2.dp)
                                                        )
                                                    }
                                                }
                                            }
                                            
                                            for (pair in adjustedMapped) {
                                                val player = pair.first
                                                val slot = pair.second
                                                
                                                Box(
                                                    modifier = Modifier
                                                        .offset(x = w * slot.x - 18.dp, y = h * slot.y - 18.dp)
                                                        .size(width = 36.dp, height = 40.dp),
                                                    contentAlignment = Alignment.TopCenter
                                                ) {
                                                    Column(
                                                        horizontalAlignment = Alignment.CenterHorizontally
                                                    ) {
                                                        Box(
                                                            modifier = Modifier
                                                                .size(24.dp)
                                                                .background(Color(0xFF0D47A1), CircleShape)
                                                                .border(1.dp, AccentGold, CircleShape),
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            Text(
                                                                text = player.name.split(" ").lastOrNull()?.take(3)?.uppercase() ?: player.name.take(3),
                                                                color = Color.White,
                                                                fontSize = 7.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                maxLines = 1
                                                            )
                                                        }
                                                        Spacer(modifier = Modifier.height(1.dp))
                                                        Text(
                                                            text = slot.name,
                                                            color = AccentGold,
                                                            fontSize = 7.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            modifier = Modifier
                                                                .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(2.dp))
                                                                .padding(horizontal = 2.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(140.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    val formationsList = listOf(
                                        "4-4-2", "4-4-1-1", "4-5-1", "4-3-3", "4-3-2-1", "4-1-3-2", "5-4-1", 
                                        "4-1-2-1-2 Diamond", "3-5-2", "5-3-2", "4-2-3-1", "3-4-3", "3-2-4-1", 
                                        "3-2-5 (W-M)", "2-3-2-3", "4-2-4"
                                    )
                                    
                                    val rows = formationsList.chunked(3)
                                    items(rows) { rowItems ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            rowItems.forEach { item ->
                                                val isSelected = (liveHomeFormation == item)
                                                Box(
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .background(
                                                            if (isSelected) AccentLime.copy(alpha = 0.2f) else CardSurfaceDark,
                                                            RoundedCornerShape(8.dp)
                                                        )
                                                        .border(
                                                            width = 1.dp,
                                                            color = if (isSelected) AccentLime else Color.Transparent,
                                                            shape = RoundedCornerShape(8.dp)
                                                        )
                                                        .clickable {
                                                            viewModel.changeLiveHomeFormation(item)
                                                        }
                                                        .padding(vertical = 12.dp),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        text = item,
                                                        color = if (isSelected) AccentLime else Color.White,
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                            if (rowItems.size < 3) {
                                                repeat(3 - rowItems.size) {
                                                    Spacer(modifier = Modifier.weight(1f))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        "OPÇÕES" -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Text(
                                    text = "Ajustes e Preferências da Partida",
                                    color = Color.LightGray,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )

                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = CardSurfaceDark),
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .padding(16.dp)
                                            .fillMaxWidth(),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Text(
                                            text = "Velocidade da Simulação em Tempo Real",
                                            color = Color.White,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            listOf("Tempo Real", "2x", "4x", "8x").forEach { spd ->
                                                val isSel = (liveMatchSpeed == spd)
                                                Button(
                                                    onClick = { viewModel.changeLiveMatchSpeed(spd) },
                                                    colors = ButtonDefaults.buttonColors(
                                                        containerColor = if (isSel) AccentGold else Color.White.copy(alpha = 0.1f),
                                                        contentColor = if (isSel) TurfDeepGreen else Color.White
                                                    ),
                                                    shape = RoundedCornerShape(8.dp),
                                                    modifier = Modifier.weight(1f),
                                                    contentPadding = PaddingValues(vertical = 6.dp)
                                                ) {
                                                    Text(
                                                        text = if (spd == "Tempo Real") "TR" else spd,
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                val infiniteStaminaEnabled by viewModel.infiniteStaminaEnabled.collectAsStateWithLifecycle()
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = CardSurfaceDark),
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .padding(16.dp)
                                            .fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Atletas não Cansam",
                                                color = Color.White,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = "Mantém a energia de todos os seus jogadores em 100%.",
                                                color = Color.LightGray,
                                                fontSize = 11.sp,
                                                lineHeight = 15.sp
                                            )
                                        }
                                        Switch(
                                            checked = infiniteStaminaEnabled,
                                            onCheckedChange = { viewModel.setInfiniteStaminaEnabled(it) },
                                            colors = SwitchDefaults.colors(
                                                checkedThumbColor = TurfDeepGreen,
                                                checkedTrackColor = AccentLime,
                                                uncheckedThumbColor = Color.Gray,
                                                uncheckedTrackColor = Color.DarkGray
                                            )
                                        )
                                    }
                                }
                            }
                        }

                        "MENTALIDADE" -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Text(
                                    text = "Selecione a Mentalidade de Jogo",
                                    color = Color.LightGray,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )

                                val mentalities = listOf(
                                    Triple("Ofensiva", "Ataque Total", "Linha alta, pressão intensa pós-perda, transições rápidas e alta frequência de infiltração na área."),
                                    Triple("Equilibrada", "Equilibrada", "Distribuição estável, preservação da compactação tática, ritmo controlled sem exposição excessiva."),
                                    Triple("Defensiva", "Defensiva", "Bloco baixo, proteção da área, risco de passe reduzido, foco em contra-ataques e recomposição.")
                                )

                                mentalities.forEach { (name, label, desc) ->
                                    val isSelected = (liveHomeStyle == name)
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { viewModel.changeLiveHomeStyle(name) },
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (isSelected) TurfDeepGreen else CardSurfaceDark
                                        ),
                                        shape = RoundedCornerShape(12.dp),
                                        border = BorderStroke(
                                            width = 1.dp,
                                            color = if (isSelected) AccentGold else Color.White.copy(alpha = 0.1f)
                                        )
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .padding(16.dp)
                                                .fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            RadioButton(
                                                selected = isSelected,
                                                onClick = { viewModel.changeLiveHomeStyle(name) },
                                                colors = RadioButtonDefaults.colors(
                                                    selectedColor = AccentGold,
                                                    unselectedColor = Color.LightGray
                                                )
                                            )
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = label,
                                                    color = if (isSelected) AccentGold else Color.White,
                                                    fontSize = 15.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(
                                                    text = desc,
                                                    color = Color.LightGray,
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

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentLime, contentColor = TurfDeepGreen),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("CONFIRMAR AJUSTES", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
