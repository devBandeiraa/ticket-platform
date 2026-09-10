package com.devbandeiraa.eventservice.dto.response;

import com.devbandeiraa.eventservice.domain.Sector;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

/**
 * Um setor, como o mapa precisa dele.
 *
 * <p>Devolve as dimensoes, e nao a lista de lugares: com filas e lugares por fila, o cliente
 * desenha a grade inteira. Enviar mil e quinhentos objetos para descrever o que dois inteiros
 * descrevem seria pagar banda por informacao que ja esta na resposta.
 *
 * <p>A disponibilidade de cada lugar <strong>nao</strong> vem daqui. Quem sabe o que ja foi
 * vendido e o booking-service, e este servico responde apenas pela planta da casa.
 *
 * @param rowLabels rotulos das filas, na ordem — "A", "B", ... A regra de nomeacao mora no
 *                  dominio, e devolve-la pronta impede que cliente e servidor cheguem a rotulos
 *                  diferentes para a mesma fila
 * @param capacity  {@code rowsCount * seatsPerRow}, calculado aqui para o cliente nao repetir a
 *                  multiplicacao e eventualmente erra-la
 */
public record SectorResponse(
        UUID id,
        String name,
        BigDecimal price,
        int rowsCount,
        int seatsPerRow,
        List<String> rowLabels,
        int capacity) {

    public static SectorResponse de(Sector setor) {
        return new SectorResponse(
                setor.getId(),
                setor.getName(),
                setor.getPrice(),
                setor.getRowsCount(),
                setor.getSeatsPerRow(),
                IntStream.range(0, setor.getRowsCount())
                        .mapToObj(Sector::rotuloDaFila)
                        .toList(),
                setor.getCapacidade());
    }
}
