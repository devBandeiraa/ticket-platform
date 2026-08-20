package com.devbandeiraa.eventservice.dto.response;

import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;

/**
 * Envelope de paginacao da API.
 *
 * <p>Existe para nao serializar o {@code Page} do Spring Data diretamente. A estrutura JSON dele
 * e detalhe interno da biblioteca — ja mudou entre versoes, e o proprio Spring Boot alerta que
 * serializa-lo nao e suportado. Um record proprio deixa o contrato da API estavel e legivel,
 * independente da versao do framework por baixo.
 */
public record PaginaResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last) {

    public static <E, T> PaginaResponse<T> de(Page<E> pagina, Function<E, T> conversor) {
        return new PaginaResponse<>(
                pagina.getContent().stream().map(conversor).toList(),
                pagina.getNumber(),
                pagina.getSize(),
                pagina.getTotalElements(),
                pagina.getTotalPages(),
                pagina.isFirst(),
                pagina.isLast());
    }
}
