# Segunda revisão completa, depois da 1.0 (2026-11-14)

Revisão do mod inteiro com a 1.0.0 já publicada: seis áreas, cada uma lida por um revisor separado (rede e config, itens,
bancadas, Organizar e rótulos, cliente e modo cliente, build e docs). Tudo o que já estava em `docs/REVISAO-1.0.md` como
corrigido ou aceito ficou de fora. Como na primeira revisão, este PR só traz **correções pequenas com teste**; o que é
grande virou item novo no roadmap (Itens 29–38).

**Resumo:** nenhum caminho de duplicar ou perder item e nenhuma forma de derrubar o servidor. Os achados são regras do
próprio projeto que tinham um furo (o mais importante: **baú duplo aberto por outro jogador não era detectado**), um
cadeado contornável e incômodos visíveis. Corrigidos 11; os testes novos falham no código anterior (teste de mutação) e
passam agora: **211 GameTests no Fabric** (204 + 7) e 220 unitários verdes.

## Achados

Gravidade: **alta** = dupe, perda de item ou queda do servidor; **média** = regra do projeto quebrada, cadeado
contornável ou falha visível; **baixa** = robustez, memória, texto.

| # | Área | Achado | Grav. | Onde | Status |
|---|------|--------|-------|------|--------|
| 1 | Itens / Rede | **Baú duplo aberto por outro jogador não era reconhecido.** A tela de quem abriu e a varredura do mod criam cada uma o seu objeto para o mesmo par de blocos, e a comparação só enxergava "metade contra baú duplo". N, reabastecer, botão do meio, Litematica, bancadas, Organizar, travar slot e o botão N mexiam no baú por baixo de quem estava olhando (achado por dois revisores) | média | `source/ContainerInsert.isOrContains` | **corrigido** (compara as metades dos dois lados) + `Review2GameTests.doubleChestOpenedByAnotherIsSkipped` |
| 2 | Rede | Baú duplo na divisa de um terreno protegido: o mod só perguntava a permissão de **uma** metade | média | `source/NearbyContainers.entries` | **corrigido** (as duas metades precisam estar liberadas) + `doubleChestNeedsBothHalvesAllowed` |
| 3 | Itens | Reabastecer a mão disparava quando o jogador só **mudava o item de lugar** (shift-clique ou tecla numérica com a própria mochila aberta): vinha uma pilha extra do baú sem necessidade | média | `refill/RefillService.tickPlayer` | **corrigido** (só reabastece se a quantidade do item no inventário diminuiu) + `refillIgnoresItemMovedInsideTheInventory` |
| 4 | Bancadas | Cadeado do sinalizador (`bench_beacon`) não valia para o pedido forjado "ponha no meu cursor": uma pilha de pagamento saía do baú | média | `bench/BenchSync.listed` | **corrigido** + `beaconLockAlsoCoversCursorRequests` |
| 5 | Bancadas | Espada do baú posta na bigorna, renomeada e com o **resultado no cursor** ao fechar: o resultado ia para o baú (o caderno só conferia o tipo do item). O mesmo com a ferramenta própria do jogador trocada no cursor | média (nada some) | `bench/BenchLedger.returnCursor`, `reconcile` | **corrigido** (o caderno guarda como era cada item emprestado; só ele volta) + `anvilResultOnTheCursorStaysWithThePlayer` e `borrowedItemOnTheCursorStillReturnsToTheChest` |
| 6 | Rede | Prévia/busca do Organizar mandava até 256 itens **com todos os componentes** e sem teto de bytes: biblioteca de livros escritos ou shulkers cheias estourava o limite do pacote e derrubava a conexão de quem pediu, a cada tentativa | média | `organize/OrganizeService.preview`, `find` | **corrigido** (item acima de 2 KiB vai só como o tipo) + `organizeRowsNeverCarryHeavyItems` |
| 7 | Organizar | Aplicar com outra tela aberta apagava a prévia em silêncio; a ordem do criativo era montada **antes** do cadeado e do intervalo (podia repetir a cada pacote) | baixa | `organize/OrganizeService.apply`, `process` | **corrigido** (os testes do Organizar seguem verdes) |
| 8 | Config | `stashlink.json` e `stashlink-client.json` eram gravados direto: queda no meio da gravação deixava o arquivo cortado | baixa | `config/StashLinkConfig.save`, `client/ClientPrefs.save` | **corrigido** (grava num arquivo vizinho e troca) |
| 9 | Modo cliente | Último balde/tigela/garrafa usado perto de um baú: o modo cliente abria e fechava um baú à toa (ele só repõe com a mão vazia) | média | `clientmode/ClientModeEngine.watchHands` | **corrigido** — sem teste automático (cliente) |
| 10 | Modo cliente | Entre dois baús da N, agachar fazia o clique **usar o item da mão** na face do baú (colocar bloco, despejar balde); abrir o inventário no intervalo deixava um baú abrindo sem ninguém cuidando | média | `clientmode/ContainerSession.openNext` | **corrigido** (interrompe a sessão) — sem teste automático (cliente) |
| 11 | Cliente | Shift + passar o mouse ignorava os slots travados escolhidos no mundo local (a tela grava na config do mundo, o gesto só lia as preferências pessoais) | baixa | `client/HoverCollectClient` | **corrigido** — sem teste automático (cliente) |
| 12 | Build | `release.yml` não conferia a tag contra a versão: tag `v1.0.1` sem subir a versão publicaria os jars 1.0.0 com o título novo | média | `.github/workflows/release.yml` | **corrigido** (o workflow falha se não bater) |
| 13 | Build | Falha de teste só no CI não deixava relatório para baixar; sem tempo-limite | baixa | `.github/workflows/build.yml` | **corrigido** |
| 14 | Docs | `UPDATING.md` e `gradle.properties` apontavam para `buildSrc/` (a pasta é `build-logic/`) | baixa | `docs/UPDATING.md:31` | **corrigido** |
| 15 | Modo cliente | W e Shift + passar aceitam **qualquer tela de baú** em servidor sem o mod: numa loja ou kit feito com tela de baú, a W clica em tudo (compra/resgata tudo). O conserto do servidor (`isWorldStorage`) não existe no cliente | média | `client/LootAllKey`, `HoverCollectClient`, `ClientModeEngine.startLootAll` | **vira item** (Item 30) |
| 16 | Modo cliente | Sem checar linha de visão e com até 5 cliques por tick: anticheats podem marcar | média (plausível) | `clientmode/ClientContainers.find`, `ClientModeEngine.CLICKS_PER_TICK` | **vira item** (Item 30) |
| 17 | Bancadas | Morte ou troca de dimensão com a estação aberta sem teste; queda do servidor com item emprestado na grade; cursor/PLACE varrem o raio 1 vez por tick; PLACE não usa o mesmo filtro do cursor | baixa | `bench/BenchLedger`, `BenchPullService` | **vira item** (Item 31) |
| 18 | Itens | Slots travados do jogador valem para N e W, mas não para o botão do meio, Litematica e devoluções à mochila; shift-clique completa pilha em slot reservado para outro item (a conferir) | baixa/média | `pull/PullLogic.chooseSlot`, `source/StackListSink` | **vira item** (Item 32) |
| 19 | Organizar | Aplicar/Desfazer conferem o conteúdo, mas não as travas e filtros criados depois da prévia | baixa | `organize/OrganizeSystem.write` | **vira item** (Item 32) |
| 20 | Rede | Aviso de função trancada sai antes do anti-flood (1 pacote de resposta por pacote recebido); operador nível 1 tranca pela tela mas não pelo comando | baixa | `config/FeatureGate.allow`, `compat/mc/McCompat.canManageServer` | **vira item** (Item 33) |
| 21 | Dados | Uma trava de slot com item de mod removido apaga todas as travas do baú ao carregar; rótulo na shulker sobrescreve outros dados do item; rótulos do End sem teto por jogador | baixa | `mixin/BaseContainerBlockEntityMixin`, `compat/mc/LabelCompat.writeToItem`, `label/EnderLabels` | **vira item** (Item 34) |
| 22 | Organizar | A ordem do criativo é montada uma vez por execução do jogo e reconstrói uma lista compartilhada com o cliente no mundo local | baixa (plausível) | `compat/mc/OrganizeCompat.ensureOrder` | **vira item** (Item 34) |
| 23 | Desempenho | Organizar o sistema compara tudo com tudo (modelos × baús × slots); cada função refaz a varredura no mesmo tick | baixa | `organize/OrganizeSystem.movePhase` | **vira item** (Item 35; medir antes) |
| 24 | Build | GameTests do NeoForge fora do CI; release depende de baixar mods de teste na hora; sem teste automático de idioma | média | `.github/workflows/` | **vira item** (Item 36) |
| 25 | Cliente | W dispara digitando na busca do JEI/REI; botões Organizar/Sistema sem zona de exclusão; redimensionar a janela perde a prévia do Organizar e o rótulo em edição | baixa | `client/LootAllKey`, `mixin/ContainerScreenOrganizeMixin`, `client/OrganizeScreen` | **vira item** (Item 37) |
| 26 | Docs | `pack.mcmeta` com `pack_format` 8 e a chave de idioma `stashlink.label.note` sem uso | baixa | `common/src/main/resources/` | aceito como limite (os loaders ignoram; texto morto inofensivo) |

### O que foi conferido e está certo

- **Itens:** toda transferência continua "simular → aplicar", inclusive nas tomadas de outros mods (Fabric aborta a
  simulação e confirma a de verdade; NeoForge igual); contas com gavetas enormes não estouram; shulker dentro de shulker
  barrada nos dois caminhos; chunks nunca são carregados à força; o raio é calculado num lugar só.
- **Bancadas:** nenhum slot de resultado é clicado sozinho; receita forjada (índice fora da faixa) não faz nada; o
  planejador de poções não entra em laço; o caderno nunca devolve mais do que emprestou.
- **Organizar:** Aplicar e Desfazer reconferem cada baú, slot por slot, e são "tudo ou nada"; a ordenação é consistente.
- **Rede:** todo pedido tem `try/catch`, confere jogador vivo, tela certa e limites de tamanho; Fabric e NeoForge
  registram os mesmos 16 pacotes, 5 teclas, comandos e eventos.
- **Cliente:** o modo cliente nunca manda pacote próprio a servidor sem o mod; trocar de servidor zera o estado.
- **Build:** os mods de teste e os GameTests não entram nos jars publicados; idiomas pt_br e en_us com as mesmas chaves.
- A branch antiga `fix/organizar-reserva` já está na `main` (PR #43); pode ser apagada.

### Teste de mutação

Com as correções 1–5 desfeitas (e os testes novos mantidos), cinco testes falham, um por correção: baú duplo aberto
("foi mexido: 25"), metade negada ("o baú recebeu: 25"), item só movido ("o baú cedeu"), sinalizador trancado ("cursor 3
diamantes") e resultado da bigorna no baú. Com as correções, os 211 passam.

## Desempenho

Linha `[STASHLINK-PERF]` desta sessão (289 containers, raio 32, média/pior em µs, máquina compartilhada):
find 318/1407, findAll 161/491, N 1498/5373, reabastecer 263/746, pedir item ausente 760/2903, tick parado 0. Tudo abaixo
do teto de 5 ms na média, como na 1.0. As correções não mexem na varredura; a única conta nova por tick é somar um tipo
de item nos 41 slots do jogador (reabastecimento). **Nenhuma otimização entrou**: os alvos ficaram no Item 35, com a
regra de sempre (sem número antes e depois, não entra).
