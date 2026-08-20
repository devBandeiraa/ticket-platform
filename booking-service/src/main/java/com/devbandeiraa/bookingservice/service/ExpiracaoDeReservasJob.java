package com.devbandeiraa.bookingservice.service;

import com.devbandeiraa.bookingservice.domain.Booking;
import com.devbandeiraa.bookingservice.repository.BookingRepository;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Devolve ao estoque as reservas cujo prazo de pagamento venceu.
 *
 * <p>Sem esta varredura, um carrinho abandonado seguraria ingressos para sempre e o evento
 * esgotaria com assentos vazios. Note que a expiracao ja e respeitada no pagamento, pelo
 * {@code expires_at > now()} da transicao condicional: o job nao existe para <em>impedir</em> o
 * pagamento de uma reserva vencida, e sim para <em>liberar o estoque</em> que ela retinha.
 *
 * <p><strong>Com varias replicas do servico, todas executam esta varredura.</strong> Isso nao
 * afeta a correcao: cada expiracao passa por um {@code UPDATE} condicional, entao apenas uma
 * replica consegue a transicao de cada reserva e apenas ela devolve o estoque. As demais leem
 * zero linhas afetadas e nao fazem nada. O desperdicio e de consultas repetidas, e a solucao
 * conhecida — ShedLock — fica anotada como melhoria, nao como correcao pendente.
 */
@Component
public class ExpiracaoDeReservasJob {

    private static final Logger log = LoggerFactory.getLogger(ExpiracaoDeReservasJob.class);

    private final BookingRepository bookingRepository;
    private final BookingService bookingService;
    private final ExpiracaoProperties propriedades;

    public ExpiracaoDeReservasJob(BookingRepository bookingRepository,
                                  BookingService bookingService,
                                  ExpiracaoProperties propriedades) {
        this.bookingRepository = bookingRepository;
        this.bookingService = bookingService;
        this.propriedades = propriedades;
    }

    /**
     * Varre um lote de reservas vencidas.
     *
     * <p>{@code fixedDelay}, e nao {@code fixedRate}: o intervalo conta a partir do <em>fim</em>
     * da execucao anterior. Com {@code fixedRate}, uma varredura que demorasse mais que o
     * intervalo veria a proxima comecar antes de a anterior terminar, e as duas competiriam pelas
     * mesmas reservas.
     *
     * <p>A busca acontece fora de transacao e cada expiracao abre a sua. Em transacao unica para
     * o lote inteiro, uma reserva problematica desfaria o trabalho de todas as outras a cada
     * rodada — e, como a varredura e ordenada por vencimento, ela voltaria a bloquear a passada
     * seguinte, indefinidamente.
     */
    @Scheduled(
            fixedDelayString = "${booking.expiracao.intervalo:60s}",
            initialDelayString = "${booking.expiracao.atraso-inicial:30s}")
    public void expirarVencidas() {
        Instant agora = Instant.now();
        List<Booking> vencidas =
                bookingRepository.proximoLoteDeVencidas(agora, propriedades.tamanhoDoLote());

        if (vencidas.isEmpty()) {
            return;
        }

        int expiradas = 0;
        for (Booking reserva : vencidas) {
            if (bookingService.expirar(reserva)) {
                expiradas++;
            }
        }

        log.info("varredura de expiracao: {} vencida(s) encontrada(s), {} expirada(s) por esta "
                + "instancia", vencidas.size(), expiradas);
    }
}
