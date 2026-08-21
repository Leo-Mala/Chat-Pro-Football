# Fase 10.1 — Performance 60K

Baseline da fase: `main@9d55a0b02dfcf1aa08986427b83e88a30c99f3a2`.

## Baseline medido

O harness global mais recente materializa uma carreira com **60.885 jogadores persistidos** (18.405 FC26 + 42.480 fallback), 2.524 clubes e 2.998 fixtures de calendário.

Métricas do baseline auditado em GitHub Actions / Robolectric SDK 34:

- seed + persistência inicial: 6.763 ms;
- reload completo de jogadores após reopen: 15.727 ms;
- renovação CPU: 157 ms;
- tick de contratos: 411 ms;
- integridade de elencos CPU: 514 ms;
- geração de calendário: 45 ms;
- persistência de calendário: 167 ms;
- evolução mensal de 60.885 jogadores: **21.851 ms**;
- standings globais: 81 ms build + 45 ms persistência;
- pico de heap observado no checkpoint: **435.997.464 bytes**.

O hotspot dominante remanescente é a evolução mensal; o segundo problema estrutural é manter transações Room abertas durante cálculo CPU-heavy quando a evolução é disparada dentro do fechamento semanal atômico da Fase 10.0.

## Mudança 10.1-A — plano fail-closed

`PlayerEvolutionUseCase` foi dividido em duas etapas explícitas:

1. `prepareMonthlyEvolution()` — leitura + cálculo CPU-heavy fora da transação;
2. `commitMonthlyEvolution()` — validação fail-closed do snapshot `(season, week, playerTeamId)` e commit atômico de jogadores + histórico.

A API manual `executeMonthlyEvolution()` continua existindo e usa as duas etapas, preservando o contrato existente.

A integração desse plano ao fechamento semanal **ainda não foi feita**: o fechamento semanal altera contratos/elencos antes da evolução, então um plano calculado cedo demais poderia reintroduzir snapshots completos de Player e sobrescrever estado de contratos. A Fase 10.1 mantém esse ponto bloqueado até existir uma composição que preserve a atomicidade da 10.0 sem stale writes.

## Mudança 10.1-B — persistência mensal por delta

O caminho legado fazia `@Update` completo em todos os ~60 mil jogadores a cada evolução mensal, mesmo quando o único efeito era zerar `minutosJogados` e `evolucaoMensal`.

Foi criado `MonthlyEvolutionMaintenanceQueries.kt` com um único action set SQL:

`UPDATE players SET minutosJogados = 0, evolucaoMensal = 0.0`

Depois desse reset atômico, apenas jogadores com mudança real de atributos (`historyLogs`) ou força (`netChange != 0`) recebem `@Update` completo. Isso preserva o comportamento persistido, inclusive casos em que a força recalculada muda sem histórico de atributo, e evita dezenas de milhares de full-row writes desnecessários.

`PlayerEvolutionPlanAtomicityTest` agora também cobre o reset de contadores de jogador inalterado sem reescrever força, clube ou contrato.

## Estado de validação

- Room: **V21**, sem migration;
- FC26 factual: nenhuma alteração de IDs, overall/force inicial, potential, atributos, posição ou metadados;
- PR #34: permanece congelado;
- CI do head anterior foi `skipped` porque o PR está Draft sob a política econômica atual; nenhum resultado verde novo deve ser inferido disso.

## Próximos passos ainda obrigatórios desta fase

- compor a evolução mensal com o fechamento semanal sem permitir stale overwrite nem alongar indevidamente a transação;
- reduzir materializações globais restantes nos fluxos de UI/restart/fixture validation;
- otimizar o reset de temporada para action-set SQL em vez de `getAllPlayers() + map + update`;
- reexecutar o harness 60K e registrar antes/depois;
- executar suite não-stress, CareerFunctionalFlow, SaveSlotIsolation, atomicidade, migrations, FC26, stress 20 e 100 temporadas;
- somente classificar como APTO PARA MERGE após todos os gates.

Status atual: **FASE 10.1 EM ANDAMENTO — NÃO APTO PARA MERGE**.
