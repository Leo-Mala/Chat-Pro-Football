# Fase 10.4 — Empréstimos FC26 Seguros

## Estado da fase

- Repositório: `Leo-Mala/Chat-Pro-Football`
- Baseline oficial: `main@f9c01e53130fe46762d988600aa3ec18c3ee4e1d`
- Branch: `agent/phase-10-4-fc26-safe-loans`
- PR: `#54 — feat: Fase 10.4 — FC26 safe loans`
- Room: V22, sem mudança de schema e sem migration nova.
- PR #34 permanece fora do escopo e não foi usado como fonte factual.
- Head auditado no checkpoint factual FC26: `919a284cf5a6c78e67abec360d2a2d66d5bc4096`.
- Gate consolidado Android/benchmark/stress: pendente da execução final no head definitivo.

## Objetivo e fonte factual

A fase materializa somente empréstimos que podem ser resolvidos deterministicamente a partir do snapshot FC26 já integrado. Não há pesquisa externa, API-Football, atualização de elenco 2026/27 nem reaproveitamento de dados do PR #34.

O snapshot validado contém 18.405 jogadores. A auditoria do pipeline encontrou os seguintes metadados relevantes:

- `club_team_id`: identidade do clube atual na fonte;
- `club_name`: nome do clube atual;
- `club_position`: posição no clube atual;
- `club_loaned_from`: sinal textual de clube proprietário/origem do empréstimo;
- `contract_until_year`: informação de contrato principal.

O runtime FC26 não fornece data factual confiável de início ou término do empréstimo. `contract_until_year` não é tratado como `loan end`. Portanto:

- `startDate`: `NOT_AVAILABLE`;
- `endDate`: `NOT_AVAILABLE`;
- nenhuma data de empréstimo é inventada;
- a política temporal dos empréstimos FC26 sem data é `OPEN_ENDED_UNTIL_EXPLICIT_CAREER_EVENT`.

## Cobertura real do dataset

Artifact do workflow `FC26 Bulk Player Import #79`, no head `919a284cf5a6c78e67abec360d2a2d66d5bc4096`:

| Métrica | Resultado |
|---|---:|
| datasetPlayers | 18.405 |
| validatedPlayers | 18.405 |
| processedPlayers | 18.405 |
| importedPlayers | 18.405 |
| persistedPlayers | 60.885 |
| jogadores com sinal de empréstimo | 1.325 |
| resolvedLoans | 816 |
| rejectedLoans | 509 |
| ambiguousLoans | 1 |
| ownerNotFound | 60 |
| borrowerNotFound | 448 |
| selfLoansRejected | 0 |
| duplicateLoans | 0 |
| duplicatePlayerIds | 0 |
| duplicateTeamIds | 0 |
| overallMutated | 0 |
| potentialMutated | 0 |
| attributesMutated | 0 |

Distribuição de status medida:

- `RESOLVED`: 816;
- `BORROWER_NOT_FOUND`: 448;
- `OWNER_NOT_FOUND`: 60;
- `AMBIGUOUS_BORROWER`: 1.

Os 509 casos rejeitados não são promovidos por heurística. Eles permanecem fail-closed e auditáveis.

## Resolução de owner, borrower e aliases

O borrower é o clube atual do jogador no snapshot, desde que esse clube tenha associação canônica segura no universo do jogo. O owner é resolvido a partir de `club_loaned_from`.

A resolução reutiliza as identidades/aliases conservadores já suportados pelo projeto. São aceitos apenas matches determinísticos: identidade explícita auditada, identidade estável conhecida, nome/alias exato e normalização conservadora única. Similaridade textual/fuzzy pode aparecer apenas em diagnóstico e nunca promove um candidato a relação persistida.

Antes da persistência são exigidos:

- `playerId > 0`;
- `ownerTeamId > 0`;
- `borrowerTeamId > 0`;
- `ownerTeamId != borrowerTeamId`;
- borrower igual ao roster atual resolvido;
- nenhuma segunda relação ativa incompatível para o mesmo jogador;
- nenhuma referência ambígua ou inexistente.

## Fail-closed

A resolução classifica explicitamente falhas, em vez de usar um booleano genérico. O domínio suporta as categorias:

- `RESOLVED`;
- `AMBIGUOUS_OWNER`;
- `AMBIGUOUS_BORROWER`;
- `OWNER_NOT_FOUND`;
- `BORROWER_NOT_FOUND`;
- `SELF_LOAN`;
- `DUPLICATE_ACTIVE_LOAN`;
- `INVALID_REFERENCE`;
- `UNSUPPORTED_METADATA`.

Uma inconsistência de empréstimo não aborta a importação completa dos 18.405 jogadores. Apenas a relação insegura deixa de ser materializada.

## Owner x roster esportivo

Para uma relação `RESOLVED`:

- o jogador permanece uma única linha/identidade;
- `Player.teamId` representa o borrower e, portanto, o roster esportivo atual;
- `Player.originalTeamId` preserva o owner;
- `Player.isOnLoan = true`;
- `PlayerLoan` persiste a relação owner/borrower separadamente;
- o owner não recebe uma segunda cópia escalável do jogador.

O mercado passou a diferenciar roster atual de propriedade. O borrower pode abrir negociação para compra permanente do atleta que está em seu elenco por empréstimo, enquanto o owner não vê o próprio atleta emprestado para fora como alvo de aquisição.

## Idempotência

A relação FC26 usa identidade determinística de snapshot baseada no `playerId`, dentro de namespace próprio. Reprocessar o mesmo snapshot gera os mesmos players e os mesmos loans.

Os testes verificam que a mesma relação inserida novamente não cria duplicidade e que o novo-save seed é one-shot por `GameRepository`, impedindo reaproveitamento acidental do snapshot em operações posteriores.

## New Save, save/reopen e isolamento

O caminho real de novo save consome `players` e `loans` do mesmo plano factual. A seed temporária é associada ao `GameRepository` do slot e removida após consumo; não existe cache global de empréstimos compartilhado entre saves.

O teste de integração fecha e reabre o banco Room e verifica:

- mesmo `playerId`;
- mesmo owner;
- mesmo borrower;
- status `ACTIVE` preservado;
- roster no borrower;
- propriedade no owner;
- mesma contagem de empréstimos ativos.

Teste dedicado com dois bancos independentes confirma que encerrar um empréstimo em um save não altera o outro.

## Lifecycle e política temporal

Empréstimos FC26 sem data factual são persistidos com sentinel temporal explícito (`0`) e reconhecidos por `Fc26LoanPolicy.isUnknownEndSnapshotLoan`. Esse sentinel significa desconhecido, não “vence agora”.

Consequências:

- não há decremento semanal inventado;
- não há taxa semanal inventada;
- não há retorno automático por data inexistente;
- encerramento ocorre por evento explícito da carreira, como retorno comandado, transferência definitiva ou perda do vínculo principal;
- processamento repetido de retorno/encerramento é idempotente.

O integrity check semanal distingue esse sentinel de um empréstimo gameplay inválido com prazo esgotado.

## Retorno ao owner

`LoanLifecycleUseCase.returnToOwner` executa em transação Room única. Para contrato principal ainda ativo, move o roster de volta ao owner, limpa `originalTeamId`, `isOnLoan` e o estado de prazo do empréstimo, e marca `PlayerLoan` como concluído.

Se o contrato principal já expirou, o jogador é liberado como free agent em vez de ter o contrato recriado.

O retorno preserva `playerId`, overall/force, potential, atributos e evolução/histórico já persistidos.

## Transferência permanente durante empréstimo

Compra/venda definitiva é mantida dentro do `ProcessTransfersUseCase` para que:

- saldo;
- histórico financeiro;
- parcelas;
- contrato;
- roster;
- propriedade;
- fechamento do `PlayerLoan`

participem da mesma transação.

O fluxo valida novamente Player e PlayerLoan dentro da transação. Uma linha ativa stale não autoriza ownership: a operação falha fechada se `isOnLoan`, borrower, owner e `originalTeamId` divergirem.

O borrower não pode vender um jogador que não possui. O owner pode vender um jogador emprestado para fora mesmo estando no mínimo de 16 jogadores ativos, pois a venda não reduz seu roster esportivo atual.

Na conversão de empréstimo em compra definitiva, o wage-cap substitui o salário atual do loanee pelo novo salário contratual; não soma os dois salários. A mesma regra vale para compra à vista e parcelada.

Um teste com trigger SQLite força falha no histórico após mutações intermediárias e comprova rollback conjunto de saldo, Player, PlayerLoan e histórico.

## Contratos

Contrato principal e período de empréstimo continuam conceitos separados. A fase não usa `contract_until_year` como fim de empréstimo e não destrói o contrato do owner ao materializar a relação.

Não foi necessário redesign do sistema de contratos nem mudança de schema.

## Room

O schema permanece V22 porque já existiam:

- `Player.teamId`;
- `Player.originalTeamId`;
- `Player.isOnLoan`;
- tabela `player_loans` com `playerId`, `ownerTeamId`, `borrowerTeamId`, status e campos temporais.

Não há `fallbackToDestructiveMigration`, limpeza de banco ou recriação automática de saves. Nenhuma migration V22→V23 foi necessária.

O workflow FC26 tinha uma verificação histórica hardcoded em V21. Ela foi corrigida para ler a versão atual de `database.kt` e exigir o schema correspondente versionado, sem enfraquecer a barreira de imutabilidade das superfícies Room/identidade.

## Coroutines e transações

A fase não introduz `GlobalScope`, `runBlocking` em produção ou parsing do universo FC26 dentro de transações. O planejamento/resolução do snapshot ocorre antes da persistência; transações carregam apenas as mutações necessárias.

O lifecycle semanal não usa `getAllPlayers()` para loans. Os empréstimos ativos são agrupados por borrower e usam consultas de roster por `teamId`. O helper temporário com `IN (:ids)` foi removido, evitando limite de variáveis SQLite e alteração de DAO protegida.

## UI

Não houve redesign. O ajuste mínimo no mercado usa ownership para:

- permitir ao borrower negociar a compra permanente de um loanee em seu roster;
- impedir que o owner veja seu próprio jogador emprestado para fora como alvo de compra;
- não materializar ou exibir data de término inexistente.

A geração semanal de ofertas recebidas exclui atletas que o clube apenas recebeu por empréstimo.

## Testes específicos da Fase 10.4

Cobertura adicionada/atualizada inclui:

- jogador normal permanece normal;
- owner/borrower válido;
- owner inexistente;
- borrower inexistente;
- self-loan;
- alias canônico;
- alias ambíguo fail-closed;
- importação/relação determinística e idempotente;
- jogador único e roster somente no borrower;
- propriedade no owner;
- save/reopen;
- isolamento entre saves;
- retorno ao owner e retorno repetido;
- empréstimo FC26 open-ended sem countdown inventado;
- compra definitiva encerrando loan;
- cobrança única;
- borrower impedido de vender;
- owner autorizado a vender loanee sem reduzir roster ativo;
- conversão à vista/parcelada sem dupla contagem salarial;
- stale loan row rejeitado;
- rollback atômico forçado por trigger SQLite;
- integrity check aceitando o sentinel FC26 válido;
- visibilidade ownership-aware no mercado;
- invariantes 18.405/IDs/ratings/atributos.

## Review técnico / Codex

A primeira revisão Codex encontrou sete findings P2. Seis exigiam correção de código e receberam correções/regressões: wage-cap da conversão, roster-floor do owner, UI de compra pelo borrower, geração de ofertas para loanees, sentinel do integrity check e autorização por loan row stale. O sétimo finding (`IN (:ids)`) ficou obsoleto porque o helper/DAO temporário já havia sido removido e o caminho final usa rosters por borrower.

As threads serão encerradas somente após CI verde e nova revisão Codex no head definitivo.

## Gates consolidados

Checkpoint factual já comprovado:

- FC26 Bulk Player Import #79: SUCCESS;
- artifact `fc26-bulk-player-import-reports` no head `919a284cf5a6c78e67abec360d2a2d66d5bc4096`;
- FC26 18.405/18.405;
- duplicatePlayerIds = 0;
- duplicateTeamIds = 0;
- overallMutated = 0;
- potentialMutated = 0;
- attributesMutated = 0;
- save/reopen do snapshot: PASS;
- Room V22 verificado: PASS.

Ainda pendente neste checkpoint intermediário:

- suíte Android consolidada final;
- benchmark 60K final;
- stress 20 temporadas;
- stress 100 temporadas match-by-match;
- novo Codex no head definitivo;
- resolução final das review threads;
- comparação `auditHead == PR head` do head definitivo.

## Riscos residuais e itens não implementados por falta de dado factual

- Data factual de início do empréstimo: `NOT_AVAILABLE`.
- Data factual de término do empréstimo: `NOT_AVAILABLE`.
- Taxa semanal factual do empréstimo FC26: não materializada quando não existe na fonte.
- Opção de compra factual: não inventada.
- Casos com owner/borrower ausentes ou ambíguos: rejeitados, não inferidos.

O principal risco residual é informacional, não de identidade: os 509 sinais rejeitados só poderão ser materializados no futuro se o próprio snapshot/fonte canônica fornecer informação suficiente ou se a identidade de clube correspondente passar a existir de maneira auditada no universo do jogo.

## Conclusão provisória

A implementação preserva os invariantes FC26 e materializa apenas relações owner/borrower comprováveis. O estado deste documento ainda é provisório até a conclusão de Android CI, benchmark 60K, stress 20/100, Codex final e auditoria do head exato. A classificação APTO/NÃO APTO e o merge somente serão registrados após esses gates.
