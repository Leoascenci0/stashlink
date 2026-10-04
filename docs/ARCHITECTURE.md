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
## Item 18.1 — Liga/desliga e cadeado por função

- **Dois níveis, como a dificuldade do jogo.** O botão liga/desliga é **pessoal** (`ClientPrefs.disabledFeatures`, máscara de
  bits em `stashlink-client.json`; vai ao servidor dentro de `PlayerPrefs`). O cadeado é do **servidor**
  (`StashLinkConfig.lockedFeatures`, nomes em `stashlink.json`). A função vale se `!trancada && !desligada pelo jogador`.
  Trancada é "não funciona neste servidor"; o botão do jogador fica desligado (como a dificuldade travada).
- **Quem decide é o servidor.** Cada serviço pergunta a `FeatureGate` antes de agir (`allow` avisa na barra de ação se foi
  o cadeado; `allowSilently` é para o reabastecimento, que roda sozinho). O cliente repete a conta (`ClientFeatures`)
  só para não mandar pedido à toa e dizer o motivo. Nunca confiar no cliente.
- **Política no cliente.** `FeaturePolicySync` (servidor -> cliente com o mod: máscara de trancadas + "você pode mexer?")
  é a resposta a cada `PlayerPrefsRequest` (entrar no servidor, abrir ou fechar a tela) e é difundida a todos quando
  alguém tranca. `ClientPolicy` guarda isso; fica em `config/` sem nada do cliente do jogo, então o código comum (por
  exemplo `SlotLocks`) pode consultá-la num servidor dedicado, onde ela nunca é preenchida e vale o arquivo.
- **Cadeado.** `SetFeatureLockRequest` (cliente -> servidor) só é atendido para o dono do mundo ou operador
  (`McCompat.canManageServer`); outro jogador recebe aviso e a política de volta. `/stashlink feature [nome lock|unlock]`
  faz o mesmo pelo console. Sem mundo aberto (tela de mods no menu) a tela edita o arquivo local.
- **Sem o mod no servidor (modo cliente)** não há autoridade: o cadeado fica inativo (dica na tela) e vale só o liga/desliga.
- **Slot reservado trancado:** `SlotLocks.lockedItem` devolve nada, então a prévia, a regra do `mayPlace` e a preferência da
  N somem juntas; as reservas continuam gravadas no baú e voltam quando o cadeado abre.
- **Tela:** duas abas (Funções / Ajustes) para caber em janelas baixas; cadeado é o `LockIconButton` do próprio jogo.
- **Testes:** `FeatureTest` (8, unitários) e `FeatureGameTests` (7, no total 54 GameTests).

## Item 16 — Bancadas usam o armazenamento como inventário

**Investigação (como o jogo 26.3 monta uma receita).** Três fatos mandam no desenho:

1. **O livro de receitas coloca ingredientes por um único ponto**, `ServerPlaceRecipe.placeRecipe` (estático). Bancada
   (`CraftingMenu`), fornalha, defumador e alto-forno (`AbstractFurnaceMenu`) chamam a mesma função; a grade 2x2 da mochila
   (`InventoryMenu`) também. Ela só lê a **mochila** (`Inventory.fillStackedContents`), faz a conta (receita com forma,
   sem forma, "máximo" com shift, limpar a grade) e move os itens da mochila para a grade. Reescrever isso seria copiar
   código do jogo e quebrar a cada versão.
2. **O cliente decide se manda o pedido**: `RecipeBookComponent.tryPlaceRecipe` só repete o clique na mesma receita se o
   cliente a considera "fazível" (conta feita com a mochila **dele**). Cliente vanilla manda o primeiro clique sempre, mas
   bloqueia o segundo na mesma receita enquanto ela estiver vermelha. Por isso, com o mod no cliente, o servidor manda o que
   há no armazenamento e o livro soma isso ao que ele conhece (`RecipeBookComponentMixin`).
3. **Só bancada e fornalhas têm livro.** Cortador de pedra, tear, cartografia, amolar, ferreiro, bigorna, encantamento e
   suporte de poções (`AbstractContainerMenu` / `ItemCombinerMenu`) não têm: o jogador põe o item direto no slot. Não há
   "uma peça de código" de receita para elas; o que elas têm em comum é o **cursor** (o item que se carrega com o mouse) e
   os slots de entrada.

**Como outros mods fazem.** Sophisticated Storage, Refined Storage e afins fazem uma de duas coisas: uma **tela própria**
(grade do armazenamento ao lado da estação; o clique pega o item para o cursor) ou **preenchem a grade** a partir da rede.
O StashLink faz as duas, sobre as mesmas fontes: livro de receitas para as 4 estações que têm livro, e um **painel
"Armazenamento"** para todas (decisão do Eliel: "todas as bancadas").

**Decisões do Eliel (2026-10-03).** Todas as estações; o livro acende + aviso na barra de ação; mochila primeiro e baús só
se faltar; (REVISTO depois, ver a seção "a bancada usa só baús e barris": nem shulkers, e vale "usar baús como fonte". O liga/desliga + cadeado da função, Item 18.1, é o
consentimento).

**Peças.**
- `bench/BenchPool`: o armazenamento visto pela estação: shulkers no inventário, shulkers colocadas, baús e barris no raio
  (`PlayerPrefsStore.radius`, nunca um número fixo; o Item 15 sobe o teto sem mexer aqui). Mesmas regras de sempre:
  container trancado, de loot ou sem permissão fica de fora (`NearbyContainers`), e baú **aberto por outro jogador**
  também (`QuickStackService.openedByAnother`). Fornalha, suporte de poções, funil, dispenser, dropper e crafter **nunca**
  entram: `NearbyContainers` só aceita `ChestBlockEntity`, `BarrelBlockEntity` e `ShulkerBoxBlockEntity` (instanceof do
  tipo concreto, não do tipo base). Foi preciso criar `ItemSource.forEachStack` (listar sem tirar) e
  `NearbyContainers.find(player, chests)`.
- `bench/BenchRecipe` + `mixin/ServerPlaceRecipeMixin`: **antes** de o jogo colocar a receita, calcula o que falta na
  mochila (`BenchCompat.missingIngredients`, repete a conta do jogo com o armazenamento somado) e traz **só isso** do
  armazenamento para a mochila; o jogo roda **sem mudar**; **depois**, o que sobrou do que foi trazido volta à origem
  (`ContainerSource.give` só devolve ao que foi tocado). Sem lugar na mochila, volta tudo ao container antes de o jogo
  olhar. Espectador, função trancada/desligada e menu que não é estação: o mod não faz nada. Criativo funciona (o livro continua pedindo ingredientes da mochila).
- `bench/BenchPullService` + `BenchPullRequest`: painel. O cliente pede "este item" (um stack, ou um com o botão direito);
  o servidor confere (estação aberta com o mesmo `containerId`, função ligada, cursor livre ou do mesmo item, no máximo 1
  pedido por tick) e **põe no cursor**, tirando do container no mesmo passo. Depois é item de verdade na mão: colocar no
  slot, shift-clicar o resultado e fechar a tela são cliques normais do jogo. Por isso não existe item fantasma.
- `bench/BenchSync` + `BenchPoolSync`: servidor → cliente com o mod: a lista (tipo + quantidade, até 512 tipos, em ordem de
  nome) ao abrir a estação, quando o mod mexe no armazenamento e a cada 5 s. Cliente sem o mod nunca recebe, e a
  varredura não se repete para ele.
- Cliente: `BenchPanel` (grade rolável com busca, à direita da estação), `BenchClient` (guarda a lista) e dois mixins:
  `AbstractContainerScreenMixin` (desenhar o painel, clique, rolagem, teclas da busca) e `RecipeBookComponentMixin`
  (soma o armazenamento na conta do livro e refaz quando a lista muda).
- `compat/mc/BenchCompat`: tudo do jogo que pode mudar: quais menus são estação, a conta do livro
  (`StackedItemContents`), a identidade de um stack.

**Estações (a investigação pedida).**

| Estação | Entra? | Como |
|---|---|---|
| Bancada | sim | livro de receitas + painel |
| Fornalha, defumador, alto-forno | sim | livro de receitas (põe na entrada) + painel |
| Cortador de pedra, tear, mesa de cartografia, pedra de amolar, mesa de ferreiro | sim | painel (o item vai ao cursor e o jogador põe no slot) |
| Bigorna, mesa de encantamento | sim | painel. O XP e os lápis continuam sendo do jogador (o mod só entrega o item), então não há nada especial a proteger |
| Suporte de poções | sim | painel (garrafas, ingrediente, pó de blaze). O tempo da poção é do bloco; o mod nunca toca nos itens que já estão lá |
| Grade 2x2 da mochila (`InventoryMenu`) | **não** | não é uma bancada; usaria o armazenamento em qualquer tela |
| Crafter (bloco automático) | **não** | é redstone, não há jogador no meio |

**Regra: o inventário interno das estações não é armazenamento.** Garantido por construção (a lista de blocos de
`NearbyContainers` é por tipo concreto) **e por teste**: `stationsWithItemsInsideAreNeverTouched` (fornalha, defumador,
alto-forno, suporte de poções, funil, dispenser, dropper e crafter com pedra dentro; N, reabastecer, W, livro de receitas
e painel rodam; nenhum slot muda) e `snapshotListsOnlyChestsBarrelsAndShulkers` (a lista nunca mostra o que está na
fornalha). Funil, dispenser e dropper ficam de fora de propósito (decidido na investigação).

**Limites conhecidos.**
- O livro precisa de **espaço na mochila** para o que ele traz (o jogo base também precisa ao limpar a grade): sem lugar,
  não puxa e nada se perde.
- Só itens **comuns** (sem dano, encantamento ou nome) alimentam o livro, como no jogo base; o painel mostra tudo, inclusive
  item encantado (para bigorna, ferreiro e amolar).
- A lista do painel tem teto de 512 tipos de item por pacote; passando disso entram os primeiros em ordem de nome.
- O raio é o do jogador (`PlayerPrefsStore.radius`, padrão 8, teto 64); o Item 15 só muda o teto.
- Os mixins de cliente (`RecipeBookComponentMixin` e as novas partes de `AbstractContainerScreenMixin`) só se conferem no
  jogo: o `runClientGameTest` não roda nesta máquina.
- **Suspeita (ServerPlayerMixin na lista "server"):** no Fabric a lista `"server"` do arquivo de mixins vale só para o
  servidor **dedicado**; o servidor integrado do mundo único roda no processo do cliente e não carregaria esse mixin.
  O `ServerPlayerMixin` foi movido para a lista comum (`ServerPlayer` existe nos dois lados; não faz mal ao dedicado).
  Não foi confirmado com o log do Prism.

- **Posição final do holograma (Eliel, depósito de baús virados para a parede, 2026-10-03).** "Na frente do bloco" falhava quando a
  frente do baú aponta para longe de quem olha (estoques com baús de costas) e o texto ficava atrás deles. Como o holograma já
  é `see_through`, agora ele fica no **centro do próprio bloco**, perto do topo (`Labels.hologramPos`, 0,75): visível de qualquer
  lado e sempre dentro do bloco do seu baú (nunca no vizinho nem no de cima). Teste:
  `hologramBelongsToItsOwnChestFromAnySideAndStack` (empilhado, virado ao sul e ao leste). Limite: em paredes grandes de baús
  nomeados, todos os nomes até 32 blocos aparecem juntos (atravessam os blocos); se poluir, mostrar só o do baú mirado.

## Raios por tipo de container (Eliel, 2026-10-03)

O raio único de até 64 estava desbalanceado (a bancada alcançava baús a 50 blocos). Agora são dois:
- **Baús, barris e bancadas:** teto duro 16 (`StashLinkConfig.HARD_MAX_RADIUS`), padrão 8. O Item 15 (conduíte) vai subir o teto para 32.
- **Shulkers colocadas:** raio próprio, padrão 32 (`StashLinkConfig.shulkerRadius`), a tela deixa subir até 64 (`HARD_MAX_SHULKER_RADIUS`);
  o servidor pode baixar o teto em `maxShulkerRadius`. É uma preferência por jogador (`PlayerPrefs.shulkerRadius`, quinto campo do pacote).
- `NearbyContainers.collect` varre os chunks do maior raio e confere cada tipo com o seu. A bancada usa as mesmas fontes e os mesmos raios:
  não existe raio só de bancada.
- Teste: `chestsReach16AndShulkersReach32` (baú a 6 entra; baú a 20 não; shulker a 30 entra; a 40 não, com raio pedido 50).
- **Cadeado nos ajustes (Eliel, 2026-10-03):** os três ajustes da aba Ajustes (raio de baús/bancadas, raio de shulkers, usar baús)
  ganharam o mesmo cadeado das funções. São entradas do enum `Feature` marcadas como `isSetting()` (sem liga/desliga, só cadeado),
  então reaproveitam a máscara, o pacote `SetFeatureLockRequest`, o `/stashlink feature radius|shulker_radius|chests lock|unlock` e a
  gravação em `lockedFeatures`. Trancado, `PlayerPrefsStore` ignora a escolha do jogador e vale o valor do servidor. A aba Funções
  não lista os ajustes. Teste: `lockedSettingsUseTheServerValue`.

## Item 16 — itens emprestados voltam à origem (Eliel, 2026-10-03)

Pedido do Eliel depois de testar: ao escolher outra receita (ou outro item no painel), o item anterior ia para a **mochila**; devia
voltar ao **baú de origem**. E dois jogadores querendo o mesmo item não podem duplicar nem brigar: vale quem clicou primeiro.

- **`bench/BenchLedger`** (caderno por jogador): lembra o que o mod tirou do armazenamento para a estação aberta (grade, slot de
  entrada, cursor) e a **origem** (posições dos containers, nunca objetos). Enquanto o item está na estação ele é só daquele jogador:
  nenhum outro o enxerga, então **a promessa é física** (o item saiu do baú no mesmo passo em que entrou na grade). Quem chega depois
  encontra o baú sem ele e não puxa nada; se o primeiro desistir, o item volta e o segundo passa a conseguir.
- **Devolução à origem** (`BenchPool.returnTarget`, o mesmo desenho da troca de slot do Litematica: só containers que ainda estão
  no alcance, liberados e sem outro jogador olhando; se nenhum aceitar, o item **fica** onde está, nunca no chão):
  1. ao escolher **outra receita**: o que o mod pôs na grade e sobrou volta antes de montar a nova (`returnFromGrid`);
  2. ao pegar **outro item no painel**: o do cursor volta (`returnCursor`);
  3. ao **fechar** a estação: o jogo devolve a grade à mochila e, no tick seguinte, `BenchSync` chama `BenchLedger.tick`, que devolve de lá.
- **O que foi gasto não volta:** a conta é refeita a cada tick contra o que ainda está no cursor e nos slots de entrada; item craftado
  (ou levado pelo jogador para a mochila) sai do caderno.
- Escolha de desenho: o item **sai do baú ao escolher a receita** (como o jogo já fazia), em vez de só ser consumido ao pegar o
  resultado. Isso mantém o craft 100% vanilla (grade de verdade, shift-clique) e dá a "promessa" pedida sem fila nem reserva
  virtual; o custo é que o baú fica sem o item enquanto a bancada está aberta.
- **Raio em mundo próprio:** nos sliders, mudar o raio em mundo único só alterava o padrão do servidor, e a preferência pessoal antiga
  (que vale mais) continuava mandando. Agora o slider muda os dois.
- Testes: `switchingRecipeReturnsLeftoversToTheChest`, `closingTheBenchReturnsUnusedItemsToTheChest`,
  `panelSwapReturnsTheCursorItemToItsChest`, `twoPlayersNeverShareTheSameItems` (GameTests 74 no total). Mutação: sem devolver ao
  trocar de receita, sem devolver ao fechar e sem registrar no caderno: pegos por 1, 2 e 3 testes.

## Item 14 — rótulo da shulker na sobrevivência (Eliel, 2026-10-03)

O rótulo da shulker se perdia ao quebrar em **sobrevivência**: o drop vem da tabela de loot (`blocks/<cor>_shulker_box`), cujo
`copy_components` só inclui `custom_name`, `container`, `lock` e `container_loot`; o rótulo viaja em `CUSTOM_DATA`. Só o criativo, que
copia todos os componentes do bloco, o preservava (e foi onde o Item 14 foi conferido). `ShulkerBoxBlockMixin` acrescenta o rótulo ao
item solto no fim de `ShulkerBoxBlock.getDrops`. Teste do caso real: `shulkerKeepsTheLabelWhenBrokenInSurvival` (usa o drop de verdade,
não `collectComponents`); sem o mixin ele falha.

## Item 16 — a bancada usa só baús e barris (Eliel, 2026-10-03)

Depois de testar em jogo, duas decisões que **substituem** as anteriores deste item:
- **Shulkers nunca servem à bancada**, nem a do inventário nem a colocada (`BenchPool` só monta a fonte de baús e barris). As
  shulkers continuam sendo fonte do reabastecimento, da N e do Litematica; só a bancada/painel as ignora.
- **"Usar baús como fonte" vale para a bancada**: com o ajuste em Não, ela não enxerga armazenamento nenhum (painel "Nada por perto").
  Antes a função ignorava esse ajuste porque tinha o próprio liga/desliga; agora vale o ajuste e o liga/desliga.
- Devolver itens emprestados (caderno) continua possível mesmo se o ajuste for desligado no meio: o item volta a quem o emprestou.
- Testes: `chestsOffBlocksTheBench`, `shulkersNeverServeTheBench`, `chestsReach16AndShulkersAreNeverBenchStorage`. Mutação: ignorar o
  ajuste e deixar as shulkers servirem foram pegos.

## Item 17 — Escolher o que cada baú recebe com a tecla N

- **Dois filtros, duas perguntas.** (a) *Este baú aceita receber com a N?* — é do **baú** (botão na tela dele). (b) *Este
  jogador deixa a N guardar este tipo de item?* — é do **jogador** (aba "Tecla N" da config). A N só coloca o item se as
  duas respostas forem sim. Nenhum dos dois toca em guardar à mão, W, funil, Litematica ou reabastecer: só a N.
- **Onde a memória do botão fica.** No mesmo mixin dos Itens 13/14 (`BaseContainerBlockEntityMixin` implementa também
  `ReceiveHolder`): `stashlink_no_quick_stack = true` no NBT do bloco, **gravado só quando desligado** (o padrão é receber,
  então nenhum baú existente muda e baú sem mexer não ganha dado nenhum). Mesmas consequências boas dos outros itens:
  sobrevive a reiniciar, é copiado com o mundo, some com o baú/barril quebrado. **Shulker** leva o valor no item: o
  `CUSTOM_DATA` agora é montado num lugar só (`LabelCompat.shulkerTag`) com **rótulo e botão juntos** — antes cada um
  sobrescreveria o outro —, tanto em `collectImplicitComponents` (criativo/pegar bloco) quanto no `getDrops` da sobrevivência
  (`ShulkerBoxBlockMixin`, o caso real do Eliel). O teste confere o drop real com `Block.getDrops`. Descartado: uma lista de
  posições no mundo (ficaria órfã) e Data Attachment (duas implementações, uma sem harness).
- **Baú duplo.** O botão grava nas duas metades (`QuickStackReceive.set`). Para **receber**, as duas precisam aceitar: se um
  baú novo foi colocado ao lado de um desligado, o conjunto continua desligado — o lado seguro (a N nunca enche um baú que
  alguém desligou). Ligar pelo botão religa as duas.
- **Cliente.** Botão `ReceivePanel` na linha do título (à esquerda do lápis do rótulo, que cedeu 24 px de largura). Quem
  desenha é `AbstractContainerScreenMixin` (`init` e `extractRenderState`, alvos que já constavam no UPDATING). O estado
  vem do servidor **dentro do pacote que já existia** (`SlotLocksSync` ganhou o campo `receives`; o tick `SlotLockSync`
  compara e só reenvia se mudou), então não há pacote servidor→cliente novo. Só o pedido é novo: `ReceivesRequest(containerId,
  receives)` — valor explícito, não "inverter", para um clique duplo não desfazer o outro. Cliente vanilla nunca recebe nada
  (`sendIfSupported`) e não vê o botão. Ligado = cor normal; desligado = cinza e riscado (não só cor). Botão e lápis têm 12 px e a mesma cor. Os textos não citam letras de tecla (a tecla é remapeável).
- **Validação no servidor (`QuickStackReceiveService`).** Igual à trava de slot (Item 13): vivo e não espectador; é **este**
  o menu aberto; menu de baú/barril/shulker; `stillValid` (distância); claims (`canPlayerUseBlock` por block entity);
  **nenhum outro jogador com o container aberto**. Container sem memória (baú do End) responde "não pode ser
  configurado". Não passa pelo cadeado da N: só mexe em metadado.
- **Categorias (`ItemCategory` + `compat/mc/ItemKinds`).** Armadura (tags de elmo/peitoral/calça/bota, asa-delta, escudo),
  Ferramentas (picareta, machado, pá, enxada, tesoura, vara — machado conta aqui, não como arma), Armas (espada, lança, arco,
  besta, tridente, maça), Comida (componente `FOOD`) e Poções (componente `POTION_CONTENTS`, menos flecha de poção, que é
  munição). **Cada item cai em no máximo uma categoria** (a primeira da ordem); o resto — blocos, minérios, ferramentas de
  mods fora das tags — não tem categoria e a N o trata como sempre. Só tags/componentes do jogo, sem lista de itens à mão:
  itens de mods que entram nelas são reconhecidos sozinhos. A API frágil mora toda em `ItemKinds`.
- **Cada categoria é uma `Feature`** (`CAT_ARMOR`...`CAT_POTIONS`, `isCategory()`): reaproveita o bit "desligado pelo jogador"
  (no `PlayerPrefs.disabledFeatures`, **formato do pacote inalterado**), o cadeado do servidor (`/stashlink feature`, config) e
  a política enviada ao cliente. Isso responde "padrão do servidor para quem não personalizou": ligado, e o servidor pode
  trancar (**trancada = a N nunca guarda aquele tipo naquele servidor**, o mesmo "trancada = não funciona" do 18.1).
  Aba própria "Itens bloqueados" na tela (as outras ficaram como estavam); a linha mostra o **ícone do item** como "emoji" (recurso do
  próprio jogo, `Component.object` com `AtlasSprite`; a fonte padrão não tem emoji colorido, ver Item 14).
- **Como combina com "o baú já tem o item".** A categoria desligada **vence tudo**: o item nunca é candidato, mesmo que o
  baú o contenha ou tenha um slot reservado para ele (Item 13). O botão do baú desligado também vence a reserva. Na prática
  `QuickStackLogic.stack` ganhou o parâmetro `excluded` e consulta `QuickStackReceive.accepts` antes de qualquer das duas
  rodadas; o resto do algoritmo (simular → aplicar, conservação) não mudou.
- **Modo cliente (servidor sem o mod).** As categorias valem (são preferência pessoal): `QuickStackJob` não clica nos itens
  excluídos nem os conta como "guardáveis". O botão do baú **não existe** lá (não há onde guardar o valor).
- **Testes.** Ver `ROADMAP.md`. Lição repetida: itens exclusivos por teste (TUFF, BASALT, CALCITE, DRIPSTONE_BLOCK, ...).
- **Limites conhecidos.** (1) O desenho do botão e dos ícones depende do cliente gráfico: roteiro manual no `ROADMAP.md`.
  (2) Funil e outros mods continuam enchendo um baú "desligado": o botão só governa a N. (3) O botão aparece em toda tela
  de baú/barril/shulker, inclusive baú do End; ali o clique só avisa que não dá. (4) Shulker colocada e depois "pega com
  bloco do meio" no criativo leva o botão (e o rótulo) por `collectComponents`; em sobrevivência vai pelo `getDrops`.

## Item 16.2 — Painel das bancadas com a cara do livro de receitas (Eliel, 2026-10-03)

- **Decisão: painel próprio com os mesmos widgets, não estender o livro.** `RecipeBookComponent` só sabe listar receitas
  (`RecipeCollection`, abas por categoria, pacote do jogo para colocar a receita). Para tear, ferraria e bigorna, que não
  têm receitas assim, seria gambiarra e frágil entre versões. `client/BenchPanel` usa o fundo do livro, `EditBox` de busca
  (a lupa faz parte do foco), `ImageButton` de página e botões de 25 px com o sprite `slot_uncraftable` para o vermelho.
- **Bancada e fornalhas não têm o painel**: já têm o livro do jogo, que o Item 16 liga ao armazenamento. O jogador pediu isso.
- **Protocolo.** `BenchPoolSync.Entry(item, count, id, missing, tab, color)`: `id >= 0` é um resultado de receita,
  `COLOR_PICK (-3)` é a escolha de cor do tear. `BenchPullRequest.recipeId`: `-1` põe no cursor (caminho antigo), `-2`
  (`BenchResults.PLACE`) põe no slot da estação que aceita o item, `>= 0` monta a receita (o campo `item` leva o corante
  escolhido no tear). O servidor refaz a lista e revalida; o cliente só pede.
- **Receitas e "descoberto"** (`compat/mc/StationRecipes`): cortador de pedra e tear. Sem livro no jogo, "descoberto" = o
  jogador já pegou, fabricou ou usou o item de entrada (estatísticas) ou o tem à mão. Montar confere antes se dá para
  preencher **todos** os slots e só então mexe (nunca deixa a estação pela metade); tira do armazenamento, ou da mochila se
  faltar, e registra no `BenchLedger` (volta ao baú se não for usado).
- **Tear.** Abas Cores / Estandartes / Padrões (moldes). O servidor manda cada padrão com o banner em branco; o cliente
  recolore a última camada com a cor escolhida. Padrões com molde só aparecem se o molde é conhecido.
- **Abas** (`client/BenchTabs`, `BenchResults.slotTab`): bigorna = Livros / Equipamento (inclui etiqueta) / Materiais;
  ferraria = o slot que aceita o item (Enfeites / Equipamento / Minérios); encantamento = Equipamento / Livros / Lápis;
  poções = Garrafas / Ingredientes / Combustível (o pó de blaze conta como combustível).
- **Posição.** `BenchPanel.stationLeft` centraliza painel + estação como um bloco. Duas peculiaridades do 26.3 tratadas:
  a pedra de amolar desenha o fundo no centro e ignora `leftPos` (`GrindstoneScreenMixin`, `@ModifyVariable` com `ordinal = 2`);
  encantamento e suporte de poções fazem o mesmo em vários pontos, então a `width` que enxergam passa a ser `2 * leftPos + imageWidth`;
  o campo de nome da bigorna nasce depois, no centro da janela, e é realinhado a cada quadro.
- **Armadilha do 26.3.** `MouseButtonEvent.button()`: esquerdo = 1, direito = 3. Foi o motivo de setas e cliques "mortos".
- **Bancada:** `RecipeBookComponentMixin` (com `GhostSlotsAccessor`/`GhostSlotAccessor`) lê o ingrediente fantasma do slot
  clicado e, se o jogador não o tem (mochila nem armazenamento), coloca a receita que o fabrica (só descobertas).
- **Limites.** Só Fabric foi exercitado; abas na cartografia e a cor do banner de base ficam para depois.

## Item 19 — Botão do meio puxa o item do armazenamento

- **Onde o jogo resolve o pick block.** Desde o 26.x, o cliente só manda `ServerboundPickItemFromBlockPacket(pos)`;
  `ServerGamePacketListenerImpl.tryPickItem(ItemStack)` escolhe o slot no servidor e só conhece o inventário. Por isso
  **não há mixin no cliente nem pacote novo** (a ideia inicial de reaproveitar o pacote do Litematica caiu): o
  `ServerGamePacketListenerImplMixin` (lista comum, serve Fabric e NeoForge) injeta no início de `tryPickItem` e chama
  `PullItemService.pickBlock`. O item a pegar é o que o jogo base já escolheu (`getCloneItemStack`), então "bloco que dá
  outro item" (trigo → semente, baú → baú) vem de graça.
- **Não brigar com o jogo base.** `pickBlock` devolve `false` (e o jogo base segue) se: o item está em qualquer slot da
  mochila/hotbar (`findSlotMatchingItem`, o jogo base seleciona ou troca), o jogador é criativo (ganha o item do nada),
  espectador, morto, com GUI aberta, a função `PULL` está trancada/desligada (em silêncio: o botão do meio é apertado o
  tempo todo), no intervalo de 4 ticks do `PullItemService`, ou não há o item guardado por perto. Só quando traz algo
  devolve `true` e cancela o jogo base. Usamos `isCreative()` e não `hasInfiniteMaterials()`: em produção são iguais, e o
  jogador simulado dos testes herda "materiais infinitos" do mundo criativo.
- **Nunca sobrescreve.** `PullLogic.chooseSlot` (selecionado se livre → mesmo item com espaço → primeiro vazio);
  hotbar cheia: aviso `stashlink.pick_block.hotbar_full` na barra de ação e nada muda. Sem `owned`/`returnTo`: a troca no
  mesmo slot (Item 18) é só do Litematica, e o registro do Litematica não é tocado (teste
  `middleClickLeavesTheLitematicaSlotAlone`). Vem 1 stack (como o Litematica).
- **Achado: baú aberto por outro jogador.** `PlayerSources.operation` (reabastecer/Litematica) nunca conferia
  `QuickStackService.openedByAnother` ao **tirar**, só ao devolver (Item 18). O botão do meio usa
  `PlayerSources.operationSkippingOpened`, que embrulha as entradas com a mesma regra da tecla N. Reabastecer e
  Litematica não mudaram (decisão a rever, ver handoff).
- **Shulker no inventário** vale (mesmas fontes). **Raios:** `PlayerPrefsStore` (16/8 baús, 32 a 64 shulkers).
- **Modo cliente: não se aplica.** Sem o mod no servidor não existe o gancho; o jogo base continua como é.
- **Limites.** Só Fabric foi testado no harness (o NeoForge compila com o mesmo mixin comum); Ctrl + botão do meio
  (copiar com dados) só existe em criativo e não é tocado.

## Item 21 — Shift + passar o mouse coleta

- **Ideia:** em tela de baú/barril/shulker/ender chest (`LootAllService.isSupportedMenu`), com Shift **e o botão esquerdo** pressionados, cada
  slot em que o mouse **entra** recebe um shift-clique (`Click.quickMove` via `ClientCompat.click`). É só clique
  normal de inventário: o servidor decide o que cabe, então nada duplica nem some e funciona sem o mod no servidor.
  Vale nos dois sentidos (o shift-clique do jogo já leva baú→mochila e mochila→baú).
- **Peças:** `Feature.HOVER_COLLECT` (liga/desliga + cadeado, passa por `ClientFeatures.enabled`),
  `mixin/HoverCollectScreenMixin` (injeta em `extractRenderState`, mixin à parte do `AbstractContainerScreenMixin`),
  `client/HoverCollectClient` (condições e o clique) e `client/HoverCollectPass` (a "memória" da passagem; não conhece o
  Minecraft e tem teste). `ClientCompat.isShiftDown` isola `Minecraft.hasShiftDown()` (era `Screen.hasShiftDown()`).
- **Regras:** o slot sob o mouse na hora de apertar já leva o clique normal do jogo (por isso é "armado" sem clicar de novo); um clique por entrada em slot (parado em cima não repete; sair e voltar é nova passagem); no máximo 4
  cliques por tick; 3 cliques seguidos que não moveram nada (destino cheio) param a passagem até soltar o Shift; nunca
  com item no cursor, em espectador, em slot sem item, em slot que não aceita item nenhum (resultado) nem em slot da
  mochila travado na config (`ClientPrefs.lockedSlots`). Telas fora de baú (inventário, criativo, bancadas) nunca agem.
- **Detecção de "não coube":** o cliente aplica o shift-clique na hora (previsão); se o slot não mudou, não coube.

