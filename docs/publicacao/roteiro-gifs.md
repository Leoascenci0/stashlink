# Roteiro de gravação dos GIFs do README (Item 22)

**Ficou para depois da 1.0** (decisão do Eliel, 2026-10-06: publicar sem gravar). A 1.0 saiu com o banner e as
ilustrações da galeria; a tabela dos GIFs está comentada no README. Quando gravar, salve com **exatamente** estes nomes,
descomente a tabela no README e envie os GIFs à galeria das lojas (dá para editar a página sem versão nova).

Regras gerais: 5 a 10 s cada, até ~5 MB, ~800 px de largura, 15 fps, loop. Mundo de teste com baús e shulkers
visíveis. Ferramenta sugerida: ScreenToGif (grava e já otimiza) ou gravar MP4 e converter.

| Arquivo | O que mostrar |
|---|---|
| `docs/img/refill.gif` | Colocando blocos até o stack da mão acabar → o stack se reabastece sozinho (de shulker no inventário ou baú por perto). Depois apertar **N** e os itens sumirem para os baús que já os têm. |
| `docs/img/bench.gif` | Abrir uma bancada de trabalho perto de baús → painel de busca/abas com os itens do armazenamento → clicar numa receita e fabricar sem pegar nada nos baús. |
| `docs/img/organize.gif` | Apertar **O** → prévia da organização → confirmar; mostrar um baú antes/depois e o **desfazer**. |

Não mostrar nada dos "limites conhecidos" (modo cliente, Litematica no NeoForge).

# Checklist por loja (Modrinth e CurseForge)

Campos iguais nas duas; o Eliel digita login/token e clica em publicar.

- **Nome:** StashLink · **Resumo:** frase de abertura de `pagina-loja-en.md` (≤ 256 caracteres no Modrinth).
- **Descrição:** colar `pagina-loja-en.md` sem o comentário do topo (o banner já vem do GitHub). Versão em português: `pagina-loja-pt.md`.
- **Categorias:** Utility, Storage (Modrinth) · Inventory/Utility (CurseForge).
- **Versão do jogo:** 26.3 · **Loader:** um arquivo por loader.
- **Fabric:** `stashlink-fabric-26.3-1.0.0.jar` — dependência **obrigatória** Fabric API; **opcional** Litematica.
- **NeoForge:** `stashlink-neoforge-26.3-1.0.0.jar` — sem dependências; **sem** Litematica (diga isso na página).
- **Licença:** MIT · **Ambiente:** cliente e servidor.
- **Links:** código `https://github.com/Leoascenci0/stashlink`, problemas `.../issues`.
- **Ícone:** `docs/img/icon.png` (400×400, 211 KB: a CurseForge pede no mínimo 400 px e o Modrinth aceita até 256 KB).
  É o baú com corrente recortado do banner; o mesmo desenho, em 256×256, é o ícone dentro do jogo.
- **Galeria** (ilustrações, não capturas do jogo: a legenda diz isso para não enganar quem baixa):

  | Arquivo | Título | Legenda |
  |---|---|---|
  | `docs/img/banner.jpg` (destaque) | StashLink | Storage within reach. |
  | `docs/img/galeria/visao-geral.jpg` | Storage within reach | Illustration: items come from nearby chests and shulker boxes straight to your hotbar. |
  | `docs/img/galeria/reabastecer.jpg` | Hand refill | Illustration: keep building; when your stack runs out, more comes from a shulker box or a nearby chest. |
  | `docs/img/galeria/bancadas.jpg` | Stations use your storage | Illustration: crafting table, furnace, anvil, enchanting table and more take ingredients from nearby storage. |
  | `docs/img/galeria/armazenamento.jpg` | Name, organize and find | Illustration: name your chests (in game the name floats as a hologram), organize them and search for an item. |
- **Changelog da versão:** seção 1.0.0 do `CHANGELOG.md`. Não prometer o que está em "Limites conhecidos".
