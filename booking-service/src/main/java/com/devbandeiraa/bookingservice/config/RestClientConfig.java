package com.devbandeiraa.bookingservice.config;

import com.devbandeiraa.bookingservice.client.EventServiceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Cliente HTTP para o event-service.
 *
 * <p>A construcao mora aqui, e nao dentro do {@code EventClient}, para separar "como se conecta"
 * de "o que se pede". O ganho pratico aparece nos testes: como o cliente recebe um
 * {@code RestClient} pronto, um teste pode entregar um instrumentado e verificar o tratamento de
 * 404 e de timeout sem subir servidor nenhum.
 */
@Configuration
public class RestClientConfig {

    /**
     * Os timeouts sao o ponto importante desta configuracao.
     *
     * <p>Sem eles, o cliente espera indefinidamente. Um event-service lento — nao fora do ar,
     * apenas lento — prenderia uma thread do booking-service por requisicao ate o pool esgotar,
     * e entao o booking-service tambem pararia de responder. E assim que a falha de um servico
     * se espalha para outro que estava saudavel.
     */
    @Bean
    RestClient eventRestClient(RestClient.Builder builder, EventServiceProperties propriedades) {
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(propriedades.connectTimeout());
        fabrica.setReadTimeout(propriedades.readTimeout());

        return builder
                .baseUrl(propriedades.url())
                .requestFactory(fabrica)
                .build();
    }
}
