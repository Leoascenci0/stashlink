# Arquitetura (rascunho inicial)

## Princípio central: o servidor manda no inventário

O cliente **não consegue** ver o conteúdo de uma shulker que está só no inventário (o cliente só recebe o
conteúdo quando a GUI da shulker é aberta). E mesmo que visse, quem valida "esse item existe e foi consumido" é
o servidor. Logo, toda mudança de item acontece no **servidor**; o cliente só pede (teclas, Litematica).

Consequência boa: as funções passivas (F1 e F3 — reabastecer a mão) funcionam **só com o mod no servidor**, com
cliente vanilla. As teclas (F4/F5) e Litematica (F2) exigem o mod também no cliente.

## Estrutura de módulos

```
common/     lógica pura: ItemSource, storage de shulker, quick stack, pacotes (definição), config
fabric/     entrypoints, registro de pacotes/keybinds, mixins específicos Fabric
neoforge/   idem NeoForge (forge/ só se decidido no Item 0)
```

Regra: tudo que dá para escrever sem tocar API de loader fica em `common`. Loader-specific = só "cola".

## Peças principais

- **`ItemSource`** — abstração "de onde posso tirar item": shulker no inventário, shulker no chão, baú no raio.
  Novas fontes = nova implementação, sem mexer nos consumidores.
- **Refill hook** — ponto onde o stack da mão esgota (mixin em `ItemStack`/`Player` shrink ou evento do loader).
- **Pacotes custom** — `PullItemRequest` (Litematica), `QuickStackRequest` (N), `LootAllRequest` (W).
  Sempre validados no servidor: distância, permissão, tamanho do pedido.
- **Índice de containers no raio** — cache por chunk, invalidado por eventos; nunca varredura bruta por uso.
- **Compat Litematica** — soft dependency, isolada em pacote próprio; carregada só se o mod existir.

## Riscos conhecidos

| Risco | Mitigação |
|-------|-----------|
| Dupe/perda de item em corrida | Operações atômicas (simular → aplicar), testes de conservação de itens (Item 5) |
| Performance com muitos containers | Índice + raio com teto no servidor (Item 6) |
| Anti-grief/claims | Checar interação permitida antes de tocar container alheio |
| Litematica muda API entre versões | Compat isolada, fixar versão de teste, documentar |
| Tecla W conflita com movimento | Só em telas de container sem campo de texto; configurável |
| Componentes de item mudam por versão | Camada única de acesso ao conteúdo de shulker (Item 2) |

## Decisões (Item 0 — 2026-09-29)

Contexto: desde 2026 o Minecraft usa numeração `26.x` (sem o "1."). Toda a linha 26.x exige Java 25.

- **Versão do Minecraft: 26.1.2.**
  - Regra original do roadmap ("a mais recente que o Litematica suporta") daria 26.3, mas nela o Litematica só
    existe em Fabric (0.29.1); não há port NeoForge (Forgematica) para 26.3, e o NeoForge 26.3 só tem builds beta.
  - Na 26.1.2 há Litematica em Fabric (0.27.14, release) **e** em NeoForge (Forgematica 0.5.1, beta), o
    MultiLoader-Template tem branch `26.1.2` e a linha NeoForge 26.1 é a mais madura.
  - Migrar de versão depois é barato: lógica em `common/`, loaders só como "cola".
  - Descartadas: 26.3 (sem Litematica em NeoForge), 26.2 (meio-termo sem ganho claro), 1.21.11 (Java 21, versão antiga).
- **Java: 25** (JDK Temurin). Exigido pela linha 26.x e pelo template.
- **Loaders: Fabric + NeoForge.** Forge clássico só se houver demanda real (ecossistema migrou para NeoForge).
- **Template: MultiLoader-Template** (Jared), branch `26.1.2` — projetos `common` + `fabric` + `neoforge`, menos
  "mágica" que o Architectury.
- **Licença: MIT.**
- **Nome / mod id: StashLink / `stashlink`.** Verificado em 2026-09-29: sem mod com esse nome no Modrinth nem no
  CurseForge (existem apenas "Stashlight" e "Stash", diferentes).
- **Litematica:** oficial só Fabric; em NeoForge apenas o port não oficial Forgematica (+ MaFgLib), sempre atrás
  em versão. Consequência: compat com Litematica (Item 7) é por loader e só testada onde houver build.
- **Estado da máquina:** JDK 25 ainda não instalado; instalação fica no Item 1.

## Item 2 — Armazenamento de shulker (2026-09-29)

- Classe única de acesso: `storage/ShulkerStorage` (em `common`). Lê/grava o componente `container`
  (`ItemContainerContents`); sempre 27 slots, posições preservadas; cópias na leitura.
- Operações atômicas: calcula numa cópia e grava uma vez. `insert` devolve o que não coube (nunca altera o stack
  recebido) e recusa shulker dentro de shulker (`canFitInsideContainerItems`).
- Shulker vazia grava `ItemContainerContents.EMPTY` (o valor padrão), não remove o componente — senão ela deixa
  de ser igual a uma recém-craftada.
- Testes (JUnit 5, `./gradlew :common:test`): o plugin do `common` não expõe o Minecraft ao `src/test`, então o
  `common/build.gradle` reaproveita o classpath principal. No 26.x é preciso ligar os componentes padrão dos
  itens no setup (`DATA_COMPONENT_INITIALIZERS`) — ver `ShulkerStorageTest`.

## Item 4 — Reabastecimento da mão (2026-09-29)

- **Como detecta:** sem mixin. `RefillService.tick` roda no fim de cada tick do servidor (Fabric:
  `END_SERVER_TICK`; NeoForge: `ServerTickEvent.Post`) e um `HandWatcher` por mão compara "o que havia no tick
  anterior" com "o que há agora". Mão que tinha item, no mesmo slot da hotbar, e agora está vazia = esgotou.
  Serve igual para colocar bloco, comer, arremessar e ferramenta quebrada, nos dois loaders.
- **Quando NÃO age:** criativo/espectador, jogador morto, GUI de container aberta (`containerMenu !=
  inventoryMenu`, cobre a shulker aberta), item na "mão do cursor", troca de slot da hotbar, Q (contador de
  estatística `DROP` mudou) e troca de mão com F (o item apareceu na outra mão).
- **O que puxa:** um stack cheio (`getMaxStackSize`) do mesmo item. Ferramenta quebrada: ignora o desgaste e
  exige o resto igual (encantamentos, nome).
- **Recipiente vazio:** último balde de água/lava/leite, sopa ou poção que vira balde/tigela/garrafa também
  reabastece: o recipiente vai para o inventário e o item original entra na mão. Sem lugar no inventário, o
  estoque volta à shulker e nada muda (`PlayerShulkerSource.give`).
- **Sem mão vazia visível:** um mixin em `ServerPlayer.tick` (início do tick, antes de o servidor enviar o
  inventário ao cliente) chama `RefillService.tickPlayer`. O tick do servidor continua como reserva (é
  idempotente). Efeitos que terminam dentro do próprio tick (comer) podem levar 1 tick.
- **Limite conhecido:** mover o último item da mão pela tela de inventário com shift-click pode puxar um stack
  extra.
- **Sincronia:** `setItemInHand` altera o slot do inventário; o servidor envia a mudança ao cliente no envio
  normal de inventário. Cliente vanilla funciona (o mixin roda só no servidor).
- Testes: `RefillTest` (vigia, lógica de refill, swap). Servidores dedicados Fabric e NeoForge sobem com o mod e o mixin sem erros.

## Item 6 — Raio de fontes (2026-09-29)

- **Novas fontes:** `ContainerSource` (qualquer `Container`: shulker colocada, baú, barril) e `NearbyContainers`
  (acha os containers perto do jogador). Prioridade no refill: shulkers do inventário → shulkers colocadas →
  baús/barris (esses só com `includeChests`, desligado por padrão).
- **Índice:** em vez de cache próprio, usa o mapa de block entities que cada chunk carregado já mantém
  (`LevelChunk.getBlockEntities`). O jogo o atualiza sozinho (colocar/quebrar/carregar/descarregar), então não há
  cache para ficar velho — e cache velho aqui significaria item duplicado ou fantasma. Custo: percorrer só as
  block entities dos chunks no raio, nunca o cubo de blocos; só quando um stack esgota, e só se as shulkers do
  inventário não bastaram (`LazyItemSource`). Chunk descarregado não é carregado à força.
- **Raio:** padrão 8, teto do servidor `maxRadius` (padrão e máximo 64) imposto em `StashLinkConfig.effectiveRadius`.
- **Config:** `config/stashlink.json` (Gson, `IPlatformHelper.getConfigDir`), lido no `StashLink.init()` e por
  `/stashlink reload`. Valores fora da faixa são corrigidos; arquivo quebrado mantém os valores atuais e não é
  sobrescrito. O servidor é a autoridade: a tela (`StashLinkConfigScreen`) só edita quando há servidor local
  (mundo único/LAN); em servidor remoto é somente leitura.
- **Proteção:** (1) o jogo: container trancado e baú de loot ainda não aberto são ignorados (ler baú de loot o
  geraria à distância); (2) mods de claim: `IPlatformHelper.canPlayerUseBlock` dispara o evento de "usar bloco"
  do loader (Fabric `UseBlockCallback`, NeoForge `PlayerInteractEvent.RightClickBlock`) e respeita o
  cancelamento. Só é perguntado para containers que **têm** o item, uma vez por refill.
- **Desfazer:** `ItemSource.give` (novo, padrão "não guarda nada"). `ContainerSource.give` devolve só aos
  containers de onde saiu item naquela operação. `RefillService` usa `sources.give` no caminho "sem lugar no
  inventário".
- **Limites conhecidos:** baú duplo conta cada metade como container; baú com bloco em cima ainda serve (o
  vanilla não abriria); o evento sintético de "usar bloco" pode ser visto por outros mods como uma interação.
- **Não medido:** o critério "sem queda de TPS com 200+ containers" não foi medido em servidor real (só há testes
  unitários de lógica). Ver handoff.

## Item 7 — Integração Litematica (2026-09-30)

- **Investigação (Litematica 0.27.14, código de `sakura-ryoko/litematica`, branch `LTS/26.1`):**
  - Existe uma API de eventos de pick block (`ISchematicPickBlockEventListener`, `SchematicPickBlockEventHandler`),
    feita para mods de terceiros. **Não serve:** só é chamada pela tecla de pick block
    (`WorldUtils.doSchematicWorldPickBlock`); o **Easy Place** chama `InventoryUtils.schematicWorldPickBlock`
    direto, sem passar pelos eventos.
  - `InventoryUtils.schematicWorldPickBlock(ItemStack, BlockPos, Level, Minecraft)` é o funil comum: pick block
    (depois dos eventos) e as duas rotas do Easy Place terminam nele. Assinatura conferida com `javap` no jar
    0.27.14 do Modrinth. Se o item não está no inventário, o Litematica (opção `PICK_BLOCK_SHULKERS`) põe a
    *shulker* inteira na mão, o que não queremos.
- **Decisão:** um mixin no início desse método (`InventoryUtilsMixin`). Alvo por nome (`targets = "..."`), então
  o Litematica **não** é dependência de compilação; `require = 0` + plugin de mixin
  (`LitematicaMixinPlugin`, liga só se `litematica` estiver carregado) tornam a dependência opcional de verdade.
  Assinatura mudou numa versão futura → a integração se desliga sozinha (não derruba o jogo).
- **Fluxo:** cliente sem o item em nenhum slot (nem mão secundária) → `LitematicaPull.onPickBlock` manda
  `PullItemRequest(item, quantidade)` e **cancela** o método do Litematica (evita a shulker ir para a mão). Já tem
  o item → não faz nada, o Litematica troca de slot como sempre. Servidor sem o StashLink
  (`ClientPlayNetworking.canSend` falso) → não cancela, comportamento original. Freio no cliente:
  `PullRequestThrottle` (1 pedido por item a cada 6 ticks; o Easy Place pede todo tick).
- **Servidor (`PullItemService`, tudo revalidado):** ignora item inválido/quantidade < 1; quantidade limitada ao
  stack cheio; ignora jogador morto, criativo, espectador ou com container aberto; no máximo 1 pedido a cada 4
  ticks por jogador. As fontes são as mesmas do refill (`PlayerSources`: shulkers do inventário → shulkers no
  raio → baús), então raio, teto de 64, trancas e claims valem igual. O item vai para a hotbar
  (`PullLogic`: slot selecionado se livre → slot com o mesmo item → primeiro vazio; hotbar cheia de outras
  coisas → nada é movido) e o slot é selecionado (`ClientboundSetHeldSlotPacket`).
- **Loaders:** o pacote é registrado em Fabric (`PayloadTypeRegistry`) e NeoForge (`optional()`, clientes sem o
  mod entram). O mixin e o envio ficam **só no Fabric**: Litematica oficial só existe lá. NeoForge (Forgematica
  0.5.1 beta) tem o receptor pronto, mas não há lado cliente — não testável sem build.
- **Não testado no jogo:** compilação, 9 testes novos (`PullLogicTest`) e assinatura do alvo verificados; o
  fluxo real (schematic carregada + Easy Place) precisa de um teste manual com Litematica 0.27.14 + MaLiLib
  0.28.12 num cliente Fabric.

## Atualização de versão: Minecraft 26.3 (2026-09-30)

- **Decisão:** o projeto passa do Minecraft 26.1.2 para **26.3** (Fabric API 0.161.0+26.3, loader 0.19.5,
  NeoForge 26.3.0.39-beta, NeoForm 26.3-1). Isso supera a decisão anterior (26.1.2, por causa do Forgematica).
- **Litematica é Fabric-only na 26.3:** Litematica 0.29.1 tem build para Fabric; ainda não há Forgematica para
  NeoForge 26.3. A integração (Item 7) segue só no Fabric; reavaliar quando o Forgematica sair.
- **Regra do compat layer: nenhum uso direto de API do Minecraft sujeita a mudar fora de `compat/`.** No `common`:
  `compat/mc/McCompat` e `ClientCompat` (testes: `TestCompat`). Uma atualização quebra em poucos pontos conhecidos.
  Passo a passo em [UPDATING.md](UPDATING.md).

## Item 10.2 — Modo cliente (servidor sem o mod)

- **Dois modos.** (1) *Servidor com o mod*: pacotes próprios, o servidor valida e mexe no inventário (tudo acima).
  (2) *Modo cliente*: o servidor não conhece o StashLink (ex.: Realms). `ClientMode.active()` é verdadeiro quando há
  servidor remoto **sem** o mod **e** a opção `clientModeEnabled` (em `stashlink-client.json`, padrão ligada) está
  ligada. Nesse caso o cliente faz o trabalho sozinho; com o mod no servidor o comportamento não muda.
- **O que o modo cliente faz.** W (puxar tudo do container aberto), N (guardar em containers próximos que já têm o
  item) e reabastecer a mão (Item 10.3, abaixo). Para cada container: abre (interação
  normal de bloco), move itens por cliques de inventário, fecha.
- **Decisão: por que cliques de inventário.** O cliente não pode editar inventário; só pode pedir ao servidor o que
  um jogador pode pedir. Clique de inventário é exatamente isso, e o **servidor vanilla valida tudo** (distância,
  permissão, claims, regras do container). É a mesma abordagem do Litematica. Assim o mod não precisa de pacote
  próprio e nunca manda pacote desconhecido a servidor que não o conhece.
- **Sem duplicar nem perder item.** Depois de cada clique o mod confere o resultado (o que ficou no slot e no
  cursor) antes do próximo passo. Se o servidor recusou ou algo não bate, o mod para, devolve o que está no cursor
  e fecha o container, em vez de seguir às cegas.
- **Limites.** Alcance = o de interação do jogo (~4,5 blocos), não o raio do servidor (até 64). O conteúdo de um
  container só é conhecido ao abri-lo. Sem reabastecer a partir de shulker **no inventário**. Proteções/claims do
  servidor valem sozinhas (se não abre, o mod não abre). Containers abrem e fecham de forma visível.
- **Tela de config.** Botão "Modo cliente" em servidor remoto. Com o modo ativo, a tela mostra um aviso e esconde o
  raio do servidor e "usar baús e barris" (não se aplicam); os slots travados continuam valendo, usados localmente.
- **Não testado em jogo ainda** (roteiro no `ROADMAP.md`, Item 10.2).

## Item 10.3 — Modo cliente: reabastecer a mão

- **Gatilho.** `ClientModeEngine.tick` roda o `HandWatcher` (o mesmo do servidor) na mão principal a cada tick. Esgotou
  = estava com item e agora está vazia, no mesmo slot da hotbar. Não conta (`ClientMoveLogic.refillAllowed`): tela
  aberta, agachado, morto, criativo/espectador, cursor com item, Q apertada (tick atual ou anterior; um toque
  rapidíssimo pode escapar, o pior caso é um reabastecimento a mais). Troca de mão com F é filtrada por
  `RefillLogic.movedToOtherHand`. Um `RefillJob` por vez, sem competir com sessão W/N, pausa de 10 ticks.
- **Fonte.** Containers do alcance de interação (`ClientContainers.find`), ordenados pelo `ContentsCache` (tem o item,
  nunca visto, visto sem o item; depois o mais perto), no máximo 6. O cache é só dica de ordem: nunca decide que um
  container "não tem".
- **Como move.** Um clique **SWAP** do slot do container com o slot da hotbar selecionado: com a mão vazia, o stack
  inteiro vai para a mão sem passar pelo cursor. Item igual = mesmo item e componentes, **ignorando desgaste**
  (`ClientMoveLogic.sameForRefill`, como `RefillLogic`). A cada tick o job lê o menu: mão com o item = pronto; 4
  ticks sem confirmação = slot recusado (tenta outro); mão com outro item ou hotbar trocada = para sem clicar.
  Nunca deixa container aberto (`ContainerSession` do 10.2). Nunca manda pacote próprio.
- **Limites.** Não faz a troca balde/tigela/garrafa do modo servidor (só repõe com a mão vazia). Shulker no
  inventário fica fora. Alcance de interação do jogo, não o raio do servidor.
- **Não testado em jogo ainda** (roteiro no `ROADMAP.md`, Item 10.3).

## Testes de carga/multiplayer (Item 11)

- **Harness.** GameTest do Fabric em `fabric/src/gametest` (source set separado, fora do jar). Roda com
  `./gradlew :fabric:runGameTest` (~25 s; na 1ª vez precisa de internet para baixar Carpet e Upgraded Iron Chests,
  carregados só nos testes). Servidor real, baús reais, jogadores simulados em **sobrevivência** (o mock do vanilla é
  criativo e o mod ignora criativo). Classes: `Lab.java` (montagem do cenário) e `StashLinkGameTests.java`.
- **Cobertura (14 cenários).** Dois jogadores em N/W no mesmo baú, N pulando baú aberto por outro, W parcial com
  inventário cheio, reabastecer com baú aberto, queda com baú aberto, baú duplo = 1 container, W com slots travados
  por jogador, 289 containers, fuzz de corrida (3000 ações aleatórias; soma de itens conferida após cada uma), Carpet
  e baús de terceiros. Teste de mutação: um dupe injetado em `ContainerSource.take` foi pego por 2 testes.
- **Política de concorrência.** O servidor roda tudo numa thread, então não há corrida de dados; a invariante "soma de
  itens não muda" se mantém. Só a tecla N pula baú aberto por outro jogador; reabastecer e pedir item podem tirar de
  baú aberto por outro (como um funil). Baú com bloco em cima ainda é fonte (limite documentado).
- **Desempenho** (289 containers, raio 16, 100 repetições, média/pior em µs): find 181/1438, findAll 75/231,
  N 297/1770, reabastecer 226/7833 (pior caso = JIT frio), pedir item ausente 403/2263, tick parado ~0. Orçamento:
  média < 5 ms (10% de um tick de 50 ms). Mede custo por operação, não TPS global; fecha o "sem queda de TPS com
  200+ containers" do Item 6.
- **Bug achado:** `LootAllService` (W) usava os slots travados globais e ignorava os por jogador (Item 10.1); agora usa
  `PlayerPrefsStore.isSlotLocked(player, slot)`.
- **Compatibilidade.** Carpet 26.3 e Upgraded Iron Chests (estendem o baú vanilla): tudo passa. Sophisticated
  Storage/Backpacks 26.3 só existem para NeoForge: sem harness NeoForge, **não testado**. Litematica/Tweakeroo/MaLiLib
  (só cliente): Litematica 0.29.1 + MaLiLib 0.30.2 + Tweakeroo 0.30.1 (só cliente) carregam junto do StashLink no menu principal, sem erro de Mixin nos logs (com o fix do crash do Easy Place). NÃO testado em jogo: Easy Place/pick block precisa de mundo e clique humano (roteiro: `./gradlew :fabric:runClient -PcompatMods`, schematic + Tweakeroo easy place, bloco na hotbar e um baú com mais dele; conferir colocação, reabastecer ao esgotar e pick block sem duplicar).
- **Não testado:** Realms/anticheat e timeouts do modo cliente (precisa de jogo real; roteiros do 10.2/10.3), e
  GameTest no NeoForge.

## Item 18 — Litematica: trocar o bloco no mesmo slot

- **Problema.** Cada pedido do Litematica (`PullItemService` → `PullLogic.pullIntoHotbar`) achava um slot da hotbar
  e selecionava, sem devolver nada. Com 30 blocos diferentes, a hotbar enchia de itens puxados.
- **Ideia.** O servidor lembra, por jogador, **qual slot é do mod** (`PulledSlot`). No pedido seguinte, se o bloco é
  outro, o item antigo é devolvido e o novo ocupa o mesmo slot (`PullLogic.pull` + `owned`/`returnTo`).
- **Como lembrar a origem (decisão).** Não guardamos o objeto do container. Guardamos uma `Origin`: dimensão,
  posições de bloco (duas para baú duplo) e se algo saiu de shulker do inventário. Dois motivos: (1) o baú pode ter
  sido quebrado ou o jogador ter se afastado entre os pedidos, então na hora de devolver o container é **procurado de
  novo** (`NearbyContainers.find`) e revalidado, em vez de confiar num objeto antigo (inserir num baú quebrado
  perderia o item); (2) um objeto de container preso num mapa ligaria a memória ao mundo inteiro (o mapa é por
  jogador, com chave fraca, e o valor não pode alcançar o jogador). `ContainerSource.Entry` ganhou `where` (as
  posições) como identidade estável: o baú duplo vira outro `CompoundContainer` a cada varredura, então comparar
  objetos não serviria. `ContainerSource.returningTo` monta a fonte de devolução só com os containers de origem.
- **Para onde vai o item antigo (ordem).** (1) origem: shulkers do inventário e containers de origem que **ainda**
  estão no raio, liberados (claims) e sem outro jogador com a GUI aberta (mesma regra da tecla N); (2) mochila
  (slots 9-35, `StackListSink`); (3) se nada aceitar, o item **fica no slot** e o novo vai para outro slot, como antes
  do item 18. Nunca no chão (a soma de itens é conferida nos testes: um drop sairia da soma) e nunca perdido: o que
  sai do slot é exatamente o que alguma fonte aceitou (`give` devolve a sobra).
- **Só o que o mod pôs.** `PulledSlot.count` é o que o mod colocou. Se o jogador gastou parte, volta o que sobrou; se
  juntou mais do mesmo item no slot, esse extra fica. O registro some se o slot já não tem o item do mod (jogador
  trocou ou esvaziou), se o slot foi travado, ou se parte do item não coube em lugar nenhum (o que ficou é do
  jogador). Slot com item que o jogador pôs antes do pedido nunca vira "do mod": só slot vazio vira.
- **Escolha do slot.** Se já há um slot com o mesmo item e espaço, ele ganha (como antes). Senão, o slot do mod
  (troca no lugar); só então slot vazio. Sem registro, o comportamento é idêntico ao anterior (`PullLogicTest`).
- **Pedido impossível não esvazia a mão.** A troca só começa se `source.available(item) > 0`.
- **Quando devolver (decisão do Eliel, 2026-10-03).** Só ao pedir outro bloco. Descartadas: ao trocar de slot (um
  scroll por cima do slot faria o bloco sumir da mão; exigiria vigiar a hotbar todo tick) e ao fechar o Litematica
  (pacote novo e mixin extra, frágil a cada versão dele). Custo: ao terminar de construir sobra 1 stack do mod na
  hotbar, igual ao pick block normal.
- **Limite conhecido.** Se o item saiu de uma shulker do inventário, volta para a primeira shulker do inventário com
  espaço, não necessariamente para a mesma (todas estão no inventário do jogador; nada se perde).
- **Modo cliente.** Não se aplica: o modo cliente (10.2/10.3) só faz W, N e reabastecer a mão, não puxa itens para a
  hotbar a pedido do Litematica; sem pull não há o que trocar. O pedido do Litematica em servidor sem o mod continua
  sendo o comportamento original do Litematica.
- **Testes.** `PullSwapTest` (unitário, 8), `SwapGameTests` (8 cenários no servidor real, incl. fuzz com 2 jogadores)
  e mutação: dupe, perda e "ignorar baú aberto por outro" injetados e pegos. 22 GameTests no total.
- **Mudança em `ContainerSource.give`.** Agora também consulta a permissão do container (claims), não só "tocado":
  na devolução o jogador pode ter andado para dentro de uma área protegida desde o pedido anterior. No reabastecimento
  isso não muda nada (a permissão já foi consultada e lembrada no `take`).

## Item 13 — Slot de baú travado (reservado) com um item

- **Investigação (como os outros resolvem).** O Sophisticated Storage tem "memória de slot": o jogador marca um slot
  com um item, o slot vazio mostra um ícone fantasma e o item passa a preferir/ficar nesse slot. O jogo base não tem
  nada parecido, mas dá as peças: (1) a "tela" que o jogador vê é um **menu** (`ChestMenu`) cujos slots são objetos
  `Slot`; clique, shift-clique, arrastar e troca por número perguntam `Slot.mayPlace(stack)` antes de colocar
  (confirmado com `javap`: `moveItemStackTo` só consulta no passo dos slots vazios, o passo de "completar stack igual"
  não consulta; por isso a trava vale para slot **vazio** ou com o próprio item); (2) o dado do baú mora na *block
  entity* e é gravado por `saveAdditional` / lido por `loadAdditional`, então qualquer coisa gravada ali sobrevive a
  reiniciar e some junto com o bloco; (3) um cliente vanilla só vê o que o servidor manda no menu: se a "prévia" nunca
  for item de verdade, ele vê o slot vazio.
- **Decisões do Eliel (2026-10-03).** (a) **Reservar de verdade**: o slot só aceita o item dele (clique, shift-clique,
  troca por número, arrastar, N, devolução do Litematica); N e guardar preferem o slot reservado. (b) **Alt + clique**
  no slot (qualquer botão) trava/destrava. Descartadas: "só mostrar e preferir" (o slot não ficaria reservado de fato),
  clique do meio (conflita com mods e com o criativo) e tecla dedicada (mais uma tecla).
- **A prévia é só metadado, nunca item.** O baú guarda `slot → tipo de item` (`SlotLockHolder`); o slot continua
  **vazio** de verdade. Por isso: clicar, shift-clicar, clique duplo, arrastar, tecla W e funil não têm o que pegar —
  não existe item falso para duplicar. Cliente sem o mod enxerga o slot vazio e nunca recebe nada. Só o tipo do item é
  guardado (não os componentes): um slot reservado para espada aceita qualquer espada.
- **Onde a memória fica.** Um mixin (`BaseContainerBlockEntityMixin`) faz toda block entity de container (baú, barril,
  shulker colocada, funil...) implementar `SlotLockHolder` e grava/lê a lista `stashlink_slot_locks` no mesmo NBT do
  bloco (Codec: `slot` + `item`). Vantagens sobre um arquivo à parte do mod: sobrevive a reiniciar, é copiada com o
  mundo, e **some quando o bloco é quebrado** (um baú novo no mesmo lugar não herda a reserva — teste
  `brokenChestTakesItsLocksWithIt`). Funciona igual nos dois loaders (o mixin é do `common`). Descartadas: Data
  Attachment do Fabric/NeoForge (duas implementações, uma não testável aqui) e `SavedData` por posição (ficaria órfã).
  **Baú duplo:** são duas block entities; cada metade guarda as suas, com índice **local** (0-26). Quem une as duas é
  `SlotLocks.locate`, que abre as metades do `CompoundContainer` por um accessor (`CompoundContainerAccessor`) — assim a
  ordem das metades no container do jogo (que pode ser inversa à do teste) não importa.
- **Quem barra o item errado.** `SlotMixin` (em `Slot.mayPlace` e `ShulkerBoxSlot.mayPlace`, que sobrescreve sem chamar
  o pai) devolve falso se o slot está reservado para outro item; `ContainerInsert` (usado por N, devolução do
  Litematica e reabastecer) faz a mesma checagem em `capacity` e `insert`. As duas usam `SlotLocks.mayPlace`.
  No **cliente** o mesmo mixin consulta `ClientSlotLocks` (o que o servidor contou), então o clique recusado nem
  "pisca"; o servidor continua sendo quem decide.
- **N e W.** `ContainerInsert.insert` agora tem 3 passos: (0) slots reservados para aquele item, (1) completar stacks
  iguais, (2) slots vazios. `QuickStackLogic` ganhou duas rodadas: primeiro só containers que têm **reserva** para o item
  (mesmo vazios e mesmo havendo outro baú mais perto que já contém o item), depois a regra de sempre. A permissão
  (claims) é perguntada no máximo uma vez por container. A tecla W só tira itens: o slot esvazia e a reserva continua
  (teste `lootAllKeepsTheReservation`).
- **Validação no servidor (`SlotLockService`).** Pedido `LockSlotRequest(containerId, menuSlot)`; o servidor confere:
  jogador vivo e não espectador; é **este** o menu aberto (`containerId`); menu de baú/barril/shulker; `stillValid`
  (distância); índice válido; slot do container (não do jogador) e que sabe guardar trava; claims
  (`canPlayerUseBlock` em cada block entity); **nenhum outro jogador com o container aberto** (mesma regra da tecla N;
  mensagem "outro jogador está com este container aberto"). O item da trava é decidido pelo servidor: o do slot, ou o
  do cursor se o slot está vazio (reservar antes de guardar). Slot já travado: destrava. Erro no tratamento nunca
  derruba o servidor.
- **Sincronia com o cliente (`SlotLockSync`).** A cada tick, para cada jogador com menu suportado aberto, o servidor
  compara o "retrato" das travas com o último enviado e só manda `SlotLocksSync` se mudou (abrir o menu, alguém
  travar). `IPlatformHelper.sendIfSupported` só envia a quem tem o canal registrado (Fabric: `canSend`; NeoForge:
  `hasChannel`), então cliente vanilla nunca recebe pacote desconhecido. O cliente (`SlotLockClient`) guarda a lista
  por `Container` do menu e **desenha** a prévia (`AbstractContainerScreenMixin` depois de `extractSlot`): item
  esmaecido + pontinho azul no canto (o mesmo pontinho no slot reservado que tem item). Alt + clique é
  tratado no `mouseClicked` (o clique normal é engolido; sem o mod no servidor ele segue como sempre).
- **Limites conhecidos.** (1) **Funil/hopper e outros mods que mexem direto no container** não respeitam a reserva (o
  `Slot.mayPlace` só vale para menus, e o `canPlaceItem` do baú não é sobrescrevível sem um mixin por classe); o que
  entra assim fica no slot e pode sair normalmente, nada se perde. (2) Shift-clique **de fora** para o baú não prefere o
  slot reservado (usa o primeiro vazio permitido); só N/guardar preferem. (3) Os componentes do item não entram na
  reserva (só o tipo). (4) Shulker box quebrada e pega de volta perde as reservas (o item da shulker só guarda o
  conteúdo). (5) Baú de ender e contêineres de outros mods (não são `BaseContainerBlockEntity`) não suportam a trava.
  (6) **Upgraded Iron Chests** (mod carregado nos testes do Item 11) tem "auto-compactação": depois de cada clique ele
  reorganiza o baú vanilla direto no container, o que ignora a reserva (e mexe nos slots nos testes de clique). Os
  testes de clique do Item 13 desligam a compactação do jogador de teste (por reflexão). (7) Sem o mod no servidor não
  há trava (só o modo cliente de W/N/reabastecer; ele não conhece reservas).
- **Testes (36 GameTests no total, +14 novos em `LockGameTests`, todos com cliques reais `menu.clicked`).** Travar e
  destravar (slot com item, vazio com item no cursor, vazio sem nada, slot do jogador, índice inválido, menu já
  fechado); reserva recusa outro item por clique, troca por número e shift-clique; a prévia nunca vira item (clicar,
  shift, clique duplo, clonar, jogar fora, W, funil via `removeItem`); N prefere o slot reservado, N alimenta uma reserva
  vazia mesmo havendo baú mais perto, N nunca usa reserva de outro item; W mantém a reserva; baú duplo (metade certa,
  N, clique); barril e shulker; gravar e recarregar o bloco (o mesmo caminho do disco) e destravar também persiste;
  quebrar o baú leva as travas; 2 jogadores no mesmo baú (ninguém muda trava com o baú aberto por outro); pacotes
  (ida e volta, e cliente sem o mod não recebe); **fuzz de 3000 ações com 2 jogadores** (N, W, abrir, fechar,
  travar/destravar ~135 vezes, clique, shift-clique, troca, clique duplo, arrastar) conferindo **a soma de cada item
  depois de CADA ação** e a invariante "slot reservado só contém o seu item". **Mutação:** 4 defeitos injetados e
  pegos — `Slot.mayPlace` ignorando a trava (4 testes), dupe em `ContainerInsert.insert` (9, inclusive os testes dos
  Itens 11 e 18), N ignorando a reserva (2) e a prévia virando item de verdade (9).
- **Achados no caminho.** O passo "completar stack igual" do jogo ignora `mayPlace` (por isso a invariante vale para
  slot vazio ou com o próprio item). A auto-compactação do Upgraded Iron Chests só apareceu porque o clique "caía" no
  primeiro slot livre em vez do slot clicado.
- **Não testado em jogo (cliente).** O desenho da prévia, o Alt + clique e a sincronia precisam de cliente gráfico; o
  `runClientGameTest` do Fabric chegou a abrir o mundo, mas o cliente caiu de forma nativa (erro do driver/JVM, também
  com o Carpet removido) nesta máquina, então a verificação visual fica no roteiro manual do `ROADMAP.md`. As assinaturas
  dos alvos de mixin do cliente (`mouseClicked`, `extractSlot`, `getHoveredSlot`) foram conferidas com `javap`. O lado
  NeoForge compila, mas não tem harness.

## Item 14 — Nome do armazenamento (rótulo) com resumo, ícones e holograma

- **Investigação (como o jogo e os outros resolvem).** (1) O jogo base já dá nome a um container: `BaseContainerBlockEntity`
  guarda `name` (o "nome personalizado" de um baú renomeado na bigorna) e ele vira o **título da tela** do baú — mas
  não aparece olhando para o bloco, não tem resumo e não tem emoji. O Sophisticated Storage (de memória, não testado
  aqui) tem nome e ícone de "memória" na interface do baú, também só dentro da tela. Por isso o mod tem um **rótulo
  próprio**, separado do nome da bigorna (que continua valendo no título da tela). (2) **Fonte do jogo (conferido no
  jar e no unifont 17 do 26.3).** A fonte padrão tem uns 60 símbolos úteis (❤ ⭐ ⚡ ✔ ❌ ⚔ ☠ ⛏ ❄ ☀ ☁ ♪ ✉ ⌛ ⚓...) e o
  unifont cobre quase todo o plano básico, mas **não tem nenhum emoji colorido** (nenhum dos blocos U+1F300–1FAFF:
  📦🔥🍎😀). Digitar 📦 mostraria um quadradinho. (3) O texto do jogo aceita **ícones inline**: `Component.object` com
  `AtlasSprite` desenha uma textura de item (atlas `items`, `item/apple`) ou de bloco (atlas `blocks`, `block/oak_log`)
  no meio da frase, e é um recurso **vanilla** (cliente sem o mod também vê). (4) `Display.TextDisplay` (a entidade de
  texto do jogo) tem `view_range`, `billboard` (sempre vira para o jogador) e não tem hitbox.
- **Decisões do Eliel (2026-10-03).** Aparece em **holograma** sobre o baú (descartados: só ao mirar com texto na
  tela do cliente, e só no título), resumo **digitado**, emojis = **símbolos da fonte + ícones de item**, baú duplo
  conta como um (descartados: contagem por "sistema" e nomear vários de uma vez). Depois, ao testar a ideia: o
  holograma só aparece **perto** (~10 blocos, `view_range` 0,16); **baú e barril perdem o rótulo ao quebrar**;
  **shulker e baú do End mantêm**.
- **Texto (`LabelText`).** Guarda o texto como foi digitado (limpo), com atalhos `:nome:`; só na hora de mostrar vira
  `Component`. `:heart:`, `:star:`, `:bolt:`... são símbolos da fonte; `:apple:`, `:oak_log:`... são ícones (lista em
  `stashlink_sprites.txt`: 2131 nomes gerados dos arquivos `textures/item` e `textures/block`, item vence bloco quando o
  nome repete). Atalho desconhecido fica como texto. **Servidor limpa tudo que vem da rede** (`sanitize`): sem `§`
  (formatação), sem caracteres de controle, de direção (U+202A–202E, que embaralham o texto), invisíveis (U+200B–200F,
  FE00–FE0F, FEFF), de uso privado, não atribuídos nem emoji colorido (U+1F000–1FAFF); espaços juntos, **sem quebra de
  linha**; nome ≤ 32 e resumo ≤ 64 **pontos de código**; o pacote limita a 256 antes disso. O holograma mostra o nome
  em negrito e o resumo em cinza.
- **Onde o rótulo mora.** Baú, barril e shulker: no NBT do próprio bloco (`stashlink_label`), pelo mesmo
  `BaseContainerBlockEntityMixin` do Item 13 (`LabelHolder`). Por isso some com o baú/barril quebrado (um bloco novo no
  mesmo lugar não herda). **Shulker:** o mixin também escreve o rótulo no `CUSTOM_DATA` do item solto
  (`collectImplicitComponents`) e lê ao colocar (`applyImplicitComponents`) — sem registrar componente novo
  (que exigiria código por loader). **Baú do End** (não é `BaseContainerBlockEntity`, o conteúdo é do jogador e o bloco
  solta sem dados): `EnderLabels`, um `SavedData` (`data/stashlink/ender_labels.dat`, no mundo principal, com dimensão +
  posição), então sobrevive a quebrar e recolocar no mesmo lugar. **Baú duplo:** `Labels.set` grava nas duas metades;
  só a de menor posição (âncora) tem holograma, no meio.
- **Holograma (`HologramService`).** É um *reflexo*: `TextDisplay` com a tag `stashlink_label_hologram`, **nunca gravado**
  (`EntityMixin` faz `shouldBeSaved` falso), então não existe holograma órfão (crash, área descarregada, baú quebrado
  com o mod desligado). A cada 10 ticks compara "o que deveria existir" (blocos com rótulo carregados, rastreados em
  um conjunto fraco, mais os baús do End do `EnderLabels`) com "o que existe" e corrige: cria, troca se o texto/posição
  mudou, apaga se o bloco foi quebrado. Nenhum chunk é carregado à força. Sem o mod no **cliente** o jogador ainda vê o
  holograma (entidade vanilla); o editor exige o mod.
- **Edição.** Tecla **J** (Controles) olhando para um bloco: `LabelEditRequest(pos)` → servidor responde
  `LabelEditorData(pos, nome, resumo)` só a quem tem o canal (`sendIfSupported`) → `LabelEditScreen` → `SetLabelRequest`.
  `LabelService` revalida: vivo e não espectador, `isWithinBlockInteractionRange(pos, 1)`, bloco carregado e que aceita
  rótulo, `canPlayerUseBlock` em cada metade; limpa o texto. Não exige o baú fechado/desocupado: o rótulo não mexe em
  nenhum item. Sem o mod no servidor a tecla só mostra um aviso.
- **Bug achado nos testes.** A primeira versão apagava a lista de blocos rastreados no primeiro ciclo (tratava "primeiro
  servidor visto" como "servidor trocado"): no jogo real, baús com rótulo carregados antes do primeiro ciclo perderiam o
  holograma até alguém mexer no rótulo. Corrigido (só zera se o servidor *mudou*) e coberto por teste.
- **Testes (`LabelGameTests`, +9; 45 no total).** Limpeza do texto (§, direção, controle, emoji, corte, par substituto),
  atalhos, baú (gravar, ir ao disco e voltar, holograma único na posição certa, nunca gravado, atualizar e remover,
  nenhum item muda), quebrar baú/barril, shulker (rótulo vai e volta pelo item), baú do End (por posição, recolocar,
  limpar), baú duplo (duas metades, um holograma no meio, sobra de uma metade), pedidos inválidos (longe, bloco errado,
  texto gigante), pacotes (ida e volta, limite, cliente sem o mod não recebe). **Mutação:** hologramas gravados no disco,
  shulker perdendo o rótulo, baú duplo só numa metade e `§` passando — todos pegos.
- **Limites conhecidos.** (1) Só quem tem o mod edita; cliente vanilla só vê. (2) O rótulo de baú/barril é perdido ao
  quebrar (decisão); mod de claims que proíba "usar bloco" também proíbe rotular. (3) Ícone de **bloco** usa a textura
  do bloco no atlas `blocks` (um lado, ex.: tronco), e alguns blocos/itens com textura própria ficam de fora da lista.
  (4) Texto, ícone e desenho dependem do cliente gráfico (não coberto pelo harness): roteiro manual no `ROADMAP.md`.
  (5) Holograma é uma entidade por bloco rotulado: milhares de rótulos pesam; o alcance curto (~10 blocos) limita o
  custo de rede. (6) Para regenerar `stashlink_sprites.txt` numa versão nova: listar `assets/minecraft/textures/item/*.png`
  (prefixo `i `) e `textures/block/*.png` (prefixo `b `), só nomes `[a-z0-9_]+`, ordenado e sem repetir.
- **Lápis na tela do baú (pedido do Eliel após testar).** `AbstractContainerScreenMixin` (cliente) ganhou `init` (acrescenta o botão ✎ ao lado do título e dois campos acima da tela, `LabelPanel`) e `keyPressed` (enquanto se digita, as teclas vão para o campo: E não fecha o baú; Enter salva; Esc fecha a tela). O bloco é o que a mira apontava ao abrir a tela. Clicar no lápis pede o texto atual (`LabelEditRequest`) e a resposta preenche os campos; clicar de novo ou Enter grava (`SetLabelRequest`). A tecla J continua, e agora avisa quando não há bloco mirado ou o servidor não responde em 2 s (antes falhava calada). Mixin novo na tabela do `UPDATING.md`. Não testado no jogo (cliente gráfico).
- **Ajustes após o 1º teste em jogo (Eliel, 2026-10-03).** (1) **Um campo só**: o rótulo na interface é um único texto
  (até 48 caracteres), num campo na linha do título, ao lado do ✎; abre preenchido (o cliente pede ao servidor ao abrir
  a tela) e grava com Enter, com o lápis ou ao fechar a tela (`AbstractContainerScreenMixin.removed`). O resumo separado
  deixou de existir na interface (o modelo ainda tem o campo `note`, vazio). (2) **Holograma menor**: escala 0,5 e sem
  negrito. (3) **Alcance de 32 blocos decidido pelo servidor**: `view_range` 1,0 no cliente (a distância de
  desenho é `view_range × 64 × opção "distância de entidades"` do cliente, que o servidor não conhece e que deixava o
  texto visível de muito longe), e `EntityMixin.broadcastToPlayer` só deixa o servidor mostrar o holograma a quem está a
  até 32 blocos (o jogo reavalia quando o jogador anda). (4) O primeiro lápis falhava ao abrir o jogo: `@Shadow` de
  método herdado (`Screen.addRenderableWidget`) não funciona; usa-se o invoker `ScreenInvoker`.
- **Polimento final da interface.** Com o campo fechado, o nome é desenhado como texto comum ao lado do título (mesma
  cor, sem fundo; `AbstractContainerScreenMixin.extractLabels`) e o botão mostra ✎; clicar abre o campo e o botão vira ✔;
  clicar no ✔ (ou Enter) grava e fecha o campo; fechar a tela também grava. Alvo de mixin novo: `extractLabels` (UPDATING).
- **Posição do holograma (ajuste após teste em jogo).** Em cima do baú o texto ficava longe e **sumia dentro de um baú
  empilhado por cima**. Agora ele flutua **na frente do bloco** (a face por onde se abre; `Labels.hologramPos`, lê o
  `FACING` do estado): 0,56 bloco à frente e 0,8 de altura, então cada baú de uma coluna ou fileira tem o seu texto à
  vista. Barril/shulker virados para cima/baixo, que não têm frente horizontal, ficam com o texto logo acima (1,15).
  Baú duplo: no meio das duas metades, também à frente. Teste: `hologramStaysVisibleWhenAChestIsStackedAbove`.
  Limite: se houver um bloco colado na frente do baú, o texto fica atrás dele.
- **Texto cortado de lado.** Vista de um ângulo, a quina do baú (ou do baú empilhado) escondia metade do texto, porque ele é
  um objeto no mundo e respeita a profundidade. O holograma agora usa `see_through` (sem teste de profundidade): nunca é
  cortado, ao custo de também aparecer **através de paredes** dentro dos 32 blocos.
