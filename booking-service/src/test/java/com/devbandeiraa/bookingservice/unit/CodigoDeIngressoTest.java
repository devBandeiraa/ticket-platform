package com.devbandeiraa.bookingservice.unit;

import static org.assertj.core.api.Assertions.assertThat;

import com.devbandeiraa.bookingservice.domain.CodigoDeIngresso;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CodigoDeIngressoTest {

    @Test
    @DisplayName("tem o formato TP-XXXXXX-XXXXXX e cabe na coluna")
    void deveTerOFormatoCombinado() {
        String codigo = CodigoDeIngresso.gerar();

        assertThat(codigo).matches("TP-[0-9A-Z]{6}-[0-9A-Z]{6}");
        // A coluna e VARCHAR(20). Um formato que crescesse para 21 caracteres so falharia no
        // INSERT, ja com o pagamento autorizado.
        assertThat(codigo).hasSize(16);
    }

    @Test
    @DisplayName("nao usa os simbolos que se confundem ao ler ou digitar")
    void naoDeveUsarSimbolosAmbiguos() {
        // I, O, Q, S e Z se confundem com 1, 0 e 2. O codigo existe para ser lido em voz alta e
        // digitado a partir de uma tela, entao a ambiguidade custa mais do que a entropia.
        String amostra = IntStream.range(0, 400)
                .mapToObj(i -> CodigoDeIngresso.gerar())
                .reduce("", String::concat);

        assertThat(amostra).doesNotContain("I", "O", "Q", "Z");
        // "S" merece cuidado a parte: nao pode aparecer nos grupos, mas nao ha "S" no prefixo
        // "TP-" para confundir a assercao.
        assertThat(amostra.replace("TP-", "")).doesNotContain("S");
    }

    @Test
    @DisplayName("nao repete em dez mil geracoes")
    void naoDeveRepetirEmVolumeRazoavel() {
        // Nao prova unicidade, e nem poderia — a garantia e o indice unico no banco. Prova que o
        // gerador nao tem um vies grosseiro, do tipo devolver o mesmo codigo por semente fixa,
        // que e o defeito que passaria despercebido ate a producao.
        Set<String> vistos = new HashSet<>();
        IntStream.range(0, 10_000).forEach(i -> vistos.add(CodigoDeIngresso.gerar()));

        assertThat(vistos).hasSize(10_000);
    }
}
