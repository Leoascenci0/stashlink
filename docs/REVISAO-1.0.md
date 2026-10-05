# Revisão completa antes da 1.0 (2026-10-05)

Revisão do mod inteiro antes da tag `v1.0.0`: correção, conexões entre módulos, segurança no servidor e desempenho.
Nove áreas, cada uma lida por um revisor separado; as áreas de risco alto (dupe, validação, rede) tiveram uma segunda
leitura das correções. Esta sessão só traz **correções pequenas com teste**; o que é grande virou item novo no roadmap
(Itens 23–25).

**Resumo:** nenhum achado de gravidade alta ficou aberto. Achamos e corrigimos **um bug de perda de item** e **um de
dupe** (a W copiava itens de telas falsas de outros mods): o
reabastecimento da mão a partir de balde, tigela ou garrafa vazia apagava o recipiente vazio. Também corrigimos três
caminhos em que um cliente adulterado passava por um cadeado, dois vazamentos de memória e um caso raro de chunk
carregado à força. Os GameTests rodam no CI dentro do `build` (`check → :fabric:runGameTest`): 171 testes, todos verdes
neste PR. Ao rodá-los com os testes novos apareceu um problema dos próprios testes (raio padrão alcançando o teste
vizinho), corrigido no item 7.

## Achados

Gravidade: **alta** = dupe/perda de item ou queda do servidor; **média** = regra do projeto quebrada, cadeado
contornável ou falha visível; **baixa** = robustez, memória, texto.

| # | Área | Achado | Grav. | Onde | Status |
|---|------|--------|-------|------|--------|
| 1 | 2 Itens | Reabastecer com recipiente vazio (balde/tigela/garrafa) apagava o recipiente: o inventário juntava o vazio na própria mão e o item novo era posto por cima | média (perda de 1 item) | `refill/RefillService.java:108` | **corrigido** + `ReviewGameTests.refillKeepsTheEmptyBucket` |
| 2 | 2 Itens | Shulker podia entrar numa shulker **colocada** (reserva de slot + N, Organizar): o `canPlaceItem` do bloco não confere | média | `source/ContainerInsert.java:19` | **corrigido** + `ReviewGameTests.shulkerNeverGoesIntoAPlacedShulker` |
| 3 | 4 Bancadas | Pedido forjado "pôr no slot" (PLACE) punha pagamento no sinalizador com `bench_beacon` trancado | média | `bench/BenchResults.java:241` | **corrigido** + `BenchGameTests.beaconKeepsThePlayersOwnPaymentAndRespectsTheLock` |
| 4 | 4 Bancadas | Pedido forjado de cursor (`recipeId = -1`) ignorava os filtros do painel (ex.: combustível com a aba trancada) | média | `bench/BenchPullService.java:70` | **corrigido** (usa `BenchSync.listed`, a mesma regra do painel) + `BenchGameTests.cursorRequestRespectsTheFuelLock` |
| 5 | 1 Rede / 7 Config | Botão "recebe com a N" não passava por `FeatureGate` (mudava o baú com a N trancada) | média | `quickstack/QuickStackReceiveService.java:52` | **corrigido** + `ReviewGameTests.receivesButtonRespectsTheQuickStackLock` |
| 6 | 9 Dados | Baú rotulado sozinho e depois emendado com outro: o holograma sumia e cada metade mostrava um nome | média | `label/Labels.java:30`, `label/HologramService.java:96` | **corrigido** + `LabelGameTests.chestLabeledAloneKeepsItsLabelWhenItBecomesDouble` |
| 7 | 8 Build | No CI, os GameTests sem raio fixo usavam o padrão do servidor (16 desde o Item 15) e a N de um teste guardava itens no baú do teste vizinho (`raceFuzz` e `twoPlayersQuickStackSameChest` falhavam juntos, conforme a ordem) | média (teste instável, não bug do mod) | `fabric/src/gametest/.../Lab.java:66` | **corrigido** (jogador simulado nasce com raio 8; quem precisa de outro raio já define) |
| 8 | 3 Fontes | Baú duplo na borda de um chunk não carregado: ler a outra metade carregava o chunk à força | média (tranco de tick, raro) | `source/NearbyContainers.java:126` | **corrigido** (`getChunkNow` antes) — sem teste automático: o GameTest não controla quais chunks estão carregados |
| 9 | 9 Dados | `SlotLockSync` segurava o jogador na memória para sempre se ele saísse com um baú aberto (valor forte num `WeakHashMap`) | baixa | `slotlock/SlotLockSync.java:31` | **corrigido** (referência fraca ao menu) — sem teste: depende do coletor de lixo |
| 10 | 1/7/9 | Preferências por jogador nunca saíam da memória | baixa | `config/PlayerPrefsStore.java:15` | **corrigido** (`StashLink.onPlayerLeave` nos dois loaders; o cliente reenvia ao entrar) |
| 11 | 1 Rede | Cadeado pedido repetido regravava o `stashlink.json` e avisava todos a cada pacote (só operador) | baixa | `config/FeaturePolicyService.java:65` | **corrigido** (só grava se mudou); `FeatureGameTests` cobre |
| 12 | 6 Mixins | Mixins só visuais com `require = 1`: um mod ou versão que mude o método derruba o jogo ao abrir a tela | média | `mixin/GrindstoneScreenMixin.java:28`, `mixin/RecipeBookComponentMixin.java:138` | **corrigido** (`require = 0`: perde-se só o enfeite) |
| 13 | 5 Cliente | Botões "Organizar"/"Sistema" saíam da janela em GUI 4 com janela pequena | média | `mixin/ContainerScreenOrganizeMixin.java:37` | **corrigido** (encostam na borda) — teste visual, sem automação |
| 14 | 2 Itens | `isSupportedMenu` aceita qualquer `ChestMenu`, inclusive telas de mods feitas com container de mentira (lojas, kits): a W copiava os itens da vitrine (dupe) | média | `lootall/LootAllService.java:47` | **corrigido (Item 23)**: a W (revisão) e agora também Organizar, travar slot e o botão N só aceitam container de bloco, baú duplo ou baú do End; `ReviewGameTests.lootAllIgnoresFakeChestScreens` e `fakeChestScreensAreNotStorage` |
| 15 | 1 Rede | Sem anti-flood em travar slot, rótulo, botão N e preferências; bancada aceita 20 pedidos/s (cada um varre o raio) | baixa | `slotlock/SlotLockService`, `label/LabelService`, `bench/BenchPullService.java:53` | **corrigido (Item 23)**: `RequestLimiter` em travar slot, rótulo, botão N, preferências e bancada (receita/pagar); `ReviewGameTests` com rajadas |
| 16 | 6 Compat | Painel das bancadas e botões não registram zona de exclusão para JEI/EMI/REI (podem ficar por baixo da lista deles) | média | `mixin/AbstractContainerScreenMixin.java:100` | **vira item** (Item 24) |
| 17 | 3 Desemp. | Estação aberta refaz a varredura inteira a cada 100 ticks mesmo sem mudança | baixa | `bench/BenchSync.java:29` | **vira item** (Item 25, medir antes) |
| 18 | 9 Dados | Rótulos de baú do End ficam gravados para sempre depois de o baú sumir, e a lista é percorrida a cada 0,5 s | baixa | `label/EnderLabels.java`, `label/HologramService.java:133` | **vira item** (Item 25) |
| 19 | 4 Bancadas | Bigorna: clicar no material antes da ferramenta põe o material no 1º slot (a bigorna fica sem resultado) | baixa | `compat/mc/BenchCompat.placementOrder` | **vira item** (Item 25) |
| 20 | 4 Bancadas | Item encantado/danificado posto pelo painel na bigorna/amolar/ferraria volta à **mochila** (não ao baú) ao fechar | baixa | `bench/BenchLedger.java` | aceito como limite (nada some; documentado) |
| 21 | 6/4 | Se o jogo lançar erro no meio de `ServerPlaceRecipe`, o que veio do baú fica na mochila | baixa | `mixin/ServerPlaceRecipeMixin.java:37` | aceito como limite (nada some) |
| 22 | 7 Config | `stashlink.json` quebrado é sobrescrito pelo próximo comando/cadeado; versão futura é rebaixada para 2 | baixa | `config/StashLinkConfig.java:230` | **corrigido (Item 23)**: cópia `.bak` antes de sobrescrever; versão futura não é regravada pelo `load`; `StashLinkConfigTest` |
| 23 | 9 Dados | Travas de slot não vão no item da shulker; texto do rótulo aceita alguns invisíveis raros (U+3164, U+2800) e marcas combinantes empilhadas | baixa | `label/LabelText.java:128` | aceito como limite (o primeiro é decisão do Item 13) |
| 24 | 5 Cliente | `OrganizeScreen` e frases fixas da tela de config apertadas em GUI 4 com janela muito baixa | baixa | `client/OrganizeScreen.java:58`, `client/StashLinkConfigScreen.java:~270` | aceito como limite (funciona; só aperta) |
| 25 | 7 Config | Cadeado de "Shift + passar" só vale no cliente (o servidor não sabe de onde veio o pedido; o pedido em si passa pelo cadeado da W/N) | baixa | `client/HoverCollectClient.java:50` | aceito como limite |
| 26 | 8 Build | `pack.mcmeta` com `pack_format` 8 (herdado do modelo); README aponta GIFs que ainda não existem | baixa | `common/src/main/resources/pack.mcmeta:4`, `README.md:11` | GIFs já estão no Item 22; `pack_format` inofensivo (o mod não tem dados próprios) |

### O que foi conferido e está certo

- **Rede:** todo pedido do cliente passa por `try/catch` (pacote ruim não derruba o servidor), confere `containerId`
  + `stillValid`, jogador vivo e fora do espectador, `canPlayerUseBlock` (claims) e `FeatureGate`. Textos e listas
  têm teto no codec (rótulo 256, lista de prefs 36, painel 512, travas 256). Só operador/dono mexe em cadeado.
- **Itens:** toda transferência é "simular → aplicar"; comparação sempre com componentes (`isSameItemSameComponents`);
  dois jogadores no mesmo baú são barrados (`openedByAnother`); devolução da bancada ao fechar, sair e parar o
  servidor. `raceFuzz` e `benchFuzzKeepsEveryItem` conferem a soma de itens depois de cada ação.
- **Fontes:** a varredura usa o mapa de block entities dos chunks já carregados (sem cache próprio para ficar velho),
  baú duplo conta uma vez, fornalha/funil/poções/etc. nunca são fonte nem destino, baú trancado e de loot ficam de fora.
- **Loaders:** NeoForge registra os mesmos pacotes, teclas, comandos e eventos que o Fabric; metadados com versão
  1.0.0, MIT, ícone; `release.yml` acha os dois jars e ignora `-sources`.
- **Config:** pt_br e en_us com as mesmas chaves; toda função tem botão + cadeado; valores fora da faixa e JSON
  quebrado são corrigidos sem derrubar.
- **Mixins:** nenhum `@Overwrite` nem `@Redirect`; mixins de tela só em `client`; Litematica é opcional de verdade.

### Onde ainda falta teste (para itens futuros)

Morte e troca de dimensão com a bancada aberta; itens com componentes (encantado, nomeado) nos fuzzers; Pull/Refill
com shulker do inventário cheia; Organizar e travar slot em tela falsa de outro mod; NeoForge (sem GameTests).

## Desempenho

Medido pelo GameTest `StashLinkGameTests` (289 containers, raio 32; linha `[STASHLINK-PERF]` no log do CI, passo
`:fabric:runGameTest`). Teto do projeto: 5 ms por operação e 0,05 ms parado.

Valores em microssegundos, média / pior de 100 repetições, no runner do GitHub (máquina compartilhada: o "pior" varia
muito de uma execução para outra por causa de JIT e coletor de lixo).

| Operação | Antes (`main`, CI de `cefa146`) | Depois (este PR, CI de `2477e56`) |
|----------|-------------------------------|-----------------------------------|
| find | 371 / 1394 | 692 / 4035 |
| findAll | 181 / 553 | 319 / 3119 |
| quickStack (N) | 2090 / 8086 | 1654 / 4730 |
| refill (pior caso) | 414 / 1069 | 236 / 733 |
| pull (item ausente) | 736 / 4135 | 500 / 2569 |
| idleTick | 1 | 0 |

Todas as médias continuam abaixo de 5 ms (5000 µs). As diferenças entre as colunas são ruído do runner (umas subiram,
outras desceram, sem mudança no caminho medido); uma segunda execução no mesmo CI deu, por exemplo, quickStack
2801 / 107149, com um pico isolado de 107 ms no pior caso. Isso mostra que o "pior" no CI não serve como medida fina,
e por isso o Item 25 pede `spark` num servidor de verdade.

**Nenhuma otimização entrou nesta revisão.** A regra é "sem ganho medido, não entra", e este ambiente não consegue
rodar o jogo (rede bloqueada para os repositórios do Fabric/NeoForge). As correções não mexem no caminho quente; a única
mudança na varredura (`getChunkNow` na outra metade do baú duplo) é uma consulta a um mapa já carregado. Os alvos de
medição (bancada aberta com 289 baús cheios, N/Organizar com mod de claims, fontes só em baús com muitos jogadores)
ficaram no Item 25.
