package com.devbandeiraa.notificationservice.config;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationPredicate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.observation.ServerRequestObservationContext;

/**
 * Mantem o proprio monitoramento fora do que ele monitora.
 *
 * <p>Copia deliberada do {@code ObservacaoSemActuator} do shared-security, e nao um reuso: aquele
 * modulo exige um segredo JWT valido na subida, e este servico nao valida token nenhum — depender
 * dele obrigaria a distribuir uma credencial que nao tem uso aqui, o que e pior que repetir seis
 * linhas. O comentario completo sobre <em>por que</em> filtrar esta la.
 *
 * <p>O efeito e mais visivel neste servico do que nos demais: como ele nao expoe API alguma, todo o
 * seu trafego HTTP e healthcheck e coleta. Sem este filtro, procura-lo no Jaeger — que e o que se
 * faz para conferir se a notificacao ficou pendurada na arvore da compra — devolveria apenas
 * {@code /actuator}.
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
