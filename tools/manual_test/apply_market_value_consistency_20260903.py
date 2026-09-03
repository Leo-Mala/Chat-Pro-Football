#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def replace_once(path: str, old: str, new: str) -> None:
    target = ROOT / path
    text = target.read_text(encoding="utf-8")
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"expected exactly one match in {path}, found {count}")
    target.write_text(text.replace(old, new, 1), encoding="utf-8")


def write_new(path: str, content: str) -> None:
    target = ROOT / path
    if target.exists():
        raise SystemExit(f"refusing to overwrite existing file: {path}")
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(content, encoding="utf-8")

# 1) Make Player.calculateMarketValue() the single canonical formula while preserving the
#    exact formula that already powered the Market list/filter/instant-buy path.
replace_once(
    "app/src/main/java/com/example/data/entities.kt",
    '''    fun calculateMarketValue(): Long {
        val baseValue = when {
            force < 40 -> force * 12000L
            force < 60 -> 40 * 12000L + (force - 40) * 60000L
            force < 75 -> 40 * 12000L + 20 * 60000L + (force - 60) * 450000L
            force < 85 -> 40 * 12000L + 20 * 60000L + 15 * 450000L + (force - 75) * 1800000L
            else -> 40 * 12000L + 20 * 60000L + 15 * 450000L + 10 * 1800000L + (force - 85) * 6000000L
        }
        val ageFactor = when {
            age < 23 -> 0.8 + (age - 15) * 0.025
            age in 23..29 -> 1.0
            age in 30..33 -> 1.0 - (age - 29) * 0.07
            else -> (0.72 - (age - 33) * 0.07).coerceAtLeast(0.12)
        }
        return (baseValue * ageFactor).toLong().coerceAtLeast(40000L)
    }
''',
    '''    /**
     * Canonical market value used by every transfer surface.
     *
     * This preserves the price formula that already powered the Market list, sorting, price filter,
     * negotiation decisions and instant purchase. Keeping the rule here prevents UI surfaces from
     * silently recomputing a second, incompatible value for the same player.
     */
    fun calculateMarketValue(): Long {
        val baseValue = force * 150_000L + potential * 100_000L
        val ageFactor = when {
            age < 21 -> 1.5
            age < 28 -> 1.2
            age < 32 -> 0.9
            else -> 0.6
        }
        return (baseValue * ageFactor).toLong().coerceAtLeast(100_000L)
    }
'''
)

replace_once(
    "app/src/main/java/com/example/usecase/TransferNegotiationUseCase.kt",
    '''        fun calculateDynamicPlayerPrice(player: Player): Long {
            val base = player.force * 150_000L + player.potential * 100_000L
            val ageFactor = when {
                player.age < 21 -> 1.5
                player.age < 28 -> 1.2
                player.age < 32 -> 0.9
                else -> 0.6
            }
            return (base * ageFactor).toLong().coerceAtLeast(100_000L)
        }
''',
    '''        fun calculateDynamicPlayerPrice(player: Player): Long = player.calculateMarketValue()
'''
)

# 2) The squad/player detail must not prefer a stale persisted market_value over the canonical rule.
replace_once(
    "app/src/main/java/com/example/ui/components/squad/SquadComponents.kt",
    '''    val valorMercado = remember(player.market_value, player) {
        if (player.market_value > 0) player.market_value else player.calculateMarketValue()
    }
''',
    '''    val valorMercado = remember(player) { player.calculateMarketValue() }
'''
)

# 3) Expose the actual editable negotiation range as a pure helper and make the dialog use it.
purchase_path = "app/src/main/java/com/example/ui/components/transfers/PurchaseNegotiationDialog.kt"
replace_once(
    purchase_path,
    '''import com.example.ui.viewmodel.*

@Composable
fun PurchaseNegotiationDialog(
''',
    '''import com.example.ui.viewmodel.*

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
'''
)
replace_once(
    purchase_path,
    '''    val marketValue = player.calculateMarketValue()
    val minOffer = (marketValue * 0.5).toLong().coerceAtLeast(10000L)
    val maxOffer = (marketValue * 1.5).toLong()

    var sliderValue by remember { mutableFloatStateOf(marketValue.toFloat()) }
''',
    '''    val editableRange = remember(player) { purchaseOfferRange(player) }
    val marketValue = editableRange.marketValue
    val minOffer = editableRange.minimum
    val maxOffer = editableRange.maximum

    var sliderValue by remember(player.id, marketValue) { mutableFloatStateOf(marketValue.toFloat()) }
'''
)

# 4) Make Market filtering testable and prevent an old result list from being rendered under a newly
#    selected filter chip while the background recomputation is still running.
transfers_path = "app/src/main/java/com/example/ui/screens/TransfersScreen.kt"
replace_once(
    transfers_path,
    '''import kotlinx.coroutines.withContext

@Composable
fun MarketTab(viewModel: GameViewModel) {
''',
    '''import kotlinx.coroutines.withContext

internal data class MarketSearchCriteria(
    val query: String,
    val position: String,
    val minimumForce: Int,
    val maximumAge: Int,
    val maximumPrice: Long,
    val sortBy: String
)

internal data class MarketSearchKey(
    val criteria: MarketSearchCriteria,
    val playerTeamId: Long?,
    val locallyPurchasedIds: Set<Long>
)

internal data class MarketSearchResult(
    val key: MarketSearchKey,
    val players: List<Player>
)

internal fun filterAndSortMarketPlayers(
    candidates: List<Player>,
    criteria: MarketSearchCriteria
): List<Player> = candidates.filter { player ->
    (criteria.query.isBlank() || player.name.contains(criteria.query, ignoreCase = true)) &&
        (criteria.position == "TODOS" || player.position == criteria.position) &&
        player.force >= criteria.minimumForce &&
        player.age <= criteria.maximumAge &&
        (criteria.maximumPrice >= 500_000_000L || player.calculateMarketValue() <= criteria.maximumPrice)
}.let { filtered ->
    when (criteria.sortBy) {
        "FORCA_DESC" -> filtered.sortedByDescending { it.force }
        "FORCA_ASC" -> filtered.sortedBy { it.force }
        "IDADE_ASC" -> filtered.sortedBy { it.age }
        "IDADE_DESC" -> filtered.sortedByDescending { it.age }
        "NOME" -> filtered.sortedBy { it.name }
        "VALOR_ASC" -> filtered.sortedBy { it.calculateMarketValue() }
        "VALOR_DESC" -> filtered.sortedByDescending { it.calculateMarketValue() }
        else -> filtered
    }
}.take(80)

@Composable
fun MarketTab(viewModel: GameViewModel) {
'''
)
old_market_block = '''    var availablePlayers by remember { mutableStateOf<List<Player>>(emptyList()) }
    LaunchedEffect(
        allPlayers,
        save?.playerTeamId,
        searchQuery,
        searchPos,
        searchMinForce,
        searchMaxAge,
        searchMaxPrice,
        searchSortBy,
        locallyPurchasedIds
    ) {
        availablePlayers = withContext(Dispatchers.Default) {
            allPlayers.filter { player ->
                player.id !in locallyPurchasedIds &&
                player.isTransferMarketCandidateFor(save?.playerTeamId) &&
                (searchQuery.isBlank() || player.name.contains(searchQuery, ignoreCase = true)) &&
                (searchPos == "TODOS" || player.position == searchPos) &&
                player.force >= searchMinForce &&
                player.age <= searchMaxAge &&
                (searchMaxPrice >= 500_000_000L || viewModel.getDynamicPlayerPrice(player) <= searchMaxPrice)
            }.let { filtered ->
                when (searchSortBy) {
                    "FORCA_DESC" -> filtered.sortedByDescending { it.force }
                    "FORCA_ASC" -> filtered.sortedBy { it.force }
                    "IDADE_ASC" -> filtered.sortedBy { it.age }
                    "IDADE_DESC" -> filtered.sortedByDescending { it.age }
                    "NOME" -> filtered.sortedBy { it.name }
                    "VALOR_ASC" -> filtered.sortedBy { viewModel.getDynamicPlayerPrice(it) }
                    "VALOR_DESC" -> filtered.sortedByDescending { viewModel.getDynamicPlayerPrice(it) }
                    else -> filtered
                }
            }.take(80)
        }
    }
'''
new_market_block = '''    val searchCriteria = remember(
        searchQuery, searchPos, searchMinForce, searchMaxAge, searchMaxPrice, searchSortBy
    ) {
        MarketSearchCriteria(
            query = searchQuery,
            position = searchPos,
            minimumForce = searchMinForce,
            maximumAge = searchMaxAge,
            maximumPrice = searchMaxPrice,
            sortBy = searchSortBy
        )
    }
    val searchKey = remember(searchCriteria, save?.playerTeamId, locallyPurchasedIds) {
        MarketSearchKey(searchCriteria, save?.playerTeamId, locallyPurchasedIds.toSet())
    }
    var marketSearchResult by remember {
        mutableStateOf(MarketSearchResult(searchKey, emptyList()))
    }
    LaunchedEffect(allPlayers, searchKey) {
        val requestedKey = searchKey
        // Never show a previous filter's rows under the newly-selected filter chip.
        marketSearchResult = MarketSearchResult(requestedKey, emptyList())
        val computed = withContext(Dispatchers.Default) {
            val candidates = allPlayers.filter { player ->
                player.id !in requestedKey.locallyPurchasedIds &&
                    player.isTransferMarketCandidateFor(requestedKey.playerTeamId)
            }
            filterAndSortMarketPlayers(candidates, requestedKey.criteria)
        }
        marketSearchResult = MarketSearchResult(requestedKey, computed)
    }
    val availablePlayers = if (marketSearchResult.key == searchKey) {
        marketSearchResult.players
    } else {
        emptyList()
    }
'''
replace_once(transfers_path, old_market_block, new_market_block)

# 5) Regression tests for the exact manual evidence and the transaction invariants.
write_new("app/src/test/java/com/example/usecase/MarketPlayerValueConsistencyRegressionTest.kt", r'''package com.example.usecase

import com.example.data.Player
import org.junit.Assert.assertEquals
import org.junit.Test

class MarketPlayerValueConsistencyRegressionTest {
    @Test
    fun `Igor Almeida uses the same canonical value in player and negotiation policy`() {
        val player = Player(
            id = 9001L,
            teamId = null,
            name = "Igor Almeida",
            age = 25,
            position = "GOL",
            force = 99,
            potential = 99,
            market_value = 110_430_000L
        )

        val canonical = player.calculateMarketValue()

        assertEquals(29_700_000L, canonical)
        assertEquals(canonical, TransferNegotiationUseCase.calculateDynamicPlayerPrice(player))
        // A legacy persisted field must never silently override the value shown/charged by the Market.
        assertEquals(29_700_000L, player.calculateMarketValue())
    }

    @Test
    fun `same policy remains deterministic for a lower force player`() {
        val player = Player(
            id = 9002L,
            teamId = 2L,
            name = "Declan White",
            age = 25,
            position = "ZAG",
            force = 80,
            potential = 80
        )
        assertEquals(player.calculateMarketValue(), TransferNegotiationUseCase.calculateDynamicPlayerPrice(player))
    }
}
''')

write_new("app/src/test/java/com/example/ui/components/transfers/MarketNegotiationRangeRegressionTest.kt", r'''package com.example.ui.components.transfers

import com.example.data.Player
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarketNegotiationRangeRegressionTest {
    @Test
    fun `manual evidence player opens negotiation around the exact market card value`() {
        val player = Player(id = 1L, teamId = null, name = "Igor Almeida", age = 25, position = "GOL", force = 99, potential = 99)
        val range = purchaseOfferRange(player)

        assertEquals(29_700_000L, range.marketValue)
        assertEquals(14_850_000L, range.minimum)
        assertEquals(44_550_000L, range.maximum)
        assertTrue(range.marketValue in range.minimum..range.maximum)
    }
}
''')

write_new("app/src/test/java/com/example/ui/components/transfers/EditorPlayerValueRangeRegressionTest.kt", r'''package com.example.ui.components.transfers

import com.example.data.Player
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorPlayerValueRangeRegressionTest {
    @Test
    fun `editable market offer control never excludes the value shown before opening it`() {
        val samples = listOf(
            Player(id = 1L, teamId = null, name = "A", age = 19, position = "ATA", force = 99, potential = 99),
            Player(id = 2L, teamId = 2L, name = "B", age = 25, position = "ZAG", force = 80, potential = 80),
            Player(id = 3L, teamId = 3L, name = "C", age = 34, position = "MEI", force = 70, potential = 75)
        )
        samples.forEach { player ->
            val range = purchaseOfferRange(player)
            assertTrue(range.marketValue in range.minimum..range.maximum)
        }
    }
}
''')

write_new("app/src/test/java/com/example/ui/screens/MarketPriceFilterRegressionTest.kt", r'''package com.example.ui.screens

import com.example.data.Player
import org.junit.Assert.assertEquals
import org.junit.Test

class MarketPriceFilterRegressionTest {
    @Test
    fun `price maximum filters by the exact value rendered in market cards`() {
        val cheap = Player(id = 1L, teamId = null, name = "Cheap", age = 25, position = "MEI", force = 60, potential = 60)
        val expensive = Player(id = 2L, teamId = null, name = "Expensive", age = 25, position = "MEI", force = 99, potential = 99)
        val cutoff = cheap.calculateMarketValue()
        val result = filterAndSortMarketPlayers(
            listOf(cheap, expensive),
            MarketSearchCriteria("", "TODOS", 0, 99, cutoff, "VALOR_ASC")
        )
        assertEquals(listOf(cheap.id), result.map { it.id })
        assertEquals(cutoff, result.single().calculateMarketValue())
    }
}
''')

write_new("app/src/test/java/com/example/ui/screens/MarketPositionFilterRegressionTest.kt", r'''package com.example.ui.screens

import com.example.data.Player
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarketPositionFilterRegressionTest {
    @Test
    fun `selected position never returns rows from a previous position`() {
        val players = listOf(
            Player(id = 1L, teamId = null, name = "Zagueiro", age = 25, position = "ZAG", force = 80),
            Player(id = 2L, teamId = null, name = "Lateral", age = 25, position = "LAT", force = 80),
            Player(id = 3L, teamId = null, name = "Atacante", age = 25, position = "ATA", force = 80)
        )
        val criteria = MarketSearchCriteria("", "ZAG", 0, 99, 500_000_000L, "FORCA_DESC")
        val result = filterAndSortMarketPlayers(players, criteria)
        assertEquals(listOf(1L), result.map { it.id })
        assertTrue(result.all { it.position == "ZAG" })
    }

    @Test
    fun `result key distinguishes an old LAT result from a newly selected ZAG filter`() {
        val oldKey = MarketSearchKey(MarketSearchCriteria("", "LAT", 0, 99, 500_000_000L, "FORCA_DESC"), 10L, emptySet())
        val newKey = MarketSearchKey(MarketSearchCriteria("", "ZAG", 0, 99, 500_000_000L, "FORCA_DESC"), 10L, emptySet())
        assertTrue(oldKey != newKey)
    }
}
''')

write_new("app/src/test/java/com/example/usecase/MarketBuyNowPriceConsistencyRegressionTest.kt", r'''package com.example.usecase

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.data.AppDatabase
import com.example.data.GameRepository
import com.example.data.GameSave
import com.example.data.Player
import com.example.data.Team
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(manifest = Config.NONE)
class MarketBuyNowPriceConsistencyRegressionTest {
    private lateinit var db: AppDatabase
    private lateinit var repository: GameRepository

    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
        repository = GameRepository(db)
    }

    @After fun tearDown() { db.close() }

    @Test
    fun `buy now debits exactly the canonical price shown to the user`() = runTest {
        val buyerId = 1L
        val sellerId = 2L
        val save = GameSave(id = 1, playerTeamId = buyerId, bankBalance = 100_000_000L, coachReputation = 100)
        repository.saveTeams(listOf(
            Team(id = buyerId, name = "Buyer", city = "A", state = "AA", division = 1),
            Team(id = sellerId, name = "Seller", city = "B", state = "BB", division = 1)
        ))
        val player = Player(id = 77L, teamId = sellerId, name = "Igor Almeida", age = 25, position = "GOL", force = 99, potential = 99)
        repository.saveGameSave(save)
        repository.savePlayers(listOf(player))

        val shownPrice = player.calculateMarketValue()
        assertEquals(29_700_000L, shownPrice)
        val result = ProcessTransfersUseCase(repository).buyPlayer(save, player, shownPrice)
        assertTrue(result is ProcessTransfersUseCase.TransferResult.Success)
        assertEquals(save.bankBalance - shownPrice, repository.getGameSave()?.bankBalance)
        assertEquals(shownPrice, repository.getAllTransactions().single().amount)
        assertEquals(buyerId, repository.getPlayer(player.id)?.teamId)
    }
}
''')

write_new("docs/manual-test/MARKET_VALUE_DIAGNOSTIC_20260903.md", '''# Market value diagnostic — 2026-09-03\n\n| Surface | Before | Problem | After |\n|---|---|---|---|\n| Market card / price filter / sorting | `TransferNegotiationUseCase.calculateDynamicPlayerPrice` | Existing canonical-looking path | `Player.calculateMarketValue()` through the same preserved formula |\n| Negotiation modal / Buy Now label | `Player.calculateMarketValue()` old piecewise formula | Igor: R$ 110.430.000 vs card R$ 29.700.000 | Same canonical value as card |\n| Instant Buy debit | dynamic market price | Could debit a different amount than the modal displayed | Same canonical value displayed and debited |\n| Squad player detail | persisted `market_value` or old piecewise fallback | Could show a third value while sale used dynamic price | Canonical value only |\n| Sale default | dynamic market price | Could differ from squad detail | Same canonical value |\n| Offer slider | 50%..150% of old modal value | Range could exclude/contradict the previous screen | 50%..150% of the canonical value; current displayed value is always inside |\n| Market position filter | async result stored independently of selected chip | New chip could temporarily render rows from old filter | Result keyed to the exact active criteria; stale rows hidden immediately |\n| Editor Técnico | no market-value editor exists in current source | It must not become a separate price authority | No separate price authority introduced |\n\nThe preserved canonical formula is the formula already used by the Market list before this fix:\n`(force * 150000 + potential * 100000) * ageFactor`, with age factors 1.5 / 1.2 / 0.9 / 0.6.\nNo sporting data or factual player attributes are changed.\n''')

print("market value consistency patch applied")
