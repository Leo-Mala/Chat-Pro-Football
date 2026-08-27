package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.ui.theme.AccentLime
import com.example.ui.viewmodel.GameViewModel
import com.example.ui.viewmodel.ensureSaveActiveForEditor

enum class PreCareerEditorPreparationState {
    PREPARING,
    READY,
    FAILED
}

/**
 * O Editor pré-carreira não pode renderizar um snapshot provisório enquanto o Room ainda está sendo
 * preparado. A UI só entra no editor real depois que a mesma sessão que será editada está pronta.
 */
@Composable
fun PreparedPreCareerEditorScreen(
    viewModel: GameViewModel,
    onBack: () -> Unit
) {
    var state by remember { mutableStateOf(PreCareerEditorPreparationState.PREPARING) }

    LaunchedEffect(viewModel) {
        state = PreCareerEditorPreparationState.PREPARING
        viewModel.ensureSaveActiveForEditor(
            onReady = { ready ->
                state = if (ready) {
                    PreCareerEditorPreparationState.READY
                } else {
                    PreCareerEditorPreparationState.FAILED
                }
            }
        )
    }

    when (state) {
        PreCareerEditorPreparationState.READY -> {
            TeamAndPlayerEditorScreen(
                viewModel = viewModel,
                onBack = onBack
            )
        }

        PreCareerEditorPreparationState.PREPARING -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("precareer_editor_loading_guard"),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    CircularProgressIndicator(color = AccentLime)
                    Text("Carregando dados salvos do Editor...", color = Color.White)
                }
            }
        }

        PreCareerEditorPreparationState.FAILED -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
                    .testTag("precareer_editor_failed_guard"),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text("Não foi possível preparar o Editor.", color = Color.White)
                    Button(onClick = onBack) {
                        Text("VOLTAR")
                    }
                }
            }
        }
    }
}