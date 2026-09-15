package com.devbandeiraa.bookingservice.service;

import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Taxa de servico cobrada sobre o subtotal da reserva.
 *
 * <p>Configuravel, e nao constante no codigo, porque e um numero comercial: quem decide mudar de
 * 10% para 8% nao deveria precisar de um deploy com recompilacao. Zero desliga a cobranca, e o
 * total passa a ser exatamente a soma dos lugares.
 *
 * <p>O valor aplicado fica GRAVADO em {@code bookings.fee}, e nao recalculado na leitura. Uma
 * reserva feita sob 10% continua valendo 10% depois de a configuracao mudar — recalcular faria o
 * historico inteiro mudar de valor junto da propriedade, e o comprovante do usuario deixaria de
 * bater com o que o sistema mostra.
 *
 * @param percentual taxa em pontos percentuais: {@code 10} significa 10%
 */
@ConfigurationProperties(prefix = "booking.taxa")
public record TaxaProperties(BigDecimal percentual) {

    /** Teto de sanidade. Nao e regra de negocio: e um erro de digitacao que nao pode ir ao ar. */
    private static final BigDecimal MAXIMO = new BigDecimal("100");

    public TaxaProperties {
        if (percentual == null || percentual.signum() < 0) {
            throw new IllegalArgumentException(
                    "booking.taxa.percentual precisa ser zero ou positivo");
        }
        if (percentual.compareTo(MAXIMO) > 0) {
            // Um `1000` digitado no lugar de `10` cobraria dez vezes o preco do ingresso, e o
            // erro so apareceria na fatura de alguem.
            throw new IllegalArgumentException(
                    "booking.taxa.percentual nao pode passar de 100");
        }
    }
}
