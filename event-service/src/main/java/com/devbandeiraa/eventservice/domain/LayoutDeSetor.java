package com.devbandeiraa.eventservice.domain;

import java.math.BigDecimal;
import java.util.List;

/**
 * Descricao de um setor, sem ainda ser um {@link Sector}.
 *
 * <p>Existe para que o {@link Event} construa os proprios setores. Um {@code Sector} nasce
 * apontando para o evento a que pertence, e deixar o servico monta-lo exigiria entregar a ele uma
 * referencia ao evento ainda meio construido — com a chance de sair de la um setor apontando para
 * o evento errado, ou nenhum. Assim quem cria o filho e o pai, e a relacao ja nasce coerente.
 *
 * @param rowsCount    quantidade de filas
 * @param seatsPerRow  lugares por fila; junto com as filas, define a capacidade do setor
 * @param description  texto corrido opcional; nulo significa que o nome e o preco se explicam
 * @param benefits     rotulos curtos, na ordem em que a tela os exibe; nunca nulo, possivelmente
 *                     vazio, para quem le nao precisar testar nulidade antes de percorrer
 * @param tier         faixa comercial; nunca nula, {@link SectorTier#STANDARD} por omissao
 */
public record LayoutDeSetor(String name, BigDecimal price, int rowsCount, int seatsPerRow,
                            String description, List<String> benefits, SectorTier tier) {

    public LayoutDeSetor {
        // Normaliza na entrada para o resto do dominio nao carregar dois casos que significam a
        // mesma coisa. Copia imutavel porque o record vaza a lista pelo acessor, e uma lista
        // mutavel vazada e um setor que muda sem passar por `aplicarLayout`.
        benefits = benefits == null ? List.of() : List.copyOf(benefits);
        tier = tier == null ? SectorTier.STANDARD : tier;
    }
}
