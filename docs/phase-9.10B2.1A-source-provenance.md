# 9.10B2.1A — Proveniência dos dados domésticos europeus

Verificado em 2026-08-18.

Janela continental de referência: UEFA 2026/27.

Esta fase separa fatos esportivos (participantes/estrutura) dos atributos internos de gameplay.
`rating` não foi copiado de nenhuma base externa. Noruega e Suécia usam temporada doméstica por ano
civil e, portanto, são registradas como temporada `2026`, não `2026/27`.

## Política de fonte

A lista factual só recebe `VERIFIED_TOP_FLIGHT` quando pode ser fechada por fonte oficial/primária da
liga, federação ou organizador. Quando a página oficial da temporada seguinte ainda não expõe uma
lista única, a composição pode ser derivada exclusivamente de registros oficiais de:

- classificação final da temporada anterior;
- promoções/rebaixamentos confirmados;
- resultado oficial de playoff;
- registro oficial dos clubes na nova divisão.

Fonte secundária não é usada para preencher lacunas silenciosamente.

## Inglaterra — Premier League

Fonte primária: Premier League.

- https://www.premierleague.com/en/news/4673099/the-202627-premier-league-season-officially-starts/
- https://www.premierleague.com/en/news/4675097/all-380-fixtures-for-202627-premier-league-season

A Premier League confirmou 20 membros e as promoções de Coventry City, Ipswich Town e Hull City.

## Espanha — La Liga

Fonte primária: LALIGA.

- https://www.laliga.com/laliga-easports/clubes
- https://www.laliga.com/noticias/laliga-da-la-bienvenida-a-los-siete-clubes-ascendidos-para-la-temporada-2026-27

A página oficial de clubes 2026/27 é a source of truth da lista usada no baseline.

## Itália — Serie A

Fonte primária: Lega Serie A.

- https://en.legaseriea.it/serie-a/standings
- https://www.legaseriea.it/serie-a/news/aspettando-il-calendario-della-serie-a-enilive-2026-27

A standings oficial 2026/27 e a publicação do calendário confirmam as 20 equipes.

## Alemanha — Bundesliga

Fonte primária: Bundesliga/DFL.

- https://www.bundesliga.com/en/bundesliga/table
- https://www.bundesliga.com/en/bundesliga/news/2026-27-fixture-lists-now-available-38068

A tabela oficial 2026/27 contém os 18 clubes usados no baseline.

## França — Ligue 1

Fonte primária: Ligue 1/LFP.

- https://ligue1.com/en/articles/l1_article_5292-the-2026-27-ligue-1-mc-donald-s-calendar-is-released
- https://ligue1.com/fr/articles/l1_article_5293-les-dates-de-reprise-des-clubs-de-l1-2627

O calendário e a relação oficial de clubes confirmam os 18 participantes usados no baseline.

## Demais associações verificadas

| País | Temporada doméstica | Fonte primária usada | Observação de fechamento |
|---|---:|---|---|
| Portugal | 2026/27 | Liga Portugal | classificação final 2025/26 + promoções de Marítimo e Académico + permanência do Casa Pia no playoff |
| Países Baixos | 2026/27 | Eredivisie CV | página oficial de clubes da temporada |
| Bélgica | 2026/27 | Pro League | rodada inaugural oficial fecha os 18 participantes; formato novo com 18 clubes |
| Turquia | 2026/27 | TFF | calendário/rodada inaugural oficial fecha os 18 participantes |
| Escócia | 2026/27 | SPFL | seis jogos da rodada inaugural fecham os 12 participantes |
| Áustria | 2026/27 | Österreichische Fußball-Bundesliga | calendário/clubes oficiais; 12 participantes |
| Suíça | 2026/27 | Swiss Football League | rodada inaugural oficial fecha os 12 participantes |
| Dinamarca | 2026/27 | 3F Superliga / Divisionsforeningen | rodada inaugural oficial fecha os 12 participantes |
| Noruega | 2026 | NFF / Eliteserien | calendário oficial do ano civil fecha os 16 participantes |
| Suécia | 2026 | Allsvenskan / Svensk Elitfotboll | calendário oficial do ano civil fecha os 16 participantes |
| Polônia | 2026/27 | Ekstraklasa | tabela oficial da temporada fecha os 18 participantes |
| Tchéquia | 2026/27 | Chance Liga / LFA | tabela/calendário oficial fecha os 16 participantes |
| Croácia | 2026/27 | HNS / SuperSport HNL | tabela/sorteio oficial fecha os 10 participantes |
| Sérvia | 2026/27 | SuperLiga Srbije | tabela oficial confirma redução para 14 participantes |
| Grécia | 2026/27 | Super League Greece + Super League 2 | composição derivada apenas de registros oficiais de promoção/rebaixamento |

## Derivações que merecem auditoria explícita

### Portugal

A composição de 18 clubes foi fechada usando apenas registros da Liga Portugal: tabela final da
Liga Portugal Betclic 2025/26, promoções diretas de Marítimo e Académico e vitória do Casa Pia no
playoff de permanência. As páginas 2026/27 de Marítimo e Académico no escalão principal confirmam a
mudança de divisão.

### Grécia

A composição foi fechada pela combinação das listas oficiais da Super League Greece com os registros
da Super League 2: Iraklis e Kalamata foram campeões dos dois grupos de 2025/26; AEL e Panserraikos
foram registrados como novos membros da Super League 2 em julho de 2026. Essa combinação é tratada
como derivação de promoção/rebaixamento entre fontes primárias e permanece documentada para revisão.

## Estado de cobertura

As 20 associações UEFA atualmente modeladas no `CountryFootballRulesRegistry` possuem primeira
divisão factual fechada no `EuropeanDomesticBaseline2026_27`.

Isso NÃO significa que as 20 já estejam materializadas no seed do jogo. Neste checkpoint:

- Inglaterra e Espanha já usam clubes reais no `DefaultData`;
- as outras 18 associações já possuem lista factual e `teamId` reservado, mas ainda aguardam a
  integração segura ao seed;
- divisões inferiores continuam com cobertura parcial/procedural até suas próprias listas serem
  verificadas;
- elencos reais são uma fase separada, depois do gate de identidade canônica de jogador.
