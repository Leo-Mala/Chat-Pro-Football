# Fase 10.1 — Performance 60K

Baseline da fase: `main@9d55a0b02dfcf1aa08986427b83e88a30c99f3a2`.

Branch: `agent/phase-10-1-performance-60k`  
PR: `#51 — perf: phase 10.1 60k performance hardening`

> O SHA final não é hard-coded neste arquivo para evitar uma auto-referência impossível (editar o
> relatório cria um novo SHA). A evidência autoritativa do head auditado é o campo `auditHead` de
> `reports/global_main_audit_performance.json`, materializado pelo CI a partir do head exato do PR,
> e a descrição final do PR registra o mesmo SHA antes do merge.

## Escala e baseline medidos

O harness global materializa uma carreira com **60.885 jogadores persistidos** (18.405 FC26 +
42.480 fallback), 2.524 clubes e 2.998 fixtures de calendário.

Baseline anterior da Fase 10.1 em GitHub Actions / Robolectric SDK 34:

- seed + persistência inicial: **6.763 ms**;
- reload completo de jogadores após reopen: **15.727 ms**;
- evolução mensal de 60.885 jogadores: **21.851 ms**;
- pico de heap observado: **435.997.464 bytes**.

A execução de referência já validada no CI #648 (`auditHead =
0eda239e218f48852d129ac6ad974d853431e0c4`) mediu:

- seed + persistência inicial: **3.640 ms**;
- reload completo após reopen: **15.423 ms**;
- evolução mensal de 60.885 jogadores: **18.554 ms**;
- banco persistido: **75.005.952 bytes**;
- pico de heap observado: **491.688.376 bytes**.

Em relação ao baseline, a persistência inicial caiu cerca de 46%, o reload permaneceu estável com
pequena melhora e a evolução mensal caiu cerca de 15%. O heap subiu no checkpoint de evolução, mas
permaneceu abaixo do budget explícito e tolerante à variação de runner (**762.995.562 bytes**).

Os budgets do gate são explícitos, derivados do baseline e intencionalmente folgados para não
transformar ruído de runner hospedado em falso negativo:

- persistência inicial: até 20.289 ms;
- reload completo: até 47.181 ms;
- evolução mensal: até 65.553 ms;
- pico de heap: até 762.995.562 bytes.

Esses limites impedem regressões materiais/several-minute sem serem um contrato rígido de latência
do GitHub Actions.

## Arquitetura final da evolução mensal

### Planejamento fora da transação semanal

`PlayerEvolutionUseCase.prepareMonthlyEvolution()` realiza a leitura e o cálculo CPU-heavy antes de
a transação de escrita semanal ser aberta. O fechamento semanal continua sendo a unidade atômica de
finanças, contratos, evolução, copas e avanço de calendário, mas não mantém o lock Room durante o
cálculo global de ~60 mil jogadores.

O `MonthlyEvolutionPlan` captura:

- `(season, week, playerTeamId)` esperados;
- snapshots leves de todos os inputs que influenciam evolução;
- tamanho exato do universo de jogadores;
- níveis de centro de treinamento relevantes;
- deltas de jogadores e histórico do período.

### Proteção fail-closed contra stale writes

`commitMonthlyEvolution()` valida o snapshot já dentro da transação antes de escrever. Um plano
standalone é rejeitado se temporada/semana/clube controlado, universo, input esportivo ou centro de
treinamento mudou durante o cálculo.

A persistência não reaplica entidades `Player` completas. Somente as colunas pertencentes à evolução
mensal são gravadas (`atributosJson`, `force`, `minutosJogados` e `evolucaoMensal`). Portanto um plano
preparado não pode restaurar contrato, salário, `teamId`, energia, moral, lesão, estado de
transferência, escalação ou outros campos externos à evolução.

### Drift legítimo de elenco durante o fechamento semanal

Contratos/empréstimos podem mover jogadores e a integridade CPU pode criar um pequeno número de
atletas emergenciais depois que o plano global foi preparado. O caminho semanal não recalcula o
universo inteiro sob lock. Ele faz uma projeção leve do universo atual e recalcula somente o
subconjunto cuja mudança de clube alterou o nível efetivo de centro de treinamento, mais jogadores
novos inseridos depois do planejamento.

Qualquer drift real de atributo, força, potencial, idade, posição, minutos, nota média ou foco de
treino continua sendo rejeitado fail-closed, provocando rollback do fechamento semanal em vez de
aplicar estado obsoleto.

### Stale standalone sem crash

A execução standalone expõe `MonthlyEvolutionExecutionOutcome`. Se o estado muda legitimamente
entre preparação e commit, o resultado é `committed = false`, nenhuma escrita mensal é feita e o
plano é descartado. O caminho compatível `executeMonthlyEvolution()` não transforma essa condição
esperada em `IllegalStateException`; callers que precisam distinguir um mês sem mudanças de um plano
stale usam `executeMonthlyEvolutionDetailed()`.

Há regressões específicas para mudança de `focoTreino` e upgrade do centro de treinamento entre
preparação e commit, verificando rejeição sem exception, preservação do estado novo, ausência de
histórico duplicado e ausência de escrita parcial.

## Persistência mensal por delta

O caminho legado fazia `@Update` completo em todos os ~60 mil jogadores apenas para zerar os
contadores mensais. A Fase 10.1 usa um action-set SQL atômico:

`UPDATE players SET minutosJogados = 0, evolucaoMensal = 0.0`

Depois do reset, somente jogadores com mudança real de atributos/força recebem update direcionado
das colunas da evolução. Isso elimina dezenas de milhares de full-row writes redundantes e reduz o
risco de stale overwrite.

## Idempotência e histórico

O retry do mesmo plano consulta fingerprints apenas para o período alvo. Se todo o conjunto planejado
já existe, retorna sucesso antes de tocar novamente em jogadores/contadores; sobreposição parcial é
tratada como estado inválido e falha fechada.

A consulta é apoiada pelo índice Room V22 `index_historico_evolucao_data`, evitando scan cumulativo
do histórico completo em saves longos.

## Room V22

A Fase 10.1 avança o banco de V21 para **V22** exclusivamente para indexar
`historico_evolucao.data`.

A migration `21 → 22` é não destrutiva e:

- cria `index_historico_evolucao_data`;
- preserva a contagem de linhas de histórico;
- executa `PRAGMA integrity_check`;
- permanece registrada em `AppDatabase`.

O snapshot gerado de V22 deve permanecer versionado em:

`app/schemas/com.example.data.AppDatabase/22.json`

A validação final do workflow exige que o snapshot esteja rastreado pelo Git, exista no head e seja
compatível com o schema exportado pelo build, evitando que KSP masque um snapshot ausente.

## FC26 e integridade factual

Nenhuma mudança desta fase altera os dados factuais FC26. O artifact de referência confirmou:

- dataset/processados: **18.405 / 18.405**;
- jogadores FC26 persistidos: **18.405**;
- duplicate player IDs: **0**;
- `overallMutated`: **0**;
- `potentialMutated`: **0**;
- `attributesMutated`: **0**.

O PR #34 permanece fora do escopo e congelado.

## Gates

O CI #648 já demonstrou, no head de referência citado acima, sucesso do job principal em:

- importer tests e FC26 bootstrap/validation;
- `assembleDebug`;
- suíte non-stress + migration/save safety;
- materialização independente dos relatórios FC26/Phase 9.14;
- benchmark 60K com budget;
- stress de 20 temporadas;
- stress de 100 temporadas match-by-match;
- export/validação do Room schema;
- geração do APK Debug.

O único failure do workflow #648 foi o job temporário de materialização do `22.json`, que executou
`assembleDebug` sem preparar `debug.keystore`. Esse job é apenas mecanismo de reparo do snapshot e
deve ser removido antes do merge depois que o schema gerado estiver versionado.

O head final precisa repetir todos os gates após: (1) tratamento seguro do stale standalone,
(2) versionamento definitivo do `22.json` e (3) remoção do workflow temporário. A classificação
`APTO PARA MERGE` só pode ser registrada no PR depois de CI final `completed/success`, revisão de
todos os threads e auditoria do head exato.

## Riscos residuais e escopo posterior

Fluxos globais fora do hot path mensal/semanal, como algumas recargas de UI ou reset amplo de
temporada, continuam candidatos a hardening arquitetural posterior. Eles não foram reescritos
cosmeticamente nesta fase porque o objetivo 10.1 é o caminho crítico mensurável de ~60K e a
preservação da atomicidade da Fase 10.0. Qualquer refatoração adicional pertence à fase arquitetural
seguinte se não for necessária para fechar um gate desta fase.

Status deste documento antes do gate exact-head final: **IMPLEMENTAÇÃO CONCLUÍDA; VALIDAÇÃO FINAL EM ANDAMENTO**.
