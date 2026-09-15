package com.devbandeiraa.bookingservice.repository;

import com.devbandeiraa.bookingservice.domain.EventSeat;
import com.devbandeiraa.bookingservice.domain.SeatStatus;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Acesso ao estado dos lugares. */
public interface EventSeatRepository extends JpaRepository<EventSeat, UUID> {

    /**
     * Toma os lugares pedidos, se todos ainda estiverem livres.
     *
     * <p><strong>Este metodo e o coracao do projeto.</strong> A condicao que impede vender o
     * mesmo lugar duas vezes — {@code status = FREE} — esta dentro do {@code WHERE} da mesma
     * instrucao que marca a tomada. Nao ha "consultar e depois gravar": o PostgreSQL avalia a
     * condicao e aplica a mudanca numa unica operacao atomica, sob o lock de linha que ele
     * mesmo adquire para atualizar.
     *
     * <p>O chamador compara as linhas afetadas com quantos lugares pediu. Menos significa que
     * alguem levou pelo menos um deles no meio do caminho, e a transacao inteira desfaz — nao
     * existe reserva pela metade.
     *
     * <p>E por isso que o lock do Redis segue sendo otimizacao, e nao a garantia. Se ele falhar
     * de qualquer maneira imaginavel — TTL vencido na secao critica, Redis fora do ar, particao
     * de rede colocando dois nos como donos do mesmo lock — a correcao continua de pe, porque
     * toda reserva passa por aqui.
     *
     * <p>Diferente do contador que existia antes, a invariante agora e estrutural: ha uma linha
     * por lugar, e ela so sai de {@code FREE} uma vez. Vender o mesmo assento duas vezes
     * exigiria duas linhas para o mesmo assento, e a unicidade da chave natural nao permite.
     *
     * @return quantos lugares foram efetivamente tomados
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE EventSeat assento
               SET assento.status = :reservado,
                   assento.bookingId = :bookingId
             WHERE assento.id IN :assentos
               AND assento.eventId = :eventId
               AND assento.status = :livre
            """)
    int reservar(@Param("assentos") Collection<UUID> assentos,
                 @Param("eventId") UUID eventId,
                 @Param("bookingId") UUID bookingId,
                 @Param("livre") SeatStatus livre,
                 @Param("reservado") SeatStatus reservado);

    /**
     * Devolve ao pool os lugares de uma reserva, no cancelamento e na expiracao.
     *
     * <p>Este caminho dispensa o lock do Redis, e a assimetria e intencional: liberar um lugar
     * nunca cria conflito — no maximo dois caminhos tentam liberar o mesmo, e o segundo afeta
     * zero linhas. So a tomada disputa.
     *
     * <p>O {@code status = RESERVED} no {@code WHERE} e o que impede liberar um lugar ja pago:
     * uma reserva confirmada nao volta a ficar livre por um cancelamento tardio.
     *
     * @return quantos lugares voltaram a ficar livres
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE EventSeat assento
               SET assento.status = :livre,
                   assento.bookingId = NULL
             WHERE assento.bookingId = :bookingId
               AND assento.status = :reservado
            """)
    int liberar(@Param("bookingId") UUID bookingId,
                @Param("livre") SeatStatus livre,
                @Param("reservado") SeatStatus reservado);

    /**
     * Marca como vendidos os lugares de uma reserva paga.
     *
     * <p>Definitivo: o job de expiracao so libera o que estiver {@code RESERVED}, entao a partir
     * daqui nenhum caminho automatico devolve o lugar ao pool.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE EventSeat assento
               SET assento.status = :vendido
             WHERE assento.bookingId = :bookingId
               AND assento.status = :reservado
            """)
    int vender(@Param("bookingId") UUID bookingId,
               @Param("reservado") SeatStatus reservado,
               @Param("vendido") SeatStatus vendido);

    /**
     * Escolhe os {@code quantos} lugares livres mais baratos — o "melhor disponivel".
     *
     * <p>{@code SKIP LOCKED} e o ponto desta consulta. Sem ele, duas requisicoes simultaneas
     * leriam a MESMA lista de livres: a primeira travaria as linhas e a segunda ficaria
     * esperando, para descobrir no fim que os lugares acabaram de sair — enfileirando
     * compradores que poderiam ter sido atendidos em paralelo, cada um com lugares diferentes.
     *
     * <p>Com {@code SKIP LOCKED}, a segunda simplesmente pula as linhas travadas e pega as
     * seguintes. Vinte compradores pedindo um lugar cada, num evento com vinte livres, saem
     * todos atendidos sem se bloquearem — o que o contador unico nunca permitiu, porque la
     * todos disputavam a mesma linha.
     *
     * <p>{@code FOR UPDATE} exige transacao aberta: sem ela o lock seria liberado no fim da
     * propria consulta, e a garantia viraria enfeite. Quem chama e {@code ReservaTransacional}.
     *
     * <p>A ordem e por preco e depois por POSICAO — setor, fila, numero —, e nao por id. Com
     * {@code ORDER BY id} sobre um UUID aleatorio, "os mais baratos" viravam lugares sorteados
     * pela casa: alguem que pedisse tres ingressos podia receber A2, A7 e A9, separados, sem
     * razao alguma. Por posicao, a escolha comeca na frente e tende a manter o grupo junto.
     *
     * <p>Nativa, e nao JPQL, porque {@code SKIP LOCKED} nao existe em JPQL.
     */
    @Query(value = """
            SELECT *
              FROM event_seats
             WHERE event_id = :eventId
               AND status = 'FREE'
             ORDER BY price ASC, sector_name ASC, row_label ASC, seat_number ASC
             LIMIT :quantos
               FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<EventSeat> escolherMaisBaratosLivres(@Param("eventId") UUID eventId,
                                              @Param("quantos") int quantos);

    /** Os lugares pedidos, para conferir a que evento pertencem e montar a mensagem de recusa. */
    List<EventSeat> findByIdIn(Collection<UUID> ids);

    /** O mapa do evento, na ordem em que e desenhado. */
    List<EventSeat> findByEventIdOrderBySectorNameAscRowLabelAscSeatNumberAsc(UUID eventId);

    /** Ha assentos deste evento? E a pergunta "ja foi hidratado?". */
    boolean existsByEventId(UUID eventId);

    long countByEventId(UUID eventId);

    long countByEventIdAndStatus(UUID eventId, SeatStatus status);

    /** Total de lugares num dado estado, em todos os eventos hidratados. */
    long countByStatus(SeatStatus status);

    // Os metodos abaixo apenas fixam as constantes de estado das consultas acima, pelo mesmo
    // motivo que BookingRepository faz o mesmo: deixar o estado a cargo de cada chamador
    // abriria espaco para montar uma transicao que a maquina de estados nao preve.

    /** Toma os lugares pedidos. Devolve quantos conseguiu — o chamador compara com o pedido. */
    default int reservar(Collection<UUID> assentos, UUID eventId, UUID bookingId) {
        return reservar(assentos, eventId, bookingId, SeatStatus.FREE, SeatStatus.RESERVED);
    }

    /** Devolve ao pool os lugares reservados por esta reserva. */
    default int liberar(UUID bookingId) {
        return liberar(bookingId, SeatStatus.FREE, SeatStatus.RESERVED);
    }

    /** Marca como vendidos os lugares desta reserva. */
    default int vender(UUID bookingId) {
        return vender(bookingId, SeatStatus.RESERVED, SeatStatus.SOLD);
    }

    /** Quantos lugares deste evento ainda estao livres. */
    default long contarLivres(UUID eventId) {
        return countByEventIdAndStatus(eventId, SeatStatus.FREE);
    }

    /**
     * Lugares livres em TODOS os eventos, para o painel de vendas.
     *
     * <p>Conta apenas eventos ja hidratados neste servico. Um evento publicado que nunca recebeu
     * tentativa de reserva ainda nao tem assentos aqui — a hidratacao acontece na primeira
     * reserva —, entao a capacidade dele nao aparece neste numero. O painel informa isso ao
     * lado, porque um "disponiveis" menor que a soma das casas publicadas parece defeito quando
     * nao e.
     */
    default long contarLivresEmTodosOsEventos() {
        return countByStatus(SeatStatus.FREE);
    }
}
