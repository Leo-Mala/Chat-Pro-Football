# Fase 10.4 — Empréstimos FC26 Seguros

## Estado e escopo

- Repositório: `Leo-Mala/Chat-Pro-Football`
- Baseline oficial: `main@f9c01e53130fe46762d988600aa3ec18c3ee4e1d`
- Branch: `agent/phase-10-4-fc26-safe-loans`
- PR: `#54 — feat: Fase 10.4 — FC26 safe loans`
- Head técnico consolidado antes deste relatório: `5f63c17cc31b42ab3f5fc6edc2e9549c6c2722a9`
- Room: V22, sem nova migration e sem `fallbackToDestructiveMigration`.
- PR #34 permanece fora do escopo, Draft e congelado em `c421dafee0ecdd2ea23f7d4f794228ab5f881131`.
- Não houve pesquisa de novos elencos, API-Football, reaproveitamento factual do PR #34 ou alteração de ratings/atributos FC26.

O SHA final não é gravado literalmente neste arquivo porque o próprio conteúdo participa do cálculo do commit. O gate final exige que o artifact `phase_10_4_fc26_loans_audit.json` tenha `auditHead` exatamente igual ao head do PR que contém este relatório; a classificação/merge é registrada no PR após essa validação.

## Fonte factual FC26

Snapshot validado:

- 18.405 jogadores;
- 662 clubes de origem;
- 1.325 jogadores com `club_loaned_from` não nulo.

Metadados preservados no runtime:

- `club_team_id`;
- `club_name`;
- `club_position`;
- `club_loaned_from`;
- `contract_until_year`.

`contract_until_year` representa o contrato principal e não é usado como data de término do empréstimo. O snapshot não fornece datas factuais confiáveis de empréstimo, portanto:

- `startDate = NOT_AVAILABLE`;
- `endDate = NOT_AVAILABLE`;
- política temporal = `OPEN_ENDED_UNTIL_EXPLICIT_CAREER_EVENT`;
- sentinel runtime: season/week/durationWeeks/remainingWeeks = `0`;
- nenhuma data, taxa semanal ou opção de compra é inventada.

## Resolução owner / borrower

O borrower é o clube atual factual do snapshot quando existe match canônico seguro. O owner é resolvido a partir de `club_loaned_from`. A resolução aceita apenas identidade explícita/estável, nome/alias exato ou normalização conservadora única. Fuzzy matching é somente diagnóstico e nunca promove uma relação.

Antes de materializar `PlayerLoan` são exigidos `playerId > 0`, owner/borrower positivos, owner diferente do borrower, borrower coerente com o roster atual e ausência de segunda relação ativa incompatível.

Categorias auditáveis: `RESOLVED`, `AMBIGUOUS_OWNER`, `AMBIGUOUS_BORROWER`, `OWNER_NOT_FOUND`, `BORROWER_NOT_FOUND`, `SELF_LOAN`, `DUPLICATE_ACTIVE_LOAN`, `INVALID_REFERENCE` e `UNSUPPORTED_METADATA`.

## Métricas factuais consolidadas

O workflow `FC26 Bulk Player Import #111`, no head técnico `5f63c17c...`, terminou SUCCESS. O artifact manteve as métricas:

| Métrica | Resultado |
|---|---:|
| datasetPlayers | 18.405 |
| validatedPlayers | 18.405 |
| processedPlayers | 18.405 |
| importedPlayers | 18.405 |
| persistedPlayers | 60.885 |
| datasetLoanPlayers | 1.325 |
| resolvedLoans | 816 |
| rejectedLoans | 509 |
| ownerNotFound | 60 |
| borrowerNotFound | 448 |
| ambiguousBorrower | 1 |
| ambiguousOwner real no snapshot | 0 |
| selfLoansRejected | 0 |
| duplicateLoans | 0 |
| duplicatePlayerIds | 0 |
| duplicateTeamIds | 0 |
| overallMutated | 0 |
| potentialMutated | 0 |
| attributesMutated | 0 |

Distribuição factual: `RESOLVED=816`, `BORROWER_NOT_FOUND=448`, `OWNER_NOT_FOUND=60`, `AMBIGUOUS_BORROWER=1`.

## Fail-closed e quarentena

Nenhum dos 509 sinais rejeitados é convertido em ownership por heurística.

- borrower não resolvido/ambíguo permanece sem clube runtime negociável;
- owner não resolvido/ambíguo entra em estado de quarentena `LOAN_OWNERSHIP_UNRESOLVED`;
- a quarentena não cria `PlayerLoan` fictício;
- não autoriza venda, recompra, reempréstimo, renovação de contrato, aposentadoria com prospecto para o borrower ou qualquer inferência de ownership;
- corrupção/stale state detectada pelo financeiro também termina em quarentena ou owner verificado, nunca em promoção do roster atual para owner.

IDs, force/overall, potential e `Atributos` permanecem factuais e imutáveis.

## Lifecycle de empréstimo

Para relações `RESOLVED`:

- há uma única identidade de jogador;
- `Player.teamId` é o roster esportivo borrower;
- `Player.originalTeamId` é o owner;
- `Player.isOnLoan = true`;
- `PlayerLoan` guarda owner/borrower e estado temporal.

O sentinel FC26 open-ended não sofre countdown inventado. O empréstimo pode terminar por retorno/recall explícito, transferência permanente ou perda real do vínculo principal.

`LoanLifecycleUseCase.returnToOwner` revalida Player/PlayerLoan dentro da transação. Linha stale não movimenta o jogador. Se o contrato principal já expirou, o jogador vira free agent; o contrato não é recriado.

O fluxo de retorno/recall foi ligado à UI de produção com autorização ownership-aware.

## Contratos

Borrower não pode renovar o contrato principal de um loanee. O owner humano pode renovar um jogador emprestado para fora pela projeção owner-side da UI.

Para owner CPU, a manutenção semanal inclui loanees consistentes na decisão de retenção por `originalTeamId`. Essa decisão ocorre antes de `FinanceUseCase`, impedindo que um snapshot loan seja encerrado/free-agent antes de a CPU ter oportunidade de renovar o contrato principal.

Se um snapshot loan chega à última semana sem renovação válida, ele é encerrado no mesmo fechamento semanal, sem uma semana extra de ownership artificial.

## Transferências permanentes e UI

O borrower pode converter seu loanee em compra permanente, mas não pode vendê-lo como owner. O owner pode vender um jogador emprestado para fora mesmo com exatamente 16 atletas no roster ativo, porque essa venda não reduz seu elenco esportivo atual.

Na conversão compra/parcelamento, o wage-cap substitui o salário do loanee pelo novo salário contratual e não soma os dois. Saldo, histórico, parcelas, contrato, roster, ownership e encerramento do `PlayerLoan` participam da mesma transação Room.

A UI owner-side expõe renovação e venda (à vista/parcelada) para jogadores emprestados para fora. A geração semanal de ofertas não cria propostas impossíveis para jogadores apenas recebidos por empréstimo.

## Atomicidade, save/reopen e isolamento

Testes cobrem:

- rollback forçado após mutações intermediárias;
- Player, PlayerLoan, saldo e histórico restaurados juntos;
- novo save com players/loans do mesmo plano factual;
- save/reopen preservando playerId, owner, borrower, status e contagem ativa;
- isolamento entre slots independentes;
- seed one-shot por `GameRepository` sem cache global compartilhado.

## Room e concorrência

- schema atual verificado: `app/schemas/com.example.data.AppDatabase/22.json`;
- artifact Android #758 contém `22.json` com `version: 22`;
- nenhuma migration V22→V23 foi criada;
- nenhuma limpeza/destructive migration foi adicionada;
- sem `GlobalScope` ou `runBlocking` novo em produção;
- resolução/parsing do universo FC26 ocorre fora das transações;
- caminho semanal de loans não materializa `getAllPlayers()` e evita `IN (:ids)` massivo.

## Performance

`Android CI Build #758` executou `GlobalMainAuditPerformanceStressTest` no head `5f63c17c...` e terminou SUCCESS.

| Medição | Resultado | Budget existente |
|---|---:|---:|
| persistedPlayers | 60.885 | — |
| initialPersistenceMillis | 3.622 ms | 20.289 ms |
| reopenAndFullPlayerReloadMillis | 16.108 ms | 47.181 ms |
| monthlyEvolutionMillis | 19.354 ms | 65.553 ms |
| observedCheckpointPeakHeapBytes | 441.392.576 | 762.995.562 |

Nenhum threshold foi aumentado ou afrouxado.

## Gates técnicos consolidados

No head técnico `5f63c17cc31b42ab3f5fc6edc2e9549c6c2722a9`:

- Android CI Build #758: SUCCESS;
- FC26 Bulk Player Import #111: SUCCESS;
- FC26 Factual Club Target Materialization #103: SUCCESS;
- FC26 Remaining Lower-Tier Coverage #30: SUCCESS;
- `assembleDebug`: PASS;
- suíte unitária/regressão/migration-save safety: PASS;
- materialização FC26/Phase 9.14 sem cache: PASS;
- benchmark 60K: PASS;
- stress 20 temporadas: PASS;
- stress 100 temporadas match-by-match: PASS;
- save-slot/Room migration safety: PASS;
- Room V22 exportado e rastreado: PASS;
- APK debug artifact: presente.

O commit que contém este relatório restaura também o gate Bulk mais forte (incluindo `Fc26RejectedLoanQuarantineIntegrationTest`, mapper/identity tests e `-PexcludeStressTests=true`) e faz Android/Bulk/Factual/Lower-Tier reagirem ao relatório, garantindo revalidação do head final sem remoção ou enfraquecimento de testes.

## Review técnico / Codex

As rodadas de Codex encontraram problemas de ownership/lifecycle/UI, todos endereçados com correções e regressões: dupla contagem salarial, roster-floor, compra de loanee na UI, ofertas impossíveis, sentinel open-ended, stale loan ownership, fechamento da última semana, retorno stale, classificação de owner ambíguo, sentinel com playerId inválido, quarentena de owner/borrower não resolvido, renovação ownership-aware, aposentadoria em quarentena, wiring de retorno, renovação CPU antes do financeiro e venda owner-side.

A revisão Codex final deve ocorrer sobre o head exato que contém este relatório. Findings materiais P0/P1/P2 impedem merge e exigem nova correção + nova rodada completa de CI. A classificação final APTO/NÃO APTO é registrada no PR depois dessa revisão, sem alterar novamente este relatório se não houver mudança de código.

## Riscos residuais

O risco residual é informacional: 509 sinais factuais não podem ser materializados como loans completos porque falta identidade canônica segura de owner/borrower no universo atual. Eles permanecem fail-closed e auditáveis. Uma futura expansão de identidades poderá resolvê-los sem alterar playerId, ratings ou atributos.

## Critério de conclusão

A Fase 10.4 só é considerada concluída quando o head final que contém este documento tiver:

1. Android/Bulk/Factual/Lower-Tier SUCCESS;
2. `phase_10_4_fc26_loans_audit.json.auditHead == PR head`;
3. Room V22 e APK artifacts presentes;
4. Codex final sem finding material não resolvido;
5. PR/base/head estáveis e sem conflito;
6. classificação **APTO PARA MERGE** seguida de merge automático com `expected_head_sha`.
