package com.devbandeiraa.bookingservice.integration;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.devbandeiraa.bookingservice.client.EventClient;
import com.devbandeiraa.bookingservice.domain.Booking;
import com.devbandeiraa.bookingservice.domain.Valores;
import com.devbandeiraa.bookingservice.domain.BookingStatus;
import com.devbandeiraa.bookingservice.repository.BookingRepository;
import com.devbandeiraa.bookingservice.repository.BookingSeatRepository;
import com.devbandeiraa.bookingservice.repository.EventSeatRepository;
import com.devbandeiraa.bookingservice.support.GeradorDeToken;
import com.devbandeiraa.bookingservice.support.AssentosDeTeste;
import com.devbandeiraa.bookingservice.support.PlantaDeTeste;
import com.devbandeiraa.bookingservice.support.TestcontainersConfig;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

/** Testes da disponibilidade publica e da listagem administrativa. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
@ActiveProfiles("test")
class ConsultasIntegrationTest {

    private static final BigDecimal PRECO = new BigDecimal("150.00");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private EventSeatRepository assentoRepository;

    @Autowired
    private BookingSeatRepository bookingSeatRepository;

    @Autowired
    private TransactionTemplate transacao;

    @MockitoBean
    private EventClient eventClient;

    private UUID eventoId;

    @BeforeEach
    void limparEstado() {
        bookingRepository.deleteAllInBatch();
        bookingSeatRepository.deleteAllInBatch();
        assentoRepository.deleteAllInBatch();
        eventoId = UUID.randomUUID();
    }

    // ---------- disponibilidade ----------

    @Test
    @DisplayName("disponibilidade e publica: um visitante sem conta consegue consultar")
    void disponibilidadeDeveSerPublica() throws Exception {
        estoqueHidratado(10);
        reservaPendente(3);

        // Sem cabecalho Authorization: quem ainda nao tem conta precisa ver se vale a pena criar.
        mockMvc.perform(get("/events/" + eventoId + "/availability"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventId").value(eventoId.toString()))
                .andExpect(jsonPath("$.total").value(10))
                .andExpect(jsonPath("$.reserved").value(3))
                .andExpect(jsonPath("$.available").value(7));
    }

    /**
     * O mapa e publico, e este teste chama o endpoint DE VERDADE — sem token.
     *
     * <p>Na Fase 18 a documentacao ja anunciava o mapa como aberto, e a especificacao OpenAPI
     * concordava, enquanto o {@code SecurityConfig} exigia token. Um teste que so verificasse a
     * especificacao passaria; quem abrisse a tela levava {@code 401}. A licao: documentacao e
     * configuracao sao duas afirmacoes distintas, e cada uma precisa ser verificada onde vive.
     */
    @Test
    @DisplayName("mapa de assentos e publico: um visitante sem conta consegue ver os lugares")
    void mapaDeAssentosDeveSerPublico() throws Exception {
        estoqueHidratado(10);

        mockMvc.perform(get("/events/" + eventoId + "/seats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventId").value(eventoId.toString()))
                .andExpect(jsonPath("$.seats.length()").value(10))
                .andExpect(jsonPath("$.seats[0].status").value("FREE"))
                .andExpect(jsonPath("$.seats[0].label").isNotEmpty());
    }

    @Test
    @DisplayName("o mapa mostra como ocupado o lugar que ja foi reservado")
    void mapaDeveMarcarOcupados() throws Exception {
        estoqueHidratado(10);
        reservaPendente(3);

        mockMvc.perform(get("/events/" + eventoId + "/seats"))
                .andExpect(status().isOk())
                // Tres lugares saem de FREE: e o que a tela usa para desabilita-los.
                .andExpect(jsonPath("$.seats[?(@.status == 'RESERVED')]")
                        .value(org.hamcrest.Matchers.hasSize(3)))
                .andExpect(jsonPath("$.seats[?(@.status == 'FREE')]")
                        .value(org.hamcrest.Matchers.hasSize(7)));
    }

    @Test
    @DisplayName("evento nunca visto e hidratado na primeira consulta de disponibilidade")
    void deveHidratarNaPrimeiraConsulta() throws Exception {
        when(eventClient.buscarPublicado(eventoId))
                .thenReturn(PlantaDeTeste.eventoCom(eventoId, 42, PRECO));

        mockMvc.perform(get("/events/" + eventoId + "/availability"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(42))
                .andExpect(jsonPath("$.available").value(42));
    }

    @Test
    @DisplayName("disponibilidade de evento inexistente devolve 404")
    void deveDevolver404ParaEventoInexistente() throws Exception {
        when(eventClient.buscarPublicado(eventoId))
                .thenThrow(new com.devbandeiraa.bookingservice.exception
                        .EventoNaoDisponivelException(eventoId));

        mockMvc.perform(get("/events/" + eventoId + "/availability"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("EVENT_NOT_AVAILABLE"));
    }

    // ---------- listagem administrativa ----------

    @Test
    @DisplayName("a listagem administrativa exige papel ADMIN")
    void listagemAdministrativaDeveExigirAdmin() throws Exception {
        mockMvc.perform(get("/admin/bookings")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + GeradorDeToken.deUsuarioComum()))
                .andExpect(status().isForbidden());

        // Sem token nenhum e 401, e nao 403: a distincao entre "nao sei quem e voce" e
        // "sei quem e voce, e voce nao pode" importa para o cliente saber se deve renovar
        // o token ou desistir.
        mockMvc.perform(get("/admin/bookings"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("o admin ve reservas de todos os usuarios")
    void adminDeveVerTodasAsReservas() throws Exception {
        estoqueHidratado(50);
        reservaPendente(1);
        reservaPendente(2);

        mockMvc.perform(get("/admin/bookings")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + GeradorDeToken.deAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    @DisplayName("os filtros de evento e situacao se combinam")
    void deveFiltrarPorEventoESituacao() throws Exception {
        estoqueHidratado(50);
        Booking pendente = reservaPendente(1);
        reservaPendente(2);
        transacao.executeWithoutResult(status ->
                bookingRepository.cancelar(pendente.getId()));

        UUID outroEvento = UUID.randomUUID();

        mockMvc.perform(get("/admin/bookings")
                        .param("status", BookingStatus.PENDING.name())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + GeradorDeToken.deAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));

        // Filtro por evento sem reservas: a combinacao precisa devolver vazio, e nao tudo.
        mockMvc.perform(get("/admin/bookings")
                        .param("eventId", outroEvento.toString())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + GeradorDeToken.deAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    @DisplayName("status invalido na query string e erro do cliente, e nao 500")
    void deveRecusarStatusInvalido() throws Exception {
        mockMvc.perform(get("/admin/bookings")
                        .param("status", "INEXISTENTE")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + GeradorDeToken.deAdmin()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_PARAMETER"));
    }

    // ---------- apoio ----------

    private void estoqueHidratado(int capacidade) {
        AssentosDeTeste.criarCasa(assentoRepository, eventoId, capacidade, PRECO);
    }

    private Booking reservaPendente(int quantidade) {
        return transacao.execute(status -> {
            Booking reserva = bookingRepository.saveAndFlush(Booking.pendente(
                    eventoId, UUID.randomUUID(), quantidade, valoresDe(quantidade),
                    Instant.now().plus(10, ChronoUnit.MINUTES), "chave-" + UUID.randomUUID()));

            AssentosDeTeste.ocuparPara(assentoRepository, eventoId, quantidade, reserva.getId());
            return reserva;
        });
    }

    /**
     * Os valores da reserva: soma dos lugares, taxa e total.
     *
     * <p>Taxa ZERO de proposito. Estes testes verificam ciclo de vida, outbox e consulta, e
     * nenhum deles tem opiniao sobre taxa. Com zero, o total continua sendo a soma dos lugares e
     * as assercoes sobre valor seguem valendo o que valiam. Quem exercita a taxa e o teste que
     * existe para isso.
     */
    private static Valores valoresDe(int quantidade) {
        return Valores.de(PRECO.multiply(BigDecimal.valueOf(quantidade)), BigDecimal.ZERO);
    }

}
