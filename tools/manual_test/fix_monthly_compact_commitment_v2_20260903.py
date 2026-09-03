from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def replace_exact(path: Path, old: str, new: str) -> None:
    text = path.read_text(encoding="utf-8")
    if old not in text:
        raise SystemExit(f"expected source block not found in {path}: {old[:220]!r}")
    path.write_text(text.replace(old, new, 1), encoding="utf-8")


streaming = ROOT / "app/src/main/java/com/example/data/MonthlyEvolutionStreamingQueries.kt"
replace_exact(
    streaming,
    '''internal fun GameRepository.forEachMonthlyEvolutionPlayerBatch(
    batchSize: Int,
    onBatchReadNanos: (Long) -> Unit = {},
    consume: (List<Player>) -> Unit
): Int {''',
    '''internal fun GameRepository.forEachMonthlyEvolutionPlayerBatch(
    batchSize: Int,
    onBatchReadNanos: (Long) -> Unit = {},
    onPlayerRead: (Player, String) -> Unit = { _, _ -> },
    consume: (List<Player>) -> Unit
): Int {'''
)
replace_exact(
    streaming,
    '''            batch.add(
                Player(''',
    '''            val player = Player('''
)
replace_exact(
    streaming,
    '''                    focoTreino = if (cursor.isNull(focusIndex)) null else cursor.getString(focusIndex)
                )
            )

            if (batch.size == batchSize) {''',
    '''                    focoTreino = if (cursor.isNull(focusIndex)) null else cursor.getString(focusIndex)
                )
            onPlayerRead(player, atributosStorage)
            batch.add(player)

            if (batch.size == batchSize) {'''
)

commitment = ROOT / "app/src/main/java/com/example/data/MonthlyEvolutionUniverseCommitment.kt"
replace_exact(
    commitment,
    '''    fun add(player: Player) {
        updateMonthlyEvolutionStateDigest(''',
    '''    fun add(player: Player, atributosStorage: String) {
        updateMonthlyEvolutionStateDigest('''
)
replace_exact(
    commitment,
    '''            atributosJson = player.atributosJson,
            atributosStorage = requireNotNull(AtributosConverter.atributosToJson(player.atributos))
        )''',
    '''            atributosJson = player.atributosJson,
            atributosStorage = atributosStorage
        )'''
)

usecase = ROOT / "app/src/main/java/com/example/usecase/PlayerEvolutionUseCase.kt"
replace_exact(
    usecase,
    '''            for (player in batch) {
                if (detailed) {
                    expectedInputs!!.add(player.toMonthlyEvolutionInputSnapshot())
                } else {
                    commitmentBuilder!!.add(player)
                }
                player.teamId?.let(referencedTeamIds::add)
            }''',
    '''            for (player in batch) {
                if (detailed) {
                    expectedInputs!!.add(player.toMonthlyEvolutionInputSnapshot())
                }
                player.teamId?.let(referencedTeamIds::add)
            }'''
)
replace_exact(
    usecase,
    '''            val processed = repository.forEachMonthlyEvolutionPlayerBatch(MONTHLY_EVOLUTION_BATCH_SIZE) { batch ->
                processBatch(batch, detailed = false)
            }''',
    '''            val processed = repository.forEachMonthlyEvolutionPlayerBatch(
                batchSize = MONTHLY_EVOLUTION_BATCH_SIZE,
                onPlayerRead = { player, atributosStorage ->
                    commitmentBuilder!!.add(player, atributosStorage)
                }
            ) { batch ->
                processBatch(batch, detailed = false)
            }'''
)

memory_test = ROOT / "app/src/test/java/com/example/usecase/MonthlyEvolutionCompactPlanMemoryRegressionTest.kt"
replace_exact(
    memory_test,
    '''        assertTrue(useCase.commitMonthlyEvolution(plan))''',
    '''        assertTrue("stable compact commitment must validate and commit", useCase.commitMonthlyEvolution(plan))'''
)

print("monthly compact commitment v2 raw-storage patch prepared")
