# Fase 9.10B2.1A — European Domestic Database

Baseline factual: **2026/27**  
Checkpoint verificado em: **2026-08-18**

## Objetivo

Transformar os participantes UEFA em consequência do futebol doméstico persistido no save. Esta
fatia não declara a Europa completa: ela cria a primeira barreira de dados reais e corrige a
identidade de clubes antes de expandir centenas de clubes e milhares de jogadores.

## Auditoria — estado encontrado

### P1 — identidade de clube dependia da posição na lista

**ANTES**

`GlobalFootballSystem.getGlobalId()` calculava o ID por `countryIndex * 200 + teamIndex + 1`.
Mover um clube entre divisões altera a ordem construída em `DefaultData.countriesMap`, podendo
alterar o ID retornado pelo mesmo nome.

**RISCO**

Promoção/rebaixamento, atualização de temporada-base ou simples reordenação de templates poderia
quebrar referências de clube, qualification sources e materialização de fixtures.

**ALTERAÇÃO**

Foi criado `StableTeamIdentityRegistry`. Clubes reais migrados recebem ID explícito. IDs já usados
por clubes pré-definidos de Inglaterra/Espanha foram preservados; novos clubes usam slots livres do
mesmo bloco legado. `GlobalFootballSystem` consulta esse registry antes do cálculo posicional e usa
o registry também na materialização reversa.

**DEPOIS**

Exemplos: Coventry continua `27` mesmo promovido à primeira divisão; West Ham continua `9` mesmo
rebaixado; Elche continua `228` ao subir; Girona continua `204` ao descer.

**TESTE**

`EuropeanDomesticBaselineTest` valida IDs, aliases, colisões, promoção/rebaixamento e materialização
reversa.

### P1 — primeira divisão europeia parcialmente fictícia

**ANTES**

Somente Inglaterra/Espanha tinham listas pré-definidas relevantes, e mesmo essas listas estavam
desatualizadas para 2026/27. Os demais países eram completados por nomes procedurais.

**RISCO**

A futura Access List UEFA poderia classificar clubes fictícios porque a origem doméstica ainda não
representava a temporada-base real.

**ALTERAÇÃO DESTE CHECKPOINT**

- catálogo explícito das 20 associações UEFA atualmente modeladas;
- listas 2026/27 verificadas para Premier League, La Liga, Serie A, Bundesliga e Ligue 1;
- Inglaterra e Espanha integradas de fato ao `EuropeDefaultData` na primeira divisão;
- cobertura dos demais países permanece marcada como `STRUCTURE_ONLY` até ser integrada;
- divisões inferiores de Inglaterra/Espanha continuam deliberadamente parciais, com fallback
  determinístico, e não são declaradas completas.

## Fontes factuais usadas neste checkpoint

Fontes primárias consultadas em 2026-08-18:

- Premier League — confirmação oficial dos 20 membros 2026/27 e promovidos Coventry City,
  Ipswich Town e Hull City; fixture release 2026/27.
- LALIGA — página oficial de clubes de LALIGA EA SPORTS 2026/27 e nota oficial dos promovidos.
- Lega Serie A — standings/lista oficial 2026/27 e apresentação do calendário.
- Bundesliga/DFL — página oficial de clubes Bundesliga 2026/27 e fixture release.
- Ligue 1/LFP — calendário oficial 2026/27 e relação dos 18 clubes.

A lista factual de participantes fica isolada de `rating`, que permanece atributo interno de
simulação e não é tratado como rating oficial/licenciado.

## Auditoria de simulação doméstica já existente

`GlobalLeagueSimulationUseCase` já possui uma arquitetura adequada para o objetivo UEFA:

- país do usuário pode usar fixtures detalhadas quando a temporada está estruturalmente completa;
- demais países são simulados de forma compacta e determinística em memória;
- somente a tabela final é persistida em `GlobalLeagueStanding`.

`SeasonTransitionUseCase` já usa essas tabelas para promoção/rebaixamento global e preserva `teamId`
ao alterar apenas o campo `division`.

Isso significa que não é necessário persistir milhares de fixtures CPU para obter vagas UEFA.

## Auditoria de jogadores

O modelo Room atual usa `Player.id` como PK auto-gerável, mas `DefaultData.generateRosterForTeam()`
preenche explicitamente `id = teamId * 1000 + slot` para o roster inicial gerado.

Esse ID não é recalculado durante uma transferência, portanto uma transferência normal preserva o
ID já persistido. Porém esse esquema não é uma identidade canônica adequada para uma base factual
de jogadores porque a identidade inicial continua acoplada ao clube/slot de seed.

Por segurança, esta fatia **não** introduz milhares de jogadores reais antes de definir e testar o
namespace de IDs reais. Isso fica como gate obrigatório da B2.1B; nenhuma migration Room foi criada
neste checkpoint.

## Persistência / Room

- versão Room: permanece V21;
- nenhuma entidade alterada;
- nenhum DAO alterado;
- nenhuma migration criada;
- nenhum schema deve mudar.

## Cobertura deste checkpoint

Integrado de fato ao seed inicial:

- Inglaterra — primeira divisão 2026/27 completa (20);
- Espanha — primeira divisão 2026/27 completa (20).

Transcrito em catálogo oficial, ainda não integrado ao `DefaultData`:

- Itália — 20;
- Alemanha — 18;
- França — 18.

Estrutura registrada, clubes ainda pendentes:

- Portugal;
- Países Baixos;
- Bélgica;
- Turquia;
- Escócia;
- Áustria;
- Suíça;
- Dinamarca;
- Noruega;
- Suécia;
- Polônia;
- Tchéquia;
- Croácia;
- Sérvia;
- Grécia.

## Próximos checkpoints obrigatórios

1. B2.1A-2: transcrever e integrar Serie A, Bundesliga e Ligue 1; depois completar as outras 15
   primeiras divisões e suas divisões inferiores representadas no jogo.
2. Corrigir `countryDivisionSizes` onde a temporada-base oficial divergir do valor legado.
3. B2.1B: definir identidade canônica de jogadores reais sem acoplamento permanente a `teamId` e
   importar elencos completos clube a clube.
4. B2.1C/D: copas nacionais + qualification sources domésticas.
5. B2.1E/F/G: coeficientes, Access List, qualificatórias e integração end-to-end UEFA.

Nenhum item acima pode ser marcado como concluído apenas porque existe fallback procedural.
