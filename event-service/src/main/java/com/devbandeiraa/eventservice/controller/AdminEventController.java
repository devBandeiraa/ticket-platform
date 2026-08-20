package com.devbandeiraa.eventservice.controller;

import com.devbandeiraa.eventservice.domain.EventStatus;
import com.devbandeiraa.eventservice.dto.request.EventRequest;
import com.devbandeiraa.eventservice.dto.response.EventDetailResponse;
import com.devbandeiraa.eventservice.dto.response.EventSummaryResponse;
import com.devbandeiraa.eventservice.dto.response.PaginaResponse;
import com.devbandeiraa.eventservice.service.EventService;
import com.devbandeiraa.shared.security.AuthenticatedUser;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Administracao de eventos.
 *
 * <p>Nao ha anotacao de autorizacao em metodo algum aqui: a {@code SecurityConfig} exige papel
 * ADMIN para todo o prefixo {@code /admin/**}. E deliberado — um endpoint administrativo novo
 * nasce protegido so por morar neste controller, sem depender de alguem lembrar de anota-lo.
 */
@RestController
@RequestMapping("/admin/events")
public class AdminEventController {

    private final EventService eventService;

    public AdminEventController(EventService eventService) {
        this.eventService = eventService;
    }

    /** Diferente do catalogo publico, enxerga rascunhos e cancelados. */
    @GetMapping
    public ResponseEntity<PaginaResponse<EventSummaryResponse>> listar(
            @RequestParam(required = false) EventStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        return ResponseEntity.ok(eventService.listarParaAdmin(status, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<EventDetailResponse> buscarPorId(@PathVariable UUID id) {
        return ResponseEntity.ok(eventService.buscarParaAdmin(id));
    }

    /**
     * O autor vem do token, nunca do corpo da requisicao: aceita-lo no JSON permitiria a um
     * admin registrar um evento em nome de outro.
     */
    @PostMapping
    public ResponseEntity<EventDetailResponse> criar(
            @Valid @RequestBody EventRequest requisicao,
            @AuthenticationPrincipal AuthenticatedUser admin) {

        EventDetailResponse criado = eventService.criar(requisicao, admin.id());
        return ResponseEntity.created(URI.create("/admin/events/" + criado.id())).body(criado);
    }

    @PutMapping("/{id}")
    public ResponseEntity<EventDetailResponse> alterar(
            @PathVariable UUID id, @Valid @RequestBody EventRequest requisicao) {

        return ResponseEntity.ok(eventService.alterar(id, requisicao));
    }

    /** Publicar e uma acao propria, e nao efeito colateral de uma edicao. */
    @PostMapping("/{id}/publish")
    public ResponseEntity<EventDetailResponse> publicar(@PathVariable UUID id) {
        return ResponseEntity.ok(eventService.publicar(id));
    }

    /** Exclusao logica: o evento passa a CANCELLED, e nao some do banco. */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> cancelar(@PathVariable UUID id) {
        eventService.cancelar(id);
        return ResponseEntity.noContent().build();
    }
}
