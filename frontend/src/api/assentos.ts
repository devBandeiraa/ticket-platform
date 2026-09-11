import type { MapaDeAssentos } from './tipos'
import { requisitar } from './cliente'

/**
 * O mapa de lugares de um evento, com o estado de cada um.
 *
 * Vem do booking-service, e nao do event-service: a pergunta que o mapa responde e "quais
 * lugares posso escolher?", e quem sabe o que ja foi vendido e quem guarda o estado. O catalogo
 * responde pela planta da casa; este endpoint, por quem esta sentado onde.
 */
export function buscarMapa(eventId: string): Promise<MapaDeAssentos> {
  return requisitar(`/events/${eventId}/seats`)
}
