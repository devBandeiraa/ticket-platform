package com.devbandeiraa.bookingservice.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.devbandeiraa.bookingservice.config.SecurityConfig;
import com.devbandeiraa.bookingservice.controller.BookingController;
import com.devbandeiraa.bookingservice.exception.AssentosIndisponiveisException;
import com.devbandeiraa.bookingservice.exception.ChaveDeIdempotenciaInvalidaException;
import com.devbandeiraa.bookingservice.exception.EstoqueEsgotadoException;
import com.devbandeiraa.bookingservice.exception.EventoNaoDisponivelException;
import com.devbandeiraa.bookingservice.exception.GlobalExceptionHandler;
import com.devbandeiraa.bookingservice.lock.LockIndisponivelException;
import com.devbandeiraa.bookingservice.service.BookingService;
import com.devbandeiraa.bookingservice.support.GeradorDeToken;
import com.devbandeiraa.shared.security.SharedSecurityAutoConfiguration;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Fatia web do {@code BookingController} — sem banco, sem Redis, sem Docker.
 *
 * <h2>Por que existe, se ja ha testes de integracao do mesmo endpoint</h2>
 *
 * <p>Nao substitui o {@code ReservaIntegrationTest}: aquele prova que a reserva acontece de
 * verdade, contra PostgreSQL e Redis reais, e nenhum mock provaria isso. O que esta fatia
 * cobre e o que <em>nao</em> depende de banco algum — validacao de entrada, traducao de excecao
 * em codigo HTTP e formato da resposta de erro.
 *
 * <p>A diferenca pratica e o custo. Todo teste de controller deste projeto exigia Testcontainers,
 * e portanto um Docker de pe; um matriz exaustiva de validacao custaria dezenas de segundos e um
 * daemon rodando. Aqui ela custa milissegundos e roda em qualquer maquina — inclusive naquelas
 * em que o Docker esta fora do ar, situacao nada teorica.
 *
 * <p>O {@code SecurityConfig} e o {@code GlobalExceptionHandler} entram de proposito: sem eles a
 * fatia testaria um controller que nao existe em producao — um sem autenticacao e sem traducao
 * de erro.
 */
@WebMvcTest(BookingController.class)
// A autoconfiguracao do shared-security entra explicitamente: uma fatia web nao carrega
// autoconfiguracoes de terceiros, e sem ela faltariam o leitor de JWT e o respondedor de erro
// de que o SecurityConfig depende. Importa-la e o que faz a fatia exercitar a MESMA cadeia de
// filtros que roda em producao, em vez de uma montada so para o teste.
@Import({SharedSecurityAutoConfiguration.class, SecurityConfig.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = {
        "jwt.secret=" + GeradorDeToken.SEGREDO,
        "jwt.issuer=" + GeradorDeToken.EMISSOR,
})
class BookingControllerWebMvcTest {

    private static final UUID EVENTO = UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private BookingService bookingService;

    // ---------- validacao de entrada ----------

    /**
     * A matriz que os testes de integracao nao cobrem por inteiro, porque cada caso custaria
     * um contexto Spring e um container.
     */
    @ParameterizedTest(name = "{1}")
    @CsvSource({
            "0,  quantidade zero e recusada",
            "-1, quantidade negativa e recusada",
    })
    @DisplayName("recusa quantidade invalida com 400 e detalhe por campo")
    void deveRecusarQuantidadeInvalida(int quantidade, String caso) throws Exception {

        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("eventId", EVENTO.toString());
        corpo.put("quantity", quantidade);

        mockMvc.perform(reservar(corpo))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fields.quantity").isNotEmpty());

        // A requisicao nao pode ter alcancado a regra de negocio: entrada invalida para na borda.
        verify(bookingService, never()).criar(any(), any(), anyString());
    }

    @Test
    @DisplayName("recusa reserva sem evento com 400")
    void deveRecusarSemEvento() throws Exception {
        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("quantity", 1);

        mockMvc.perform(reservar(corpo))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.eventId").isNotEmpty());
    }

    /**
     * O teto de lugares por reserva existe para que uma lista enorme nao vire um {@code IN} com
     * milhares de ids e um lock sobre metade da casa. Verificar isso por integracao exigiria
     * montar onze assentos de verdade.
     */
    @Test
    @DisplayName("recusa mais de dez lugares escolhidos com 400")
    void deveRecusarMaisDeDezLugares() throws Exception {
        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("eventId", EVENTO.toString());
        corpo.put("seatIds", Stream.generate(UUID::randomUUID).limit(11).map(UUID::toString).toList());
        corpo.put("quantity", 11);

        mockMvc.perform(reservar(corpo))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.seatIds").isNotEmpty());

        verify(bookingService, never()).criar(any(), any(), anyString());
    }

    // ---------- traducao de excecao em resposta ----------

    /**
     * Cada excecao de dominio tem um codigo proprio, e a distincao nao e decorativa: a tela
     * decide pelo codigo o que dizer a quem esta comprando. Um mapeamento errado faz a
     * interface dar o conselho oposto ao correto.
     */
    @Test
    @DisplayName("assentos tomados viram 409 SEATS_TAKEN, distinto de esgotado")
    void deveTraduzirAssentosTomados() throws Exception {
        when(bookingService.criar(any(), any(), anyString()))
                .thenThrow(new AssentosIndisponiveisException(EVENTO, List.of("Plateia A1")));

        mockMvc.perform(reservar(corpoValido()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("SEATS_TAKEN"))
                // A mensagem nomeia os lugares: e o que a tela mostra a quem perdeu a escolha.
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers
                        .containsString("Plateia A1")));
    }

    @Test
    @DisplayName("estoque esgotado vira 409 SOLD_OUT")
    void deveTraduzirEsgotado() throws Exception {
        when(bookingService.criar(any(), any(), anyString()))
                .thenThrow(new EstoqueEsgotadoException(EVENTO, 2));

        mockMvc.perform(reservar(corpoValido()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("SOLD_OUT"));
    }

    @Test
    @DisplayName("lock indisponivel vira 409 LOCK_TIMEOUT, e nao 503")
    void deveTraduzirLockIndisponivel() throws Exception {
        when(bookingService.criar(any(), any(), anyString()))
                .thenThrow(new LockIndisponivelException("lock:event:" + EVENTO, 3));

        // 409 e nao 503 porque o servico esta saudavel — o que faltou foi a vez na fila. Um 503
        // convidaria um cliente a tratar como indisponibilidade e desistir.
        mockMvc.perform(reservar(corpoValido()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("LOCK_TIMEOUT"));
    }

    @Test
    @DisplayName("evento indisponivel vira 404 EVENT_NOT_AVAILABLE")
    void deveTraduzirEventoIndisponivel() throws Exception {
        when(bookingService.criar(any(), any(), anyString()))
                .thenThrow(new EventoNaoDisponivelException(EVENTO));

        mockMvc.perform(reservar(corpoValido()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("EVENT_NOT_AVAILABLE"));
    }

    @Test
    @DisplayName("chave de idempotencia ausente vira 400, e nao erro do framework")
    void deveTraduzirChaveInvalida() throws Exception {
        when(bookingService.criar(any(), any(), any()))
                .thenThrow(new ChaveDeIdempotenciaInvalidaException("o cabecalho e obrigatorio"));

        // Sem o cabecalho. O binding usa required = false justamente para que a ausencia chegue
        // ao servico e vire o erro padrao da plataforma, em vez de uma excecao do Spring que
        // escaparia do formato de erro da API.
        mockMvc.perform(post("/bookings")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + GeradorDeToken.deUsuarioComum())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(corpoValido())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_IDEMPOTENCY_KEY"));
    }

    // ---------- autenticacao ----------

    @Test
    @DisplayName("sem token, reservar devolve 401 no formato de erro da plataforma")
    void deveExigirToken() throws Exception {
        mockMvc.perform(post("/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "chave-1")
                        .content(objectMapper.writeValueAsString(corpoValido())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"))
                // O corpo segue o mesmo formato de qualquer outro erro: um cliente nao precisa
                // de um caminho especial para ler falha de autenticacao.
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.path").value("/bookings"));
    }

    @Test
    @DisplayName("token malformado devolve 401, e nao 500")
    void deveRecusarTokenMalformado() throws Exception {
        // Uma string que nao e JWT algum. O filtro precisa tratar isso como falha de
        // autenticacao; deixar a excecao de parsing subir viraria erro de servidor sobre uma
        // entrada que o cliente controla.
        //
        // O codigo e UNAUTHORIZED, e nao INVALID_TOKEN: este ultimo pertence ao gateway, que
        // distingue "token invalido" de "sem token" porque recusa na borda antes de encaminhar.
        // Dentro do servico as duas situacoes dao no mesmo — nao ha identidade estabelecida —, e
        // inventar aqui um codigo diferente faria a API contar duas historias para o mesmo fato.
        mockMvc.perform(post("/bookings")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer nao-e-um-jwt")
                        .header("Idempotency-Key", "chave-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(corpoValido())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
    }

    // ---------- auxiliares ----------

    private Map<String, Object> corpoValido() {
        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("eventId", EVENTO.toString());
        corpo.put("quantity", 2);
        return corpo;
    }

    private org.springframework.test.web.servlet.RequestBuilder reservar(Map<String, Object> corpo)
            throws Exception {
        return post("/bookings")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + GeradorDeToken.deUsuarioComum())
                .header("Idempotency-Key", "chave-" + UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(corpo));
    }
}
