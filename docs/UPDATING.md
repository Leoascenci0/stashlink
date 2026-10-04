# Como atualizar a versão do Minecraft

Guia do passo a passo. Ideia central: **tudo que muda de uma versão do Minecraft para outra fica numa pasta só**
(`common/src/main/java/io/github/leoascenci0/stashlink/compat/mc/`, mais `TestCompat` nos testes). Quando o
Minecraft muda um nome, o compilador reclama e você conserta **num lugar só**, em vez de caçar o código inteiro.
Analogia: é como o quadro de cargas de um projeto — mudou a norma, você atualiza o quadro, não cada folha.

## 0. Antes de começar

1. `git status` + `git fetch origin --prune`; árvore limpa. Crie branch: `chore/update-mc-<versão>`. Nunca em `main`.
2. Rode `powershell -ExecutionPolicy Bypass -File scripts/check-versions.ps1` — ele imprime a última versão
   estável do Minecraft e as linhas prontas para o `gradle.properties` (ou `-Minecraft 26.4` para uma específica).

## 1. Onde mora cada número de versão

| Onde | Chave / campo | Observação |
|---|---|---|
| `gradle.properties` | `minecraft_version` | ex.: `26.3` |
| `gradle.properties` | `minecraft_version_range` | ex.: `[26.3, 26.4)` — vai para os arquivos de mod |
| `gradle.properties` | `neo_form_version` | ex.: `26.3-1`; **tem de ser a mesma linha do Minecraft** |
| `gradle.properties` | `fabric_version` | Fabric API, ex.: `0.161.0+26.3` |
| `gradle.properties` | `fabric_loader_version` | ex.: `0.19.5` (carregador estável mais novo) |
| `gradle.properties` | `neoforge_version` | ex.: `26.3.0.39-beta` |
| `build.gradle` (raiz) | plugin `net.neoforged.moddev` | ex.: `2.0.148`; **precisa acompanhar o Minecraft** (versão velha falha ao recompilar) |
| `gradle.properties` | `java_version` | só muda se o Minecraft exigir outro Java |
| `fabric/src/main/resources/fabric.mod.json` | `"minecraft": "~${minecraft_version}"`, `"litematica": ">=x.y.z"` | o Minecraft vem do gradle.properties; a faixa do **Litematica é manual** |
| `neoforge/src/main/resources/META-INF/neoforge.mods.toml` | `versionRange = "[${neoforge_version},)"` | vem do gradle.properties, nada manual |
| `docs/ARCHITECTURE.md` | seção de decisões | registre a decisão e a data |

Se criar uma chave nova no `gradle.properties`, ela precisa entrar no `expandProps` de
`buildSrc/src/main/groovy/multiloader-common.gradle` (aviso no topo do arquivo).

## 2. Como achar as versões certas

- **Fabric** (loader, Fabric API): https://fabricmc.net/develop/ ou `https://meta.fabricmc.net/v2/versions/game`.
  Fabric API: Modrinth `fabric-api`, filtrando pela versão do jogo.
- **NeoForge**: https://projects.neoforged.net/neoforged/neoforge ou o maven
  `https://maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml`. Versão `26.3.0.39-beta` = jogo
  26.3, build 39. "beta" é normal no começo de uma versão nova.
- **NeoForm** (os "mapas" que traduzem o código ofuscado do jogo): https://projects.neoforged.net/neoforged/neoform
  — formato `<minecraft>-<n>`.
- **MultiLoader-Template** (https://github.com/jaredlll08/MultiLoader-Template): há um *branch* por versão do
  Minecraft. Compare o branch novo com o nosso (`git diff` ou pela interface do GitHub) para ver mudanças em
  `build.gradle`, `buildSrc` e no Gradle wrapper — são elas que costumam quebrar o build, não o nosso código.
- **Litematica / Forgematica**: Modrinth (`litematica`, `forgematica`) — o script já confere. Se não houver build
  para a nova versão, a integração fica ausente (ver seção 6).

## 3. Passo a passo

1. Atualize as chaves do `gradle.properties` (seção 1).
2. `./gradlew :common:build --console=plain` — o `common` é onde quase tudo quebra.
3. Leia os erros de compilação. Cada um deve cair em `compat/mc/`. Se cair em outro lugar, é sinal de que há uma
   chamada frágil **fora** do compat: mova para lá (regra do projeto).
4. `./gradlew :fabric:compileJava` e `:neoforge:compileJava`.
5. Rode `./gradlew build` uma vez no fim (testes inclusos).
6. Teste no jogo (`runClient` do Fabric e do NeoForge): teclas N e W, refill da mão, Litematica (se houver).
7. Atualize a tabela da seção 5 e o log da seção 8; registre a decisão no `docs/ARCHITECTURE.md`.

## 4. Como ler o código do Minecraft para achar o nome novo

O Gradle (ModDevGradle) já gera os fontes decompilados do jogo:
`common/build/moddev/artifacts/vanilla-<versão>-sources.jar` (ex.: `vanilla-26.3-1-sources.jar`).

```bash
mkdir -p /tmp/mc && cd /tmp/mc
unzip -q /caminho/stashlink/common/build/moddev/artifacts/vanilla-26.3-1-sources.jar
grep -n "placeItemBackInInventory" net/minecraft/world/entity/player/Inventory.java
grep -rln "DYED_SHULKER_BOX" net/
```

Procure o nome antigo; se não existir, procure a ideia (ex.: "ShulkerBox", "screen"). O IDE (IntelliJ: "Go to
declaration") também abre esses fontes. O mesmo vale para a Fabric API e o NeoForge (fontes no cache do Gradle).

## 5. Mapa do compat layer

Regra: **nenhuma chamada frágil da API do Minecraft fora de `compat/mc/`.** O compat é só métodos estáticos finos,
sem reflexão.

| Wrapper | O que esconde | Última mudança (MC) |
|---|---|---|
| `McCompat.id` | `Identifier.fromNamespaceAndPath` (antes `ResourceLocation`) | 26.1 |
| `McCompat.payloadType` | `CustomPacketPayload.Type` com id do mod (pacotes de rede) | estável desde 1.20.5 |
| `McCompat.gameTime`, `playersOnServer` | `player.level()` / servidor / lista de jogadores | estável |
| `McCompat.dropCount` | estatística `Stats.DROP` do jogador (detectar tecla Q) | estável |
| `McCompat.placeBackInInventory` | `Inventory.placeItemBackInInventory(stack, Prediction)` | **26.3** (ganhou `Prediction`) |
| `McCompat.sendHeldSlot` | pacote `ClientboundSetHeldSlotPacket` | estável |
| `McCompat.readContainerComponent` / `writeContainerComponent` | componente `DataComponents.CONTAINER` da shulker | estável desde 1.20.5 |
| `McCompat.resetDamage` | componente `DataComponents.DAMAGE` | estável desde 1.20.5 |
| `ClientCompat.hasScreenOpen` | `Minecraft.screen` virou `mc.gui.screen()` | **26.3** |
| `ClientCompat.keyCategory` | `KeyMapping.Category.register` | 1.21.9 |
| `ClientCompat.keyMatches` | `KeyMapping.matches(KeyEvent)` | 1.21.9 |
| `TestCompat.dyedShulker` | `RED_SHULKER_BOX` etc. viraram `DYED_SHULKER_BOX.pick(cor)` | **26.3** |
| `TestCompat.registryLookup` | `VanillaRegistries.createLookup()` virou `createWorldLookup()` | **26.3** |

Fora do compat, de propósito: a "cola" dos loaders (`fabric/`, `neoforge/`) — usa API do Fabric/NeoForge (eventos,
registro de pacotes, teclas), que muda com versões *deles*, não do Minecraft. Se quebrar, conserte lá mesmo; ela
já é a camada fina por design. A integração Litematica (`fabric/.../compat/litematica`; os mixins ficam no subpacote `mixin`, porque o Mixin proíbe chamar diretamente classes do pacote que ele possui) depende de classes de outro
mod: se o Litematica mudar, é lá. Também fora: os tipos do Minecraft usados como dado (`ItemStack`, `Container`,
`BlockPos`...) — são o vocabulário do mod e raramente mudam de nome.

**Alvos de mixin (não dá para esconder no compat; o nome do método *é* o alvo).** Quebrou na atualização =
o jogo falha ao abrir (`defaultRequire = 1`) e o erro aponta o mixin. Confira os nomes com `javap` no jar do jogo:

| Mixin | Alvo no Minecraft | Onde olhar se mudar |
|---|---|---|
| `ServerPlayerMixin` | `ServerPlayer.tick` (reabastecer sem mão vazia visível). Está na lista **comum** do arquivo de mixins: a lista `"server"` não vale no servidor integrado | Item 4 |
| `ServerPlaceRecipeMixin` | `ServerPlaceRecipe.placeRecipe(CraftingMenuAccess, int, int, List, List, Inventory, RecipeHolder, boolean, boolean)` (o livro de receitas traz do armazenamento antes e devolve a sobra depois) | Item 16; confira o descritor completo com `javap -p` |
| `RecipeBookComponentMixin` (cliente) | `tick()`, o privado `updateStackedContents()` e a chamada interna `selectMatchingRecipes()`; campos `menu`, `stackedContents`, `timesInventoryChanged` | Item 16 |
| `BaseContainerBlockEntityMixin` | `saveAdditional(ValueOutput)` e `loadAdditional(ValueInput)` (memória do slot travado) | Item 13; API `ValueOutput.store` / `ValueInput.read` |
| `BaseContainerBlockEntityMixin` (rótulo e botão da N) | também `collectImplicitComponents(DataComponentMap.Builder)` e `applyImplicitComponents(DataComponentGetter)` (a shulker leva o rótulo e o "recebe com a N" no item, via `CUSTOM_DATA`); `saveAdditional`/`loadAdditional` gravam `stashlink_no_quick_stack` (`ValueInput.getBooleanOr`, `ValueOutput.putBoolean`) | Itens 14 e 17; `LabelCompat.writeToItem/readFromItem/readNoQuickStackFromItem` |
| `ShulkerBoxBlockMixin` | `ShulkerBoxBlock.getDrops(BlockState, LootParams.Builder)` (na sobrevivência o drop vem da tabela de loot, que não copia o rótulo nem o botão da N em CUSTOM_DATA; o mixin os põe no item) | Itens 14 e 17; `LabelCompat.writeToStack` |
| `EntityMixin` | `Entity.shouldBeSaved`, `Entity.broadcastToPlayer(ServerPlayer)` (alcance de 32 blocos do holograma; o contorno de destaque da busca só vai ao dono) e `Entity.entityTags()` | Itens 14 e 20 |
| `CompoundContainerAccessor` | campos privados `container1` / `container2` do baú duplo | Item 13 |
| `SlotMixin` | `Slot.mayPlace` e `ShulkerBoxSlot.mayPlace` (este não chama o pai) | Item 13; confira também se outros `Slot` do jogo sobrescrevem `mayPlace` |
| `ScreenInvoker` (cliente) | `Screen.addRenderableWidget` (protegido; `@Shadow` de método herdado não funciona) | Item 14 |
| `AbstractContainerScreenMixin` (cliente) | `init()`, `extractLabels(GuiGraphicsExtractor,int,int)`, `removed()`, `keyPressed(KeyEvent)` (lápis do rótulo, Item 14), `mouseClicked(MouseButtonEvent, boolean)`, `extractSlot(GuiGraphicsExtractor, Slot, int, int)` e o privado `getHoveredSlot(double, double)`, mais `extractRenderState(GuiGraphicsExtractor,int,int,float)` e `mouseScrolled(double,double,double,double)` (painel das estações, Item 16) | Item 13; em 26.3 os métodos de desenho se chamam `extract*` (antes `render*`) |
| `ContainerScreenOrganizeMixin` (cliente) | `AbstractContainerScreen.init()` (botões Organizar e Sistema ao lado da tela do baú). Usa `ScreenInvoker.addRenderableWidget` | Item 20 |
| `ServerGamePacketListenerImplMixin` | `ServerGamePacketListenerImpl.tryPickItem(ItemStack)` (privado; pick block resolvido no servidor) e o campo público `player` | Item 19; confira o nome com `javap -p` |
| `InventoryUtilsMixin` (Litematica) | `InventoryUtils.schematicWorldPickBlock` | Item 7 |

Os GameTests do Item 13 (`LockGameTests`) pegam quebra do `SlotMixin`, da memória no bloco e do baú duplo; o desenho
da prévia e o Alt + clique só se conferem no jogo (roteiro no `ROADMAP.md`).
Os do Item 14 (`LabelGameTests`) pegam quebra do texto limpo, do holograma (nunca gravado, sem órfão), da shulker/baú do End e do
baú duplo. Se mudar a versão: `EntityTypes.TEXT_DISPLAY`, os campos NBT do `text_display` (`text`, `billboard`, `view_range`...),
`Component.object(AtlasSprite)` e os nomes das texturas (`stashlink_sprites.txt` é gerado de `textures/item` e `textures/block`;
regenere com o comando no fim da seção "Item 14" do `ARCHITECTURE.md`) ficam em `LabelCompat`/`LabelText`.
Os do Item 17 (`FilterGameTests`) pegam a N ignorando o botão do baú ou as categorias, o botão perdido no disco, na shulker
(`collectComponents` e drop de sobrevivência) e o pedido aceito com o baú aberto por outro jogador. Se mudar a versão:
as tags de item (`ItemTags.HEAD_ARMOR`, `PICKAXES`, `SWORDS`, `SPEARS`, `ARROWS`...) e os componentes `FOOD`/`POTION_CONTENTS`
ficam só em `compat/mc/ItemKinds`; um teste de classificação (`itemsAreClassifiedByGameTags`) acusa tag renomeada.

Ao achar uma chamada nova que quebrou: crie o wrapper, ponha um javadoc dizendo **o que mudou e em qual versão**,
e acrescente uma linha na tabela acima.

## 6. Litematica / Forgematica

Confira com o script (ou Modrinth) **antes** de decidir a versão-alvo. Em 26.3: Litematica 0.29.1 existe só para
Fabric; **não há Forgematica** para NeoForge 26.3 — então a integração Litematica é Fabric-only por ora. Ao subir
de versão, atualize `"litematica": ">=x.y.z"` no `fabric.mod.json` para a versão mínima testada. O mixin só é
aplicado se o Litematica estiver presente (`LitematicaMixinPlugin`), então a ausência dele não quebra o mod.

## 7. Erros que já aconteceram

| Sintoma | Causa | Conserto |
|---|---|---|
| `cannot find symbol: Minecraft.screen` | campo removido em 26.3 | `ClientCompat.hasScreenOpen` (usa `mc.gui.screen()`) |
| `placeItemBackInInventory(ItemStack)` não existe | agora pede `Prediction` | `McCompat.placeBackInInventory` (`Prediction.SERVER_ONLY`) |
| Testes: `Items.RED_SHULKER_BOX` não existe | virou um item único com cor | `TestCompat.dyedShulker(DyeColor.RED)` |
| Testes: `VanillaRegistries.createLookup` não existe | renomeado | `TestCompat.registryLookup()` |
| NeoForge não resolve ou compila com erros estranhos de patch | `neoforge_version` e `neo_form_version` de linhas diferentes: os *patches* do NeoForge só valem para o NeoForm da própria versão (principalmente em betas) | use o `neo_form_version` correspondente ao NeoForge escolhido; o script mostra |
| NeoForge: `recompile` falha com `contents() ... cannot override` (`HolderSet$1`) mesmo com versões certas | plugin `net.neoforged.moddev` (no `build.gradle` raiz) velho demais para a nova versão do MC (2.0.141 não serve para 26.3) | subir o plugin (26.3 usa `2.0.148`); veja a última em maven.neoforged.net |
| "Components not bound yet" nos testes | componentes padrão dos itens não ligados | `MinecraftTestSetup.init()` (já faz) |

## 8. Log de migrações

### 26.1.2 para 26.3 (2026-09-30)
- `gradle.properties`: Minecraft 26.3, NeoForm 26.3-1, Fabric API 0.161.0+26.3, loader 0.19.5, NeoForge
  26.3.0.39-beta.
- Quebras de API: `Minecraft.screen`, `placeItemBackInInventory`, `DYED_SHULKER_BOX`, `createWorldLookup`
  (tabela da seção 7).
- Criada a camada `compat/mc/` e movidas para ela as chamadas frágeis (lista na seção 5).
- Litematica 0.29.1 só em Fabric; sem Forgematica para 26.3, então a integração é Fabric-only.

## 9. Commit / PR

Branch `chore/update-mc-<versão>`, commits pequenos, PR com squash merge. Descreva no PR: versões novas, o que
quebrou, o que foi testado no jogo. Nunca commitar em `main`.
