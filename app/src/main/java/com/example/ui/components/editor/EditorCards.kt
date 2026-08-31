package com.example.ui.components.editor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.Player
import com.example.data.Team
import com.example.ui.screens.TeamBadge
import com.example.ui.theme.*

@Composable
fun TabButton(
    text: String,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) AccentLime else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = if (isSelected) TurfPitchDark else Color.White.copy(alpha = 0.7f)
        )
    }
}

@Composable
fun TeamEditorCard(
    team: Team,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onViewSquad: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onViewSquad),
        colors = CardDefaults.cardColors(containerColor = CardSurfaceDark),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TeamBadge(
                teamName = team.name,
                logoUrl = team.logoUrl,
                teamId = team.id,
                size = 46.dp,
                colorHex = team.colorHex
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = team.name,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
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
                            text = "Série ${('A' + team.division - 1)}",
                            color = AccentGold,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = "${team.city}, ${team.state} (${team.country}) • Estádio: ${team.stadiumName}",
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Force Badge
            Surface(
                color = AccentLime.copy(alpha = 0.15f),
                shape = CircleShape,
                border = BorderStroke(1.dp, AccentLime.copy(alpha = 0.4f))
            ) {
                Text(
                    text = "${team.rating}",
                    color = AccentLime,
                    fontWeight = FontWeight.Black,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Action Buttons
            IconButton(onClick = onViewSquad, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Groups, contentDescription = "Ver Elenco", tint = AccentLime, modifier = Modifier.size(20.dp))
            }

            IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Edit, contentDescription = "Editar", tint = Color.White, modifier = Modifier.size(20.dp))
            }

            IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Delete, contentDescription = "Excluir", tint = NeonRedAccent, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
fun PlayerEditorCard(
    player: Player,
    teamName: String,
    onEdit: () -> Unit,
    onTransfer: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit),
        colors = CardDefaults.cardColors(containerColor = CardSurfaceDark),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Position Badge
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(getPositionBadgeColor(player.position).copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                    .border(1.dp, getPositionBadgeColor(player.position), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = player.position,
                    color = getPositionBadgeColor(player.position),
                    fontWeight = FontWeight.Black,
                    fontSize = 12.sp
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = player.name,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (player.isStarter) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            color = AccentLime.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "TITULAR",
                                color = AccentLime,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Black,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = "${player.age} anos • ${player.nationality} • $teamName",
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = "Salário: R$ %,d".format(player.salary),
                    fontSize = 10.sp,
                    color = AccentGold,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // Force
            Surface(
                color = getForceBadgeColor(player.force).copy(alpha = 0.2f),
                shape = CircleShape,
                border = BorderStroke(1.dp, getForceBadgeColor(player.force))
            ) {
                Text(
                    text = "${player.force}",
                    color = getForceBadgeColor(player.force),
                    fontWeight = FontWeight.Black,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            IconButton(onClick = onTransfer, modifier = Modifier.size(34.dp)) {
                Icon(Icons.Default.SwapHoriz, contentDescription = "Transferir", tint = AccentLime, modifier = Modifier.size(18.dp))
            }

            IconButton(onClick = onEdit, modifier = Modifier.size(34.dp)) {
                Icon(Icons.Default.Edit, contentDescription = "Editar", tint = Color.White, modifier = Modifier.size(18.dp))
            }

            IconButton(onClick = onDelete, modifier = Modifier.size(34.dp)) {
                Icon(Icons.Default.Delete, contentDescription = "Excluir", tint = NeonRedAccent, modifier = Modifier.size(18.dp))
            }
        }
    }
}

fun getPositionBadgeColor(position: String): Color {
    return when (position.uppercase()) {
        "GOL" -> Color(0xFFFFB300) // Gold/Amber
        "ZAG", "LAT" -> Color(0xFF1E88E5) // Blue
        "VOL", "MEI" -> Color(0xFF43A047) // Green
        "ATA" -> Color(0xFFE53935) // Red
        else -> Color(0xFF00E5FF) // Cyan
    }
}

fun getForceBadgeColor(force: Int): Color {
    return when {
        force >= 85 -> Color(0xFFFFD700) // Gold
        force >= 75 -> AccentLime // Neon Cyan/Green
        force >= 65 -> Color(0xFF2196F3) // Blue
        else -> Color.White
    }
}
