# Fase 10.3 — UEFA / Competições Mundiais

## Escopo e baseline

- Repositório: `Leo-Mala/Chat-Pro-Football`
- Baseline oficial: `main@6167cac46836a3c9b8552c8615096fbf1aad96ef`
- Branch: `agent/phase-10-3-uefa-world-competitions`
- PR: `#53`
- Head final: não é hard-coded neste arquivo porque qualquer edição do próprio relatório altera o SHA; o head autoritativo é o head auditado registrado no PR e no `auditHead` do artifact de performance do CI final.
- Room: V22, sem alteração de schema nesta fase.

## Motivação

O baseline já possuía um motor UEFA substancial para Champions League, Europa League e Conference League, além de um Super Mundial de 32 clubes. A auditoria encontrou dois riscos principais: a seleção UEFA não consumia diretamente a classificação doméstica anterior no contrato tipado, e o Super Mundial ainda dependia de nomes hard-coded, fillers virtuais e promoção automática do clube controlado.

A Fase 10.3 consolida esses sistemas sem atualizar jogadores, clubes, ratings ou empréstimos FC26.

## Arquitetura encontrada e decisões

O fluxo preservado é:

`UI -> ViewModel -> UseCases/Coordinators -> Competition engines/domain -> GameRepository -> DAO/Room`

A geração de calendário continua em `GenerateCalendarUseCase`. A progressão esportiva permanece nos engines de competição, sem mover sorteios, standings ou mata-mata para ViewModel.

Componentes centrais:

- `UefaQualificationRules`: seleção tipada e exclusiva das três competições UEFA.
- `UefaCompetitionSystem`: league phase, standings, playoff, mata-mata e histórico.
- `SuperMundialQualificationRules`: field mundial esportivo e fail-closed.
- `WorldClubDrawEngine`: sorteio mundial determinístico com restrições de confederação/associação suportadas.
- `FifaClubWorldCupRules`: critérios de grupo suportados pelo domínio atual.
- `SuperMundialSystem`: fixtures, progressão, campeão e histórico.
- `GenerateCalendarUseCase`: integração com classificação doméstica da temporada anterior.

## UEFA — Champions League

O motor mantém 36 clubes na league phase, oito partidas por clube, quatro em casa e quatro fora, oito jornadas, top 8 direto às oitavas, posições 9–24 no playoff e 25–36 eliminadas. Playoff, oitavas, quartas e semifinais usam ida/volta; a final é jogo único.

O sorteio existente divide os clubes em quatro potes de nove e produz dois adversários por pote, um em casa e um fora, evitando adversário da mesma associação e limitando a dois adversários de uma mesma outra associação quando o field permite.

## UEFA — Europa League

A mesma estrutura de 36 clubes e oito jogos é preservada, com engine compartilhado e código concreto `UEFA_EL`. Progressão, persistência, desempates e histórico não são duplicados em um segundo motor.

## UEFA — Conference League

A Conference usa 36 clubes, seis partidas por clube, três em casa e três fora, seis potes de seis e um adversário por pote. O fluxo top 8 / 9–24 / 25–36 e o mata-mata reutilizam o engine UEFA configurável.

## Qualificação UEFA

A classificação doméstica persistida da temporada anterior agora pode ser passada diretamente ao `UefaQualificationRules`. Dentro de cada associação, posição e pontos do snapshot esportivo têm precedência; o `Team.rating` factual não é mutado nem usado como falso coeficiente UEFA.

O projeto ainda não persiste a access list UEFA completa, coeficientes de associação/clube ou todos os qualifying paths. Portanto a distribuição entre associações permanece uma aproximação determinística do jogo, explicitamente documentada, em vez de ser apresentada como a access list oficial real. Os três fields são exclusivos: um `teamId` não pode ocupar Champions, Europa e Conference simultaneamente.

Cada `QualificationSlot` preserva também a `destinationCompetition` concreta, evitando perda de identidade tipada durante a seleção.

## Sorteio, league phase e standings UEFA

O draw é determinístico e não depende do relógio. A league phase mantém fixtures únicas, nenhum clube contra si mesmo, adversários únicos, distribuição casa/fora e validação central pelo `FixtureScheduleValidator`.

Os critérios de standings suportados pelo domínio seguem, em ordem, pontos, saldo de gols, gols marcados, gols fora, vitórias, vitórias fora e os critérios coletivos dos adversários já calculáveis pelo save. Pontuação disciplinar e coeficiente de clube não são persistidos atualmente; `teamId` permanece somente como fallback determinístico depois dos critérios esportivos disponíveis.

## Mata-mata UEFA

O engine preserva playoff, oitavas, quartas e semifinais em ida/volta e final única. Empates agregados recebem decisão determinística compatível com o sistema existente. A gravação do campeão é idempotente: reprocessar a final não cria histórico duplicado.

## Calendário

As competições UEFA utilizam `MIDWEEK` e o calendário de 48 semanas existente. A geração continua passando pelo `FixtureScheduleValidator`, que impede colisões do mesmo clube no mesmo slot lógico. A liga doméstica detalhada permanece em `WEEKEND`.

## Save / reopen e isolamento de slots

Foram adicionados testes persistentes que fecham e reabrem um banco Room real de teste e comparam a assinatura dos fixtures UEFA e Mundial. Também existem bancos independentes para provar que temporadas/competições de um slot não aparecem em outro.

Nenhum cache global novo de competição foi introduzido.

## Season rollover

A seleção UEFA aceita snapshots domésticos de temporadas sucessivas e testes confirmam que uma mudança de classificação altera a prioridade esportiva da edição seguinte. O Mundial usa o ciclo quadrienal já existente (`2025`, `2029`, `2033`...). Estado esportivo é identificado por `season`, e histórico antigo não é sobrescrito pelo campeão novo.

## Mundial de Clubes

O Super Mundial mantém o formato estrutural de 32 clubes, oito grupos de quatro, três partidas por clube, top 2 de cada grupo e mata-mata de jogo único das oitavas à final.

Foram removidos do caminho de qualificação:

- lista hard-coded por nome de clube;
- criação de fillers virtuais para completar 32 vagas;
- inclusão automática do clube controlado pelo usuário.

O novo `SuperMundialQualificationRules` usa somente clubes reais persistidos e falha fechado se uma confederação não consegue preencher sua alocação estrutural. A 32ª vaga é do anfitrião determinado pela política de edição existente.

Como o save ainda não persiste ranking FIFA de quatro anos nem campeões continentais tipados de todas as seis confederações, a escolha dentro das quotas usa snapshots domésticos como aproximação esportiva auditável. Isso não é apresentado como ranking FIFA oficial.

## Sorteio mundial

`WorldClubDrawEngine` é seedable/determinístico por temporada e input. Ele impede clubes da mesma associação no mesmo grupo e limita, como princípio de sorteio, um clube por confederação em cada grupo, exceto UEFA, onde são admitidos até dois.

O projeto não possui os rankings continentais completos usados para formar os quatro potes reais da edição FIFA 2025. Portanto não inventa esses rankings; a seed determinística e as restrições geográficas são o subconjunto implementável com os dados persistidos.

## Desempate da fase de grupos mundial

`FifaClubWorldCupRules` aplica os critérios suportados pelo domínio: entre clubes empatados em pontos, mini-tabela dos confrontos diretos por pontos, saldo e gols, com reaplicação ao subconjunto que continuar empatado; em seguida, saldo e gols gerais. Pontuação disciplinar e sorteio FIFA não são persistidos, então `teamId` é usado somente como fallback técnico determinístico, explicitamente não factual.

## Histórico mundial

A final só grava campeão e vice se ambos os clubes existirem de fato no save. O histórico é idempotente por temporada e competição. O placeholder factual de artilheiro foi removido: quando o projeto não possui apuração real para esse torneio, os campos permanecem vazios/zero em vez de inventar um destaque.

## FIFA Intercontinental Cup anual

`CompetitionRulesRegistry` possui somente uma entrada `CATALOG_ONLY` para `WORLD_INTERCONTINENTAL`; não existe engine esportivo nem, no schema atual, campeões continentais tipados e persistidos para todas as seis confederações. Implementar participantes agora exigiria inventar classificados. Por isso a competição anual permanece explicitamente residual nesta fase, em conformidade com a regra de não fabricar dados. A expansão segura depende primeiro de vencedores continentais tipados para AFC, CAF, Concacaf e OFC.

## Room / transações

Nenhum novo estado persistente foi necessário. Room permanece V22 e `app/schemas/com.example.data.AppDatabase/22.json` continua sendo o schema oficial. Não foi introduzida migration, `fallbackToDestructiveMigration`, rede em transaction ou processamento de 60K jogadores dentro de locks de competição.

## Testes adicionados / fortalecidos

A fase inclui cobertura para:

- qualificação UEFA por posição doméstica anterior e exclusividade;
- `destinationCompetition` tipada;
- rollover de qualificação UEFA;
- lifecycle completo de Champions, Europa e Conference;
- field mundial apenas com clubes reais;
- fail-closed quando quotas mundiais não podem ser preenchidas;
- alteração do classificado mundial por snapshot esportivo;
- draw mundial determinístico e diversidade geográfica;
- 48 jogos / três partidas por clube na fase de grupos;
- ciclo mundial quadrienal;
- desempate FIFA por confronto direto antes do saldo geral;
- persistência após fechar/reabrir banco;
- isolamento entre bancos/save slots;
- final mundial decidida e histórico idempotente.

A suíte consolidada do CI continua responsável também pelos gates de carreira, saves, atomicidade semanal, evolução, migrations, FC26, 60K e stress.

## Performance 60K

A Fase 10.3 não adiciona loops por jogador aos engines de competição. Qualificação e sorteios operam sobre clubes, standings e fixtures. O benchmark consolidado de 60.885 jogadores permanece gate obrigatório; os valores finais e `auditHead` são publicados no artifact `global_main_audit_performance.json` do CI final para evitar registrar métricas de um SHA diferente neste relatório.

Os budgets existentes não foram alterados.

## FC26 invariant

Nenhum asset FC26 nem atributo factual de jogador faz parte do diff desta fase. O CI final deve reconfirmar 18.405/18.405 jogadores, IDs duplicados zero e mutações factuais zero.

## Stress 20 / 100 temporadas

Os testes consolidados de 20 e 100 temporadas permanecem gates obrigatórios no head final. Nenhum timeout, retry ou threshold foi relaxado nesta fase. O resultado autoritativo é o run final registrado no PR.

## Regras oficiais consultadas

Consulta realizada em 2026-08-21, priorizando fontes oficiais:

- UEFA — Regulamentos 2026/27, artigos de draw/league phase da Champions League, Europa League e Conference League: 36 clubes; Champions/Europa com oito adversários e 4H/4A; Conference com seis adversários e 3H/3A; top 8 direto às oitavas, 9–24 ao playoff e 25–36 eliminados; restrições de associação/potes conforme o regulamento.
- FIFA — procedimentos oficiais do sorteio do Mundial de Clubes 2025: 32 clubes, oito grupos; princípio de no máximo um clube por confederação em cada grupo, exceto UEFA com até dois, e proibição de clubes da mesma associação no mesmo grupo.
- FIFA — formato oficial do Mundial de Clubes 2025: oito grupos de quatro em turno único, top 2 às oitavas e mata-mata em jogo único até a final.
- FIFA — critérios publicados para desempate da fase de grupos 2025: confronto direto entre empatados antes de saldo/gols gerais; disciplina e sorteio permanecem fora do modelo persistido atual.
- FIFA Council — a FIFA Intercontinental Cup é anual desde 2024, mas sua implementação esportiva foi conscientemente adiada porque o save não possui todos os campeões continentais necessários de forma tipada.

## Riscos residuais

1. Access list e coeficientes UEFA completos não estão persistidos; a qualificação é aproximação determinística baseada em resultados domésticos.
2. Rankings FIFA/continentais de quatro anos e campeões tipados de todas as confederações não estão disponíveis; a qualificação mundial usa uma aproximação documentada por snapshots domésticos.
3. Disciplina/coeficiente para os últimos desempates UEFA e disciplina/sorteio para os últimos desempates FIFA não estão no schema; fallback determinístico é usado somente depois dos critérios suportados.
4. FIFA Intercontinental Cup anual continua `CATALOG_ONLY` até que os vencedores continentais necessários sejam dados reais persistidos.

## Fora de escopo

- empréstimos factuais FC26 e Fase 10.4;
- atualização de elencos, ratings, jogadores ou clubes;
- PR #34 congelado;
- remodelagem visual ampla.

## Conclusão

A implementação da Fase 10.3 consolida UEFA e Mundial sobre os dados realmente disponíveis, remove hardcodes/fillers factuais do Mundial e fortalece determinismo, persistência e invariantes. A classificação final `APTO PARA MERGE` somente poderá ser registrada no PR depois de CI completo no head exato, artifact auditado e review final sem finding material.
