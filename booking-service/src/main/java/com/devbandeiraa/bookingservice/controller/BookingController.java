package com.devbandeiraa.bookingservice.controller;

import com.devbandeiraa.bookingservice.config.OpenApiConfig;
import com.devbandeiraa.bookingservice.dto.request.CreateBookingRequest;
import com.devbandeiraa.bookingservice.dto.response.BookingResponse;
import com.devbandeiraa.bookingservice.dto.response.PaginaResponse;
import com.devbandeiraa.bookingservice.service.BookingService;
import com.devbandeiraa.bookingservice.service.ResultadoDaReserva;
import com.devbandeiraa.shared.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
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
@Tag(name = "Reservas",
        description = "Criacao, consulta, pagamento e cancelamento. Toda operacao exige "
                + "token, e o dono da reserva sai sempre dele — nunca do corpo ou da URL.")
@SecurityRequirement(name = OpenApiConfig.ESQUEMA_JWT)
@ApiResponse(responseCode = "401", description = "INVALID_TOKEN: ausente ou invalido",
        content = @Content)
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
    @Operation(summary = "Cria uma reserva",
            description = "A reserva nasce PENDING e segura o ingresso ate `expiresAt`. "
                    + "Passado o prazo sem pagamento, ela expira sozinha e o ingresso volta "
                    + "ao estoque.\n\n"
                    + "Repetir a chamada com o mesmo `Idempotency-Key` devolve **200** com a "
                    + "reserva ja criada, em vez de **201** com uma segunda. E o que torna "
                    + "seguro repetir um pedido que estourou por timeout sem se saber se "
                    + "chegou.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "reserva criada"),
            @ApiResponse(responseCode = "200",
                    description = "a mesma chave de idempotencia ja havia criado esta reserva"),
            @ApiResponse(responseCode = "400",
                    description = "INVALID_IDEMPOTENCY_KEY ou VALIDATION_ERROR",
                    content = @Content),
            @ApiResponse(responseCode = "409",
                    description = "`SOLD_OUT` — nao ha ingressos suficientes. **Resposta "
                            + "definitiva:** e o que recebe quem perdeu a corrida pelo ultimo "
                            + "ingresso, e repetir nao muda o resultado.\n\n"
                            + "`LOCK_TIMEOUT` — havia disputa demais neste evento e a vez nao "
                            + "chegou a tempo. Esta, sim, pode ser repetida.",
                    content = @Content),
            @ApiResponse(responseCode = "404", description = "EVENT_NOT_AVAILABLE",
                    content = @Content)})
    @PostMapping
    public ResponseEntity<BookingResponse> criar(
            @Valid @RequestBody CreateBookingRequest requisicao,
            // Documentado como obrigatorio embora o binding use `required = false`. Nao e
            // contradicao: com `true`, a ausencia viraria excecao do framework antes de o
            // codigo rodar, e a resposta escaparia do formato de erro da plataforma. Para
            // quem consome, o cabecalho e obrigatorio — e e isso que a documentacao diz.
            @Parameter(name = "Idempotency-Key", in = ParameterIn.HEADER, required = true,
                    description = "Identificador unico desta tentativa, gerado pelo cliente. "
                            + "Um UUID serve. Repetir o valor devolve a reserva ja criada.")
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
    @Operation(summary = "Lista as reservas do usuario autenticado",
            description = "O id do usuario vem do token, nunca da URL — nao ha como pedir "
                    + "as reservas de outra pessoa trocando um parametro.")
    @GetMapping("/me")
    public ResponseEntity<PaginaResponse<BookingResponse>> listarMinhas(
            @AuthenticationPrincipal AuthenticatedUser usuario,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {

        return ResponseEntity.ok(bookingService.listarDoUsuario(usuario.id(), pageable));
    }

    @Operation(summary = "Detalha uma reserva",
            description = "Um administrador enxerga qualquer reserva; um usuario comum, "
                    + "apenas as suas. Pedir a reserva de outro devolve 404, e nao 403: "
                    + "confirmar que ela existe ja seria vazamento.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "reserva encontrada"),
            @ApiResponse(responseCode = "404",
                    description = "BOOKING_NOT_FOUND: inexistente, ou de outro usuario",
                    content = @Content)})
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
    @Operation(summary = "Paga a reserva (simulado)",
            description = "Leva a reserva de PENDING a CONFIRMED e registra, na mesma "
                    + "transacao, o evento que o notification-service consome.\n\n"
                    + "`POST` e nao `PUT`: pagar nao e substituir o recurso por uma versao "
                    + "nova, e sim disparar uma transicao que so o servidor sabe se pode "
                    + "acontecer.\n\n"
                    + "Pagar o que ja esta pago devolve **200** com a mesma reserva. Um "
                    + "duplo clique nao deve produzir tela de erro para algo que deu certo.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "reserva confirmada"),
            @ApiResponse(responseCode = "409",
                    description = "BOOKING_EXPIRED: o prazo venceu. "
                            + "BOOKING_CANCELLED: a reserva foi cancelada.",
                    content = @Content),
            @ApiResponse(responseCode = "404", description = "BOOKING_NOT_FOUND",
                    content = @Content)})
    @PostMapping("/{id}/pay")
    public ResponseEntity<BookingResponse> pagar(
            @PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser usuario) {

        return ResponseEntity.ok(bookingService.pagar(id, usuario));
    }

    /** Cancela a reserva, devolvendo os ingressos ao estoque. */
    @Operation(summary = "Cancela a reserva",
            description = "Devolve os ingressos ao estoque. Cancelar o que ja esta cancelado "
                    + "devolve 204 do mesmo jeito; cancelar o que ja foi pago nao, porque "
                    + "estorno envolve devolver dinheiro e nao apenas estoque.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "reserva cancelada"),
            @ApiResponse(responseCode = "409",
                    description = "BOOKING_ALREADY_CONFIRMED: ja paga, exigiria estorno",
                    content = @Content),
            @ApiResponse(responseCode = "404", description = "BOOKING_NOT_FOUND",
                    content = @Content)})
    @PostMapping("/{id}/cancel")
    public ResponseEntity<Void> cancelar(
            @PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser usuario) {

        bookingService.cancelar(id, usuario);
        return ResponseEntity.noContent().build();
    }
}
