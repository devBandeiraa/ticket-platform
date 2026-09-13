package com.devbandeiraa.bookingservice.logging;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.LoggingEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.logging.logback.StructuredLogEncoder;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.mock.env.MockEnvironment;

/**
 * Verifica que o log sai em JSON no formato ECS, com o MDC virando campo.
 *
 * <p>Sem isto, a configuracao de log estruturado seria uma afirmacao nao verificada — e do tipo
 * que ninguem percebe quebrada, porque log continua saindo de algum jeito. O sintoma apareceria
 * so no agregador, em producao, quando alguem tentasse filtrar por {@code requestId} e nao
 * achasse o campo.
 *
 * <h2>Por que sem contexto Spring</h2>
 *
 * <p>A primeira versao subia um {@code @SpringBootTest} com a propriedade ligada e capturava a
 * saida. Passava sozinha e falhava junto das demais: o Logback e inicializado <strong>uma vez por
 * JVM</strong>, e um contexto anterior com formato diferente deixava o appender ja montado. Um
 * teste que depende da ordem de execucao e pior que teste nenhum — ele passa no laptop e falha no
 * CI, ou o contrario, e o tempo perdido investigando nao volta.
 *
 * <p>Aqui o encoder e montado explicitamente, e o que se verifica e exatamente o que a
 * propriedade {@code logging.structured.format.console=ecs} liga em producao: o
 * {@code StructuredLogEncoder} do Spring Boot no formato ECS.
 */
class LogEstruturadoTest {

    @Test
    @DisplayName("cada linha e um objeto JSON, e nao texto com prefixo")
    void deveEmitirJson() throws Exception {
        JsonNode linha = codificar("reserva paga", Map.of());

        assertThat(linha.get("message").asText()).isEqualTo("reserva paga");
        // Aninhado, e nao um campo de nome "log.level": o ECS agrupa por dominio, entao o
        // caminho e /log/level. Um agregador configurado para o campo plano nao acharia nada.
        assertThat(linha.at("/log/level").asText()).isEqualTo("INFO");
        // O padrao que o coletor reconhece. Sem ele os campos existiriam, e nenhum agregador
        // saberia que aquilo e um log ECS.
        assertThat(linha.at("/ecs/version").asText()).isNotBlank();
    }

    /**
     * O ganho concreto do formato.
     *
     * <p>Em texto, o {@code requestId} vive num prefixo — {@code [abc123 def456]} — e achar uma
     * requisicao no agregador exige expressao regular sobre como a linha foi montada. Virando
     * campo, a busca e por chave, e continua funcionando se o padrao do log mudar amanha.
     */
    @Test
    @DisplayName("o MDC vira campo consultavel, e nao prefixo a ser extraido por regex")
    void deveExporOMdcComoCampo() throws Exception {
        JsonNode linha = codificar("reserva criada",
                Map.of("requestId", "req-abc-123", "traceId", "4bf92f3577b34da6"));

        assertThat(linha.get("requestId").asText()).isEqualTo("req-abc-123");
        // Os dois identificadores juntos: o requestId e o que o usuario consegue ditar ao
        // suporte, e o traceId e o que se cola na busca do Jaeger.
        assertThat(linha.get("traceId").asText()).isEqualTo("4bf92f3577b34da6");
    }

    /**
     * Codifica um evento pelo mesmo encoder que a propriedade liga em producao.
     *
     * @param mdc campos de contexto, como o {@code CorrelacaoServletFilter} os deposita
     */
    private static JsonNode codificar(String mensagem, Map<String, String> mdc) throws Exception {
        LoggerContext contexto = new LoggerContext();
        contexto.start();

        StructuredLogEncoder encoder = new StructuredLogEncoder();
        encoder.setFormat("ecs");
        encoder.setContext(contexto);
        // O encoder le o nome do servico do Environment, como faria na aplicacao.
        contexto.putObject(org.springframework.core.env.Environment.class.getName(), ambiente());
        encoder.start();

        LoggingEvent evento = new LoggingEvent();
        evento.setLoggerContext(contexto);
        evento.setLoggerName(LogEstruturadoTest.class.getName());
        evento.setLevel(Level.INFO);
        evento.setMessage(mensagem);
        evento.setTimeStamp(System.currentTimeMillis());
        evento.setMDCPropertyMap(mdc);

        String linha = new String(encoder.encode(evento), StandardCharsets.UTF_8);
        encoder.stop();

        return new ObjectMapper().readTree(linha);
    }

    private static MockEnvironment ambiente() {
        MockEnvironment ambiente = new MockEnvironment();
        MutablePropertySources fontes = ambiente.getPropertySources();
        fontes.addFirst(new MapPropertySource("teste",
                Map.of("spring.application.name", "booking-service")));
        return ambiente;
    }
}
