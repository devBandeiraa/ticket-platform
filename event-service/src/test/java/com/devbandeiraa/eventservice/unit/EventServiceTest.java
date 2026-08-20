package com.devbandeiraa.eventservice.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.devbandeiraa.eventservice.domain.Event;
import com.devbandeiraa.eventservice.domain.EventStatus;
import com.devbandeiraa.eventservice.dto.request.EventRequest;
import com.devbandeiraa.eventservice.exception.EventNotEditableException;
import com.devbandeiraa.eventservice.exception.EventNotFoundException;
import com.devbandeiraa.eventservice.repository.EventRepository;
import com.devbandeiraa.eventservice.service.EventService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Testes de unidade da regra de negocio do catalogo, sem contexto Spring e sem banco. */
@ExtendWith(MockitoExtension.class)
class EventServiceTest {

    private static final UUID ID_DO_ADMIN = UUID.randomUUID();

    @Mock
    private EventRepository eventRepository;

    @InjectMocks
    private EventService service;

    @Captor
    private ArgumentCaptor<Event> capturadorDeEvento;

    @Test
    @DisplayName("evento criado nasce como rascunho, nunca publicado")
    void deveCriarComoRascunho() {
        when(eventRepository.save(any(Event.class))).thenAnswer(chamada -> chamada.getArgument(0));

        service.criar(requisicaoValida(), ID_DO_ADMIN);

        verify(eventRepository).save(capturadorDeEvento.capture());
        assertThat(capturadorDeEvento.getValue().getStatus()).isEqualTo(EventStatus.DRAFT);
    }

    @Test
    @DisplayName("o autor vem do token, e nao do corpo da requisicao")
    void deveRegistrarAutorDoToken() {
        when(eventRepository.save(any(Event.class))).thenAnswer(chamada -> chamada.getArgument(0));

        service.criar(requisicaoValida(), ID_DO_ADMIN);

        verify(eventRepository).save(capturadorDeEvento.capture());
        assertThat(capturadorDeEvento.getValue().getCreatedBy()).isEqualTo(ID_DO_ADMIN);
    }

    @Test
    @DisplayName("busca publica nao enxerga rascunho")
    void buscaPublicaNaoDeveEnxergarRascunho() {
        UUID id = UUID.randomUUID();
        // O repositorio filtra por PUBLISHED; um rascunho simplesmente nao volta.
        when(eventRepository.findByIdAndStatus(id, EventStatus.PUBLISHED)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.buscarPublicado(id))
                .isInstanceOf(EventNotFoundException.class);
    }

    @Test
    @DisplayName("publicar muda o status para PUBLISHED")
    void devePublicar() {
        Event rascunho = eventoEmRascunho();
        UUID id = UUID.randomUUID();
        when(eventRepository.findById(id)).thenReturn(Optional.of(rascunho));

        service.publicar(id);

        assertThat(rascunho.getStatus()).isEqualTo(EventStatus.PUBLISHED);
    }

    @Test
    @DisplayName("publicar duas vezes nao e erro: a operacao e idempotente")
    void publicarDeveSerIdempotente() {
        Event evento = eventoEmRascunho();
        UUID id = UUID.randomUUID();
        when(eventRepository.findById(id)).thenReturn(Optional.of(evento));

        service.publicar(id);
        service.publicar(id);

        assertThat(evento.getStatus()).isEqualTo(EventStatus.PUBLISHED);
    }

    @Test
    @DisplayName("cancelar faz exclusao logica, sem apagar o registro")
    void deveCancelarSemApagar() {
        Event evento = eventoEmRascunho();
        UUID id = UUID.randomUUID();
        when(eventRepository.findById(id)).thenReturn(Optional.of(evento));

        service.cancelar(id);

        assertThat(evento.getStatus()).isEqualTo(EventStatus.CANCELLED);
        // Reservas ja feitas apontam para este evento: apagar o registro as deixaria orfas.
        verify(eventRepository, never()).delete(any(Event.class));
        verify(eventRepository, never()).deleteById(any(UUID.class));
    }

    @Test
    @DisplayName("evento cancelado nao pode mais ser alterado")
    void naoDeveAlterarEventoCancelado() {
        Event cancelado = eventoEmRascunho();
        cancelado.cancelar();
        UUID id = UUID.randomUUID();
        when(eventRepository.findById(id)).thenReturn(Optional.of(cancelado));

        assertThatThrownBy(() -> service.alterar(id, requisicaoValida()))
                .isInstanceOf(EventNotEditableException.class);
    }

    @Test
    @DisplayName("evento cancelado nao pode ser republicado")
    void naoDeveRepublicarEventoCancelado() {
        Event cancelado = eventoEmRascunho();
        cancelado.cancelar();
        UUID id = UUID.randomUUID();
        when(eventRepository.findById(id)).thenReturn(Optional.of(cancelado));

        assertThatThrownBy(() -> service.publicar(id))
                .isInstanceOf(EventNotEditableException.class);
    }

    @Test
    @DisplayName("alterar evento inexistente devolve nao encontrado")
    void deveFalharAoAlterarInexistente() {
        UUID id = UUID.randomUUID();
        when(eventRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.alterar(id, requisicaoValida()))
                .isInstanceOf(EventNotFoundException.class);
    }

    private static EventRequest requisicaoValida() {
        return new EventRequest(
                "Show de Rock",
                "Uma noite inesquecivel",
                "Estadio Municipal",
                Instant.now().plus(30, ChronoUnit.DAYS),
                500,
                new BigDecimal("150.00"));
    }

    private static Event eventoEmRascunho() {
        return Event.rascunho(
                "Show de Rock",
                "Uma noite inesquecivel",
                "Estadio Municipal",
                Instant.now().plus(30, ChronoUnit.DAYS),
                500,
                new BigDecimal("150.00"),
                ID_DO_ADMIN);
    }
}
