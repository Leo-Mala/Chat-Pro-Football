package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.*
import com.example.ui.components.tactics.*
import com.example.ui.theme.*
import com.example.ui.viewmodel.GameViewModel

@Composable
fun TacticsTab(viewModel: GameViewModel) {
    val playerTeam by viewModel.playerTeam.collectAsStateWithLifecycle()
    val formation by viewModel.playerFormation.collectAsStateWithLifecycle()
    val style by viewModel.playerStyle.collectAsStateWithLifecycle()
    val roster by viewModel.playerRoster.collectAsStateWithLifecycle()

    val calculatedRating = GameEngine.calculateTeamRating(roster)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CardSurfaceDark),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Tática de Jogo", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("Configure sua escalação e modo de operar", color = Color.Gray, fontSize = 11.sp)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("AVALIAÇÃO GERAL", color = Color.Gray, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    Text("$calculatedRating", color = AccentGold, fontSize = 24.sp, fontWeight = FontWeight.Black)
                }
            }
        }

        // Dropdown configs
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Formations selector
            Column(modifier = Modifier.weight(1f)) {
                Text("Esquema Tático", color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = CardSurfaceDark)
                ) {
                    var expanded by remember { mutableStateOf(false) }
                    Box {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { expanded = true }
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(formation, color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold)
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = Color.White)
                        }
                        DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false },
                            modifier = Modifier.background(CardSurfaceDark)
                        ) {
                            GameEngine.formations.forEach { form ->
                                DropdownMenuItem(
                                    text = { Text(form, color = MaterialTheme.colorScheme.onBackground) },
                                    onClick = {
                                        viewModel.setTactics(form, style)
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Play style selector
            Column(modifier = Modifier.weight(1f)) {
                Text("Estilo de Jogo", color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = CardSurfaceDark)
                ) {
                    var expanded by remember { mutableStateOf(false) }
                    Box {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { expanded = true }
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(style, color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold)
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = Color.White)
                        }
                        DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false },
                            modifier = Modifier.background(CardSurfaceDark)
                        ) {
                            GameEngine.playStyles.forEach { st ->
                                DropdownMenuItem(
                                    text = { Text(st, color = MaterialTheme.colorScheme.onBackground) },
                                    onClick = {
                                        viewModel.setTactics(formation, st)
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Tactical Roles Card
        val captainId by viewModel.captainPlayerId.collectAsStateWithLifecycle()
        val penaltyId by viewModel.penaltyPlayerId.collectAsStateWithLifecycle()
        val freekickId by viewModel.freekickPlayerId.collectAsStateWithLifecycle()
        val cornerId by viewModel.cornerPlayerId.collectAsStateWithLifecycle()

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CardSurfaceDark),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Funções Táticas & Liderança", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text("Defina as principais responsabilidades táticas do seu elenco.", color = Color.Gray, fontSize = 11.sp)
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        TacticalRoleSelector(
                            roleLabel = "Capitão",
                            roleIcon = "👑",
                            selectedPlayerId = captainId,
                            roster = roster,
                            onSelect = { viewModel.setTacticalRole("CAPTAIN", it) }
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        TacticalRoleSelector(
                            roleLabel = "Pênaltis",
                            roleIcon = "🎯",
                            selectedPlayerId = penaltyId,
                            roster = roster,
                            onSelect = { viewModel.setTacticalRole("PENALTY", it) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        TacticalRoleSelector(
                            roleLabel = "Faltas",
                            roleIcon = "☄️",
                            selectedPlayerId = freekickId,
                            roster = roster,
                            onSelect = { viewModel.setTacticalRole("FREEKICK", it) }
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        TacticalRoleSelector(
                            roleLabel = "Escanteios",
                            roleIcon = "📐",
                            selectedPlayerId = cornerId,
                            roster = roster,
                            onSelect = { viewModel.setTacticalRole("CORNER", it) }
                        )
                    }
                }
            }
        }

        // Auto Substitutions & Individual Instructions Card
        var autoSubFatigue by remember { mutableStateOf(true) }
        var autoSubYellowCards by remember { mutableStateOf(true) }
        var selectedRoleInstruction by remember { mutableStateOf("Equilibrado") }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CardSurfaceDark),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Substituições Inteligentes & Instruções", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text("Configure automações e postura tática individual para as partidas.", color = Color.Gray, fontSize = 11.sp)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Substituir atletas desgastados (<60% energia)", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Text("Realiza trocas automáticas de jogadores cansados", color = Color.Gray, fontSize = 10.sp)
                    }
                    Switch(
                        checked = autoSubFatigue,
                        onCheckedChange = { autoSubFatigue = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = TurfDeepGreen, checkedTrackColor = AccentLime)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Proteger amarelados no 2º tempo", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Text("Substitui atletas pendurados para evitar expulsões", color = Color.Gray, fontSize = 10.sp)
                    }
                    Switch(
                        checked = autoSubYellowCards,
                        onCheckedChange = { autoSubYellowCards = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = TurfDeepGreen, checkedTrackColor = AccentLime)
                    )
                }

                HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

                Text("Instrução Tática do Setor Ofensivo:", color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                val instructions = listOf("Equilibrado", "Pressionar Saída", "Contra-Ataque", "Linha Alta")
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    instructions.chunked(2).forEach { rowInstructions ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            rowInstructions.forEach { inst ->
                                val isSelected = selectedRoleInstruction == inst
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { selectedRoleInstruction = inst },
                                    label = {
                                        Text(
                                            text = inst,
                                            fontSize = 11.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    },
                                    modifier = Modifier.weight(1f),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = TurfDeepGreen,
                                        selectedLabelColor = AccentLime,
                                        containerColor = Color.White.copy(alpha = 0.05f),
                                        labelColor = Color.LightGray
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }

        // Football Pitch Visual Representation
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp)
                .border(2.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(12.dp)),
            colors = CardDefaults.cardColors(containerColor = TurfDeepGreen),
            shape = RoundedCornerShape(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Tactical lines
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .border(1.5.dp, Color.White.copy(alpha = 0.4f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.5.dp)
                            .background(Color.White.copy(alpha = 0.4f))
                            .align(Alignment.Center)
                    )
                    Box(
                        modifier = Modifier
                            .size(70.dp)
                            .border(1.5.dp, Color.White.copy(alpha = 0.4f), CircleShape)
                            .align(Alignment.Center)
                    )
                }

                val starters = roster.filter { it.isStarter }
                val goalkeeper = starters.find { it.position == "GOL" } ?: starters.lastOrNull()
                val outfieldPlayers = starters.filter { it != goalkeeper }

                val slots = getFormationSlots(formation)
                val mapped = mapPlayersToFormation(outfieldPlayers, slots)
                val adjustedMapped = resolveMiniPitchOverlaps(mapped, minDistance = 0.12f)

                val assignedOriginalSlots = mapped.map { it.second }.toSet()
                val unassignedSlots = slots.filter { it !in assignedOriginalSlots }

                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val w = maxWidth
                    val h = maxHeight

                    val gkX = w * 0.5f - 27.5.dp
                    val gkY = h * 0.88f - 20.dp

                    Box(modifier = Modifier.offset(x = gkX, y = gkY)) {
                        SinglePitchPlayerDot(goalkeeper, "GOL", NeonGoldAccent)
                    }

                    for (pair in adjustedMapped) {
                        val player = pair.first
                        val slot = pair.second
                        val px = w * slot.x - 27.5.dp
                        val py = h * slot.y - 20.dp

                        val color = when (slot.role) {
                            "ZAG", "LAT" -> NeonBlueAccent
                            "VOL", "MEI" -> NeonGreenAccent
                            else -> NeonPurpleAccent
                        }

                        Box(modifier = Modifier.offset(x = px, y = py)) {
                            SinglePitchPlayerDot(player, slot.name, color)
                        }
                    }

                    for (slot in unassignedSlots) {
                        val px = w * slot.x - 27.5.dp
                        val py = h * slot.y - 20.dp

                        val color = when (slot.role) {
                            "ZAG", "LAT" -> NeonBlueAccent
                            "VOL", "MEI" -> NeonGreenAccent
                            else -> NeonPurpleAccent
                        }

                        Box(modifier = Modifier.offset(x = px, y = py)) {
                            SinglePitchPlayerDot(null, slot.name, color)
                        }
                    }
                }
            }
        }

        // Assistant Coach Suggestions Card
        val context = LocalContext.current
        val (recForm, recStyle, recAdvice) = remember(roster) {
            val rating = GameEngine.calculateTeamRating(roster)
            val forwards = roster.filter { it.position == "ATA" }
            if (rating < 65) {
                Triple("4-4-2", "Retranca", "Temos um elenco limitado técnica e fisicamente. Recomendo focar na defesa sólida e jogar fechado por uma bola rápida.")
            } else if (forwards.size >= 3 && forwards.any { it.force > 72 }) {
                Triple("4-3-3", "Ataque Total", "Nosso poder de fogo ofensivo é excelente! Recomendo pressionar alto e sobrecarregar o adversário no esquema 4-3-3.")
            } else {
                Triple("4-4-2", "Equilibrado", "Temos um elenco bem equilibrado. Recomendo manter a posse de bola firme e controlar o ritmo do jogo no meio-campo.")
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CardSurfaceDark),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, AccentLime.copy(alpha = 0.3f))
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("🧔", fontSize = 24.sp)
                    Column(modifier = Modifier.weight(1f)) {
                        Text("SUGESTÃO DO AUXILIAR TÉCNICO", color = AccentLime, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        Text(recAdvice, color = Color.White.copy(alpha = 0.9f), fontSize = 11.sp, lineHeight = 14.sp)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        SuggestionBadge(label = "Tática: $recForm")
                        SuggestionBadge(label = "Estilo: $recStyle")
                    }
                    Button(
                        onClick = {
                            viewModel.setTactics(recForm, recStyle)
                            Toast.makeText(context, "Sugestão do auxiliar aplicada!", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = TurfDeepGreen),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Text("Aplicar", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }
    }
}
