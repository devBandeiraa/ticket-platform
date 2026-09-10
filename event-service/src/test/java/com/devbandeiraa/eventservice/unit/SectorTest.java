package com.devbandeiraa.eventservice.unit;

import static org.assertj.core.api.Assertions.assertThat;

import com.devbandeiraa.eventservice.domain.Sector;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Testes do rotulo de fila.
 *
 * <p>Parece detalhe de apresentacao e nao e: o rotulo entra na chave natural do assento. O
 * booking-service grava "Plateia A3" junto da reserva, e o mapa no navegador desenha "Plateia
 * A3" — se as duas pontas discordarem da nomeacao, passam a falar de lugares diferentes com o
 * mesmo nome. Por isso a regra mora no dominio e e devolvida pronta na API.
 */
class SectorTest {

    @ParameterizedTest
    @CsvSource({
            "0, A",
            "1, B",
            "25, Z",
            // A virada e o caso que uma implementacao ingenua erra: depois de Z vem AA, e nao
            // BA nem AAA. Uma casa com mais de 26 filas nao e incomum.
            "26, AA",
            "27, AB",
            "51, AZ",
            "52, BA",
            "701, ZZ",
            "702, AAA"
    })
    @DisplayName("nomeia as filas como colunas de planilha: A, Z, AA, AB")
    void deveNomearFilas(int indice, String esperado) {
        assertThat(Sector.rotuloDaFila(indice)).isEqualTo(esperado);
    }

    @Test
    @DisplayName("nenhuma fila repete rotulo, mesmo numa casa grande")
    void rotulosDevemSerUnicos() {
        // Rotulo repetido tornaria dois lugares indistinguiveis pela chave natural — e a
        // reserva de um seria a reserva do outro. O teto de filas do SectorRequest e 200.
        assertThat(IntStream.range(0, 200).mapToObj(Sector::rotuloDaFila).distinct().count())
                .isEqualTo(200);
    }
}
