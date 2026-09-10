package com.devbandeiraa.bookingservice.domain;

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

import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Reserva de ingressos.
 *
 * <p>Diferente de {@code Event}, esta entidade nao expoe metodos de transicao de estado, e a
 * ausencia e deliberada. Confirmar, cancelar e expirar sao operacoes disputadas: o usuario pode
 * clicar em "pagar" no mesmo instante em que o job de expiracao varre a tabela. Uma transicao
 * feita em memoria — carregar, checar o status, atribuir o novo, deixar o flush gravar — abre
 * uma janela entre a checagem e a gravacao na qual as duas operacoes se acham validas, e as
 * duas gravam.
 *
 * <p>Por isso as transicoes vivem no repositorio, como {@code UPDATE} condicional que traz o
 * estado esperado no proprio {@code WHERE}. O banco decide quem chegou primeiro, e quem chegou
 * depois recebe zero linhas afetadas — uma resposta, e nao uma corrida silenciosa. Ver
 * {@code BookingRepository}.
 */
@Entity
@Table(name = "bookings")
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(nullable = false, updatable = false)
    private int quantity;

    @Column(name = "total_price", nullable = false, updatable = false, precision = 10, scale = 2)
    private BigDecimal totalPrice;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BookingStatus status;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "paid_at")
    private Instant paidAt;

    /**
     * Comprovante do provedor de pagamento. Nulo enquanto a reserva nao foi paga, e nas reservas
     * anteriores a existencia do provedor.
     */
    @Column(name = "payment_authorization", length = 40)
    private String paymentAuthorization;

    @Column(name = "idempotency_key", nullable = false, updatable = false, length = 100)
    private String idempotencyKey;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Exigido pelo JPA. Nao usar diretamente. */
    protected Booking() {
    }

    private Booking(UUID eventId, UUID userId, int quantity, BigDecimal totalPrice,
                    Instant expiresAt, String idempotencyKey) {
        this.eventId = eventId;
        this.userId = userId;
        this.quantity = quantity;
        this.totalPrice = totalPrice;
        // Truncado para microssegundos, que e a precisao de TIMESTAMPTZ no PostgreSQL. Sem isso
        // o objeto em memoria carrega nanossegundos que o banco arredonda ao gravar, e a reserva
        // devolvida no 201 sai diferente da mesma reserva lida logo depois — mesmo id, mesmo
        // registro, campo diferente. Um cliente que guardasse esse instante para comparar com
        // uma consulta posterior veria uma divergencia que nao existe.
        this.expiresAt = expiresAt.truncatedTo(ChronoUnit.MICROS);
        this.idempotencyKey = idempotencyKey;
        this.status = BookingStatus.PENDING;
    }

    /**
     * Cria a reserva ja segurando os lugares, com prazo para pagar.
     *
     * <p>Nao ha mais preco unitario. Uma reserva de Plateia a 180 e Galeria a 70 nao tem um: a
     * media seria 125, valor que nenhum ingresso custou. O total vem somado dos lugares
     * efetivamente tomados, e o preco de cada um fica em {@code booking_seats} — que e mais
     * informacao do que o campo removido dava, e nunca uma media inventada.
     *
     * <p>O total e calculado pelo servidor a partir dos assentos, e nunca recebido do cliente:
     * um valor vindo de fora poderia discordar da soma dos lugares, por erro de arredondamento
     * ou por adulteracao, e o banco aceitaria a incoerencia.
     */
    public static Booking pendente(UUID eventId, UUID userId, int quantity, BigDecimal totalPrice,
                                   Instant expiresAt, String idempotencyKey) {
        return new Booking(eventId, userId, quantity, totalPrice, expiresAt, idempotencyKey);
    }

    public boolean estaPendente() {
        return status == BookingStatus.PENDING;
    }

    /** Usado para distinguir 403 (reserva de outro) de 404 (reserva inexistente). */
    public boolean pertenceA(UUID usuarioId) {
        return userId.equals(usuarioId);
    }

    public UUID getId() {
        return id;
    }

    public UUID getEventId() {
        return eventId;
    }

    public UUID getUserId() {
        return userId;
    }

    public int getQuantity() {
        return quantity;
    }

    public BigDecimal getTotalPrice() {
        return totalPrice;
    }

    public BookingStatus getStatus() {
        return status;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getPaidAt() {
        return paidAt;
    }

    public String getPaymentAuthorization() {
        return paymentAuthorization;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    /** Identidade por id, como nas demais entidades do projeto. */
    @Override
    public boolean equals(Object outro) {
        if (this == outro) {
            return true;
        }
        if (!(outro instanceof Booking reserva)) {
            return false;
        }
        return id != null && id.equals(reserva.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
