package com.devbandeiraa.bookingservice.integration;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.devbandeiraa.bookingservice.client.EventClient;
import com.devbandeiraa.bookingservice.client.EventSnapshot;
import com.devbandeiraa.bookingservice.domain.Booking;
import com.devbandeiraa.bookingservice.domain.BookingStatus;
import com.devbandeiraa.bookingservice.domain.EventInventory;
import com.devbandeiraa.bookingservice.repository.BookingRepository;
import com.devbandeiraa.bookingservice.repository.EventInventoryRepository;
import com.devbandeiraa.bookingservice.support.GeradorDeToken;
import com.devbandeiraa.bookingservice.support.PostgresContainerConfig;
import com.devbandeiraa.bookingservice.support.RedisContainerConfig;
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
@Import({PostgresContainerConfig.class, RedisContainerConfig.class})
@ActiveProfiles("test")
class ConsultasIntegrationTest {

    private static final BigDecimal PRECO = new BigDecimal("150.00");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private EventInventoryRepository estoqueRepository;

    @Autowired
    private TransactionTemplate transacao;

    @MockitoBean
    private EventClient eventClient;

    private UUID eventoId;

    @BeforeEach
    void limparEstado() {
        bookingRepository.deleteAllInBatch();
        estoqueRepository.deleteAllInBatch();
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

    @Test
    @DisplayName("evento nunca visto e hidratado na primeira consulta de disponibilidade")
    void deveHidratarNaPrimeiraConsulta() throws Exception {
        when(eventClient.buscarPublicado(eventoId))
                .thenReturn(new EventSnapshot(eventoId, 42, PRECO));

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
        estoqueRepository.saveAndFlush(EventInventory.hidratado(eventoId, capacidade, PRECO));
    }

    private Booking reservaPendente(int quantidade) {
        return transacao.execute(status -> {
            estoqueRepository.reservar(eventoId, quantidade);
            return bookingRepository.saveAndFlush(Booking.pendente(
                    eventoId, UUID.randomUUID(), quantidade, PRECO,
                    Instant.now().plus(10, ChronoUnit.MINUTES), "chave-" + UUID.randomUUID()));
        });
    }
}
