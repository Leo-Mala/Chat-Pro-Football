package com.example.ui.screens

import com.example.ui.viewmodel.*

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.example.ui.theme.*
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.example.data.PlayerDataParser
import com.example.data.ParsedJsonResult
import com.example.R
import com.example.data.*
import com.example.ui.viewmodel.GameViewModel
import coil.compose.rememberAsyncImagePainter

fun resolveLogoUrl(url: String?): String? {
    if (url.isNullOrBlank()) return null
    // First, decode the URL to avoid double-encoding issues
    val decoded = try {
        java.net.URLDecoder.decode(url, "UTF-8")
    } catch (e: Exception) {
        url
    }
    
    // Convert to a 240px PNG thumbnail if it's a Wikimedia SVG
    if (decoded.startsWith("https://upload.wikimedia.org/wikipedia/") && decoded.endsWith(".svg")) {
        val lastSlash = decoded.lastIndexOf('/')
        if (lastSlash != -1) {
            val fileName = decoded.substring(lastSlash + 1)
            val basePath = decoded.substring(0, lastSlash)
            val thumbPath = basePath.replace("/wikipedia/commons", "/wikipedia/commons/thumb")
                                    .replace("/wikipedia/en", "/wikipedia/en/thumb")
            return "$thumbPath/$fileName/240px-$fileName.png"
        }
    }
    return decoded
}

// Theme colors are imported from com.example.ui.theme.*

val ShieldColors = listOf(
    Color(0xFF1E88E5), // Blue
    Color(0xFFE53935), // Red
    Color(0xFF43A047), // Green
    Color(0xFFFDD835), // Yellow
    Color(0xFF8E24AA), // Purple
    Color(0xFFF4511E), // Deep Orange
    Color(0xFF00ACC1), // Cyan
    Color(0xFFD81B60), // Pink
    Color(0xFFFB8C00), // Orange
    Color(0xFF546E7A), // Blue Grey
    Color(0xFF3949AB), // Indigo
    Color(0xFF00897B), // Teal
)

fun getTeamColor(teamName: String, colorHex: String? = null): Color {
    if (!colorHex.isNullOrBlank()) {
        try {
            return Color(android.graphics.Color.parseColor(colorHex))
        } catch (e: Exception) {
            // ignore fallback
        }
    }
    val nameLower = teamName.lowercase()
    return when {
        nameLower.contains("flamengo") -> Color(0xFFC4122C)
        nameLower.contains("palmeiras") -> Color(0xFF006437)
        nameLower.contains("atlético mineiro") || nameLower.contains("atletico mineiro") || nameLower.contains("atletico-mg") -> Color(0xFF111111)
        nameLower.contains("cruzeiro") -> Color(0xFF0033A0)
        nameLower.contains("são paulo") || nameLower.contains("sao paulo") -> Color(0xFFE20E0E)
        nameLower.contains("fluminense") -> Color(0xFF800830)
        nameLower.contains("grêmio") || nameLower.contains("gremio") -> Color(0xFF0D80E3)
        nameLower.contains("internacional") -> Color(0xFFE31C24)
        nameLower.contains("botafogo") -> Color(0xFF111111)
        nameLower.contains("corinthians") -> Color(0xFF222222)
        nameLower.contains("vasco") -> Color(0xFF111111)
        nameLower.contains("santos") -> Color(0xFF333333)
        nameLower.contains("bahia") -> Color(0xFF0045A5)
        nameLower.contains("fortaleza") -> Color(0xFF002F93)
        nameLower.contains("real madrid") -> Color(0xFF00529F)
        nameLower.contains("barcelona") -> Color(0xFF004D98)
        nameLower.contains("atlético de madrid") || nameLower.contains("atletico de madrid") -> Color(0xFFCB3524)
        nameLower.contains("girona") -> Color(0xFFE2231A)
        nameLower.contains("arsenal") -> Color(0xFFEF0107)
        nameLower.contains("chelsea") -> Color(0xFF034694)
        nameLower.contains("liverpool") -> Color(0xFFC8102E)
        nameLower.contains("manchester city") -> Color(0xFF6CABDD)
        nameLower.contains("manchester united") -> Color(0xFFDA291C)
        nameLower.contains("tottenham") -> Color(0xFF132257)
        else -> {
            val hash = teamName.hashCode()
            val index = kotlin.math.abs(hash) % ShieldColors.size
            ShieldColors[index]
        }
    }
}

@Composable
fun TeamBadge(
    teamName: String,
    logoUrl: String?,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 44.dp,
    colorHex: String? = null
) {
    val badgeColor = getTeamColor(teamName, colorHex)
    var isSuccess by remember(logoUrl) { mutableStateOf(false) }

    Box(
        modifier = modifier
            .size(size)
            .background(
                brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                    colors = listOf(
                        badgeColor.copy(alpha = 0.85f),
                        badgeColor
                    )
                ),
                shape = CircleShape
            )
            .border(2.dp, Color.White.copy(alpha = 0.8f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        val abbrev = teamName.take(2).uppercase()

        val resolvedUrl = remember(logoUrl) { resolveLogoUrl(logoUrl) }
        val context = androidx.compose.ui.platform.LocalContext.current

        if (!resolvedUrl.isNullOrEmpty()) {
            val imageRequest = remember(resolvedUrl) {
                coil.request.ImageRequest.Builder(context)
                    .data(resolvedUrl)
                    .crossfade(true)
                    .diskCachePolicy(coil.request.CachePolicy.ENABLED)
                    .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
                    .build()
            }

            val painter = rememberAsyncImagePainter(
                model = imageRequest,
                onSuccess = { isSuccess = true },
                onError = { isSuccess = false }
            )
            Image(
                painter = painter,
                contentDescription = teamName,
                modifier = Modifier
                    .size(size * 0.8f)
                    .clip(CircleShape)
            )
        }

        if (!isSuccess) {
            val isLightColor = badgeColor.red > 0.8f && badgeColor.green > 0.8f && badgeColor.blue > 0.8f
            val textColor = if (isLightColor) Color(0xFF111111) else Color.White
            Text(
                text = abbrev,
                color = textColor,
                fontWeight = FontWeight.Black,
                fontSize = (size.value * 0.35f).sp
            )
        }
    }
}

@Composable
fun GameApp(
    viewModel: GameViewModel
) {
    val currentSaveId by viewModel.currentSaveId.collectAsStateWithLifecycle()
    val gameSave by viewModel.gameSave.collectAsStateWithLifecycle()
    val matchState by viewModel.matchState.collectAsStateWithLifecycle()
    val saveSlots by viewModel.saveSlots.collectAsStateWithLifecycle()

    val context = LocalContext.current
    var activeToast by remember { mutableStateOf<Toast?>(null) }
    LaunchedEffect(viewModel) {
        viewModel.toastMessage.collect { message ->
            activeToast?.cancel()
            val toast = Toast.makeText(context, message, Toast.LENGTH_SHORT)
            activeToast = toast
            toast.show()
        }
    }

    var menuScreenState by remember { mutableStateOf("MAIN_MENU") }

    androidx.activity.compose.BackHandler(enabled = menuScreenState != "MAIN_MENU") {
        if (viewModel.gameSave.value == null) {
            viewModel.exitToSavesMenu()
        }
        menuScreenState = "MAIN_MENU"
    }

    LaunchedEffect(currentSaveId) {
        if (currentSaveId == null) {
            menuScreenState = "MAIN_MENU"
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = TurfPitchDark
    ) {
        val selectedSlot = currentSaveId?.let { id -> saveSlots.firstOrNull { it.id == id } }
        val waitingForExistingCareer =
            currentSaveId != null && gameSave == null && selectedSlot?.exists == true

        val screenKey = remember(
            matchState,
            menuScreenState,
            currentSaveId,
            gameSave,
            waitingForExistingCareer
        ) {
            when {
                matchState != GameViewModel.MatchState.IDLE -> "LIVE_MATCH"
                menuScreenState == "EDITOR" -> "EDITOR"
                currentSaveId == null -> "MENU_$menuScreenState"
                waitingForExistingCareer -> "CAREER_LOADING"
                gameSave == null -> "TEAM_SELECTION"
                else -> "CAREER_DASHBOARD"
            }
        }

        AnimatedContent(
            targetState = screenKey,
            transitionSpec = {
                fadeIn(animationSpec = androidx.compose.animation.core.tween(250)) togetherWith fadeOut(animationSpec = androidx.compose.animation.core.tween(250))
            },
            label = "main_screen_transition",
            modifier = Modifier.fillMaxSize()
        ) { key ->
            when {
                key == "LIVE_MATCH" -> {
                    LiveMatchScreen(viewModel)
                }
                key == "EDITOR" -> {
                    TeamAndPlayerEditorScreen(
                        viewModel = viewModel,
                        onBack = {
                            viewModel.exitToSavesMenu()
                            menuScreenState = "MAIN_MENU"
                        }
                    )
                }
                key.startsWith("MENU_") -> {
                    when (menuScreenState) {
                        "MAIN_MENU" -> {
                            MainMenuScreen(
                                viewModel = viewModel,
                                onNewGame = {
                                    val emptySlot = saveSlots.firstOrNull { !it.exists }
                                    if (emptySlot != null) {
                                        viewModel.selectSaveSlotSafely(emptySlot.id)
                                    } else {
                                        menuScreenState = "SAVES"
                                    }
                                },
                                onOpenSaves = {
                                    menuScreenState = "SAVES"
                                },
                                onOpenEditor = {
                                    viewModel.ensureSaveActiveForEditor { ready ->
                                        if (ready) {
                                            menuScreenState = "EDITOR"
                                        }
                                    }
                                }
                            )
                        }
                        "SAVES" -> {
                            SavesScreen(
                                viewModel = viewModel,
                                onBack = { menuScreenState = "MAIN_MENU" }
                            )
                        }
                        "EDITOR" -> {
                            TeamAndPlayerEditorScreen(
                                viewModel = viewModel,
                                onBack = { menuScreenState = "MAIN_MENU" }
                            )
                        }
                    }
                }
                key == "CAREER_LOADING" -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("career_loading_guard"),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            CircularProgressIndicator(color = AccentLime)
                            Text(
                                text = "Carregando carreira...",
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                key == "TEAM_SELECTION" -> {
                    TeamSelectionScreen(
                        viewModel = viewModel,
                        coachName = "Técnico",
                        onBack = {
                            viewModel.exitToSavesMenu()
                        }
                    )
                }
                else -> {
                    CareerDashboardScreen(viewModel)
                }
            }
        }
    }
}

