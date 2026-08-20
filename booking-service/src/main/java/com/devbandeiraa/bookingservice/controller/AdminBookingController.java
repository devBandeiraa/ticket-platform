package com.devbandeiraa.bookingservice.controller;

import com.devbandeiraa.bookingservice.domain.BookingStatus;
import com.devbandeiraa.bookingservice.dto.response.BookingResponse;
import com.devbandeiraa.bookingservice.dto.response.PaginaResponse;
import com.devbandeiraa.bookingservice.service.BookingService;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Listagem administrativa de reservas, para o dashboard.
 *
 * <p>Nao ha anotacao de autorizacao em metodo algum: a {@code SecurityConfig} exige papel ADMIN
 * para todo o prefixo {@code /admin/**}. Um endpoint administrativo novo nasce protegido so por
 * morar neste controller.
 *
 * <p>O mapeamento da Fase 0 previa {@code GET /bookings} com papel ADMIN. Ficou sob
 * {@code /admin} para acompanhar a separacao ja adotada no event-service entre {@code /events} e
 * {@code /admin/events}: a regra por prefixo protege por construcao, enquanto uma excecao para
 * um verbo especifico em {@code /bookings} depende de alguem lembrar dela ao adicionar o proximo
 * endpoint.
 */
@RestController
@RequestMapping("/admin/bookings")
public class AdminBookingController {

    private final BookingService bookingService;

    public AdminBookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    /** Filtros opcionais que se combinam: por evento, por situacao, ou ambos. */
    @GetMapping
    public ResponseEntity<PaginaResponse<BookingResponse>> listar(
            @RequestParam(required = false) UUID eventId,
            @RequestParam(required = false) BookingStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {

        return ResponseEntity.ok(bookingService.listarParaAdmin(eventId, status, pageable));
    }
}
