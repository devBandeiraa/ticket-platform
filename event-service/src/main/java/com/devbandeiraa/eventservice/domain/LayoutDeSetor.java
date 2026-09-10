package com.devbandeiraa.eventservice.domain;

import java.math.BigDecimal;

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
 */
public record LayoutDeSetor(String name, BigDecimal price, int rowsCount, int seatsPerRow) {
}
