# Prompt — Item 16.3, Fase 3: melhorias das bancadas pedidas pelo Eliel

Leia `CLAUDE.md`, `docs/ARCHITECTURE.md` (Itens 16, 16.1, 16.2, 16.3) e `docs/PROMPT-bancadas-revisao.md`
(regra de ouro da estética: tudo vanilla; seção de segurança; Fase 3). Comece com `git status` +
`git fetch origin --prune`; `git pull --ff-only` na `main`. Confira que a Fase 2 (PR "padronização das bancadas",
branch `feat/bancadas-padronizacao`) já está mesclada — se não estiver, PARE e avise. Rode `git worktree list`; se
outra sessão usar esta pasta, abra um worktree próprio. Branch: `feat/bancadas-fase3` (nunca commitar em `main`).

## O que já existe (não refazer)

- Fase 1 (PR #42/#43): release/releaseAll, teto de 700 kB, `BenchLedger.beforeClose`, chave estável de receita,
  `BenchFlood`, `BenchCompat.Station`, Organizar na ordem do criativo.
- Fase 2: painel com esquerdo = pilha, direito = 1, Shift = máximo, meio passa para o jogo; direito em resultado de
  cortador/tear fabrica 1; vazio diz "Nada no raio de N blocos" (raio efetivo vindo do servidor em
  `BenchPoolSync.radius`); `BenchText` (textos e cliques); `BenchOrder` (ordem única: disponível > faltante > nome >
  id); `BenchCompat.anvilKind` (filtro e aba da bigorna); constantes de layout em `BenchPanel`/`BenchCompat`
  (`ignoresLeftPos`, `widthForLeftPos`, `LOOM_TAB_*`); realce da cor do tear com sprites `slot_highlight_*`.
- 154 GameTests + 189 unitários passam; não pode cair.
- Arquivos centrais: `client/BenchPanel`, `client/BenchTabs`, `client/BenchText`, `client/BenchClient`,
  `bench/BenchSync`, `bench/BenchResults`, `bench/BenchOrder`, `bench/BenchPullService`, `compat/mc/BenchCompat`,
  `compat/mc/StationRecipes`, `mixin/AbstractContainerScreenMixin`, `network/BenchPoolSync`, `BenchPullRequest`,
  lang `pt_br`/`en_us`, `fabric/src/gametest/.../BenchGameTests`. Use `graphify update` + consultas com
  `--budget` baixo (ou subagente Sonnet com retorno curto) para mapear antes de ler arquivos inteiros.

## Pedidos do Eliel (texto dele resumido, sem perder nada)

Ele testou a Fase 2 no jogo (prints de defumador, alto-forno, fornalha, bigorna, ferreiro, encantamento, sinalizador
e suporte de poções) e pediu:

1. **Fornalhas (fornalha, defumador, alto-forno) — aba de combustível igual em todas.**
   - Hoje a fornalha tem a aba de combustível (ícone balde de lava + esmeralda) e uma aba de pedra; o defumador e o
     alto-forno **não** têm aba de combustível.
   - Ícone da aba de combustível: **só o balde de lava** (tirar a esmeralda), nas três.
   - Alto-forno: aba de combustível + aba de pedra/minério (como a da fornalha).
   - Defumador: mantém a aba da costela de porco + aba de combustível.
2. **Ícone único de armadura = peitoral de ferro** (o da bigorna) em todas as estações, inclusive no ferreiro (hoje
   usa peitoral de diamante).
3. **Ferreiro (smithing table):** abas Enfeites (moldes) / **Armaduras** / **Ferramentas e armas** (ícone igual ao da
   aba da bancada: machado + espada, print 6) / Materiais (lingote de netherite). Motivo: subir ferramenta/arma de
   diamante para netherite sem precisar levá-la na mão.
4. **Mesa de encantamento:** abas **Armaduras / Ferramentas / Armas / Livros**. **Lápis-lazúli automático**: se há
   lápis ao alcance (raio), o servidor abastece o slot de lápis sozinho, sem aba nem clique. Deixa de existir a aba de
   lápis.
5. **Bigorna:** abas **Armaduras / Ferramentas / Armas / Livros / Materiais (minérios)**. Ao colocar um item no
   primeiro slot (ex.: espada), a aba Livros mostra **só os livros encantados com encantamento aplicável àquele item**;
   vale para todo item. Sem item no slot, mostra todos os livros.
6. **Sinalizador (beacon) — estação nova:** o pagamento (minério/lingote/esmeralda/diamante) vira **botões com o
   ícone de cada item pagável** ao lado do slot de pagamento; ao clicar, o servidor puxa 1 daquele item do
   armazenamento ao alcance e coloca no slot. Item que não existe no raio: botão desativado/vermelho, com tooltip.
7. **Suporte de poções — melhoria grande:**
   - **Não mostrar a contagem no canto dos itens no painel** (o "1.6k", "63" do print). Confirme com o Eliel em balão
     se isso vale para **todas** as estações (a frase dele foi "assim como todas as outras bancadas") — a contagem
     continua no tooltip "No baú: N".
   - Na posição do frasco de água: listar **todas as poções possíveis** com o que o jogador tem (baús ao alcance +
     inventário): parte das garrafas de água disponíveis e percorre as receitas de poção do jogo (incluindo cadeias:
     água → estranha → força → força II/longa), marcando em vermelho a que falta ingrediente. Ao clicar numa poção,
     o servidor monta o **próximo passo** (garrafas + ingrediente + pó de blaze se faltar combustível). Proponha o
     comportamento exato de cadeias de vários passos em balão antes de implementar.

## Regras

- Lógica em `common/`; API frágil do MC (receitas de poção, encantamentos aplicáveis, menus de beacon/encantamento)
  só em `compat/mc/` (BenchCompat/StationRecipes).
- **Toda função nova** (lápis automático, pagamento do sinalizador, lista de poções, filtro de livros por item) entra
  no enum `config/Feature` com liga/desliga + cadeado na tela de config (`stashlink.feature.<id>` e `.tip` nos dois
  idiomas) e passa por `FeatureGate` no servidor.
- Servidor valida tudo: distância/raio, menu aberto é mesmo a estação, slot correto, item pagável/aplicável, anti-flood
  (`BenchFlood`), devolução ao baú no fechamento (`BenchLedger`). Abastecimento automático de lápis **não pode
  duplicar nem sumir** item: teste de dupe/fechar tela/sair do servidor no meio. Isso é integridade de item → revisão
  em **Opus/high**.
- Nenhuma textura nova: só ícones de item e sprites/widgets do jogo; compatível com resource pack. Textos em chaves
  `stashlink.bench.*` em pt_br e en_us, nada hard-coded. Medidas em constantes. Ordem pela `BenchOrder`.
- Cada mudança com teste (GameTest para servidor, unitário para lógica pura).

## Fluxo

1. Mapear (grafo/subagente). Para cada pedido, dar custo/risco em 1–2 linhas e **perguntar em balão
   (AskUserQuestion, recomendada primeiro)**: ordem de execução em ondas, o que fica para depois, as dúvidas dos itens
   5, 6 e 7. Sugestão de ondas: (1) abas e ícones — itens 1, 2, 3; (2) encantamento + bigorna — itens 4, 5;
   (3) sinalizador — item 6; (4) poções — item 7. Pode ser um PR por onda.
2. Implementar o aprovado, testes, jar: `./gradlew :fabric:build -x test -x runGameTest`; copiar para
   `%APPDATA%\PrismLauncher\instances\{26.3,Fabulously Optimized}\minecraft\mods\` só com o jogo FECHADO
   (`tasklist | grep javaw`), conferir com `cmp`.
3. Roteiro de teste por estação para o Eliel e pedir prints.
4. Depois de aprovado: commit com `git add <caminhos>` explícitos, `docs/ARCHITECTURE.md` (seção "Item 16.3"),
   `docs/ROADMAP.md` ✅ e `docs/roadmap.html` (`s:"done"`), republicar o artifact "StashLink Roadmap"
   (https://claude.ai/artifact/DoHRdusXQJgZcMcqhqs1sr), PR com squash.
5. Handoff: resumo em linguagem simples do que foi feito e por quê (incluindo bugs achados) + prompt do próximo item
   do roadmap (Item 15 — conduíte, ou Item 22 — release, conforme `docs/ROADMAP.md`).

## Roteamento

Mapeamento em subagente Sonnet com retorno curto; implementação de UI/abas em Sonnet/medium; lápis automático,
pagamento do sinalizador e montagem de poções (mexem em item do servidor) com revisão Opus/high — gatilho:
integridade de item. Relatar: `Roteamento: … | gatilho Opus: …`.
