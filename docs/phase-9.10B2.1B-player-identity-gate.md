# Fase 9.10B2.1B — Player Identity Gate

## Problema encontrado

O seed legado cria jogadores com `playerId = teamId * 1000 + slot`. O reparador de integridade usa
a mesma família de IDs, com busca collision-safe quando necessário. Transferências normais preservam
o ID já persistido, mas a identidade inicial continua acoplada ao clube de origem.

Isso é insuficiente para uma base factual completa: uma atualização de elenco ou mudança de clube
não pode transformar a mesma pessoa em outro jogador.

## Alteração

Foi criado um namespace de IDs para jogadores factuais:

- chave: nome canônico + data de nascimento ISO + desambiguador opcional;
- `teamId` não participa da chave;
- normalização remove diferenças de acento/caixa/espaçamento;
- namespace começa em `100_000_000_000_000`, acima dos IDs procedurais atuais;
- nenhum campo Room foi adicionado.

`EuropeanRealPlayerTemplate` separa dados factuais dos atributos internos do jogo. Atributos de
força/finalização/passe/velocidade/defesa/visão, salário e valor de mercado são derivados de forma
determinística pelo próprio Pro Football e não representam ratings de terceiros.

## Comportamento esperado

1. Um jogador factual é materializado uma vez no novo save com seu ID canônico.
2. Mercado, empréstimo, free agency e transferência modificam `teamId`, nunca `id`.
3. Atualizações futuras de DefaultData afetam novos saves, não reescrevem uma carreira já iniciada.

## Gate de importação dos elencos

Este PR ainda NÃO declara nenhum elenco real completo. Antes de importar milhares de registros é
obrigatório:

- confirmar fonte/temporada de cada elenco;
- validar colisão global de `stableId` no conjunto importado;
- integrar o resolver ao seed inicial sem sobrescrever saves existentes;
- preservar o fallback procedural apenas para regens/base/faltas de cobertura;
- testar tempo de criação de save e stress 20/100.

A importação factual por país será feita após este contrato ficar verde no CI.

## Room

Permanece V21. Não há migration nem alteração de schema nesta fatia.
