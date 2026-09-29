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
| Dupe/perda de item em corrida | Operações atômicas (simular → aplicar), GameTests (Item 5) |
| Performance com muitos containers | Índice + raio com teto no servidor (Item 6) |
| Anti-grief/claims | Checar interação permitida antes de tocar container alheio |
| Litematica muda API entre versões | Compat isolada, fixar versão de teste, documentar |
| Tecla W conflita com movimento | Só em telas de container sem campo de texto; configurável |
| Componentes de item mudam por versão | Camada única de acesso ao conteúdo de shulker (Item 2) |

## Decisões (preencher no Item 0)

- Versão do Minecraft: _a decidir_
- Loaders: _a decidir (recomendado Fabric + NeoForge)_
- Template: _a decidir (recomendado MultiLoader-Template)_
- Licença / mod id: _a decidir_
