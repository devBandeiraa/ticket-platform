package com.devbandeiraa.eventservice.controller;

import com.devbandeiraa.eventservice.config.OpenApiConfig;
import com.devbandeiraa.eventservice.domain.EventStatus;
import com.devbandeiraa.eventservice.dto.request.EventRequest;
import com.devbandeiraa.eventservice.dto.response.EventDetailResponse;
import com.devbandeiraa.eventservice.dto.response.EventSummaryResponse;
import com.devbandeiraa.eventservice.dto.response.PaginaResponse;
import com.devbandeiraa.eventservice.service.EventService;
import com.devbandeiraa.shared.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
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
@Tag(name = "Catalogo (admin)",
        description = "Criacao, edicao e publicacao de eventos. Exige papel ADMIN.")
// No controller inteiro, e nao por metodo: espelha a regra de prefixo da SecurityConfig, entao
// um endpoint novo ja nasce documentado como protegido — do mesmo modo que ja nasce protegido.
@SecurityRequirement(name = OpenApiConfig.ESQUEMA_JWT)
@ApiResponses({
        @ApiResponse(responseCode = "401", description = "INVALID_TOKEN: ausente ou invalido",
                content = @Content),
        @ApiResponse(responseCode = "403", description = "FORBIDDEN: o token nao e de um ADMIN",
                content = @Content)})
public class AdminEventController {

    private final EventService eventService;

    public AdminEventController(EventService eventService) {
        this.eventService = eventService;
    }

    /** Diferente do catalogo publico, enxerga rascunhos e cancelados. */
    @Operation(summary = "Lista eventos em qualquer estado",
            description = "Diferente do catalogo publico, enxerga rascunhos e cancelados.")
    @GetMapping
    public ResponseEntity<PaginaResponse<EventSummaryResponse>> listar(
            @RequestParam(required = false) EventStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        return ResponseEntity.ok(eventService.listarParaAdmin(status, pageable));
    }

    @Operation(summary = "Detalha um evento em qualquer estado")
    @GetMapping("/{id}")
    public ResponseEntity<EventDetailResponse> buscarPorId(@PathVariable UUID id) {
        return ResponseEntity.ok(eventService.buscarParaAdmin(id));
    }

    /**
     * O autor vem do token, nunca do corpo da requisicao: aceita-lo no JSON permitiria a um
     * admin registrar um evento em nome de outro.
     */
    @Operation(summary = "Cria um evento",
            description = "Nasce como rascunho: criar nao publica. O autor sai do token, nunca "
                    + "do corpo — aceita-lo no JSON deixaria um admin registrar em nome de outro.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "evento criado, em rascunho"),
            @ApiResponse(responseCode = "400", description = "VALIDATION_ERROR", content = @Content)})
    @PostMapping
    public ResponseEntity<EventDetailResponse> criar(
            @Valid @RequestBody EventRequest requisicao,
            @AuthenticationPrincipal AuthenticatedUser admin) {

        EventDetailResponse criado = eventService.criar(requisicao, admin.id());
        return ResponseEntity.created(URI.create("/admin/events/" + criado.id())).body(criado);
    }

    @Operation(summary = "Altera um evento")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "evento alterado"),
            @ApiResponse(responseCode = "400", description = "VALIDATION_ERROR", content = @Content),
            @ApiResponse(responseCode = "404", description = "EVENT_NOT_FOUND", content = @Content)})
    @PutMapping("/{id}")
    public ResponseEntity<EventDetailResponse> alterar(
            @PathVariable UUID id, @Valid @RequestBody EventRequest requisicao) {

        return ResponseEntity.ok(eventService.alterar(id, requisicao));
    }

    /** Publicar e uma acao propria, e nao efeito colateral de uma edicao. */
    @Operation(summary = "Publica o evento",
            description = "Acao propria, e nao efeito colateral de uma edicao. So depois disto "
                    + "o evento aparece no catalogo e aceita reservas.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "evento publicado"),
            @ApiResponse(responseCode = "404", description = "EVENT_NOT_FOUND", content = @Content)})
    @PostMapping("/{id}/publish")
    public ResponseEntity<EventDetailResponse> publicar(@PathVariable UUID id) {
        return ResponseEntity.ok(eventService.publicar(id));
    }

    /** Exclusao logica: o evento passa a CANCELLED, e nao some do banco. */
    @Operation(summary = "Cancela o evento",
            description = "Exclusao logica: o evento passa a CANCELLED e sai do catalogo, mas "
                    + "continua no banco — as reservas que apontam para ele precisam dele.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "evento cancelado"),
            @ApiResponse(responseCode = "404", description = "EVENT_NOT_FOUND", content = @Content)})
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> cancelar(@PathVariable UUID id) {
        eventService.cancelar(id);
        return ResponseEntity.noContent().build();
    }
}
