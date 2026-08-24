package com.devbandeiraa.bookingservice.client;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Endereco e limites de tempo da chamada ao provedor de pagamento.
 *
 * <p>Os timeouts sao mais folgados que os do event-service, e a diferenca e proposital. Consultar
 * um catalogo e uma leitura barata que deve responder em milissegundos; autorizar uma cobranca
 * envolve um terceiro que legitimamente demora. Copiar os 3s de leitura de la para ca faria o
 * retry disparar contra cobrancas que teriam passado — e cada tentativa a mais e uma chance a mais
 * de cobrar em duplicidade se a idempotencia falhar.
 *
 * @param url            base do provedor
 * @param connectTimeout limite para estabelecer a conexao
 * @param readTimeout    limite para a resposta chegar
 */
@ConfigurationProperties(prefix = "booking.pagamento")
public record PagamentoProperties(String url, Duration connectTimeout, Duration readTimeout) {

    public PagamentoProperties {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("booking.pagamento.url e obrigatoria");
        }
    }
}
