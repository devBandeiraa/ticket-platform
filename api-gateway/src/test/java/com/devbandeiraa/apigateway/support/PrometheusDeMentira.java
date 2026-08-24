package com.devbandeiraa.apigateway.support;

import java.io.IOException;
import java.io.UncheckedIOException;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.springframework.test.context.DynamicPropertyRegistry;

/**
 * Um Prometheus de mentira, que responde de acordo com a consulta recebida.
 *
 * <p>Servidor HTTP de verdade em vez de um dublê do {@code ClientePrometheus}: parte do que se
 * verifica e a traducao do JSON — o valor que vem como texto, o {@code NaN} de uma divisao por
 * zero, o rotulo de que cada consulta agrupa. Substituindo o cliente, nada disso seria exercitado,
 * e o teste passaria a afirmar apenas que o codigo chama o codigo.
 *
 * <p>O despacho olha a consulta porque as quatro saem em paralelo: uma fila de respostas na ordem
 * de enfileiramento daria resultados trocados a cada execucao.
 */
public final class PrometheusDeMentira {

    public static final MockWebServer SERVIDOR = iniciar();

    /** Consultas conhecidas, identificadas por um trecho que so aparece em uma delas. */
    private static final String CIRCUITOS = "resilience4j_circuitbreaker_state";
    private static final String UPTIME = "process_uptime_seconds";
    private static final String LATENCIA = "http_server_requests_seconds_sum";

    private PrometheusDeMentira() {
    }

    public static void registrarEndereco(DynamicPropertyRegistry registro) {
        registro.add("status.prometheus-url", () -> SERVIDOR.url("/").toString());
    }

    /**
     * Instala um retrato: quais servicos existem, quais responderam e como estao os circuitos.
     *
     * <p>Um {@link Dispatcher}, e nao respostas enfileiradas, para o mesmo retrato valer por
     * quantas coletas o teste fizer.
     */
    public static void responder(String noAr, String latencia, String uptime, String circuitos) {
        SERVIDOR.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest requisicao) {
                String consulta = String.valueOf(requisicao.getPath());

                // A ordem importa: "uptime" contem "up", entao a consulta mais especifica precisa
                // ser testada antes — do contrario todo mundo cairia no primeiro caso.
                if (consulta.contains(CIRCUITOS)) {
                    return json(circuitos);
                }
                if (consulta.contains(UPTIME)) {
                    return json(uptime);
                }
                if (consulta.contains(LATENCIA)) {
                    return json(latencia);
                }
                return json(noAr);
            }
        });
    }

    /** Faz o servidor recusar tudo, para exercitar o caminho em que a fonte de metricas caiu. */
    public static void falhar() {
        SERVIDOR.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest requisicao) {
                return new MockResponse().setResponseCode(500);
            }
        });
    }

    /**
     * Monta o envelope do Prometheus em volta das series informadas.
     *
     * @param series pares rotulo/valor ja no formato do Prometheus, separados por virgula
     */
    public static String vetor(String... series) {
        return """
                {"status":"success","data":{"resultType":"vector","result":[%s]}}"""
                .formatted(String.join(",", series));
    }

    /** Uma serie: um rotulo e um valor. O valor vai como texto, como o Prometheus responde. */
    public static String serie(String rotulo, String nome, String valor) {
        return """
                {"metric":{"%s":"%s"},"value":[1787000000.0,"%s"]}"""
                .formatted(rotulo, nome, valor);
    }

    private static MockResponse json(String corpo) {
        return new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(corpo);
    }

    private static MockWebServer iniciar() {
        MockWebServer servidor = new MockWebServer();
        try {
            servidor.start();
        } catch (IOException falha) {
            throw new UncheckedIOException("nao foi possivel subir o Prometheus de mentira", falha);
        }
        return servidor;
    }
}
