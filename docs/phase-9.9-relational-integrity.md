# Fase 9.9 — Matriz de Integridade Relacional (Room V21)

Este documento registra as decisões relacionais da Fase 9.9. O objetivo é impedir corrupção estrutural no banco sem apagar dados históricos nem aplicar Foreign Keys onde o domínio ainda possui semântica incompatível.

## Modelo canônico de Free Agent

A partir do schema V21:

- `Player.teamId: Long?`;
- `teamId = null` significa **Free Agent**;
- o sentinela legado `teamId = 0` deixa de ser usado em `Player`;
- `Player.originalTeamId: Long?` também passa a usar `null` quando não existe clube proprietário legado.

Na migration V20→V21:

- `teamId = 0` → `NULL`;
- `teamId` não nulo que referencia clube existente é preservado;
- `teamId` órfão legado → `NULL`, preservando o jogador;
- `originalTeamId = 0` ou órfão → `NULL`.

## SAFE NOW

### Player.teamId → Team.id

Implementado com:

- `ON DELETE SET NULL`;
- `ON UPDATE NO ACTION`;
- índice composto de elenco preservado.

Justificativa: excluir um clube não deve apagar jogadores em cascata. O jogador permanece existente e passa ao estado canônico de Free Agent.

### Fixture.homeTeamId → Team.id

Implementado com:

- `ON DELETE NO ACTION`;
- `ON UPDATE NO ACTION`;
- índice em `homeTeamId`.

### Fixture.awayTeamId → Team.id

Implementado com:

- `ON DELETE NO ACTION`;
- `ON UPDATE NO ACTION`;
- índice em `awayTeamId`.

Justificativa: fixture persistido não pode sobreviver apontando para clube inexistente.

## NEEDS DOMAIN CHANGE

### Player.originalTeamId → Team.id

Não recebe FK nesta fase.

`originalTeamId` duplica informação também presente em `PlayerLoan.ownerTeamId` e é estado legado/derivável. Antes de protegê-lo por FK é necessário decidir formalmente qual estrutura é a fonte de verdade para empréstimos.

### PlayerLoan.playerId → Player.id

Não recebe FK nesta fase.

Aposentadoria atualmente remove a entidade `Player`, mas o registro de empréstimo concluído pode permanecer como histórico. Uma FK exigiria ou apagar histórico em cascata ou redesenhar a persistência histórica de empréstimos.

### PlayerLoan.ownerTeamId / borrowerTeamId → Team.id

Não recebem FK nesta fase.

A tabela de empréstimos mistura estado operacional ativo e histórico concluído. A futura normalização deve separar claramente essas responsabilidades antes de impor comportamento de exclusão.

### TransferInstallment.sellerTeamId → Team.id

Não recebe FK nesta fase.

Em compra de Free Agent, `sellerTeamId = 0` continua sendo um valor histórico que significa **sem clube vendedor**, não o vínculo atual do jogador.

## NOT APPROPRIATE NESTA MODELAGEM

### TransferInstallment.playerId → Player.id

Não é apropriado enquanto parcelas financeiras puderem continuar existindo depois de aposentadoria/remoção do jogador. O registro financeiro precisa sobreviver à entidade esportiva.

### HistoricalRecord e GlobalLeagueStanding → Team

São snapshots históricos por nome/temporada e não relações vivas que devam desaparecer ou bloquear exclusões futuras de `Team`.

## Clubes virtuais e fixtures

A partir da V21, todo `Fixture` persistido deve apontar para um `Team` persistido.

Regra de runtime:

- IDs gerados no namespace virtual `>= 200000` podem ser materializados como `Team` antes da gravação do fixture;
- IDs baixos desconhecidos são rejeitados em vez de serem convertidos silenciosamente em clubes inventados;
- registros materializados usam `country = "Mundial"` e existem para integridade referencial da competição;
- esses registros não são tratados como clubes domésticos ativos pelo hardening de elenco CPU nem pelo reparo de roster.

Regra de migration:

- fixtures V20 já persistidos que apontam para clube ausente são preservados;
- a migration materializa um `Team` legado com o mesmo ID antes de criar as FKs;
- fixtures com IDs não positivos são recusados pela migration, pois não existe semântica confiável que permita inventar referência sem risco de adulterar o save.

## Escritas em tabelas relacionadas

`INSERT OR REPLACE`/`OnConflictStrategy.REPLACE` não é usado para substituir `Team`, `Player` ou `Fixture` onde a semântica SQLite poderia provocar `DELETE + INSERT` e disparar efeitos de FK.

Os caminhos relacionais usam `@Upsert` ou `@Update` explícito.

## DatabaseIntegrityUseCase

Continua existindo para:

- diagnóstico;
- saves legados;
- recuperação controlada de estados semanticamente reparáveis.

A partir da V21 ele não é mais a primeira defesa para relações protegidas. O banco deve rejeitar novas referências inválidas antes que um reparo posterior seja necessário.

## Compatibilidade de saves

- versão Room anterior: V20;
- nova versão: V21;
- migration explícita: `MIGRATION_20_21`;
- nenhuma destructive migration;
- `MINIMUM_AUTOMATICALLY_MIGRATABLE_VERSION = 14`.

Não existe definição histórica confiável no repositório atual para reconstruir automaticamente schemas anteriores à V14. Saves anteriores devem falhar de modo seguro e permanecer fisicamente preservados.

Downgrade também permanece fail-closed: uma base criada por versão futura incompatível não deve ser apagada ou sobrescrita.

## Verificações de QA

A Fase 9.9 exige nos testes físicos:

- `PRAGMA foreign_key_check` → zero violações;
- `PRAGMA integrity_check` → `ok`;
- corrupção proposital de `Player.teamId` rejeitada;
- corrupção proposital de `Fixture.homeTeamId/awayTeamId` rejeitada;
- migration V20→V21 sem perda de linhas;
- Free Agent legado `0 → NULL`;
- empréstimos, transferências, aposentadoria, backup/restore, isolamento de save e continuidade de carreira preservados;
- stress de 20 e 100 temporadas sem regressão das Fases 9.6, 9.7 e 9.8.
