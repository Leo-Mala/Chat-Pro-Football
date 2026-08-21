# Fase 10.2 — Arquitetura

## Baseline e escopo

- Repositório: `Leo-Mala/Chat-Pro-Football`
- Baseline oficial: `main@1571afb4a98a3bc767d16631e9bb992e4c066eaf`
- Branch: `agent/phase-10-2-architecture`
- PR: `#52`
- Room: **V22**, sem mudança de schema nesta fase
- Dataset FC26: **18.405 jogadores** preservados por contrato
- Universo de performance: **60.885 jogadores persistidos**

A fase é exclusivamente arquitetural. UEFA/competições mundiais (10.3), empréstimos factuais FC26 (10.4), atualização de elencos/ratings e remodelagem visual ficam fora do escopo.

> Nota sobre o SHA final: um arquivo versionado não pode conter o SHA do próprio commit final sem criar um novo SHA. Por isso o head final exato, o run final e a classificação `APTO PARA MERGE` são registrados na descrição/comentário de auditoria do PR depois que este relatório já faz parte do head auditado.

## Auditoria arquitetural inicial

A árvore consolidada após a Fase 10.1 mostrou os seguintes pontos de maior risco:

1. `GameViewModel.kt` permanece uma classe grande (aprox. 72 KB), acumulando sessão de save, estado de UI, escalação, simulação, preferências, evolução e coordenação de vários domínios.
2. `GameViewModelTransfers.kt` continha regras de negócio de renovação contratual, negociação de compra, troca de clube do treinador e persistência de academia diretamente na camada de ViewModel/extensões.
3. `YouthAcademyUseCase` dependia de `GameViewModel.AcademyProspect`, invertendo o boundary desejado (`usecase -> UI`).
4. A negociação de compra reportava `accepted` depois de chamar a persistência sem verificar se `ProcessTransfersUseCase` havia retornado erro; uma contratação rejeitada transacionalmente podia ser apresentada como concluída.
5. A renovação contratual emitia feedback de UI de dentro da transação e misturava snapshot de UI com regra/persistência.
6. A aceitação de oferta de treinador misturava a transação de domínio com a publicação de `_selectedTeamId`.
7. `GameViewModelFinances.kt` possuía uma extensão `upgradeTrainingCenter()` sombreada pelo membro de mesmo nome em `GameViewModel`. Além de não ser o destino das chamadas normais Kotlin, as duas implementações carregavam regras/custos diferentes.
8. DI/save slots estavam estruturalmente corretos: `GameSaveRepository` mantém `GameRepository`/`AppDatabase` estáveis por slot. O risco seria transformar repositories dependentes de slot em singletons compartilhados; isso foi explicitamente evitado.
9. A Fase 10.1 já havia criado boundaries críticos que não deveriam ser reabertos: planejamento mensal pesado fora da transação, stale fail-closed, writes de evolução direcionados, idempotência do histórico e `_incomingOffers` publicado somente após commit semanal.

## Implementação arquitetural

A refatoração foi incremental, sem reescrita total e sem alterar o pipeline semanal/mensal de 60K.

### `ContractLifecycleUseCase`

A renovação contratual saiu do ViewModel. O novo boundary:

- recebe apenas identidade/duração;
- relê `GameSave` e `Player` dentro da transação;
- valida pertencimento ao clube atual e estado de agente livre;
- altera apenas campos de contrato/salário;
- retorna `Success`, `Rejected` ou `Unavailable`;
- não emite toast nem toca StateFlow.

O ViewModel passou a somente chamar o caso de uso e traduzir o resultado para feedback após o retorno da transação.

### `TransferNegotiationUseCase`

A política de preço, recusa e contraproposta saiu de `GameViewModelTransfers`.

O boundary preserva a fórmula existente de preço dinâmico e delega qualquer mutação a `ProcessTransfersUseCase`. Foi corrigida uma inconsistência funcional encontrada durante a auditoria: uma oferta que atingia o preço pedido era anteriormente reportada como `accepted` mesmo se `buyPlayerAdvanced` retornasse erro (por exemplo, falha transacional/estado inválido). Agora somente um `TransferResult.Success` vira `Accepted`; erros reais viram `Declined` com a razão original.

### `CoachCareerUseCase`

A troca do clube controlado e a limpeza das ofertas são uma única unidade de persistência. `_selectedTeamId` permanece responsabilidade de UI e só é publicado depois de `Success`.

### Academia

Foi criado `AcademyProspect` no domínio e removido o import de `GameViewModel` de `YouthAcademyUseCase`. O codec/gerador agora depende apenas de dados/domínio.

`YouthAcademyManagementUseCase` passou a ser responsável por:

- promoção atômica de prospecto;
- upgrade de nível;
- investimento semanal;
- descarte de prospecto.

`GameViewModelTransfers` mantém DTO compatível para a UI e faz a conversão somente no boundary.

### Código morto comprovado

A extensão `GameViewModel.upgradeTrainingCenter()` em `GameViewModelFinances.kt` foi removida. O membro `GameViewModel.upgradeTrainingCenter()` continua intacto, portanto o comportamento canônico existente não foi trocado pela implementação divergente da extensão.

## Arquitetura resultante no recorte

`UI -> GameViewModel/extensions -> UseCase -> GameRepository -> DAO/Room`

Regras aplicadas:

- UseCases novos não conhecem toast, `MutableStateFlow` ou callback de tela.
- Feedback em memória é publicado depois do retorno/commit.
- Rejeições esperadas usam resultados tipados em vez de exceções.
- `ProcessTransfersUseCase` continua owner da transação financeira/jogador; negociação não duplica persistência.
- Casos de uso dependentes de banco recebem sempre o `GameRepository` da `SaveSession` ativa.
- Nenhum repository de carreira foi promovido a singleton global.

## DI e isolamento de save slots

`GameSaveRepository` permanece singleton apenas como gerenciador/factory de repositories por slot. Cada `slotId` mantém seu próprio `GameRepository` e `AppDatabase`. A Fase 10.2 não mudou `SlotDatabaseFactory`, nomes de banco, lifecycle de fechamento ou cache por slot.

Os novos casos de uso são efêmeros e recebem o repository do save ativo, evitando retenção acidental de repository de outro slot quando o usuário troca de carreira.

## Coroutines e transações

- nenhum `GlobalScope` adicionado;
- nenhum `runBlocking` de produção adicionado;
- nenhum `Thread.sleep` adicionado;
- nenhum retry genérico adicionado;
- nenhuma rede dentro de transaction;
- nenhuma computação 60K nova dentro de transaction;
- nenhum timeout/budget foi relaxado;
- `processWeekEndEconomicAndEvolution` e `MonthlyEvolutionPlan` da Fase 10.1 permanecem intactos.

## Room

Room permanece **V22**.

Não houve alteração de entity/schema, portanto:

- `MIGRATION_21_22` permanece a migration final;
- `index_historico_evolucao_data` permanece ativo;
- `app/schemas/com.example.data.AppDatabase/22.json` permanece o snapshot atual;
- não foi criada migration artificial apenas por refatoração de código.

## Testes da Fase 10.2

`Phase102ArchitectureBoundaryTest` adiciona regressões para:

1. renovação contratual usando estado fresco e preservando campos não relacionados;
2. rejeição sem escrita quando o jogador não pertence mais ao clube atual;
3. negociação não reportar `accepted` quando `ProcessTransfersUseCase` não persistiu a contratação;
4. troca de clube do treinador persistida antes da publicação de estado de UI;
5. academia persistida por boundary de domínio sem dependência de ViewModel.

A suíte existente continua obrigatória: CareerFunctionalFlow, SaveSlotIsolation, SaveAtomicityRegression, WeeklyFinalizationAtomicity, migrations, evolução/stale/idempotência, rollback de incoming offers, FC26, benchmark 60K e stress 20/100.

## Performance — baseline da Fase 10.1

Artifact final do head `39e75f587038520f687dba6d4701cbadf127162a`:

- dataset FC26: 18.405;
- jogadores persistidos: 60.885;
- persistência inicial: 3.232 ms;
- reload completo: 14.260 ms;
- evolução mensal: 17.252 ms;
- pico observado de heap: 442.694.264 bytes;
- database: 75.005.952 bytes.

Budgets preservados sem alteração:

- persistência: 20.289 ms;
- reload: 47.181 ms;
- evolução mensal: 65.553 ms;
- heap: 762.995.562 bytes.

O CI final da Fase 10.2 deve repetir o mesmo gate no head exato; resultados finais ficam no artifact `fc26-phase-10-1-audit-reports` do run aprovado e são registrados na auditoria do PR.

## FC26

Nenhum asset, manifest, rating, atributo, ID, source ID, clube ou outro dado factual FC26 foi modificado pelo diff arquitetural. O gate final continua exigindo:

- 18.405/18.405 processados/importados;
- `duplicatePlayerIds = 0`;
- `duplicateTeamIds = 0`;
- `overallMutated = 0`;
- `potentialMutated = 0`;
- `attributesMutated = 0`.

## Review

O primeiro review Codex do candidato arquitetural `d998dfcc9dcf424c03b28f3800b981e4e9b9fde7` não encontrou problemas materiais. Como este relatório gera um commit posterior, o head final deve receber novamente review/auditoria exatos antes do merge.

## Riscos residuais

- `GameViewModel.kt` ainda é grande. Uma divisão total de uma só vez foi rejeitada por risco de regressão; a estratégia permanece incremental e orientada a boundaries cobertos por testes.
- `GameRepository` continua como façade ampla por compatibilidade. Dividi-lo integralmente agora exigiria migração de dezenas de call sites e aumentaria o risco sem benefício proporcional para 10.3/10.4.
- Algumas APIs legadas ainda coordenam domínios em extensões de ViewModel. Elas não justificam abstrações vazias; futuras extrações devem ocorrer quando houver responsabilidade concreta + teste correspondente.

## Conclusão

A Fase 10.2 remove regras concretas de contrato, negociação, carreira e academia da camada de UI, elimina uma dependência invertida `usecase -> ViewModel`, corrige um falso sucesso de negociação, remove código financeiro sombreado e preserva integralmente os boundaries de performance/atomicidade da Fase 10.1. O estado final de merge é determinado exclusivamente pelo CI, artifacts, review e auditoria do head que contém este relatório.
