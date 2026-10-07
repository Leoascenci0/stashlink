# StashLink

<p align="center"><img src="docs/img/banner.jpg" alt="StashLink — Storage Within Reach"></p>

Mod de qualidade de vida para Minecraft 26.3: **Fabric** e **NeoForge**. O armazenamento por perto funciona como se
estivesse na sua mochila.

**Baixar:** [GitHub Releases](https://github.com/Leoascenci0/stashlink/releases/latest) (um jar para Fabric e um para
NeoForge). Modrinth e CurseForge: em breve.

<!-- GIFs (depois da 1.0): gravar no jogo seguindo docs/publicacao/roteiro-gifs.md e trocar este comentário por:
| Reabastecer e tecla N | Bancadas com armazenamento | Organizar e buscar |
|---|---|---|
| ![](docs/img/refill.gif) | ![](docs/img/bench.gif) | ![](docs/img/organize.gif) |
-->

- **Reabastecer a mão** com itens de shulkers no inventário e de baús, barris e shulkers por perto.
- **N** guarda seus itens nos baús que já os têm; **W** puxa tudo; **Shift + clique esquerdo + passar o mouse** move item por item.
- **Botão do meio** num bloco traz o item do armazenamento para a hotbar; **Litematica** (Fabric) constrói com os itens das shulkers.
- **Bancadas e estações** (bancada, fornalhas, ferreiro, bigorna, encantamento, poções, sinalizador...) usam o armazenamento
  por perto, com um painel de busca e abas que mostra só o que serve.
- **Nome no baú** (lápis ✎ ou **J**), com holograma; **Alt + clique** reserva um slot para um item; **O** organiza todo o
  armazenamento, com prévia e desfazer, e acha o baú de um item.
- Cada função tem **liga/desliga e um cadeado** na tela de config (tecla **K**), como o seletor de dificuldade do jogo: o
  botão é seu; o cadeado, do dono do servidor ou de um operador (ou `/stashlink feature <nome> lock|unlock`).

## Dois modos de funcionar

- **Servidor com o StashLink** (mundo local, ou servidor com o mod): o servidor faz o trabalho. Raio configurável
  (baús, barris e bancadas: padrão 16, até 32 blocos; shulkers: padrão 32, até 64), usa shulkers e baús próximos, e tudo é validado no servidor.
- **Modo cliente** (servidor **sem** o mod, como um Realms): o mod funciona só no seu cliente, agindo como um
  jogador. Ele abre o container, move os itens por cliques de inventário e fecha — o mesmo que você faria à mão,
  só que automático. Liga/desliga em "Modo cliente" na tela de configuração (padrão: ligado). Cobre **W**
  (puxar tudo) e **N** (guardar em containers que já têm o item). Também reabastece a mão: quando o
  último item acaba, abre containers/shulkers colocados perto (os mais prováveis primeiro), traz o mesmo item para a
  mão e fecha. Shulker no inventário fica fora: o cliente não vê o conteúdo dela.

Limites do modo cliente:
- O alcance é o de interação do jogo (~4,5 blocos), não o raio do servidor.
- O mod só conhece o conteúdo de um container depois de abri-lo.
- Não reabastece a partir de shulker que está no **inventário** (isso só com o mod no servidor).
- As proteções/claims do servidor valem sozinhas: se o servidor não deixa abrir o container, o mod não abre.
- Os containers abrem e fecham de forma visível enquanto o mod trabalha.

Status: versão 1.0. Veja [CHANGELOG.md](CHANGELOG.md), [docs/ROADMAP.md](docs/ROADMAP.md) e [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

## Instalar

Baixe o jar do seu loader (Fabric ou NeoForge) na página de Releases e coloque na pasta `mods`. Litematica é opcional (só Fabric).

## Testes

`./gradlew build` (testes unitários) e `./gradlew :fabric:runGameTest` (servidor real com jogadores simulados).
