package com.devbandeiraa.bookingservice.client;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * O que o booking-service aproveita da resposta do event-service.
 *
 * <p>Declara so tres campos, embora {@code GET /events/{id}} devolva bem mais. E deliberado: o
 * contrato entre os servicos deve ser o menor possivel, porque cada campo copiado aqui vira uma
 * razao a mais para este servico quebrar quando o outro mudar. Nome, local e descricao do evento
 * sao assunto do catalogo, e o frontend os busca de la.
 *
 * <p>Campos desconhecidos sao ignorados pelo Jackson na configuracao padrao do Spring Boot, de
 * modo que o event-service pode acrescentar campos sem quebrar este cliente.
 */
public record EventSnapshot(UUID id, int totalTickets, BigDecimal price) {
}
