package com.devbandeiraa.bookingservice.controller;

import com.devbandeiraa.bookingservice.dto.response.AvailabilityResponse;
import com.devbandeiraa.bookingservice.service.EstoqueService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * Quantos ingressos ainda restam.
 *
 * <p>Publico, como o catalogo: quem ainda nao tem conta precisa ver se vale a pena criar uma.
 *
 * <p>Consultar um evento nunca visto dispara a hidratacao do estoque, que faz uma chamada ao
 * event-service. Sendo um endpoint aberto, isso e um caminho de amplificacao — uma requisicao
 * aqui pode virar uma requisicao la. Duas coisas o contem: a hidratacao acontece uma unica vez
 * por evento, e o rate limiting do gateway, na Fase 6, cobre a rajada inicial. Recusar o
 * endpoint para eventos ainda nao hidratados seria a alternativa, mas deixaria a tela de detalhe
 * sem numero ate que alguem reservasse — e ninguem reserva o que parece indisponivel.
 */
@RestController
@Tag(name = "Disponibilidade",
        description = "Quantos ingressos restam. Publico, como o catalogo: quem ainda nao "
                + "tem conta precisa ver se vale a pena criar uma.")
public class AvailabilityController {

    private final EstoqueService estoqueService;

    public AvailabilityController(EstoqueService estoqueService) {
        this.estoqueService = estoqueService;
    }

    @Operation(summary = "Consulta o estoque de um evento",
            description = "Nao exige token. O numero e um retrato do instante da consulta: "
                    + "sob concorrencia ele muda entre a leitura e a reserva, e e por isso "
                    + "que a decisao de vender nao se apoia nele — quem decide e o `UPDATE` "
                    + "condicional no banco, na hora de reservar.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "estoque do evento"),
            @ApiResponse(responseCode = "404",
                    description = "EVENT_NOT_AVAILABLE: nao existe ou nao esta publicado",
                    content = @Content)})
    @GetMapping("/events/{eventId}/availability")
    public ResponseEntity<AvailabilityResponse> consultar(@PathVariable UUID eventId) {
        return ResponseEntity.ok(
                AvailabilityResponse.de(estoqueService.garantirHidratado(eventId)));
    }
}
