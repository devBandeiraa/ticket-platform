package com.devbandeiraa.apigateway.status;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * Faz uma consulta instantanea ao Prometheus e devolve o resultado ja reduzido a um mapa.
 *
 * <p>Esta classe conhece o formato do Prometheus e nada mais. Quem sabe o que cada numero
 * significa para a plataforma e o {@link ColetorDeStatus} — a separacao existe para que trocar a
 * origem das metricas nao obrigue a mexer em quem interpreta.
 */
@Component
public class ClientePrometheus {

    /** Consulta instantanea: o valor de agora, e nao uma serie ao longo do tempo. */
    private static final String CAMINHO = "/api/v1/query";

    private final WebClient webClient;
    private final String base;
    private final Duration tempoLimite;

    public ClientePrometheus(WebClient.Builder construtor, StatusProperties propriedades) {
        this.webClient = construtor.build();
        this.base = semBarraFinal(propriedades.prometheusUrl());
        this.tempoLimite = propriedades.tempoLimite();
    }

    /**
     * Executa {@code expressao} e indexa o resultado pelo rotulo indicado.
     *
     * <p>O rotulo e parametro porque as consultas agrupam por coisas diferentes: as de servico por
     * {@code job}, a dos circuitos por {@code name}. Fixar {@code job} aqui obrigaria o chamador a
     * renomear rotulos dentro do PromQL so para caber na assinatura.
     *
     * <p>Series sem o rotulo pedido, ou com valor nao numerico, sao descartadas em silencio. O
     * {@code NaN} nao e hipotetico: uma divisao por taxa zero — nenhum trafego na janela — produz
     * exatamente isso, e trata-lo como numero faria a tela mostrar "NaN ms".
     */
    public Mono<Map<String, Double>> consultar(String expressao, String rotulo) {
        return webClient.get()
                .uri(enderecoDe(expressao))
                .retrieve()
                .bodyToMono(RespostaDeConsulta.class)
                .timeout(tempoLimite)
                .map(resposta -> resposta.indexarPor(rotulo))
                // A expressao vai na mensagem porque este erro vira um 503 para o navegador, e
                // saber qual das consultas falhou e o que separa "o Prometheus caiu" de "escrevi
                // PromQL invalido".
                .onErrorMap(falha -> new MetricasIndisponiveisException(
                        "falha ao consultar o Prometheus: " + expressao, falha));
    }

    /**
     * Monta a URI com a expressao ja codificada, em vez de deixar o WebClient monta-la.
     *
     * <p>Nao e preferencia de estilo. O construtor de URI do Spring trata {@code {...}} como
     * variavel de template, e PromQL usa chaves para filtrar rotulos — {@code
     * resilience4j_circuitbreaker_state{state="open"}} vira uma tentativa de expandir uma variavel
     * chamada {@code state="open"}, e a requisicao morre antes de sair. Passando uma
     * {@link URI} pronta, nao ha expansao alguma a acontecer.
     */
    private URI enderecoDe(String expressao) {
        return URI.create(base + CAMINHO + "?query="
                + URLEncoder.encode(expressao, StandardCharsets.UTF_8));
    }

    /** Evita a barra dupla quando o endereco configurado ja termina em barra. */
    private static String semBarraFinal(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    /**
     * O envelope do Prometheus, reduzido ao que se usa.
     *
     * <p>{@code @JsonIgnoreProperties} porque a resposta traz campos que nao interessam
     * ({@code resultType}, avisos, estatisticas). Sem ele, o Prometheus acrescentar um campo numa
     * versao nova quebraria a desserializacao — a tela cairia por causa de um dado que ninguem le.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record RespostaDeConsulta(Dados data) {

        Map<String, Double> indexarPor(String rotulo) {
            if (data == null || data.result() == null) {
                return Map.of();
            }
            return data.result().stream()
                    .filter(serie -> serie.rotulo(rotulo) != null && serie.numero() != null)
                    .collect(Collectors.toMap(
                            serie -> serie.rotulo(rotulo),
                            Serie::numero,
                            // Duas series com o mesmo rotulo so aconteceria se a consulta
                            // esquecesse um `sum by`. Ficar com a primeira evita a excecao do
                            // toMap e mantem a tela no ar; a consulta errada aparece como numero
                            // estranho, que e mais facil de investigar que um 503.
                            (primeira, segunda) -> primeira));
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Dados(List<Serie> result) {
    }

    /**
     * @param value par {@code [instante, valor]}, com o valor em texto — e assim mesmo que o
     *              Prometheus responde, para nao perder precisao em numeros muito grandes
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Serie(Map<String, String> metric, List<Object> value) {

        String rotulo(String nome) {
            return metric == null ? null : metric.get(nome);
        }

        Double numero() {
            if (value == null || value.size() < 2) {
                return null;
            }
            try {
                double numero = Double.parseDouble(String.valueOf(value.get(1)));
                return Double.isFinite(numero) ? numero : null;
            } catch (NumberFormatException naoEhNumero) {
                return null;
            }
        }
    }
}
