# Prompt — revisão completa do StashLink antes da 1.0

Colar numa sessão nova do Claude Code no repositório `Leoascenci0/stashlink`. A revisão vem **antes** da tag `v1.0.0`.

---

Leia `CLAUDE.md`, `docs/ARCHITECTURE.md`, `docs/ROADMAP.md`, `docs/UPDATING.md` e `CHANGELOG.md`. Comece com
`git status` + `git fetch origin --prune`; `git pull --ff-only` na `main`. Branch: `chore/revisao-1.0`.

**Objetivo:** revisar o mod inteiro antes de publicar a versão 1.0: correção, conexões entre módulos, segurança no
servidor e desempenho. Esta sessão é de **revisão + correções pequenas**. Mudança grande vira item novo no roadmap, não
entra aqui.

**Roteamento:** a sessão principal só coordena e decide. A varredura vai para subagentes, um por área (lista abaixo), em
Sonnet/medium, cada um com retorno curto: achado, arquivo:linha, gravidade, como reproduzir. Áreas de risco alto (dupe de
item, validação no servidor, rede) passam por uma segunda leitura em Sonnet/high. Opus só para decidir correções que mudam
arquitetura ou para revisar os achados de gravidade alta (gatilho 4). Relatar a linha `Roteamento:` no fim.

## Áreas (um subagente por área)

1. **Rede e validação no servidor** (`network/`, todos os handlers de pacote): todo pacote do cliente confere distância,
   permissão/claim, `FeatureGate`, tamanho máximo e índices (slot, quantidade, id de receita) antes de mexer em item.
   Cliente nunca decide nada sozinho. Anti-flood em todos os pedidos repetíveis. Pacote malformado não derruba o servidor.
2. **Integridade de itens, sem dupe e sem perda** (`refill/`, `quickstack/`, `lootall/`, `pull/`, `organize/`, `bench/`,
   `slotlock/`): toda transferência é atômica (tira e põe, ou nada); devolução ao fechar a tela, sair, morrer, trocar de
   dimensão ou cair a conexão; dois jogadores no mesmo baú; baú quebrado durante a operação; shulker dentro de shulker;
   item com NBT/componentes (encantado, nomeado, livro); pilhas parciais; mochila cheia. Conferir a soma de itens antes e
   depois nos GameTests e apontar onde falta teste.
3. **Fontes de armazenamento e desempenho** (`source/`, `storage/`, `NearbyContainers`, `PlayerSources`): nunca carregar
   chunk; varredura com raio 32 e 289+ containers abaixo de 5 ms (linha `[STASHLINK-PERF]`); cache e invalidação corretos
   (baú colocado/quebrado/movido por pistão); nada de varredura por tick quando não precisa; alocação em loop quente;
   estruturas O(n²) escondidas; inventário interno das estações nunca vira fonte nem destino.
4. **Bancadas e estações** (`bench/`): todos os 13 menus: filtro do painel, colocar no slot certo, lápis/combustível/
   sinalizador/poções, devolução ao fechar, teto de 512 itens no pacote, slots de resultado nunca disparados.
5. **Cliente e modo cliente** (`client/`, `clientmode/`, mixins de tela): cliques por tick limitados (anticheat), nada
   roda no criativo quando não deve, o mod não quebra em servidor sem o mod, Shift + passar o mouse sem cliques repetidos,
   telas centralizadas e sem sobreposição em escala de GUI 1 a 4 e em janela pequena.
6. **Mixins e compatibilidade** (`mixin/`, `compat/`, `compat/mc/`): API frágil do Minecraft só em `compat/mc/`; mixins
   com alvo e `ordinal` certos, `require`/`expect` adequados, sem `@Overwrite`; Litematica opcional (sem a dependência, o
   jogo abre); conflitos prováveis com Mouse Tweaks, Inventory Profiles Next, JEI/EMI/REI, Sophisticated Storage, Carpet.
7. **Config, Feature e cadeados** (`config/`): toda função no enum `Feature`, com liga/desliga + cadeado, textos
   `stashlink.feature.<id>` e `.tip` em pt_br e en_us (sem chave faltando nem sobrando), passando por `FeatureGate`;
   migração do `stashlink.json` (`configVersion`); valores inválidos no arquivo são corrigidos, não derrubam.
8. **Loaders e build** (`fabric/`, `neoforge/`, `build-logic/`, `gradle.properties`, `.github/workflows/`): código de
   loader é só cola; NeoForge registra tudo o que o Fabric registra (pacotes, teclas, comandos, eventos); metadados (nome,
   descrição, versão 1.0.0, licença, ícone, dependências) certos nos dois; `release.yml` acha os dois jars; build sem
   warnings novos.
9. **Dados persistidos** (`label/`, `slotlock/`, filtro da N): o que vai para o disco, o que some ao quebrar, shulker leva
   no item, nada cresce sem limite, texto do jogador limpo (sem `§`, invisíveis, tamanho).

## Otimização (prioridade depois da correção)

- Medir antes de mudar: usar o GameTest de desempenho e o perfil (`spark`, se der) para achar o que realmente pesa.
- Alvos: varredura de containers, montagem do painel das estações, cálculo de receitas/poções, sincronização de pacotes
  (mandar só o que mudou), holograma de nomes (distância e quantidade desenhada por frame).
- Toda otimização precisa de número antes e depois. Sem ganho medido, não entra.

## Regras

- Lógica em `common/`; toda mudança de item é server-side e validada; nunca enfraquecer validação para ganhar desempenho.
- Corrigir no mesmo PR só o que é pequeno e seguro, sempre com teste (unitário ou GameTest) que falhava antes.
- Rodar `./gradlew build` e `./gradlew :fabric:runGameTest` antes de cada push; CI verde antes do merge.
- Achado grande vira item novo no `docs/ROADMAP.md` e no `docs/roadmap.html`, e depois republicar o artifact "StashLink Roadmap".

## Entrega

1. Relatório em `docs/REVISAO-1.0.md`: tabela com área, achado, gravidade (alta/média/baixa), arquivo:linha, status
   (corrigido / vira item / aceito como limite), e as medições de desempenho antes e depois.
2. PR `chore/revisao-1.0` com as correções pequenas e os testes novos.
3. Resumo em linguagem simples para o Eliel: o que estava bom, o que foi corrigido, o que fica para depois, e se a 1.0
   está pronta para a tag (sim/não, e por quê).
