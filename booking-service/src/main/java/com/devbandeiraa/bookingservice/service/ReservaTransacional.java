package com.devbandeiraa.bookingservice.service;

import com.devbandeiraa.bookingservice.domain.Booking;
import com.devbandeiraa.bookingservice.exception.EstoqueEsgotadoException;
import com.devbandeiraa.bookingservice.repository.BookingRepository;
import com.devbandeiraa.bookingservice.repository.EventInventoryRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A parte da reserva que precisa ser atomica.
 *
 * <p>Esta classe existe separada de {@code BookingService} por uma razao tecnica concreta: o
 * {@code @Transactional} do Spring atua por proxy, e um metodo transacional chamado de dentro da
 * propria classe nao passaria pelo proxy — a anotacao seria silenciosamente ignorada. Como o
 * {@code BookingService} precisa abrir o lock <em>antes</em> da transacao e fecha-lo
 * <em>depois</em> do commit, a transacao tem de morar em outro bean.
 *
 * <p>A ordem lock-fora-transacao-dentro nao e detalhe. Se o lock fosse liberado antes do commit,
 * outra thread entraria na secao critica e leria um estoque que ainda nao refletia a reserva
 * recem-feita — o lock estaria protegendo o intervalo errado.
 */
@Service
public class ReservaTransacional {

    private final EventInventoryRepository estoqueRepository;
    private final BookingRepository bookingRepository;

    public ReservaTransacional(EventInventoryRepository estoqueRepository,
                               BookingRepository bookingRepository) {
        this.estoqueRepository = estoqueRepository;
        this.bookingRepository = bookingRepository;
    }

    /**
     * Toma o estoque e grava a reserva, ou nao faz nem uma coisa nem outra.
     *
     * <p>As duas operacoes compartilham a transacao de proposito. Se a insercao falhar — por
     * exemplo, quando duas requisicoes com a mesma chave de idempotencia chegam ao mesmo tempo e
     * a segunda esbarra na constraint de unicidade — o rollback desfaz tambem o incremento do
     * estoque. Em transacoes separadas, o ingresso ficaria reservado para uma reserva que nunca
     * existiu, e o evento esgotaria com assentos vazios.
     *
     * @throws EstoqueEsgotadoException se nao ha ingressos suficientes
     */
    @Transactional
    public Booking registrar(UUID eventId, UUID userId, int quantidade, BigDecimal precoUnitario,
                             Instant expiraEm, String chaveDeIdempotencia) {

        // Aqui esta a garantia contra overselling. Zero linhas afetadas significa que a condicao
        // reserved + quantidade <= total nao se satisfez no instante exato do UPDATE — nao antes
        // dele, o que e o que torna a verificacao confiavel sob concorrencia.
        if (estoqueRepository.reservar(eventId, quantidade) == 0) {
            throw new EstoqueEsgotadoException(eventId, quantidade);
        }

        return bookingRepository.save(Booking.pendente(
                eventId, userId, quantidade, precoUnitario, expiraEm, chaveDeIdempotencia));
    }
}
