package com.devbandeiraa.eventservice.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.devbandeiraa.eventservice.config.SecurityConfig;
import com.devbandeiraa.eventservice.controller.AdminEventController;
import com.devbandeiraa.eventservice.domain.EventStatus;
import com.devbandeiraa.eventservice.dto.response.EventDetailResponse;
import com.devbandeiraa.eventservice.exception.GlobalExceptionHandler;
import com.devbandeiraa.eventservice.service.EventService;
import com.devbandeiraa.eventservice.support.GeradorDeToken;
import com.devbandeiraa.shared.security.SharedSecurityAutoConfiguration;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
import org.springframework.test.web.servlet.RequestBuilder;

/**
 * Fatia web da administracao de eventos — sem banco, sem Docker.
 *
 * <p>O {@code AdminEventCrudIntegrationTest} continua provando o que depende de banco: que o
 * evento nasce rascunho, que cancelar e exclusao logica, que o layout trava apos a publicacao.
 * O que esta fatia cobre e a borda — a matriz de validacao do corpo, que nao toca em persistencia
 * alguma e cuja verificacao por integracao custaria um container por caso.
 *
 * <p>Vale para o que a fatia NAO deve fazer: nada aqui verifica regra de negocio. O
 * {@code EventService} e um mock, e um teste que afirmasse algo sobre o que ele faz estaria
 * testando o proprio mock.
 */
@WebMvcTest(AdminEventController.class)
@Import({SharedSecurityAutoConfiguration.class, SecurityConfig.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = {
        "jwt.secret=" + GeradorDeToken.SEGREDO,
        "jwt.issuer=" + GeradorDeToken.EMISSOR,
})
class AdminEventControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private EventService eventService;

    // ---------- autorizacao por prefixo ----------

    @Test
    @DisplayName("sem token, a administracao devolve 401")
    void deveExigirToken() throws Exception {
        mockMvc.perform(post("/admin/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(corpoValido())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
    }

    /**
     * A regra de autorizacao e por prefixo, e nao por anotacao em cada metodo. Esta fatia
     * verifica justamente isso sem subir banco: um usuario autenticado mas sem o papel ADMIN
     * para na cadeia de filtros, antes de o controller existir para ele.
     */
    @Test
    @DisplayName("usuario comum autenticado devolve 403, e nao 401")
    void deveRecusarUsuarioComum() throws Exception {
        mockMvc.perform(post("/admin/events")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + GeradorDeToken.deUsuarioComum())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(corpoValido())))
                // 403 e nao 401: a pessoa se identificou, e o que falta e permissao. Confundir
                // os dois faria o cliente pedir login a quem ja esta logado.
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));

        verify(eventService, never()).criar(any(), any());
    }

    // ---------- matriz de validacao do setor ----------

    /**
     * Os limites do setor existem por razoes concretas, e cada um merece verificacao: filas ou
     * lugares fora da faixa viram uma casa que o navegador nao desenha, e preco negativo nao
     * existe. Por integracao, esta tabela custaria seis contextos e seis containers.
     */
    @ParameterizedTest(name = "{3}")
    @CsvSource({
            "0,    20,  150.00, sem fila alguma",
            "201,  20,  150.00, filas acima do teto",
            "10,   0,   150.00, fila sem lugar",
            "10,   101, 150.00, lugares por fila acima do teto",
            "10,   20,  -1.00,  preco negativo",
    })
    @DisplayName("recusa dimensoes e preco de setor fora da faixa")
    void deveRecusarSetorInvalido(int filas, int lugares, BigDecimal preco, String caso)
            throws Exception {

        Map<String, Object> corpo = corpoValido();
        corpo.put("sectors", List.of(setor("Plateia", preco, filas, lugares)));

        mockMvc.perform(criar(corpo))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));

        verify(eventService, never()).criar(any(), any());
    }

    @Test
    @DisplayName("recusa evento sem setor algum")
    void deveRecusarSemSetores() throws Exception {
        Map<String, Object> corpo = corpoValido();
        corpo.put("sectors", List.of());

        mockMvc.perform(criar(corpo))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.sectors").isNotEmpty());
    }

    @Test
    @DisplayName("recusa data no passado")
    void deveRecusarDataNoPassado() throws Exception {
        Map<String, Object> corpo = corpoValido();
        corpo.put("eventDate", Instant.now().minus(1, ChronoUnit.DAYS).toString());

        mockMvc.perform(criar(corpo))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.eventDate").isNotEmpty());
    }

    @Test
    @DisplayName("recusa capa que nao e endereco http")
    void deveRecusarCapaComEsquemaInvalido() throws Exception {
        Map<String, Object> corpo = corpoValido();
        // `javascript:` num atributo src e o caminho classico de XSS. A validacao de esquema
        // nao e a defesa principal — o React escapa —, mas recusar na entrada evita guardar no
        // banco algo que nenhum caminho legitimo produz.
        corpo.put("imageUrl", "javascript:alert(1)");

        mockMvc.perform(criar(corpo))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.imageUrl").isNotEmpty());
    }

    @Test
    @DisplayName("aceita evento sem capa: arte pendente e estado legitimo")
    void deveAceitarSemCapa() throws Exception {
        // Unico teste da fatia que chega ao servico, e por isso o unico que precisa do retorno
        // configurado. Os demais param na validacao ou na autorizacao, antes do controller.
        when(eventService.criar(any(), any())).thenReturn(respostaQualquer());

        Map<String, Object> corpo = corpoValido();
        corpo.remove("imageUrl");

        mockMvc.perform(criar(corpo))
                .andExpect(status().isCreated())
                .andExpect(header().exists(HttpHeaders.LOCATION));
    }

    /** Resposta minima, so para o controller ter o que serializar e de onde tirar o Location. */
    private static EventDetailResponse respostaQualquer() {
        return new EventDetailResponse(
                UUID.randomUUID(), "Show de Rock", "Uma noite inesquecivel", "Teatro Municipal",
                Instant.now().plus(30, ChronoUnit.DAYS), new BigDecimal("150.00"), 500, null,
                List.of(), EventStatus.DRAFT, UUID.randomUUID(), Instant.now(), Instant.now());
    }

    // ---------- auxiliares ----------

    private Map<String, Object> corpoValido() {
        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("name", "Show de Rock");
        corpo.put("description", "Uma noite inesquecivel");
        corpo.put("venue", "Teatro Municipal");
        corpo.put("eventDate", Instant.now().plus(30, ChronoUnit.DAYS).toString());
        corpo.put("sectors", List.of(setor("Plateia", new BigDecimal("150.00"), 25, 20)));
        corpo.put("imageUrl", "https://cdn.exemplo.test/capa.jpg");
        return corpo;
    }

    private static Map<String, Object> setor(String nome, BigDecimal preco, int filas, int lugares) {
        Map<String, Object> setor = new LinkedHashMap<>();
        setor.put("name", nome);
        setor.put("price", preco);
        setor.put("rowsCount", filas);
        setor.put("seatsPerRow", lugares);
        return setor;
    }

    private RequestBuilder criar(Map<String, Object> corpo) throws Exception {
        return post("/admin/events")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + GeradorDeToken.deAdmin())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(corpo));
    }
}
