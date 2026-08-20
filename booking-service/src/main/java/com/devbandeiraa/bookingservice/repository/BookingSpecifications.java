package com.devbandeiraa.bookingservice.repository;

import com.devbandeiraa.bookingservice.domain.Booking;
import com.devbandeiraa.bookingservice.domain.BookingStatus;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

/**
 * Filtros combinaveis da listagem administrativa.
 *
 * <p>Mesma solucao adotada no event-service, e pelo mesmo motivo: uma consulta JPQL com
 * {@code (:parametro IS NULL OR ...)} falha contra o PostgreSQL quando o parametro so aparece
 * dentro do teste de nulidade — o banco nao consegue inferir o tipo do bind. Com Specifications,
 * o filtro ausente simplesmente nao entra na consulta.
 */
public final class BookingSpecifications {

    private BookingSpecifications() {
    }

    public static Specification<Booking> doEvento(UUID eventId) {
        return (raiz, consulta, construtor) -> construtor.equal(raiz.get("eventId"), eventId);
    }

    public static Specification<Booking> comStatus(BookingStatus status) {
        return (raiz, consulta, construtor) -> construtor.equal(raiz.get("status"), status);
    }
}
