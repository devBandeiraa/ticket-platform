package com.devbandeiraa.eventservice.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.devbandeiraa.eventservice.domain.Event;
import com.devbandeiraa.eventservice.domain.EventStatus;
import com.devbandeiraa.eventservice.dto.response.EventSummaryResponse;
import com.devbandeiraa.eventservice.dto.response.PaginaResponse;
import com.devbandeiraa.eventservice.repository.EventRepository;
import com.devbandeiraa.eventservice.service.EventService;
import com.devbandeiraa.eventservice.support.PostgresContainerConfig;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Verifica o seed do catalogo de desenvolvimento.
 *
 * <p>O profile {@code dev} monta um contexto proprio, e com ele um container proprio — o mesmo
 * arranjo do teste do seed do administrador. Por isso este teste enxerga um banco onde so o seed
 * rodou, sem interferir nos testes que limpam a tabela a cada metodo.
 */
@SpringBootTest
@Import(PostgresContainerConfig.class)
@ActiveProfiles("dev")
class SeedDeEventosIntegrationTest {

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private EventService eventService;

    /**
     * A razao de ser deste teste.
     *
     * <p>O seed calcula as datas a partir de {@code NOW()} justamente para nao envelhecer. Se
     * alguem trocar por constantes — o caminho mais curto quando se quer um seed deterministico —
     * o catalogo passa a nascer cheio de eventos que ja aconteceram, e a validacao de data futura
     * impede ate de corrigi-los pela tela. O sintoma aparece meses depois, longe da mudanca que
     * o causou; aqui ele aparece na mesma rodada.
     */
    @Test
    @DisplayName("todo evento do seed tem data no futuro")
    void asDatasDevemSerRelativas() {
        List<Event> eventos = eventRepository.findAll();

        assertThat(eventos)
                .as("o seed do profile dev nao populou o catalogo")
                .isNotEmpty();

        assertThat(eventos)
                .allSatisfy(evento -> assertThat(evento.getEventDate())
                        .as("evento '%s' com data no passado: o seed usou data fixa?",
                                evento.getName())
                        .isAfter(Instant.now()));
    }

    /**
     * O seed traz rascunho e cancelado de proposito, e o catalogo afirma na tela que so mostra
     * publicados. Sem os dois no banco, a afirmacao nao teria como ser conferida.
     */
    @Test
    @DisplayName("o catalogo publico ignora o rascunho e o cancelado que o seed criou")
    void oCatalogoDeveMostrarSomentePublicados() {
        assertThat(eventRepository.findAll())
                .as("o seed precisa conter os tres status para este teste ter objeto")
                .extracting(Event::getStatus)
                .contains(EventStatus.DRAFT, EventStatus.CANCELLED, EventStatus.PUBLISHED);

        PaginaResponse<EventSummaryResponse> catalogo =
                eventService.listarPublicados(null, null, null, PageRequest.of(0, 100));

        long publicados = eventRepository.findAll().stream()
                .filter(evento -> evento.getStatus() == EventStatus.PUBLISHED)
                .count();

        assertThat(catalogo.totalElements()).isEqualTo(publicados);
    }

    /**
     * A capa e opcional no dominio — um evento sem arte e estado valido. No seed, porem, ela
     * precisa estar la: o proposito do seed e que o catalogo pareca um produto ao subir, e um
     * cartao sem imagem no meio dos outros parece cadastro pela metade.
     */
    @Test
    @DisplayName("todo evento publicado do seed tem capa")
    void osPublicadosDevemTerCapa() {
        assertThat(eventRepository.findAll())
                .filteredOn(evento -> evento.getStatus() == EventStatus.PUBLISHED)
                .allSatisfy(evento -> assertThat(evento.getImageUrl())
                        .as("evento publicado '%s' sem capa", evento.getName())
                        .isNotBlank());
    }

    /**
     * A demo de concorrencia dispara N reservas simultaneas contra um evento. Com capacidade de
     * milhares, todas passam e a tela nao mostra disputa alguma — o resultado pareceria dizer que
     * nao ha problema a resolver, que e o oposto da tese do projeto.
     */
    @Test
    @DisplayName("ha um evento pequeno o bastante para a demo de concorrencia esgotar")
    void deveHaverEventoPequenoParaADemo() {
        assertThat(eventRepository.findAll())
                .filteredOn(evento -> evento.getStatus() == EventStatus.PUBLISHED)
                .as("nenhum evento publicado cabe na demo de concorrencia")
                .anySatisfy(evento -> assertThat(evento.getTotalTickets()).isLessThanOrEqualTo(100));
    }
}
