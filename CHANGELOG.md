# Changelog

## 1.0.0 — Minecraft 26.3 (Fabric e NeoForge)

Primeira versão pública. O StashLink faz o armazenamento por perto funcionar como se estivesse na sua mochila.

### Armazenamento por perto
- **Reabastecer a mão:** quando o item da mão acaba, vem mais de shulkers no inventário e de shulkers, baús e barris colocados perto.
- **Raio configurável:** baús, barris e bancadas com padrão 16 e até 32 blocos; shulkers colocadas com padrão 32 e até 64. O dono do servidor pode baixar o teto (`maxRadius`).
- **Botão do meio** mirando um bloco traz o item do armazenamento para a hotbar. Precisa de slot livre e nunca troca um item seu.
- **Litematica** (Fabric): Easy Place e pick block usam os itens do armazenamento. O bloco novo entra no mesmo slot e o anterior volta ao armazenamento, então a hotbar não enche. Com a hotbar cheia de itens seus, um aviso na barra de ação explica por que o bloco não veio. O log diz se a integração ligou; se uma versão nova do Litematica não for compatível, ela se desliga com aviso, sem afetar o resto do mod.
- **Baús e gavetas de outros mods** (Sophisticated Storage, Storage Drawers e outros baús e barris marcados como tal) contam como armazenamento: a N guarda neles, e a mão, o botão do meio e as bancadas tiram deles. Gavetas com milhares de itens num slot funcionam. Máquinas e controladores de rede nunca são usados. Servidores podem acrescentar blocos pela tag `stashlink:mod_storage` (datapack).

### Teclas e gestos
- **N** guarda seus itens nos baús por perto que já têm aquele item. **W** puxa tudo do container aberto.
- **Shift + clique esquerdo + passar o mouse** move cada item por onde o mouse passa, do baú para a mochila ou ao contrário. Funciona também em servidor sem o mod.
- Na tela do baú, o botão **N** liga/desliga se aquele baú recebe itens com a N. Na config, a aba **Tecla N** escolhe as categorias que a N guarda: armadura, ferramentas, armas, comida e poções.

### Organizar e encontrar
- **Nome no baú:** lápis ✎ na tela do baú ou tecla **J**. O nome aparece num holograma na frente do bloco (até 32 blocos), com símbolos (❤ ⭐ ⚡) e ícones de item (`:apple:`). A shulker leva o nome no item.
- **Slot reservado:** Alt + clique num slot de baú reserva o slot para um item. Só ele entra ali, a N prefere esse slot e o slot vazio mostra uma prévia.
- **Organizar:** botão Organizar no baú; tecla **O** organiza todo o armazenamento por perto, com prévia, Aplicar e Desfazer; busca de item que destaca o baú onde ele está.

### Bancadas e estações
- Bancada, fornalha, defumador, alto-forno, cortador de pedra, tear, mesa de cartografia, pedra de amolar, mesa de ferreiro, bigorna, mesa de encantamento, suporte de poções e sinalizador usam a sua mochila e o armazenamento por perto, a mochila primeiro. Só a mochila de quem abriu a estação: ninguém usa a mochila de outro jogador.
- Cada estação tem um painel no estilo do livro de receitas, com busca e abas, mostrando só o que serve nela. Em vermelho fica o que falta, e clicar põe o item no slot certo.
- Extras: lápis-lazúli automático no encantamento, livros que servem na bigorna, aba Combustível nas fornalhas, pagamento do sinalizador com um clique e todas as poções possíveis no suporte, montadas passo a passo.
- O inventário interno das estações nunca é usado como armazenamento: item que você deixou cozinhando não é mexido.

### Controle
- **Liga/desliga e cadeado** em cada função, como o seletor de dificuldade. O botão é seu; o cadeado é do dono do servidor ou de um operador (na tela ou com `/stashlink feature <nome> lock|unlock`).
- Tela de config no jogo (tecla **K**), comando `/stashlink` e preferências por jogador.
- **Modo cliente:** em servidor sem o mod, W, N, reabastecer a mão e Shift + passar o mouse funcionam por cliques normais de inventário.

### Segurança e desempenho
- Toda mudança de item é feita e validada no servidor (distância, permissões e claims).
- As funções que mexem em baú sem você abri-lo (N, botão do meio, Litematica, reabastecer a mão e bancadas) pulam o baú que outro jogador está olhando, até ele fechar.
- Pedidos ao servidor com limite de ritmo (travar slot, botão N, rótulo, preferências, bancada) e recusados em telas de outros mods que só imitam um baú. Config quebrada ou de versão futura ganha uma cópia `.bak` antes de ser sobrescrita.
- Testado em servidor com 2 jogadores, 289 containers e Carpet, sem duplicação nos cenários testados; varredura bem abaixo de 5 ms por operação com raio 32.
- Revisão completa antes da publicação (`docs/REVISAO-1.0.md`): corrigido o reabastecimento que apagava o balde, a tigela ou a garrafa vazia, e fechados três atalhos pelos quais um cliente adulterado passava por um cadeado.

### Limites conhecidos
- Modo cliente (servidor sem o mod) e a troca de slot do Litematica ainda sem teste em jogo.
- NeoForge com poucos testes automáticos: só os de baús e gavetas de outros mods rodam lá; o resto roda no Fabric (o código é o mesmo).
- Em baús e gavetas de outros mods: o W não funciona na tela deles, e slot reservado, nome e Organizar não valem. Shulkers de outros mods não contam. Se outro jogador está perto com a tela de um mod aberta, o bloco fica de fora naquele momento, por segurança.
- Slot reservado não vale para funil nem para mods que mexem direto no container, e só funciona com o mod no servidor e no cliente.
- O nome do baú usa só os símbolos que a fonte do jogo tem; emojis coloridos (📦) são descartados.
- Com JEI, EMI ou REI, o painel das bancadas pode ficar por baixo da lista deles (compatibilidade planejada).
