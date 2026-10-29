# Fase 9.10B2.1 — UEFA Access List, Coeficientes e Qualificatórias

Base da fase: `main` pós-9.10B2 (`b8a1be1464a46fa3175ff927e969573508cfeb40`).

Branch: `agent/v3-uefa-access-coefficients-qualifying-b2-1`.

## Objetivo

Substituir gradualmente a projeção temporária de acesso da B2 por contratos tipados baseados na access list, coeficientes UEFA, classificação doméstica, titleholders, European Performance Spots e caminhos qualificatórios reais, sem voltar a usar `Team.rating` como falso coeficiente UEFA.

Esta primeira fatia é deliberadamente sem mudança Room. Ela cria contratos puros e testáveis antes de decidir a persistência de históricos de coeficiente.

## Fontes oficiais utilizadas

Regulamentos UEFA 2026/27, edição em vigor a partir de 1 de março de 2026:

- Champions League — Art. 3: entradas, titleholders e reequilíbrio de vagas;
- Champions League — Art. 13: Champions Path, League Path, play-offs e league phase;
- Champions League — Art. 15: transferências de eliminados UCL -> UEL/UECL;
- Europa League — Art. 3: entradas, Conference titleholder e reequilíbrio;
- Europa League — Art. 15: qualificatórias e transferências;
- Conference League — Art. 13/15: Main Path, Champions Path e play-offs;
- Annex A: access list 2026/27;
- Annex D.2: períodos de referência;
- Annex D.3: coeficiente de associação;
- Annex D.4: coeficiente de clube e piso de 20% da associação;
- Annex D.5: bônus por posição/etapa;
- Annex D.7: milésimos, sem arredondamento para cima, e shoot-out fora do cálculo.

A confirmação oficial da UEFA para a Champions 2026/27 fecha as 36 vagas da league phase da seguinte forma: titleholder UCL (1), titleholder UEL (1), Inglaterra 4, Itália 4, Espanha 4, Alemanha 4, França 3, Países Baixos 2, Portugal 1, Bélgica 1, Tchéquia 1, Turquia 1, dois European Performance Spots, cinco classificadas pelo Champions Path e duas pelo League Path. Os EPS 2026/27 pertencem a Inglaterra e Espanha.

## Contratos adicionados nesta fatia

### `UefaQualificationStructure.kt`

Cria identidades tipadas para:

- competição: UCL / UEL / UECL;
- caminho: Champions / League / Main;
- estágio: Q1 / Q2 / Q3 / Play-off / League Phase;
- origem e destino de transferências após eliminação.

Caminhos representados:

- UCL Champions Path: Q1 -> Q2 -> Q3 -> Play-off;
- UCL League Path: Q2 -> Q3 -> Play-off;
- UEL Main Path: Q1 -> Q2 -> Q3 -> Play-off;
- UEL Champions Path: Q3 -> Play-off;
- UECL Main Path: Q1 -> Q2 -> Q3 -> Play-off;
- UECL Champions Path: Q2 -> Q3 -> Play-off.

Também são modeladas as transferências oficiais de eliminados entre UCL, UEL e UECL. Quando o regulamento aponta uma fase que contém mais de um subcaminho e o domínio ainda não possui dados suficientes para decidir corretamente, o `path` permanece `null` em vez de ser inventado.

### `UefaCoefficientRules.kt`

Valores são armazenados como `Long` em milésimos de ponto.

Isso evita erro binário de ponto flutuante e permite truncar naturalmente no milésimo, conforme o Annex D.7.

Períodos de referência para 2026/27:

- access list / associação: 2020/21 a 2024/25;
- coeficiente de clube: 2021/22 a 2025/26.

Regras já codificadas:

- coeficiente de associação: vitória 2, empate 1; nas qualificatórias/play-offs, vitória 1 e empate 0,5;
- divisão pelo número de clubes participantes/clubes a que a associação tinha direito;
- soma de cinco temporadas;
- coeficiente de clube = maior entre soma própria de cinco temporadas e 20% do coeficiente de cinco temporadas da associação;
- pontos fixos de eliminação da Conference: Q1 1,000; Q2 1,500; Q3 2,000; Play-off 2,500;
- mínimo da league phase: UEL 3,000 e UECL 2,500;
- bônus de posição da league phase conforme Annex D.5;
- bônus de oitavas, quartas, semifinais e final;
- shoot-out não altera o resultado usado no coeficiente.

Importante: o próprio regulamento 2026/27 determina que D.4.1–D.4.3, ao construir o ranking inicial 2026/27, se referem aos pontos atribuídos em 2025/26. O motor não deve reutilizar cegamente esses pesos para temporadas futuras sem consultar o regulamento correspondente.

### `UefaAccessList2026_27.kt`

Primeira projeção segura da access list:

- 36 vagas totais da Champions league phase;
- vagas domésticas diretas por associação já confirmadas;
- dois titleholders;
- dois EPS (Inglaterra e Espanha);
- cinco vagas via Champions Path;
- duas via League Path.

País/associação desconhecido não recebe fallback.

### `UefaDomesticAccessPlanner`

Liga as futuras vagas UEFA aos dados já persistidos no save:

- `GlobalLeagueStanding` para posição em campeonato;
- `QualificationSource.LeaguePosition`;
- `QualificationSource.NationalCupWinner`;
- `QualificationSource.ContinentalChampion`.

Titleholders mantêm a associação nacional real do clube; não existe país virtual `UEFA`.

## Por que não existe Room V22 nesta fatia

`GlobalLeagueStanding` já guarda a posição da primeira divisão por temporada e foi criada com uso explícito de qualificação continental, portanto vagas domésticas podem ser derivadas sem migration.

Por outro lado, fixtures antigas são podadas pelo sistema de save. Consequentemente, não é seguro reconstruir um coeficiente UEFA completo de cinco temporadas retrospectivamente apenas consultando partidas antigas.

Antes de qualquer V22, a próxima fatia deve definir o mínimo modelo persistente necessário para snapshots de:

- coeficiente anual da associação;
- coeficiente anual do clube;
- ranking de cinco temporadas;
- provenance/regulation season usada no cálculo.

Só depois desse contrato estar fechado deve ser avaliada migration 21 -> 22. Não será criada tabela apenas para antecipar uma necessidade ainda não modelada.

## Próximas fatias da 9.10B2.1

1. Transcrever e testar a access list completa do Annex A para todas as associações presentes no dataset.
2. Implementar reequilíbrio por titleholders e European Performance Spots.
3. Introduzir ranking de associações e ranking de clubes como entrada do solver, sem `Team.rating`.
4. Implementar seeded/unseeded das qualificatórias e proibição de confronto entre clubes da mesma associação.
5. Gerar Q1/Q2/Q3/Play-off no calendário interno e transferir eliminados entre UCL -> UEL -> UECL.
6. Integrar vencedores das qualificatórias aos 36 clubes da league phase da B2.
7. Definir o gate de persistência de coeficientes e, somente se necessário, propor Room V22 com migration e schema.
8. Executar build, regressão core, testes específicos, stress 20/100 e Room schema antes de qualquer merge.

## Gate atual

Esta fatia deve permanecer em PR separado da B2. O merge da 9.10B2.1 não é automático e requer nova autorização após validação completa.
