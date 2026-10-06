<!-- Versão em português da página da loja (para conferir o texto ou usar como descrição traduzida).
     A versão principal, para colar no Modrinth/CurseForge, é a pagina-loja-en.md. -->

<p align="center"><img src="https://raw.githubusercontent.com/Leoascenci0/stashlink/main/docs/img/banner.jpg" alt="StashLink — Storage Within Reach"></p>

<p align="center"><b>Seu armazenamento, sempre à mão.</b><br>
Reabasteça, guarde, crafte e organize usando os baús ao seu redor, como se tudo estivesse na sua mochila.</p>

---

## ✨ O que ele faz

Você montou uma sala de armazenamento. Agora pare de ir e voltar até ela.

O StashLink faz os **baús, barris e shulkers por perto** funcionarem como uma extensão do seu inventário. A mão se
reabastece sozinha, uma tecla guarda tudo e cada estação de trabalho pega os ingredientes direto do armazenamento.

## 📦 Armazenamento ao alcance

| | |
|---|---|
| **Reabastecer a mão** | Quando a pilha acaba, vem mais das shulkers do inventário e de baús, barris e shulkers por perto. Construa sem parar. |
| **Botão do meio** | Clique com o botão do meio num bloco e o item vem do armazenamento para a hotbar. Nunca troca um item seu. |
| **Litematica** (Fabric) | Easy Place e pick block pegam os blocos do armazenamento. O bloco anterior volta, e a hotbar não enche. |
| **Armazenamento de outros mods** | Baús, barris e gavetas de mods como Sophisticated Storage e Storage Drawers também contam. Gavetas com milhares de itens funcionam. Máquinas e controladores de rede nunca são usados. |
| **Raio ajustável** | Baús, barris e estações: padrão 16 blocos, até 32. Shulkers colocadas: padrão 32, até 64. |

## ⌨️ Uma tecla e pronto

| Tecla | Ação |
|---|---|
| **N** | Guarda seus itens nos baús por perto que já têm aquele item. |
| **W** | Pega tudo do container aberto. |
| **Shift + clique esquerdo + passar o mouse** | Move cada item por onde o mouse passa, nos dois sentidos. |
| **O** | Organiza todo o armazenamento por perto, com prévia e desfazer, e busca um item. |
| **J** | Dá nome ao container que você está olhando. |
| **K** | Abre a configuração do StashLink. |
| **Alt + clique** | Reserva um slot do baú para um item. |

Escolha o que a **N** guarda: armadura, ferramentas, armas, comida e poções têm cada uma o seu botão. Um botão dentro de
cada baú decide se aquele baú recebe itens da N.

## 🛠️ Estações que enxergam o armazenamento

Bancada, fornalha, defumador, alto-forno, cortador de pedra, tear, mesa de cartografia, pedra de amolar, mesa de ferreiro,
bigorna, mesa de encantamento, suporte de poções e sinalizador usam o armazenamento ao redor.

- **Sua mochila primeiro**, depois o armazenamento por perto. Só a mochila de quem abriu a estação é usada.
- **Painel no estilo do livro de receitas**, com busca e abas, mostrando **só o que serve naquela estação**.
- O que falta aparece em **vermelho**. Um clique põe o item no slot certo.
- O **lápis-lazúli** entra sozinho na mesa de encantamento e volta ao baú ao fechar.
- A **bigorna** mostra só os livros encantados que servem no item colocado.
- O **suporte de poções** lista todas as poções que dá para fazer, um passo por clique.
- O **sinalizador** recebe o pagamento do armazenamento com um clique.

Item que você deixou cozinhando ou fermentando **nunca é mexido**. Funciona ao lado do **JEI** e do **REI**: a lista de itens deles sai da frente do painel.

## 🗂️ Organizar e encontrar

- **Dê nome aos baús** com o lápis ✎ ou a tecla **J**. O nome flutua na frente do bloco, com símbolos (❤ ⭐ ⚡) e ícones
  de item (`:apple:`, `:oak_log:`). A shulker guarda o nome quando é quebrada.
- **Organize** um baú com um botão, ou todo o armazenamento com **prévia** antes de mover, e **Desfazer** se mudar de ideia.
- **Busque** um item e o baú que tem ele fica **destacado** no mundo.

## 🔒 Feito para servidores

- **Cada função tem liga/desliga e cadeado**, como o cadeado da dificuldade. O botão é seu; o cadeado é do dono do servidor
  ou dos operadores (`/stashlink feature <nome> lock|unlock`).
- **Tudo o que move item roda no servidor** e é conferido: distância, permissões e claims.
- Testado com 2 jogadores, 289 containers e Carpet, sem duplicação nos casos testados; a varredura fica bem abaixo de
  5 ms por ação com raio 32.
- **Modo cliente:** em servidor sem o mod (como o Realms), N, W, reabastecer a mão e Shift + passar o mouse continuam
  funcionando com cliques normais de inventário, no alcance normal do jogo.

## 📥 Instalação

1. Instale o **Fabric** (com o Fabric API) ou o **NeoForge** para o **Minecraft 26.3**.
2. Coloque o jar do StashLink na pasta `mods`.
3. Servidor: instale lá também para ter o raio completo, as estações, os nomes, o organizar e os cadeados.
4. Opcional: **Litematica** (Fabric).

## ⚠️ Limites conhecidos

- Ainda sem teste em jogo (só testes automáticos): modo cliente, troca de slot do Litematica com o Easy Place, estações
  usando a mochila de quem abriu e o painel das estações ao lado do JEI e do REI.
- NeoForge com menos testes automáticos (só os de armazenamento de outros mods); o resto roda no Fabric, com o mesmo código.
- No armazenamento de outros mods, a tecla W não funciona na tela deles, e slot reservado, nome e Organizar não valem.
  Shulkers de outros mods não contam.
- Slot reservado não vale para funil nem para mods que mexem direto no container.
- O nome do baú usa a fonte do jogo, então emojis coloridos (📦) são removidos.
- O EMI ainda não tem versão para o Minecraft 26.x, então não foi testado.

<p align="center"><sub>Licença MIT · feito por Leoascenci0</sub></p>
