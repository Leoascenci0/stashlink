# Ícone e nome do mod

> **Resolvido em 2026-10-06:** o ícone novo é o baú com corrente recortado do banner que o Eliel gerou
> (`docs/img/banner.jpg`). Este arquivo fica como registro de por que trocar e de como gerar outro.

## Por que trocar o ícone atual

O ícone atual (`docs/img/icon.png`) é bonito em tamanho grande, mas tem muita coisa: baú, shulker, 5 itens, a hotbar e
partículas. Nas lojas ele aparece em **64×64** ou **96×96** e vira uma mancha roxa. Ícone profissional de mod é
**uma ideia só, com silhueta forte**: dá para reconhecer em 32 px e lembra o nome.

Ideia central: **um baú com um "elo"**, o símbolo de link, porque o mod liga o armazenamento a você.

## Prompt para gerar o ícone (colar no gerador de imagem)

> Square app icon for a Minecraft mod called "StashLink", 1024x1024, clean professional pixel-art style inspired by
> Minecraft textures but crisp and readable at small size. Centered subject: a single wooden Minecraft chest seen from a
> slight 3/4 angle, lid slightly open, with a bold glowing chain link (two interlocked rounded rectangles) in teal-cyan
> (#2EC4B6) emerging from the opening and wrapping around the front. Strong dark outline around the whole silhouette.
> Background: smooth rounded-square tile with a deep navy-to-indigo gradient (#14213D to #2B2D6E), subtle soft vignette,
> no scenery, no stars, no particles, no text, no letters, no hotbar, no extra items. Limited palette: warm oak brown,
> gold latch, teal-cyan glow, navy background. High contrast, flat lighting with one soft rim light from the top left.
> The icon must stay recognizable at 64x64 pixels. Centered composition with 10% padding on all sides.

**Variação 2 (mais minimalista):**

> Minimal flat icon, 1024x1024, a pixel-art Minecraft chest front view in warm oak brown with a gold latch shaped like a
> chain link, glowing teal-cyan. Solid deep navy rounded-square background. Thick outline, 3 to 4 colors only, no text,
> no background details. Readable at 32x32.

**Variação 3 (armazenamento conectado):**

> Pixel-art icon, 1024x1024: three small Minecraft containers (chest, barrel, purple shulker box) arranged in a triangle,
> connected by glowing teal-cyan lines to a center point shaped like a backpack. Deep navy rounded-square background,
> thick outlines, no text, no particles, high contrast, readable at 64x64.

### Depois de gerar
1. Teste em tamanho pequeno: reduza para 64×64 e 32×32. Se não der para ver o baú e o elo, gere de novo.
2. Salve como PNG quadrado em `common/src/main/resources/assets/stashlink/icon.png` (é o ícone dentro do jogo; 128×128 ou
   256×256 bastam) e uma cópia em `docs/img/icon.png` (README).
3. Nas lojas, envie a versão 512×512 ou 1024×1024.
4. Para o **banner** da página (CurseForge e Modrinth aceitam), use o mesmo prompt com "wide 1920x480 banner, chest on the
   left, empty space on the right for the title" e escreva o nome por cima num editor. Geradores de imagem erram letras.

## Nome

O nome **StashLink** já é bom: curto, fácil de lembrar e diz o que faz (stash = estoque, link = ligação). Trocar o
**identificador interno** (`stashlink`) seria arriscado: ele está no nome do arquivo de config, nos dados gravados
nos baús e nos comandos. Um mundo que já usa o mod perderia nomes, slots reservados e filtros. O que dá para mudar com
segurança é o **nome de exibição** na loja e na lista de mods (`mod_name` em `gradle.properties`).

Sugestões, da mais recomendada para a menos:

| Nome de exibição | Por quê |
|---|---|
| **StashLink: Storage Within Reach** ⭐ | Mantém a marca e acrescenta o que o mod faz. Ajuda na busca da loja ("storage"). |
| **StashLink: Nearby Storage & Crafting** | Mostra as duas metades do mod: armazenamento e estações. |
| **StashLink: Craft & Refill from Chests** | Mais direto para quem procura "craft from chests". |
| **Reachstash** | Nome novo de uma palavra: "estoque ao alcance". Perde o histórico do nome atual. |
| **Stockwise** | Soa como produto: "estoque esperto" (organizar, buscar, reabastecer). |

**Recomendação:** ficar com **StashLink** e usar o subtítulo **"Storage Within Reach"** na loja (nome do projeto ou resumo
curto). Antes de decidir, pesquise o nome no Modrinth e no CurseForge para ver se já existe algo parecido. Os slugs
`stashlink` e `stash-link` precisam estar livres.

Resumo curto para a loja (campo "Summary", até ~120 caracteres):

> Refill, store, craft and organize from the chests around you, as if everything were in your backpack.
