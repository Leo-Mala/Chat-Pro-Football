package com.example.ui.components.finances

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.GameSave
import com.example.data.Player
import com.example.ui.theme.*
import com.example.ui.viewmodel.*

@Composable
fun StaffPanel(viewModel: GameViewModel, save: GameSave?) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "GERENCIAMENTO DE COMISSÃO TÉCNICA (STAFF)",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp
        )
        Text(
            text = "Contrate profissionais especializados de nível mundial para elevar o desempenho técnico do clube.",
            color = Color.Gray,
            fontSize = 11.sp
        )

        // Treinador Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CardSurfaceDark),
            border = BorderStroke(1.dp, if (save?.hasHiredCoach == true) TurfDeepGreen else Color.White.copy(alpha = 0.1f)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Treinador de Elite (OVR Máximo)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text("Especialidade: Aceleração de Evolução de Jovens", color = Color.Gray, fontSize = 11.sp)
                    }
                    if (save?.hasHiredCoach == true) {
                        Surface(color = TurfDeepGreen, shape = RoundedCornerShape(4.dp)) {
                            Text("CONTRATADO • ATIVO", color = AccentLime, fontWeight = FontWeight.Bold, fontSize = 9.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp))
                        }
                    } else {
                        Text("R$ 8.000.000", color = AccentGold, fontWeight = FontWeight.Black, fontSize = 12.sp)
                    }
                }
                Text(
                    text = "Aumenta em 10% a chance semanal de evolução dos atributos (+1 OVR) para todos os jogadores do elenco profissional com menos de 23 anos.",
                    color = Color.LightGray,
                    fontSize = 11.sp
                )
                if (save?.hasHiredCoach != true) {
                    Button(
                        onClick = { viewModel.hireStaff("COACH") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentGold, contentColor = TurfDeepGreen)
                    ) {
                        Text("CONTRATAR COMISSÃO DE TREINADORES", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Fisioterapeuta Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CardSurfaceDark),
            border = BorderStroke(1.dp, if (save?.hasHiredPhysio == true) TurfDeepGreen else Color.White.copy(alpha = 0.1f)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Fisioterapeuta Chefe (Especialista)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text("Especialidade: Redução e Prevenção de Lesões", color = Color.Gray, fontSize = 11.sp)
                    }
                    if (save?.hasHiredPhysio == true) {
                        Surface(color = TurfDeepGreen, shape = RoundedCornerShape(4.dp)) {
                            Text("CONTRATADO • ATIVO", color = AccentLime, fontWeight = FontWeight.Bold, fontSize = 9.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp))
                        }
                    } else {
                        Text("R$ 5.000.000", color = AccentGold, fontWeight = FontWeight.Black, fontSize = 12.sp)
                    }
                }
                Text(
                    text = "Melhora as instalações médicas do clube reduzindo em 20% a duração média de todas as lesões graves sofridas durante as partidas profissionais.",
                    color = Color.LightGray,
                    fontSize = 11.sp
                )
                if (save?.hasHiredPhysio != true) {
                    Button(
                        onClick = { viewModel.hireStaff("PHYSIO") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentGold, contentColor = TurfDeepGreen)
                    ) {
                        Text("CONTRATAR DEPARTAMENTO DE FISIOTERAPIA", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun ScoutSelectionDialog(
    player: Player,
    viewModel: GameViewModel,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = CardSurfaceDark),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(2.dp, AccentGold)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    text = "CONTRATAR OLHEIRO",
                    color = AccentGold,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = "Escolha o nível de detalhamento do relatório técnico para ${player.name}:",
                    color = Color.LightGray,
                    fontSize = 12.sp
                )

                // Scout Level 1 option
                Card(
                    modifier = Modifier.fillMaxWidth().clickable {
                        viewModel.scoutPlayer(player, 1)
                        onDismiss()
                    },
                    colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Olheiro Nível 1", color = Color.White, fontWeight = FontWeight.Bold)
                            Text("R$ 50.000", color = AccentLime, fontWeight = FontWeight.Black)
                        }
                        Text("Revela os atributos com margem de erro de ±5 após 1 rodada.", color = Color.Gray, fontSize = 11.sp)
                    }
                }

                // Scout Level 3 option
                Card(
                    modifier = Modifier.fillMaxWidth().clickable {
                        viewModel.scoutPlayer(player, 3)
                        onDismiss()
                    },
                    colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Olheiro Nível 3", color = Color.White, fontWeight = FontWeight.Bold)
                            Text("R$ 150.000", color = AccentLime, fontWeight = FontWeight.Black)
                        }
                        Text("Revela os atributos com margem de erro de ±2 após 1 rodada.", color = Color.Gray, fontSize = 11.sp)
                    }
                }

                // Scout Level 5 option
                Card(
                    modifier = Modifier.fillMaxWidth().clickable {
                        viewModel.scoutPlayer(player, 5)
                        onDismiss()
                    },
                    colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Olheiro Nível 5 (Exato)", color = Color.White, fontWeight = FontWeight.Bold)
                            Text("R$ 400.000", color = AccentLime, fontWeight = FontWeight.Black)
                        }
                        Text("Revela os atributos reais e exatos sem erros após 1 rodada.", color = Color.Gray, fontSize = 11.sp)
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) {
                        Text("CANCELAR", color = Color.Gray)
                    }
                }
            }
        }
    }
}

@Composable
fun YouthAcademyDialog(rosterSize: Int, onDismiss: () -> Unit, onInvest: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = CardSurfaceDark),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Default.School, contentDescription = null, tint = AccentGold, modifier = Modifier.size(48.dp))
                Text("Categorias de Base", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold, fontSize = 16.sp)

                if (rosterSize >= 30) {
                    Text(
                        "⚠️ Seu elenco profissional já atingiu o limite máximo de 30 jogadores. Dispense ou venda algum jogador para poder revelar novas promessas.",
                        color = Color.Red,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Bold
                    )
                } else {
                    Text(
                        "Pague uma taxa de investimento de R$ 250.000 para revelar uma nova promessa das categorias de base diretamente para o elenco profissional!",
                        color = Color.Gray,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Gray.copy(alpha = 0.2f), contentColor = Color.White)
                    ) {
                        Text("CANCELAR", fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = onInvest,
                        enabled = rosterSize < 30,
                        modifier = Modifier.weight(1.5f),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentLime, contentColor = TurfDeepGreen)
                    ) {
                        Text(if (rosterSize >= 30) "ELENCO CHEIO" else "REVELAR (R$ 250k)", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}
