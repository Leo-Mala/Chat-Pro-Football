package com.example.ui.components.transfers

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.Player
import com.example.data.isInRosterLoanConversionFor
import com.example.ui.theme.*
import com.example.ui.viewmodel.*

internal data class PurchaseOfferRange(
    val marketValue: Long,
    val minimum: Long,
    val maximum: Long
)

internal fun purchaseOfferRange(player: Player): PurchaseOfferRange {
    val marketValue = player.calculateMarketValue()
    return PurchaseOfferRange(
        marketValue = marketValue,
        minimum = (marketValue * 0.5).toLong().coerceAtLeast(10_000L),
        maximum = (marketValue * 1.5).toLong()
    )
}

@Composable
fun PurchaseNegotiationDialog(
    player: Player,
    viewModel: GameViewModel,
    onPurchased: () -> Unit = {},
    onDismiss: () -> Unit
) {
    val save by viewModel.gameSave.collectAsStateWithLifecycle()
    val roster by viewModel.playerRoster.collectAsStateWithLifecycle()
    val rosterSize = roster.size
    val balance = save?.bankBalance ?: 0L
    val isInRosterLoanConversion = player.isInRosterLoanConversionFor(save?.playerTeamId)

    val editableRange = remember(player) { purchaseOfferRange(player) }
    val marketValue = editableRange.marketValue
    val minOffer = editableRange.minimum
    val maxOffer = editableRange.maximum

    var sliderValue by remember(player.id, marketValue) { mutableFloatStateOf(marketValue.toFloat()) }
    val offeredPrice = sliderValue.toLong()

    var offerResult by remember { mutableStateOf<GameViewModel.IAOfferResult?>(null) }
    var isSubmitting by remember { mutableStateOf(false) }
    var isPurchasing by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var paymentType by remember { mutableStateOf("VISTA") }
    var hasGoalBonus by remember { mutableStateOf(false) }
    var hasSolidarity by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = { if (!isPurchasing) onDismiss() },
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .padding(vertical = 12.dp),
            colors = CardDefaults.cardColors(containerColor = CardSurfaceDark),
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(2.dp, AccentGold)
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "NEGOCIAÇÃO DE ATLETA",
                        color = AccentGold,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Black
                    )
                    IconButton(onClick = onDismiss, enabled = !isPurchasing) {
                        Icon(Icons.Default.Close, contentDescription = "Fechar", tint = Color.White)
                    }
                }

                if (rosterSize >= 30 && !isInRosterLoanConversion) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.Red.copy(alpha = 0.1f)),
                        border = BorderStroke(1.dp, Color.Red),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = Color.Red, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Elenco profissional cheio (limite de 30 atletas). Venda ou dispense algum jogador primeiro.",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = TurfDeepGreen.copy(alpha = 0.4f)),
                    border = BorderStroke(0.5.dp, AccentLime.copy(alpha = 0.3f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .background(
                                    when (player.position) {
                                        "GOL" -> NeonGoldAccent.copy(alpha = 0.15f)
                                        "ZAG", "LAT" -> NeonBlueAccent.copy(alpha = 0.15f)
                                        "VOL", "MEI" -> NeonGreenAccent.copy(alpha = 0.15f)
                                        else -> NeonPurpleAccent.copy(alpha = 0.15f)
                                    },
                                    RoundedCornerShape(10.dp)
                                )
                                .border(
                                    1.dp,
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
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                Text(
                                    text = player.position,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black,
                                    color = when (player.position) {
                                        "GOL" -> NeonGoldAccent
                                        "ZAG", "LAT" -> NeonBlueAccent
                                        "VOL", "MEI" -> NeonGreenAccent
                                        else -> NeonPurpleAccent
                                    }
                                )
                                Text(
                                    text = player.force.toString(),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = AccentGold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column {
                            Text(
                                text = player.name,
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Black
                            )
                            Text(
                                text = "Força: ${player.force} • Idade: ${player.age} anos • Nal: ${player.nationality ?: "Sem clube"}",
                                color = Color.LightGray,
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Valor de Mercado:", color = Color.Gray, fontSize = 12.sp)
                    Text("R$ %,d".format(marketValue), color = AccentLime, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Seu Saldo:", color = Color.Gray, fontSize = 12.sp)
                    Text("R$ %,d".format(balance), color = if (balance >= marketValue) AccentLime else Color.Red, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }

                HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "COMPRA IMEDIATA",
                        color = Color.Gray,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Button(
                        onClick = {
                            isPurchasing = true
                            errorMessage = null
                            viewModel.executeInstantBuy(player) { success ->
                                isPurchasing = false
                                if (success) {
                                    onPurchased()
                                    onDismiss()
                                } else {
                                    errorMessage = "Não foi possível concluir a compra imediata. Verifique saldo, elenco e propriedade do atleta."
                                }
                            }
                        },
                        enabled = (rosterSize < 30 || isInRosterLoanConversion) && balance >= marketValue && !isPurchasing,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentGold, contentColor = TurfDeepGreen)
                    ) {
                        if (isPurchasing) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = TurfDeepGreen)
                        } else {
                            Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("COMPRAR AGORA (R$ %,d)".format(marketValue), fontWeight = FontWeight.Black, fontSize = 12.sp)
                        }
                    }
                }

                HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

                if (offerResult == null) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "PROPONHA UM VALOR DE OFERTA",
                            color = Color.Gray,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )

                        Text(
                            text = "Sua Oferta: R$ %,d".format(offeredPrice),
                            color = AccentGold,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )

                        Slider(
                            value = sliderValue,
                            onValueChange = { sliderValue = it },
                            valueRange = minOffer.toFloat()..maxOffer.toFloat(),
                            colors = SliderDefaults.colors(
                                thumbColor = AccentGold,
                                activeTrackColor = AccentLime,
                                inactiveTrackColor = Color.White.copy(alpha = 0.1f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Mín: R$ %,d".format(minOffer), color = Color.Gray, fontSize = 10.sp)
                            Text("Máx: R$ %,d".format(maxOffer), color = Color.Gray, fontSize = 10.sp)
                        }

                        Text("Forma de Pagamento:", color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("VISTA" to "À Vista (Dinheiro)", "PARCELADO" to "Parcelado (3x)").forEach { type ->
                                val isSelected = paymentType == type.first
                                Card(
                                    modifier = Modifier.weight(1f).clickable { paymentType = type.first },
                                    colors = CardDefaults.cardColors(containerColor = if (isSelected) AccentLime else Color.White.copy(alpha = 0.05f))
                                ) {
                                    Box(modifier = Modifier.padding(10.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                                        Text(type.second, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (isSelected) TurfDeepGreen else Color.LightGray)
                                    }
                                }
                            }
                        }

                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = hasGoalBonus,
                                onCheckedChange = { hasGoalBonus = it },
                                colors = CheckboxDefaults.colors(checkedColor = AccentLime, uncheckedColor = Color.Gray)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Column {
                                Text("Bônus de Gols (+R$ 500k se atingir 15 gols)", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Text("Aumenta a aceitação da proposta pelo clube vendedor", color = Color.Gray, fontSize = 10.sp)
                            }
                        }

                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = hasSolidarity,
                                onCheckedChange = { hasSolidarity = it },
                                colors = CheckboxDefaults.colors(checkedColor = AccentLime, uncheckedColor = Color.Gray)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Column {
                                Text("Cláusula de Solidariedade (15% da revenda)", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Text("Seller recebe 15% de qualquer transferência futura", color = Color.Gray, fontSize = 10.sp)
                            }
                        }

                        val downPaymentReq = offeredPrice / com.example.usecase.ProcessTransfersUseCase.INSTALLMENT_COUNT
                        val reqBalance = if (paymentType == "PARCELADO") downPaymentReq else offeredPrice
                        Button(
                            onClick = {
                                isSubmitting = true
                                errorMessage = null
                                viewModel.submitPurchaseOffer(player, offeredPrice, paymentType, hasGoalBonus, hasSolidarity) { result ->
                                    isSubmitting = false
                                    offerResult = result
                                }
                            },
                            enabled = (rosterSize < 30 || isInRosterLoanConversion) && balance >= reqBalance && !isSubmitting && !isPurchasing,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = AccentLime, contentColor = TurfDeepGreen)
                        ) {
                            if (isSubmitting) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = TurfDeepGreen)
                            } else {
                                Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("ENVIAR OFERTA", fontWeight = FontWeight.Black, fontSize = 12.sp)
                            }
                        }
                    }
                } else {
                    offerResult?.let { res ->
                        val cardColor = when (res.status) {
                            "accepted" -> TurfDeepGreen.copy(alpha = 0.3f)
                            "counter" -> AccentGold.copy(alpha = 0.15f)
                            else -> Color.Red.copy(alpha = 0.1f)
                        }
                        val borderColor = when (res.status) {
                            "accepted" -> AccentLime
                            "counter" -> AccentGold
                            else -> Color.Red
                        }

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = cardColor),
                            border = BorderStroke(1.dp, borderColor),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = when (res.status) {
                                        "accepted" -> "OFERTA ACEITA! 🎉"
                                        "counter" -> "CONTRA-PROPOSTA RECEBIDA! 📋"
                                        else -> "OFERTA RECUSADA ❌"
                                    },
                                    color = borderColor,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black
                                )

                                Text(
                                    text = res.message,
                                    color = Color.White,
                                    fontSize = 12.sp
                                )

                                if (res.status == "accepted") {
                                    Button(
                                        onClick = {
                                            isPurchasing = true
                                            errorMessage = null
                                            viewModel.buyPlayerAdvanced(player, offeredPrice, paymentType, hasGoalBonus, hasSolidarity) { result ->
                                                isPurchasing = false
                                                when (result) {
                                                    is com.example.usecase.ProcessTransfersUseCase.TransferResult.Success -> {
                                                        onPurchased()
                                                        onDismiss()
                                                    }
                                                    is com.example.usecase.ProcessTransfersUseCase.TransferResult.Error -> errorMessage = result.reason
                                                }
                                            }
                                        },
                                        enabled = !isPurchasing,
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.buttonColors(containerColor = AccentLime, contentColor = TurfDeepGreen)
                                    ) {
                                        if (isPurchasing) {
                                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = TurfDeepGreen)
                                        } else {
                                            Text("CONTRATAR ATLETA", fontWeight = FontWeight.Black)
                                        }
                                    }
                                } else if (res.status == "counter" && res.counterPrice != null) {
                                    val counterPrice = res.counterPrice
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Button(
                                            onClick = {
                                                if (balance >= counterPrice) {
                                                    isPurchasing = true
                                                    errorMessage = null
                                                    viewModel.buyPlayerAdvanced(player, counterPrice, paymentType, hasGoalBonus, hasSolidarity) { result ->
                                                        isPurchasing = false
                                                        when (result) {
                                                            is com.example.usecase.ProcessTransfersUseCase.TransferResult.Success -> {
                                                                onPurchased()
                                                                onDismiss()
                                                            }
                                                            is com.example.usecase.ProcessTransfersUseCase.TransferResult.Error -> errorMessage = result.reason
                                                        }
                                                    }
                                                } else {
                                                    errorMessage = "Saldo bancário insuficiente!"
                                                }
                                            },
                                            enabled = balance >= counterPrice && !isPurchasing,
                                            modifier = Modifier.weight(1.5f),
                                            colors = ButtonDefaults.buttonColors(containerColor = AccentLime, contentColor = TurfDeepGreen)
                                        ) {
                                            if (isPurchasing) {
                                                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = TurfDeepGreen)
                                            } else {
                                                Text("ACEITAR R$ %,d".format(counterPrice), fontWeight = FontWeight.Bold, fontSize = 10.sp)
                                            }
                                        }

                                        Button(
                                            onClick = { offerResult = null },
                                            enabled = !isPurchasing,
                                            modifier = Modifier.weight(1f),
                                            colors = ButtonDefaults.buttonColors(containerColor = Color.Gray.copy(alpha = 0.2f), contentColor = Color.White)
                                        ) {
                                            Text("RECUSAR/AJUSTAR", fontWeight = FontWeight.Bold, fontSize = 10.sp)
                                        }
                                    }
                                } else {
                                    Button(
                                        onClick = { offerResult = null },
                                        enabled = !isPurchasing,
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.buttonColors(containerColor = AccentGold, contentColor = TurfDeepGreen)
                                    ) {
                                        Text("FAZER NOVA OFERTA", fontWeight = FontWeight.Black)
                                    }
                                }
                            }
                        }
                    }
                }

                errorMessage?.let { error ->
                    Text(
                        text = error,
                        color = Color.Red,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}
