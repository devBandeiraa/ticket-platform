package com.devbandeiraa.bookingservice.client;

import java.util.List;
import java.util.UUID;

/**
 * O que o booking-service aproveita da resposta do event-service.
 *
 * <p>Declara so o id e a planta da casa, embora {@code GET /events/{id}} devolva bem mais. E
 * deliberado: o contrato entre os servicos deve ser o menor possivel, porque cada campo copiado
 * aqui vira uma razao a mais para este servico quebrar quando o outro mudar. Nome, local e
 * descricao do evento sao assunto do catalogo, e o frontend os busca de la.
 *
 * <p>{@code totalTickets} e {@code price} sairam na Fase 17, quando a reserva passou a ser por
 * assento. Os dois continuam existindo no catalogo, agora derivados dos setores — mas aqui
 * perderam a funcao: capacidade e a contagem de lugares gerados, e preco e por setor, gravado
 * em cada linha de assento.
 *
 * <p>Campos desconhecidos sao ignorados pelo Jackson na configuracao padrao do Spring Boot, de
 * modo que o event-service pode acrescentar campos sem quebrar este cliente.
 */
public record EventSnapshot(UUID id, List<SectorSnapshot> sectors) {
}
