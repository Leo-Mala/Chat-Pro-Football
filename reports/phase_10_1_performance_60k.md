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

## Mudança 10.1-A

`PlayerEvolutionUseCase` foi dividido em duas etapas explícitas:

1. `prepareMonthlyEvolution()` — leitura + cálculo CPU-heavy fora da transação;
2. `commitMonthlyEvolution()` — validação fail-closed do snapshot `(season, week, playerTeamId)` e commit atômico de jogadores + histórico.

A API manual `executeMonthlyEvolution()` continua existindo e usa as duas etapas, preservando o contrato existente. O novo plano imutável permite que o fechamento semanal prepare a evolução antes de adquirir a transação externa e descarte o plano se o save se mover, sem sacrificar atomicidade.

## Próximos passos ainda obrigatórios desta fase

- integrar o plano pré-calculado ao fechamento semanal sem permitir replay/stale commit;
- reduzir materializações globais restantes nos fluxos de UI/restart/fixture validation;
- otimizar o reset de temporada para action-set SQL em vez de `getAllPlayers() + map + update`;
- reexecutar o harness 60K e registrar antes/depois;
- executar suite não-stress, CareerFunctionalFlow, SaveSlotIsolation, atomicidade, migrations, FC26, stress 20 e 100 temporadas;
- somente classificar como APTO PARA MERGE após todos os gates.

Room permanece V21. Nenhum dado factual FC26 foi alterado e o PR #34 permanece congelado.
