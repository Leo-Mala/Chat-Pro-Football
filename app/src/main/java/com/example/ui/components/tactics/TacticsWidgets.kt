package com.example.ui.components.tactics

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.Player
import com.example.ui.theme.*

@Composable
fun TacticalRoleSelector(
    roleLabel: String,
    roleIcon: String,
    selectedPlayerId: Long?,
    roster: List<Player>,
    onSelect: (Long) -> Unit
) {
    val selectedPlayer = roster.find { it.id == selectedPlayerId }
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text("$roleIcon $roleLabel", color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(4.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background)
        ) {
            Box {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { expanded = true }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = selectedPlayer?.let { "${it.name} (${it.position} - FOR ${it.force})" } ?: "Não Definido",
                        color = if (selectedPlayer != null) Color.White else Color.Gray,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = Color.White)
                }
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    modifier = Modifier.background(CardSurfaceDark)
                ) {
                    roster.forEach { p ->
                        DropdownMenuItem(
                            text = { Text("${p.name} (${p.position} - FOR ${p.force})", color = Color.White, fontSize = 11.sp) },
                            onClick = {
                                onSelect(p.id)
                                expanded = false
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SuggestionBadge(label: String) {
    Box(
        modifier = Modifier
            .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(label, color = Color.LightGray, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun SinglePitchPlayerDot(player: Player?, defaultPos: String, color: Color) {
    val displayName = player?.name?.split(" ")?.lastOrNull()?.take(10) ?: defaultPos
    val displayForce = player?.force?.toString() ?: defaultPos
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(55.dp)
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(if (player != null) color else Color.Gray.copy(alpha = 0.5f), CircleShape)
                .border(1.5.dp, if (player != null) Color.White else Color.White.copy(alpha = 0.5f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = displayForce,
                fontSize = if (player != null) 11.sp else 9.sp,
                fontWeight = FontWeight.Black,
                color = if (player != null) TurfPitchDark else Color.White
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = displayName,
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
            color = if (player != null) Color.White else Color.White.copy(alpha = 0.6f),
            maxLines = 1,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun PitchPlayerNode(pos: String, count: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(AccentLime, CircleShape)
                .border(1.5.dp, Color.White, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(pos, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TurfDeepGreen)
        }
        Text("x$count", color = MaterialTheme.colorScheme.onBackground, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}
