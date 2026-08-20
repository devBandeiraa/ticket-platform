package com.devbandeiraa.bookingservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Evento a publicar, gravado na mesma transacao do fato que o originou.
 *
 * <p>O payload e guardado ja serializado, e nao como objeto. Um evento e um registro do que
 * aconteceu no momento em que aconteceu: se a classe do evento ganhar um campo amanha, as
 * mensagens gravadas ontem devem continuar significando o que significavam. Serializar na
 * gravacao congela o conteudo; serializar na publicacao o deixaria a merce da versao do codigo
 * que rodar depois.
 */
@Entity
@Table(name = "outbox_messages")
public class OutboxMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "aggregate_type", nullable = false, updatable = false, length = 50)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false, updatable = false)
    private UUID aggregateId;

    /** Vira a routing key na publicacao. */
    @Column(nullable = false, updatable = false, length = 100)
    private String type;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, updatable = false)
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OutboxStatus status;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "last_error", columnDefinition = "text")
    private String lastError;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    /** Exigido pelo JPA. Nao usar diretamente. */
    protected OutboxMessage() {
    }

    private OutboxMessage(String aggregateType, UUID aggregateId, String type, String payload) {
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.type = type;
        this.payload = payload;
        this.status = OutboxStatus.PENDING;
        this.attempts = 0;
    }

    public static OutboxMessage pendente(String aggregateType, UUID aggregateId, String type,
                                         String payload) {
        return new OutboxMessage(aggregateType, aggregateId, type, payload);
    }

    public UUID getId() {
        return id;
    }

    public String getAggregateType() {
        return aggregateType;
    }

    public UUID getAggregateId() {
        return aggregateId;
    }

    public String getType() {
        return type;
    }

    public String getPayload() {
        return payload;
    }

    public OutboxStatus getStatus() {
        return status;
    }

    public int getAttempts() {
        return attempts;
    }

    public String getLastError() {
        return lastError;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    @Override
    public boolean equals(Object outro) {
        if (this == outro) {
            return true;
        }
        if (!(outro instanceof OutboxMessage mensagem)) {
            return false;
        }
        return id != null && id.equals(mensagem.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
