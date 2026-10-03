# Changelog

## 1.0.0 — Minecraft 26.3 (Fabric e NeoForge)

- **Reabastecer a mão** a partir de shulkers no inventário, e de shulkers/baús/barris colocados perto.
- **Raio de fontes** configurável: baús, barris e bancadas até 16 blocos; shulkers colocadas até 64 (padrão 32).
- **Litematica** (Fabric): Easy Place e pick block usam os itens das shulkers; o bloco novo troca no mesmo slot e o anterior volta ao armazenamento (a hotbar não enche).
- **Slot travado**: Alt + clique num slot de baú reserva o slot para aquele item (só ele entra; N prefere o slot; a prévia aparece no slot vazio). Fica gravado no baú e funciona em baú duplo, barril e shulker colocada.
- **Tecla N** guarda itens em baús próximos que já têm o item; **tecla W** puxa tudo do container aberto. Na tela do baú, o botão **N** liga/desliga se aquele baú recebe itens com a N (vale para baú, barril e shulker, e persiste); na config, a aba **Tecla N** escolhe as categorias que a N guarda (armadura, ferramentas, armas, comida, poções), cada uma com cadeado do servidor.
- **Liga/desliga + cadeado** em cada função (reabastecer, N, W, Litematica, Alt + clique, nome do baú), como o seletor de dificuldade: o botão é seu, o cadeado é do servidor (dono ou operador, pela tela ou `/stashlink feature <nome> lock|unlock`); trancada, a função não funciona naquele servidor.
- **Config**: tela no jogo (tecla K), `/stashlink`, preferências por jogador (Realms sem comandos).
- **Modo cliente**: W, N e reabastecer a mão funcionam em servidor sem o mod (ex.: Realms).
- Testado em servidor real com 2 jogadores, 289 containers e Carpet; sem dupe nos cenários testados.
- Testado em jogo pelo Eliel num servidor próprio com o mod instalado (2026-10-03): funciona. O Realms foi abandonado como alvo de teste.

Limites conhecidos: slot travado não vale para funil nem para mods que mexem direto no container, e só com o mod no servidor e no cliente (desenho da prévia ainda sem teste em jogo); modo cliente (servidor sem o mod) e Easy Place com Litematica ainda sem teste em jogo; NeoForge sem testes automáticos.
