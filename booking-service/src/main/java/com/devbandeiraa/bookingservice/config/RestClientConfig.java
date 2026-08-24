package com.devbandeiraa.bookingservice.config;

import com.devbandeiraa.bookingservice.client.EventServiceProperties;
import com.devbandeiraa.bookingservice.client.PagamentoProperties;
import com.devbandeiraa.shared.security.CorrelacaoDeRequisicao;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpRequest;
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
                .requestInitializer(RestClientConfig::propagarCorrelacao)
                .build();
    }

    /**
     * Cliente do provedor de pagamento.
     *
     * <p>Bean separado, e nao o mesmo {@code RestClient} com outra URL: os limites de tempo sao
     * diferentes por natureza. Consultar um catalogo e leitura barata; autorizar uma cobranca
     * envolve um terceiro que legitimamente demora. Um cliente unico obrigaria a adotar o maior
     * dos dois timeouts para os dois destinos, e o event-service voltaria a poder prender threads
     * por segundos — exatamente o que os timeouts curtos dele existem para impedir.
     */
    @Bean
    RestClient pagamentoRestClient(RestClient.Builder builder, PagamentoProperties propriedades) {
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(propriedades.connectTimeout());
        fabrica.setReadTimeout(propriedades.readTimeout());

        return builder
                .baseUrl(propriedades.url())
                .requestFactory(fabrica)
                .requestInitializer(RestClientConfig::propagarCorrelacao)
                .build();
    }

    /**
     * Repassa o {@code X-Request-Id} adiante, para o servico de destino registrar os proprios logs
     * sob o mesmo identificador.
     *
     * <p>Esta e a unica chamada HTTP entre servicos do projeto, e portanto o unico ponto em que a
     * corrente de correlacao poderia se partir. Sem esta linha, o gateway e o booking-service
     * compartilhariam um id e o event-service inventaria outro — e a investigacao de uma reserva
     * que falhou na hidratacao do estoque, que e exatamente onde esta chamada acontece, pararia na
     * fronteira entre os dois servicos.
     *
     * <p>Ler do MDC funciona porque {@code RestClient} e sincrono: a chamada sai na mesma thread
     * que atende a requisicao, onde o {@code CorrelacaoServletFilter} ja gravou o id. Fosse
     * assincrono, o valor teria que ser capturado antes e carregado explicitamente.
     */
    private static void propagarCorrelacao(HttpRequest requisicao) {
        String id = MDC.get(CorrelacaoDeRequisicao.CHAVE_MDC);
        if (id != null) {
            requisicao.getHeaders().set(CorrelacaoDeRequisicao.CABECALHO, id);
        }
    }
}
