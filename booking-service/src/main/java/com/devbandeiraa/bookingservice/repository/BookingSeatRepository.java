package com.devbandeiraa.bookingservice.repository;

import com.devbandeiraa.bookingservice.domain.BookingSeat;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Acesso ao registro permanente dos lugares de cada reserva. */
public interface BookingSeatRepository extends JpaRepository<BookingSeat, BookingSeat.Id> {

    /** Os lugares de uma reserva, na ordem em que se leem num ingresso. */
    List<BookingSeat> findByIdBookingIdOrderBySectorNameAscRowLabelAscSeatNumberAsc(UUID bookingId);

    /**
     * Os lugares de varias reservas de uma vez.
     *
     * <p>Existe para a listagem: buscar os assentos reserva a reserva faria uma consulta por
     * linha da pagina — o N+1 que uma tela de vinte reservas transformaria em vinte e uma idas
     * ao banco.
     */
    List<BookingSeat> findByIdBookingIdIn(Collection<UUID> bookingIds);
}
