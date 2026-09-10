package com.devbandeiraa.eventservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/**
 * Um setor da casa: Plateia, Balcao, Camarote.
 *
 * <p>Guarda as dimensoes, e nao os lugares. Um setor e regular — {@code rowsCount} filas de
 * {@code seatsPerRow} lugares —, entao a lista de assentos e o resultado de uma multiplicacao.
 * Persisti-la seria guardar 1500 linhas para representar dois inteiros, e guarda-las tambem no
 * booking-service, que precisa de uma linha por assento de qualquer forma por ser quem conhece o
 * ESTADO de cada lugar.
 *
 * <p>A identidade de um assento e, por isso, a chave natural: evento, setor, fila e numero. Ver a
 * nota na migration {@code V3}, inclusive para a limitacao aceita — layout irregular e assento
 * interditado exigiriam a tabela que aqui nao existe.
 */
@Entity
@Table(name = "sectors")
public class Sector {

    /** Fila e nomeada por letra: A, B, ... Z, e entao AA, AB. Ver {@link #rotuloDaFila(int)}. */
    private static final int LETRAS = 26;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * Dono do setor.
     *
     * <p>{@code LAZY} porque a navegacao util e sempre a inversa — parte-se do evento para os
     * setores. Carregar o evento ao ler um setor traria de volta, por tabela, exatamente o objeto
     * de onde se veio.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false, updatable = false)
    private Event event;

    @Column(nullable = false, length = 60)
    private String name;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(name = "rows_count", nullable = false)
    private int rowsCount;

    @Column(name = "seats_per_row", nullable = false)
    private int seatsPerRow;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    /** Exigido pelo JPA. Nao usar diretamente. */
    protected Sector() {
    }

    private Sector(Event event, String name, BigDecimal price, int rowsCount, int seatsPerRow,
                   int displayOrder) {
        this.event = event;
        this.name = name;
        this.price = price;
        this.rowsCount = rowsCount;
        this.seatsPerRow = seatsPerRow;
        this.displayOrder = displayOrder;
    }

    public static Sector de(Event event, String name, BigDecimal price, int rowsCount,
                            int seatsPerRow, int displayOrder) {
        return new Sector(event, name, price, rowsCount, seatsPerRow, displayOrder);
    }

    /**
     * Atualiza o setor no lugar, preservando o nome e a identidade.
     *
     * <p>Existe para que trocar o preco de um setor seja um {@code UPDATE}, e nao um par
     * delete-insert: dentro do mesmo flush o Hibernate emite os INSERT antes dos DELETE, e
     * recriar um setor com um nome que ainda esta na tabela viola {@code uk_sectors_nome}.
     */
    void redefinir(BigDecimal price, int rowsCount, int seatsPerRow, int displayOrder) {
        this.price = price;
        this.rowsCount = rowsCount;
        this.seatsPerRow = seatsPerRow;
        this.displayOrder = displayOrder;
    }

    /** Quantos lugares este setor tem. */
    public int getCapacidade() {
        return rowsCount * seatsPerRow;
    }

    /**
     * Rotulo da fila de indice zero-based: 0 vira "A", 25 vira "Z", 26 vira "AA".
     *
     * <p>Mora aqui, e nao no frontend, porque e o booking-service que precisa gravar a fila junto
     * da reserva — e as duas pontas tem de chegar exatamente ao mesmo rotulo, ou "Plateia A3"
     * significaria lugares diferentes de cada lado.
     */
    public static String rotuloDaFila(int indice) {
        StringBuilder rotulo = new StringBuilder();
        int restante = indice;

        do {
            rotulo.insert(0, (char) ('A' + restante % LETRAS));
            restante = restante / LETRAS - 1;
        } while (restante >= 0);

        return rotulo.toString();
    }

    public UUID getId() {
        return id;
    }

    public Event getEvent() {
        return event;
    }

    public String getName() {
        return name;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public int getRowsCount() {
        return rowsCount;
    }

    public int getSeatsPerRow() {
        return seatsPerRow;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }

    @Override
    public boolean equals(Object outro) {
        if (this == outro) {
            return true;
        }
        if (!(outro instanceof Sector setor)) {
            return false;
        }
        return id != null && id.equals(setor.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
