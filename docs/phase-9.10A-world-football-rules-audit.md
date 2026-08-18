# Fase 9.10A — Auditoria do Sistema Mundial de Competições e Projeto do Motor de Regras

## 0. Estado da fase

- **Baseline auditado:** `cc5c95c43e00ba825f488aac6de4dc21e886ee27`
- **Baseline esperado da Fase 9.9:** confirmado.
- **Comparação `baseline..main` antes da branch:** `identical`, 0 commits de diferença.
- **Comparação `baseline..main` imediatamente antes deste documento:** `identical`, 0 commits de diferença.
- **Branch:** `agent/v3-world-football-rules-audit`
- **Main:** não alterada.
- **Código de produção alterado:** não.
- **Schema Room alterado:** não.
- **Room:** permanece V21.
- **V22:** não necessário para a arquitetura proposta nesta subfase.
- **GitHub Actions durante auditoria:** não executadas.
- **Stress 20/100:** não reduzidos, não alterados e não rerodados nesta auditoria somente-leitura.
- **Merge:** não realizado.
- **Fase 9.11:** não iniciada.

## 1. Resumo executivo

A arquitetura atual preserva bem invariantes estruturais conquistadas nas fases 9.8/9.9: temporada de 48 semanas, slots MIDWEEK/WEEKEND, validação de colisão de fixtures, integridade referencial V21, promoção/rebaixamento com troca balanceada de clubes e simulação global determinística e compacta. Não foi identificado, na leitura do baseline, um P0 ativo que prove corrupção de save no fluxo normal atual.

O principal problema da Fase 9.10 é de **fidelidade esportiva e source of truth**. A CONMEBOL possui um motor dedicado relativamente desenvolvido; UEFA, CONCACAF, CAF, AFC e OFC não possuem motores próprios. Fora da CONMEBOL, `CupCompetitionSystem` aplica o mesmo formato continental legado T1/T2/T3. Da mesma forma, a maioria das regras nacionais usa hierarquia genérica de duas vagas de promoção/rebaixamento, copas nacionais usam uma chave genérica de até 32 clubes e a qualificação continental é representada por uma priorização transitória via `Team.rating`, em vez de origens de vaga tipadas.

Há também fallbacks perigosos para domínio: país desconhecido vira CONMEBOL; competição desconhecida pode virar `SERIE_A`; IDs virtuais acima de 200.000 podem ser materializados como `Team(country="Mundial")`; e clubes `Mundial` acabam entrando na simulação global de liga/standings, embora sejam corretamente excluídos do gerenciamento de elenco CPU.

**Decisão da 9.10A:** manter esta etapa documental. O motor data-driven deve ser introduzido em uma mudança posterior controlada, começando pelo registry fail-safe de país/confederação e identidades de competição, antes de implementar UEFA. Não há justificativa para V22.

## 2. Arquivos e áreas auditadas

### Produção — núcleo esportivo/persistência

- `app/src/main/java/com/example/data/GlobalFootballSystem.kt`
- `app/src/main/java/com/example/data/CompetitionRules.kt`
- `app/src/main/java/com/example/data/ContinentalQualificationQuotaPolicy.kt`
- `app/src/main/java/com/example/data/ContinentalQualificationRules.kt`
- `app/src/main/java/com/example/data/CupCompetitionSystem.kt`
- `app/src/main/java/com/example/data/ConmebolCompetitionSystem.kt`
- `app/src/main/java/com/example/data/GameCalendar.kt`
- `app/src/main/java/com/example/data/MatchSlot.kt`
- `app/src/main/java/com/example/data/LeagueHierarchy.kt`
- `app/src/main/java/com/example/data/LeagueSeasonFormat.kt`
- `app/src/main/java/com/example/data/DetailedGroupTopology.kt`
- `app/src/main/java/com/example/data/GlobalLeagueStanding.kt`
- `app/src/main/java/com/example/data/DefaultData.kt`
- `app/src/main/java/com/example/data/defaultdata/AmericasDefaultData.kt`
- `app/src/main/java/com/example/data/defaultdata/BrazilDefaultData.kt`
- `app/src/main/java/com/example/data/defaultdata/EuropeDefaultData.kt`
- `app/src/main/java/com/example/data/defaultdata/WorldDefaultData.kt`
- `app/src/main/java/com/example/data/SuperMundialEditionPolicy.kt`
- `app/src/main/java/com/example/data/SuperMundialSystem.kt`
- `app/src/main/java/com/example/data/GameEngine.kt`
- `app/src/main/java/com/example/data/entities.kt`
- `app/src/main/java/com/example/data/daos.kt`
- `app/src/main/java/com/example/data/database.kt`
- `app/src/main/java/com/example/data/repository.kt`
- `app/src/main/java/com/example/usecase/GenerateCalendarUseCase.kt`
- `app/src/main/java/com/example/usecase/GlobalLeagueSimulationUseCase.kt`
- `app/src/main/java/com/example/usecase/SeasonTransitionUseCase.kt`
- `app/src/main/java/com/example/usecase/SimulateWeekUseCase.kt`
- `app/src/main/java/com/example/usecase/CpuSquadManagementUseCase.kt`
- `app/src/main/java/com/example/ui/viewmodel/GameViewModel.kt`
- `app/src/main/java/com/example/ui/viewmodel/GameViewModelMatch.kt`
- `app/src/main/java/com/example/ui/screens/CompetitionPhaseRules.kt`

`League.kt` foi procurado no caminho prioritário indicado, mas **não existe** no baseline atual.

### Testes/contratos mapeados

Foram identificados e preservados, entre outros:

- `Phase97CareerIntegrationTest`
- `TwentySeasonStressTest`
- `OneHundredSeasonMatchByMatchStressTest`
- `Phase98CpuSquadSeasonIntegrationTest`
- `Phase98SuperMundialPersistenceTest`
- `Phase99RelationalIntegrityTest`
- `CareerInvariantAssertions`
- `RelationalIntegrityAssertions`
- migration tests V20→V21
- `FootballRulesIntegrityTest`
- `ConmebolCompetitionSystemTest`
- `ConmebolAggregateRulesTest`
- `ConmebolGroupDrawRestrictionTest`
- `ContinentalQualificationQuotaPolicyTest`
- `ContinentalQualificationRulesTest`
- `CupCompetitionSystemTest`
- `CupQualificationIsolationTest`
- `FixtureScheduleValidatorTest`
- `GameCalendarTest`
- `GameRepositoryFixtureScheduleTest`
- `LeagueHierarchyTest`
- `LeagueSeasonFormatTest`
- `SuperMundialEditionPolicyTest`
- `SuperMundialSystemTest`
- `AdaptiveLeagueCalendarTest`
- `CalendarSlotIntegrationTest`
- `GlobalCpuMultiSeasonTransitionTest`
- `GlobalWorldSimulationScaleTest`
- `GroupedDetailedPromotionPriorityTest`
- `UnfinishableDetailedDivisionFallbackTest`

Nenhum teste foi removido, enfraquecido ou alterado.

## 3. Source of truth atual

Hoje as regras estão espalhadas:

| Área | Source atual | Situação |
|---|---|---|
| País → confederação | `GlobalFootballSystem` | central, porém fallback incorreto |
| Catálogo/nome de competições mundiais | `GlobalFootballSystem.competitions` | catálogo não coincide integralmente com o motor executável |
| Formato continental não-CONMEBOL | `CupCompetitionSystem` | fallback genérico |
| Formato CONMEBOL | `ConmebolCompetitionSystem` | motor dedicado |
| Quotas CONMEBOL | `ContinentalQualificationQuotaPolicy` | parcial/simplificado |
| Prioridade de qualificação | `ContinentalQualificationRules` | proxy via rating |
| Hierarquia nacional | `LeagueHierarchyLoader` | Brasil + fallback genérico |
| Formato de liga | `LeagueSeasonFormat` | genérico por tamanho |
| Promoção/rebaixamento | `SeasonTransitionUseCase` + `LeagueHierarchy` | mecanismo seguro, regra esportiva genérica |
| Copa nacional | `CupCompetitionSystem` | genérica |
| Calendário | `GameCalendar`, `MatchSlot`, geradores de cada competição | estruturalmente forte, regras distribuídas |
| Fases exibidas na UI | `CompetitionPhaseRules.kt` | duplicação de semanas/fases |
| Identidade de competição | strings + `CompetitionType` enum + aliases | fragmentada |
| Classificação histórica/global | `GlobalLeagueStanding` + `GlobalLeagueSimulationUseCase` | compacta e determinística |
| Super Mundial | `SuperMundialEditionPolicy` + `SuperMundialSystem` | formato preservado; qualificação parcial |

Conclusão: **não existe uma única source of truth esportiva**.

## 4. Países encontrados e mapeamento atual país → confederação

### UEFA — 20

1. Inglaterra
2. Espanha
3. Itália
4. Alemanha
5. França
6. Portugal
7. Países Baixos
8. Bélgica
9. Turquia
10. Escócia
11. Áustria
12. Suíça
13. Dinamarca
14. Noruega
15. Suécia
16. Polônia
17. Tchéquia
18. Croácia
19. Sérvia
20. Grécia

### CONMEBOL — 10

1. Brasil
2. Argentina
3. Colômbia
4. Chile
5. Uruguai
6. Paraguai
7. Equador
8. Peru
9. Bolívia
10. Venezuela

### CONCACAF — 10

1. México
2. Estados Unidos / Canadá
3. Costa Rica
4. Guatemala
5. Honduras
6. Panamá
7. El Salvador
8. Jamaica
9. República Dominicana
10. Trinidad e Tobago

### AFC — 8

1. Japão
2. Coreia do Sul
3. Arábia Saudita
4. Emirados Árabes Unidos
5. Catar
6. Irã
7. China
8. Austrália

### CAF — 4

1. Egito
2. Marrocos
3. Tunísia
4. África do Sul

### OFC — 0 países explícitos no registry normal

Há dados agregados legados para `Oceania` em `WorldDefaultData`, porém `Oceania` não faz parte de `GlobalFootballSystem.keys` e, portanto, não é semeado como país normal no fluxo de criação mundial atual.

### Aliases/agrupamentos legados

- `Estados Unidos / México` → CONCACAF
- `América Central` → CONCACAF
- `África` → CAF
- `Ásia` → AFC
- `Oceania` → OFC
- `África / Ásia / Oceania` → MIXED

### Fallback atual proibido como regra final

Qualquer outro texto de país, incluindo `Mundial`, cai em **CONMEBOL**.

## 5. Confederações — status real

| Confederação | Países normais | Motor dedicado | Vagas próprias | Formato próprio | Status |
|---|---:|---|---|---|---|
| CONMEBOL | 10 | sim | sim, porém simplificadas | sim | PARTIAL / QUASE COMPLETO |
| UEFA | 20 | não | não | não | GENERIC FALLBACK |
| CONCACAF | 10 | não | não | não | GENERIC FALLBACK |
| CAF | 4 | não | não | não | GENERIC FALLBACK |
| AFC | 8 | não | não | não | GENERIC FALLBACK |
| OFC | 0 | não | não | não | PARTIAL / GENERIC FALLBACK / normal flow sem universo elegível |

A árvore não contém `UefaCompetitionSystem`, `ConcacafCompetitionSystem`, `CafCompetitionSystem`, `AfcCompetitionSystem` ou `OfcCompetitionSystem`.

## 6. Inventário de competições

### 6.1 Ligas nacionais detalhadas

- **NOME:** varia por país, porém internamente usa `SERIE_A/B/C/D` e aliases `DIV_n`.
- **TIPO:** LEAGUE.
- **PAÍS:** somente o país do clube do usuário recebe fixtures detalhados persistidos.
- **PARTICIPANTES:** definidos por `DefaultData.countryDivisionSizes`.
- **FORMA:** round-robin genérico por tamanho; 2 turnos quando cabem nas 40 rodadas, 1 turno quando só um cabe; divisões grandes exatamente particionáveis podem usar grupos balanceados 4..20.
- **CALENDÁRIO:** WEEKEND, começando na semana 1, máximo 40 rodadas domésticas.
- **DESEMPATE:** pontos, vitórias, saldo, gols pró, rating, ID; em grupos, posição dentro do grupo prevalece antes da comparação entre grupos.
- **PERSISTÊNCIA:** fixtures somente do país detalhado; standings globais persistidos.
- **HISTÓRICO:** `GlobalLeagueStanding`; primeira divisão retida por longo prazo, divisões inferiores podadas.
- **FALLBACK:** formato compacto em memória quando a divisão não tem formato detalhado suportado.
- **STATUS:** GENERIC FALLBACK / PARTIAL do ponto de vista de regra real por país; SAFE estruturalmente.

### 6.2 Copa nacional (`COPA`)

- **TIPO:** CUP.
- **PAÍS:** somente país do usuário.
- **PARTICIPANTES:** maior potência de 2 até 32 entre os clubes do país, ordenados por divisão/rating/ID; clube do usuário é forçado para a lista se necessário.
- **FASES:** mata-mata simples em jogo único.
- **CALENDÁRIO:** MIDWEEK; final semana 27. Com 32 clubes, semanas 23..27.
- **CAMPEÃO:** vencedor da final, com decisão de empate determinística.
- **PERSISTÊNCIA:** fixtures + `HistoricalRecord` do campeão.
- **FALLBACK:** mesma regra para todos os países.
- **STATUS:** GENERIC FALLBACK / NEEDS DOMAIN RULE.

### 6.3 Estadual / STATE / `ESTADUAL`

Há labels/enum/códigos, porém **não foi encontrado gerador/progressão ativo de campeonato estadual** no calendário atual.

- **STATUS:** DEAD CODE / UNIMPLEMENTED no motor atual.

### 6.4 Libertadores — runtime `CONTINENTAL_T1` em temporada CONMEBOL

- **PARTICIPANTES:** 32.
- **QUALIFICAÇÃO:** plano CONMEBOL simplificado: quotas nacionais base + slots suplementares preenchidos pela ordenação dos candidatos.
- **GRUPOS:** 8×4, turno e returno.
- **SEMANAS DE GRUPOS:** 2, 5, 8, 11, 14, 17 MIDWEEK.
- **OITAVAS:** ida/volta 30/31.
- **QUARTAS:** 37/38.
- **SEMIFINAL:** 39/40.
- **FINAL:** 41, jogo único.
- **PERSISTÊNCIA:** fixtures e histórico do campeão.
- **STATUS:** PARTIAL / QUASE COMPLETO. Execução forte; qualificação e aderência oficial integral não estão modeladas/verificadas nesta auditoria.

### 6.5 Sul-Americana — runtime `CONTINENTAL_T2` em temporada CONMEBOL

- **PARTICIPANTES:** 32.
- **GRUPOS:** 8×4, turno e returno.
- **GRUPOS:** semanas 2,5,8,11,14,17 MIDWEEK.
- **PLAYOFF:** 28/29, envolvendo vice de grupo e terceiro da Libertadores.
- **OITAVAS:** 30/31.
- **QUARTAS:** 37/38.
- **SEMIFINAL:** 39/40.
- **FINAL:** 41.
- **STATUS:** PARTIAL / QUASE COMPLETO.

### 6.6 UEFA Champions / Europa / Conference

Existem nomes/códigos de catálogo `UEFA_CL`, `UEFA_EL`, `UEFA_ECL`, porém o runtime usa `CONTINENTAL_T1/T2/T3` e o mesmo motor legado genérico:

- T1: 8/16/32 participantes conforme disponibilidade.
- T2: próximo bloco de 8/16/32.
- T3: maior potência de 2 restante, até 16.
- T1/T2: grupos de 4, apenas 3 jogos por clube, semanas 29/30/31.
- top 2 por grupo.
- mata-mata em jogo único, terminando semana 36.
- nenhuma política UEFA de países, coeficientes ou vagas.

**STATUS:** GENERIC FALLBACK / NEEDS DOMAIN RULE.

### 6.7 CONCACAF

Catálogo possui Champions Cup, Central American Cup e Caribbean Cup, mas o runtime não implementa essas competições separadamente. Usa T1/T2/T3 genérico.

**STATUS:** GENERIC FALLBACK / NEEDS DOMAIN RULE.

### 6.8 CAF

Catálogo possui CAF Champions League e Confederation Cup. Runtime continua T1/T2/T3 genérico, inclusive podendo gerar T3 embora o catálogo de CAF tenha apenas dois níveis explícitos.

**STATUS:** GENERIC FALLBACK / NEEDS DOMAIN RULE.

### 6.9 AFC

Catálogo possui AFC Champions League Elite, Champions League Two e Challenge League. Runtime continua T1/T2/T3 genérico.

**STATUS:** GENERIC FALLBACK / NEEDS DOMAIN RULE.

### 6.10 OFC Champions League

O catálogo existe, e o alias legado `Oceania` resolve para OFC, mas o registry normal não possui países OFC. Não existe motor OFC próprio.

**STATUS:** PARTIAL / GENERIC FALLBACK; normal flow sem conjunto explícito de países OFC.

### 6.11 Super Mundial

- **CADÊNCIA:** preservada: `season >= 2025 && (season - 2025) % 4 == 0`.
- **PARTICIPANTES:** 32.
- **SEDE:** determinística, somente países reais persistidos; `Mundial` excluído da rotação.
- **ANFITRIÃO:** clube do país-sede, sem duplicação por ID.
- **GRUPOS:** semanas 42/43/44 MIDWEEK.
- **OITAVAS:** 45.
- **QUARTAS:** 46.
- **SEMIFINAL:** 47.
- **FINAL:** 48.
- **FORMATO/CALENDÁRIO:** SAFE / COMPLETE conforme contratos 9.8.
- **QUALIFICAÇÃO DOS 32:** PARTIAL. Usa lista histórica, melhores clubes persistidos, virtualização e inclusão histórica do clube do usuário; não é um motor data-driven de qualificação mundial.
- **OBSERVAÇÃO:** não reescrever a estrutura 9.8 sem necessidade comprovada.

### 6.12 FIFA Intercontinental

`WORLD_INTERCONTINENTAL` aparece no catálogo com semanas 40/41, mas **não foi encontrado gerador/progressão ativo** na árvore auditada.

**STATUS:** DEAD CODE / CATALOG ONLY.

## 7. Regras nacionais e divisões configuradas

`DefaultData.countryDivisionSizes` possui configuração de quantidade de clubes para os 52 países, mas isso não equivale a regras oficiais. Os formatos são derivados genericamente do tamanho.

### UEFA

- Inglaterra `[20,24,24]`
- Espanha `[20,22,40]`
- Itália `[20,20,60]`
- Alemanha `[18,18,20]`
- França `[18,18,17]`
- Portugal `[18,18,18]`
- Países Baixos `[18,20,18]`
- Bélgica `[16,16,18]`
- Turquia `[19,20,38]`
- Escócia `[12,10,10]`
- Áustria `[12,16,16]`
- Suíça `[12,10]`
- Dinamarca `[12,12,12]`
- Noruega `[16,16]`
- Suécia `[16,16]`
- Polônia `[18,18]`
- Tchéquia `[16,16,48]`
- Croácia `[10,12,56]`
- Sérvia `[16,16,18]`
- Grécia `[14,14]`

### CONMEBOL

- Brasil `[20,20,20,96,15]`
- Argentina `[30,36,40]`
- Colômbia `[20,16,32]`
- Chile `[16,16,32]`
- Uruguai `[16,14,14]`
- Paraguai `[12,12,12]`
- Equador `[16,10]`
- Peru `[19,14]`
- Bolívia `[16]`
- Venezuela `[14,14]`

### CONCACAF

- México `[18,15,57]`
- Estados Unidos / Canadá `[30,24,24]`
- Costa Rica `[12,18,16]`
- Guatemala `[12,20]`
- Honduras `[10,10]`
- Panamá `[11,10]`
- El Salvador `[12,16]`
- Jamaica `[14,20]`
- República Dominicana `[10]`
- Trinidad e Tobago `[12]`

### AFC

- Japão `[20,20,20]`
- Coreia do Sul `[12,13,27]`
- Arábia Saudita `[18,18,18]`
- Emirados Árabes Unidos `[14,12]`
- Catar `[12,12]`
- Irã `[16,18,28]`
- China `[16,16,36]`
- Austrália `[13,12]`

### CAF

- Egito `[16,18]`
- Marrocos `[16,16,16]`
- Tunísia `[16,14]`
- África do Sul `[16,16]`

Não há país OFC explícito.

## 8. Promoção e rebaixamento

### Como funciona hoje

1. Ao fechar a semana 48, `SeasonTransitionUseCase` calcula snapshots globais.
2. O país do usuário usa resultados detalhados quando a topologia está completa.
3. Outros países usam standings compactos determinísticos.
4. Para cada fronteira entre divisões adjacentes, `LeagueHierarchy.safeMovementSpotsBetween` limita a quantidade de movimentos para evitar sobreposição e alteração silenciosa do tamanho das divisões.
5. Os últimos da divisão superior descem e os primeiros da inferior sobem.

### Regras de quantidade

- Brasil: base de 4 vagas por fronteira; uma quinta divisão é adaptada a partir do dataset e herda a regra de movimento.
- Inglaterra/Espanha/Argentina/EUA-Canadá aparecem no mapa estático, porém usam `genericDivisions`, isto é, 2 por fronteira.
- Demais países: fallback genérico de 2.
- Playoffs: não modelados.

### Integridade

O mecanismo de troca é estruturalmente seguro e possui testes. O problema é esportivo: as quantidades/formas não são por federação.

**STATUS:** SAFE estruturalmente / GENERIC FALLBACK no domínio.

## 9. Qualificação continental — origem atual das vagas

| Origem | Existe? | Como é usada |
|---|---|---|
| Posição da liga anterior | sim | transformada em `Team.rating` transitório 100,99,98... |
| Campeão da copa nacional | não como slot tipado | não há regra própria de vaga |
| Campeão continental | não como slot automático | não há origem tipada |
| Coeficiente/association ranking | não | ausente |
| Lista fixa | parcialmente | Super Mundial usa lista histórica de nomes |
| Rating real | sim como fallback | primeira temporada/sem snapshot e ordenações auxiliares |
| Virtual team | sim | usado para completar universos/fallbacks mundiais/legados |
| Random | não para classificação estrutural principal | sorteios usam seed determinística |

Na CONMEBOL, `ContinentalQualificationQuotaPolicy` define quotas por país, porém slots suplementares ainda são preenchidos a partir da ordem dos candidatos, sem representar explicitamente preliminares/campeões automáticos.

## 10. Matriz de calendário atual

| Competição | Fase | Semana(s) | Slot |
|---|---|---|---|
| Liga doméstica detalhada | rodadas | 1..até 40 | WEEKEND |
| CONMEBOL T1/T2 | grupos | 2,5,8,11,14,17 | MIDWEEK |
| Copa nacional | mata-mata | termina 27; com 32, 23..27 | MIDWEEK |
| Sudamericana | playoff | 28,29 | MIDWEEK |
| CONMEBOL T1/T2 | oitavas | 30,31 | MIDWEEK |
| Continental legado não-CONMEBOL T1/T2 | grupos | 29,30,31 | MIDWEEK |
| Continental legado | mata-mata | até 36 | MIDWEEK |
| CONMEBOL | quartas | 37,38 | MIDWEEK |
| CONMEBOL | semifinal | 39,40 | MIDWEEK |
| CONMEBOL | final | 41 | MIDWEEK |
| Super Mundial | grupos | 42,43,44 | MIDWEEK |
| Super Mundial | oitavas | 45 | MIDWEEK |
| Super Mundial | quartas | 46 | MIDWEEK |
| Super Mundial | semifinal | 47 | MIDWEEK |
| Super Mundial | final | 48 | MIDWEEK |
| WORLD_INTERCONTINENTAL | catálogo apenas | 40,41 no catálogo | sem motor ativo |

`FixtureScheduleValidator` impede, para novos fixtures, semana fora de 1..48, auto-confronto, fixture duplicado e dois jogos do mesmo clube no mesmo `season/week/slot`. Liga WEEKEND pode coexistir com copa/continental MIDWEEK na mesma semana.

**STATUS:** SAFE / COMPLETE para o modelo de slots atual; NEEDS REFACTOR como source of truth esportiva.

## 11. Clubes virtuais

### Namespace

- `GlobalFootballSystem.VIRTUAL_TEAM_ID_FLOOR = 200_000`.
- IDs acima/iguais ao piso são considerados gerados/virtuais.
- `getGlobalId` pode produzir ID hash no intervalo virtual para país/time não decodificável.
- Há fallbacks explícitos na faixa `900_000+` e `990_000+`.

### Criação

- Super Mundial: nomes históricos ausentes e dummies.
- `GenerateCalendarUseCase.generateKnockoutFixtures*`: chave ímpar adiciona clube virtual.
- `selectLibertadoresTeams`: método legado completa até 32 com clubes virtuais.

### Persistência V21

`GameRepository.saveFixtures/updateFixtures` primeiro materializa `Team` ausente quando o ID pertence ao namespace virtual e só depois grava Fixture. Isso preserva as FKs V21.

### Identidade persistida

Quando o repository materializa uma referência virtual, `GlobalFootballSystem.getVirtualTeam` cria normalmente:

- nome `Clube Virtual <id>`;
- país `Mundial`;
- divisão 1;
- rating 50.

Isso pode perder o nome/rating lógico do objeto virtual que existia apenas durante a seleção do Super Mundial, caso ele não tenha sido persistido separadamente antes do fixture.

### Consumo posterior

- `getAllTeams()`: inclui virtual.
- `CpuSquadManagementUseCase`: **exclui `country="Mundial"`**, preservando contratos da Fase 9.8.
- `GlobalLeagueSimulationUseCase`: **não exclui `Mundial`**, portanto esses clubes podem formar uma liga compacta fictícia `Mundial` e gerar `GlobalLeagueStanding`.
- `SeasonTransitionUseCase`: agrupa todos os Teams por país, portanto também enxerga `Mundial`.
- `getConfederationForCountry("Mundial")`: hoje retorna CONMEBOL pelo fallback desconhecido.
- Remoção automática do Team virtual: não encontrada; tende a permanecer no save.

**STATUS:** FK SAFE / domínio PARTIAL / NEEDS REFACTOR.

## 12. Randomização

### Legítima/determinística para estrutura

- sorteio de grupos/participantes de copas: `Random(stableSeed(...))`;
- Super Mundial: `Random(season.toLong())`;
- grupos detalhados: distribuição determinística por rating/ID;
- simulação global CPU: seed por temporada/país/divisão/confronto;
- pênaltis de mata-mata: seed derivada do Fixture.

### Aleatoriedade de resultado esportivo

`GameEngine` possui aleatoriedade para placares; quando o fluxo fornece seed, a simulação automática estrutural permanece reproduzível.

### Fallback não determinístico para esconder regra

Não foi encontrado uso central de `Random` para escolher arbitrariamente confederação ou fabricar classificação estrutural. O problema principal é fallback **genérico**, não aleatoriedade oculta.

## 13. Fallbacks e hardcodes principais

| ID | Arquivo/função | Quando | Ação atual | Risco |
|---|---|---|---|---|
| F-01 | `GlobalFootballSystem.getConfederationForCountry` | país desconhecido | CONMEBOL | alto domínio |
| F-02 | `getContinentalTournamentsForCountry` | confederação não reconhecida | trio CONMEBOL | alto domínio |
| F-03 | `LeagueHierarchyLoader` | país sem hierarquia | 2 sobe/2 desce | regra genérica |
| F-04 | `DefaultData.getTeamsForCountry` | país não encontrado | Brasil | identidade/domínio |
| F-05 | `DefaultData.getCountryInfo` | país não encontrado | Brasil | identidade/domínio |
| F-06 | `DefaultData.getCountryForTeam` | clube não encontrado | Brasil | identidade/domínio |
| F-07 | `CompetitionType.fromCode` | código desconhecido | SERIE_A | identidade de competição |
| F-08 | `CupCompetitionSystem` | confederação não-CONMEBOL | T1/T2/T3 legado | regra continental genérica |
| F-09 | `selectNationalCupTeams` | qualquer país | copa genérica até 32 | regra nacional genérica |
| F-10 | `GenerateCalendarUseCase.generateKnockoutFixtures*` | participantes ímpares | virtual 990k+ | mascaramento de bracket |
| F-11 | `selectLibertadoresTeams` legado | candidatos insuficientes | virtual 900k+ até 32 | compatibilidade perigosa |
| F-12 | `SuperMundialSystem` | clube histórico ausente | real global, virtual histórico ou dummy | qualificação parcial |
| F-13 | `SimulateWeekUseCase` | Team ausente em memória | `Time A/Time B` in-memory | mascara estado impossível V21 |
| F-14 | `MatchSlotConverter` | slot inválido persistido | WEEKEND | compatibilidade silenciosa |
| F-15 | `GameViewModelMatch` | Team ausente | virtual fallback | dívida de UI/ViewModel |

## 14. Hardcodes/regras duplicadas importantes

1. `GlobalFootballSystem.competitions` declara semanas 33..36 para continentais, mas a CONMEBOL real do runtime usa 2,5,8,11,14,17,28..31,37..41.
2. `CompetitionPhaseRules.kt` na camada de UI repete semanas/fases.
3. Strings `SERIE_*`, `DIV_*`, `CONTINENTAL_T*`, nomes de catálogo e `CompetitionType` coexistem.
4. `CompetitionType` não contém `CONTINENTAL_T3` e possui labels fortemente brasileiras/CONMEBOL.
5. `GameEngine.generateRoundRobin` duplica geração de round-robin que o calendário novo centraliza em `GenerateCalendarUseCase`.
6. Standings são calculados em mais de um ponto (`GlobalLeagueSimulationUseCase`, `SeasonTransitionUseCase`, ViewModel/helpers).
7. Super Mundial possui `getWinner` próprio, enquanto existe `CompetitionRules`; em empate sem pênaltis ele escolhe menor ID.
8. Histórico do Super Mundial usa `topScorerName = "Destaque Mundial"` e `topScorerGoals = 5` hardcoded.
9. Brasil possui 5 divisões no dataset, mas códigos para nível 4+ convergem para `SERIE_D`.
10. `WORLD_INTERCONTINENTAL` existe no catálogo sem motor ativo.

## 15. Dados mundiais agregados desconectados

`DefaultData.originalMap` contém datasets agregados/legados como `Estados Unidos / México`, `África`, `Ásia`, `Oceania`, enquanto o registry normal trabalha com países explícitos como `Estados Unidos / Canadá`, `Egito`, `Arábia Saudita`, `Austrália` etc.

Como `countriesMap` é construído sobre `GlobalFootballSystem.keys` e procura dataset predefinido por chave exata, vários clubes reais contidos nos agrupamentos legados não alimentam diretamente o país explícito correspondente. O resultado é uso de geração sintética em diversos países e aumento da necessidade de fallback virtual no Super Mundial.

**STATUS:** PARTIAL / NEEDS REFACTOR de dados, sem exigir Room V22.

## 16. Matriz de classificação

| Componente | Classificação |
|---|---|
| Room V21 / FK Fixture→Team | SAFE / COMPLETE |
| `Player.teamId nullable` / Free Agent | SAFE / COMPLETE |
| Validação 1..48 e slots | SAFE / COMPLETE |
| Promoção/rebaixamento — integridade de contagem | SAFE / COMPLETE |
| Promoção/rebaixamento — regra por país | GENERIC FALLBACK / NEEDS DOMAIN RULE |
| Simulação global compacta | SAFE estruturalmente / GENERIC DOMAIN |
| Copa nacional | GENERIC FALLBACK |
| CONMEBOL formato | PARTIAL / QUASE COMPLETO |
| CONMEBOL qualificação | PARTIAL / NEEDS DOMAIN RULE |
| UEFA | GENERIC FALLBACK |
| CONCACAF | GENERIC FALLBACK |
| CAF | GENERIC FALLBACK |
| AFC | GENERIC FALLBACK |
| OFC | PARTIAL / GENERIC FALLBACK |
| Super Mundial formato/calendário/sede | SAFE / COMPLETE |
| Super Mundial qualificação | PARTIAL / NEEDS DOMAIN RULE |
| WORLD_INTERCONTINENTAL | DEAD CODE / CATALOG ONLY |
| ESTADUAL | DEAD CODE / UNIMPLEMENTED |
| País desconhecido | HIGH RISK DOMAIN FALLBACK |
| Identidade de competição | NEEDS REFACTOR |
| Clubes virtuais — FK | SAFE |
| Clubes virtuais — isolamento de domínio | PARTIAL / NEEDS REFACTOR |

## 17. Problemas detalhados

### 9.10A-001 — País desconhecido vira CONMEBOL

- **ID:** 9.10A-001
- **PRIORIDADE:** P1
- **ARQUIVO:** `GlobalFootballSystem.kt`
- **CLASSE/FUNÇÃO:** `getConfederationForCountry`, `getContinentalTournamentsForCountry`
- **COMO ESTÁ:** país/confederação desconhecido cai silenciosamente em CONMEBOL.
- **POR QUE É UM PROBLEMA:** ausência de regra é convertida em regra esportiva válida errada.
- **EFEITO NA CARREIRA:** clube/placeholder desconhecido pode entrar no universo CONMEBOL e receber competição errada.
- **RISCO EM 5 TEMPORADAS:** classificação errada já pode aparecer.
- **RISCO EM 20 TEMPORADAS:** registros/fallbacks persistidos podem acumular associações incorretas.
- **RISCO EM 100 TEMPORADAS:** erro semântico torna-se parte permanente da história da carreira.
- **COMO DEVERIA FICAR:** registry fail-safe retornando confederação tipada ou `UNKNOWN/null`, nunca vaga continental automática.
- **DEPENDÊNCIAS:** todos consumidores de `getConfederationForCountry`.
- **TESTE NECESSÁRIO:** `unknown country does not become CONMEBOL`; `Mundial does not qualify domestically/continentally`.

### 9.10A-002 — UEFA/CONCACAF/CAF/AFC/OFC compartilham o mesmo motor continental legado

- **PRIORIDADE:** P1
- **ARQUIVO:** `CupCompetitionSystem.kt`
- **CLASSE/FUNÇÃO:** `selectContinentalFields`, `selectLegacyContinentalFields`, `generateLegacyGroupStage`
- **COMO ESTÁ:** mesmas T1/T2/T3, grupos de 4 com 3 jogos e mata-mata até semana 36.
- **POR QUE É UM PROBLEMA:** nomes diferentes escondem uma única regra genérica.
- **EFEITO NA CARREIRA:** histórias continentais esportivamente incorretas desde a primeira temporada.
- **RISCO 5/20/100:** crescente apenas em histórico esportivo; não foi demonstrada corrupção relacional.
- **COMO DEVERIA FICAR:** `ConfederationRules` + regras de competição próprias por confederação, validadas em fontes oficiais quando implementadas.
- **DEPENDÊNCIAS:** registry de país/confederação, calendário, qualificação.
- **TESTE NECESSÁRIO:** formatos/participantes/slots por competição e ausência de duplicatas.

### 9.10A-003 — Copa nacional genérica para todos os países

- **PRIORIDADE:** P1
- **ARQUIVO:** `CupCompetitionSystem.kt`
- **FUNÇÃO:** `selectNationalCupTeams`, progressão de `COPA`
- **COMO ESTÁ:** potência de 2 até 32, jogo único, final semana 27, força inclusão do usuário.
- **POR QUE É UM PROBLEMA:** não representa copa/federação específica.
- **EFEITO NA CARREIRA:** campeão e vagas futuras derivadas da copa seriam semanticamente errados.
- **RISCO 5:** imediato no país do usuário.
- **RISCO 20/100:** histórico de copas acumula regra genérica.
- **COMO DEVERIA FICAR:** `NationalCupRules?` por país, podendo ser nulo quando não suportado.
- **DEPENDÊNCIAS:** calendário e futura qualificação continental.
- **TESTE NECESSÁRIO:** field, rounds, week/slot e champion source por país.

### 9.10A-004 — Promoção/rebaixamento esportivamente genérico

- **PRIORIDADE:** P1
- **ARQUIVO:** `LeagueHierarchy.kt`, `SeasonTransitionUseCase.kt`
- **COMO ESTÁ:** mecanismo de troca é seguro; maioria usa 2 sobe/2 desce, sem playoff.
- **POR QUE É UM PROBLEMA:** quantidade/forma real varia por país/divisão.
- **EFEITO NA CARREIRA:** composição das divisões diverge da regra esperada.
- **RISCO 5:** divergência rápida.
- **RISCO 20:** ecossistema nacional historicamente diferente.
- **RISCO 100:** grande divergência esportiva, embora contagens permaneçam estáveis.
- **COMO DEVERIA FICAR:** `PromotionRelegationRules` por fronteira de divisão, com direct spots e playoffs opcionais.
- **DEPENDÊNCIAS:** standings e calendário de playoffs.
- **TESTE NECESSÁRIO:** preserve club counts; no duplicate/missing; regras por fronteira.

### 9.10A-005 — Qualificação continental via mutação transitória de rating

- **PRIORIDADE:** P1
- **ARQUIVO:** `ContinentalQualificationRules.kt`, `CupCompetitionSystem.kt`
- **COMO ESTÁ:** posição anterior é transformada em rating 100/99/98... para influenciar ordenação.
- **POR QUE É UM PROBLEMA:** origem da vaga não é um dado de domínio; copa/campeão continental/coeficiente não são representáveis corretamente.
- **EFEITO NA CARREIRA:** vagas podem ser escolhidas por ordenação genérica em vez de regra explícita.
- **RISCO 5/20/100:** histórico continental incorreto e difícil de depurar.
- **COMO DEVERIA FICAR:** `QualificationSlot`/`QualificationSource` tipados e seleção por competition ID.
- **DEPENDÊNCIAS:** standings, cup champions, competition registry.
- **TESTE NECESSÁRIO:** tabela de origem de vaga, uniqueness por Team.id, Team existente.

### 9.10A-006 — OFC sem países explícitos no fluxo normal

- **PRIORIDADE:** P2
- **ARQUIVO:** `GlobalFootballSystem.kt`, `DefaultData.kt`, `WorldDefaultData.kt`
- **COMO ESTÁ:** confederação e catálogo existem, dataset legado `Oceania` existe, mas registry normal possui 0 países OFC.
- **POR QUE É UM PROBLEMA:** OFC não tem universo normal de classificação.
- **EFEITO NA CARREIRA:** competição OFC não consegue operar como confederação real no fluxo padrão.
- **RISCO 5/20/100:** ausência estrutural de representação esportiva, sem corrupção de DB.
- **COMO DEVERIA FICAR:** países OFC explícitos ou uma política conscientemente limitada, sem alias agregado mascarando suporte.
- **DEPENDÊNCIAS:** DefaultData e registry.
- **TESTE NECESSÁRIO:** cada país suportado mapeia exatamente uma confederação; OFC possui elegíveis válidos.

### 9.10A-007 — Clubes virtuais `Mundial` vazam para standings globais

- **PRIORIDADE:** P2
- **ARQUIVO:** `GlobalFootballSystem.kt`, `repository.kt`, `GlobalLeagueSimulationUseCase.kt`, `SeasonTransitionUseCase.kt`
- **COMO ESTÁ:** virtual é materializado para FK, permanece em `Team`, e a simulação global não filtra `Mundial`.
- **POR QUE É UM PROBLEMA:** entidade criada para integridade de fixture vira participante de uma liga doméstica fictícia.
- **EFEITO NA CARREIRA:** rows `GlobalLeagueStanding(country="Mundial")`, possível contaminação de consumidores futuros.
- **RISCO 5:** pequeno, mas observável após Mundial com virtualização.
- **RISCO 20:** rows históricas adicionais persistem.
- **RISCO 100:** crescimento e ambiguidade semântica acumulados.
- **COMO DEVERIA FICAR:** virtual/world-only excluído explicitamente de liga doméstica e registry continental; preservar Team persistido para FK.
- **DEPENDÊNCIAS:** sem necessidade imediata de schema.
- **TESTE NECESSÁRIO:** virtual participant exists for Fixture FK but never appears in domestic standings/continental qualification.

### 9.10A-008 — Múltiplas fontes de verdade e calendário de catálogo desatualizado

- **PRIORIDADE:** P3
- **ARQUIVO:** `GlobalFootballSystem.kt`, `CupCompetitionSystem.kt`, `ConmebolCompetitionSystem.kt`, `CompetitionPhaseRules.kt`, `entities.kt`
- **COMO ESTÁ:** catálogo diz 33..36 enquanto CONMEBOL executa outro calendário; UI replica fases; enum/strings divergem.
- **POR QUE É UM PROBLEMA:** alterações futuras podem corrigir um lugar e quebrar outro.
- **EFEITO NA CARREIRA:** risco de apresentação/regra divergente e regressões de schedule.
- **RISCO 5:** manutenção difícil.
- **RISCO 20/100:** alto risco de regressão durante evolução do motor.
- **COMO DEVERIA FICAR:** `CompetitionRulesRegistry` como fonte canônica, UI apenas consulta projeções.
- **DEPENDÊNCIAS:** migração incremental sem trocar strings persistidas de uma vez.
- **TESTE NECESSÁRIO:** registry/code/phase/calendar consistency.

### 9.10A-009 — Namespace virtual baseado apenas em faixa de ID

- **PRIORIDADE:** P3
- **ARQUIVO:** `GlobalFootballSystem.kt`, `repository.kt`, `GenerateCalendarUseCase.kt`
- **COMO ESTÁ:** qualquer ID ausente >=200.000 é legitimado como virtual; hashes usam faixa limitada; fallbacks 900k/990k.
- **POR QUE É UM PROBLEMA:** não há provenance/competition scope; expansão aumenta risco de colisão lógica.
- **EFEITO NA CARREIRA:** identidade virtual pode sobreviver sem saber origem/finalidade.
- **RISCO 5:** baixo no universo atual.
- **RISCO 20:** moderado com novos torneios.
- **RISCO 100:** maior chance de acúmulo/colisão sem governança.
- **COMO DEVERIA FICAR:** factory/registry determinístico de identidade virtual, com validação de colisão e escopo lógico; schema só se futuramente comprovado necessário.
- **DEPENDÊNCIAS:** V21 FK deve permanecer.
- **TESTE NECESSÁRIO:** collision-free IDs for deterministic inputs; no real/virtual logical duplicate.

### 9.10A-010 — Super Mundial: formato correto, qualificação não data-driven

- **PRIORIDADE:** P2
- **ARQUIVO:** `SuperMundialSystem.kt`
- **COMO ESTÁ:** lista histórica → clubes persistidos → virtuais/dummies; clube do usuário pode ser forçado.
- **POR QUE É UM PROBLEMA:** participantes não derivam de histórico continental/slots tipados.
- **EFEITO NA CARREIRA:** edição pode ter participantes esportivamente artificiais.
- **RISCO 5:** aparece apenas em anos de edição.
- **RISCO 20/100:** histórico mundial acumulado com seleção simplificada.
- **COMO DEVERIA FICAR:** preservar 9.8 e substituir somente a fonte de qualificação quando o motor mundial estiver pronto.
- **DEPENDÊNCIAS:** fases 9.10B-D e integração 9.10E.
- **TESTE NECESSÁRIO:** 32 únicos, quotas/origens válidas, host único, cadence preservada.

### 9.10A-011 — Datasets agregados desconectados dos países explícitos

- **PRIORIDADE:** P2/P3
- **ARQUIVO:** `DefaultData.kt`, `defaultdata/*`
- **COMO ESTÁ:** `originalMap` usa algumas chaves agregadas que não coincidem com `GlobalFootballSystem.keys`.
- **POR QUE É UM PROBLEMA:** clubes reais disponíveis no código podem não alimentar os países normais correspondentes.
- **EFEITO NA CARREIRA:** mais clubes sintéticos e mais fallback mundial.
- **RISCO 5/20/100:** identidade esportiva pobre, não corrupção relacional.
- **COMO DEVERIA FICAR:** separar registry de país de fontes de seed; mapear datasets explicitamente.
- **DEPENDÊNCIAS:** DefaultData apenas; sem Room migration necessária se aplicado em novos saves com estratégia de compatibilidade.
- **TESTE NECESSÁRIO:** seeded country dataset provenance and stable IDs.

### 9.10A-012 — `CompetitionType.fromCode` desconhecido vira `SERIE_A`

- **PRIORIDADE:** P3
- **ARQUIVO:** `entities.kt`
- **COMO ESTÁ:** enum incompleto, sem T3, e fallback arbitrário para Serie A.
- **POR QUE É UM PROBLEMA:** erro de identidade de competição é escondido.
- **EFEITO NA CARREIRA:** consumidor futuro pode interpretar código novo/corrompido como liga principal.
- **RISCO 5:** baixo se só códigos conhecidos circularem.
- **RISCO 20/100:** aumenta com expansão do motor.
- **COMO DEVERIA FICAR:** parse nullable/fail-safe e nova identidade de domínio compatível com strings legadas.
- **DEPENDÊNCIAS:** UI/standings/readers.
- **TESTE NECESSÁRIO:** unknown code is not SERIE_A; T3/world/cup parse explicitly.

### 9.10A-013 — Formato de liga determinado por tamanho, não por país

- **PRIORIDADE:** P2
- **ARQUIVO:** `LeagueSeasonFormat.kt`, `GlobalLeagueSimulationUseCase.kt`
- **COMO ESTÁ:** tamanho decide 1/2 turnos/grupos; CPU >20 usa turno único.
- **POR QUE É UM PROBLEMA:** regra computacional de escala substitui formato esportivo nacional.
- **EFEITO NA CARREIRA:** standings/promotions podem vir de formato diferente do esperado.
- **RISCO 5/20/100:** divergência esportiva crescente, estrutura permanece estável.
- **COMO DEVERIA FICAR:** `NationalLeagueRules.format` por divisão, com fallback explícito apenas para países não suportados.
- **DEPENDÊNCIAS:** calendário e standings.
- **TESTE NECESSÁRIO:** configured format fits 48 weeks and preserves deterministic outputs.

### 9.10A-014 — Regras duplicadas em UI/GameEngine/ViewModel

- **PRIORIDADE:** P3
- **ARQUIVO:** `CompetitionPhaseRules.kt`, `GameEngine.kt`, `GameViewModelMatch.kt`
- **COMO ESTÁ:** fases, round-robin e standings/winner logic reaparecem fora do núcleo.
- **POR QUE É UM PROBLEMA:** refatoração mundial pode deixar consumidores divergentes.
- **EFEITO NA CARREIRA:** risco indireto de cálculo/exibição incompatível.
- **RISCO 5:** baixo no baseline atual.
- **RISCO 20/100:** manutenção futura aumenta risco.
- **COMO DEVERIA FICAR:** ViewModel/UI sem regra esportiva; migração incremental, sem grande refactor nesta fase.
- **DEPENDÊNCIAS:** registry central.
- **TESTE NECESSÁRIO:** UI phase projection equals domain competition rules.

### 9.10A-015 — WORLD_INTERCONTINENTAL é catálogo sem motor

- **PRIORIDADE:** P4/P2 se for requisito de produto
- **ARQUIVO:** `GlobalFootballSystem.kt`
- **COMO ESTÁ:** código/nome/semanas existem; gerador/progressão não encontrado.
- **POR QUE É UM PROBLEMA:** catálogo sugere funcionalidade inexistente.
- **EFEITO NA CARREIRA:** nenhuma competição é disputada.
- **RISCO 5/20/100:** ausência funcional, sem corrupção.
- **COMO DEVERIA FICAR:** remover do catálogo executável ou implementar em fase própria com regra validada.
- **DEPENDÊNCIAS:** motor mundial.
- **TESTE NECESSÁRIO:** catalog competition has executable rules or explicit `catalogOnly=false/true` semantics.

## 18. P0/P1/P2/P3/P4 consolidados

### P0 — integridade/corrupção de carreira

**Nenhum P0 ativo comprovado no fluxo normal do baseline durante a auditoria somente-leitura.**

As garantias V21, `FixtureScheduleValidator`, transição atômica e safe movement reduzem significativamente o risco. Os futuros trabalhos não podem enfraquecê-las.

### P1 — regra esportiva incorreta

1. unknown → CONMEBOL;
2. continentais não-CONMEBOL genéricas;
3. copas nacionais genéricas;
4. promoção/rebaixamento genérico;
5. qualificação continental via rating proxy.

### P2 — competição incompleta

1. OFC sem países normais;
2. CONMEBOL sem modelagem integral de origens/preliminares;
3. Super Mundial com qualificação simplificada;
4. formatos de liga por tamanho;
5. datasets agregados desconectados;
6. virtual `Mundial` aparecendo em standings globais.

### P3 — arquitetura/fallback

1. múltiplas sources of truth;
2. `CompetitionType` incompleto/fallback `SERIE_A`;
3. namespace virtual sem provenance;
4. regras duplicadas em UI/ViewModel/GameEngine;
5. catálogos/labels não equivalem à implementação.

### P4 — melhoria futura

- Intercontinental, histórico esportivo mais rico, datasets reais completos e demais refinamentos que não sejam pré-requisitos para a correção das regras mundiais.

## 19. Arquitetura recomendada — data-driven e fail-safe

A solução deve ser incremental e compatível com V21.

### 19.1 Identidades

```kotlin
enum class Confederation {
    UEFA, CONMEBOL, CONCACAF, CAF, AFC, OFC
}
```

Preferir que país desconhecido retorne `null`/resultado de erro de domínio. Se `UNKNOWN` for usado, ele **não pode conceder vaga continental**.

Criar uma identidade de competição de domínio separada do enum legado de UI/persistência, mantendo adaptadores para strings existentes.

### 19.2 Registry de país

```kotlin
interface CountryFootballRulesRegistry {
    fun find(country: String): CountryFootballRules?
}
```

`CountryFootballRules` deve conter apenas aquilo que o projeto realmente precisa e pode validar, por exemplo:

- country id/name;
- confederation;
- divisões;
- formato de cada divisão;
- promoção/rebaixamento por fronteira;
- copa nacional opcional;
- política de qualificação continental.

### 19.3 Regras continentais

Separar:

- `ConfederationRules`;
- `ContinentalCompetitionRules`;
- `ContinentalQualificationRules`;
- `QualificationSlot` / `QualificationSource`;
- `CompetitionCalendarRules`.

A origem da vaga deve ser explícita, não codificada em `Team.rating`.

### 19.4 Calendário

Cada regra de competição deve fornecer suas fases e slots para o validador global. `FixtureScheduleValidator` permanece como barreira final.

### 19.5 Clubes virtuais

Criar uma factory/registry explícita para virtualização. Mesmo sem nova coluna Room nesta etapa, consumidores domésticos devem possuir um filtro canônico único para `worldOnly/virtual` ao invés de inferências espalhadas.

Não desabilitar FK. Não voltar a `teamId=0`.

## 20. Arquivos que deverão ser modificados futuramente

### Núcleo provável

- `GlobalFootballSystem.kt` — retirar fallbacks arbitrários por meio de adaptação gradual.
- `CupCompetitionSystem.kt` — deixar de ser registry de todas as confederações.
- `ContinentalQualificationRules.kt` — substituir rating proxy por seleção tipada.
- `ContinentalQualificationQuotaPolicy.kt` — virar uma implementação CONMEBOL dentro do novo modelo.
- `LeagueHierarchy.kt` — consumir regras por país.
- `LeagueSeasonFormat.kt` — permanecer como helper matemático, não regra nacional final.
- `GenerateCalendarUseCase.kt` — orquestrar regras, sem inventar participante.
- `GlobalLeagueSimulationUseCase.kt` — usar formato nacional configurado e excluir world-only.
- `SeasonTransitionUseCase.kt` — consumir regras de movement, preservando transação/invariantes.
- `DefaultData.kt` — alinhar country registry/datasets.
- `CompetitionPhaseRules.kt` — futuramente consultar projeção do domínio, sem refactor de UI nesta fase.
- `entities.kt` — somente adaptar parse de identidade; **sem schema change necessário**.

### Preservar ao máximo

- `GameCalendar.kt`;
- `FixtureScheduleValidator`/`MatchSlot`;
- `CompetitionRules` como mecânica compartilhada;
- `SuperMundialEditionPolicy`;
- fluxo V21 de materialização de Team antes de Fixture, com tightening futuro de provenance.

## 21. Arquivos novos sugeridos

Nomes podem ser adaptados ao estilo do projeto:

- `Confederation.kt`
- `CountryFootballRules.kt`
- `CountryFootballRulesRegistry.kt`
- `NationalLeagueRules.kt`
- `NationalCupRules.kt`
- `PromotionRelegationRules.kt`
- `ContinentalCompetitionRules.kt`
- `ContinentalQualificationRulesV2.kt` ou substituição compatível
- `QualificationSlot.kt`
- `CompetitionRulesRegistry.kt`
- `UefaCompetitionSystem.kt` somente quando a fase UEFA começar

Evitar criar uma floresta de abstrações antes do primeiro consumidor real. O núcleo mínimo deve ser implementado por necessidade demonstrada.

## 22. Testes novos necessários

1. `CountryConfederationRegistryTest`
   - todos os 52 países atuais;
   - unknown não vira CONMEBOL;
   - `Mundial` não classifica automaticamente.
2. `CompetitionRulesRegistryTest`
   - competição conhecida tem exatamente uma regra;
   - código desconhecido falha de modo seguro.
3. `PromotionRelegationCountryRulesTest`
   - contagem preservada;
   - sem clube duplicado/desaparecido;
   - playoffs quando configurados.
4. `ContinentalQualificationUniquenessTest`
   - sem duplicate Team.id;
   - todos IDs existem em Team;
   - origem da vaga rastreável.
5. `VirtualTeamIsolationTest`
   - virtual pode satisfazer FK mundial;
   - não entra em liga doméstica/qualificação continental indevida.
6. `WorldCalendarRulesTest`
   - todas semanas 1..48;
   - sem dois jogos incompatíveis no mesmo slot;
   - Super Mundial preservado.
7. `StructuralDeterminismTest`
   - mesma entrada → mesma estrutura de participantes/grupos/calendário.
8. 5 temporadas antes do stress longo.
9. 20 temporadas.
10. 100 temporadas.
11. `foreign_key_check == 0` e `integrity_check == ok` após os stresses.

Todos os testes anteriores permanecem contratos obrigatórios.

## 23. Room / V22

### Mudança Room necessária agora?

**NÃO.**

O motor de regras pode ser data-driven em Kotlin e consumir as entidades existentes. Não há motivo para criar V22 apenas para organizar regras esportivas.

### Quando reconsiderar schema?

Somente se uma fase posterior demonstrar necessidade persistente real, por exemplo histórico de coeficientes, provenance de qualificação que precise sobreviver à temporada ou identidade virtual persistente não derivável. Nesse caso deve existir proposta separada de migration antes da implementação.

## 24. Plano recomendado para a Fase 9.10B

A 9.10B deve começar por um pequeno **B0 de fundação**, dentro da própria fase, antes de implementar UEFA:

1. criar enum/identidade `Confederation` tipada;
2. criar registry fail-safe país→confederação para os países atualmente suportados;
3. garantir que unknown/`Mundial` não sejam classificados;
4. criar `CompetitionRulesRegistry` mínimo;
5. criar tipos mínimos para `QualificationSlot/Source`;
6. adaptar consumidores sem alterar schema/string persistida em massa;
7. adicionar testes de registry/fail-safe;
8. só então consultar documentação oficial/primária UEFA atual e modelar Champions/Europa/Conference;
9. definir quotas/formato/calendário UEFA com fontes registradas no PR;
10. integrar UEFA sem tocar na CONMEBOL além de adaptadores necessários;
11. rodar testes focados; depois checkpoint consolidado de CI apenas ao fim do lote.

Não começar 9.10C antes de estabilizar o registry e UEFA.

## 25. Sequência recomendada das subfases

- **9.10A — atual:** auditoria + desenho do motor base; documentação apenas neste checkpoint.
- **9.10B:** fundação mínima do registry + UEFA.
- **9.10C:** CONCACAF + CAF.
- **9.10D:** AFC + OFC.
- **9.10E:** integração mundial, origens de vagas, calendário cruzado, isolamento de virtual; revisar qualificação do Super Mundial sem destruir formato 9.8.
- **9.10F:** stress mundial 5 → 20 → 100 temporadas e validações Room.

## 26. Resultado pelos itens exigidos

1. **SHA atual da main utilizado:** `cc5c95c43e00ba825f488aac6de4dc21e886ee27`.
2. **Branch criada:** `agent/v3-world-football-rules-audit`.
3. **PR draft:** deve apontar para esta branch; sem merge.
4. **Main alterada:** não.
5. **Arquivos auditados:** listados na seção 2.
6. **Países encontrados:** 52 explícitos; lista completa na seção 4.
7. **País→confederação:** seção 4; unknown→CONMEBOL identificado como P1.
8. **Ligas nacionais:** `SERIE_A/B/C/D` + aliases `DIV_n`, genéricas por tamanho.
9. **Copas nacionais:** `COPA`, genérica; `ESTADUAL` sem motor ativo.
10. **Continentais:** CONMEBOL dedicada; demais genéricas T1/T2/T3.
11. **Super Mundial:** formato, cadência, sede e semanas preservados; qualificação parcial.
12. **Classificação:** standings globais compactos + resultados detalhados do país do usuário.
13. **Promoção:** troca segura, regras majoritariamente genéricas.
14. **Rebaixamento:** idem.
15. **Calendário:** 48 semanas, dual-slot, matriz seção 10.
16. **Clubes virtuais:** seção 11.
17. **Fallbacks:** seção 13.
18. **Hardcodes:** seção 14.
19. **Regras duplicadas:** seção 14.
20. **Regras incorretas:** seção 17/P1.
21. **Riscos P0:** nenhum ativo comprovado na leitura; preservar invariantes V21.
22. **Riscos P1:** seção 18.
23. **Riscos P2:** seção 18.
24. **Arquitetura recomendada:** seção 19.
25. **Arquivos a modificar:** seção 20.
26. **Arquivos novos sugeridos:** seção 21.
27. **Testes novos:** seção 22.
28. **Mudança Room:** não necessária.
29. **V22:** não necessária nesta fase.
30. **Plano 9.10B:** seção 24.
31. **Merge:** não realizado.
32. **Fase 9.11:** não iniciada.

## 27. Encerramento formal

**FASE 9.10A: AUDITORIA CONCLUÍDA**

- MAIN: INALTERADA
- BRANCH: CRIADA A PARTIR DA MAIN VALIDADA
- PR: DRAFT / SEM MERGE
- MAPEAMENTO MUNDIAL: CONCLUÍDO PARA O CÓDIGO ATUAL
- CONMEBOL: PARTIAL / QUASE COMPLETO
- UEFA: GENERIC FALLBACK
- CONCACAF: GENERIC FALLBACK
- CAF: GENERIC FALLBACK
- AFC: GENERIC FALLBACK
- OFC: PARTIAL / GENERIC FALLBACK
- FALLBACKS: MAPEADOS
- PAÍS → CONFEDERAÇÃO: AUDITADO
- PROMOÇÃO/REBAIXAMENTO: AUDITADO
- QUALIFICAÇÃO CONTINENTAL: AUDITADA
- CALENDÁRIO: AUDITADO
- CLUBES VIRTUAIS: AUDITADOS
- ROOM V21: PRESERVADO
- SUPER MUNDIAL: PRESERVADO
- STRESS 20/100: NÃO ENFRAQUECIDOS
- V22: NÃO
- MERGE: NÃO
- FASE 9.11: NÃO INICIADA

A implementação deve parar neste ponto até o próximo comando, conforme mutation gate definido para a Fase 9.10A.
