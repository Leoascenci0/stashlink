package io.github.leoascenci0.stashlink.label;

/**
 * Guarda o {@link Label} de uma block entity de container (baú, barril, shulker...). Um mixin
 * ({@code BaseContainerBlockEntityMixin}) faz toda block entity de container implementar isto; o rótulo vai ao
 * disco junto com o bloco. O baú do End não é container "de bloco" (o conteúdo é do jogador) e usa
 * {@link EnderLabels}.
 */
public interface LabelHolder {
    Label stashlink$label();

    void stashlink$setLabel(Label label);
}
