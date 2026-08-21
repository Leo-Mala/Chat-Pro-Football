# Fase 10.3 — UEFA / Competições Mundiais

## Baseline e escopo

- Repositório: `Leo-Mala/Chat-Pro-Football`
- Baseline: `main@6167cac46836a3c9b8552c8615096fbf1aad96ef`
- Branch: `agent/phase-10-3-uefa-world-competitions`
- PR: `#53`
- Head final: não é hard-coded neste arquivo porque editar o próprio relatório altera o SHA; o head autoritativo é o head auditado do PR e o `auditHead` do artifact do CI final.
- Room: V22, sem mudança de schema.

A fase consolida UEFA Champions League, Europa League, Conference League e o Super Mundial já existentes, sem alterar jogadores, ratings, elencos, empréstimos FC26 ou o PR #34 congelado.

## Auditoria inicial

O baseline já possuía um engine UEFA substancial e um Super Mundial de 32 clubes. Os principais riscos encontrados foram: qualificação UEFA que não consumia diretamente a classificação doméstica anterior no contrato tipado; seleção mundial por nomes hard-coded/fillers virtuais; promoção automática do clube controlado; ausência de fail-closed em sorteios impossíveis; identidade de associação não canônica em aliases; e lacunas de persistência/regressão para rollover e save/reopen.

## Arquitetura resultante

Fluxo preservado:

`UI -> ViewModel -> UseCases/Coordinators -> Competition engines/domain -> GameRepository -> DAO/Room`

Componentes principais:

- `UefaQualificationRules`: fields tipados e exclusivos das três competições UEFA.
- `UefaCompetitionSystem`: league phase, standings, playoff, mata-mata e histórico.
- `SuperMundialQualificationRules`: field mundial esportivo, elegibilidade e tratamento explícito de data gaps.
- `WorldClubDrawEngine`: sorteio mundial determinístico e fail-closed.
- `FifaClubWorldCupRules`: desempates da fase de grupos suportados pelo domínio.
- `SuperMundialSystem`: fixtures, progressão, campeão e histórico.
- `GenerateCalendarUseCase`: integração com standings da temporada anterior.

Não foi movida lógica de competição para ViewModel e nenhum loop de 60K jogadores foi introduzido no caminho de torneios.

## UEFA Champions League

Mantém 36 clubes, oito partidas por clube (4 casa/4 fora), oito jornadas, top 8 direto às oitavas, posições 9–24 no playoff e 25–36 eliminadas. Playoff, oitavas, quartas e semifinais são ida/volta; a final é jogo único.

## UEFA Europa League

Mantém 36 clubes e oito partidas de league phase por clube, usando o mesmo engine configurável, com código concreto `UEFA_EL` e histórico independente.

## UEFA Conference League

Mantém 36 clubes, seis partidas por clube (3 casa/3 fora), seis potes e um adversário por pote, reutilizando a mesma progressão top 8 / 9–24 / 25–36 e mata-mata configurável.

## Qualificação UEFA

`UefaQualificationRules` recebe `GlobalLeagueStanding` da temporada anterior. Apenas clubes que permanecem na primeira divisão atual são elegíveis; um rebaixado não pode consumir uma vaga com o snapshot antigo. Dentro da aproximação suportada pelo save, posição/resultado esportivo têm precedência e país não recebe preferência por ordem alfabética.

Os três fields são exclusivos por `teamId`. Cada `QualificationSlot` mantém `destinationCompetition` concreta para Champions, Europa ou Conference. `Team.rating` factual não é alterado nem usado como falso coeficiente UEFA.

O jogo ainda não persiste a access list UEFA completa, coeficientes de associação/clube e todos os qualifying paths. Portanto a distribuição entre associações é uma aproximação determinística documentada, não uma afirmação de reproduzir integralmente a access list oficial.

## Sorteio, league phase e standings UEFA

O draw é determinístico e independe do relógio. Fixtures são únicas, nenhum clube enfrenta a si próprio, e a validação passa por `FixtureScheduleValidator`.

Champions/Europa usam quatro potes de nove e dois adversários por pote; Conference usa seis potes de seis e um adversário por pote. As restrições de associação suportadas pelo engine são preservadas.

Os critérios de standings implementáveis pelo domínio são pontos, saldo, gols marcados, gols fora, vitórias, vitórias fora e critérios coletivos dos adversários. Pontuação disciplinar e coeficiente de clube não são persistidos; `teamId` é somente fallback determinístico depois dos critérios esportivos disponíveis.

## Mata-mata UEFA

Playoff, oitavas, quartas e semifinais usam duas pernas; final é jogo único. Empates agregados usam a decisão determinística já consolidada. O histórico do campeão é idempotente: reprocessar a final não duplica registros.

## Calendário

Competições internacionais usam `MIDWEEK`; a liga doméstica detalhada permanece em `WEEKEND`. O calendário de 48 semanas continua validado centralmente e não recebe duas fixtures do mesmo clube no mesmo slot lógico.

## Save/reopen e isolamento de slots

Testes persistentes fecham e reabrem banco Room e comparam a assinatura de fixtures UEFA e Mundial. Bancos independentes demonstram que temporadas, fixtures e competições de um slot não aparecem em outro. Nenhum cache global novo de competição foi introduzido.

## Season rollover

A qualificação UEFA responde a snapshots domésticos sucessivos, inclusive impedindo rebaixados de herdarem posição antiga. O Mundial continua no ciclo quadrienal da política existente (`2025`, `2029`, `2033`...). Teste dedicado comprova que participantes mundiais podem mudar entre 2029 e 2033 conforme o snapshot esportivo.

## Mundial de Clubes

Mantém 32 clubes, oito grupos de quatro, três jogos por clube, top 2 por grupo e mata-mata de jogo único das oitavas à final.

Foram removidos do caminho de qualificação:

- lista hard-coded de clubes por nome;
- fillers virtuais para completar o field;
- inclusão automática do clube controlado.

`SuperMundialQualificationRules` aceita somente clubes positivos de associações nacionais tipadas por `CountryFootballRulesRegistry.isContinentalCompetitionEligible`. Entradas agregadas legadas como `África` e `Oceania` não podem receber vaga nem sede.

### Data gap OFC

O dataset factual atual não possui associação nacional OFC persistida no registry. Exigir uma vaga OFC estrita faria toda edição normal do Mundial desaparecer; por outro lado, criar Auckland City/um filler ou transformar o agregado `Oceania` em associação nacional violaria a regra da fase de não inventar dados factuais.

Por isso existe um fallback explícito e auditável somente para esse data gap: quando não existe nenhuma associação nacional OFC real, o slot regular OFC é convertido em uma vaga esportiva suplementar entre clubes nacionais reais já persistidos. O field marca isso com `usedOfcDataGapFallback=true`. Nenhum clube virtual/legado é criado. Falta de capacidade em uma confederação suportada pelo dataset (por exemplo CAF) continua falhando fechado.

Esse fallback é uma aproximação do jogo, não a regra oficial FIFA, e deve ser removido quando existir um participante OFC factual/qualificação continental tipada no dataset.

## Seleção mundial e ordem esportiva

Dentro de cada confederação os candidatos são organizados por slot da associação e evidência esportiva persistida, sem favorecer associações por ordem alfabética. O host é escolhido do mesmo universo de associações nacionais elegíveis e a final resolve novamente a sede usando exatamente esse filtro, evitando divergência em saves migrados com países desconhecidos.

Como não existem ranking FIFA/continental de quatro anos nem campeões continentais tipados de todas as confederações, snapshots domésticos são usados como aproximação esportiva auditável; não são apresentados como ranking oficial FIFA.

## Sorteio mundial

`WorldClubDrawEngine` é determinístico por temporada/input. Compara associações pelo nome canônico do registry, inclusive aliases. Proíbe duas equipes da mesma associação no grupo e limita confederações conforme o princípio suportado: até dois clubes UEFA por grupo e até um das demais.

Fields impossíveis (por exemplo mais de oito clubes da mesma associação) retornam sorteio vazio em vez de lançar exceção e abortar a criação da temporada. `SuperMundialSystem` também valida 8 grupos de 4 antes de indexá-los.

O projeto não possui os rankings completos usados para os potes reais FIFA 2025; portanto não inventa esses rankings.

## Desempates do Mundial

`FifaClubWorldCupRules` exige todos os seis jogos do grupo jogados e aplica, entre empatados em pontos, mini-tabela de confrontos diretos por pontos, saldo e gols, reaplicando ao subconjunto que permanecer empatado; depois usa saldo e gols gerais. Disciplina e sorteio FIFA não estão persistidos, então `teamId` é somente último fallback técnico determinístico e não é descrito como regra factual.

## Progressão e histórico mundial

Antes das oitavas, o sistema exige exatamente 48 jogos de grupos, oito grupos válidos e quatro clubes com três partidas em cada grupo. O mata-mata exige exatamente 8/4/2/1 fixtures nas fases sucessivas e vencedores únicos.

A final empatada recebe decisão compatível com `CompetitionRules`. Campeão e vice precisam existir no banco; nenhum nome virtual é materializado. O placeholder de artilheiro foi removido e permanece vazio/zero quando não há apuração real. O histórico é idempotente por temporada.

## FIFA Intercontinental Cup anual

`CompetitionRulesRegistry` possui `WORLD_INTERCONTINENTAL` apenas como `CATALOG_ONLY`. Não existe engine esportivo nem campeões continentais tipados/persistidos para todas as confederações. Implementá-la agora exigiria inventar participantes; por isso permanece risco/item residual permitido pelo escopo. A implementação segura depende primeiro de vencedores continentais tipados para AFC, CAF, Concacaf e OFC.

## Room e transações

Nenhum novo estado persistente foi necessário. Room permanece V22 e `app/schemas/com.example.data.AppDatabase/22.json` continua oficial. Não houve migration nova, `fallbackToDestructiveMigration`, rede em transaction ou trabalho 60K dentro de locks de competição.

## Testes adicionados/fortalecidos

Cobertura desta fase inclui:

- classificação UEFA por resultado doméstico anterior;
- exclusividade dos três fields e destino tipado;
- clube rebaixado não herdando vaga por snapshot antigo;
- lifecycle completo de Champions, Europa e Conference;
- rollover UEFA;
- field mundial com somente associações nacionais reais;
- agregados legados excluídos;
- fallback OFC explicitamente marcado e sem clube inventado;
- fail-closed para falta de capacidade em confederação suportada;
- ordem mundial por mérito, não por país alfabético;
- draw mundial determinístico e associação canônica;
- draw impossível retornando vazio sem crash;
- 48 fixtures e três jogos por clube nos grupos;
- desempate FIFA por confronto direto antes do saldo geral;
- ciclo quadrienal e rotação de participantes;
- save/reopen;
- isolamento de slots;
- final decidida e histórico idempotente.

A suíte consolidada continua cobrindo carreira, save atomicity, weekly atomicity, evolução, migrations, FC26, benchmark 60K e stress 20/100.

## Performance 60K

A Fase 10.3 opera sobre clubes, standings e fixtures, não sobre todos os jogadores. O benchmark de 60.885 jogadores permanece gate obrigatório. Métricas finais e `auditHead` são lidos do artifact `global_main_audit_performance.json` do CI final; budgets não foram alterados.

## FC26 invariant

Nenhum asset FC26, jogador ou atributo factual faz parte do escopo. O CI final deve reconfirmar 18.405/18.405, IDs duplicados zero e mutações de overall/potential/attributes zero.

## Stress 20 / 100

Os testes de 20 e 100 temporadas continuam gates obrigatórios. Nenhum timeout, retry ou threshold foi relaxado. O resultado autoritativo será o run final no SHA auditado.

## Regras oficiais consultadas

Consulta em 2026-08-21, priorizando fontes UEFA/FIFA oficiais:

- UEFA: formatos 2026/27 das três competições — 36 clubes; Champions/Europa com 8 jogos e Conference com 6; top 8 direto, 9–24 playoff, 25–36 eliminados; regras de potes/associação conforme os regulamentos.
- FIFA: Club World Cup 2025 — 32 clubes, oito grupos de quatro, turno único, top 2 às oitavas e mata-mata até a final; princípio de máximo um clube por confederação no grupo salvo UEFA com até dois e restrição de mesma associação.
- FIFA: desempates de grupo — confronto direto antes de saldo/gols gerais, seguido de disciplina e sorteio quando necessário; os dois últimos não existem no schema atual.
- FIFA Council: Intercontinental Cup anual desde 2024; implementação esportiva não foi inventada sem campeões continentais tipados.

## Riscos residuais

1. Access list e coeficientes UEFA completos não estão persistidos; a qualificação é aproximação determinística baseada em resultados domésticos.
2. Ranking mundial/continental plurianual e campeões continentais de todas as confederações não estão disponíveis; a qualificação mundial usa snapshots domésticos como aproximação.
3. Não existe associação nacional OFC factual no dataset atual; um fallback suplementar explícito evita inventar clube e mantém o Mundial funcional.
4. Disciplina/coeficiente UEFA e disciplina/sorteio FIFA não estão no schema; fallback determinístico só ocorre após os critérios suportados.
5. FIFA Intercontinental Cup anual permanece `CATALOG_ONLY` até existirem vencedores continentais tipados suficientes.

## Fora de escopo

- Fase 10.4 / empréstimos FC26;
- atualização de jogadores, clubes, elencos ou ratings;
- PR #34;
- remodelagem visual ampla.

## Conclusão

A Fase 10.3 consolida UEFA e Mundial sobre os dados realmente disponíveis, elimina hardcodes/fillers factuais do Mundial, fortalece qualificação, determinismo, persistência, rollover e fail-closed. `APTO PARA MERGE` só será registrado depois de CI completo no head exato, artifacts auditados e review final sem finding material.
