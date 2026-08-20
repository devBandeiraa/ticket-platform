package com.devbandeiraa.bookingservice.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.devbandeiraa.bookingservice.client.EventClient;
import com.devbandeiraa.bookingservice.client.EventSnapshot;
import com.devbandeiraa.bookingservice.domain.BookingStatus;
import com.devbandeiraa.bookingservice.exception.EventServiceIndisponivelException;
import com.devbandeiraa.bookingservice.exception.EventoNaoDisponivelException;
import com.devbandeiraa.bookingservice.repository.BookingRepository;
import com.devbandeiraa.bookingservice.repository.EventInventoryRepository;
import com.devbandeiraa.bookingservice.support.GeradorDeToken;
import com.devbandeiraa.bookingservice.support.PostgresContainerConfig;
import com.devbandeiraa.bookingservice.support.RedisContainerConfig;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Testes do fluxo de reserva.
 *
 * <p>O {@code EventClient} e simulado: o que se verifica aqui e a regra de reserva, e um
 * event-service real no meio tornaria os testes dependentes de outro servico no ar. A traducao
 * das respostas HTTP daquele servico ja e coberta em {@code EventClientTest}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({PostgresContainerConfig.class, RedisContainerConfig.class})
@ActiveProfiles("test")
class ReservaIntegrationTest {

    private static final BigDecimal PRECO = new BigDecimal("150.00");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private EventInventoryRepository estoqueRepository;

    @MockitoBean
    private EventClient eventClient;

    private UUID eventoId;
    private UUID usuarioId;
    private String tokenDoUsuario;

    @BeforeEach
    void limparEstado() {
        bookingRepository.deleteAllInBatch();
        estoqueRepository.deleteAllInBatch();

        eventoId = UUID.randomUUID();
        usuarioId = UUID.randomUUID();
        tokenDoUsuario = GeradorDeToken.deUsuario(usuarioId);
    }

    // ---------- criacao ----------

    @Test
    @DisplayName("cria a reserva, segura o estoque e devolve 201 com o local do recurso")
    void deveCriarReserva() throws Exception {
        eventoComCapacidade(10);

        mockMvc.perform(reservar(2, "chave-1", tokenDoUsuario))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, org.hamcrest.Matchers.startsWith("/bookings/")))
                .andExpect(jsonPath("$.status").value(BookingStatus.PENDING.name()))
                .andExpect(jsonPath("$.quantity").value(2))
                .andExpect(jsonPath("$.userId").value(usuarioId.toString()))
                .andExpect(jsonPath("$.expiresAt").exists());

        assertThat(estoqueRepository.findById(eventoId).orElseThrow().getReservedTickets())
                .isEqualTo(2);
    }

    @Test
    @DisplayName("o preco gravado e o do estoque, e nao um valor vindo do cliente")
    void deveGravarSnapshotDoPreco() throws Exception {
        eventoComCapacidade(10);

        mockMvc.perform(reservar(3, "chave-preco", tokenDoUsuario))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.unitPrice").value(150.00))
                // Total calculado no servidor a partir do unitario: um total vindo pronto
                // poderia discordar da multiplicacao.
                .andExpect(jsonPath("$.totalPrice").value(450.00));
    }

    @Test
    @DisplayName("o event-service e consultado uma unica vez por evento")
    void deveHidratarUmaVezSo() throws Exception {
        eventoComCapacidade(10);

        mockMvc.perform(reservar(1, "chave-a", tokenDoUsuario)).andExpect(status().isCreated());
        mockMvc.perform(reservar(1, "chave-b", tokenDoUsuario)).andExpect(status().isCreated());
        mockMvc.perform(reservar(1, "chave-c", tokenDoUsuario)).andExpect(status().isCreated());

        // Depois da hidratacao, as reservas do evento nao dependem mais do event-service no ar.
        verify(eventClient, times(1)).buscarPublicado(eventoId);
    }

    // ---------- idempotencia ----------

    @Test
    @DisplayName("repetir a mesma chave devolve a reserva original, sem reservar de novo")
    void deveSerIdempotente() throws Exception {
        eventoComCapacidade(10);

        String primeiraResposta = mockMvc.perform(reservar(2, "mesma-chave", tokenDoUsuario))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        // 200, e nao 201: nada novo foi criado nesta segunda chamada.
        String segundaResposta = mockMvc.perform(reservar(2, "mesma-chave", tokenDoUsuario))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(segundaResposta).isEqualTo(primeiraResposta);
        assertThat(bookingRepository.count()).isEqualTo(1);
        assertThat(estoqueRepository.findById(eventoId).orElseThrow().getReservedTickets())
                .isEqualTo(2);
    }

    @Test
    @DisplayName("a chave de idempotencia so vale dentro do proprio usuario")
    void chaveNaoDeveVazarEntreUsuarios() throws Exception {
        eventoComCapacidade(10);
        UUID outroUsuarioId = UUID.randomUUID();

        mockMvc.perform(reservar(1, "chave-compartilhada", tokenDoUsuario))
                .andExpect(status().isCreated());

        // Mesma chave, outro usuario: precisa gerar uma reserva propria. Se a chave fosse
        // global, este usuario receberia a reserva alheia em vez da sua.
        mockMvc.perform(reservar(1, "chave-compartilhada", GeradorDeToken.deUsuario(outroUsuarioId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value(outroUsuarioId.toString()));

        assertThat(bookingRepository.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("sem o cabecalho Idempotency-Key a reserva e recusada")
    void deveExigirChaveDeIdempotencia() throws Exception {
        eventoComCapacidade(10);

        mockMvc.perform(post("/bookings")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenDoUsuario)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(1)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_IDEMPOTENCY_KEY"));

        assertThat(bookingRepository.count()).isZero();
    }

    // ---------- limites de estoque ----------

    @Test
    @DisplayName("pedido maior que o disponivel recebe 409 SOLD_OUT")
    void deveRecusarQuandoNaoCabe() throws Exception {
        eventoComCapacidade(5);

        mockMvc.perform(reservar(6, "chave-grande", tokenDoUsuario))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("SOLD_OUT"));

        // Nada foi reservado: o UPDATE condicional nao afetou linha nenhuma.
        assertThat(estoqueRepository.findById(eventoId).orElseThrow().getReservedTickets()).isZero();
        assertThat(bookingRepository.count()).isZero();
    }

    @Test
    @DisplayName("da para reservar exatamente o ultimo ingresso, e nao mais que isso")
    void deveReservarAteOUltimo() throws Exception {
        eventoComCapacidade(3);

        mockMvc.perform(reservar(3, "leva-tudo", tokenDoUsuario)).andExpect(status().isCreated());
        mockMvc.perform(reservar(1, "sobrou-nada", tokenDoUsuario))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("SOLD_OUT"));
    }

    // ---------- dependencia do event-service ----------

    @Test
    @DisplayName("evento inexistente ou nao publicado devolve 404")
    void deveRecusarEventoIndisponivel() throws Exception {
        when(eventClient.buscarPublicado(eventoId))
                .thenThrow(new EventoNaoDisponivelException(eventoId));

        mockMvc.perform(reservar(1, "chave-x", tokenDoUsuario))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("EVENT_NOT_AVAILABLE"));
    }

    @Test
    @DisplayName("event-service fora do ar devolve 503, e nao 404")
    void deveDevolver503QuandoEventServiceCai() throws Exception {
        when(eventClient.buscarPublicado(eventoId))
                .thenThrow(new EventServiceIndisponivelException(eventoId, new RuntimeException("timeout")));

        mockMvc.perform(reservar(1, "chave-y", tokenDoUsuario))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("EVENT_SERVICE_UNAVAILABLE"));
    }

    // ---------- autorizacao e validacao ----------

    @Test
    @DisplayName("reservar exige autenticacao")
    void deveExigirToken() throws Exception {
        mockMvc.perform(post("/bookings")
                        .header("Idempotency-Key", "chave-sem-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(1)))
                .andExpect(status().isUnauthorized());

        verify(eventClient, never()).buscarPublicado(any());
    }

    @Test
    @DisplayName("quantidade zero ou negativa e recusada na validacao")
    void deveValidarQuantidade() throws Exception {
        mockMvc.perform(reservar(0, "chave-zero", tokenDoUsuario))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fields.quantity").exists());
    }

    // ---------- consultas ----------

    @Test
    @DisplayName("cada usuario ve apenas as proprias reservas")
    void deveListarSomenteAsProprias() throws Exception {
        eventoComCapacidade(10);
        String tokenDeOutro = GeradorDeToken.deUsuarioComum();

        mockMvc.perform(reservar(1, "minha", tokenDoUsuario)).andExpect(status().isCreated());
        mockMvc.perform(reservar(1, "dele", tokenDeOutro)).andExpect(status().isCreated());

        mockMvc.perform(get("/bookings/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenDoUsuario))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].userId").value(usuarioId.toString()));
    }

    @Test
    @DisplayName("a reserva de outra pessoa aparece como inexistente, e nao como proibida")
    void naoDeveExporReservaAlheia() throws Exception {
        eventoComCapacidade(10);

        String resposta = mockMvc.perform(reservar(1, "minha", tokenDoUsuario))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String reservaId = idDe(resposta);

        // 404 e nao 403: um 403 confirmaria que aquele id existe, e bastaria varrer ids para
        // mapear o volume de reservas da plataforma.
        mockMvc.perform(get("/bookings/" + reservaId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + GeradorDeToken.deUsuarioComum()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("BOOKING_NOT_FOUND"));
    }

    @Test
    @DisplayName("o administrador enxerga qualquer reserva")
    void adminDeveEnxergarQualquerReserva() throws Exception {
        eventoComCapacidade(10);

        String resposta = mockMvc.perform(reservar(1, "minha", tokenDoUsuario))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        mockMvc.perform(get("/bookings/" + idDe(resposta))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + GeradorDeToken.deAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(usuarioId.toString()));
    }

    // ---------- apoio ----------

    private void eventoComCapacidade(int capacidade) {
        when(eventClient.buscarPublicado(eventoId))
                .thenReturn(new EventSnapshot(eventoId, capacidade, PRECO));
    }

    private org.springframework.test.web.servlet.RequestBuilder reservar(
            int quantidade, String chave, String token) {

        return post("/bookings")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .header("Idempotency-Key", chave)
                .contentType(MediaType.APPLICATION_JSON)
                .content(corpo(quantidade));
    }

    private String corpo(int quantidade) {
        return """
                {"eventId": "%s", "quantity": %d}
                """.formatted(eventoId, quantidade);
    }

    private String idDe(String respostaJson) {
        return com.jayway.jsonpath.JsonPath.read(respostaJson, "$.id");
    }
}
