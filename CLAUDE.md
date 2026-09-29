# StashLink — instruções do projeto

Mod de QoL para Minecraft (Fabric + Forge/NeoForge). Plano em `docs/ROADMAP.md`, arquitetura em
`docs/ARCHITECTURE.md`. Regras pessoais globais do Eliel (`~/.claude/CLAUDE.md`) valem aqui.

## Fluxo

- Trabalhar **um item do roadmap por vez**, em branch própria (nome sugerido no item). Nunca commitar em `main`.
- Início de sessão: `git status` + `git fetch origin --prune`; `git pull --ff-only` se limpo.
- PR com squash merge. Ao concluir, marcar ✅ no `docs/ROADMAP.md` no mesmo PR.
- Lógica em `common/`; código de loader só como "cola".
- Toda mudança de item/inventário é **server-side** e validada (distância, permissão). Cliente só pede.
- Explicar decisões técnicas em linguagem simples (Eliel é engenheiro civil aprendendo programação).

## Handoff ao concluir um item

Gerar como último passo: (1) resumo em linguagem simples do que foi feito e por quê, incluindo bugs achados;
(2) prompt autocontido do próximo item: texto exato do roadmap, decisões já fixadas, arquivos/módulos
envolvidos, nome de branch. Último item do plano: avisar que terminou e perguntar o que vem a seguir.
