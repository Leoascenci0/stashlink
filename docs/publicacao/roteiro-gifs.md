# Roteiro de gravação dos GIFs do README (Item 22)

O README já aponta para três arquivos em `docs/img/`. Hoje **nenhum existe** (só `icon.png`), então a página mostraria
imagem quebrada. Grave, salve com **exatamente** estes nomes e avise a sessão.

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
- **Descrição:** colar `pagina-loja-en.md` (trocar `ICON_URL`/`GIF_*` pelos links da galeria da loja). Versão em português: `pagina-loja-pt.md`.
- **Categorias:** Utility, Storage (Modrinth) · Inventory/Utility (CurseForge).
- **Versão do jogo:** 26.3 · **Loader:** um arquivo por loader.
- **Fabric:** `stashlink-fabric-26.3-1.0.0.jar` — dependência **obrigatória** Fabric API; **opcional** Litematica.
- **NeoForge:** `stashlink-neoforge-26.3-1.0.0.jar` — sem dependências; **sem** Litematica (diga isso na página).
- **Licença:** MIT · **Ambiente:** cliente e servidor.
- **Links:** código `https://github.com/Leoascenci0/stashlink`, problemas `.../issues`.
- **Ícone** (`icon.png`, quadrado) e **galeria** (os três GIFs).
- **Changelog da versão:** seção 1.0.0 do `CHANGELOG.md`. Não prometer o que está em "Limites conhecidos".
