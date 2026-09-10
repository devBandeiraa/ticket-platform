package com.devbandeiraa.bookingservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/**
 * Um lugar que uma reserva pegou — registro permanente, e nao estado.
 *
 * <p>Separado de {@link EventSeat} porque as duas coisas nao sao a mesma. Cancelada a reserva,
 * o assento volta a ficar livre e perde o ponteiro para ela; mas o usuario ainda precisa ver,
 * em "minhas reservas", quais lugares eram os dele e quanto custaram. Guardar so o estado
 * apagaria a historia junto.
 *
 * <p>Setor, fila, numero e preco sao <strong>copiados</strong>, e nao lidos por join. Sao um
 * retrato do ato da compra: se a casa for reformada ou o preco mudar, a reserva de ontem
 * continua dizendo onde a pessoa sentou e quanto pagou.
 */
@Entity
@Table(name = "booking_seats")
public class BookingSeat {

    @EmbeddedId
    private Id id;

    @Column(name = "sector_name", nullable = false, updatable = false, length = 60)
    private String sectorName;

    @Column(name = "row_label", nullable = false, updatable = false, length = 4)
    private String rowLabel;

    @Column(name = "seat_number", nullable = false, updatable = false)
    private int seatNumber;

    @Column(nullable = false, updatable = false, precision = 10, scale = 2)
    private BigDecimal price;

    /** Exigido pelo JPA. Nao usar diretamente. */
    protected BookingSeat() {
    }

    private BookingSeat(UUID bookingId, EventSeat assento) {
        this.id = new Id(bookingId, assento.getId());
        this.sectorName = assento.getSectorName();
        this.rowLabel = assento.getRowLabel();
        this.seatNumber = assento.getSeatNumber();
        this.price = assento.getPrice();
    }

    public static BookingSeat de(UUID bookingId, EventSeat assento) {
        return new BookingSeat(bookingId, assento);
    }

    /** Como o lugar se chama para quem compra: "Plateia A12". */
    public String getEtiqueta() {
        return "%s %s%d".formatted(sectorName, rowLabel, seatNumber);
    }

    public UUID getBookingId() {
        return id.bookingId();
    }

    public UUID getSeatId() {
        return id.seatId();
    }

    public String getSectorName() {
        return sectorName;
    }

    public String getRowLabel() {
        return rowLabel;
    }

    public int getSeatNumber() {
        return seatNumber;
    }

    public BigDecimal getPrice() {
        return price;
    }

    /**
     * Chave composta.
     *
     * <p>Um assento so pode aparecer uma vez na mesma reserva, e a chave e o que garante isso —
     * uma lista com o mesmo lugar repetido cobraria duas vezes por um assento so.
     */
    @Embeddable
    public record Id(
            @Column(name = "booking_id", nullable = false, updatable = false) UUID bookingId,
            @Column(name = "seat_id", nullable = false, updatable = false) UUID seatId)
            implements Serializable {
    }

    @Override
    public boolean equals(Object outro) {
        if (this == outro) {
            return true;
        }
        if (!(outro instanceof BookingSeat assento)) {
            return false;
        }
        return id != null && id.equals(assento.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
