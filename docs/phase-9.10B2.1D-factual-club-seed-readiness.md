# Fase 9.10B2.1D — Factual Club Seed Readiness Gate

## Problema

A Fase B2.1A já reserva uma identidade determinística para os 320 clubes das primeiras divisões das 20 associações UEFA modeladas. Isso, por si só, não significa que os 320 clubes já possam substituir os placeholders do seed.

Para persistir um clube factual com segurança, o jogo também precisa conhecer os metadados mínimos usados pelo domínio (`name`, `city`, `state`, `division`, `rating`, `stadium`) e provar que o ID que `GlobalFootballSystem` atribui àquele template é exatamente o ID canônico do `StableTeamIdentityRegistry`.

Promover um placeholder procedural apenas porque seu nome se parece com um clube real poderia fazer um save antigo herdar a identidade de outra entidade. Inventar cidade/estádio/rating apenas para completar a cobertura produziria uma base aparentemente factual, mas tecnicamente falsa.

## Status

`EuropeanFactualClubSeedReadiness` classifica cada clube factual em um dos estados:

- `READY`: existe template explícito de primeira divisão, metadados mínimos válidos e ID global canônico;
- `MISSING_EXPLICIT_TEMPLATE`: identidade factual existe, mas o seed ainda não possui template explícito;
- `NON_TOP_FLIGHT_TEMPLATE`: existe template, mas ele não representa a divisão 1 atual;
- `INVALID_TEMPLATE_METADATA`: template não possui os metadados mínimos válidos;
- `GLOBAL_ID_MISMATCH`: o template existe, mas o ID resolvido pelo sistema não coincide com a identidade factual reservada.

Nenhum status diferente de `READY` pode ser materializado automaticamente como clube factual.

## Cobertura esperada neste checkpoint

Baseline total: 320 clubes.

- Inglaterra: 20 `READY`;
- Espanha: 20 `READY`;
- demais 18 associações: 280 `MISSING_EXPLICIT_TEMPLATE`;
- nenhum `NON_TOP_FLIGHT_TEMPLATE` esperado;
- nenhum `INVALID_TEMPLATE_METADATA` esperado;
- nenhum `GLOBAL_ID_MISMATCH` esperado.

Esse estado é intencional: `EuropeDefaultData` já contém os 20 participantes factuais da Premier League 2026/27 e os 20 da La Liga 2026/27. As outras ligas possuem nomes factuais no baseline/registry, mas o `DefaultData` central ainda gera seus clubes proceduralmente.

## Caso de controle: Manchester United e Trabzonspor

Manchester United é `READY`: o template explícito existe e resolve para `teamId = 5`.

Trabzonspor possui identidade factual estável, mas ainda é `MISSING_EXPLICIT_TEMPLATE`. Por isso o empréstimo factual de Andre Onana não deve ser forçado para um placeholder turco. O `EuropeanFactualSeedPlanner` deve continuar retornando esse loan como bloqueado até o Trabzonspor factual ser materializado com seu ID canônico.

## Caminho para elevar a cobertura

Para cada associação ainda pendente:

1. transcrever templates factuais da primeira divisão com fonte auditável;
2. preservar os IDs do `StableTeamIdentityRegistry`;
3. preencher apenas metadados confirmados ou explicitamente internos de gameplay;
4. rodar o readiness gate e exigir zero `GLOBAL_ID_MISMATCH`;
5. somente então permitir que o novo-save seed use esses clubes como entidades factuais;
6. não reaplicar a base factual sobre saves já existentes.

## Room

Nenhuma alteração. O gate é puro e somente leitura; Room permanece V21.
