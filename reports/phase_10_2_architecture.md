# Fase 10.2 — Arquitetura

## Baseline e escopo

- Repositório: `Leo-Mala/Chat-Pro-Football`
- Baseline oficial: `main@1571afb4a98a3bc767d16631e9bb992e4c066eaf`
- Branch: `agent/phase-10-2-architecture`
- Room: **V22**, sem alteração de schema planejada nesta fase
- Dataset FC26: preservação obrigatória de **18.405 jogadores**
- Universo de performance de referência: **60.885 jogadores persistidos**

A fase é exclusivamente arquitetural. UEFA/competições mundiais (10.3), empréstimos factuais FC26 (10.4), atualização de elencos/ratings e remodelagem visual ficam explicitamente fora do escopo.

## Auditoria arquitetural inicial

A árvore consolidada após a Fase 10.1 mostrou os seguintes pontos de maior risco:

1. `GameViewModel.kt` permanece uma classe muito grande (aprox. 72 KB), acumulando sessão de save, estado de UI, escalação, simulação, preferências, evolução e coordenação de vários domínios.
2. `GameViewModelTransfers.kt` continha regras de negócio de renovação contratual, negociação de compra, troca de clube do treinador e persistência de academia diretamente na camada de ViewModel/extensões.
3. `YouthAcademyUseCase` dependia de `GameViewModel.AcademyProspect`, invertendo o boundary desejado (`usecase -> UI`).
4. A negociação de compra reportava `accepted` depois de chamar a persistência sem verificar se `ProcessTransfersUseCase` havia retornado erro; um negócio recusado por regra transacional podia ser apresentado pela UI como concluído.
5. A renovação contratual emitia feedback de UI de dentro da transação e usava parte do estado lido antes do boundary transacional.
6. A aceitação de oferta de treinador misturava a transação de domínio com a publicação de `_selectedTeamId`.
7. `GameViewModelFinances.kt` possuía uma extensão `upgradeTrainingCenter()` sombreada por um membro de mesmo nome em `GameViewModel`; além de morta para chamadas normais Kotlin, as duas implementações tinham regras/custos diferentes, criando risco de manutenção.
8. A Fase 10.1 já estabeleceu boundaries críticos que não devem ser reabertos: planejamento mensal pesado fora da transação, stale fail-closed, writes de evolução direcionados, idempotência do histórico e publicação de `_incomingOffers` somente após commit semanal.

## Plano técnico adotado

A refatoração é incremental, sem reescrita total do projeto e sem transformar repositories de save em singletons globais.

### Boundaries extraídos

- `ContractLifecycleUseCase`: renovação contratual com snapshot fresco e validação/persistência em uma única transação; retorna resultado tipado e não toca UI.
- `TransferNegotiationUseCase`: política de preço/contraproposta e tradução do resultado real de `ProcessTransfersUseCase`; não pode reportar compra aceita se a transação falhou.
- `CoachCareerUseCase`: troca do clube controlado e limpeza de ofertas como uma unidade de persistência; `_selectedTeamId` só é publicado pelo ViewModel após sucesso.
- `YouthAcademyManagementUseCase`: promoção, upgrade, investimento e descarte de prospectos fora da camada de UI.
- `AcademyProspect`: modelo de domínio próprio da academia; `YouthAcademyUseCase` deixa de importar `GameViewModel`.

`GameViewModelTransfers.kt` passa a coordenar esses casos de uso e traduzir resultados para toast/callback/StateFlow, mantendo o contrato público da UI existente.

### Código morto comprovado

A extensão `GameViewModel.upgradeTrainingCenter()` de `GameViewModelFinances.kt` foi removida. Em Kotlin, um membro da classe sempre prevalece sobre uma extensão de mesmo nome; portanto o método de extensão não era o destino das chamadas normais e carregava uma regra financeira divergente sem efeito canônico.

## Boundaries resultantes

Fluxo alvo aplicado neste recorte:

`UI -> GameViewModel/extensions -> UseCase -> GameRepository -> DAO/Room`

Regras adotadas:

- UseCases novos não escrevem `MutableStateFlow`/toast.
- Feedback só é publicado após retorno/commit do caso de uso.
- Repository usado pelos casos de uso continua sendo o repository do `SaveSession` ativo; nenhum cache/repository global foi introduzido.
- Situações normais de rejeição usam resultados tipados em vez de exceção.
- `ProcessTransfersUseCase` continua sendo o owner da transação financeira/jogador; `TransferNegotiationUseCase` não duplica sua persistência.

## Coroutines, transações e save slots

- Não foi adicionado `GlobalScope`, `runBlocking`, `Thread.sleep` ou retry genérico.
- Nenhuma operação de rede foi introduzida em transação.
- Novas mutações de contrato/carreira/academia mantêm leitura de estado relevante no boundary transacional ou usam a operação atômica já existente.
- A criação dinâmica de casos de uso recebe sempre o `GameRepository` do save ativo, preservando o isolamento por slot estabelecido nas fases anteriores.
- O fechamento semanal e o pipeline mensal da Fase 10.1 não foram reescritos nesta fase.

## Testes adicionados

`Phase102ArchitectureBoundaryTest` cobre:

- renovação contratual a partir de estado fresco;
- rejeição de renovação após mudança de clube;
- negociação que não pode reportar sucesso quando a transferência não persistiu;
- troca de clube do treinador somente após commit;
- persistência da academia sem dependência da camada de ViewModel.

As suítes existentes continuam sendo gates obrigatórios, especialmente save-slot isolation, atomicidade semanal/save, stale-state, Room/migrations, FC26, performance 60K e stress 20/100 temporadas.

## Room e dados factuais

Nenhuma mudança de schema foi necessária: Room permanece **V22**, `MIGRATION_21_22` e `22.json` permanecem inalterados.

Nenhum arquivo factual FC26 foi alterado por esta refatoração. O gate final deve reconfirmar 18.405/18.405, IDs duplicados zero e mutações factuais de overall/potential/atributos zero.

## Riscos residuais deliberadamente limitados

- `GameViewModel.kt` ainda é grande. A fase reduz responsabilidades de maior risco de forma incremental em vez de uma divisão total que aumentaria muito o risco de regressão antes das fases 10.3/10.4.
- Algumas APIs legadas continuam como façade/compatibilidade para evitar mudança massiva de call sites.
- Outras extensões de ViewModel ainda coordenam domínios existentes; só devem ser extraídas quando houver benefício concreto e cobertura de comportamento.

## Validação final

A preencher com o head final e os artifacts do CI:

- Head final: pendente
- PR: pendente
- Build: pendente
- Regressões/non-stress: pendente
- Save-slot isolation: pendente
- Atomicidade: pendente
- Room V22/schema: pendente
- FC26 18.405/18.405: pendente
- Benchmark 60.885: pendente
- Stress 20 temporadas: pendente
- Stress 100 temporadas: pendente
- Codex review: pendente

## Conclusão provisória

A refatoração concentra a mudança em boundaries concretos e testáveis, preserva o repository por save, não altera schema ou dados factuais e evita reabrir o hot path mensal/semanal já estabilizado. A conclusão definitiva depende do CI no head exato, artifacts, review e auditoria final.
