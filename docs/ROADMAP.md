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
- **Correção (fix/litematica-mixin-package):** o primeiro build derrubava o jogo ao usar o Easy Place/pick block do
  Litematica 0.29.0 (`IllegalClassLoadError`): o Mixin proíbe chamar direto uma classe do pacote que ele possui, e o
  `InventoryUtilsMixin` chamava `LitematicaPull` no mesmo pacote. Os mixins agora ficam em `compat/litematica/mixin`.

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
  (dependência opcional) e botão "Config" nativo no NeoForge. Em servidor remoto a tela ficava somente leitura (superado pelo Item 10.1). A tela também abre pela tecla **K**
  (configurável; `ConfigKey`), sem precisar de Mod Menu.
  `/stashlink radius [n]` e `/stashlink reload`, só operadores. "Incluir hotbar" na tecla N **não** foi feito.
  Testado em jogo (tela, tecla K, comandos): ok.

### Item 10.1 — Preferências por jogador, para Realms/servidores sem comandos ✅
- **Branch:** `feat/preferencias-jogador`
- Problema (achado por um amigo no Realms): em servidor a tela ficava somente leitura e mandava usar
  `/stashlink` ou o `stashlink.json`, mas num Realms com comandos desligados nenhum dos dois existe.
- **Feito:** em servidor/Realms a tela edita as **preferências pessoais** do jogador (`config/stashlink-client.json`):
  raio, "usar baús e barris" (padrão do servidor / sim / não) e slots travados. O cliente manda um pacote
  (`PlayerPrefsRequest`) ao entrar no servidor e ao fechar a tela. O servidor (`PlayerPrefsStore`) **corrige e
  limita tudo** (`PlayerPrefs.sanitized`): raio nunca passa de `maxRadius`, slots só 0-35. Quem não manda nada (ou
  não tem o mod) usa a config do servidor; os slots travados do servidor valem para todos, somados aos do jogador.
  `/stashlink` e `stashlink.json` continuam como antes (padrão para quem não personalizou). Mundo local não muda.
  O estado fica só em memória no servidor (o cliente reenvia a cada entrada).

### Item 10.2 — Modo cliente: funcionar sem o mod no servidor (Realms) ✅
- **Branch:** `feat/modo-cliente`
- Quando o servidor não conhece o StashLink, o cliente faz o trabalho sozinho, como um jogador: abre containers no alcance normal do jogo, move itens por cliques de inventário e fecha. Tecla W e tecla N com containers no alcance normal do jogo (reabastecer a mão ficou para o Item 10.3).
- **Pronto quando:** num Realms (ou servidor vanilla) com o mod só no cliente, W e N funcionam sem nada duplicar ou sumir; com o mod no servidor, o comportamento atual não muda.
- **Feito:** o motor do modo cliente (`ClientMode.active()` = servidor remoto sem o mod + opção ligada) usa só o que um
  jogador comum faz: abrir o container no alcance de interação do jogo (~4,5 blocos), mover itens por **cliques de
  inventário** e fechar. O servidor vanilla valida cada clique (distância, permissão, claims), como no Litematica;
  por isso nada burla regra do servidor. O mod confere o resultado de cada clique antes do próximo, então não
  duplica nem perde item. Com o mod no servidor nada muda (continua tudo server-side). O modo cliente nunca manda
  pacote próprio a servidor que não conhece o mod. Config: opção `clientModeEnabled` (padrão ligado) em
  `stashlink-client.json`, botão "Modo cliente: ligado/desligado" na tela (em servidor remoto). Quando o modo está
  ativo a tela mostra um aviso e esconde o que não se aplica (raio do servidor e "usar baús e barris"); os slots
  travados continuam valendo, usados localmente. Mensagens novas `stashlink.client_mode.*` em en_us/pt_br.
  Limites e decisão de design em `docs/ARCHITECTURE.md` e no README.
- **Não testado em jogo ainda.** Roteiro para o Eliel (servidor vanilla local **ou** Realms com o mod só no cliente):
  1. **W:** abrir um baú com itens e apertar W; tudo o que cabe vem para o inventário. Testar também com o inventário
     quase cheio (o resto deve ficar no baú).
  2. **N:** colocar 2-3 baús perto, cada um já com algum item que você carrega; apertar N; conferir que os itens vão
     só para o baú que já tinha aquele item.
  3. **Conferir:** somar a quantidade de itens antes e depois: nada duplicou, nada sumiu.
  4. **Cancelar no meio** (andar para longe, fechar a tela, sair do alcance): nenhum container deve ficar aberto.
  5. **Com mod no servidor** (mundo local ou servidor com StashLink): W e N se comportam como antes.

### Item 10.3 — Modo cliente: reabastecer a mão ✅
- **Branch:** `feat/modo-cliente-reabastecer`
- Dividido do 10.2 por ser a parte mais arriscada. Sem o mod no servidor, detectar a mão principal que ficou vazia e
  reabastecer a partir de container/shulker **colocados** perto: cache em memória "posição → conteúdo visto"
  (preenchido sempre que o jogador abre um container), tentar o mais provável primeiro, depois varrer; abrir,
  mover o item e fechar. Shulker no inventário fica fora (não dá para abrir sem colocá-la).
- **Pronto quando:** num Realms/servidor vanilla com o mod só no cliente, esvaziar a mão com um baú/shulker colocado
  perto reabastece sem duplicar nem perder item.
- **Feito:** a cada tick do cliente (só em modo cliente) o `HandWatcher` vigia a mão principal. Mão com o último
  item que ficou vazia = esgotou, e o motor cria um `RefillJob` (um por vez, com pausa de 10 ticks depois do
  anterior; não compete com W/N em andamento). Não age com tela aberta, agachado, criativo/espectador, morto, item
  no cursor, Q apertada (no tick ou no anterior), troca de slot da hotbar ou troca de mão com F. Candidatos = os
  containers do alcance de interação (como a N), ordenados pelo `ContentsCache`: tem o item (0), nunca visto (1),
  visto sem o item (2), depois mais perto; no máximo 6 por reabastecimento. Em cada um: abre, acha o slot com o
  mesmo item e componentes (**desgaste ignorado**, como o `RefillLogic` do servidor; o stack maior primeiro) e o
  leva à mão com **um clique SWAP** com o slot da hotbar selecionado. A cada tick lê o menu: mão com o item =
  sucesso; sem confirmação em 4 ticks = slot recusado, tenta outro; mão com **outro** item (jogador colocou algo)
  ou hotbar trocada = aborta sem clicar. Sempre fecha o container (mesma `ContainerSession` do 10.2). Sem a troca
  balde/tigela/garrafa do modo servidor: só repõe o mesmo item, com a mão vazia. Sem nenhum container ao alcance
  fica quieto; abriu e não achou mostra "Nada para reabastecer por perto". Lógica pura em `ClientMoveLogic`
  (15 testes novos em `ClientRefillLogicTest`); API do MC só em `ClientCompat`.
- **Por que SWAP:** com a hotbar vazia o stack inteiro vai para a mão em um clique, sem passar pelo cursor (nada
  fica pendurado se falhar) e sem o shift-clique escolher outro destino. O servidor vanilla valida o clique.
- **Não testado em jogo ainda.** Roteiro para o Eliel (servidor vanilla local **ou** Realms, mod só no cliente):
  1. **Baú ao lado:** colocar um baú com pedra (cobblestone) por perto, ficar com 1 pedra na mão, colocá-la; o baú
     abre e fecha sozinho e a mão volta a ter pedra.
  2. **Shulker colocada:** o mesmo, com a pedra numa shulker colocada no chão.
  3. **Baú sem o item:** com só baús sem pedra por perto, esvaziar a mão: eles abrem/fecham, aparece "Nada para
     reabastecer por perto" e nada é retirado nem movido.
  4. **Cancelar no meio:** apertar ESC, trocar de slot ou andar para longe durante a abertura: nenhum container
     fica aberto.
  5. **Conferir:** somar os itens antes e depois: nada duplicou, nada sumiu. Depois da 1ª vez, o mesmo baú deve ser
     o primeiro a abrir (cache).
  6. **Não deve agir:** soltar com Q, trocar com F, criativo, agachado.

### Item 11 — Testes de carga, multiplayer e compat com outros mods ✅
- **Branch:** `test/carga-e-compat`
- Servidor dedicado com 2+ jogadores; verificar corridas entre jogadores no mesmo baú; testar com mods comuns
  (Litematica, Tweakeroo, Carpet, mods de armazenamento como Sophisticated/Iron Chests).
- **Feito:** novo harness de teste com **GameTest do Fabric** em `fabric/src/gametest` (source set separado, fora do
  jar). Roda com `./gradlew :fabric:runGameTest` (~25 s; precisa de internet na 1ª vez para baixar Carpet e Upgraded
  Iron Chests, carregados só nos testes). Servidor real, baús reais e jogadores simulados em **sobrevivência** (o
  mock do vanilla é criativo e o mod ignora criativo). Classes: `Lab.java` e `StashLinkGameTests.java`.
- **14 cenários, todos passam:** 2 jogadores N no mesmo baú; N pula baú aberto por outro e volta a valer ao fechar;
  2 jogadores W no mesmo baú; W parcial com inventário cheio; reabastecer da mão com baú aberto por outro; jogador que
  cai com baú aberto não trava o baú; baú duplo = 1 container; baú com bloco em cima ainda é fonte (limite
  documentado); W respeita slots travados por jogador; 289 containers de carga; **fuzz de corrida** (2 jogadores,
  3000 ações aleatórias N/W/abrir/fechar/shift-clique/pedir item/reabastecer/embaralhar, ~400 moveram itens; soma de
  itens conferida após **cada** ação); Carpet carregado; mod de baús de terceiros inofensivo.
- **Teste de mutação:** injetar de propósito um dupe em `ContainerSource.take` foi pego por 2 testes (fuzz e
  reabastecer), ou seja, os testes realmente enxergam o defeito que deveriam enxergar.
- **Desempenho** (289 containers, raio 16, 100 repetições; média/pior em µs): find 181/1438, findAll 75/231,
  N 297/1770, reabastecer pior caso 226/7833 (pior caso = JIT frio), pedir item ausente 403/2263, tick parado ~0.
  Orçamento do teste: média < 5 ms (10% de um tick de 50 ms). Conclusão: sem risco de queda de TPS; fecha o "sem queda
  de TPS com 200+ containers" do Item 6. (Mede custo por operação, não TPS global.)
- **Bug achado e corrigido:** `LootAllService` (tecla W) usava os slots travados globais
  (`StashLinkConfig::isSlotLocked`) e ignorava os travados por jogador do Item 10.1; agora usa
  `PlayerPrefsStore.isSlotLocked(player, slot)`.
- **Política observada (não é bug):** reabastecer/pedir item podem tirar de baú que outro jogador tem aberto (como um
  funil); só a tecla N pula baú aberto por outro. O servidor roda tudo numa thread, então não há corrida de dados; a
  invariante de soma de itens se mantém.
- **Mods de terceiros:** Carpet 26.3 carregado junto no servidor: tudo passa. Upgraded Iron Chests (6 baús; estendem
  o baú vanilla) são vistos pelo StashLink e nada duplica/some. Sophisticated Storage/Backpacks 26.3 existem só para
  NeoForge: sem harness NeoForge, **não testado**. Litematica/Tweakeroo/MaLiLib (0.29.1/0.30.1/0.30.2) são só
  cliente: Litematica 0.29.1 + MaLiLib 0.30.2 + Tweakeroo 0.30.1 (só cliente) carregam junto do StashLink no menu principal, sem erro de Mixin nos logs (com o fix do crash do Easy Place). NÃO testado em jogo: Easy Place/pick block precisa de mundo e clique humano (roteiro: `./gradlew :fabric:runClient -PcompatMods`, schematic + Tweakeroo easy place, bloco na hotbar e um baú com mais dele; conferir colocação, reabastecer ao esgotar e pick block sem duplicar).
- **Não testado:** Realms/anticheat e timeouts do modo cliente (precisa de jogo real; os roteiros do 10.2/10.3
  continuam valendo) e GameTest no NeoForge.

### Item 12 — Preparar o release (workflow, changelog, README) ✅
- **Branch:** `chore/release-1.0`
- Workflow de release, changelog e README. A publicação em si foi movida para o **Item 22** (último), porque ainda há itens a implementar antes (13–21), decisão do Eliel em 2026-10-03.
- **Feito até aqui:** workflow `release.yml` (tag `v*` → release em rascunho com um jar por loader), `CHANGELOG.md` e README com instalação/testes. Teste em jogo com o mod instalado num servidor próprio: ok (2026-10-03). Modo cliente (servidor sem o mod) e Easy Place com Litematica continuam sem teste em jogo.

## Itens a implementar antes do release (Itens 13–21)

Pedidas pelo Eliel em 2026-10-03, para fazer em outros chats, um item por vez. Cada item começa com uma investigação
curta (como o Sophisticated Storage / o jogo base resolvem) registrada em `docs/ARCHITECTURE.md`.

### Item 13 — Slot de baú travado com um item (pré-visualização) ✅
- **Branch:** `feat/slot-travado-item`
- Ideia: o jogador **trava um slot de um baú com um item específico**. O slot passa a mostrar só uma **prévia
  (fantasma)** daquele item, e o baú "guarda na memória" que aquele slot é daquele item. É o que o Sophisticated
  Storage já faz. Serve para organizar: o slot fica reservado para aquele item.
- A decidir na investigação: o que a prévia faz (só mostra? bloqueia outros itens naquele slot? a tecla N e o
  guardar passam a preferir o slot reservado?), onde a memória fica guardada (dados do baú, para sobreviver a
  reiniciar) e como se destrava. Regras de ouro: toda mudança é **server-side** e validada (distância, permissão, baú
  não aberto por outro jogador); a prévia **não pode virar item real** (nenhum dupe ao clicar, ao shift-clicar, com
  funil ou com W); baú duplo, barril e shulker precisam funcionar; quem não tem o mod no cliente não pode ver item
  falso nem perder item.
- **Pronto quando:** travar/destravar um slot funciona, a prévia aparece, nada duplica nem some (testes no harness
  `:fabric:runGameTest`, incluindo 2 jogadores no mesmo baú) e N/W respeitam o slot reservado.
- **Feito (decisões em `docs/ARCHITECTURE.md`, "Item 13"):** decisões do Eliel (2026-10-03): o slot travado **reserva
  de verdade** (só aceita o item dele) e o gesto é **Alt + clique** (num slot com item trava para ele; num slot vazio
  com item no cursor reserva para o item do cursor; num slot já travado destrava). A prévia é **só metadado**: o baú
  guarda `slot → item` no próprio NBT do bloco (um mixin em `BaseContainerBlockEntity`; sobrevive a reiniciar e some
  com o bloco quebrado) e o slot continua vazio de verdade, então não existe item falso para duplicar. `Slot.mayPlace`
  (clique, shift-clique, troca por número, arrastar) e `ContainerInsert` (N, devolução do Litematica) recusam outro
  item; N prefere o slot reservado e alimenta até um baú que só tem a reserva. W só tira itens (a reserva continua).
  Servidor valida tudo (menu aberto, distância, claims, baú não aberto por outro jogador); o cliente com o mod recebe a
  lista de travas e desenha a prévia (item esmaecido + pontinho azul no canto); cliente sem o mod vê o slot vazio e nunca recebe o
  pacote. Baú duplo (uma trava por metade), barril e shulker funcionam.
- **Testes:** 14 GameTests novos (`LockGameTests`, 36 no total) com cliques reais no menu: travar/destravar, recusa
  por clique/troca/shift, prévia nunca vira item, N e W, baú duplo, barril, shulker, gravar e recarregar o bloco,
  quebrar o baú, 2 jogadores no mesmo baú, pacotes e um fuzz de 3000 ações com 2 jogadores (soma de itens conferida após
  cada ação). **Mutação:** 4 defeitos injetados e pegos (trava ignorada no `mayPlace`, dupe no `insert`, N ignorando a
  reserva, prévia virando item). **Limites:** funil e mods que mexem direto no container não respeitam a reserva (nada
  se perde); shift-clique de fora não prefere o slot reservado; só o tipo do item é guardado.
- **Não testado em jogo ainda (cliente gráfico).** Roteiro para o Eliel (Fabric, `./gradlew :fabric:runClient`, ou o
  servidor próprio com o mod no servidor **e** no cliente; modo Sobrevivência):
  1. Num baú, ponha 10 pedras no slot 4. Segure **Alt** e clique no slot: aparece "Slot reservado para Pedra" e o
     slot ganha um **pontinho azul** no canto. O clique **não** pode pegar a pedra.
  2. Pegue as pedras (clique normal ou W). O slot fica vazio, mas mostra a **pedra esmaecida** (a prévia) com a moldura.
  3. Tente colocar terra nesse slot (clique, shift-clique do inventário e tecla numérica): **não entra**. Coloque
     pedra: entra. A prévia some enquanto há pedra e volta ao esvaziar.
  4. Feche e reabra o baú: a reserva continua. **Reinicie o mundo/servidor** e reabra: continua.
  5. Com o baú fechado, aperte **N** com pedras na mochila: elas vão para o slot 4 (mesmo se houver outro baú mais
     perto que também tenha pedra). Num baú **vazio** com só uma reserva de terra, N com terra na mochila leva a terra
     para lá.
  6. Aperte **W** com o slot reservado cheio: leva tudo e a reserva continua.
  7. **Alt + clique** de novo no slot: "Slot destravado", a prévia some e o slot aceita qualquer item.
  8. **Baú duplo:** reserve um slot da metade esquerda e um da direita; as duas valem. Repita num barril e numa
     shulker colocada.
  9. **2 jogadores:** com os dois no mesmo baú, Alt + clique não muda nada ("Outro jogador está com este container
     aberto"); depois que um sai, o outro consegue.
  10. **Sem o mod no cliente** (vanilla entrando no servidor com o mod): o slot reservado aparece **vazio**, sem item
      falso; nada some. **Sem o mod no servidor:** Alt + clique faz o clique normal do jogo.
  11. Somar os itens antes e depois: nada duplicou, nada sumiu.

### Item 14 — Nome do sistema de armazenamento, com resumo do conteúdo e emojis ✅
- **Branch:** `feat/nome-armazenamento`
- Ideia: dar um **nome ao baú/sistema de armazenamento** e poder **escrever ali quais itens ele tem, sem precisar
  abrir**. O nome aceita **emojis**.
- A decidir na investigação: onde o nome aparece (ao olhar para o baú, em placa/holograma, na tela do baú); se vale
  o nome padrão do jogo (baú renomeado na bigorna) ou um nome do mod; como "sistema" agrupa vários baús; se o resumo
  é digitado à mão ou gerado do conteúdo.
- **Risco dos emojis:** a fonte padrão do Minecraft não tem a maioria dos emojis. Opções: usar só os símbolos que a
  fonte já tem, ou incluir uma fonte/resource pack próprio com os emojis. Também checar o limite de tamanho do nome e
  que o servidor valide e limite o texto (sem texto gigante, sem formatação maliciosa); em servidor sem o mod o nome
  precisa degradar bem.
- **Pronto quando:** dá para nomear, ver o nome/resumo sem abrir o baú, com emojis que aparecem de verdade no jogo, e
  isso persiste ao reiniciar o servidor.
- **Feito e confirmado em jogo pelo Eliel (decisões em `docs/ARCHITECTURE.md`, "Item 14"):** o nome do baú aparece num
  **holograma** (texto flutuante, metade do tamanho) **na frente do bloco**, visto só a **até 32 blocos** (decidido pelo
  servidor) e sem ser cortado por baú empilhado nem pela quina do baú (sem teste de profundidade; aparece também através
  de paredes). Na tela do baú há um **lápis ✎** ao lado do título: clicar abre um **campo único** e o botão vira ✔;
  clicar no ✔ (ou Enter, ou fechar a tela) grava, e o nome fica como texto comum ao lado de "Baú". A **tecla J**
  (olhando para o bloco) abre um editor equivalente. Emojis = **símbolos que a fonte já tem (❤ ⭐ ⚡ ✔ ⚔ ⛏...) + ícones
  de item/bloco inline** (`:apple:`, `:oak_log:`), porque a fonte padrão **não tem** 📦🔥🍎 (conferido no unifont do
  26.3). Até 48 caracteres. Baú duplo = um só rótulo. **Baú e barril perdem o rótulo ao quebrar; shulker leva no item;
  baú do End guarda por posição.** O holograma nunca vai para o disco. O servidor limpa o texto (sem `§`,
  invisíveis/direção, emoji sem glifo) e revalida alcance, tipo de bloco e claims. Cliente sem o mod num servidor com
  o mod vê o holograma, mas não edita; servidor sem o mod: nada muda.
- **Roteiro manual (cliente gráfico; o harness não abre janela):** (1) abra um baú: ao lado do título há um **lápis ✎**; clique nele (ou olhe para o baú e aperte **J**): aparecem dois campos acima da tela;
  digite `Pedras :cobblestone:` e `tudo de construção ❤`, **Concluído** → um texto aparece sobre o baú, com o ícone da
  pedra e o ❤ desenhados (não quadradinhos); (2) o texto fica na frente do baú, perto da face (empilhe outro baú por cima: o texto continua visível); afaste-se ~35 blocos: o texto some, volte: aparece; (3) baú duplo: um
  só texto no meio; (4) quebre o baú → o texto some; coloque outro → sem texto; (5) shulker: nomeie, quebre, pegue o
  item, coloque → o nome volta; baú do End: nomeie, quebre, recoloque no mesmo lugar → volta; (6) saia e entre no
  mundo / reinicie o servidor: o texto continua; (7) digite um emoji colorido (📦) → ele é descartado ao salvar;
  (8) num servidor **sem** o mod, J só mostra o aviso.

### Item 15 — Raio de até 128 blocos com conduíte (conduit) perto do estoque ⬜
- **Branch:** `feat/raio-128-conduite`
- Ideia: hoje o teto do raio é 64 (`HARD_MAX_RADIUS`). Passa a ser possível **chegar a 128 blocos**, desde que haja um
  **conduíte ativo instalado perto do estoque**. Sem conduíte, continua o teto de 64.
- Confirmado pelo Eliel: é o **conduit** do Minecraft, o bloco que se instala debaixo d'água. Ele só fica **ativo** dentro da
  água, cercado pela estrutura de prismarina (mínimo 16 blocos), então o estoque precisa de um conduíte montado de verdade.
- A decidir na investigação: o que é "perto" (distância do conduíte ao container ou ao jogador) e se o conduíte
  precisa estar ativo (com a estrutura de prismarina completa) ou basta existir; se o raio maior vale por container
  (só os que estão perto de um conduíte) ou para o jogador inteiro. Tudo no servidor, com o teto configurável
  (`maxRadius`) e o custo de varredura medido de novo no harness (chunks descarregados continuam fora; nunca forçar
  carregar chunk).
- **Pronto quando:** com conduíte perto o raio sobe até 128, sem ele fica em 64, e a medição com 289+ containers segue
  bem abaixo de 5 ms por operação.

### Item 16 — Bancadas usam o armazenamento como inventário ⬜
- **Branch:** `feat/bancada-com-armazenamento`
- Ideia: a bancada passa a enxergar os **containers próximos como se fossem o inventário do jogador**, para craftar
  sem carregar os materiais. **Raio inicial de 64 blocos**, podendo subir com o Item 15 (conduíte, até 128).
- **Outras bancadas também (ideia do Eliel, 2026-10-03):** o mesmo vale, com o **mesmo raio e as mesmas fontes**, para
  as demais estações: fornalha, defumador (smoker), alto-forno, mesa de ferreiro, cortador de pedra (stonecutter),
  tear, mesa de cartografia, pedra de amolar, bigorna, mesa de encantamento e suporte de poções. A investigação lista
  quais menus dá para ligar com a mesma peça de código (o menu tem entrada e saída, o resto muda só a receita) e quais
  ficam de fora por serem especiais (ex.: bigorna e encantamento gastam XP/lápis; poções e fornalha têm tempo). Vale
  fazer a base na bancada comum e ir ligando as outras sobre ela, sem copiar código.
- **Regra: o inventário interno das estações NÃO é armazenamento.** Os slots de fornalha, defumador, alto-forno,
  suporte de poções e das próprias bancadas (grade, entrada/saída) **nunca** viram fonte nem destino do mod:
  reabastecer, tecla N, tecla W e a nova função de craftar **não pegam nem colocam item ali**. Hoje já é assim por
  construção (`NearbyContainers` só aceita `RandomizableContainerBlockEntity`: baú, barril, shulker), mas **falta
  garantir com teste** e manter assim quando entrarem mais tipos de bloco (funil, dispenser e dropper também ficam de
  fora, a decidir na investigação). Regra de ouro: item que o jogador deixou cozinhando/processando nunca é mexido.
- A decidir na investigação: como ligar isso ao menu da bancada (como o Sophisticated Storage / outros mods fazem),
  quais fontes entram (as mesmas do reabastecimento, `PlayerSources`) e como sincronizar o resultado com o cliente
  sem item fantasma. Regras de ouro: tirar item dos containers é **sempre server-side** e validado (distância, claim,
  baú não aberto por outro jogador); nada duplica nem some ao craftar, ao shift-clicar o resultado ou com 2 jogadores.
- **Pronto quando:** craftar um item usando só materiais que estão nos baús ao redor funciona, nada duplica nem some
  (testes no harness com 2 jogadores) e respeita o raio; as outras estações listadas funcionam do mesmo jeito (ou
  ficam registradas como "de fora" com o motivo); e um teste prova que fornalha/suporte de poções/estações com item
  dentro **nunca** são tocadas por reabastecer, N, W nem craftar.

### Item 17 — Escolher o que cada baú recebe com a tecla N ⬜
- **Branch:** `feat/filtro-tecla-n`
- Ideia: **dentro do baú**, um botão liga/desliga **"recebe itens com a tecla N"**. Ligado, a N guarda ali; desligado,
  a N nunca coloca nada nele (para não encher o baú de armadura, por exemplo).
- Na **tela de configuração**, categorias de item que a N guarda ou não, cada uma com botão e emoji: 🛡️ **Armadura**
  (ligado guarda armadura, desligado não guarda), e também ferramentas, armas, comida, poções etc. **Blocos comuns
  ficam de fora** (não precisam de filtro).
- A decidir na investigação: onde fica a memória do botão do baú (dados do baú, para sobreviver a reiniciar) e como
  mostrá-lo na tela do baú sem quebrar quem não tem o mod; como classificar os itens por categoria (tags do jogo) e
  como isso se combina com "o baú já tem o item" da N; emoji na fonte padrão (ver Item 14). Valores validados no
  servidor, por jogador (como o `PlayerPrefs`), com o padrão do servidor para quem não personalizou.
- **Pronto quando:** baú com N desligada nunca recebe nada; armadura com a categoria desligada nunca é guardada pela
  N; as escolhas persistem; testes no harness (inclusive baú duplo e 2 jogadores).

### Item 18 — Litematica: trocar o bloco no mesmo slot e devolver o anterior ao armazenamento ✅
- **Branch:** `feat/litematica-troca-no-slot`
- Problema (achado pelo Eliel testando em jogo, 2026-10-03): construindo com Litematica, apareceu uma laje de pinheiro
  uma única vez; o mod puxou o item para a mão, e como ela não foi usada nos próximos ~30 blocos, **a hotbar foi
  enchendo** de itens puxados. Hoje cada pedido (`PullItemService` / `PullLogic.pullIntoHotbar`) acha um slot da hotbar
  e seleciona ele, sem devolver nada.
- Ideia: os blocos se **trocam no mesmo slot** conforme a schematic pede. Quando um novo bloco é puxado, o que ficou
  de antes **volta para o armazenamento de onde veio** (shulker do inventário ou container do raio), e o novo ocupa o
  mesmo slot.
- A decidir na investigação: como lembrar de onde cada item veio (origem por pedido; o `ContainerSource` já devolve
  só ao que ele mesmo tocou); o que fazer se a origem está cheia ou fora do raio (cair no inventário, nunca no chão,
  nunca perder item); se vale só para o slot que o mod escolheu (nunca mexer em item que o jogador colocou ali);
  quando devolver (ao pedir outro item, ao trocar de slot, ao fechar o Litematica); e se o mesmo vale para o modo
  cliente (mover por cliques de inventário). Server-side e validado, como o resto.
- **Feito (decisões em `docs/ARCHITECTURE.md`, "Item 18"):** o servidor guarda, por jogador, **qual slot da hotbar é do
  mod** (`PulledSlot`: slot, item, quantos o mod pôs e de onde vieram). No pedido seguinte, se a schematic pede
  outro bloco, o item antigo é devolvido e o novo ocupa **o mesmo slot**. Ordem da devolução: shulker do
  inventário ou container de origem (só se ainda está no raio, liberado e sem outro jogador com ele aberto) → mochila
  → se nada aceitar, o item **fica** no slot (e o novo vai para outro slot, como antes): nunca no chão, nunca perdido.
  Só mexe no que o mod pôs: slot trocado pelo jogador, item do jogador e slot travado nunca são tocados. Devolve só
  **ao pedir outro bloco** (decisão do Eliel, 2026-10-03): sobra no máximo 1 stack do mod na hotbar ao terminar.
  O modo cliente não foi alterado: ele não puxa para a hotbar (só W, N e reabastecer), então não há o que trocar.
- **Testes:** 8 unitários (`PullSwapTest`, incluindo 31 pedidos de tipos diferentes) e 8 GameTests novos
  (`SwapGameTests`, 22 no total): 35 pedidos de 31 tipos mantêm a hotbar com 1 slot e cada tipo volta ao baú de onde
  veio; origem cheia; origem fora do raio; baú quebrado; mochila e origem cheias; slot do jogador intocado; 2
  jogadores no mesmo baú (com baú aberto por outro); fuzz de 300 passos com 2 jogadores (soma de itens conferida
  depois de cada ação). **Mutação:** dupe injetado (não esvaziar o slot ao devolver) pego por 5 unitários + 7
  GameTests; perda injetada (mochila "aceita" e descarta) pega por 2 + 5; ignorar "baú aberto por outro" pego pelo
  teste de 2 jogadores.
- **Não testado em jogo ainda.** Roteiro para o Eliel (Fabric, `./gradlew :fabric:runClient -PcompatMods`, ou o
  servidor próprio com o mod; Litematica 0.29.1 + MaLiLib 0.30.2 + Tweakeroo 0.30.1; modo Sobrevivência):
  1. Guardar numa shulker no inventário (ou em baús perto) pelo menos 30 tipos de bloco, 64 de cada, e carregar
     uma schematic que use todos. Hotbar vazia.
  2. Construir com Easy Place percorrendo a schematic: a cada tipo novo, o bloco antigo some do slot e o novo ocupa
     **o mesmo slot**. A hotbar nunca passa de 1 slot do mod (mais o que você mesmo pôs).
  3. Conferir de onde veio: abrir a shulker/baú e ver que o que sobrou do bloco anterior voltou para lá.
  4. Encher o baú de origem (ou ficar fora do raio) e pedir outro bloco: o antigo vai para a mochila, nada no chão.
  5. Pôr outro item seu (picareta) no slot do mod e pedir um bloco novo: a picareta não pode ser tocada.
  6. Somar os itens antes e depois (descontando o que foi colocado): nada duplicou, nada sumiu.
- **Pronto quando:** construir 30+ blocos de tipos diferentes mantém a hotbar limpa, o item anterior volta para onde
  estava, nada duplica nem some (testes no harness `:fabric:runGameTest`, incluindo origem cheia e 2 jogadores) e
  confirmado em jogo com Litematica.

### Item 18.1 — Funções com liga/desliga e cadeado, como o seletor de dificuldade ✅
- **Branch:** `feat/funcoes-liga-trava`
- Pedido do Eliel (2026-10-03): **toda função do mod** tem um botão liga/desliga na tela de config, igual ao seletor de
  dificuldade do jogo, com um **cadeado** ao lado. Trancada (cadeado fechado), a função **não funciona naquele
  servidor**, para quem quer usar o mod mas acha alguma função "roubada" e fecha por dentro do jogo.
- **Feito (decisões em `docs/ARCHITECTURE.md`, "Item 18.1"):** enum `Feature` (reabastecer a mão, N, W, Litematica,
  Alt + clique). Dois níveis: o **liga/desliga é pessoal** (`stashlink-client.json`, vai ao servidor junto das
  preferências) e o **cadeado é do servidor** (`lockedFeatures` em `stashlink.json`). Só vale se **não** estiver
  trancada **e** o jogador não a desligou. O servidor confere em cada serviço (`FeatureGate`); o cliente repete a conta
  só para não mandar pedido à toa e avisar o motivo na barra de ação. Quem tranca: o dono do mundo ou um operador,
  pela tela (pacote conferido no servidor) ou por `/stashlink feature <nome> lock|unlock`; a mudança é gravada e avisada
  a todos. Botão de função trancada fica desligado, como a dificuldade travada. Sem o mod no servidor (modo cliente)
  não há cadeado: vale só o liga/desliga. Função de slot reservado trancada: as reservas ficam guardadas no baú mas
  deixam de valer (e voltam quando o cadeado abre).
- **Regra daqui para frente:** toda função nova (Itens 14-17, 19...) entra no enum `Feature`, ganha linha na tela
  (idioma `stashlink.feature.<id>` e `.tip`) e passa por `FeatureGate` no servidor.
- **Testes:** 8 unitários (`FeatureTest`) e 7 GameTests (`FeatureGameTests`, 54 no total): cada função respeita o
  cadeado e o desligar pessoal; só o dono/operador tranca; reserva existente deixa de valer trancada. A tela **não foi
  vista em jogo** ainda.
- **Pronto quando:** cada função tem liga/desliga + cadeado na tela, trancada não funciona no servidor, e confirmado em
  jogo (tela, cadeado por operador num servidor com 2 jogadores, comando).

### Item 19 — Botão do meio do mouse puxa o item do armazenamento para a hotbar ⬜
- **Branch:** `feat/pick-block-armazenamento`
- Ideia (Eliel, 2026-10-03): ao clicar com o **botão do meio (scroll) do mouse mirando um bloco**, se o item daquele
  bloco estiver guardado em algum container **dentro do raio do jogador**, ele **vai para a hotbar**. Exige **um slot
  livre na hotbar**; sem slot livre não faz nada (avisa, e nunca sobrescreve nem troca item que o jogador tem).
- A decidir na investigação: reaproveitar o pedido que o Litematica já usa (`PullItemService`/`PullLogic`, pacote ao
  servidor) em vez de criar outro caminho; como o jogo base trata o pick block em sobrevivência (só pega do
  inventário) e como não brigar com ele (se o item já está na hotbar, o jogo base seleciona; só puxamos quando não
  está); se vale também para o item em shulker no inventário; Litematica ligado (pick block da preview continua
  sendo dele); mirar em baú ou bloco que dá outro item (usa o que o jogo base escolheria). Server-side e validado
  (distância, claim, baú não aberto por outro), sem dupe; modo cliente (sem o mod no servidor) a decidir.
- **Pronto quando:** mirar um bloco, apertar o botão do meio e o item chega à hotbar vindo de um baú no raio; com a
  hotbar cheia nada acontece e nada some; fora do raio não puxa; testes no harness `:fabric:runGameTest`, incluindo 2
  jogadores.

### Item 20 — Organizar os itens do sistema de armazenamento ⬜
- **Branch:** `feat/organizar-armazenamento`
- Pedido do Eliel (2026-10-03): uma forma de organizar os itens dentro do sistema de armazenamento que seja **boa e
  fácil de mexer**. Proposta de desenho, em 3 camadas, da mais simples para a mais completa (cada uma já é útil sozinha;
  dá para entregar em partes, 20.1 → 20.3):
  1. **20.1 — Organizar um baú (1 clique).** Botão "Organizar" na tela do baú/barril/shulker (e tecla opcional):
     junta stacks parciais e ordena por categoria (blocos, ferramentas, comida, poções...) e depois por nome. Só
     reordena dentro do mesmo container; é o gesto que todo jogador já conhece. Respeita slots travados (Item 13).
  2. **20.2 — Organizar o sistema (com prévia e desfazer).** Botão/tecla abre uma tela que mostra **o que o mod vai
     mover** (ex.: "128 pedregulho: baú A → baú B") **antes** de mexer, com botões *Aplicar* e *Desfazer*. A regra de
     destino é a mesma da tecla N: o item vai para o baú que já tem mais dele; item sem casa vai para um baú com
     espaço. Respeita o filtro "recebe com N" (Item 17), os slots travados (13) e o nome/categoria do baú (14).
     Nunca toca em fornalha/estações (regra do Item 16), nem em baú aberto por outro jogador.
  3. **20.3 — Busca e destaque.** Caixa de busca na tela do sistema: digitou "ferro", lista os baús que têm ferro e
     quanto; um clique **destaca o baú no mundo** (contorno) para você achar.
- A decidir na investigação: onde fica a tela do sistema (tecla própria? aba na tela de config?); como agrupar por
  categoria (tags do jogo, as mesmas do Item 17); onde guardar o histórico do "desfazer" (só na sessão, no servidor,
  por jogador); limite de movimentos por clique para não travar o tick (dividir em vários ticks, como o modo cliente).
  Server-side e validado (distância, claim); a prévia é só um plano, nada se move até o *Aplicar*.
- **Pronto quando:** organizar um baú e o sistema inteiro funciona com prévia e desfazer; nada duplica nem some
  (soma de itens conferida antes e depois, como no fuzz do Item 11, incluindo 2 jogadores); desfazer devolve tudo
  ao lugar; a busca acha e destaca o baú; sem queda de TPS com 289+ containers.

### Item 21 — Shift + passar o mouse coleta os itens do baú ⬜
- **Branch:** `feat/shift-passar-coleta`
- Pedido do Eliel (2026-10-03): dentro da tela de um baú, **segurar Shift e passar o mouse por cima dos itens já vai
  coletando**, sem precisar clicar item por item. (É o gesto "arrastar com Shift" de mods como o Mouse Tweaks.)
- Como funciona: com Shift pressionado, cada slot com item por onde o mouse passa recebe **um shift-clique**, uma vez
  só por passagem. Usa os cliques normais de inventário, então **funciona em servidor sem o mod** (como o modo
  cliente do Item 10.2). Vale nos dois sentidos (baú → mochila e mochila → baú), em baú, barril, shulker e ender chest.
- A decidir na investigação: limite de cliques por tick (para o anticheat não achar que é bot e para não encher o
  buffer de pacotes); **não** disparar em slots de resultado (bancada, fornalha, bigorna), senão o hover vai craftar
  sem parar; compatibilidade com o Mouse Tweaks e com a tecla W do Item 9 (W continua o "puxar tudo"); opção na tela
  de config para ligar/desligar (padrão: ligado); respeitar slots travados; parar se a mochila encher; não agir no
  criativo.
- **Pronto quando:** Shift + passar o mouse coleta os itens por onde passou, e só esses; mochila cheia para sem perder
  item; nada duplica nem some (conferir a soma antes e depois, também com latência alta); slots de resultado nunca
  são disparados.

### Item 22 — Release 1.0 (publicação) ⬜
- **Branch:** `chore/publicar-1.0`
- Último item do plano: só entra depois dos Itens 13–21. Usa o que o Item 12 já deixou pronto (workflow `release.yml`, `CHANGELOG.md`, README).
- **Falta (depende do Eliel):** GIFs do README (gravar no jogo), publicar no Modrinth/CurseForge (conta e tokens: o Eliel digita as credenciais, nunca o Claude), testar o workflow criando a tag `v1.0.0`; atualizar o `CHANGELOG.md` com tudo o que entrou nos Itens 13–21 e os limites conhecidos (modo cliente e troca de slot do Litematica sem teste em jogo, NeoForge sem testes automáticos).
- **Pronto quando:** versão 1.0 publicada e baixável para cada loader.

## Como usar este roadmap

1. Começar cada sessão: `git status` + `git fetch origin --prune` (regra 7 do Eliel).
2. Uma branch por item, PR com squash merge; ao concluir, marcar ✅ aqui.
3. Ao terminar um item, gerar o **handoff** do próximo (ver `CLAUDE.md`): resumo em linguagem simples do que foi
   feito + prompt autocontido com o texto exato do próximo item.
