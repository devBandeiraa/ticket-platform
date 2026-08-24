package com.devbandeiraa.apigateway.status;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * De onde o painel de status tira os numeros.
 *
 * @param prometheusUrl endereco do Prometheus <em>dentro</em> da rede. O navegador nunca fala com
 *                      ele: quem consulta e o gateway. Publicar o Prometheus para a internet so
 *                      para uma pagina de status entregaria, junto, toda a topologia interna
 * @param tempoLimite   teto por consulta. Curto de proposito — a pagina se atualiza a cada poucos
 *                      segundos, e uma consulta que demora mais que isso ja perdeu a validade
 */
@ConfigurationProperties(prefix = "status")
public record StatusProperties(String prometheusUrl, Duration tempoLimite) {

    public StatusProperties {
        if (prometheusUrl == null || prometheusUrl.isBlank()) {
            throw new IllegalStateException("status.prometheus-url nao foi configurado");
        }
        if (tempoLimite == null) {
            tempoLimite = Duration.ofSeconds(3);
        }
    }
}
