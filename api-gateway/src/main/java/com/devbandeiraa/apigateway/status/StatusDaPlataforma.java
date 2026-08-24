package com.devbandeiraa.apigateway.status;

import java.time.Instant;
import java.util.List;

/**
 * O que a tela {@code /status} mostra, ja no formato em que ela precisa.
 *
 * <p>Deliberadamente <em>nao</em> e um repasse do JSON do Prometheus. Aquele formato descreve
 * series temporais — resultType, vetores, pares [instante, valor em texto] — e traduzi-lo no
 * navegador espalharia o conhecimento de PromQL pelo frontend. Aqui a traducao acontece uma vez,
 * do lado do servidor, e a tela recebe nomes que ela entende.
 *
 * <p>O ganho pratico aparece no dia em que a origem mudar. Trocar Prometheus por outra coisa, ou
 * passar a somar duas consultas numa so, e uma mudanca deste pacote — a tela nao percebe.
 *
 * @param coletadoEm quando o gateway perguntou, e nao quando o Prometheus mediu; serve para a tela
 *                   mostrar que continua viva mesmo quando nenhum numero muda
 */
public record StatusDaPlataforma(
        Instant coletadoEm,
        List<Servico> servicos,
        List<Circuito> circuitos) {

    /**
     * @param noAr             vem da serie {@code up}, produzida pelo proprio Prometheus a partir
     *                         do sucesso da coleta. Nao e o servico se declarando saudavel: e um
     *                         terceiro constatando que ele respondeu — a diferenca entre um
     *                         processo vivo e um processo que acha que esta vivo
     * @param latenciaMediaMs  nulo quando nao houve trafego na janela. Nulo e diferente de zero, e
     *                         a tela distingue os dois: "sem trafego" nao e "instantaneo"
     * @param uptimeSegundos   nulo quando o servico esta fora — nao ha processo de que perguntar
     */
    public record Servico(
            String nome,
            boolean noAr,
            Double latenciaMediaMs,
            Long uptimeSegundos) {
    }

    public record Circuito(String nome, Estado estado) {
    }

    /**
     * Os tres estados que interessam a quem olha.
     *
     * <p>O Resilience4j tem mais — {@code DISABLED}, {@code FORCED_OPEN}, {@code METRICS_ONLY} —
     * mas nenhum deles acontece nesta plataforma, que nao manipula circuitos em tempo de execucao.
     * Traduzi-los seria inventar rotulos para estados que a tela nunca vai mostrar.
     */
    public enum Estado {
        FECHADO,
        MEIO_ABERTO,
        ABERTO
    }
}
