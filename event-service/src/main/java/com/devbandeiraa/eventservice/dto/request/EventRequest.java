package com.devbandeiraa.eventservice.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

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
 * <p>Tambem nao ha mais {@code totalTickets} nem {@code price}: os dois passaram a ser derivados
 * dos setores — capacidade e a soma das dimensoes, preco e o menor entre eles. Aceita-los aqui
 * permitiria que discordassem do layout, e o catalogo anunciaria uma casa que nao existe.
 *
 * @param eventDate exigido no futuro: cadastrar um evento que ja aconteceu so pode ser engano
 *                  de digitacao, e aceita-lo colocaria no catalogo algo impossivel de vender
 * @param sectors   a planta da casa; so pode ser alterada enquanto o evento e rascunho
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

        @NotEmpty(message = "O evento precisa de ao menos um setor")
        @Size(max = 20, message = "Um evento pode ter no maximo 20 setores")
        @Valid
        List<SectorRequest> sectors,

        @Size(max = 500, message = "A URL da capa deve ter no maximo 500 caracteres")
        @Pattern(regexp = "^https?://.+", message = "A URL da capa deve comecar com http:// ou https://")
        String imageUrl) {

    /** Apara espacos em volta antes da validacao, pelo mesmo motivo do cadastro de usuario. */
    public EventRequest {
        name = aparar(name);
        description = aparar(description);
        venue = aparar(venue);
        // Campo opcional que o formulario envia como texto vazio quando o admin o limpa.
        // Gravar "" faria o frontend tentar carregar uma imagem de endereco vazio, em vez de
        // cair no fundo derivado do nome que ele desenha quando nao ha capa. Vazio e ausencia,
        // e a coluna ja sabe representar ausencia.
        imageUrl = vazioComoNulo(aparar(imageUrl));
    }

    private static String aparar(String valor) {
        return valor == null ? null : valor.trim();
    }

    private static String vazioComoNulo(String valor) {
        return valor == null || valor.isEmpty() ? null : valor;
    }
}
