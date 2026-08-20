package com.devbandeiraa.bookingservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

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
public class EventInventory {

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

    /** Exigido pelo JPA. Nao usar diretamente. */
    protected EventInventory() {
    }

    private EventInventory(UUID eventId, int totalTickets, BigDecimal price) {
        this.eventId = eventId;
        this.totalTickets = totalTickets;
        this.price = price;
        this.reservedTickets = 0;
        this.syncedAt = Instant.now();
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
