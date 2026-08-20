package com.devbandeiraa.bookingservice.controller;

import com.devbandeiraa.bookingservice.dto.request.CreateBookingRequest;
import com.devbandeiraa.bookingservice.dto.response.BookingResponse;
import com.devbandeiraa.bookingservice.dto.response.PaginaResponse;
import com.devbandeiraa.bookingservice.service.BookingService;
import com.devbandeiraa.bookingservice.service.ResultadoDaReserva;
import com.devbandeiraa.shared.security.AuthenticatedUser;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Reservas do usuario autenticado. */
@RestController
@RequestMapping("/bookings")
public class BookingController {

    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    /**
     * Cria uma reserva.
     *
     * <p>O cabecalho {@code Idempotency-Key} e obrigatorio, e {@code required = false} aqui e
     * intencional: com {@code true}, a ausencia viraria uma excecao do framework antes de o
     * codigo rodar, e a resposta nao seguiria o formato de erro da plataforma. Recebendo nulo, o
     * servico devolve {@code 400 INVALID_IDEMPOTENCY_KEY} como qualquer outra falha de validacao.
     *
     * <p>Devolve {@code 201} na criacao e {@code 200} quando a mesma chave e reapresentada.
     */
    @PostMapping
    public ResponseEntity<BookingResponse> criar(
            @Valid @RequestBody CreateBookingRequest requisicao,
            @RequestHeader(value = "Idempotency-Key", required = false) String chaveDeIdempotencia,
            @AuthenticationPrincipal AuthenticatedUser usuario) {

        ResultadoDaReserva resultado =
                bookingService.criar(requisicao, usuario.id(), chaveDeIdempotencia);

        if (!resultado.nova()) {
            return ResponseEntity.ok(resultado.reserva());
        }

        return ResponseEntity
                .created(URI.create("/bookings/" + resultado.reserva().id()))
                .body(resultado.reserva());
    }

    /** Reservas de quem esta autenticado. O id do usuario vem do token, nunca da URL. */
    @GetMapping("/me")
    public ResponseEntity<PaginaResponse<BookingResponse>> listarMinhas(
            @AuthenticationPrincipal AuthenticatedUser usuario,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {

        return ResponseEntity.ok(bookingService.listarDoUsuario(usuario.id(), pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<BookingResponse> buscarPorId(
            @PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser usuario) {

        return ResponseEntity.ok(bookingService.buscar(id, usuario));
    }

    /**
     * Pagamento simulado.
     *
     * <p>{@code POST} e nao {@code PUT}: pagar nao e substituir o recurso por uma versao nova, e
     * sim disparar uma transicao que so o servidor sabe se pode acontecer.
     */
    @PostMapping("/{id}/pay")
    public ResponseEntity<BookingResponse> pagar(
            @PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser usuario) {

        return ResponseEntity.ok(bookingService.pagar(id, usuario));
    }

    /** Cancela a reserva, devolvendo os ingressos ao estoque. */
    @PostMapping("/{id}/cancel")
    public ResponseEntity<Void> cancelar(
            @PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser usuario) {

        bookingService.cancelar(id, usuario);
        return ResponseEntity.noContent().build();
    }
}
