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
import java.util.Objects;
import java.util.UUID;

/**
 * Um lugar da casa, com o estado que este servico conhece.
 *
 * <p>A planta vem do event-service — setor, filas e lugares por fila. O que mora aqui e o
 * <strong>estado</strong> de cada lugar, e e por isso que a linha precisa existir deste lado:
 * a pergunta "este assento ainda esta livre?" tem de ser atomica com a gravacao da reserva, e
 * so e atomica se as duas acontecerem no mesmo banco. E o mesmo argumento que justificava o
 * contador local antes de existirem assentos.
 *
 * <p>Assim como {@code Booking}, esta entidade <strong>nao</strong> expoe metodo para reservar.
 * Marcar o assento em memoria significaria ler o status atual e gravar o novo, com um intervalo
 * no meio em que outra thread le o mesmo "livre". E exatamente essa a corrida que o projeto
 * existe para resolver. A tomada e um {@code UPDATE} condicional em {@code EventSeatRepository},
 * que le e grava numa unica instrucao.
 *
 * <p>A identidade e a chave natural: evento, setor, fila e numero. O {@code id} e conveniencia
 * local, gerado aqui — o event-service nao o conhece e nao precisa conhecer.
 */
@Entity
@Table(name = "event_seats")
public class EventSeat {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;

    @Column(name = "sector_name", nullable = false, updatable = false, length = 60)
    private String sectorName;

    @Column(name = "row_label", nullable = false, updatable = false, length = 4)
    private String rowLabel;

    @Column(name = "seat_number", nullable = false, updatable = false)
    private int seatNumber;

    @Column(nullable = false, updatable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SeatStatus status;

    /** Quem segura o lugar agora. Nulo quando livre. */
    @Column(name = "booking_id")
    private UUID bookingId;

    /** Exigido pelo JPA. Nao usar diretamente. */
    protected EventSeat() {
    }

    private EventSeat(UUID eventId, String sectorName, String rowLabel, int seatNumber,
                      BigDecimal price) {
        this.eventId = eventId;
        this.sectorName = sectorName;
        this.rowLabel = rowLabel;
        this.seatNumber = seatNumber;
        this.price = price;
        this.status = SeatStatus.FREE;
    }

    /** Lugar recem-hidratado a partir da planta do event-service: nasce livre. */
    public static EventSeat livre(UUID eventId, String sectorName, String rowLabel,
                                  int seatNumber, BigDecimal price) {
        return new EventSeat(eventId, sectorName, rowLabel, seatNumber, price);
    }

    /** Como o lugar se chama para quem compra: "Plateia A12". */
    public String getEtiqueta() {
        return "%s %s%d".formatted(sectorName, rowLabel, seatNumber);
    }

    public UUID getId() {
        return id;
    }

    public UUID getEventId() {
        return eventId;
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

    public SeatStatus getStatus() {
        return status;
    }

    public UUID getBookingId() {
        return bookingId;
    }

    @Override
    public boolean equals(Object outro) {
        if (this == outro) {
            return true;
        }
        if (!(outro instanceof EventSeat assento)) {
            return false;
        }
        return id != null && id.equals(assento.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
