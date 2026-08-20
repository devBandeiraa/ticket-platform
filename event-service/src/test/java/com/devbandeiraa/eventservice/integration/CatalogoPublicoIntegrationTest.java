package com.devbandeiraa.eventservice.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.devbandeiraa.eventservice.domain.Event;
import com.devbandeiraa.eventservice.repository.EventRepository;
import com.devbandeiraa.eventservice.support.PostgresContainerConfig;
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
import org.springframework.test.web.servlet.MockMvc;

/**
 * Testes do catalogo publico.
 *
 * <p>Nenhuma requisicao daqui envia token: o catalogo precisa funcionar para um visitante sem
 * conta. O que se verifica em cada caso e que o publico ve exatamente os eventos publicados —
 * nem menos, e sobretudo nem mais.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresContainerConfig.class)
class CatalogoPublicoIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EventRepository eventRepository;

    @BeforeEach
    void limparEstado() {
        eventRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("lista apenas eventos publicados, escondendo rascunhos e cancelados")
    void deveListarApenasPublicados() throws Exception {
        publicado("Show de Rock", 10);
        publicado("Festival de Jazz", 20);
        rascunho("Evento em preparacao");
        cancelado("Evento cancelado");

        mockMvc.perform(get("/events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].name").value("Show de Rock"))
                .andExpect(jsonPath("$.content[1].name").value("Festival de Jazz"));
    }

    @Test
    @DisplayName("ordena por data do evento, do mais proximo ao mais distante")
    void deveOrdenarPorData() throws Exception {
        publicado("Mais distante", 60);
        publicado("Mais proximo", 5);

        mockMvc.perform(get("/events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("Mais proximo"))
                .andExpect(jsonPath("$.content[1].name").value("Mais distante"));
    }

    @Test
    @DisplayName("pagina os resultados e informa o total")
    void devePaginar() throws Exception {
        for (int i = 1; i <= 5; i++) {
            publicado("Evento " + i, i);
        }

        mockMvc.perform(get("/events").param("page", "0").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.first").value(true))
                .andExpect(jsonPath("$.last").value(false));

        mockMvc.perform(get("/events").param("page", "2").param("size", "2"))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.last").value(true));
    }

    @Test
    @DisplayName("limita o tamanho da pagina, mesmo com um valor absurdo na URL")
    void deveLimitarTamanhoDaPagina() throws Exception {
        for (int i = 1; i <= 3; i++) {
            publicado("Evento " + i, i);
        }

        // Sem o teto, isto viraria uma varredura completa da tabela a pedido de qualquer
        // visitante. O Spring reduz o valor ao maximo configurado.
        mockMvc.perform(get("/events").param("size", "100000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(100));
    }

    @Test
    @DisplayName("filtra por trecho do nome, ignorando maiusculas")
    void deveFiltrarPorNome() throws Exception {
        publicado("Show de Rock", 10);
        publicado("Festival de Jazz", 20);

        mockMvc.perform(get("/events").param("busca", "JAZZ"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].name").value("Festival de Jazz"));
    }

    @Test
    @DisplayName("filtra por faixa de datas")
    void deveFiltrarPorFaixaDeDatas() throws Exception {
        publicado("Proximo", 5);
        publicado("Distante", 90);

        String limite = Instant.now().plus(30, ChronoUnit.DAYS).toString();

        mockMvc.perform(get("/events").param("ate", limite))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].name").value("Proximo"));
    }

    @Test
    @DisplayName("detalhe de evento publicado devolve os dados completos")
    void deveDetalharEventoPublicado() throws Exception {
        Event evento = publicado("Show de Rock", 10);

        mockMvc.perform(get("/events/" + evento.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Show de Rock"))
                .andExpect(jsonPath("$.description").isNotEmpty())
                .andExpect(jsonPath("$.venue").isNotEmpty())
                .andExpect(jsonPath("$.totalTickets").value(500));
    }

    @Test
    @DisplayName("rascunho devolve 404 no catalogo publico, e nao 403")
    void rascunhoDeveDevolver404() throws Exception {
        Event rascunho = rascunho("Evento em preparacao");

        // 404, e nao 403: um 403 confirmaria que existe um evento naquele id, revelando ao
        // publico que ha algo sendo preparado.
        mockMvc.perform(get("/events/" + rascunho.getId()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("EVENT_NOT_FOUND"));
    }

    @Test
    @DisplayName("evento cancelado some do catalogo publico")
    void canceladoDeveSumirDoCatalogo() throws Exception {
        Event cancelado = cancelado("Evento cancelado");

        mockMvc.perform(get("/events/" + cancelado.getId()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("evento inexistente devolve 404")
    void inexistenteDeveDevolver404() throws Exception {
        mockMvc.perform(get("/events/" + UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    // ---------- auxiliares ----------

    private Event publicado(String nome, int diasAteOEvento) {
        Event evento = novo(nome, diasAteOEvento);
        evento.publicar();
        return eventRepository.saveAndFlush(evento);
    }

    private Event rascunho(String nome) {
        return eventRepository.saveAndFlush(novo(nome, 15));
    }

    private Event cancelado(String nome) {
        Event evento = novo(nome, 15);
        evento.cancelar();
        return eventRepository.saveAndFlush(evento);
    }

    private Event novo(String nome, int diasAteOEvento) {
        return Event.rascunho(
                nome,
                "Descricao de " + nome,
                "Estadio Municipal",
                Instant.now().plus(diasAteOEvento, ChronoUnit.DAYS),
                500,
                new BigDecimal("150.00"),
                UUID.randomUUID());
    }
}
