package com.devbandeiraa.bookingservice.service;

import com.devbandeiraa.bookingservice.client.Autorizacao;
import com.devbandeiraa.bookingservice.domain.Booking;
import com.devbandeiraa.bookingservice.dto.response.BookingResponse;
import com.devbandeiraa.bookingservice.exception.ReservaNaoEncontradaException;
import com.devbandeiraa.bookingservice.messaging.BookingConfirmedEvent;
import com.devbandeiraa.bookingservice.messaging.OutboxRegistrar;
import com.devbandeiraa.bookingservice.repository.BookingRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A parte da confirmacao que precisa ser atomica.
 *
 * <p>Separada de {@code BookingService} pela mesma razao tecnica de {@link ReservaTransacional}: o
 * {@code @Transactional} atua por proxy, e um metodo transacional chamado de dentro da propria
 * classe seria silenciosamente ignorado.
 *
 * <p>Mas ha uma segunda razao, especifica desta fase e mais importante: a cobranca precisa
 * acontecer <em>fora</em> desta transacao. Com o {@code @Transactional} envolvendo tambem a
 * chamada ao provedor, uma conexao do pool ficaria aberta durante as quatro tentativas do retry —
 * ate alguns segundos, por reserva. Sob a carga que esta plataforma existe para suportar, o pool
 * de conexoes esgotaria e o banco pararia de atender <b>todo mundo</b> por causa da lentidao de um
 * terceiro. E a mesma licao que a hidratacao do estoque ja aplicava ao ficar fora do lock.
 */
@Service
public class ConfirmacaoTransacional {

    private static final Logger log = LoggerFactory.getLogger(ConfirmacaoTransacional.class);

    private final BookingRepository bookingRepository;
    private final OutboxRegistrar outboxRegistrar;

    public ConfirmacaoTransacional(BookingRepository bookingRepository,
                                   OutboxRegistrar outboxRegistrar) {
        this.bookingRepository = bookingRepository;
        this.outboxRegistrar = outboxRegistrar;
    }

    /**
     * Marca a reserva como paga e registra o evento de confirmacao, ou nao faz nem uma coisa nem
     * outra.
     *
     * <p>O {@code UPDATE} condicional continua sendo quem decide: ele exige status pendente
     * <em>e</em> prazo ainda valido. Se o job de expiracao chegou primeiro, zero linhas sao
     * afetadas — e como a cobranca ja passou, quem chamou precisa estornar. Dai o
     * {@link Optional} vazio em vez de uma excecao: o vazio carrega a informacao de que ha uma
     * compensacao a fazer, enquanto uma excecao convidaria a apenas propagar o erro e esquecer
     * o dinheiro.
     *
     * @return a reserva confirmada, ou vazio se a transicao nao pode mais acontecer
     */
    @Transactional
    public Optional<BookingResponse> confirmar(UUID id, Autorizacao autorizacao) {
        Instant agora = Instant.now().truncatedTo(ChronoUnit.MICROS);

        if (bookingRepository.confirmar(id, agora, autorizacao.authorizationCode()) == 0) {
            return Optional.empty();
        }

        Booking confirmada = bookingRepository.findById(id)
                .orElseThrow(() -> new ReservaNaoEncontradaException(id));

        // Na MESMA transacao que confirmou a reserva. Este e o ponto inteiro da outbox: publicar
        // no RabbitMQ aqui deixaria uma notificacao de pagamento sem pagamento caso o commit
        // falhasse; publicar depois do commit deixaria a reserva paga sem que ninguem soubesse,
        // caso a publicacao falhasse. Gravado junto, os dois fatos existem ou nao existem.
        outboxRegistrar.registrarConfirmacao(BookingConfirmedEvent.de(confirmada));

        log.info("reserva paga: id={} comprovante={}", id, autorizacao.authorizationCode());
        return Optional.of(BookingResponse.de(confirmada));
    }
}
