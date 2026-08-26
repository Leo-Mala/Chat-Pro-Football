from pathlib import Path


def replace_between(path: str, start_marker: str, end_marker: str, replacement: str) -> None:
    p = Path(path)
    text = p.read_text()
    start_count = text.count(start_marker)
    end_count = text.count(end_marker)
    if start_count != 1 or end_count < 1:
        raise SystemExit(f"{path}: marker mismatch start={start_count} end={end_count}")
    start = text.index(start_marker)
    end = text.index(end_marker, start)
    p.write_text(text[:start] + replacement + text[end:])


def replace_once(path: str, old: str, new: str) -> None:
    p = Path(path)
    text = p.read_text()
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{path}: expected exactly one match, found {count}\n--- old ---\n{old}")
    p.write_text(text.replace(old, new, 1))


# Room calls this converter for every Player persisted. Building a JSONObject with 35 puts for
# ~75k rows dominated New Game CPU/GC. The direct string has identical keys/values/order and is
# already the fallback representation of this converter, so sporting data and schema do not change.
replace_between(
    "app/src/main/java/com/example/data/PlayerAttributes.kt",
    "    @TypeConverter\n    fun fromAtributos(atributos: Atributos?): String? {",
    "\n\n    @TypeConverter\n    fun toAtributos",
    '''    @TypeConverter
    fun fromAtributos(atributos: Atributos?): String? {
        if (atributos == null) return null
        return """{\"reflexos\":${atributos.reflexos},\"pegada\":${atributos.pegada},\"umContraUm\":${atributos.umContraUm},\"saidaDeGol\":${atributos.saidaDeGol},\"lancamento\":${atributos.lancamento},\"desarme\":${atributos.desarme},\"marcacao\":${atributos.marcacao},\"cabeceio\":${atributos.cabeceio},\"passeCurto\":${atributos.passeCurto},\"cruzamento\":${atributos.cruzamento},\"drible\":${atributos.drible},\"passe\":${atributos.passe},\"primeiroToque\":${atributos.primeiroToque},\"finalizacao\":${atributos.finalizacao},\"chuteDeLonge\":${atributos.chuteDeLonge},\"controleBola\":${atributos.controleBola},\"posicionamento\":${atributos.posicionamento},\"concentracao\":${atributos.concentracao},\"sangueFrio\":${atributos.sangueFrio},\"antecipacao\":${atributos.antecipacao},\"bravura\":${atributos.bravura},\"trabalhoEquipe\":${atributos.trabalhoEquipe},\"decisao\":${atributos.decisao},\"semBola\":${atributos.semBola},\"visaoJogo\":${atributos.visaoJogo},\"criatividade\":${atributos.criatividade},\"agressividade\":${atributos.agressividade},\"lideranca\":${atributos.lideranca},\"regularidade\":${atributos.regularidade},\"agilidade\":${atributos.agilidade},\"impulsao\":${atributos.impulsao},\"forca\":${atributos.forca},\"velocidade\":${atributos.velocidade},\"aceleracao\":${atributos.aceleracao},\"resistencia\":${atributos.resistencia}}"""
    }'''
)

# Add per-operation evidence inside the already instrumented persistence phase so the Release UI
# test tells us whether any remaining cost belongs to FC26 seed materialization, Player inserts,
# fixtures or the GameSave row.
replace_once(
    "app/src/main/java/com/example/ui/viewmodel/GameViewModel.kt",
    '''            val persistenceStartedAtNs = System.nanoTime()
            targetRepo.saveTeams(dbTeams)
            targetRepo.savePlayers(allPlayersToSave)
            targetRepo.saveFixtures(allGeneratedFixtures)
            targetRepo.saveGameSave(save)
            persistenceMs = (System.nanoTime() - persistenceStartedAtNs) / 1_000_000L
''',
    '''            val persistenceStartedAtNs = System.nanoTime()
            val teamSeedStartedAtNs = System.nanoTime()
            targetRepo.saveTeams(dbTeams)
            val teamSeedAndPersistenceMs = (System.nanoTime() - teamSeedStartedAtNs) / 1_000_000L

            val playerPersistenceStartedAtNs = System.nanoTime()
            targetRepo.savePlayers(allPlayersToSave)
            val playerPersistenceMs = (System.nanoTime() - playerPersistenceStartedAtNs) / 1_000_000L

            val fixturePersistenceStartedAtNs = System.nanoTime()
            targetRepo.saveFixtures(allGeneratedFixtures)
            val fixturePersistenceMs = (System.nanoTime() - fixturePersistenceStartedAtNs) / 1_000_000L

            val saveRowStartedAtNs = System.nanoTime()
            targetRepo.saveGameSave(save)
            val saveRowPersistenceMs = (System.nanoTime() - saveRowStartedAtNs) / 1_000_000L
            persistenceMs = (System.nanoTime() - persistenceStartedAtNs) / 1_000_000L
            Log.i(
                "CareerCreationPerformance",
                "PersistenceBreakdown(teamSeedAndPersistenceMs=$teamSeedAndPersistenceMs, " +
                    "playerPersistenceMs=$playerPersistenceMs, fixturePersistenceMs=$fixturePersistenceMs, " +
                    "saveRowPersistenceMs=$saveRowPersistenceMs)"
            )
'''
)
