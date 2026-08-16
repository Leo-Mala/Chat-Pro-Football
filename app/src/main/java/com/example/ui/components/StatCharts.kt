package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AccentGold
import com.example.ui.theme.AccentLime
import com.example.ui.theme.CardSurfaceDark
import com.example.ui.theme.TurfDeepGreen

@Composable
fun PositionEvolutionChart(
    rankings: List<Int>,
    modifier: Modifier = Modifier,
    totalTeams: Int = 20
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardSurfaceDark),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "EVOLUÇÃO DA POSIÇÃO NA LIGA",
                    color = AccentGold,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "1º topo • ${totalTeams}º base",
                    color = Color.Gray,
                    fontSize = 10.sp
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (rankings.isEmpty()) {
                Text(
                    text = "Nenhuma rodada disputada ainda nesta temporada.",
                    color = Color.Gray,
                    fontSize = 11.sp
                )
            } else {
                val dataPoints = if (rankings.size == 1) listOf(rankings[0], rankings[0]) else rankings

                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .padding(horizontal = 8.dp, vertical = 8.dp)
                ) {
                    val width = size.width
                    val height = size.height
                    val maxVal = totalTeams.toFloat()
                    val minVal = 1f

                    val stepX = width / (dataPoints.size - 1).coerceAtLeast(1)

                    // Grid lines (Top 1st, Mid 10th, Bottom 20th)
                    val topY = 0f
                    val midY = height / 2f
                    val botY = height

                    drawLine(
                        color = Color.White.copy(alpha = 0.08f),
                        start = Offset(0f, topY),
                        end = Offset(width, topY),
                        strokeWidth = 1f
                    )
                    drawLine(
                        color = Color.White.copy(alpha = 0.08f),
                        start = Offset(0f, midY),
                        end = Offset(width, midY),
                        strokeWidth = 1f
                    )
                    drawLine(
                        color = Color.White.copy(alpha = 0.08f),
                        start = Offset(0f, botY),
                        end = Offset(width, botY),
                        strokeWidth = 1f
                    )

                    val path = Path()
                    val points = mutableListOf<Offset>()

                    dataPoints.forEachIndexed { i, rank ->
                        // Inverted Y: 1st place is at top (y=0), 20th place is at bottom (y=height)
                        val normY = (rank - minVal) / (maxVal - minVal)
                        val y = (normY * height).coerceIn(0f, height)
                        val x = i * stepX
                        points.add(Offset(x, y))

                        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }

                    // Draw line
                    drawPath(
                        path = path,
                        color = AccentLime,
                        style = Stroke(width = 3.dp.toPx())
                    )

                    // Draw points
                    points.forEachIndexed { idx, pt ->
                        drawCircle(
                            color = AccentLime,
                            radius = 4.dp.toPx(),
                            center = pt
                        )
                        drawCircle(
                            color = TurfDeepGreen,
                            radius = 2.dp.toPx(),
                            center = pt
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FinancialEvolutionChart(
    balanceHistory: List<Long>,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardSurfaceDark),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "GRÁFICO DE CAIXA FINANCEIRO (R$)",
                color = AccentGold,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (balanceHistory.isEmpty()) {
                Text(
                    text = "Sem histórico financeiro recente.",
                    color = Color.Gray,
                    fontSize = 11.sp
                )
            } else {
                val dataPoints = if (balanceHistory.size == 1) listOf(balanceHistory[0], balanceHistory[0]) else balanceHistory

                val maxBalance = dataPoints.maxOrNull()?.coerceAtLeast(1L) ?: 1L
                val minBalance = dataPoints.minOrNull()?.coerceAtMost(0L) ?: 0L

                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .padding(horizontal = 8.dp, vertical = 8.dp)
                ) {
                    val width = size.width
                    val height = size.height

                    val range = (maxBalance - minBalance).coerceAtLeast(1L).toFloat()
                    val stepX = width / (dataPoints.size - 1).coerceAtLeast(1)

                    val path = Path()
                    val fillPath = Path()
                    val points = mutableListOf<Offset>()

                    dataPoints.forEachIndexed { i, bal ->
                        val normY = 1f - ((bal - minBalance).toFloat() / range)
                        val y = (normY * height).coerceIn(0f, height)
                        val x = i * stepX
                        points.add(Offset(x, y))

                        if (i == 0) {
                            path.moveTo(x, y)
                            fillPath.moveTo(x, height)
                            fillPath.lineTo(x, y)
                        } else {
                            path.lineTo(x, y)
                            fillPath.lineTo(x, y)
                        }
                    }

                    fillPath.lineTo(width, height)
                    fillPath.close()

                    // Fill gradient
                    drawPath(
                        path = fillPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                AccentLime.copy(alpha = 0.35f),
                                Color.Transparent
                            )
                        )
                    )

                    // Draw line
                    drawPath(
                        path = path,
                        color = AccentLime,
                        style = Stroke(width = 3.dp.toPx())
                    )

                    // Draw points
                    points.forEach { pt ->
                        drawCircle(
                            color = AccentGold,
                            radius = 3.dp.toPx(),
                            center = pt
                        )
                    }
                }
            }
        }
    }
}
