package com.devbandeiraa.paymentsimulator.config;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationPredicate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.observation.ServerRequestObservationContext;

/**
 * Mantem o proprio monitoramento fora do que ele monitora.
 *
 * <p>Copia deliberada do {@code ObservacaoSemActuator} do shared-security, pelo mesmo motivo do
 * notification-service: aquele modulo exige um segredo JWT valido na subida, e este servico e um
 * terceiro de mentira que nao conhece os tokens da plataforma. O comentario completo sobre
 * <em>por que</em> filtrar esta la.
 *
 * <p>Aqui a distorcao que o filtro corrige e a de latencia. As cobrancas demoram centenas de
 * milissegundos de proposito, e os healthchecks respondem em microssegundos: misturados na mesma
 * serie, empurram o p95 para baixo e fariam o provedor instavel parecer rapido justamente no painel
 * que existe para mostrar que ele nao e.
 */
@Configuration(proxyBeanMethods = false)
public class ObservabilidadeConfig {

    private static final String PREFIXO_DO_ACTUATOR = "/actuator";

    @Bean
    ObservationPredicate ignorarActuator() {
        return (nome, contexto) -> {
            if (contexto instanceof ServerRequestObservationContext requisicao) {
                return !requisicao.getCarrier().getRequestURI().startsWith(PREFIXO_DO_ACTUATOR);
            }
            return true;
        };
    }
}
