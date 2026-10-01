# Roadmap — StashLink (nome de trabalho)

Mod de qualidade de vida para Minecraft (Fabric + Forge/NeoForge). Construído **item por item**: um item = uma
branch = um PR (squash merge) = um handoff pro próximo. Nunca commitar direto em `main`.

Legenda: ⬜ a fazer · 🟨 em andamento · ✅ concluído

## Visão geral das funcionalidades

| # | Funcionalidade | Resumo |
|---|----------------|--------|
| F1 | **Uso direto de shulker no inventário** | Ao colocar bloco/usar item e o da mão acabar, o mod puxa do mesmo item que estiver dentro de shulkers no inventário. |
| F2 | **Integração com Litematica** | Pick block/Easy Place na pré-visualização enxerga itens dentro de shulkers (inventário e próximas). |
| F3 | **Raio de fontes** | Shulkers (e baús) colocados no chão, dentro de um raio configurável, também servem de fonte. |
| F4 | **Tecla N — guardar tudo** | Envia itens do inventário para baús próximos que já contenham aquele item. |
| F5 | **Tecla W no baú — puxar tudo** | Dentro da GUI de um container, traz para o inventário tudo o que couber. |
| F6 | **Config + GUI + comandos** | Raio, teclas, listas de exclusão; tela de config e `/stashlink`. |

## Decisões que travam o resto (Itens 0–1)

### Item 0 — Decisões de alvo e ferramentas ✅
- **Branch:** `chore/decisoes-alvo`
- Escolher: versão(ões) do Minecraft (recomendação: a estável mais recente que o **Litematica** já suporte),
  Java correspondente, e se Forge, NeoForge ou ambos (recomendação: **Fabric + NeoForge**, Forge clássico só se
  houver demanda real — o ecossistema migrou para NeoForge).
- Escolher o template multi-loader: **MultiLoader-Template** (Jared) ou **Architectury** — recomendação:
  MultiLoader-Template (menos "mágica", projeto `common` + `fabric` + `neoforge`).
- Escolher licença (sugestão MIT) e nome definitivo/mod id.
- **Pronto quando:** decisões registradas em `docs/ARCHITECTURE.md` (seção "Decisões") com justificativa.

### Item 1 — Esqueleto do projeto que compila e abre o jogo ✅
- **Branch:** `feat/esqueleto-multiloader`
- Projeto Gradle com módulos `common`, `fabric`, `neoforge` (e `forge` se decidido). Mod carrega e imprime uma
  linha de log no Fabric **e** no NeoForge; `runClient` de cada loader abre o jogo.
- CI no GitHub Actions: `./gradlew build` em PR.
- **Pronto quando:** `./gradlew build` verde local e no CI; jogo abre nos dois loaders com o mod listado.

## Núcleo (Itens 2–5) — só lógica, sem UI

### Item 2 — Motor de leitura/escrita de conteúdo de shulker ✅
- **Branch:** `feat/shulker-storage-api`
- Em `common`: classe que, dado um `ItemStack` de shulker box (qualquer cor), **lê e grava** seu conteúdo
  (componente `container` nas versões 1.20.5+; `BlockEntityTag` nas anteriores). Operações: `count(item)`,
  `extract(predicate, max)`, `insert(stack)`. Nada de mexer no jogador ainda.
- Testes unitários/GameTest: extrair de shulker cheia, vazia, com itens com NBT/componentes, stacks parciais.
- **Pronto quando:** testes cobrem extrair/inserir sem duplicar nem perder item.

### Item 3 — Fonte de itens abstrata (`ItemSource`) ✅
- **Branch:** `feat/item-source`
- Interface `ItemSource` (`available(item)`, `take(item, n)`) com implementações: shulker no inventário do
  jogador. Já desenhada para aceitar depois: shulker/baú no chão (Item 6). Ordem de prioridade configurável.
- **Pronto quando:** testes provam que a ordem de prioridade e a soma total estão corretas.

### Item 4 — Reabastecimento automático da mão (F1, versão servidor) ✅
- **Branch:** `feat/refill-from-shulker`
- Hook **no servidor**: quando o stack da mão principal/secundária se esgota (colocar bloco, comer, arremessar,
  usar ferramenta que quebra), puxar um stack do mesmo item de uma shulker do inventário.
  Também cobrir o caso "resta 1 na hotbar e 3 packs na shulker" (mão nunca fica vazia enquanto houver estoque).
- Regras: não puxar em criativo; ignorar shulker "aberta" na GUI; sincronizar inventário com o cliente.
- **Pronto quando:** testado em SP e servidor dedicado; funciona com cliente vanilla conectando (mod só no server).

### Item 5 — Robustez e anti-dupe da fase 1 ✅
- **Branch:** `fix/robustez-shulker`
- GameTests de: shulker dentro de shulker (proibido), desconexão no meio da operação, morte, itens com
  encantamento/nome, hotbar cheia, item de shulker igual ao item da mão que é a própria shulker.
- **Pronto quando:** nenhuma sequência de testes gera ou perde itens.

## Fontes no mundo (Item 6)

### Item 6 — Raio de fontes (F3) ✅
- **Branch:** `feat/source-radius`
- Novas `ItemSource`: **shulker boxes colocadas** e (opcional, config) baús/barris **dentro do raio** do jogador.
  Raio padrão pequeno (ex.: 8 blocos), teto duro no servidor (ex.: 64) por performance.
- Varredura eficiente: índice de block entities por chunk carregado, com invalidação por evento — **não** varrer
  cubo de blocos a cada uso.
- Respeitar proteção/claims: só usar container que o jogador poderia abrir (checar via evento de interação).
- **Pronto quando:** colocar shulker no chão, andar até o limite do raio e o refill continua funcionando; fora do
  raio, para. Sem queda de TPS medida com 200+ containers no raio.

## Litematica (Item 7)

### Item 7 — Integração Litematica (F2) ✅
- **Branch:** `feat/litematica-compat`
- **Soft dependency**: o mod funciona sem Litematica. Quando presente, o *pick block* da pré-visualização e o
  Easy Place passam a considerar `ItemSource`s: se o item não está no inventário mas está em shulker (inventário
  ou raio), o cliente pede ao servidor (pacote custom) e o item vai para a hotbar antes do clique de colocação.
- Investigar primeiro (no início do item): pontos de extensão do Litematica na versão-alvo (mixin em
  `InventoryUtils`/pick block vs. evento). Registrar achados em `docs/ARCHITECTURE.md`.
- Nota Forge/NeoForge: Litematica é Fabric-nativo; portas para NeoForge são não-oficiais → compat por loader,
  testar só onde existir build.
- **Pronto quando:** fluxo real: schematic carregada → itens em shulker no inventário → clicar na preview
  constrói sem tirar a shulker do inventário.

## Teclas (Itens 8–9)

### Item 8 — Tecla N: guardar tudo em baús próximos (F4) ✅
- **Branch:** `feat/quick-stack-n`
- Cliente: keybind configurável (padrão N) → pacote → servidor faz: para cada container dentro do raio que
  **já contém** o item, mover o que couber. Ignora hotbar e slots travados (config). Mostra resumo no chat/HUD
  ("42 itens em 3 baús").
- Servidor valida distância, permissão e se o container não está aberto por outro jogador.
- **Pronto quando:** regras de casamento (mesmo item, mesmas propriedades), baú duplo e barril testados.

### Item 9 — Tecla W na GUI do container: puxar tudo (F5) ✅
- **Branch:** `feat/loot-all-w`
- Cliente: em telas de container (baú, barril, shulker, ender chest), tecla configurável (padrão W) envia **um
  único pacote** ao servidor, que move para o inventário tudo o que couber. Não ativa em telas com campo de
  texto (bigorna, criativo). Alternativa: botão na tela.
- **Pronto quando:** nada perde/duplica; inventário cheio deixa o resto no baú; sem flood de pacotes.

## Configuração e polimento (Itens 10–12)

### Item 10 — Config, tela e comandos (F6) ✅
- **Branch:** `feat/config-ui`
- Arquivo de config comum aos loaders; tela in-game (Mod Menu/Cloth no Fabric, tela de config no NeoForge);
  comandos `/stashlink radius <n>`, `/stashlink reload`. Teto do raio definido pelo servidor.
- Na tela: interruptor **"Usar baús e barris como fonte"** (`includeChests`, hoje fixo em desligado) para o
  reabastecimento da mão e o Litematica funcionarem também com baús, como já funcionam com shulkers. A tecla N
  já usa baús sempre. Também expor os slots travados (`lockedSlots`) e, se quiser, "incluir hotbar" na tecla N.
- **Pronto quando:** mudar raio na tela e por comando tem efeito imediato e persiste.
- **Feito:** `config/stashlink.json` (JSON, igual nos dois loaders; campos `sourceRadius`, `maxRadius`,
  `includeChests`, `lockedSlots`). `maxRadius` é o teto do servidor (só no arquivo; nem tela nem comando passam
  dele, e o código limita a 64). Tela própria em `common` (widgets do jogo, sem Cloth): Mod Menu no Fabric
  (dependência opcional) e botão "Config" nativo no NeoForge. Em servidor remoto a tela fica somente leitura. A tela também abre pela tecla **K**
  (configurável; `ConfigKey`), sem precisar de Mod Menu.
  `/stashlink radius [n]` e `/stashlink reload`, só operadores. "Incluir hotbar" na tecla N **não** foi feito.
  Teste manual em jogo pendente (tela e comandos).

### Item 11 — Testes de carga, multiplayer e compat com outros mods ⬜
- **Branch:** `test/carga-e-compat`
- Servidor dedicado com 2+ jogadores; verificar corridas entre jogadores no mesmo baú; testar com mods comuns
  (Litematica, Tweakeroo, Carpet, mods de armazenamento como Sophisticated/Iron Chests).

### Item 12 — Release ⬜
- **Branch:** `chore/release-1.0`
- README com GIFs, changelog, publicação Modrinth/CurseForge, artefatos por loader via CI.

## Como usar este roadmap

1. Começar cada sessão: `git status` + `git fetch origin --prune` (regra 7 do Eliel).
2. Uma branch por item, PR com squash merge; ao concluir, marcar ✅ aqui.
3. Ao terminar um item, gerar o **handoff** do próximo (ver `CLAUDE.md`): resumo em linguagem simples do que foi
   feito + prompt autocontido com o texto exato do próximo item.
