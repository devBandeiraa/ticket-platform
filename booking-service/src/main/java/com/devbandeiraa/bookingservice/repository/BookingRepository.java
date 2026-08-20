package com.devbandeiraa.bookingservice.repository;

import com.devbandeiraa.bookingservice.domain.Booking;
import com.devbandeiraa.bookingservice.domain.BookingStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Acesso as reservas.
 *
 * <p>As transicoes de estado ficam aqui, e nao na entidade, porque todas sao disputadas: pagar
 * concorre com expirar, cancelar concorre com ambas. Cada uma e um {@code UPDATE} que carrega o
 * estado esperado no {@code WHERE} e devolve a contagem de linhas afetadas — zero significa
 * "outro chegou primeiro", uma resposta determinística em vez de uma sobrescrita silenciosa.
 */
public interface BookingRepository extends JpaRepository<Booking, UUID>, JpaSpecificationExecutor<Booking> {

    /** Consulta de idempotencia: a chave so identifica reserva dentro do proprio usuario. */
    Optional<Booking> findByUserIdAndIdempotencyKey(UUID userId, String idempotencyKey);

    Page<Booking> findByUserId(UUID userId, Pageable pageable);

    /**
     * Confirma o pagamento.
     *
     * <p>O {@code expiresAt > :agora} dentro do {@code WHERE} e o que resolve a corrida entre o
     * usuario clicando em "pagar" e o job de expiracao varrendo a tabela no mesmo instante. Sem
     * ele, a confirmacao poderia sobrescrever uma reserva ja expirada — e o estoque teria sido
     * devolvido, deixando uma reserva paga sem lastro.
     *
     * @return 1 se confirmou, 0 se a reserva ja nao estava pendente ou o prazo venceu
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE Booking reserva
               SET reserva.status = :confirmada,
                   reserva.paidAt = :agora
             WHERE reserva.id = :id
               AND reserva.status = :pendente
               AND reserva.expiresAt > :agora
            """)
    int confirmarSePendente(@Param("id") UUID id,
                            @Param("agora") Instant agora,
                            @Param("pendente") BookingStatus pendente,
                            @Param("confirmada") BookingStatus confirmada);

    /**
     * Sai de PENDING para um estado final que devolve estoque — CANCELLED ou EXPIRED.
     *
     * <p>Os dois casos compartilham a instrucao porque a mecanica e identica: so vale se a
     * reserva ainda estiver pendente. Quem obtiver a linha e quem devolve o estoque, e como
     * apenas um obtem, o estoque volta exatamente uma vez.
     *
     * @return 1 se a transicao ocorreu, 0 se a reserva ja havia saido de PENDING
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE Booking reserva
               SET reserva.status = :destino
             WHERE reserva.id = :id
               AND reserva.status = :pendente
            """)
    int encerrarSePendente(@Param("id") UUID id,
                           @Param("pendente") BookingStatus pendente,
                           @Param("destino") BookingStatus destino);

    /**
     * Reservas com prazo vencido, para o job de expiracao.
     *
     * <p>Devolve um lote limitado em vez da lista inteira: se o job ficar horas fora do ar, a
     * primeira execucao ao voltar nao deve tentar carregar e processar tudo de uma vez. O que
     * sobrar sai na proxima passada, um minuto depois.
     */
    @Query("""
            SELECT reserva
              FROM Booking reserva
             WHERE reserva.status = :pendente
               AND reserva.expiresAt <= :agora
             ORDER BY reserva.expiresAt ASC
            """)
    List<Booking> buscarVencidas(@Param("pendente") BookingStatus pendente,
                                 @Param("agora") Instant agora,
                                 Limit limite);

    // Os metodos abaixo apenas fixam as constantes de estado das consultas acima. Passar
    // BookingStatus como parametro evita repetir o nome totalmente qualificado do enum dentro
    // do JPQL; deixar isso a cargo de cada chamador e que seria ruim, porque abriria espaco
    // para alguem montar uma transicao que a maquina de estados nao preve.

    /** Confirma o pagamento de uma reserva pendente e dentro do prazo. */
    default int confirmar(UUID id, Instant agora) {
        return confirmarSePendente(id, agora, BookingStatus.PENDING, BookingStatus.CONFIRMED);
    }

    /** Cancelamento pedido pelo usuario. */
    default int cancelar(UUID id) {
        return encerrarSePendente(id, BookingStatus.PENDING, BookingStatus.CANCELLED);
    }

    /** Expiracao por prazo vencido, aplicada pelo job. */
    default int expirar(UUID id) {
        return encerrarSePendente(id, BookingStatus.PENDING, BookingStatus.EXPIRED);
    }

    /** Proximo lote de reservas vencidas, da mais antiga para a mais recente. */
    default List<Booking> proximoLoteDeVencidas(Instant agora, int tamanhoDoLote) {
        return buscarVencidas(BookingStatus.PENDING, agora, Limit.of(tamanhoDoLote));
    }
}
