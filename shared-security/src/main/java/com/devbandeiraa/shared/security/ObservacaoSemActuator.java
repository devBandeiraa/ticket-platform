package com.devbandeiraa.shared.security;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationPredicate;
import org.springframework.http.server.observation.ServerRequestObservationContext;

/**
 * Tira o proprio monitoramento de dentro do que ele monitora.
 *
 * <h2>Por que isto existe</h2>
 *
 * <p>O Prometheus colhe cada servico a cada dez segundos e o Docker checa a saude de cada um no
 * mesmo ritmo. Sao doze requisicoes HTTP por servico por minuto que ninguem pediu, e com amostragem
 * de trace em 100% cada uma vira um trace no Jaeger. Medido nesta plataforma logo depois de ligar a
 * coleta: <strong>198 dos 200 traces mais recentes eram {@code /actuator}</strong>. A requisicao de
 * verdade, que e o motivo de alguem abrir o Jaeger, ficava sepultada.
 *
 * <p>O estrago nao para no Jaeger. Como estas requisicoes tambem alimentam {@code
 * http.server.requests}, o painel de throughput passa a mostrar sobretudo o proprio monitoramento,
 * e o p95 despenca — um healthcheck responde em microssegundos e empurra os percentis para baixo,
 * fazendo a latencia parecer melhor do que e para quem usa o sistema.
 *
 * <h2>O que exatamente se perde</h2>
 *
 * <p>Nada que valha. A disponibilidade de um servico continua visivel: quem responde por ela e a
 * serie {@code up}, produzida pelo <em>proprio</em> Prometheus a partir do sucesso da coleta, e nao
 * pelo servico coletado. Um container que morre para de responder ao scrape e {@code up} vai a
 * zero, independentemente deste filtro.
 *
 * <p>Filtrar aqui, e nao no Grafana, e deliberado: uma consulta com {@code uri!~"/actuator.*"} em
 * cada painel resolveria o grafico e deixaria o Jaeger intacto, alem de ser uma regra que alguem
 * precisaria lembrar de repetir em todo painel novo.
 */
public final class ObservacaoSemActuator implements ObservationPredicate {

    private static final String PREFIXO = "/actuator";

    @Override
    public boolean test(String nome, Observation.Context contexto) {
        // Qualquer observacao que nao seja uma requisicao HTTP recebida passa intacta: chamadas de
        // saida, consumo de mensagem e spans abertos a mao nao tem nada a ver com este problema.
        if (contexto instanceof ServerRequestObservationContext requisicao) {
            return !requisicao.getCarrier().getRequestURI().startsWith(PREFIXO);
        }
        return true;
    }
}
