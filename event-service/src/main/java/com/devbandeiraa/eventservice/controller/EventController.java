package com.devbandeiraa.eventservice.controller;

import com.devbandeiraa.eventservice.dto.response.EventDetailResponse;
import com.devbandeiraa.eventservice.dto.response.EventSummaryResponse;
import com.devbandeiraa.eventservice.dto.response.PaginaResponse;
import com.devbandeiraa.eventservice.service.EventService;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Catalogo publico de eventos.
 *
 * <p>Todo endpoint daqui e aberto e mostra apenas eventos publicados. A superficie administrativa
 * vive sob {@code /admin/events}, separada de proposito: assim a autorizacao e uma regra unica de
 * prefixo, e nao uma anotacao por metodo que alguem pode esquecer de colocar.
 */
@RestController
@RequestMapping("/events")
public class EventController {

    private final EventService eventService;

    public EventController(EventService eventService) {
        this.eventService = eventService;
    }

    /**
     * @param busca filtra por trecho do nome, ignorando maiusculas
     * @param de    limite inferior da data do evento
     * @param ate   limite superior da data do evento
     */
    @GetMapping
    public ResponseEntity<PaginaResponse<EventSummaryResponse>> listar(
            @RequestParam(required = false) String busca,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant de,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant ate,
            // Paginacao com teto: sem o limite, um `size=1000000` na URL viraria uma varredura
            // completa da tabela a cada requisicao, de graca, para qualquer visitante.
            @PageableDefault(size = 20, sort = "eventDate", direction = Sort.Direction.ASC) Pageable pageable) {

        return ResponseEntity.ok(eventService.listarPublicados(busca, de, ate, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<EventDetailResponse> buscarPorId(@PathVariable UUID id) {
        return ResponseEntity.ok(eventService.buscarPublicado(id));
    }
}
