package com.devbandeiraa.bookingservice.service;

import com.devbandeiraa.bookingservice.domain.Booking;
import com.devbandeiraa.bookingservice.domain.BookingSeat;
import com.devbandeiraa.bookingservice.domain.EventSeat;
import com.devbandeiraa.bookingservice.exception.AssentosIndisponiveisException;
import com.devbandeiraa.bookingservice.exception.EstoqueEsgotadoException;
import com.devbandeiraa.bookingservice.repository.BookingRepository;
import com.devbandeiraa.bookingservice.repository.BookingSeatRepository;
import com.devbandeiraa.bookingservice.repository.EventSeatRepository;
import java.time.Instant;
import java.util.List;
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
 * outra thread entraria na secao critica e leria lugares que ainda nao refletiam a reserva
 * recem-feita — o lock estaria protegendo o intervalo errado.
 *
 * <h2>Dois caminhos de escolha, uma so garantia</h2>
 *
 * <p>Reservar lugares escolhidos e reservar "os N mais baratos livres" diferem apenas em COMO os
 * assentos sao decididos. A tomada em si — o {@code UPDATE} condicional que impede vender o
 * mesmo lugar duas vezes — e a mesma instrucao nos dois casos. Duas formas de escolher, e nao
 * duas logicas de correcao.
 */
@Service
public class ReservaTransacional {

    private final EventSeatRepository assentoRepository;
    private final BookingSeatRepository bookingSeatRepository;
    private final BookingRepository bookingRepository;

    public ReservaTransacional(EventSeatRepository assentoRepository,
                               BookingSeatRepository bookingSeatRepository,
                               BookingRepository bookingRepository) {
        this.assentoRepository = assentoRepository;
        this.bookingSeatRepository = bookingSeatRepository;
        this.bookingRepository = bookingRepository;
    }

    /**
     * Reserva lugares <strong>escolhidos</strong>.
     *
     * <p>Tudo ou nada: se um dos lugares tiver sido levado no meio do caminho, a transacao
     * inteira desfaz. Entregar parte do que se pediu seria pior do que recusar — quem escolheu
     * quatro assentos juntos nao quer dois deles, em lugares separados.
     *
     * @throws AssentosIndisponiveisException se algum lugar ja nao estava livre
     */
    @Transactional
    public Booking registrarEscolhidos(UUID eventId, UUID userId, List<UUID> assentosPedidos,
                                       Instant expiraEm, String chaveDeIdempotencia) {

        List<EventSeat> assentos = assentoRepository.findByIdIn(assentosPedidos);

        // Antes de disputar, descarta o que nao e disputa: id inexistente, ou lugar de outro
        // evento. Sem esta conferencia, pedir um assento de outro evento simplesmente afetaria
        // zero linhas, e o usuario receberia "esse lugar acabou de ser vendido" sobre um lugar
        // que nunca esteve a venda neste evento.
        if (assentos.size() != assentosPedidos.size()
                || assentos.stream().anyMatch(a -> !a.getEventId().equals(eventId))) {
            throw new AssentosIndisponiveisException(eventId, List.of());
        }

        return tomar(eventId, userId, assentos, expiraEm, chaveDeIdempotencia);
    }

    /**
     * Reserva os {@code quantidade} lugares livres mais baratos — o "melhor disponivel".
     *
     * <p>A escolha usa {@code SELECT ... FOR UPDATE SKIP LOCKED}, que deixa compradores
     * simultaneos pegarem lugares <em>diferentes</em> em paralelo em vez de enfileirarem sobre
     * a mesma lista. Ver a nota em {@code EventSeatRepository}.
     *
     * @throws EstoqueEsgotadoException se nao ha lugares livres suficientes
     */
    @Transactional
    public Booking registrarMelhoresDisponiveis(UUID eventId, UUID userId, int quantidade,
                                                Instant expiraEm, String chaveDeIdempotencia) {

        List<EventSeat> assentos = assentoRepository.escolherMaisBaratosLivres(eventId, quantidade);

        if (assentos.size() < quantidade) {
            throw new EstoqueEsgotadoException(eventId, quantidade);
        }

        return tomar(eventId, userId, assentos, expiraEm, chaveDeIdempotencia);
    }

    /**
     * Grava a reserva e toma os lugares, ou nao faz nem uma coisa nem outra.
     *
     * <p>As operacoes compartilham a transacao de proposito. Se a insercao da reserva falhar —
     * por exemplo, quando duas requisicoes com a mesma chave de idempotencia chegam ao mesmo
     * tempo e a segunda esbarra na constraint de unicidade — o rollback desfaz tambem a tomada
     * dos lugares. Em transacoes separadas, os assentos ficariam presos a uma reserva que nunca
     * existiu, e o evento esgotaria com cadeiras vazias.
     *
     * <p>A reserva e gravada <em>antes</em> da tomada porque o assento precisa apontar para ela:
     * e o id da reserva que vai para {@code event_seats.booking_id}. A ordem nao afrouxa nada —
     * as duas escritas estao na mesma transacao, e o rollback alcanca as duas.
     */
    private Booking tomar(UUID eventId, UUID userId, List<EventSeat> assentos, Instant expiraEm,
                          String chaveDeIdempotencia) {

        Booking reserva = bookingRepository.save(Booking.pendente(
                eventId, userId, assentos.size(), EstoqueService.somar(assentos),
                expiraEm, chaveDeIdempotencia));

        List<UUID> ids = assentos.stream().map(EventSeat::getId).toList();

        // Aqui esta a garantia. Menos linhas afetadas do que lugares pedidos significa que a
        // condicao `status = FREE` nao se satisfez para algum deles no instante exato do
        // UPDATE — nao antes dele, que e o que torna a verificacao confiavel sob concorrencia.
        int tomados = assentoRepository.reservar(ids, eventId, reserva.getId());
        if (tomados != assentos.size()) {
            throw new AssentosIndisponiveisException(eventId, etiquetasDe(assentos));
        }

        bookingSeatRepository.saveAll(
                assentos.stream().map(a -> BookingSeat.de(reserva.getId(), a)).toList());

        return reserva;
    }

    /**
     * Etiquetas dos lugares disputados, para a mensagem de recusa.
     *
     * <p>Devolve o conjunto inteiro, e nao apenas os perdidos: descobrir exatamente quais sairam
     * exigiria uma releitura dentro de uma transacao que ja vai desfazer, e o valor lido ali
     * tambem ja estaria velho. A tela recarrega o mapa de qualquer forma — o que ela precisa
     * saber e que a escolha caiu, e sobre quais lugares ela era.
     */
    private static List<String> etiquetasDe(List<EventSeat> assentos) {
        return assentos.stream().map(EventSeat::getEtiqueta).toList();
    }
}
