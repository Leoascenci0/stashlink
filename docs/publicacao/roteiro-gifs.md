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
- **Versão do jogo:** 26.3 · **Loader:** um arquivo por loader, com número `1.0.0+fabric` e `1.0.0+neoforge` (assim no Modrinth).
- **Ambiente (Modrinth):** "Client and server → Optional on both, works best when installed on both sides" (só cliente:
  modo cliente; só servidor: reabastecer funciona com cliente sem o mod).
- **Aviso de IA (Modrinth → Disclosures):** "Contains AI-generated content" marcado, com Code, Assets e Text. A CurseForge
  também pergunta: responder igual.
- **Fabric:** `stashlink-fabric-26.3-1.0.0.jar` — dependência **obrigatória** Fabric API; **opcional** Litematica.
- **NeoForge:** `stashlink-neoforge-26.3-1.0.0.jar` — sem dependências; **sem** Litematica (diga isso na página).
- **Licença:** MIT · **Ambiente:** cliente e servidor.
- **CurseForge (o que deu certo em 2026-10-06):** descrição no modo **Markdown** (o seletor fica acima do editor); aba
  Source → GitHub `Leoascenci0/stashlink`; License → MIT e "Allow distribution to 3rd party"; por arquivo: Environment
  Client + Server, Modloader, Java 25, Minecraft 26.3, "Publish this file automatically once approved". Ao enviar o
  segundo arquivo o formulário vem com o loader do anterior marcado: conferir antes de "Add File".
- **Links:** código `https://github.com/Leoascenci0/stashlink`, problemas `.../issues`.
- **Ícone:** `docs/img/icon.png` (400×400, 70 KB, PNG de 256 cores: a CurseForge pede no mínimo 400 px e no máximo 100 KB).
  É o baú com corrente recortado do banner; o mesmo desenho, em 256×256, é o ícone dentro do jogo.
- **Galeria** (ilustrações, não capturas do jogo: a legenda diz isso para não enganar quem baixa). Título e legenda nas
  **duas línguas**, inglês e português, separadas por " · " (pedido do Eliel, 2026-10-06). Colar exatamente assim:

  | Arquivo | Título | Legenda |
  |---|---|---|
  | `docs/img/banner.jpg` (destaque) | StashLink | Storage within reach. · Seu armazenamento, sempre à mão. |
  | `docs/img/galeria/visao-geral.jpg` | Storage within reach · Armazenamento ao alcance | Illustration: items come from nearby chests and shulker boxes straight to your hotbar. · Ilustração: os itens vêm dos baús e shulkers por perto direto para a sua hotbar. |
  | `docs/img/galeria/reabastecer.jpg` | Hand refill · Reabastecer a mão | Illustration: keep building; when your stack runs out, more comes from a shulker box or a nearby chest. · Ilustração: continue construindo; quando a pilha acaba, vem mais de uma shulker ou de um baú por perto. |
  | `docs/img/galeria/bancadas.jpg` | Stations use your storage · Estações usam seu armazenamento | Illustration: crafting table, furnace, anvil, enchanting table and more take ingredients from nearby storage. · Ilustração: bancada, fornalha, bigorna, mesa de encantamento e outras pegam os ingredientes do armazenamento por perto. |
  | `docs/img/galeria/armazenamento.jpg` | Name, organize and find · Nomeie, organize e encontre | Illustration: name your chests (in game the name floats as a hologram), organize them and search for an item. · Ilustração: dê nome aos baús (no jogo o nome flutua como holograma), organize e busque um item. |
- **Changelog da versão:** seção 1.0.0 do `CHANGELOG.md`. Não prometer o que está em "Limites conhecidos".
