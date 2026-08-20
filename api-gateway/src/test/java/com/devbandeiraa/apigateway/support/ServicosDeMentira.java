package com.devbandeiraa.apigateway.support;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.concurrent.TimeUnit;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.springframework.test.context.DynamicPropertyRegistry;

/**
 * Tres servidores de mentira no lugar de auth, event e booking.
 *
 * <p>Sao tres, e nao um, porque parte do que se verifica aqui e <em>para qual servico</em> cada
 * caminho vai. Com um servidor so, a rota de disponibilidade — o unico caminho sob {@code /events}
 * que pertence ao booking-service — passaria no teste mesmo se estivesse sendo entregue ao
 * event-service, que e exatamente o erro que o teste existe para pegar.
 *
 * <p>Servidor HTTP de verdade, e nao um {@code WebClient} simulado: o que se quer conferir e o que
 * chega do outro lado do fio depois de o gateway reescrever caminho e cabecalhos.
 */
public final class ServicosDeMentira {

    public static final MockWebServer AUTH = iniciar();
    public static final MockWebServer EVENT = iniciar();
    public static final MockWebServer BOOKING = iniciar();

    private ServicosDeMentira() {
    }

    /** Substitui os enderecos reais pelos das portas efemeras dos servidores acima. */
    public static void registrarEnderecos(DynamicPropertyRegistry registro) {
        registro.add("servicos.auth", () -> AUTH.url("/").toString());
        registro.add("servicos.event", () -> EVENT.url("/").toString());
        registro.add("servicos.booking", () -> BOOKING.url("/").toString());
    }

    /**
     * Consome a proxima requisicao recebida, ou devolve {@code null} depois de um segundo.
     *
     * <p>O {@code null} e o que permite afirmar que um servico <b>nao</b> foi chamado — o caso do
     * token invalido, que deve morrer no gateway.
     */
    public static RecordedRequest proximaRequisicao(MockWebServer servidor) throws InterruptedException {
        return servidor.takeRequest(1, TimeUnit.SECONDS);
    }

    /** Descarta requisicoes pendentes para que um teste nao enxergue o trafego do anterior. */
    public static void limpar() throws InterruptedException {
        for (MockWebServer servidor : new MockWebServer[] {AUTH, EVENT, BOOKING}) {
            while (servidor.takeRequest(50, TimeUnit.MILLISECONDS) != null) {
                // apenas esvazia a fila
            }
        }
    }

    /**
     * Iniciados em bloco estatico, e nao em {@code @BeforeAll}: os enderecos precisam existir
     * quando o {@code @DynamicPropertySource} monta a tabela de rotas, o que acontece antes.
     */
    private static MockWebServer iniciar() {
        MockWebServer servidor = new MockWebServer();

        // Um dispatcher fixo dispensa enfileirar uma resposta por requisicao. Como nenhum teste
        // aqui depende do corpo devolvido, o interesse esta sempre no que chegou, e nao no que
        // voltou, um 200 vazio basta.
        servidor.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest requisicao) {
                return new MockResponse().setResponseCode(200);
            }
        });

        try {
            servidor.start();
        } catch (IOException falha) {
            throw new UncheckedIOException("nao foi possivel subir o servico de mentira", falha);
        }
        return servidor;
    }
}
