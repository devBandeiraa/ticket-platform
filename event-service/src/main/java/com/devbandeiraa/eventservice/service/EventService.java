package com.devbandeiraa.eventservice.service;

import com.devbandeiraa.eventservice.domain.Event;
import com.devbandeiraa.eventservice.domain.EventStatus;
import com.devbandeiraa.eventservice.dto.request.EventRequest;
import com.devbandeiraa.eventservice.dto.response.EventDetailResponse;
import com.devbandeiraa.eventservice.dto.response.EventSummaryResponse;
import com.devbandeiraa.eventservice.dto.response.PaginaResponse;
import com.devbandeiraa.eventservice.exception.EventNotEditableException;
import com.devbandeiraa.eventservice.exception.EventNotFoundException;
import com.devbandeiraa.eventservice.repository.EventRepository;
import com.devbandeiraa.eventservice.repository.EventSpecifications;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Regra de negocio do catalogo de eventos. */
@Service
public class EventService {

    private static final Logger log = LoggerFactory.getLogger(EventService.class);

    private final EventRepository eventRepository;

    public EventService(EventRepository eventRepository) {
        this.eventRepository = eventRepository;
    }

    // ---------- catalogo publico ----------

    /**
     * Listagem publica: apenas eventos publicados.
     *
     * <p>O filtro por {@code PUBLISHED} e aplicado aqui, e nao deixado a cargo do chamador, para
     * que nao exista caminho pelo qual um rascunho vaze para o publico por esquecimento de quem
     * chama.
     */
    @Transactional(readOnly = true)
    public PaginaResponse<EventSummaryResponse> listarPublicados(
            String busca, Instant de, Instant ate, Pageable pageable) {

        // Cada filtro so entra na consulta quando de fato veio preenchido. Alem de gerar um SQL
        // mais enxuto, evita o problema de tipagem do bind nulo no PostgreSQL — ver a nota em
        // EventSpecifications.
        Specification<Event> filtro = EventSpecifications.comStatus(EventStatus.PUBLISHED);

        String termo = normalizarBusca(busca);
        if (termo != null) {
            filtro = filtro.and(EventSpecifications.comNomeContendo(termo));
        }
        if (de != null) {
            filtro = filtro.and(EventSpecifications.aPartirDe(de));
        }
        if (ate != null) {
            filtro = filtro.and(EventSpecifications.ate(ate));
        }

        return PaginaResponse.de(eventRepository.findAll(filtro, pageable), EventSummaryResponse::de);
    }

    @Transactional(readOnly = true)
    public EventDetailResponse buscarPublicado(UUID id) {
        Event evento = eventRepository.findByIdAndStatus(id, EventStatus.PUBLISHED)
                .orElseThrow(() -> new EventNotFoundException(id));

        return EventDetailResponse.de(evento);
    }

    // ---------- administracao ----------

    /** Diferente da listagem publica, o admin enxerga rascunhos e cancelados. */
    @Transactional(readOnly = true)
    public PaginaResponse<EventSummaryResponse> listarParaAdmin(EventStatus status, Pageable pageable) {
        Page<Event> pagina = status == null
                ? eventRepository.findAll(pageable)
                : eventRepository.findByStatus(status, pageable);

        return PaginaResponse.de(pagina, EventSummaryResponse::de);
    }

    @Transactional(readOnly = true)
    public EventDetailResponse buscarParaAdmin(UUID id) {
        return EventDetailResponse.de(carregar(id));
    }

    @Transactional
    public EventDetailResponse criar(EventRequest requisicao, UUID adminId) {
        Event evento = Event.rascunho(
                requisicao.name(),
                requisicao.description(),
                requisicao.venue(),
                requisicao.eventDate(),
                requisicao.totalTickets(),
                requisicao.price(),
                adminId);

        Event salvo = eventRepository.save(evento);
        log.info("evento criado: id={} nome='{}' por admin={}", salvo.getId(), salvo.getName(), adminId);

        return EventDetailResponse.de(salvo);
    }

    @Transactional
    public EventDetailResponse alterar(UUID id, EventRequest requisicao) {
        Event evento = carregarAlteravel(id);

        evento.alterarDados(
                requisicao.name(),
                requisicao.description(),
                requisicao.venue(),
                requisicao.eventDate(),
                requisicao.totalTickets(),
                requisicao.price());

        log.info("evento alterado: id={}", id);
        return EventDetailResponse.de(evento);
    }

    /**
     * Publica um evento, tornando-o visivel e vendavel.
     *
     * <p>Idempotente: publicar o que ja esta publicado nao e erro, apenas nao muda nada. Um duplo
     * clique no painel nao deve produzir uma mensagem de falha.
     */
    @Transactional
    public EventDetailResponse publicar(UUID id) {
        Event evento = carregarAlteravel(id);

        if (!evento.estaPublicado()) {
            evento.publicar();
            log.info("evento publicado: id={}", id);
        }

        return EventDetailResponse.de(evento);
    }

    /**
     * Cancela o evento.
     *
     * <p>Exclusao logica, e nao DELETE: reservas ja feitas apontam para este evento, e apagar o
     * registro as deixaria orfas, sem como explicar ao usuario a que evento se referiam.
     */
    @Transactional
    public void cancelar(UUID id) {
        Event evento = carregar(id);
        evento.cancelar();
        log.info("evento cancelado: id={}", id);
    }

    // ---------- apoio ----------

    private Event carregar(UUID id) {
        return eventRepository.findById(id).orElseThrow(() -> new EventNotFoundException(id));
    }

    private Event carregarAlteravel(UUID id) {
        Event evento = carregar(id);
        if (!evento.podeSerAlterado()) {
            throw new EventNotEditableException(id);
        }
        return evento;
    }

    /** Busca vazia ou so com espacos equivale a nao filtrar. */
    private String normalizarBusca(String busca) {
        return busca == null || busca.isBlank() ? null : busca.trim();
    }
}
