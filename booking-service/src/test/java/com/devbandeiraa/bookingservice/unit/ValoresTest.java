package com.devbandeiraa.bookingservice.unit;

import static org.assertj.core.api.Assertions.assertThat;

import com.devbandeiraa.bookingservice.domain.Valores;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * A aritmetica do dinheiro da reserva.
 *
 * <p>O que estes testes protegem e uma invariante que o banco tambem guarda, em
 * {@code ck_bookings_total_fecha}: total e sempre subtotal mais taxa. Chegar la e melhor do que
 * gravar incoerencia, e pior do que falhar aqui — no banco o erro aparece longe de onde nasceu.
 */
class ValoresTest {

    @Test
    @DisplayName("taxa de 10% sobre 100 da 10, e o total fecha em 110")
    void deveCalcularTaxaSimples() {
        Valores valores = Valores.de(new BigDecimal("100.00"), new BigDecimal("10"));

        assertThat(valores.subtotal()).isEqualByComparingTo("100.00");
        assertThat(valores.taxa()).isEqualByComparingTo("10.00");
        assertThat(valores.total()).isEqualByComparingTo("110.00");
    }

    @Test
    @DisplayName("percentual zero desliga a cobranca, e o total e a soma dos lugares")
    void taxaZeroDeveManterOTotal() {
        Valores valores = Valores.de(new BigDecimal("249.90"), BigDecimal.ZERO);

        assertThat(valores.taxa()).isEqualByComparingTo("0.00");
        assertThat(valores.total()).isEqualByComparingTo("249.90");
    }

    @ParameterizedTest(name = "{0} a {1}% -> taxa {2}, total {3}")
    @DisplayName("a taxa arredonda com HALF_UP, e o total sempre fecha")
    @CsvSource({
            // O caso que motiva HALF_UP: 0,5 centavo tem de subir, que e a regra que a pessoa
            // aprende na escola e consegue reproduzir de cabeca.
            "  90.00, 10,  9.00,  99.00",
            "  90.10, 10,  9.01,  99.11",
            // 9,015 -> 9,02 com HALF_UP. HALF_EVEN daria 9,02 tambem aqui; o proximo separa.
            "  90.15, 10,  9.02,  99.17",
            // 12,505 -> 12,51 com HALF_UP; HALF_EVEN daria 12,50, e o comprador nao saberia
            // explicar por que dois valores parecidos arredondaram para lados diferentes.
            " 250.10,  5, 12.51, 262.61",
            "   0.00, 10,  0.00,   0.00",
            "  33.33,  7,  2.33,  35.66",
    })
    void deveArredondarEFechar(String subtotal, String percentual, String taxa, String total) {
        Valores valores = Valores.de(new BigDecimal(subtotal), new BigDecimal(percentual));

        assertThat(valores.taxa()).isEqualByComparingTo(taxa);
        assertThat(valores.total()).isEqualByComparingTo(total);
        // A invariante, verificada de novo a partir do resultado: nao basta cada numero estar
        // certo isoladamente, eles precisam somar.
        assertThat(valores.subtotal().add(valores.taxa())).isEqualByComparingTo(valores.total());
    }

    @Test
    @DisplayName("os tres valores saem com duas casas, como a coluna NUMERIC(10,2)")
    void deveFixarAEscalaEmDuasCasas() {
        // Sem a escala fixa, um subtotal vindo de `BigDecimal.ZERO` sairia com escala 0 e o JSON
        // mostraria "0" onde a tela espera "0.00".
        Valores valores = Valores.de(BigDecimal.ZERO, new BigDecimal("10"));

        assertThat(valores.subtotal().scale()).isEqualTo(2);
        assertThat(valores.taxa().scale()).isEqualTo(2);
        assertThat(valores.total().scale()).isEqualTo(2);
    }
}
