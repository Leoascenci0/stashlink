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

## Itens a implementar antes do release (Itens 13–21, com o 15 deixado para o fim)

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

### Item 16 — Bancadas usam o armazenamento como inventário ✅
- **Branch:** `feat/bancada-com-armazenamento`
- Ideia: a bancada passa a enxergar os **containers próximos como se fossem o inventário do jogador**, para craftar
  sem carregar os materiais. **Mesmo raio dos baús:** padrão 16, até 32 na tela de config (Item 15, 2026-10-05). Shulkers colocadas têm raio próprio (padrão 32, a tela sobe até 64).
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
- **Feito (decisões em `docs/ARCHITECTURE.md`, "Item 16"):** decisões do Eliel (2026-10-03): **todas as estações**; o livro
  de receitas acende + aviso na barra; mochila primeiro e baú só se faltar; (revisto em 2026-10-03: só baús e barris, nunca shulkers, e vale "usar baús como fonte").
  A função tem liga/desliga + cadeado (nova `Feature.BENCH`). Duas peças sobre as mesmas
  fontes e o mesmo raio do jogador (`PlayerPrefsStore.radius`; o Item 15 subiu o teto para 32):
  1. **Livro de receitas** (bancada, fornalha, defumador, alto-forno): um mixin em `ServerPlaceRecipe.placeRecipe` traz do
     armazenamento **só o que falta na mochila**, o jogo monta a receita sem mudar, e a sobra volta à origem. Com shift
     (máximo) também. O cliente com o mod soma o armazenamento na conta do livro (acende o que dá para fazer).
  2. **Painel "Armazenamento"** ao lado de **todas** as estações (bancada, fornalha, defumador, alto-forno, cortador de
     pedra, tear, cartografia, amolar, ferreiro, bigorna, encantamento, suporte de poções): grade com busca e rolagem; clique
     leva um stack ao cursor, botão direito leva um. É item de verdade na mão (nada de fantasma).
  **De fora, com motivo:** a grade 2x2 da mochila (não é bancada) e o Crafter (bloco automático, sem jogador). Funil,
  dispenser e dropper continuam fora do armazenamento.
- **Testes:** 13 GameTests novos (`BenchGameTests`, 67 no total): craftar só do baú; traz só o que falta; mochila basta =
  baú intocado; raio e baú aberto por outro jogador; mochila cheia (nada some); shift monta o máximo e devolve a sobra;
  fornalha; cadeado e liga/desliga; painel (stack, um, cursor cheio/ocupado); painel recusa menu errado/trancado/sem
  estação; a lista só mostra baú/barril/shulker; **fornalha, defumador, alto-forno, suporte de poções, funil, dispenser,
  dropper e crafter com item dentro nunca são tocados** por N, reabastecer, W, livro nem painel; fuzz com 2 jogadores
  (600 ticks, soma de tábuas e pedra conferida depois de cada ação). **Mutação:** dupe injetado (devolve cópia sem tirar)
  pego por 8 testes; perda injetada (descartar o que não coube) por 1; funil virando fonte por 2; ignorar baú aberto por
  outro por 2; ignorar o raio por 6; ignorar o cadeado por 1; painel ignorando cursor ocupado por 2. Os testes acharam um
  defeito real: `Inventory.add` descarta o que sobra quando o jogador tem materiais infinitos; o mod agora guarda na
  mochila por conta própria (`StackListSink`).
- **Também neste PR:** o `ServerPlayerMixin` saiu da lista `"server"` do arquivo de mixins para a lista comum (suspeita de
  que no Fabric aquela lista só vale no servidor dedicado; **não confirmada** com o log do Prism, avisar o Eliel).
- **Não testado em jogo ainda** (a tela, o painel e o livro acendendo são cliente; o `runClientGameTest` não roda aqui).
  Roteiro para o Eliel (jar em `fabric/build/libs`, modo Sobrevivência, servidor com o mod ou mundo único):
  1. Baú com 64 tábuas a poucos blocos (raio padrão 8), mochila vazia. Abra a **bancada**: aparece o painel
     "Armazenamento" à direita com as tábuas, e o livro de receitas mostra graveto/baú **acesos** (não vermelhos).
  2. Clique na receita do graveto: a grade enche, a barra mostra "N itens vindos do armazenamento". Pegue o resultado.
     Clique na **mesma receita de novo** sem fechar: funciona de novo (é o que o cliente sem o mod bloquearia).
  3. Shift + clique na receita: monta o máximo; shift + clique no resultado crafta vários. Some as tábuas antes e depois:
     nada duplicou, nada sumiu.
  4. Mochila **cheia**: o livro não puxa e o baú continua igual. Fora do raio ou com **outro jogador** olhando o baú: nada.
  5. **Fornalha**: lenha no baú, livro de receitas → carvão; ou painel → pegue combustível/entrada e ponha no slot.
     Teste também defumador e alto-forno.
  6. **Painel nas outras estações**: cortador de pedra (pedregulho), tear, mesa de cartografia, pedra de amolar, mesa de
     ferreiro, bigorna e encantamento (lápis), suporte de poções. Na bigorna procure uma ferramenta encantada no painel.
     Clique no painel **nunca** solta o item do cursor; roda do mouse rola; digitar na busca filtra (E não fecha a tela).
  7. **Estação com item dentro** (fornalha cozinhando, suporte de poções) perto: aperte N e W, feche e abra a bancada: o
     conteúdo delas não muda.
  8. Tela de config, aba Funções: "Bancadas com armazenamento" liga/desliga e cadeado (dono/operador). Desligada, a bancada
     é a do jogo base e o painel some.
  9. Servidor **sem** o mod: tudo como no jogo base (sem painel, sem armazenamento).

### Item 16.1 — Painel das bancadas só mostra o que serve na estação ✅
- **Branch:** `feat/bancadas-filtro-armazenamento`
- Pedido do Eliel (2026-10-03): o painel "Armazenamento" ao lado das estações não deve listar tudo; cada estação mostra
  **só os itens que podem ser usados nela** (ex.: tear = banner, corante e molde).
- **Feito:** o filtro roda **no servidor** (`BenchSync.snapshot` → `BenchCompat.relevant`), então o pacote fica menor e o
  teto de 512 itens vale só para o que serve. A regra vem dos **próprios slots da estação** (`Slot.mayPlace`): tear,
  cartografia, pedra de amolar, ferreiro e suporte de poções seguem o jogo sozinhos (e itens de outros mods). Casos
  especiais: **bancada** mostra tudo (qualquer item pode ser ingrediente); **fornalha/defumador/alto-forno** = combustível
  ou item que funde (lista de receitas do jogo); **cortador de pedra** = itens com receita; **encantamento** = lápis ou item
  encantável; **bigorna** = item com dano/encantado, livro encantado ou material que conserta algo da mochila.
  Painel vazio diz "Nada por perto que sirva aqui". Pedir/retirar item continua igual (servidor confere tudo).
- **Testes:** 1 GameTest novo (`panelListsOnlyWhatTheStationAccepts`: tear, fornalha e bancada), 103 no total.

### Item 16.2 — Painel das bancadas com a cara do livro de receitas ✅
- **Branch:** `feat/bancadas-livro-de-receitas`
- Pedido do Eliel (2026-10-03): o painel "Armazenamento" ao lado das estações com a **mesma tela do livro de receitas**
  (busca, grade, setas de página), mostrando só o que serve na estação, e clicar já leva o necessário para os slots certos.
- **Feito:** painel próprio com os widgets do livro (fundo, busca com lupa, botões de página, slots de 25 px, vermelho
  para o que falta). Bancada e fornalhas ficam só com o livro do jogo. **Cortador de pedra** e **tear** listam
  **resultados** (só de itens já descobertos; vermelho = falta material) e clicar monta a receita no servidor; tear tem abas
  Cores / Estandartes / Padrões (moldes) com a cor escolhida pintando os banners. **Bigorna** (Livros, Equipamento,
  Materiais de conserto; inclui etiqueta) e **mesa de ferraria** (Enfeites, Equipamento, Minérios), **encantamento** (Equipamento, Livros, Lápis-lazúli) e **suporte de poções** (Garrafas, Ingredientes, Combustível) têm abas; clicar num item
  solto o põe no slot que o aceita. As duas telas ficam centralizadas como um bloco (inclui pedra de amolar e o campo de
  nome da bigorna). **Bancada:** clicar num ingrediente fantasma que falta leva à receita que o fabrica.
- **Achados:** no 26.3 o botão esquerdo do mouse é 1 e o direito é 3 (setas e cliques "não funcionavam"); a pedra de amolar,
  o encantamento, o suporte de poções e o nome da bigorna ignoram `leftPos`; `@ModifyVariable` precisa do `ordinal` certo (crash ao carregar telas).
- **Testes:** 108 GameTests (novos: cortador, tear, ferraria, encantamento/poções, colocar no slot certo). Visual conferido em jogo pelo Eliel.
- **Fora do item:** abas na cartografia; escolher a cor do banner de base no tear.

### Item 16.3 — Revisão e melhorias das bancadas ✅
- **Branches:** `feat/bancadas-revisao` (Fase 1, PR #42/#43), `feat/bancadas-padronizacao` (Fase 2, PR #44), Fase 3 (`claude/cool-archimedes-5ibnld`, PR #45).
- **Fases 1 e 2:** revisão de integridade (devolução ao fechar/sair, teto de pacote, anti-flood, chave estável de receita) e
  padronização (cliques: esquerdo = pilha, direito = 1, Shift = máximo; mensagens; ordem única `BenchOrder`).
- **Fase 3 (pedidos do Eliel após testar a Fase 2), feito:**
  - **Fornalha, defumador, alto-forno:** painel do StashLink no lugar do livro do jogo, com abas fixas (Comida / Blocos /
    Minérios / Combustível; defumador Comida / Combustível; alto-forno Minérios / Combustível). Combustível com só o balde
    de lava; clicar põe no slot certo (`BENCH_FUEL`).
  - **Ferreiro:** Enfeites / Armaduras / Ferramentas e armas / Materiais (subir ferramenta para netherite sem levá-la na mão).
  - **Encantamento:** Armaduras / Ferramentas / Armas / Livros e **lápis-lazúli automático** (até 3, volta ao baú ao
    fechar/sair; `BENCH_LAPIS`).
  - **Bigorna:** Armaduras / Ferramentas / Armas / Livros / Materiais; com item no 1º slot, só os livros que servem nele
    (`BENCH_BOOK_FILTER`).
  - **Sinalizador (estação nova):** os ícones de pagamento da própria tela viram botões que puxam 1 minério (`BENCH_BEACON`).
  - **Suporte de poções:** aba Poções com todas as poções do jogo a partir de água (vermelho = falta material), um passo
    por clique (`BENCH_BREWING`).
  - Ícone de armadura único (peitoral de ferro); contagem fora do canto dos itens (fica no tooltip), em todas as estações.
- **Achados:** no 26.3 as receitas de poção são receitas de dados (`BrewingRecipe`), e as telas com livro de receitas não
  chamam o `extractRenderState` da tela base (só o `extractContents`), o que escondia o primeiro botão de combustível.
- **Testes:** GameTests novos (fornalhas, ferreiro, encantamento/lápis, bigorna/livros, sinalizador, poções) e unitário do
  `BrewPlanner`; build do GitHub verde. Aprovado em jogo pelo Eliel (2026-10-05).

### Item 17 — Escolher o que cada baú recebe com a tecla N ✅
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
- **Feito (decisões em `docs/ARCHITECTURE.md`, "Item 17"):** dois filtros independentes na tecla N.
  (1) **Botão "N" no baú** (normal = recebe, cinza riscado = nunca recebe), na linha do título à esquerda do lápis do
  rótulo. O valor mora no próprio bloco (`stashlink_no_quick_stack`, só gravado quando desligado), então sobrevive a
  reiniciar e some quando baú/barril quebra; **a shulker leva o valor no item** (mesmo `CUSTOM_DATA` do rótulo, inclusive no
  drop de sobrevivência). Baú duplo: o botão grava nas duas metades. O servidor valida (vivo, menu aberto é o do pedido,
  distância, claims, nenhum outro jogador com o baú aberto) e sincroniza o estado com o cliente que tem o mod.
  (2) **Aba "Tecla N" na tela de config** com 5 categorias — 🛡️ Armadura, ⛏️ Ferramentas, ⚔️ Armas, 🍖 Comida, 🧪 Poções —
  cada uma com botão liga/desliga **e cadeado do servidor**, como as demais funções (são `Feature` novas: o bit "desligado"
  viaja no `PlayerPrefs` que já existia, sem mudar o formato do pacote). Desligada (ou trancada), a N **nunca** guarda
  aquele tipo, mesmo que o baú já tenha o item ou tenha um slot reservado para ele. Classificação pelas tags/componentes
  do jogo (`compat/mc/ItemKinds`); blocos comuns e todo o resto não têm categoria. O "emoji" é o **ícone do item** (a
  fonte do jogo não tem emoji colorido, ver Item 14).
- **Testes:** 15 GameTests novos (`FilterGameTests`; 92 no total) — baú com N desligada nunca recebe; N pula o baú
  desligado e usa o próximo; o botão vale mais que a reserva de slot; baú duplo (as duas metades, uma religada à mão);
  persistência (gravar/recarregar, padrão não grava nada, quebrar leva o botão); **shulker pelo drop real
  (`Block.getDrops`)** com rótulo junto; pedidos inválidos (menu errado, fechado, longe, baú do End); baú aberto por
  outro jogador; classificação por tags; armadura com categoria desligada nunca guardada (mesmo com o item no baú e
  com reserva); cada categoria sozinha; categoria trancada no servidor; blocos comuns ignoram categorias; pacote; **fuzz
  de 600 ações com 2 jogadores** conferindo a soma de cada item depois de CADA ação. +1 unitário do modo cliente.
  **Mutação:** 6 defeitos injetados e pegos — N ignorando o botão (5 testes), N ignorando categorias (3), shulker sem o
  botão no drop (1) e no `collectComponents` (1), botão não gravado no disco (1), mudar o botão com o baú aberto por
  outro (2).
- **Não testado em jogo ainda (cliente gráfico).** Roteiro para o Eliel (jar em `fabric/build/libs`, sem `-sources`):
  1. Abrir um baú com o mod no servidor: aparece um **"N" pequeno** à esquerda do lápis, na linha do título. O nome do baú
     continua cabendo ao lado do título.
  2. Clicar no N: fica **cinza riscado** e a barra de ação diz "A N não vai mais colocar itens neste container".
     Fechar e abrir de novo: continua cinza riscado.
  3. Pôr o mesmo item no baú e na mochila e apertar N: **nada entra** nesse baú. Com outro baú perto que também tem o
     item, vai para o outro.
  4. Reiniciar o mundo/servidor: o botão continua cinza riscado. Quebrar o baú e colocar outro no lugar: volta normal.
  5. Baú duplo: um clique vale para as duas metades. Shulker: desligar, quebrar em sobrevivência, colocar de novo: continua
     desligada (e com o nome, se tinha).
  6. Config → aba **Itens bloqueados**: 5 botões com o ícone do item ao lado do nome. Desligar Armadura, deixar uma bota e
     um capacete na mochila e apertar N perto de um baú que já tem botas: a armadura **não** é guardada, o resto sim.
  7. Dono/operador: o cadeado de uma categoria a desliga para todos no servidor (botão fica cinza "Trancada").
  8. Servidor **sem** o mod (modo cliente): a categoria desligada também vale; o botão do baú não aparece.
  9. Se o ícone aparecer como quadrado ou sumir nos botões da aba, avise: troco por símbolo da fonte.
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

### Item 19 — Botão do meio do mouse puxa o item do armazenamento para a hotbar ✅
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
- **Feito (decisões em `docs/ARCHITECTURE.md`, "Item 19"):** a investigação achou que o pick block do jogo é resolvido
  **no servidor** (`ServerGamePacketListenerImpl.tryPickItem`); o cliente só manda a posição do bloco. Então **não há
  mixin nem pacote novo no cliente**: um mixin no servidor (`ServerGamePacketListenerImplMixin`) chama
  `PullItemService.pickBlock` antes do jogo base. Só age quando o jogo base não faria nada (item em nenhum slot do
  inventário, jogador não criativo): se o item já está na hotbar ou na mochila, o jogo base seleciona/troca como sempre.
  Vem 1 stack, das mesmas fontes do Litematica (shulkers do inventário, shulkers e baús no raio, `PlayerPrefsStore`).
  Sem slot livre: avisa na barra de ação e não mexe em nada (a troca no mesmo slot do Item 18 continua só do Litematica).
  Mira em bloco que dá outro item (trigo → semente): vale o item que o jogo base escolheria. Usa `Feature.PULL`
  (liga/desliga e cadeado), em silêncio quando trancada. Achado: puxar/reabastecer pelo `PlayerSources` comum **não**
  conferia "baú aberto por outro jogador"; o botão do meio usa `PlayerSources.operationSkippingOpened`.
  **Modo cliente: não se aplica** (o pick block é do servidor; sem o mod no servidor continua o jogo base).
- **Confirmado em jogo pelo Eliel (2026-10-03, Prism, Fabric 26.3).**
- **Testes:** `PickBlockGameTests` (10 cenários no servidor real, 102 GameTests no total, 2 mutações pegas: ignorar baú
  aberto por outro e ignorar "já está no inventário").
- **Roteiro manual (no Prism, Fabric, servidor com o mod, sobrevivência; confira em Opções > Controles qual botão é o "pegar bloco"):**
  1. Guarde 64 de um bloco (ex.: tijolo) num baú a menos de 8 blocos e deixe esse bloco fora da mochila. Mire um tijolo
     colocado e aperte o botão do meio: 1 stack vai para a hotbar e para a mão; o baú perde o stack.
  2. Hotbar com algo no slot selecionado: o item vai para outro slot livre; o seu não é tocado.
  3. Encha os 9 slots da hotbar e repita: aparece o aviso e nada muda.
  4. Afaste-se do baú além do raio: nada acontece. Item já na mochila: o jogo base o traz (o baú não é tocado).
  5. Mire trigo plantado com sementes no baú: vêm as sementes. Com outro jogador com o baú aberto: nada sai.
  6. Desligue "Trazer item" na config: o botão do meio volta ao jogo base. Criativo: continua dando o item do nada.
- **Pronto quando:** mirar um bloco, apertar o botão do meio e o item chega à hotbar vindo de um baú no raio; com a
  hotbar cheia nada acontece e nada some; fora do raio não puxa; testes no harness `:fabric:runGameTest`, incluindo 2
  jogadores.

### Item 20 — Organizar os itens do sistema de armazenamento ✅
- **Branch:** `feat/organizar-armazenamento`
- **Feito (2026-10-04, aprovado pelo Eliel em jogo):** botão Organizar no baú, tecla O com busca e organizar tudo (prévia, Aplicar, Desfazer) e contorno do baú achado. Detalhes em `ARCHITECTURE.md` (Item 20). 24 GameTests + 12 unitários.
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

### Item 21 — Shift + passar o mouse coleta os itens do baú ✅
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
- **Feito (decisões em `docs/ARCHITECTURE.md`, "Item 21"):** `Feature.HOVER_COLLECT` (liga/desliga + cadeado), mixin novo
  `HoverCollectScreenMixin` + `HoverCollectClient` (cliente) + `HoverCollectPass` (regra de "um clique por passagem",
  testada). Só clique normal de inventário; só em baú/barril/shulker/ender chest; nunca em slot de resultado.

### Item 15 — Raio de baús e bancadas até 32 blocos (sem conduíte) ✅
- **Branch:** `feat/raio-32-conduite`
- **Ordem:** penúltimo item do plano, logo antes da publicação (Item 22). Ordem escolhida pelo Eliel: 17, 19, 20, 21, 15, 22.
- Ideia: o teto do raio de **baús, barris e bancadas** é **16** (`HARD_MAX_RADIUS`; decisão do Eliel, 2026-10-03: o 64 antigo
  estava desbalanceado). Passa a ser possível **chegar a 32 blocos**, desde que haja um **conduíte ativo instalado perto do
  estoque**. Sem conduíte, continua o teto de 16. As **shulkers colocadas** têm raio próprio (padrão 32, a tela sobe até 64) e
  **não** dependem do conduíte. A bancada usa os mesmos raios dos baús (só baús e barris, nunca shulkers).
- Confirmado pelo Eliel: é o **conduit** do Minecraft, o bloco que se instala debaixo d'água. Ele só fica **ativo** dentro da
  água, cercado pela estrutura de prismarina (mínimo 16 blocos), então o estoque precisa de um conduíte montado de verdade.
- A decidir na investigação: o que é "perto" (distância do conduíte ao container ou ao jogador) e se o conduíte
  precisa estar ativo (com a estrutura de prismarina completa) ou basta existir; se o raio maior vale por container
  (só os que estão perto de um conduíte) ou para o jogador inteiro. Tudo no servidor, com o teto configurável
  (`maxRadius`, limite do código 32 só com conduíte) e o custo de varredura medido de novo no harness (chunks
  descarregados continuam fora; nunca forçar carregar chunk). Ler o raio sempre por `PlayerPrefsStore.radius`.
- **Pronto quando:** com conduíte perto o raio dos baús sobe até 32, sem ele fica em 16, e a medição com 289+ containers segue
  bem abaixo de 5 ms por operação.
- **Decisão do Eliel (2026-10-05): a ideia do conduíte foi descartada** ("vai dar menos trabalho"). Ficou só o ajuste na tela
  de config: raio de baús, barris e bancadas com **padrão 16** e **máximo 32** (`HARD_MAX_RADIUS = 32`, `DEFAULT_RADIUS = 16`).
  O dono do servidor ainda pode baixar o teto em `maxRadius`. Shulkers seguem com raio próprio (padrão 32, até 64).
- **Feito:** `StashLinkConfig` (teto 32, padrão 16, campo `configVersion` = 2 no arquivo: arquivo antigo que ainda tem os
  padrões de antes, teto 16 e raio 8, passa sozinho a 32 e 16; valor escolhido pelo dono fica), textos da tela nos dois idiomas
  ("padrão 16, máximo 32"). Testes: 4 unitários novos em `StashLinkConfigTest`; o GameTest da bancada virou
  `chestsReach32AndShulkersAreNeverBenchStorage` (baú a 20 entra com 32, baú a 40 não, e no padrão 16 o baú a 20 sai); a
  medição de desempenho (`StashLinkGameTests`, 289 containers) passou a rodar com raio 32.

### Item 16.4 — Painel das bancadas também mostra a mochila ✅
- **Branch:** `feat/painel-bancada-com-mochila` (pedido do Eliel, 2026-10-05, ao ver "Nada no raio de 0 blocos" na fornalha).
- **Feito:** o painel lista **mochila + armazenamento** somados por tipo, mesmo com "Usar baús como fonte" desligado (esse era o
  "raio 0": o ajuste estava em Não). O clique tira **da mochila primeiro**; o baú só completa o que faltar. O que sai da mochila
  é do jogador e **não entra no caderno de emprestados** (nunca volta a baú). Rótulo do tooltip: "Disponível" (era "No baú").
- **Limites conhecidos:** poções (suporte) e as listas de receita (cortador, tear...) continuam contando só o armazenamento; o
  livro de receitas desconta a mochila da soma para não contar em dobro. (Resolvido no Item 16.5.)
- **Correção de layout (print do Eliel, mesmo dia):** janela de ~382 unidades de GUI deixava só 29 de folga (o código pedia 30) e
  a estação não se movia: o painel ficava por cima da fornalha. `BenchPanel.stationLeft` agora encosta o painel na margem quando a estação cabe.

### Item 16.5 — Todas as bancadas usam a mochila de quem abriu ✅
- **Branch:** `feat/bancadas-mochila-todas` (pedido do Eliel, 2026-10-05: "as bancadas têm que reconhecer o inventário do
  player como fonte válida, e não só os baús. Tem que resolver em todas as bancadas. E é somente do player que está com a
  bancada aberta").
- **Feito:** uma regra só, `BenchPool.take` (mochila primeiro, baú completa; diz quanto saiu de cada lugar), usada por todas
  as estações: cortador e tear (`BenchResults.craft`, que tirava do baú primeiro e da mochila só uma pilha), suporte de poções
  (`BenchBrewing`), lápis-lazúli automático (`BenchLapis`; decisão do Eliel: mochila primeiro), clique em item solto e cursor.
  O caderno de emprestados só registra a parte do baú. Receitas só usam itens comuns (sem nome, dano ou encantamento), a
  mesma regra do livro de receitas do jogo: a pedra renomeada do jogador nunca vai parar no cortador.
- **Só a mochila de quem abriu:** tudo usa o `ServerPlayer` que mandou o pedido; estação nunca é fonte. Teste com 2 jogadores
  (inclusive a mesma fornalha aberta pelos dois) prova que um nunca vê, usa ou perde item da mochila do outro.
- **Sinalizador (decisão do Eliel):** trocar de minério devolve o pagamento do próprio jogador à mochila (antes ele ficava e a
  troca não acontecia, o que travaria quem pagou com a mochila); sem lugar na mochila, não troca. Item do jogador nunca vai a baú.
- **Lista em dia:** o servidor reenvia a lista quando a mochila muda (no máximo a cada 5 ticks, porque cada lista varre os
  baús); antes ficava até 5 s velha, e o livro de receitas podia contar em dobro o que o jogador tinha acabado de mover.
- **Textos (pt e en):** "Nada na mochila nem no raio de N blocos"; com baús desligados, "Nada na mochila que sirva aqui (baús
  desligados)" em vez de "raio de 0 blocos"; o mesmo no sinalizador, no tear e nas dicas das funções da config.
- **Slots travados** (os que N e W não mexem) continuam valendo para as bancadas (decisão do Eliel), como no livro do jogo.
- **Testes:** 10 GameTests novos em `BackpackBenchGameTests` (190 no total): item só na mochila em cada estação, mochila + baú
  (cortador com cliques e com shift no resultado, poções, lápis), sinalizador e 2 jogadores; 2 unitários de texto. Mutação
  pega: "baú primeiro" (5 testes falham) e "caderno registra a mochila" (2 falham). Falta o teste no jogo do Eliel.

### Item 22 — Release 1.0 (publicação) 🔄
- **Branch:** `chore/publicar-1.0`
- Último item do plano: só entra depois dos Itens 13–21 **e 23–26** (ordem nova do Eliel, 2026-10-05: o 22 só fecha depois do 26, compatibilidade com armazenamento de outros mods). Usa o que o Item 12 já deixou pronto (workflow `release.yml`, `CHANGELOG.md`, README).
- **Falta (depende do Eliel):** GIFs do README (gravar no jogo), publicar no Modrinth/CurseForge (conta e tokens: o Eliel digita as credenciais, nunca o Claude), testar o workflow criando a tag `v1.0.0`; atualizar o `CHANGELOG.md` com tudo o que entrou nos Itens 13–21 e os limites conhecidos (modo cliente e troca de slot do Litematica sem teste em jogo, NeoForge sem testes automáticos).
- **Em andamento (2026-10-05):** Item 15 mesclado (PR #46), então os Itens 13–21 estão todos na `main`. Feito: `CHANGELOG.md`
  1.0.0 completo (Itens 13–21 e limites conhecidos), README com as funções novas e lugar para os GIFs, versão `1.0.0` e a
  descrição do mod em `gradle.properties` (antes era o texto do modelo e apareceria na lista de mods). Em `docs/publicacao/`:
  página da loja (en e pt), prompt do ícone e sugestões de nome, e o prompt da revisão completa, que roda **antes** da tag.
  Falta (Eliel): revisão completa, GIFs, ícone novo, tag `v1.0.0` e publicação.
- **Revisão completa feita (2026-10-05, `docs/REVISAO-1.0.md`):** 9 áreas revisadas; corrigidos com teste 1 perda de item
  (balde/tigela/garrafa vazia no reabastecimento), 1 dupe (W em tela falsa de outro mod), shulker dentro de shulker colocada, 3 cadeados contornáveis por
  pacote forjado, rótulo de baú duplo, chunk carregado à força, 2 vazamentos de memória, 2 mixins frágeis e botões fora
  da tela, e testes que se misturavam com o vizinho. Achados maiores viraram os Itens 23–25, que entram **antes** da publicação (decisão do Eliel, 2026-10-05).
  Falta (Eliel): GIFs, ícone novo, tag `v1.0.0` e publicação.
- **Publicação (2026-10-06):** Itens 23–27 mesclados. O Eliel gerou banner e ilustrações e decidiu publicar **sem** os testes
  em jogo pendentes (entram nos limites conhecidos do CHANGELOG) e **sem** os GIFs (ficam para depois da 1.0; roteiro em
  `docs/publicacao/roteiro-gifs.md`). Ícone novo = baú com corrente recortado do banner (jogo, README e lojas); banner no
  topo do README e das páginas da loja; ilustrações na galeria das lojas, com legenda dizendo que são ilustrações. O Claude
  envia às lojas pelo navegador do Eliel, já logado (sem digitar senha ou token), com a confirmação dele antes do envio.
- **Release no GitHub publicado (2026-10-06):** PR #58 mesclado, tag `v1.0.0`, o `release.yml` funcionou na primeira vez
  e o release está no ar com um jar por loader (ícone novo e versão 1.0.0 conferidos dentro dos dois):
  https://github.com/Leoascenci0/stashlink/releases/tag/v1.0.0. Achado no caminho: o `.gitattributes` (`* text eol=lf`)
  tratava `.jpg` como texto e corrompia as imagens no commit; agora `.jpg`, `.jpeg`, `.webp` e `.nbt` são binários.
- **Lojas — pendente:** **Modrinth** recusa criar projeto até a conta do Eliel ("Frosther", entrada pelo GitHub) ter
  e-mail cadastrado e confirmado (Settings → Account → Add email). **CurseForge**: o formulário novo falha no logo com
  "id must be a string", tanto pela extensão quanto escolhendo o arquivo à mão (erro do site, sem chegar ao servidor);
  tentar outro dia ou abrir chamado no suporte deles. O logo da CurseForge precisa ter no máximo 100 KB (o nosso tem 70 KB).
  O que colar em cada campo está em `docs/publicacao/roteiro-gifs.md` (checklist e legendas em inglês e português).
- **Pronto quando:** versão 1.0 publicada e baixável para cada loader.

## Antes da publicação (achados da revisão, `docs/REVISAO-1.0.md`)

### Item 23 — Endurecer os pedidos ao servidor ✅
- **Branch:** `fix/pedidos-servidor`
- **Telas falsas de outros mods:** a W já recusa (revisão 1.0, `LootAllService.isWorldStorage`); falta o mesmo para
  Organizar, travar slot e o botão N, que ainda aceitam qualquer `ChestMenu` (lojas, seletores com container "de
  mentira").
- **Anti-flood** nos pedidos que ainda não têm: travar slot, rótulo (editar/gravar), botão N, preferências; bancada
  com 2–3 ticks entre pedidos que varrem o raio.
- `stashlink.json` quebrado: guardar cópia `.bak` antes de sobrescrever; versão futura do arquivo não é rebaixada.
- **Pronto quando:** GameTest com menu de container falso (W não tira nada) e com rajada de pedidos (o servidor só
  atende no ritmo do limite).
- **Feito (2026-10-05):** `LootAllService.isWorldStorage` agora também vale em `OrganizeService` (organizar o baú aberto),
  `SlotLockService` e `QuickStackReceiveService` (e no `storageOf` dela). `isSupportedMenu` ficou como estava: o cliente
  o usa e lá o container é sempre um `SimpleContainer`. Anti-flood com o ajudante `network/RequestLimiter` (um por tipo de
  pedido, ignora em silêncio): travar slot 3 ticks, botão N 3, rótulo (abrir editor e gravar) 4, bancada 3 só para montar
  receita e pagar (colocar e cursor seguem livres), preferências 20 ticks guardando só a última (aplicada pelo tick
  do servidor). `StashLinkConfig`: config quebrada ou de versão futura ganha `stashlink.json.bak` (nunca por cima de um
  `.bak` que já existe) antes do primeiro `save`; versão futura não é regravada pelo `load`. Testes: 6 GameTests novos em
  `ReviewGameTests` (tela falsa, rajadas de 50 pedidos, bancada) e 4 unitários em `StashLinkConfigTest`.

### Item 24 — Compatibilidade com JEI, EMI e REI ✅
- **Branch:** `feat/compat-jei-emi-rei`
- O painel "Armazenamento" das bancadas e os botões ao lado do baú não avisam esses mods de que ocupam aquele espaço
  (zona de exclusão), então a lista de itens deles pode ficar por baixo. Plugin opcional por mod (só carrega se o mod
  existir, como o Litematica), e parar de mexer em `Screen.width` se a zona resolver.
- **Pronto quando:** com cada um dos três instalado, painel e lista não se sobrepõem em GUI 2 e 3.
- **Feito:** `client/PanelZones` (common) guarda o retângulo do painel + abas; plugins só no Fabric
  (`compat/jei/StashLinkJeiPlugin` via `jei_mod_plugin`, `compat/rei/StashLinkReiPlugin` via `rei_client`), carregados
  só se o mod existir. O botão N e o lápis ficam dentro do fundo do baú, que JEI/REI já tratam como ocupado.
  O `Screen.width` do encantamento/poções **fica**: vem de essas telas ignorarem `leftPos`, não dos mods.
- **Pendente:** (1) **EMI** não tem versão para o 26.x (última 1.1.24 para 1.21.1): plugin quando sair; (2) checagem
  visual manual em GUI 2 e 3 (`./gradlew :fabric:runClient -PcompatJei` / `-PcompatRei`); (3) NeoForge sem plugin nem teste.

### Item 25 — Desempenho e acabamento das bancadas e rótulos ✅
- **Branch:** `perf/bancadas-rotulos`
- **Feito (2026-10-05):** medido no harness de 289 containers (local, Windows, média de 100 repetições). **Bancada
  aberta com 289 baús cheios:** reconferência inteira (`BenchSync.snapshot`) = 0,57 ms a cada 100 ticks (~6 µs/tick) →
  **não mexeu** (sem problema medido). **Rótulos do End** (1000 gravados em chunks descarregados): ciclo do holograma
  262 → 139 µs; mais importante, o rótulo de baú que sumiu de posição carregada agora é apagado depois de 5 min de
  carência (quebrar e recolocar logo em seguida ainda traz o rótulo de volta), e a lista é percorrida sem cópia, uma vez
  por ciclo. **Bigorna:** `BenchCompat.placementOrder` põe ferramenta/arma/armadura no 1º slot e material/livro no 2º,
  na ordem que o jogador clicar (`BenchGameTests.anvilToolAlwaysGoesToTheFirstSlot`). **Não medido (sem como aqui):**
  N/Organizar com mod de claims e fontes só em baús com muitos jogadores — exigem servidor real com esses mods/jogadores;
  sem número, não se mexeu. 180 GameTests verdes (177 + 3).
- Medir antes (`spark` ou o harness de 289 containers): bancada aberta com baús cheios refaz a varredura a cada 100
  ticks mesmo sem mudança; N/Organizar com mod de claims; fontes só em baús com muitos jogadores. Só otimizar o que o
  número mostrar.
- Rótulos de baú do End: apagar quando o baú some e percorrer só os carregados.
- Bigorna: ferramenta sempre no 1º slot, mesmo clicando no material primeiro.
- **Pronto quando:** números antes/depois no PR; bigorna com GameTest da ordem.

### Item 27 — Litematica: avisos e baú aberto por outro jogador ✅
- **Branch:** `fix/litematica-avisos`
- Achados da conversa com o Eliel sobre a integração com o Litematica (2026-10-06), nenhum derruba o jogo nem perde item:
  (1) se uma versão nova do Litematica mudasse a função que o mod enxerta, a integração desligava **em silêncio**;
  (2) com a hotbar cheia de itens do jogador, o Easy Place parava sem explicação (o cliente cancela o Litematica e o
  servidor não tem onde pôr o bloco); (3) o Litematica e o reabastecimento da mão tiravam de baú que outro jogador
  estava olhando, ao contrário da N e do botão do meio (decisão "a rever" do Item 19).
- **Feito (decisões em `docs/ARCHITECTURE.md`, "Item 27"):** `LitematicaMixinPlugin` confere, antes de aplicar, se
  `InventoryUtils.schematicWorldPickBlock` existe com a assinatura esperada e escreve no log "Integração com o Litematica
  X ligada" ou por que ficou desligada. Hotbar cheia com o item por perto: aviso na barra de ação (o mesmo do botão do
  meio). Litematica e reabastecimento passam a pular baú aberto por outro jogador (`PlayerSources.operationSkippingOpened`),
  a mesma regra em todas as funções.
- **Testes:** 1 GameTest novo (`fullHotbarWarnsOnlyWhenItemIsNearby`) e 3 ajustados (`refillSkipsChestOpenedByOther`,
  `twoPlayersSwapOnSameChest`, `fullHotbarDoesNothing` agora confere o aviso); 191 GameTests verdes. **Mutação:** desfazer
  cada uma das três mudanças derruba exatamente o teste dela. Cliente com Litematica 0.29.1 (`-PcompatMods`): o log mostra
  "Integração com o Litematica 0.29.1 ligada" (o cliente de desenvolvimento cai logo depois por um erro nativo da
  máquina, `0xC0000005`, que acontece igual com o código da `main`).
- **Continua sem teste em jogo:** a troca no mesmo slot (Item 18) com Easy Place; roteiro no Item 18.

## Antes da publicação: armazenamento de outros mods (pedido do Eliel, 2026-10-05)

### Item 26 — Baús e gavetas de outros mods como armazenamento ✅
- **Feito (2026-10-05/06; aprovado em jogo pelo Eliel em 2026-10-06, Fabric com Storage Drawers):** `Feature.MOD_STORAGE` (liga/desliga + cadeado). Blocos de outros
  mods entram pela "tomada de itens" do loader (`ModStorage` em `common`; `FabricModStorage` e `NeoForgeModStorage` só
  cola). Só entram os da tag `stashlink:mod_storage`, uma lista de **permitidos** (decisão do Eliel): baús `c:chests`,
  barris `c:barrels`, barris limitados do Sophisticated e gavetas do Storage Drawers. Máquinas e controladores ficam de
  fora. N, reabastecer, botão do meio, Litematica e bancadas usam esses blocos, e o emprestado volta a eles.
  - **W na tela de outro mod: limite registrado** (a tela não diz de que bloco é).
  - Harness de GameTest do NeoForge criado: `./gradlew :neoforge:runGameTestServer`. Os mesmos 10 cenários rodam nos dois
    loaders; o Sophisticated Storage real foi testado no NeoForge (5 testes) e o Storage Drawers real no Fabric (3; a
    versão NeoForge dele não carrega no NeoForge 26.3.0.39). 204 GameTests no Fabric, 15 do StashLink no NeoForge e
    220 unitários verdes.
  - **Mutação:** 10 regras anti-dupe estragadas de propósito, as 10 pegas (uma só passou a ser pega com o teste novo
    da troca do Litematica devolvendo à gaveta).
  - **Desempenho (289 baús, média de 3 rodadas alternadas com o `main`):** tudo abaixo de 1 ms, diferenças dentro do
    ruído (varredura 191 → 213 µs, N 658 → 725 µs, bancada 722 → 766 µs). Com 289 gavetas de outro mod: varredura 84 µs,
    bancada 836 µs.
  - Detalhes, investigação e limites em `ARCHITECTURE.md` (Item 26).
- **Branch:** `feat/compat-armazenamento-mods`
- **Hoje:** só baú, barril e shulker do jogo (e baús que estendem o baú do jogo, como Iron Chests) entram no raio
  (`NearbyContainers`) e na W (`LootAllService.isSupportedMenu`). Baús e gavetas com bloco próprio (Sophisticated
  Storage, Storage Drawers, Functional Storage…) ficam de fora.
- **Ideia:** usar a "tomada padrão" de itens de cada loader, que esses mods já oferecem para funis e canos: no NeoForge a
  capability de itens do bloco, no Fabric a Transfer API (`ItemStorage.SIDED`). Começar com uma investigação curta
  (nome exato da API no 26.3 em cada loader, como o Sophisticated Storage a expõe) registrada em `docs/ARCHITECTURE.md`.
  Interface em `common`, implementação por loader (só cola), API frágil em `compat/mc/`.
- **Escopo:** N, reabastecer a mão, botão do meio e painel das bancadas passam a enxergar esses blocos. W na tela do baú
  de outro mod só se der para achar o bloco aberto com segurança; se não, fica registrado como limite.
- **Regras:** função nova `MOD_STORAGE` no `config/Feature` (liga/desliga + cadeado, `stashlink.feature.mod_storage` e
  `.tip` nos dois idiomas, `FeatureGate` no servidor). Sempre simular antes de mover (anti-dupe). Container do jogo
  continua pelo caminho atual; bloco já reconhecido não é contado duas vezes (Iron Chests também expõem a API; baú duplo).
  Gaveta guarda milhares de um item num "slot": nunca supor stack ≤ 64. Mesmas checagens de distância, claims e baú
  aberto por outro jogador.
- **Fora do escopo:** slot travado, rótulos e Organizar (Itens 13, 14, 20) em baú de outro mod; modo cliente (Realms);
  redes AE2 e Refined Storage (integração por mod, item futuro depois da 1.0).
- **Pronto quando:** GameTest no Fabric com um bloco de teste que só expõe a API padrão (não é `Container`): N,
  reabastecer, botão do meio e bancada usam, com a soma de itens conferida; harness de GameTest no **NeoForge** criado e
  o mesmo teste passando lá; teste com o Sophisticated Storage no NeoForge (e um mod de gavetas, se houver versão
  26.3); medição no harness de 289 containers sem piora relevante.

## Como usar este roadmap

1. Começar cada sessão: `git status` + `git fetch origin --prune` (regra 7 do Eliel).
2. Uma branch por item, PR com squash merge; ao concluir, marcar ✅ aqui.
3. Ao terminar um item, gerar o **handoff** do próximo (ver `CLAUDE.md`): resumo em linguagem simples do que foi
   feito + prompt autocontido com o texto exato do próximo item.
