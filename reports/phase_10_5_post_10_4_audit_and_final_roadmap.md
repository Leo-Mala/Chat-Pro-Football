# Fase 10.5 — Auditoria pós-10.4 e Roadmap Técnico Final

## 1. Baseline oficial

- Repositório: `Leo-Mala/Chat-Pro-Football`
- Baseline pós-Fase 10.4: `main@dde17e82567d81d4477fedd4d9f4e4012ac9b3fc`
- Branch de trabalho: `agent/phase-10-5`
- Room: V22
- PR #54: mergeado
- PR #34: permanece congelado em Draft no head `c421dafee0ecdd2ea23f7d4f794228ab5f881131`
- PR #27: permanece congelado; qualquer reaproveitamento futuro exige re-auditoria sobre a `main` atual, nunca merge direto do branch antigo.

Esta auditoria não altera jogadores, elencos, clubes, ratings, atributos FC26 ou conteúdo factual. O objetivo é concluir a modernização técnica antes de qualquer nova trilha factual.

## 2. Estado consolidado já concluído

### 2.1 Integridade de dados e saves

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

Os 509 sinais não resolvidos são backlog factual futuro e não podem ser “resolvidos” por fuzzy matching ou invenção.

### 2.3 Competições consolidadas

- UEFA Champions League: engine dedicado;
- UEFA Europa League: engine dedicado;
- UEFA Conference League: engine dedicado;
- CONMEBOL Libertadores/Sudamericana: engine dedicado existente;
- Mundial de Clubes: engine dedicado e fail-closed;
- rollover, save/reopen e isolamento de competições cobertos por testes.

As aproximações documentadas da Fase 10.3 permanecem aceitas até a trilha pós-modernização.

### 2.4 Performance

O benchmark atual trabalha com 60.885 jogadores persistidos. Os budgets de persistência inicial, reopen/full reload, evolução mensal e heap permanecem obrigatórios. Nenhuma fase futura pode relaxar thresholds para esconder regressão.

## 3. Dívidas técnicas confirmadas na `main`

### 3.1 Arquitetura e full-table work

`GameViewModel` ainda concentra sessão/save, flows globais, escalação, mutações de elenco, estado de partida, seleção de time, composição de UseCases e feedback. Algumas mutações ainda publicam feedback dentro de transações.

Também permanecem dependências de `allPlayers`, `allTeams` e `allFixtures` em caminhos que poderiam usar consultas direcionadas. `GameRepository` continua uma façade ampla; `restartSeasonStateAtomically()` ainda pode materializar toda a tabela Player para reset sazonal, e validações de fixtures podem carregar toda a tabela Team.

### 3.2 UI/UX e acessibilidade

O gate visual existente ainda usa `GreetingScreenshotTest` com conteúdo placeholder, portanto não protege telas reais do jogo. Faltam regressões visuais de Dashboard, Elenco, Táticas, Transferências, Finanças, Classificação, Partida, Saves e seleção de clube, incluindo estados loading/empty/error, diferentes larguras e font scale.

### 3.3 Lifecycle e resiliência

Ainda precisam de certificação explícita:

- recriação de Activity/processo;
- navegação após troca de slot;
- processo morto/reopen;
- backup/restore e data extraction multi-slot;
- upgrade real de bancos legados até V22;
- comportamento offline/erro de imagem remota;
- auditoria das permissões;
- compatibilidade em minSdk/target suportados.

### 3.4 Release engineering e identidade

O projeto ainda usa configuração provisória de produto, incluindo `applicationId = "com.aistudio.brasfutretro.djuxzt"`, `versionCode = 1`, `versionName = "1.0"`, namespace legado e nome `Remix Brasfut Retro Manager`. O CI principal valida Debug/APK, mas não fecha uma matriz de release/AAB assinável e reproduzível.

## 4. Lacunas factuais/esportivas não bloqueadoras da modernização

Continuam fora da modernização técnica:

- CONCACAF Champions Cup;
- CONCACAF Central American Cup;
- CONCACAF Caribbean Cup;
- CAF Champions League;
- CAF Confederation Cup;
- AFC Champions League Elite;
- AFC Champions League Two;
- AFC Challenge League;
- OFC Champions League;
- FIFA Intercontinental Cup ainda catalog-only;
- competition state/national-cup rules ainda parcialmente genéricas;
- access list/coefficient UEFA completos;
- ausência de associações nacionais OFC factuais;
- critérios esportivos que dependem de dados ainda não persistidos.

Esses itens pertencem à trilha 11.x e não devem ser antecipados durante a modernização técnica.

## 5. Higiene de branches/PRs

### PR #34

Permanece congelado e intocado até autorização específica de retomada factual.

### PR #27

Permanece congelado. Não deve ser mergeado diretamente; qualquer ideia reaproveitada precisa ser re-auditada sobre a `main` moderna.

### PR #46

Foi tecnicamente superado pela Fase 10.4/PR #54 e não deve ser mergeado sobre a `main` atual.

## 6. Roadmap técnico final consolidado

A partir deste ponto existem **duas fases técnicas restantes** antes de declarar a modernização concluída.

---

# FASE 10.5 — MODERNIZAÇÃO TÉCNICA INTEGRADA

Esta fase consolida integralmente os antigos escopos 10.5, 10.6, 10.7 e 10.8. Nenhum requisito ou gate foi removido; apenas a execução passa a ser tratada como uma única fase contínua.

## Objetivo

Fechar em um único ciclo o hardening arquitetural/performance residual, a modernização UI/UX e acessibilidade, a certificação de lifecycle/resiliência e a engenharia de release/identidade de produto, sem alterar dados factuais ou regras esportivas fora do escopo.

## Bloco A — Arquitetura e performance residual

1. Extrair da `GameViewModel` mutações de escalação/titularidade e outros domínios ainda transacionais na UI.
2. Garantir que toast/StateFlow/feedback seja publicado somente depois de commit.
3. Substituir dependências desnecessárias de `allPlayers`/`allFixtures`/`allTeams` por flows/queries direcionados.
4. Otimizar `restartSeasonStateAtomically()` com action-set SQL/updates direcionados, evitando materializar ~60K `Player` completos apenas para reset sazonal.
5. Tornar a validação de referências de fixtures baseada em queries de IDs/contagens direcionadas, sem carregar toda a tabela Team.
6. Reduzir responsabilidades concretas do `GameRepository` somente quando houver boundary claro e teste correspondente.
7. Não realizar big-bang rewrite de ViewModel/Repository.
8. Preservar os budgets de performance existentes sem qualquer relaxamento.

## Bloco B — UI/UX de produção e acessibilidade

1. Consolidar componentes/design tokens para Dashboard, Elenco, Táticas, Transferências, Finanças, Classificação, Partida, Saves e seleção de clube.
2. Padronizar loading, empty, erro, confirmação e feedback.
3. Validar edge-to-edge/insets e navegação de retorno.
4. Adaptar telas para diferentes larguras e font scale.
5. Adicionar semantics/content descriptions e alvos de toque adequados.
6. Remover o screenshot placeholder como único gate visual.
7. Criar Roborazzi/golden tests das telas críticas em estados representativos.
8. Garantir que renderização não introduza novos full-table loads.
9. Preservar exatamente regras, ratings, jogadores, clubes e competições.

## Bloco C — Compatibilidade, lifecycle e resiliência

1. Recriação de Activity e restauração do estado de navegação essencial.
2. Troca rápida de save slot sem repository/state leak.
3. Processo morto/reopen usando somente estado persistido necessário.
4. Backup/restore e data extraction alinhados ao modelo multi-slot.
5. Upgrade/migration de bancos legados até V22 com carreira jogável após reopen.
6. Comportamento offline/erro de imagem remota sem crash ou bloqueio de gameplay.
7. Auditoria das permissões do Manifest.
8. Teste com minSdk suportado e target atual, cobrindo APIs de compatibilidade relevantes.
9. Nenhum singleton de carreira compartilhado entre slots.

## Bloco D — Release engineering e identidade de produto

1. Definir nome final do app e `applicationId` definitivo sem perda de saves no caminho suportado.
2. Definir estratégia final de namespace/package, evitando refatoração cosmética de alto risco quando não necessária.
3. Definir versionamento real de `versionCode`/`versionName`.
4. Configurar signing release sem segredo versionado.
5. Adicionar `assembleRelease` e `bundleRelease`/AAB ao CI de release.
6. Validar R8/minify/proguard antes de qualquer ativação definitiva; nunca ativar minify sem smoke/teste da build resultante.
7. Remover configuração dummy de produção ou isolá-la estritamente ao CI/debug quando aplicável.
8. Auditar permissões, backup, metadata e recursos launcher.
9. Produzir APK e AAB release como artifacts de CI.
10. Garantir que a build release inicia e abre um save válido.

## Gates obrigatórios da Fase 10.5

A Fase 10.5 só pode ser classificada `APTO PARA MERGE` quando todos os blocos A–D estiverem concluídos no mesmo head candidato e os seguintes gates estiverem verdes:

- suíte non-stress completa;
- regressões de lineup/roster/feedback-after-commit;
- CareerFunctionalFlow;
- SaveAtomicity / SaveSlotIsolation;
- migration continuation/safety;
- BackupRestoreRoundTrip;
- startup/recreate/process-reopen tests;
- smoke da navegação principal;
- screenshot regressions reais de telas críticas;
- Compose/Robolectric tests das navegações críticas;
- comportamento offline/erro remoto sem crash;
- benchmark de 60.885 jogadores sem relaxar budgets;
- stress 20 temporadas;
- stress 100 temporadas match-by-match;
- FC26 18.405/18.405;
- `duplicatePlayerIds = 0`;
- `duplicateTeamIds = 0`;
- `overallMutated = 0`;
- `potentialMutated = 0`;
- `attributesMutated = 0`;
- Room schema atual rastreado e sem drift;
- APK Debug preservado;
- APK Release gerado;
- AAB Release gerado;
- signing/configuração de release validada sem segredo no repositório;
- install/start smoke da variante release quando tecnicamente disponível no CI;
- nenhum teste removido, ignorado ou enfraquecido;
- nenhum threshold/timeout relaxado para obter verde;
- Codex final sem finding material P0/P1/P2;
- head/base estáveis e merge automático somente pelo `expected_head_sha` auditado.

## Exit gate da Fase 10.5

O merge da Fase 10.5 significa que arquitetura, performance residual, UI/UX, acessibilidade, lifecycle, resiliência, compatibilidade e release engineering estão tecnicamente concluídos. A partir desse merge não devem existir fases intermediárias adicionais antes da certificação final, salvo blocker objetivo descoberto durante a própria validação.

---

# FASE 10.6 — CERTIFICAÇÃO FINAL / RELEASE CANDIDATE

Esta fase corresponde ao antigo escopo 10.9, renumerado após a consolidação 10.5–10.8.

## Objetivo

Executar a auditoria de encerramento da modernização e produzir a primeira release candidate tecnicamente final.

## Escopo obrigatório

1. Remover testes placeholder, arquivos temporários, workflows temporários e diagnostics de fase que não pertençam ao produto final.
2. Revisar todos os TODOs/compat adapters remanescentes e classificar explicitamente legado necessário versus dívida futura.
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
- não houver blocker de startup/reopen/release;
- a build final estiver classificada `APTO PARA RELEASE CANDIDATE`.

## 7. Trilha pós-modernização — Fase 11.x

Somente depois do exit gate da Fase 10.6 deve começar a atualização factual/regras não bloqueadoras.

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

Restam **2 fases**:

1. **10.5 — Modernização Técnica Integrada** — arquitetura + performance residual + UI/UX + acessibilidade + lifecycle + resiliência + compatibilidade + release engineering;
2. **10.6 — Certificação Final / Release Candidate**.

### Depois da modernização

A trilha 11.x é evolução factual/esportiva e não bloqueia a conclusão técnica.

## 9. Regra permanente de execução

Cada fase 10.x deve seguir `AGENTS.md`: implementação completa, CI verde, review final, head exato auditado, base válida e classificação `APTO PARA MERGE` antes do merge automático. Nenhum teste pode ser removido, ignorado ou enfraquecido para obter verde.