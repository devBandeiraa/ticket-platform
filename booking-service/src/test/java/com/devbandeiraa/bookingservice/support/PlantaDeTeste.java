package com.devbandeiraa.bookingservice.support;

import com.devbandeiraa.bookingservice.client.EventSnapshot;
import com.devbandeiraa.bookingservice.client.SectorSnapshot;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

/**
 * Plantas de casa para os testes.
 *
 * <p>Existe para que cada teste diga "um evento com 50 lugares" em vez de montar filas e
 * rotulos a mao. O que os testes verificam e a disputa pelos lugares, e nao a aritmetica de
 * transformar dimensoes em assentos — repetir essa montagem em treze arquivos so criaria treze
 * lugares para ela divergir.
 */
public final class PlantaDeTeste {

    /** Lugares por fila. Filas curtas fazem um evento pequeno ter mais de uma fila. */
    private static final int LUGARES_POR_FILA = 10;

    private PlantaDeTeste() {
    }

    /** Um evento com um setor unico de {@code capacidade} lugares, todos ao mesmo preco. */
    public static EventSnapshot eventoCom(UUID eventoId, int capacidade, BigDecimal preco) {
        return new EventSnapshot(eventoId, List.of(setor("Plateia", capacidade, preco)));
    }

    /** Um evento com dois setores de precos diferentes, para exercitar a soma da reserva. */
    public static EventSnapshot eventoComDoisSetores(UUID eventoId, int capacidadePorSetor,
                                                     BigDecimal precoPlateia,
                                                     BigDecimal precoBalcao) {
        return new EventSnapshot(eventoId, List.of(
                setor("Plateia", capacidadePorSetor, precoPlateia),
                setor("Balcao", capacidadePorSetor, precoBalcao)));
    }

    /**
     * As dimensoes precisam multiplicar exatamente a capacidade pedida: um setor e regular, e
     * uma fila incompleta nao existe no dominio.
     *
     * <p>Capacidade multipla de {@value #LUGARES_POR_FILA} vira filas cheias — util porque um
     * evento com varias filas exercita a nomeacao A, B, C. Qualquer outra vira uma fila unica,
     * que e o que permite pedir "um evento de 3 lugares" sem inventar meia fila.
     */
    private static SectorSnapshot setor(String nome, int capacidade, BigDecimal preco) {
        if (capacidade <= 0) {
            throw new IllegalArgumentException("um setor precisa de ao menos um lugar");
        }

        boolean filasCheias = capacidade % LUGARES_POR_FILA == 0;
        int filas = filasCheias ? capacidade / LUGARES_POR_FILA : 1;
        int lugaresPorFila = filasCheias ? LUGARES_POR_FILA : capacidade;

        return new SectorSnapshot(nome, preco, rotulos(filas), lugaresPorFila);
    }

    /** "A", "B", ... — a mesma nomeacao que o event-service devolve. */
    private static List<String> rotulos(int filas) {
        return IntStream.range(0, filas)
                .mapToObj(indice -> String.valueOf((char) ('A' + indice)))
                .toList();
    }
}
