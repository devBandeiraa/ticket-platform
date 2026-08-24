package com.devbandeiraa.bookingservice.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.devbandeiraa.bookingservice.support.TestcontainersConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * O que este servico promete ao Prometheus.
 *
 * <p>A coleta e um contrato silencioso: o Prometheus nunca reclama de uma metrica que deixou de
 * existir — ele simplesmente para de gravar a serie, e o painel que dependia dela fica plano. O
 * problema so aparece na hora em que alguem abre o Grafana para investigar um incidente e encontra
 * um grafico vazio. Estes testes prendem os nomes de que os paineis versionados em
 * {@code monitoring/grafana/dashboards} dependem.
 *
 * <p>{@code @AutoConfigureObservability} pelo mesmo motivo do
 * {@link TraceNaOutboxIntegrationTest}: o Boot desliga a exportacao de metricas em teste, e sem
 * religa-la o {@code /actuator/prometheus} nem existiria.
 */
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureObservability
@Import(TestcontainersConfig.class)
@ActiveProfiles("test")
class MetricasPrometheusIntegrationTest {

    private static final String CAMINHO = "/actuator/prometheus";

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("a coleta responde sem token")
    void coletaEPublica() throws Exception {
        // Nao e descuido. O endereco so existe dentro da rede do compose — nao ha rota para ele no
        // gateway — e exigir credencial faria o Prometheus precisar de uma, que teria de ser
        // distribuida e rotacionada para ler contadores de um ambiente de desenvolvimento.
        mockMvc.perform(get(CAMINHO)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("publica a latencia em faixas, e nao so a media")
    void publicaFaixasDeLatencia() throws Exception {
        // Precisa ser uma rota da aplicacao, e nao um /actuator: aquelas sao descartadas de
        // proposito antes de virar metrica. O status da resposta nao importa — o que se quer e
        // que a requisicao tenha sido cronometrada.
        mockMvc.perform(get("/bookings/me"));

        String coleta = coletar();

        // `_bucket` e o que distingue um histograma de um cronometro simples. Sem ele existiriam
        // apenas `_count` e `_sum` — suficientes para media, insuficientes para p95, que e o
        // numero que os paineis mostram. Desligar percentiles-histogram nao quebra nada
        // visivelmente: os graficos de latencia e que ficariam vazios.
        assertThat(coleta)
                .as("sem as faixas, o painel de latencia p95 fica vazio")
                .contains("http_server_requests_seconds_bucket");
    }

    @Test
    @DisplayName("o proprio monitoramento fica de fora das metricas")
    void naoMedeOProprioMonitoramento() throws Exception {
        // Duas requisicoes ao actuator, que e o que o Prometheus e o healthcheck do Docker fazem
        // doze vezes por minuto em cada servico.
        mockMvc.perform(get("/actuator/health"));
        mockMvc.perform(get("/actuator/health"));

        String coleta = coletar();

        // Contadas, elas dominariam o throughput — o grafico mostraria sobretudo o monitoramento
        // se monitorando — e puxariam o p95 para baixo, porque um healthcheck responde em
        // microssegundos e faz a latencia parecer melhor do que e para quem usa o sistema.
        assertThat(coleta)
                .as("o healthcheck nao pode virar amostra de latencia da aplicacao")
                .doesNotContain("uri=\"/actuator/health\"");
    }

    @Test
    @DisplayName("publica o estado do circuit breaker do event-service")
    void publicaEstadoDoCircuito() throws Exception {
        String coleta = coletar();

        assertThat(coleta).contains("resilience4j_circuitbreaker_state");
        // O nome da instancia e o que o painel filtra. Renomea-la no application.yml sem tocar no
        // dashboard deixaria o indicador de circuito permanentemente vazio.
        assertThat(coleta).contains("name=\"event-service\"");
    }

    @Test
    @DisplayName("publica os contadores do dominio, e nao so os da JVM")
    void publicaContadoresDoDominio() throws Exception {
        String coleta = coletar();

        // Estes quatro sao a razao de o painel "Reservas e mensageria" existir. Memoria e threads
        // qualquer servico publica; disputa por lock e mensagem descartada na outbox sao o que
        // este servico especificamente tem a dizer.
        assertThat(coleta)
                .contains("booking_lock_adquiridos_total")
                .contains("booking_lock_nao_adquiridos_total")
                .contains("booking_lock_degradacoes_total")
                .contains("booking_outbox_publicadas_total");
    }

    private String coletar() throws Exception {
        return mockMvc.perform(get(CAMINHO))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }
}
