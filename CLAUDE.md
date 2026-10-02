# StashLink — instruções do projeto

Mod de QoL para Minecraft (Fabric + Forge/NeoForge). Plano em `docs/ROADMAP.md`, arquitetura em
`docs/ARCHITECTURE.md`. Regras pessoais globais do Eliel (`~/.claude/CLAUDE.md`) valem aqui.
Atualizar versão do Minecraft: seguir `docs/UPDATING.md`; API frágil do MC só dentro de `compat/mc/`.

## Fluxo

- Trabalhar **um item do roadmap por vez**, em branch própria (nome sugerido no item). Nunca commitar em `main`.
- Início de sessão: `git status` + `git fetch origin --prune`; `git pull --ff-only` se limpo.
- PR com squash merge. Ao concluir, marcar ✅ no `docs/ROADMAP.md` no mesmo PR.
- **Roadmap sempre em dia:** ao concluir/mesclar qualquer item (ou mudar versão/decisão relevante), atualizar no mesmo PR `docs/ROADMAP.md` (✅) **e** `docs/roadmap.html` (`s:"done"`), e republicar o artifact "StashLink Roadmap" (https://claude.ai/artifact/DoHRdusXQJgZcMcqhqs1sr) a partir de `docs/roadmap.html`. Nunca deixar o roadmap atrás do código.
- Lógica em `common/`; código de loader só como "cola".
- Toda mudança de item/inventário é **server-side** e validada (distância, permissão). Cliente só pede.
- Explicar decisões técnicas em linguagem simples (Eliel é engenheiro civil aprendendo programação).

## Handoff ao concluir um item

Gerar como último passo: (1) resumo em linguagem simples do que foi feito e por quê, incluindo bugs achados;
(2) prompt autocontido do próximo item: texto exato do roadmap, decisões já fixadas, arquivos/módulos
envolvidos, nome de branch. Último item do plano: avisar que terminou e perguntar o que vem a seguir.

<!-- BEGIN roteador-modelo-esforco v1 -->
## Roteamento de modelo, esforço e linguagem (regra global do Eliel)

Vale neste projeto e em qualquer projeto novo. Fonte: skill `roteador-modelo-esforco` no repositório
`Leoascenci0/skills` (Regra 14 do `CLAUDE.md` global). Convenções específicas deste projeto vencem em conflito.

1. **Diagnosticar antes de agir:** tipo, complexidade (simples/média/complexa) e risco (baixo/médio/alto).
2. **Escolher o menor par modelo + esforço que resolve:** simples → Haiku/low; média → Sonnet/medium;
   complexa ou risco alto (produção, dado real, segurança, LGPD, migração) → Opus/high. `max` só em problema
   realmente difícil. Subir um degrau antes se a falha for cara ou silenciosa.
3. **Executar → verificar → escalar:** verificação LIGHT em risco baixo, FULL em risco alto (testes, build,
   revisão independente, advisors/logs do banco). Falhou: subir um degrau, no máximo 2 tentativas por degrau;
   na 3ª falha parar e explicar a causa.
4. **Delegar volume** (varredura, listagem, diffs grandes) a subagente barato com retorno curto.
5. **Linguagem automática:** pt-BR; registro conforme o público (didático para o Eliel, formal administrativo
   para ofício/memorial, executivo para decisão, leigo para cidadão). Código, commits e PRs seguem a convenção
   do repositório. Sigla por extenso na primeira vez.
6. **Stack:** manter a do projeto; em projeto novo, decidir pelo critério do domínio e registrar o porquê aqui.
7. **Relato curto:** uma linha de roteamento no início de tarefa não trivial; sem relatório do processo.
<!-- END roteador-modelo-esforco v1 -->
