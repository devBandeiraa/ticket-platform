package com.devbandeiraa.eventservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Evento do catalogo.
 *
 * <p>As transicoes de estado sao metodos da propria entidade, e nao atribuicoes feitas de fora.
 * Assim a regra de "o que pode virar o que" mora em um lugar so: um servico novo que precise
 * publicar um evento nao tem como pular a verificacao, porque nao existe setter para o status.
 */
@Entity
@Table(name = "events")
public class Event {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(columnDefinition = "text")
    private String description;

    @Column(nullable = false, length = 200)
    private String venue;

    @Column(name = "event_date", nullable = false)
    private Instant eventDate;

    @Column(name = "total_tickets", nullable = false)
    private int totalTickets;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EventStatus status;

    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Exigido pelo JPA. Nao usar diretamente. */
    protected Event() {
    }

    private Event(String name, String description, String venue, Instant eventDate,
                  int totalTickets, BigDecimal price, UUID createdBy) {
        this.name = name;
        this.description = description;
        this.venue = venue;
        this.eventDate = eventDate;
        this.totalTickets = totalTickets;
        this.price = price;
        this.createdBy = createdBy;
        this.status = EventStatus.DRAFT;
    }

    /**
     * Cria um evento em rascunho.
     *
     * <p>Nasce sempre como {@code DRAFT}, nunca publicado. Um evento aparece no catalogo por um
     * ato deliberado de quem o administra — publicar por acidente, ao salvar um cadastro pela
     * metade, e o tipo de erro que so se percebe quando alguem ja comprou.
     */
    public static Event rascunho(String name, String description, String venue, Instant eventDate,
                                 int totalTickets, BigDecimal price, UUID createdBy) {
        return new Event(name, description, venue, eventDate, totalTickets, price, createdBy);
    }

    /** Um evento cancelado nao volta atras: seus dados ficam congelados. */
    public boolean podeSerAlterado() {
        return status != EventStatus.CANCELLED;
    }

    public boolean estaPublicado() {
        return status == EventStatus.PUBLISHED;
    }

    public void alterarDados(String name, String description, String venue, Instant eventDate,
                             int totalTickets, BigDecimal price) {
        this.name = name;
        this.description = description;
        this.venue = venue;
        this.eventDate = eventDate;
        this.totalTickets = totalTickets;
        this.price = price;
    }

    public void publicar() {
        this.status = EventStatus.PUBLISHED;
    }

    public void cancelar() {
        this.status = EventStatus.CANCELLED;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getVenue() {
        return venue;
    }

    public Instant getEventDate() {
        return eventDate;
    }

    public int getTotalTickets() {
        return totalTickets;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public EventStatus getStatus() {
        return status;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    /**
     * Identidade por id: duas instancias carregadas em sessoes diferentes representam o mesmo
     * evento. Enquanto o id for nulo (entidade ainda nao persistida), so a identidade da propria
     * referencia vale.
     */
    @Override
    public boolean equals(Object outro) {
        if (this == outro) {
            return true;
        }
        if (!(outro instanceof Event evento)) {
            return false;
        }
        return id != null && id.equals(evento.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
