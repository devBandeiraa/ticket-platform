package com.devbandeiraa.eventservice.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.devbandeiraa.eventservice.domain.EventStatus;
import com.devbandeiraa.eventservice.repository.EventRepository;
import com.devbandeiraa.eventservice.support.GeradorDeToken;
import com.devbandeiraa.eventservice.support.PostgresContainerConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Testes de integracao do CRUD administrativo e da autorizacao, contra um PostgreSQL real. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresContainerConfig.class)
class AdminEventCrudIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private EventRepository eventRepository;

    @BeforeEach
    void limparEstado() {
        eventRepository.deleteAllInBatch();
    }

    // ---------- autorizacao ----------

    @Test
    @DisplayName("sem token, a administracao devolve 401")
    void deveExigirAutenticacao() throws Exception {
        mockMvc.perform(get("/admin/events"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("com token de usuario comum, a administracao devolve 403")
    void deveRecusarUsuarioComum() throws Exception {
        mockMvc.perform(get("/admin/events")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + GeradorDeToken.deUsuarioComum()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("token assinado com outra chave e recusado, mesmo dizendo ser ADMIN")
    void deveRecusarTokenForjado() throws Exception {
        // O papel dentro do token diz ADMIN, mas a assinatura nao confere com o segredo do
        // servico. Se este teste passasse a devolver 200, qualquer um seria administrador.
        mockMvc.perform(get("/admin/events")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + GeradorDeToken.assinadoComOutraChave()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("token expirado e recusado")
    void deveRecusarTokenExpirado() throws Exception {
        mockMvc.perform(get("/admin/events")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + GeradorDeToken.expirado()))
                .andExpect(status().isUnauthorized());
    }

    // ---------- criacao ----------

    @Test
    @DisplayName("admin cria evento, que nasce como rascunho")
    void deveCriarEventoComoRascunho() throws Exception {
        mockMvc.perform(criar(corpoValido()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.name").value("Show de Rock"))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.totalTickets").value(500));
    }

    @Test
    @DisplayName("o autor registrado e o dono do token, ignorando qualquer id enviado no corpo")
    void deveRegistrarAutorDoToken() throws Exception {
        UUID idDoAdmin = UUID.randomUUID();

        Map<String, Object> corpoComAutorFalsificado = corpoValido();
        corpoComAutorFalsificado.put("createdBy", UUID.randomUUID().toString());

        mockMvc.perform(post("/admin/events")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + GeradorDeToken.deAdmin(idDoAdmin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(corpoComAutorFalsificado)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.createdBy").value(idDoAdmin.toString()));
    }

    @Test
    @DisplayName("recusa data no passado")
    void deveRecusarDataNoPassado() throws Exception {
        Map<String, Object> corpo = corpoValido();
        corpo.put("eventDate", Instant.now().minus(1, ChronoUnit.DAYS).toString());

        mockMvc.perform(criar(corpo))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fields.eventDate").isNotEmpty());
    }

    @Test
    @DisplayName("recusa quantidade de ingressos igual a zero")
    void deveRecusarZeroIngressos() throws Exception {
        Map<String, Object> corpo = corpoValido();
        corpo.put("totalTickets", 0);

        mockMvc.perform(criar(corpo))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.totalTickets").isNotEmpty());
    }

    @Test
    @DisplayName("recusa preco negativo")
    void deveRecusarPrecoNegativo() throws Exception {
        Map<String, Object> corpo = corpoValido();
        corpo.put("price", new BigDecimal("-1.00"));

        mockMvc.perform(criar(corpo))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.price").isNotEmpty());
    }

    // ---------- ciclo de vida ----------

    @Test
    @DisplayName("publicar torna o evento visivel no catalogo publico")
    void devePublicarEvento() throws Exception {
        String id = criarEObterId();

        mockMvc.perform(post("/admin/events/" + id + "/publish").header(HttpHeaders.AUTHORIZATION, autorizacaoAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"));

        mockMvc.perform(get("/events/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id));
    }

    @Test
    @DisplayName("alterar atualiza os dados do evento")
    void deveAlterarEvento() throws Exception {
        String id = criarEObterId();

        Map<String, Object> alteracao = corpoValido();
        alteracao.put("name", "Show de Jazz");
        alteracao.put("price", new BigDecimal("99.90"));

        mockMvc.perform(put("/admin/events/" + id)
                        .header(HttpHeaders.AUTHORIZATION, autorizacaoAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(alteracao)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Show de Jazz"))
                .andExpect(jsonPath("$.price").value(99.90));
    }

    @Test
    @DisplayName("cancelar e exclusao logica: o registro permanece no banco")
    void deveCancelarSemApagar() throws Exception {
        String id = criarEObterId();

        mockMvc.perform(delete("/admin/events/" + id).header(HttpHeaders.AUTHORIZATION, autorizacaoAdmin()))
                .andExpect(status().isNoContent());

        // O registro continua existindo — reservas futuras apontarao para ele.
        assertThat(eventRepository.findById(UUID.fromString(id)))
                .isPresent()
                .get()
                .satisfies(evento -> assertThat(evento.getStatus()).isEqualTo(EventStatus.CANCELLED));
    }

    @Test
    @DisplayName("evento cancelado nao pode mais ser alterado")
    void naoDeveAlterarEventoCancelado() throws Exception {
        String id = criarEObterId();
        mockMvc.perform(delete("/admin/events/" + id).header(HttpHeaders.AUTHORIZATION, autorizacaoAdmin()));

        mockMvc.perform(put("/admin/events/" + id)
                        .header(HttpHeaders.AUTHORIZATION, autorizacaoAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(corpoValido())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("EVENT_NOT_EDITABLE"));
    }

    @Test
    @DisplayName("admin enxerga rascunhos, que o catalogo publico esconde")
    void adminDeveEnxergarRascunhos() throws Exception {
        criarEObterId();

        mockMvc.perform(get("/admin/events").header(HttpHeaders.AUTHORIZATION, autorizacaoAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));

        mockMvc.perform(get("/events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    @DisplayName("evento inexistente devolve 404")
    void deveDevolverNaoEncontrado() throws Exception {
        mockMvc.perform(get("/admin/events/" + UUID.randomUUID()).header(HttpHeaders.AUTHORIZATION, autorizacaoAdmin()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("EVENT_NOT_FOUND"));
    }

    @Test
    @DisplayName("id malformado na URL e erro do cliente, nao falha do servidor")
    void deveTratarIdMalformado() throws Exception {
        mockMvc.perform(get("/admin/events/nao-e-um-uuid").header(HttpHeaders.AUTHORIZATION, autorizacaoAdmin()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_PARAMETER"));
    }

    // ---------- auxiliares ----------

    private Map<String, Object> corpoValido() {
        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("name", "Show de Rock");
        corpo.put("description", "Uma noite inesquecivel");
        corpo.put("venue", "Estadio Municipal");
        corpo.put("eventDate", Instant.now().plus(30, ChronoUnit.DAYS).toString());
        corpo.put("totalTickets", 500);
        corpo.put("price", new BigDecimal("150.00"));
        return corpo;
    }

    private MockHttpServletRequestBuilder criar(Map<String, Object> corpo) throws Exception {
        return post("/admin/events")
                .header(HttpHeaders.AUTHORIZATION, autorizacaoAdmin())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(corpo));
    }

    private String criarEObterId() throws Exception {
        String corpo = mockMvc.perform(criar(corpoValido()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(corpo).get("id").asText();
    }

    /** Cabecalho Authorization com um token de ADMIN valido. */
    private String autorizacaoAdmin() {
        return "Bearer " + GeradorDeToken.deAdmin();
    }
}
