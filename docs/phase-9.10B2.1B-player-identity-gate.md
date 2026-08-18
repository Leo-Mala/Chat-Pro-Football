# Fase 9.10B2.1B — Player Identity & Squad Snapshot Gate

## Problema encontrado

O seed legado cria jogadores com `playerId = teamId * 1000 + slot`. O reparador de integridade usa
a mesma família de IDs, com busca collision-safe quando necessário. Transferências normais preservam
o ID já persistido, mas a identidade inicial continuava acoplada ao clube de origem.

Isso é insuficiente para uma base factual completa: uma atualização de elenco ou mudança de clube
não pode transformar a mesma pessoa em outro jogador.

## Identidade factual

Foi criado um namespace de IDs para jogadores factuais:

- chave: nome canônico + data de nascimento ISO + desambiguador opcional;
- `teamId` não participa da chave;
- normalização remove diferenças de acento/caixa/espaçamento;
- namespace começa em `100_000_000_000_000`, acima dos IDs procedurais atuais;
- nenhum campo Room foi adicionado.

`EuropeanRealPlayerTemplate` separa dados factuais dos atributos internos do jogo. Atributos de
força/finalização/passe/velocidade/defesa/visão, salário e valor de mercado são derivados de forma
determinística pelo próprio Pro Football e não representam ratings de terceiros.

## Snapshot factual de elenco

`EuropeanRealSquadSnapshot` registra:

- associação/país;
- clube;
- temporada doméstica;
- data exata de verificação;
- fontes oficiais consultadas;
- jogadores factuais com `stableId` independente do clube.

A cobertura é explícita:

- `PARTIAL_FACTUAL_SNAPSHOT`;
- `GAMEPLAY_READY_FACTUAL_SNAPSHOT`.

Um elenco só pode ser considerado pronto para gameplay quando possui ao menos 18 jogadores e
cobertura mínima por setor. O catálogo é imutável e rejeita o mesmo jogador factual em dois clubes
simultaneamente.

## Primeiro lote — Manchester United

Em 2026-08-18 foi transcrito o primeiro snapshot factual, a partir da página atual do Men's Team e
dos perfis individuais oficiais do Manchester United.

Cobertura do snapshot:

- 30 jogadores ativos;
- 4 goleiros;
- 10 defensores (`ZAG` + `LAT`);
- 9 meio-campistas (`MEI` + `VOL`);
- 7 atacantes;
- status `GAMEPLAY_READY_FACTUAL_SNAPSHOT`.

Altay Bayindir e Andre Onana aparecem na seção `On Loan` da página oficial em 2026-08-18 e não são
materializados como membros ativos neste snapshot. A futura camada factual de empréstimos deverá
preservar clube proprietário e clube tomador separadamente.

A janela de transferências inglesa ainda está aberta em 2026-08-18 e fecha em 2026-09-01. Portanto,
o lote é deliberadamente um snapshot datado, não um elenco declarado como final da temporada.

## Estado de cobertura

O baseline de primeira divisão contém 320 clubes reais nas 20 associações UEFA atualmente modeladas.
Após este primeiro lote:

- 1 clube possui snapshot factual `GAMEPLAY_READY`;
- 319 clubes continuam explicitamente ausentes do catálogo de elencos;
- ausência de snapshot não é mascarada como cobertura factual completa;
- o fallback procedural ainda não foi removido do seed.

## Sincronização com a base doméstica

Em 2026-08-18 esta branch foi sincronizada por merge commit normal com o head validado do PR #28:
`be57c82c028c45259dec296ad211b4b75d55616b`.

O PR #28 passou build, suíte core, stress 20/100 e verificação Room V21 no CI #381. A sincronização
não usou rebase, squash nem force-push e preserva os sete arquivos exclusivos desta fase sobre a
base doméstica validada.

## Comportamento esperado

1. Um jogador factual é materializado uma vez no novo save com seu ID canônico.
2. Mercado, empréstimo, free agency e transferência modificam `teamId`, nunca `id`.
3. Atualizações futuras de dados-base afetam novos saves, não reescrevem carreira já iniciada.
4. Um snapshot só entra na source of truth depois de ter data e fontes registradas.

## Próximos gates

Antes de importar os demais milhares de jogadores:

- validar o conjunto PR #28 + PR #29 no CI completo do head exato;
- validar o primeiro snapshot e colisões globais de `stableId`;
- transcrever os demais clubes em lotes auditáveis por país;
- criar a modelagem factual de jogadores emprestados antes de materializá-los no seed;
- integrar o catálogo ao novo-save seed sem sobrescrever saves existentes;
- preservar o procedural para regens, base e cobertura ainda ausente;
- medir tempo de criação de save e stress 20/100 após integração.

## Room

Permanece V21. Não há migration nem alteração de schema nesta fatia.
