# Fase 10.5 — Auditoria pós-10.4 e Roadmap Técnico Final

## 1. Baseline oficial

- Repositório: `Leo-Mala/Chat-Pro-Football`
- Baseline pós-Fase 10.4: `main@dde17e82567d81d4477fedd4d9f4e4012ac9b3fc`
- Branch de trabalho: `agent/phase-10-5`
- Room: V22
- PR #54: mergeado
- PR #34: permanece congelado em Draft no head `c421dafee0ecdd2ea23f7d4f794228ab5f881131`
- PR #27: permanece congelado; qualquer reaproveitamento futuro exige re-auditoria sobre a `main` atual, nunca merge direto do branch antigo.

Esta auditoria não altera jogadores, elencos, clubes, ratings, atributos FC26 ou conteúdo factual. O objetivo é fechar o plano de modernização técnica antes de qualquer nova trilha factual.

## 2. Estado consolidado já considerado concluído

### 2.1 Integridade de dados e saves

O projeto já possui uma base forte e não deve reabrir estes pontos sem regressão concreta:

- save por slot com banco dedicado;
- save/reopen coberto por testes;
- isolamento entre slots;
- atomicidade de operações críticas;
- migrations não destrutivas até Room V22;
- ausência de `fallbackToDestructiveMigration` no caminho consolidado;
- integridade relacional e validação de referências de fixtures;
- stress de 20 temporadas;
- stress de 100 temporadas match-by-match.

### 2.2 FC26

Estado consolidado após a Fase 10.4:

- 18.405 jogadores FC26 preservados;
- 60.885 jogadores persistidos no universo de benchmark;
- 1.325 sinais de empréstimo no snapshot;
- 816 empréstimos resolvidos com owner/borrower seguros;
- 509 sinais não resolvidos mantidos fail-closed;
- `duplicatePlayerIds = 0`;
- `duplicateTeamIds = 0`;
- `overallMutated = 0`;
- `potentialMutated = 0`;
- `attributesMutated = 0`.

Os 509 sinais não resolvidos não são blocker da modernização técnica. São backlog factual futuro e não devem ser “resolvidos” por fuzzy matching ou invenção.

### 2.3 Competições já consolidadas

- UEFA Champions League: engine dedicado;
- UEFA Europa League: engine dedicado;
- UEFA Conference League: engine dedicado;
- CONMEBOL Libertadores/Sudamericana: engine dedicado existente;
- Mundial de Clubes: engine dedicado e fail-closed;
- rollover, save/reopen e isolamento de competições cobertos por testes.

As aproximações documentadas da Fase 10.3 permanecem aceitas até a trilha pós-modernização.

### 2.4 Performance

O benchmark atual trabalha com 60.885 jogadores persistidos. A Fase 10.1 já removeu o maior hot path mensal/semanal e mantém budgets explícitos para:

- persistência inicial;
- reopen/full reload;
- evolução mensal;
- heap.

Nenhum roadmap futuro pode relaxar esses budgets para esconder regressão.

## 3. Dívidas técnicas reais encontradas na `main` atual

### 3.1 `GameViewModel` ainda concentra responsabilidades demais — prioridade alta

A Fase 10.2 extraiu contratos, negociação, carreira e academia em boundaries melhores, mas `GameViewModel.kt` continua grande e ainda coordena, no mesmo objeto:

- sessão/save ativo;
- flows globais de times, jogadores e fixtures;
- escalação e titularidade;
- mutações de elenco;
- estado de partida ao vivo;
- seleção de time;
- composição de diversos UseCases;
- feedback/toasts.

Há operações como alteração de titularidade que ainda emitem feedback de UI dentro de `repo.withTransaction`. O princípio consolidado deve ser: banco/UseCase decide e persiste; UI só publica feedback depois do commit.

### 3.2 Flows globais ainda podem materializar universo inteiro — prioridade alta

`GameViewModel` mantém `allPlayers`, `allTeams` e `allFixtures` como `StateFlow` globais derivados do repository. Com ~60 mil jogadores, telas que só precisam de subconjuntos não devem depender do universo inteiro.

A Fase 10.1 já registrou recargas globais de UI como dívida posterior. O roadmap final deve substituí-las incrementalmente por queries/flows direcionados, sem reescrita total.

### 3.3 `GameRepository` permanece uma façade ampla — prioridade média/alta

`GameRepository` continua concentrando acesso a quase todos os DAOs/domínios. Isso é funcional e compatível, mas há dois pontos claros de hardening:

1. `restartSeasonStateAtomically()` ainda carrega todos os jogadores, cria cópias em memória e grava o reset por entidades completas;
2. validações de referências de fixtures ainda podem carregar todos os times para formar o conjunto de IDs persistidos.

Esses caminhos não são o hot path semanal atual, mas são exatamente o tipo de full-table work que deve ser removido antes do fechamento técnico.

### 3.4 UI regression visual ainda não representa o jogo — prioridade alta antes da release

O teste `GreetingScreenshotTest` ainda captura apenas `Hello Robolectric!`. Ele prova que Roborazzi funciona, mas não protege nenhuma tela real do produto.

Antes da release devem existir golden/screenshot regressions de telas reais e estados reais do jogo.

### 3.5 Release engineering ainda é provisória — blocker de produto final

O `app/build.gradle.kts` atual ainda usa:

- `applicationId = "com.aistudio.brasfutretro.djuxzt"`;
- `versionCode = 1`;
- `versionName = "1.0"`;
- namespace `com.example`;
- release com `isMinifyEnabled = false`;
- signing release dependente de variáveis de ambiente, sem gate de release no CI.

`strings.xml` e `metadata.json` ainda usam o nome `Remix Brasfut Retro Manager`.

O workflow principal constrói `assembleDebug` e publica APK debug, mas não valida release/AAB. Isso impede considerar o projeto pronto para distribuição.

### 3.6 Compatibilidade/lifecycle ainda precisa de certificação de produto

Embora a base tenha bons testes de persistência, faltam gates de produto para:

- recriação de Activity/processo;
- navegação após troca de slot;
- estados vazios/loading/error das telas principais;
- font scale/acessibilidade;
- diferentes larguras de tela;
- comportamento offline/erro de imagem remota;
- política final de backup/data extraction;
- justificativa final de permissões, inclusive INTERNET;
- upgrade real de uma versão anterior do app para a release candidate.

## 4. Lacunas de regra/conteúdo identificadas, mas não bloqueadoras da modernização técnica

O registry atual deixa explicitamente como `REAL_RULES_NOT_IMPLEMENTED`:

- CONCACAF Champions Cup;
- CONCACAF Central American Cup;
- CONCACAF Caribbean Cup;
- CAF Champions League;
- CAF Confederation Cup;
- AFC Champions League Elite;
- AFC Champions League Two;
- AFC Challenge League;
- OFC Champions League.

Também permanecem:

- FIFA Intercontinental Cup como `CATALOG_ONLY`;
- competição estadual como `CATALOG_ONLY`;
- copa nacional no engine genérico legado;
- access list/coefficient UEFA completos não persistidos;
- ausência de associações nacionais OFC no dataset atual;
- fallback OFC auditável no Mundial;
- disciplina/coefficient/sorteio como critérios não persistidos em alguns desempates.

Esses itens são importantes para fidelidade esportiva, mas mexem em regras/conteúdo e devem entrar somente depois do fechamento da modernização técnica. A branch/PR #27 contém uma fundação antiga de access list/coefficient UEFA, porém está baseada numa `main` muito anterior e foi declarada incompleta; deve ser tratada apenas como material de referência em re-auditoria futura.

## 5. Higiene de branches/PRs

### PR #34

Permanece congelado e intocado até autorização específica de retomada factual.

### PR #27

Permanece Draft/congelado. Não deve ser mergeado na forma atual. Futuramente, uma fase própria de regras UEFA deve re-auditar os conceitos e reaplicar somente o que continuar correto sobre a `main` moderna.

### PR #46

`feat: phase 9.14B preserve FC26 loan identities safely` é tecnicamente superado pela Fase 10.4/PR #54. O PR #54 implementou uma política posterior e mais completa: materialização segura dos 816 casos resolvidos, quarentena dos 509 rejeitados e lifecycle/transfer/contrato/finanças/CPU/UI com regressões. O PR #46 deve ser fechado como superseded, nunca mergeado sobre a `main` atual.

## 6. Roadmap técnico final

A partir deste ponto existem **cinco fases técnicas de implementação restantes** antes de declarar a modernização concluída.

---

# FASE 10.5 — HARDENING ARQUITETURAL E PERFORMANCE RESIDUAL

## Objetivo

Fechar as dívidas arquiteturais e de full-table work que restaram após 10.1/10.2 sem alterar regras de gameplay ou conteúdo factual.

## Escopo obrigatório

1. Extrair da `GameViewModel` as mutações de escalação/titularidade e outros domínios ainda transacionais na UI.
2. Garantir que toast/StateFlow/feedback seja publicado somente depois de commit.
3. Substituir dependências desnecessárias de `allPlayers`/`allFixtures`/`allTeams` por flows direcionados nas telas/callers que não precisam do universo global.
4. Otimizar `restartSeasonStateAtomically()` com action-set SQL/updates direcionados, evitando materializar ~60K `Player` completos apenas para reset sazonal.
5. Tornar a validação de referências de fixtures baseada em queries de IDs/contagens direcionadas, sem carregar toda a tabela Team.
6. Manter `GameRepository` compatível, mas reduzir responsabilidades concretas quando houver boundary claro e teste correspondente.
7. Não realizar big-bang rewrite do ViewModel ou Repository.

## Gates

- suíte non-stress completa;
- regressões de lineup/roster/feedback-after-commit;
- CareerFunctionalFlow;
- save atomicity/isolation;
- migration safety;
- benchmark 60.885 sem relaxar budgets;
- stress 20/100;
- FC26 18.405/18.405 e mutações zero;
- Room V22 salvo necessidade real e auditada de migration.

---

# FASE 10.6 — UI/UX DE PRODUÇÃO + ACESSIBILIDADE

## Objetivo

Modernizar a interface sem alterar a lógica esportiva/factual.

## Escopo obrigatório

1. Consolidar componentes/design tokens usados por Dashboard, Elenco, Táticas, Transferências, Finanças, Classificação, Partida, Saves e seleção de clube.
2. Padronizar loading, empty, erro, confirmação e feedback.
3. Validar edge-to-edge/insets e navegação de retorno.
4. Adaptar telas para diferentes larguras e font scale.
5. Adicionar semantics/content descriptions e alvos de toque adequados.
6. Remover o screenshot placeholder como único gate visual.
7. Criar Roborazzi/golden tests das telas críticas em estados representativos.
8. Preservar exatamente regras, ratings, jogadores, clubes e competições.

## Gates

- screenshot regressions reais;
- Compose/Robolectric tests das navegações críticas;
- StartupSmokeTest;
- suíte funcional completa;
- nenhum acesso novo a full-table apenas para renderização;
- benchmark/stress preservados.

---

# FASE 10.7 — COMPATIBILIDADE, LIFECYCLE E RESILIÊNCIA

## Objetivo

Certificar que a aplicação moderna se comporta corretamente fora do caminho feliz.

## Escopo obrigatório

1. Recriação de Activity e restauração do estado de navegação essencial.
2. Troca rápida de save slot sem repository/state leak.
3. Processo morto/reopen usando somente estado persistido necessário.
4. Backup/restore e data extraction alinhados ao modelo multi-slot.
5. Upgrade/migration de bancos legados até V22 com carreira jogável após reopen.
6. Comportamento offline/erro de imagem remota sem crash ou bloqueio de gameplay.
7. Auditoria das permissões do Manifest.
8. Teste com minSdk suportado e target atual, cobrindo APIs de compatibilidade relevantes.
9. Nenhum singleton de carreira compartilhado entre slots.

## Gates

- BackupRestoreRoundTrip;
- migration continuation/safety;
- save-slot isolation;
- startup/recreate tests;
- smoke de navegação principal;
- stress 20/100;
- Room schema auditado.

---

# FASE 10.8 — RELEASE ENGINEERING E IDENTIDADE DE PRODUTO

## Objetivo

Converter o projeto de build de desenvolvimento em artefato distribuível e reproduzível.

## Escopo obrigatório

1. Definir nome final do app e applicationId definitivo sem perda de saves no caminho suportado.
2. Definir estratégia final de namespace/package; evitar refatoração cosmética desnecessária se `applicationId` puder resolver identidade com menor risco.
3. Versionamento real de `versionCode`/`versionName`.
4. Configurar signing release sem segredo versionado.
5. Adicionar build `assembleRelease` e `bundleRelease`/AAB ao CI de release.
6. Validar R8/minify/proguard antes de decidir ativação definitiva; nunca ativar minify sem smoke/teste da build resultante.
7. Remover configuração dummy de produção ou isolá-la estritamente ao CI/debug quando aplicável.
8. Auditar permissões, backup, metadata e recursos launcher.
9. Produzir APK/AAB release como artifact de CI.
10. Garantir que build release inicia e abre um save válido.

## Gates

- release compile/sign configuration validada;
- AAB e APK release gerados;
- install/start smoke da variante release quando tecnicamente disponível no CI;
- non-stress + regressões críticas contra release-compatible code;
- sem segredos no repositório;
- nenhuma dependência de debug para gameplay.

---

# FASE 10.9 — CERTIFICAÇÃO FINAL / RELEASE CANDIDATE

## Objetivo

Executar a auditoria de encerramento da modernização e produzir a primeira release candidate tecnicamente final.

## Escopo obrigatório

1. Remover testes placeholder, arquivos temporários, workflows temporários e diagnostics de fase que não pertençam ao produto final.
2. Revisar todos os TODOs/compat adapters remanescentes e classificar explicitamente o que é legado necessário versus dívida futura.
3. Auditar arquitetura final, coroutines, Room, flows, transações, save slots e lifecycle.
4. Executar matriz consolidada de gameplay:
   - novo save;
   - save/reopen;
   - 2+ slots;
   - temporada completa;
   - promoção/rebaixamento;
   - competições internacionais já suportadas;
   - contratos;
   - transferências;
   - empréstimos;
   - finanças;
   - academia;
   - evolução;
   - aposentadoria;
   - troca de clube do treinador.
5. Executar FC26, benchmark, stress 20 e 100 temporadas no mesmo head final.
6. Executar review Codex final e resolver todo P0/P1/P2 material.
7. Gerar APK/AAB final.
8. Classificar `APTO PARA RELEASE CANDIDATE` somente no head exato auditado.

## Exit gate da modernização

A modernização técnica termina somente quando:

- todos os checks obrigatórios estiverem verdes no mesmo head;
- release build/AAB existir;
- Room/migrations/save slots estiverem íntegros;
- benchmark não tiver regressão material;
- stress 20/100 estiver verde;
- FC26 continuar 18.405/18.405 e imutável;
- não houver finding Codex material aberto;
- não houver teste enfraquecido/ignorado;
- não houver workflow temporário;
- não houver blocker de startup/reopen/release.

## 7. Trilha pós-modernização — Fase 11.x

Somente depois do exit gate da Fase 10.9 deve começar a atualização factual/regras não bloqueadoras.

### 11.1 — UEFA access list / coeficientes / qualifying

- re-auditar PR #27 contra a `main` moderna;
- implementar access list/coefficient somente com fonte/regra segura;
- não mergear a branch antiga diretamente.

### 11.2 — Confederações restantes + Intercontinental

- engines dedicados CONCACAF/CAF/AFC/OFC conforme dados suportados;
- campeões continentais tipados;
- FIFA Intercontinental Cup anual;
- remover fallback OFC somente quando houver dado suficiente, nunca por invenção.

### 11.3 — Atualização factual controlada

- decidir destino do PR #34;
- atualizar elencos/jogadores/clubes somente por pipeline factual aprovado;
- tentar reduzir os 509 sinais de empréstimo não resolvidos apenas quando novas identidades canônicas existirem;
- preservar IDs/ratings/atributos conforme o contrato da fonte escolhida.

## 8. Contagem final

### Para concluir a modernização técnica

Restam **5 fases**:

1. 10.5 — Hardening arquitetural e performance residual;
2. 10.6 — UI/UX de produção + acessibilidade;
3. 10.7 — Compatibilidade, lifecycle e resiliência;
4. 10.8 — Release engineering e identidade de produto;
5. 10.9 — Certificação final / release candidate.

### Depois da modernização

A trilha 11.x é evolução factual/esportiva e não bloqueia a conclusão técnica.

## 9. Regra permanente de execução

Cada fase 10.x deve seguir `AGENTS.md`: implementação completa, CI verde, review final, head exato auditado, base válida e classificação `APTO PARA MERGE` antes do merge automático. Nenhum teste pode ser removido, ignorado ou enfraquecido para obter verde.
