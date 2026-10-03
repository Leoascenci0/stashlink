package io.github.leoascenci0.stashlink.quickstack;

/**
 * "Este container recebe itens com a tecla N?" Um mixin faz toda block entity de container (baú, barril, shulker...)
 * implementar isto; o valor vai para o disco junto com o bloco ({@code BaseContainerBlockEntityMixin}). Padrão: recebe.
 */
public interface ReceiveHolder {
    boolean stashlink$receivesQuickStack();

    void stashlink$setReceivesQuickStack(boolean receives);
}
