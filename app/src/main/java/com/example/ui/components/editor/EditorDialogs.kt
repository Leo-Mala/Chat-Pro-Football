package com.example.ui.components.editor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.Player
import com.example.data.Team
import com.example.ui.screens.TeamBadge
import com.example.ui.theme.*

// EDIT TEAM DIALOG
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditTeamDialog(
    team: Team,
    isNew: Boolean,
    onDismiss: () -> Unit,
    onSave: (Team) -> Unit,
    onEditSquad: (() -> Unit)? = null
) {
    var name by remember { mutableStateOf(team.name) }
    var city by remember { mutableStateOf(team.city) }
    var state by remember { mutableStateOf(team.state) }
    var country by remember { mutableStateOf(team.country) }
    var division by remember { mutableIntStateOf(team.division) }
    var rating by remember { mutableIntStateOf(team.rating) }
    var stadiumName by remember { mutableStateOf(team.stadiumName) }
    var colorHex by remember { mutableStateOf(team.colorHex ?: "#1E88E5") }
    var logoUrl by remember { mutableStateOf(team.logoUrl ?: "") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = NeonMidnightSurface),
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, AccentLime)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = if (isNew) "➕ CADASTRAR NOVO TIME" else "✏️ EDITAR TIME",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nome do Time") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = city,
                        onValueChange = { city = it },
                        label = { Text("Cidade") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = state,
                        onValueChange = { state = it },
                        label = { Text("Estado") },
                        singleLine = true,
                        modifier = Modifier.width(90.dp)
                    )
                }

                OutlinedTextField(
                    value = country,
                    onValueChange = { country = it },
                    label = { Text("País") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Divisão: Série ${('A' + division - 1)}", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Slider(
                    value = division.toFloat(),
                    onValueChange = { division = it.toInt() },
                    valueRange = 1f..4f,
                    steps = 2
                )

                Text("Força/Rating: $rating", color = AccentLime, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Slider(
                    value = rating.toFloat(),
                    onValueChange = { rating = it.toInt() },
                    valueRange = 20f..99f
                )

                OutlinedTextField(
                    value = stadiumName,
                    onValueChange = { stadiumName = it },
                    label = { Text("Estádio") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = colorHex,
                    onValueChange = { colorHex = it },
                    label = { Text("Cor Hex (ex: #0033A0)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = logoUrl,
                    onValueChange = { logoUrl = it },
                    label = { Text("URL do Logo/Escudo (Opcional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (!isNew && onEditSquad != null) {
                    OutlinedButton(
                        onClick = {
                            onDismiss()
                            onEditSquad()
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentLime),
                        border = BorderStroke(1.dp, AccentLime),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Groups, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("VER E EDITAR JOGADORES DO ELENCO", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("CANCELAR", color = Color.Gray)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (name.isNotBlank()) {
                                onSave(
                                    team.copy(
                                        name = name.trim(),
                                        city = city.trim(),
                                        state = state.trim(),
                                        country = country.trim(),
                                        division = division,
                                        rating = rating,
                                        stadiumName = stadiumName.trim(),
                                        colorHex = if (colorHex.isBlank()) null else colorHex.trim(),
                                        logoUrl = if (logoUrl.isBlank()) null else logoUrl.trim()
                                    )
                                )
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentLime, contentColor = TurfPitchDark)
                    ) {
                        Text("SALVAR", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// EDIT PLAYER DIALOG
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditPlayerDialog(
    player: Player,
    allTeams: List<Team>,
    isNew: Boolean,
    onDismiss: () -> Unit,
    onSave: (Player) -> Unit
) {
    var name by remember { mutableStateOf(player.name) }
    var age by remember { mutableIntStateOf(player.age) }
    var nationality by remember { mutableStateOf(player.nationality) }
    var position by remember { mutableStateOf(player.position) }
    var force by remember { mutableIntStateOf(player.force) }
    var salaryText by remember { mutableStateOf(player.salary.toString()) }
    var selectedTeamId by remember { mutableStateOf(player.teamId) }
    var isStarter by remember { mutableStateOf(player.isStarter) }

    // Detailed Attributes
    var finishing by remember { mutableIntStateOf(player.finishing) }
    var passing by remember { mutableIntStateOf(player.passing) }
    var pace by remember { mutableIntStateOf(player.pace) }
    var strength by remember { mutableIntStateOf(player.strength) }
    var vision by remember { mutableIntStateOf(player.vision) }
    var defense by remember { mutableIntStateOf(player.defense) }

    val positions = listOf("GOL", "ZAG", "LAT", "VOL", "MEI", "ATA")

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = NeonMidnightSurface),
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, AccentLime)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = if (isNew) "➕ CADASTRAR JOGADOR" else "✏️ EDITAR JOGADOR",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nome do Jogador") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = age.toString(),
                        onValueChange = { age = it.toIntOrNull() ?: age },
                        label = { Text("Idade") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = nationality,
                        onValueChange = { nationality = it },
                        label = { Text("Nacionalidade") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                Text("Posição", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    positions.forEach { pos ->
                        FilterChip(
                            selected = position == pos,
                            onClick = { position = pos },
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

                Text("Força Geral (Overall): $force", color = getForceBadgeColor(force), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Slider(
                    value = force.toFloat(),
                    onValueChange = {
                        force = it.toInt()
                        finishing = force
                        passing = force
                        pace = force
                        strength = force
                        vision = force
                        defense = force
                    },
                    valueRange = 20f..99f
                )

                OutlinedTextField(
                    value = salaryText,
                    onValueChange = { salaryText = it },
                    label = { Text("Salário Semanal (R$)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Escalado como Titular", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Switch(
                        checked = isStarter,
                        onCheckedChange = { isStarter = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = AccentLime, checkedTrackColor = CardSurfaceDark)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("CANCELAR", color = Color.Gray)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (name.isNotBlank()) {
                                val salaryValue = salaryText.toLongOrNull() ?: player.salary
                                onSave(
                                    player.copy(
                                        name = name.trim(),
                                        age = age,
                                        nationality = nationality.trim(),
                                        position = position,
                                        force = force,
                                        salary = salaryValue,
                                        teamId = selectedTeamId,
                                        isStarter = isStarter,
                                        finishing = finishing,
                                        passing = passing,
                                        pace = pace,
                                        strength = strength,
                                        vision = vision,
                                        defense = defense
                                    )
                                )
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentLime, contentColor = TurfPitchDark)
                    ) {
                        Text("SALVAR", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// TRANSFER PLAYER DIALOG
@Composable
fun TransferPlayerDialog(
    player: Player,
    allTeams: List<Team>,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit
) {
    var selectedTeamId by remember { mutableStateOf(player.teamId) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = NeonMidnightSurface),
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, AccentLime)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "🔄 TRANSFERIR JOGADOR",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White
                )

                Text(
                    text = "Selecione o novo clube para ${player.name}:",
                    fontSize = 13.sp,
                    color = Color.White.copy(alpha = 0.8f)
                )

                LazyColumn(
                    modifier = Modifier.height(260.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(allTeams) { team ->
                        val isSelected = team.id == selectedTeamId
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedTeamId = team.id },
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) AccentLime.copy(alpha = 0.2f) else CardSurfaceDark
                            ),
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) AccentLime else Color.White.copy(alpha = 0.08f)
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TeamBadge(teamName = team.name, logoUrl = team.logoUrl, size = 30.dp, colorHex = team.colorHex)
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = team.name,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = "Série ${('A' + team.division - 1)}",
                                    fontSize = 11.sp,
                                    color = AccentGold,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("CANCELAR", color = Color.Gray)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { onConfirm(selectedTeamId) },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentLime, contentColor = TurfPitchDark)
                    ) {
                        Text("CONFIRMAR TRANSFERÊNCIA", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
