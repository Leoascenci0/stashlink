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
