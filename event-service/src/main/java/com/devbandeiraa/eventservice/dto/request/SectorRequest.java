package com.devbandeiraa.eventservice.dto.request;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * Um setor da casa, como o admin o descreve.
 *
 * <p>Nao ha campo de ordem: ela vem da posicao na lista. Pedir um numero de ordem abriria a
 * chance de dois setores declararem o mesmo, e alguem teria de decidir o desempate.
 *
 * <p>Tambem nao ha campo de capacidade. Ela e {@code rowsCount * seatsPerRow}, e aceita-la de
 * fora permitiria que discordasse das dimensoes — o mapa desenharia um numero de lugares e o
 * catalogo anunciaria outro.
 *
 * @param rowsCount    filas do setor
 * @param seatsPerRow  lugares por fila
 */
public record SectorRequest(

        @NotBlank(message = "O nome do setor e obrigatorio")
        @Size(max = 60, message = "O nome do setor deve ter no maximo 60 caracteres")
        String name,

        @NotNull(message = "O preco do setor e obrigatorio")
        @PositiveOrZero(message = "O preco nao pode ser negativo")
        @Digits(integer = 8, fraction = 2, message = "O preco deve ter no maximo 8 inteiros e 2 decimais")
        BigDecimal price,

        @NotNull(message = "A quantidade de filas e obrigatoria")
        @Positive(message = "O setor precisa de ao menos uma fila")
        // Teto para que um erro de digitacao nao vire uma casa de milhoes de lugares. O mapa e
        // desenhado no navegador, e uma grade dessas travaria a aba de quem abrir o evento.
        @Max(value = 200, message = "Um setor pode ter no maximo 200 filas")
        Integer rowsCount,

        @NotNull(message = "A quantidade de lugares por fila e obrigatoria")
        @Positive(message = "A fila precisa de ao menos um lugar")
        @Max(value = 100, message = "Uma fila pode ter no maximo 100 lugares")
        Integer seatsPerRow) {

    public SectorRequest {
        name = name == null ? null : name.trim();
    }
}
