package com.devbandeiraa.bookingservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.data.domain.Persistable;

/**
 * Espelho local do estoque de um evento.
 *
 * <p>Guarda a capacidade copiada do event-service e o quanto ja foi reservado. Existir aqui, e
 * nao no eventdb, e o que torna a decisao "ainda cabe mais um ingresso?" atomica com a gravacao
 * da reserva — as duas acontecem na mesma transacao, no mesmo banco.
 *
 * <p>Assim como {@code Booking}, nao expoe metodo para reservar. Incrementar
 * {@code reservedTickets} em memoria significaria ler o valor atual e gravar o valor novo, com
 * um intervalo no meio em que outra thread le o mesmo valor atual. E exatamente essa a corrida
 * que o projeto existe para resolver. O incremento e feito por {@code UPDATE} condicional em
 * {@code EventInventoryRepository}, que le e grava em uma unica instrucao atomica.
 */
@Entity
@Table(name = "event_inventory")
public class EventInventory implements Persistable<UUID> {

    /**
     * O id vem do event-service, e nao e gerado aqui: a linha de estoque e o mesmo evento,
     * visto deste lado.
     */
    @Id
    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;

    @Column(name = "total_tickets", nullable = false)
    private int totalTickets;

    @Column(name = "reserved_tickets", nullable = false)
    private int reservedTickets;

    /**
     * Preco unitario copiado do event-service.
     *
     * <p>Mora aqui para que a reserva nao precise de uma chamada REST a cada pedido. Ver a nota
     * na migration {@code V2}.
     */
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(name = "synced_at", nullable = false)
    private Instant syncedAt;

    /**
     * Marca uma instancia recem-construida, que ainda nao existe no banco.
     *
     * <p>Sem isto o Spring Data decide entre inserir e atualizar olhando se o id e nulo. Como o
     * id desta entidade vem pronto do event-service, ele nunca e nulo, e o {@code save()}
     * executaria um <em>merge</em>: o Hibernate consultaria a linha e, achando-a, faria
     * {@code UPDATE}.
     *
     * <p>O efeito sob concorrencia e destrutivo e nada obvio. Varias requisicoes podem tentar
     * hidratar o mesmo evento ao mesmo tempo; a primeira insere e as demais, ao "salvar",
     * atualizariam a linha ja existente <strong>zerando reserved_tickets</strong> — apagando
     * reservas ja contabilizadas e liberando estoque que nao existe mais. Foi assim que o teste
     * de concorrencia chegou a vender 70 ingressos para um evento de 50.
     *
     * <p>Com {@code isNew()} devolvendo verdadeiro, o {@code save()} faz {@code persist()}, e a
     * segunda requisicao esbarra na chave primaria em vez de sobrescrever. A violacao e tratada
     * em {@code EstoqueService}, que apenas le a linha que a vencedora gravou.
     */
    @Transient
    private boolean novo;

    /** Exigido pelo JPA. Nao usar diretamente. */
    protected EventInventory() {
    }

    private EventInventory(UUID eventId, int totalTickets, BigDecimal price) {
        this.eventId = eventId;
        this.totalTickets = totalTickets;
        this.price = price;
        this.reservedTickets = 0;
        this.syncedAt = Instant.now();
        this.novo = true;
    }

    @Override
    public UUID getId() {
        return eventId;
    }

    @Override
    public boolean isNew() {
        return novo;
    }

    /** Depois de gravada ou carregada, a linha existe: qualquer save seguinte e atualizacao. */
    @PostPersist
    @PostLoad
    void marcarComoExistente() {
        this.novo = false;
    }

    /** Primeira hidratacao: capacidade e preco copiados do event-service, nada reservado ainda. */
    public static EventInventory hidratado(UUID eventId, int totalTickets, BigDecimal price) {
        return new EventInventory(eventId, totalTickets, price);
    }

    public int getDisponivel() {
        return totalTickets - reservedTickets;
    }

    public UUID getEventId() {
        return eventId;
    }

    public int getTotalTickets() {
        return totalTickets;
    }

    public int getReservedTickets() {
        return reservedTickets;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public Instant getSyncedAt() {
        return syncedAt;
    }

    @Override
    public boolean equals(Object outro) {
        if (this == outro) {
            return true;
        }
        if (!(outro instanceof EventInventory estoque)) {
            return false;
        }
        return eventId != null && eventId.equals(estoque.eventId);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(eventId);
    }
}
