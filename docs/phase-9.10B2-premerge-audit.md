# Fase 9.10B2 — Auditoria final pré-merge

Data da auditoria: 2026-08-18

Base auditada: `main` `1e435bfee00ae2d2c593640e3d1e53e96d770052`

Branch: `agent/v3-uefa-competition-system-b2`

## Escopo revisado

A revisão pré-merge cobriu o diff completo da B2 e os principais consumidores/contratos adjacentes: registry de competições, geração de calendário, `CupCompetitionSystem`, `UefaCompetitionSystem`, `UefaQualificationRules`, regras comuns de mata-mata, persistência de fixtures, validador de slots, UI de fases e testes de integração/compatibilidade.

Também foram comparadas as regras implementadas com os Regulamentos UEFA 2026/27 oficiais, em especial:

- Art. 16 — draw system da league phase;
- Art. 17 — match system da league phase;
- Art. 18 — critérios de desempate;
- Art. 19 — draw system da knockout phase;
- Art. 20/21 — mata-mata, agregado e decisão de empates;
- Annex B — estrutura do bracket.

## Achados bloqueadores corrigidos

### P1 — sequência de mandos podia gerar HHH/AAA

**Antes:** Champions League e Europa League orientavam as jornadas intermediárias 3–6 como um único bloco de quatro rodadas. A distribuição fechava em 4 jogos em casa e 4 fora, e respeitava 1H/1A nas duas primeiras e nas duas últimas jornadas, mas permitia que determinados clubes tivessem três mandos consecutivos iguais.

**Risco:** o Art. 17.02 determina, em princípio, no máximo dois jogos consecutivos em casa ou fora.

**Correção:** todas as competições UEFA passam a orientar mandos em blocos de duas jornadas. Cada bloco de duas jornadas forma um grafo de grau 2 orientado por circuitos de Euler, garantindo exatamente 1 jogo em casa e 1 fora em cada bloco.

**Depois:** CL/EL mantêm 4H/4A, Conference 3H/3A, primeiras e últimas duas jornadas continuam 1H/1A e nenhum clube recebe HHH ou AAA.

### P1 — bracket das quartas/semifinais não preservava os seeds 1–4/1–2

**Antes:** as oitavas eram persistidas em ordem 1,2,3,4,5,6,7,8. A progressão genérica agrupa caminhos dois a dois, portanto as quartas podiam virar caminhos 1×2, 3×4, 5×6, 7×8. Isso tornava impossível que todos os caminhos 1–4 mantivessem simultaneamente a condição de cabeça de chave para o jogo de volta das quartas.

**Risco:** o Art. 19.04 exige que os caminhos dos classificados 1–4 sejam seeded nas quartas e que os caminhos 1–2 também sejam seeded nas semifinais; quem elimina um seed herda essa posição no bracket.

**Correção:** a ordem determinística dos caminhos das oitavas passou a ser `1,8,4,5,2,7,3,6`, dentro das posições permitidas pelo bracket UEFA. Isso produz quartas `1/8`, `4/5`, `2/7`, `3/6` e semifinais entre os caminhos `1/4` e `2/3`.

**Depois:** caminhos 1–4 (ou seus vencedores) recebem o jogo de volta em casa nas quartas; caminhos 1–2 (ou seus vencedores) recebem a volta nas semifinais; 1 e 2 permanecem em lados opostos até uma eventual final.

## Calendário e concorrência de fixtures

- Liga detalhada permanece no slot `WEEKEND`.
- UEFA permanece no slot `MIDWEEK`.
- Copa nacional termina na semana 27.
- Knockout UEFA começa na semana 28.
- Final UEFA ocorre na semana 40.
- O calendário possui 48 semanas.
- `FixtureScheduleValidator` proíbe um clube em duas partidas do mesmo slot/semana e impede fixtures duplicados.

Não foi encontrada colisão estrutural de calendário introduzida pela B2.

## Saves e compatibilidade

- Saves antigos com apenas `CONTINENTAL_T1/T2/T3` continuam no processador legado.
- Novas temporadas UEFA usam `UEFA_CL`, `UEFA_EL`, `UEFA_ECL`.
- Os códigos legados continuam identidades diferentes das novas identidades UEFA.
- Nenhuma alteração Room ou migration foi necessária para a B2.

Risco residual baixo: uma temporada artificial contendo simultaneamente fixtures UEFA concretos e fixtures continentais legados não é um estado produzido pelo gerador normal. Esse cenário misto permanece dívida de compatibilidade defensiva, não um caminho normal de upgrade: uma temporada antiga continua integralmente legada e uma temporada nova é integralmente concreta.

## Pontos deliberadamente fora da B2 e destinados à 9.10B2.1

A auditoria confirmou que os itens abaixo não são regressões da B2; são lacunas já declaradas do modelo de acesso e serão tratados na fase seguinte:

1. coefficient ranking UEFA por clube;
2. ranking de associações e access list anual;
3. vagas de titleholders;
4. European Performance Spots;
5. Champions Path e League Path;
6. fases preliminares e play-offs de acesso;
7. transferências entre UCL -> UEL -> UECL nas eliminatórias;
8. disciplinary points e club coefficient como critérios 9 e 10 de desempate;
9. solver de sorteio baseado nos coeficientes e restrições de associação, substituindo a projeção estática de potes do B2.

A projeção B2 atual continua deliberadamente independente de `Team.rating`, para não tratar rating esportivo do jogo como falso coeficiente UEFA.

## Dívida arquitetural não bloqueadora

A final é persistida como `homeTeamId`/`awayTeamId`, pois o domínio atual não possui flag de campo neutro. Isso é uma limitação compartilhada por finais de competições já existentes e não exige mudança Room na B2. Deve ser tratado em uma fase transversal de neutral venue/simulation semantics.

A ordem dos caminhos do mata-mata é preservada pela ordem de inserção/IDs dos fixtures da rodada. Para a B2 isso é determinístico e coberto por integração, mas uma futura modelagem explícita de `bracketPath` seria arquiteturalmente mais forte.

## Gate de merge

Nenhum P0 foi encontrado.

Os dois P1 encontrados durante a auditoria foram corrigidos antes do merge e receberam testes específicos em `UefaPremergeAuditTest`.

O merge só deve ocorrer após o novo head passar novamente por:

- `assembleDebug`;
- suíte core/migration-save safety;
- stress 20 temporadas;
- stress 100 temporadas;
- validação do schema Room V21.
