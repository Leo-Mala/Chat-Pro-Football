from pathlib import Path


def replace_once(path: str, old: str, new: str) -> None:
    p = Path(path)
    text = p.read_text(encoding="utf-8")
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{path}: expected exactly one anchor, found {count}: {old[:80]!r}")
    p.write_text(text.replace(old, new, 1), encoding="utf-8")


# ---------------------------------------------------------------------------
# Persist the selected coach avatar inside the existing per-save preferences.
# ---------------------------------------------------------------------------
replace_once(
    "app/src/main/java/com/example/data/GamePreferencesRepository.kt",
    '''        private val WATCHLIST_KEY = stringSetPreferencesKey("watchlist_players")
        private const val TAG = "GamePreferencesRepo"''',
    '''        private val WATCHLIST_KEY = stringSetPreferencesKey("watchlist_players")
        const val DEFAULT_COACH_AVATAR_ID = "coach_1"
        val SUPPORTED_COACH_AVATAR_IDS: Set<String> = setOf("coach_1", "coach_2", "coach_3", "coach_4")
        private const val TAG = "GamePreferencesRepo"'''
)

replace_once(
    "app/src/main/java/com/example/data/GamePreferencesRepository.kt",
    '''    val watchlistPlayers: Flow<Set<Long>> = dataStore.data.map { prefs ->
        val set = prefs[WATCHLIST_KEY] ?: legacyPrefs.getStringSet("watchlist_players", emptySet()) ?: emptySet()
        set.mapNotNull { it.toLongOrNull() }.toSet()
    }

    suspend fun setAutoSaveEnabled(enabled: Boolean) {''',
    '''    val watchlistPlayers: Flow<Set<Long>> = dataStore.data.map { prefs ->
        val set = prefs[WATCHLIST_KEY] ?: legacyPrefs.getStringSet("watchlist_players", emptySet()) ?: emptySet()
        set.mapNotNull { it.toLongOrNull() }.toSet()
    }

    fun coachAvatarId(saveId: String): Flow<String> {
        val preferenceKey = stringPreferencesKey("slot_${saveId}_coach_avatar")
        val legacyKey = "slot_${saveId}_coach_avatar"
        return dataStore.data.map { prefs ->
            val stored = prefs[preferenceKey]
                ?: legacyPrefs.getString(legacyKey, DEFAULT_COACH_AVATAR_ID)
                ?: DEFAULT_COACH_AVATAR_ID
            stored.takeIf { it in SUPPORTED_COACH_AVATAR_IDS } ?: DEFAULT_COACH_AVATAR_ID
        }
    }

    suspend fun setCoachAvatarId(saveId: String, avatarId: String) {
        require(avatarId in SUPPORTED_COACH_AVATAR_IDS) { "Avatar interno inválido: $avatarId" }
        val preferenceKey = stringPreferencesKey("slot_${saveId}_coach_avatar")
        val legacyKey = "slot_${saveId}_coach_avatar"
        dataStore.edit { prefs ->
            prefs[preferenceKey] = avatarId
        }
        check(legacyPrefs.edit().putString(legacyKey, avatarId).commit()) {
            "Falha ao persistir avatar do técnico para o slot $saveId"
        }
    }

    suspend fun setAutoSaveEnabled(enabled: Boolean) {'''
)

replace_once(
    "app/src/main/java/com/example/data/GamePreferencesRepository.kt",
    '''                prefs.remove(longPreferencesKey("slot_${saveId}_balance"))
            }''',
    '''                prefs.remove(longPreferencesKey("slot_${saveId}_balance"))
                prefs.remove(stringPreferencesKey("slot_${saveId}_coach_avatar"))
            }'''
)

replace_once(
    "app/src/main/java/com/example/data/GamePreferencesRepository.kt",
    '''            .remove("slot_${saveId}_balance")
            .commit()''',
    '''            .remove("slot_${saveId}_balance")
            .remove("slot_${saveId}_coach_avatar")
            .commit()'''
)

# ---------------------------------------------------------------------------
# Expose one reactive avatar state for the active save in the existing VM.
# ---------------------------------------------------------------------------
replace_once(
    "app/src/main/java/com/example/ui/viewmodel/GameViewModel.kt",
    '''    internal val _currentSaveId = MutableStateFlow<String?>(null)
    val currentSaveId = _currentSaveId.asStateFlow()

    private val sessionGeneration = java.util.concurrent.atomic.AtomicLong(0L)''',
    '''    internal val _currentSaveId = MutableStateFlow<String?>(null)
    val currentSaveId = _currentSaveId.asStateFlow()

    val coachAvatarId: StateFlow<String> = currentSaveId.flatMapLatest { saveId ->
        if (saveId.isNullOrBlank()) {
            flowOf(GamePreferencesRepository.DEFAULT_COACH_AVATAR_ID)
        } else {
            preferencesRepo.coachAvatarId(saveId)
        }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        GamePreferencesRepository.DEFAULT_COACH_AVATAR_ID
    )

    private val sessionGeneration = java.util.concurrent.atomic.AtomicLong(0L)'''
)

replace_once(
    "app/src/main/java/com/example/ui/viewmodel/GameViewModel.kt",
    '''    fun setAutoLineupEnabled(enabled: Boolean) {
        _autoLineupEnabled.value = enabled
        viewModelScope.launch(Dispatchers.IO) {
            preferencesRepo.setAutoLineupEnabled(enabled)
        }
    }

    internal fun getFormationRoles(formation: String): List<String> {''',
    '''    fun setAutoLineupEnabled(enabled: Boolean) {
        _autoLineupEnabled.value = enabled
        viewModelScope.launch(Dispatchers.IO) {
            preferencesRepo.setAutoLineupEnabled(enabled)
        }
    }

    fun setCoachAvatarId(avatarId: String) {
        val saveId = _currentSaveId.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            preferencesRepo.setCoachAvatarId(saveId, avatarId)
        }
    }

    internal fun getFormationRoles(formation: String): List<String> {'''
)

# ---------------------------------------------------------------------------
# Internal, permission-free avatar renderer. No camera/gallery/file URI path.
# ---------------------------------------------------------------------------
avatar_component = r'''package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import com.example.ui.theme.AccentLime

/** A compact internal avatar catalog. No external storage, camera or gallery is used. */
data class CoachAvatarOption(
    val id: String,
    val label: String,
    val skinColor: Color,
    val hairColor: Color,
    val shirtColor: Color,
    val backgroundColor: Color
)

val CoachAvatarOptions: List<CoachAvatarOption> = listOf(
    CoachAvatarOption(
        id = "coach_1",
        label = "Clássico",
        skinColor = Color(0xFFF1C7A5),
        hairColor = Color(0xFF2C211D),
        shirtColor = Color(0xFF1565C0),
        backgroundColor = Color(0xFF10233D)
    ),
    CoachAvatarOption(
        id = "coach_2",
        label = "Executivo",
        skinColor = Color(0xFFD9A57E),
        hairColor = Color(0xFF4E342E),
        shirtColor = Color(0xFF2E7D32),
        backgroundColor = Color(0xFF17331D)
    ),
    CoachAvatarOption(
        id = "coach_3",
        label = "Veterano",
        skinColor = Color(0xFF8D5A3A),
        hairColor = Color(0xFF161616),
        shirtColor = Color(0xFF7B1F35),
        backgroundColor = Color(0xFF35131D)
    ),
    CoachAvatarOption(
        id = "coach_4",
        label = "Moderno",
        skinColor = Color(0xFFE0B28D),
        hairColor = Color(0xFFB0BEC5),
        shirtColor = Color(0xFFC69214),
        backgroundColor = Color(0xFF30280F)
    )
)

@Composable
fun CoachAvatar(
    avatarId: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false
) {
    val avatar = CoachAvatarOptions.firstOrNull { it.id == avatarId } ?: CoachAvatarOptions.first()
    Canvas(modifier = modifier.aspectRatio(1f).clip(CircleShape)) {
        val unit = size.minDimension
        val centerX = size.width / 2f

        drawCircle(
            color = avatar.backgroundColor,
            radius = unit / 2f,
            center = Offset(centerX, size.height / 2f)
        )

        // Torso and neck are deliberately centered so every option fits the same circular mask.
        drawRoundRect(
            color = avatar.shirtColor,
            topLeft = Offset(size.width * 0.17f, size.height * 0.63f),
            size = Size(size.width * 0.66f, size.height * 0.40f),
            cornerRadius = CornerRadius(unit * 0.20f, unit * 0.20f)
        )
        drawRoundRect(
            color = avatar.skinColor,
            topLeft = Offset(size.width * 0.43f, size.height * 0.52f),
            size = Size(size.width * 0.14f, size.height * 0.18f),
            cornerRadius = CornerRadius(unit * 0.05f, unit * 0.05f)
        )

        // Hair behind the face gives each portrait a recognizable head silhouette.
        drawCircle(
            color = avatar.hairColor,
            radius = unit * 0.235f,
            center = Offset(centerX, size.height * 0.355f)
        )
        drawCircle(
            color = avatar.skinColor,
            radius = unit * 0.205f,
            center = Offset(centerX, size.height * 0.405f)
        )

        val featureColor = Color(0xFF241914)
        drawCircle(
            color = featureColor,
            radius = unit * 0.018f,
            center = Offset(centerX - unit * 0.072f, size.height * 0.395f)
        )
        drawCircle(
            color = featureColor,
            radius = unit * 0.018f,
            center = Offset(centerX + unit * 0.072f, size.height * 0.395f)
        )
        drawLine(
            color = featureColor.copy(alpha = 0.75f),
            start = Offset(centerX - unit * 0.050f, size.height * 0.475f),
            end = Offset(centerX + unit * 0.050f, size.height * 0.475f),
            strokeWidth = unit * 0.014f
        )

        drawCircle(
            color = if (selected) AccentLime else Color.White.copy(alpha = 0.28f),
            radius = unit * 0.48f,
            center = Offset(centerX, size.height / 2f),
            style = Stroke(width = if (selected) unit * 0.035f else unit * 0.018f)
        )
    }
}
'''
component_path = Path("app/src/main/java/com/example/ui/components/CoachAvatar.kt")
if component_path.exists():
    raise SystemExit(f"{component_path}: file unexpectedly already exists")
component_path.write_text(avatar_component, encoding="utf-8")

# ---------------------------------------------------------------------------
# Coach profile: choose among internal avatars and display the selected one.
# ---------------------------------------------------------------------------
replace_once(
    "app/src/main/java/com/example/ui/screens/CoachScreen.kt",
    '''import com.example.ui.viewmodel.*

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.example.ui.theme.*''',
    '''import com.example.ui.viewmodel.*
import com.example.ui.components.CoachAvatar
import com.example.ui.components.CoachAvatarOptions

import com.example.ui.theme.*'''
)

replace_once(
    "app/src/main/java/com/example/ui/screens/CoachScreen.kt",
    '''    val allTeams by viewModel.allTeams.collectAsStateWithLifecycle()

    var showEditorDialog by remember { mutableStateOf(false) }''',
    '''    val allTeams by viewModel.allTeams.collectAsStateWithLifecycle()
    val coachAvatarId by viewModel.coachAvatarId.collectAsStateWithLifecycle()

    var showEditorDialog by remember { mutableStateOf(false) }'''
)

replace_once(
    "app/src/main/java/com/example/ui/screens/CoachScreen.kt",
    '''            Column(modifier = Modifier.padding(16.dp)) {
                Text("Perfil do Treinador", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(12.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Nome:", color = Color.Gray, fontSize = 13.sp)
                    Text(s.coachName, color = MaterialTheme.colorScheme.onBackground, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Reputação:", color = Color.Gray, fontSize = 13.sp)
                    Text("${s.coachReputation}/100", color = AccentGold, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }''',
    '''            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Perfil do Treinador", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(12.dp))

                CoachAvatar(
                    avatarId = coachAvatarId,
                    modifier = Modifier.size(96.dp).testTag("coach_avatar_selected")
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(s.coachName, color = MaterialTheme.colorScheme.onBackground, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text("Reputação ${s.coachReputation}/100", color = AccentGold, fontSize = 12.sp, fontWeight = FontWeight.Bold)

                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    "Escolha seu avatar",
                    color = Color.Gray,
                    fontSize = 11.sp,
                    modifier = Modifier.align(Alignment.Start)
                )
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(
                    modifier = Modifier.fillMaxWidth().testTag("coach_avatar_selector"),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(CoachAvatarOptions, key = { it.id }) { option ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { viewModel.setCoachAvatarId(option.id) }
                                .padding(4.dp)
                                .testTag("coach_avatar_option_${option.id}")
                        ) {
                            CoachAvatar(
                                avatarId = option.id,
                                modifier = Modifier.size(58.dp),
                                selected = option.id == coachAvatarId
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                option.label,
                                color = if (option.id == coachAvatarId) AccentLime else Color.Gray,
                                fontSize = 10.sp,
                                fontWeight = if (option.id == coachAvatarId) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }
            }'''
)

# ---------------------------------------------------------------------------
# Dashboard header uses the exact same selected avatar instead of Person icon.
# ---------------------------------------------------------------------------
replace_once(
    "app/src/main/java/com/example/ui/screens/DashboardScreen.kt",
    '''import com.example.ui.components.dashboard.DashboardTab
import com.example.ui.components.squad.*''',
    '''import com.example.ui.components.CoachAvatar
import com.example.ui.components.dashboard.DashboardTab
import com.example.ui.components.squad.*'''
)

replace_once(
    "app/src/main/java/com/example/ui/screens/DashboardScreen.kt",
    '''    val playerRoster by viewModel.playerRoster.collectAsStateWithLifecycle()
    val selectedCountry by viewModel.selectedCountry.collectAsStateWithLifecycle()''',
    '''    val playerRoster by viewModel.playerRoster.collectAsStateWithLifecycle()
    val coachAvatarId by viewModel.coachAvatarId.collectAsStateWithLifecycle()
    val selectedCountry by viewModel.selectedCountry.collectAsStateWithLifecycle()'''
)

replace_once(
    "app/src/main/java/com/example/ui/screens/DashboardScreen.kt",
    '''                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(AccentLime.copy(alpha = 0.1f), CircleShape)
                                    .border(1.5.dp, AccentLime.copy(alpha = 0.3f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Person,
                                    contentDescription = null,
                                    tint = AccentLime,
                                    modifier = Modifier.size(20.dp)
                                )
                            }''',
    '''                            CoachAvatar(
                                avatarId = coachAvatarId,
                                modifier = Modifier.size(40.dp).testTag("dashboard_coach_avatar")
                            )'''
)

# ---------------------------------------------------------------------------
# Regression: selection is isolated per save and survives repository reopen.
# ---------------------------------------------------------------------------
test_source = r'''package com.example

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import com.example.data.GamePreferencesRepository
import com.example.data.dataStore
import com.example.data.local.SlotDatabaseFactory
import com.example.data.repository.GameSaveRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class CoachAvatarPersistenceTest {

    private lateinit var context: Context
    private lateinit var databaseFactory: SlotDatabaseFactory
    private lateinit var saveRepository: GameSaveRepository
    private lateinit var repository: GamePreferencesRepository

    @Before
    fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        context.dataStore.edit { it.clear() }
        context.getSharedPreferences("brasfut_retro_saves", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        databaseFactory = SlotDatabaseFactory(context)
        saveRepository = GameSaveRepository(context, databaseFactory)
        repository = GamePreferencesRepository(context.dataStore, context, saveRepository)
    }

    @After
    fun tearDown() = runBlocking {
        saveRepository.closeAllDatabases()
        context.dataStore.edit { it.clear() }
        context.getSharedPreferences("brasfut_retro_saves", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun `selected avatar is isolated by career and survives repository reopen`() = runBlocking {
        repository.setCoachAvatarId("1", "coach_3")
        repository.setCoachAvatarId("2", "coach_4")

        assertEquals("coach_3", repository.coachAvatarId("1").first())
        assertEquals("coach_4", repository.coachAvatarId("2").first())
        assertEquals(
            GamePreferencesRepository.DEFAULT_COACH_AVATAR_ID,
            repository.coachAvatarId("3").first()
        )

        val reopened = GamePreferencesRepository(context.dataStore, context, saveRepository)
        assertEquals("coach_3", reopened.coachAvatarId("1").first())
        assertEquals("coach_4", reopened.coachAvatarId("2").first())
    }
}
'''
test_path = Path("app/src/test/java/com/example/CoachAvatarPersistenceTest.kt")
if test_path.exists():
    raise SystemExit(f"{test_path}: file unexpectedly already exists")
test_path.write_text(test_source, encoding="utf-8")

# Strong source-level guard for the requested permission-free implementation.
coach_text = Path("app/src/main/java/com/example/ui/screens/CoachScreen.kt").read_text(encoding="utf-8")
for forbidden in ("rememberLauncherForActivityResult", "ActivityResultContracts", "android.net.Uri"):
    if forbidden in coach_text:
        raise SystemExit(f"forbidden camera/gallery integration remains in CoachScreen: {forbidden}")

print("P3 coach avatar patch prepared successfully")
