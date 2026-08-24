package com.devbandeiraa.shared.security;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationPredicate;
import org.springframework.http.server.reactive.observation.ServerRequestObservationContext;

/**
 * O mesmo que {@link ObservacaoSemActuator}, para quem roda sobre WebFlux — hoje, o api-gateway.
 *
 * <p>Sao duas classes e nao uma porque servlet e reativo tem contextos de observacao homonimos em
 * pacotes diferentes, sem supertipo comum que exponha o caminho da requisicao. Unifica-las exigiria
 * reflexao ou um {@code instanceof} contra um tipo ausente do classpath — que falha no momento em
 * que o metodo e verificado, e nao numa condicao que se possa avaliar antes.
 *
 * <p>Aqui o efeito e ainda maior que nos demais servicos: o gateway e o servico que alguem busca
 * primeiro no Jaeger, por ser onde toda requisicao externa comeca.
 */
public final class ObservacaoSemActuatorReativa implements ObservationPredicate {

    private static final String PREFIXO = "/actuator";

    @Override
    public boolean test(String nome, Observation.Context contexto) {
        if (contexto instanceof ServerRequestObservationContext requisicao) {
            return !requisicao.getCarrier().getPath().value().startsWith(PREFIXO);
        }
        return true;
    }
}
