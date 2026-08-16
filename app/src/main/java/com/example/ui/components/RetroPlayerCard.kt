package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import coil.compose.rememberAsyncImagePainter
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.example.data.Player
import com.example.ui.theme.*
import java.text.NumberFormat
import java.util.Locale

@Composable
fun RetroPlayerCard(
    player: Player,
    modifier: Modifier = Modifier,
    isExpandedDefault: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    var isExpanded by remember { mutableStateOf(isExpandedDefault) }

    val positionBgColor = when (player.position.uppercase().trim()) {
        "GOL", "GK" -> Color(0xFFD32F2F) // Deep Red
        "ZAG", "CB", "LAT", "LB", "RB" -> Color(0xFF1976D2) // Deep Blue
        "VOL", "DM", "MEI", "CM", "AM" -> Color(0xFF388E3C) // Emerald Green
        "ATA", "ST", "FW", "CF" -> Color(0xFFF57C00) // Deep Orange
        else -> Color(0xFF7B1FA2)
    }

    val goldFrameBrush = Brush.verticalGradient(
        colors = listOf(
            Color(0xFFFFD700),
            Color(0xFFFFA000),
            Color(0xFFB78103),
            Color(0xFFFFD700)
        )
    )

    val cardBgGradient = Brush.verticalGradient(
        colors = listOf(
            Color(0xFF0F172A), // Slate 900
            Color(0xFF020617), // Slate 950
            Color(0xFF051124)
        )
    )

    fun formatMoney(amount: Long): String {
        return if (amount <= 0L) {
            val mv = player.calculateMarketValue()
            val format = NumberFormat.getCurrencyInstance(Locale("pt", "BR"))
            format.format(mv)
        } else {
            val format = NumberFormat.getCurrencyInstance(Locale("pt", "BR"))
            format.format(amount)
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .shadow(8.dp, RoundedCornerShape(16.dp))
            .border(2.dp, goldFrameBrush, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .clickable {
                isExpanded = !isExpanded
                onClick?.invoke()
            }
            .animateContentSize()
            .testTag("player_card_${player.id}"),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(cardBgGradient)
                .padding(14.dp)
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Header: Retro Badge Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Left: Position & Nationality
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            color = positionBgColor,
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.6f))
                        ) {
                            Text(
                                text = player.position.uppercase(),
                                color = Color.White,
                                fontWeight = FontWeight.Black,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        Surface(
                            color = Color.White.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    Icons.Default.Public,
                                    contentDescription = null,
                                    tint = Color.LightGray,
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = player.nationality,
                                    color = Color.LightGray,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    // Right: Retro Overall Force Star Rating Badge
                    Box(
                        modifier = Modifier
                            .background(
                                Brush.horizontalGradient(
                                    listOf(Color(0xFFFFB300), Color(0xFFFF8F00))
                                ),
                                RoundedCornerShape(8.dp)
                            )
                            .border(1.dp, Color.White.copy(alpha = 0.8f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Star,
                                contentDescription = null,
                                tint = Color.Black,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "${player.force}",
                                color = Color.Black,
                                fontWeight = FontWeight.Black,
                                fontSize = 16.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }

                // Main Info Row: Image + Name & Market Value
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Player Portrait Photo Box
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.Black.copy(alpha = 0.4f))
                            .border(1.5.dp, goldFrameBrush, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (!player.imageUrl.isNullOrBlank()) {
                            val context = LocalContext.current
                            val imageRequest = remember(player.imageUrl) {
                                ImageRequest.Builder(context)
                                    .data(player.imageUrl)
                                    .crossfade(true)
                                    .diskCachePolicy(CachePolicy.ENABLED)
                                    .memoryCachePolicy(CachePolicy.ENABLED)
                                    .build()
                            }
                            Image(
                                painter = rememberAsyncImagePainter(imageRequest),
                                contentDescription = player.name,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Icon(
                                Icons.Default.SportsSoccer,
                                contentDescription = null,
                                tint = Color(0xFF00E5FF),
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }

                    // Name, Age & Market Value
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = player.name,
                            color = Color.White,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "${player.age} anos",
                                color = Color.LightGray,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )

                            if (player.isFromAcademy) {
                                Surface(
                                    color = Color(0xFF00E5FF).copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = "BASE",
                                        color = Color(0xFF00E5FF),
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        // Market Value Tag
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "VALOR:",
                                color = Color.Gray,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = formatMoney(player.market_value),
                                color = Color(0xFF00E5FF),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.ExtraBold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = "Ver detalhes",
                        tint = Color.LightGray,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Attributes Radar & Financial Breakdown (Expanded View)
                AnimatedVisibility(visible = isExpanded) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        HorizontalDivider(color = Color.White.copy(alpha = 0.15f))

                        Text(
                            text = "ATRIBUTOS RETRÔ",
                            color = Color(0xFFFFB300),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )

                        // 6 Key Cyber Attributes Grid
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                AttributeBar(label = "FINALIZ.", value = player.finishing)
                                AttributeBar(label = "PASSE", value = player.passing)
                                AttributeBar(label = "RITMO/VEL", value = player.pace)
                            }
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                AttributeBar(label = "FORÇA", value = player.strength)
                                AttributeBar(label = "VISÃO", value = player.vision)
                                AttributeBar(label = "DEFESA", value = player.defense)
                            }
                        }

                        Spacer(modifier = Modifier.height(2.dp))

                        // Market & Contract Footer Details
                        Surface(
                            color = Color.Black.copy(alpha = 0.35f),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "SALÁRIO MENSAL",
                                        color = Color.Gray,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = formatMoney(player.salary),
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }

                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = "CONTRATO",
                                        color = Color.Gray,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "${player.contractDurationWeeks} sem.",
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "DEMANDA",
                                        color = Color.Gray,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Surface(
                                        color = when (player.demand_level.lowercase()) {
                                            "high" -> Color.Red.copy(alpha = 0.3f)
                                            "medium" -> Color.Yellow.copy(alpha = 0.3f)
                                            else -> Color.Green.copy(alpha = 0.3f)
                                        },
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = player.demand_level.uppercase(),
                                            color = when (player.demand_level.lowercase()) {
                                                "high" -> Color(0xFFFF8888)
                                                "medium" -> Color(0xFFFFE082)
                                                else -> Color(0xFFA5D6A7)
                                            },
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
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
}

@Composable
private fun AttributeBar(label: String, value: Int) {
    val barColor = when {
        value >= 85 -> Color(0xFF00E5FF) // Cyan
        value >= 75 -> Color(0xFF4CAF50) // Green
        value >= 60 -> Color(0xFFFFB300) // Gold
        else -> Color(0xFFE53935) // Red
    }

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                color = Color.LightGray,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "$value",
                color = barColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        LinearProgressIndicator(
            progress = { (value / 100f).coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(CircleShape),
            color = barColor,
            trackColor = Color.White.copy(alpha = 0.1f)
        )
    }
}
