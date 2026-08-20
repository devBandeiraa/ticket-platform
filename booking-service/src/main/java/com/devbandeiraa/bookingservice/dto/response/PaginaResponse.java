package com.devbandeiraa.bookingservice.dto.response;

import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;

/**
 * Envelope de paginacao da API.
 *
 * <p>Existe para nao serializar o {@code Page} do Spring Data diretamente. A estrutura JSON dele
 * e detalhe interno da biblioteca — ja mudou entre versoes, e o proprio Spring Boot alerta que
 * serializa-lo nao e suportado.
 *
 * <p>Identico ao do event-service, e por ora duplicado de proposito: esta e a segunda copia, e a
 * regra de tres so se cumpre na terceira. Se o notification-service ou o gateway vierem a
 * precisar do mesmo envelope, ele vai para o modulo compartilhado.
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
