package com.devbandeiraa.bookingservice.controller;

import com.devbandeiraa.bookingservice.dto.response.AvailabilityResponse;
import com.devbandeiraa.bookingservice.service.EstoqueService;
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
public class AvailabilityController {

    private final EstoqueService estoqueService;

    public AvailabilityController(EstoqueService estoqueService) {
        this.estoqueService = estoqueService;
    }

    @GetMapping("/events/{eventId}/availability")
    public ResponseEntity<AvailabilityResponse> consultar(@PathVariable UUID eventId) {
        return ResponseEntity.ok(
                AvailabilityResponse.de(estoqueService.garantirHidratado(eventId)));
    }
}
