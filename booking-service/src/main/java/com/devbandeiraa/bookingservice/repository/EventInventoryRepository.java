package com.devbandeiraa.bookingservice.repository;

import com.devbandeiraa.bookingservice.domain.EventInventory;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Acesso ao espelho local do estoque. */
public interface EventInventoryRepository extends JpaRepository<EventInventory, UUID> {

    /**
     * Reserva ingressos, se ainda couberem.
     *
     * <p><strong>Este metodo e o coracao do projeto.</strong> A condicao que impede o
     * overselling — {@code reserved + quantidade <= total} — esta dentro do {@code WHERE} da
     * mesma instrucao que faz o incremento. Nao ha "consultar e depois gravar": o PostgreSQL
     * avalia a condicao e aplica a mudanca em uma unica operacao atomica, sob o lock de linha
     * que ele mesmo adquire para atualizar.
     *
     * <p>Consequencia: duas transacoes concorrentes disputando o ultimo ingresso serializam no
     * banco. A segunda reavalia a condicao sobre o valor ja atualizado pela primeira, ve que
     * nao cabe mais, e atualiza zero linhas.
     *
     * <p>E por isso que o lock do Redis e otimizacao, e nao a garantia. Se o lock falhar de
     * qualquer maneira imaginavel — TTL vencido no meio da secao critica, Redis fora do ar,
     * particao de rede fazendo dois nos se acharem donos do mesmo lock — a correcao continua
     * de pe, porque toda reserva passa por aqui.
     *
     * @return 1 se reservou, 0 se nao havia estoque suficiente
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE EventInventory estoque
               SET estoque.reservedTickets = estoque.reservedTickets + :quantidade
             WHERE estoque.eventId = :eventId
               AND estoque.reservedTickets + :quantidade <= estoque.totalTickets
            """)
    int reservar(@Param("eventId") UUID eventId, @Param("quantidade") int quantidade);

    /**
     * Devolve ingressos ao estoque, no cancelamento e na expiracao.
     *
     * <p>Este caminho dispensa o lock do Redis, e a assimetria e intencional: decrementar
     * {@code reservedTickets} nunca viola a invariante {@code reserved <= total}. So a reserva
     * pode. O piso zero no {@code WHERE} nao protege contra concorrencia, e sim contra bug —
     * uma devolucao dupla da mesma reserva atualizaria zero linhas em vez de deixar o contador
     * negativo.
     *
     * @return 1 se devolveu, 0 se o decremento deixaria o contador negativo
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE EventInventory estoque
               SET estoque.reservedTickets = estoque.reservedTickets - :quantidade
             WHERE estoque.eventId = :eventId
               AND estoque.reservedTickets - :quantidade >= 0
            """)
    int devolver(@Param("eventId") UUID eventId, @Param("quantidade") int quantidade);
}
