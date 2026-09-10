package com.devbandeiraa.bookingservice.client;

import java.math.BigDecimal;
import java.util.List;

/**
 * Um setor, como o event-service o descreve.
 *
 * <p>Os rotulos das filas vem prontos de la, e nao sao recalculados aqui. E deliberado: o rotulo
 * entra na chave natural do assento, e duas implementacoes da mesma regra de nomeacao acabariam
 * divergindo no primeiro caso de borda — a virada de "Z" para "AA". Divergindo, os dois servicos
 * passariam a falar de lugares diferentes com o mesmo nome.
 */
public record SectorSnapshot(String name, BigDecimal price, List<String> rowLabels, int seatsPerRow) {
}
