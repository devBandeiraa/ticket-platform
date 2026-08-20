package com.devbandeiraa.eventservice.dto.request;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Dados de criacao e de alteracao de um evento.
 *
 * <p>O mesmo record serve aos dois casos porque os campos editaveis sao exatamente os mesmos.
 * Separar em dois DTOs identicos so criaria duas listas de validacao para manter em sincronia,
 * e uma delas ficaria para tras.
 *
 * <p>Nao ha campo de status: publicar e cancelar sao acoes com endpoint proprio, nao efeito
 * colateral de uma edicao. Se o status viesse aqui, uma edicao de preco poderia publicar o
 * evento sem que ninguem tivesse pedido.
 *
 * @param eventDate exigido no futuro: cadastrar um evento que ja aconteceu so pode ser engano
 *                  de digitacao, e aceita-lo colocaria no catalogo algo impossivel de vender
 */
public record EventRequest(

        @NotBlank(message = "O nome e obrigatorio")
        @Size(max = 150, message = "O nome deve ter no maximo 150 caracteres")
        String name,

        @Size(max = 2000, message = "A descricao deve ter no maximo 2000 caracteres")
        String description,

        @NotBlank(message = "O local e obrigatorio")
        @Size(max = 200, message = "O local deve ter no maximo 200 caracteres")
        String venue,

        @NotNull(message = "A data do evento e obrigatoria")
        @Future(message = "A data do evento deve estar no futuro")
        Instant eventDate,

        @NotNull(message = "A quantidade de ingressos e obrigatoria")
        @Positive(message = "A quantidade de ingressos deve ser maior que zero")
        Integer totalTickets,

        @NotNull(message = "O preco e obrigatorio")
        @PositiveOrZero(message = "O preco nao pode ser negativo")
        @Digits(integer = 8, fraction = 2, message = "O preco deve ter no maximo 8 inteiros e 2 decimais")
        BigDecimal price) {

    /** Apara espacos em volta antes da validacao, pelo mesmo motivo do cadastro de usuario. */
    public EventRequest {
        name = aparar(name);
        description = aparar(description);
        venue = aparar(venue);
    }

    private static String aparar(String valor) {
        return valor == null ? null : valor.trim();
    }
}
