import { query, requisitar } from './cliente'
import type {
  Disponibilidade,
  EventoDetalhe,
  EventoFormulario,
  EventoResumo,
  Pagina,
  StatusDoEvento,
} from './tipos'

// --- catalogo publico (event-service) ---

export function listarPublicados(parametros: {
  page?: number
  size?: number
  busca?: string
}): Promise<Pagina<EventoResumo>> {
  return requisitar(`/events${query(parametros)}`)
}

export function buscarEvento(id: string): Promise<EventoDetalhe> {
  return requisitar(`/events/${id}`)
}

/**
 * Disponibilidade vem do booking-service, e nao do event-service — apesar do caminho.
 *
 * O `totalTickets` do catalogo e a capacidade cadastrada; quantos ainda restam so quem tem o
 * contador de estoque sabe. O gateway roteia este caminho especifico para la.
 */
export function consultarDisponibilidade(eventoId: string): Promise<Disponibilidade> {
  return requisitar(`/events/${eventoId}/availability`)
}

// --- administracao (event-service) ---

export function listarParaAdmin(parametros: {
  page?: number
  size?: number
  status?: StatusDoEvento | ''
}): Promise<Pagina<EventoResumo>> {
  return requisitar(`/admin/events${query(parametros)}`)
}

export function buscarParaAdmin(id: string): Promise<EventoDetalhe> {
  return requisitar(`/admin/events/${id}`)
}

export function criarEvento(dados: EventoFormulario): Promise<EventoDetalhe> {
  return requisitar('/admin/events', { metodo: 'POST', corpo: dados })
}

export function alterarEvento(id: string, dados: EventoFormulario): Promise<EventoDetalhe> {
  return requisitar(`/admin/events/${id}`, { metodo: 'PUT', corpo: dados })
}

export function publicarEvento(id: string): Promise<EventoDetalhe> {
  return requisitar(`/admin/events/${id}/publish`, { metodo: 'POST' })
}

/** Exclusao logica: o evento vira CANCELLED, e continua visivel para o admin. */
export function cancelarEvento(id: string): Promise<void> {
  return requisitar(`/admin/events/${id}`, { metodo: 'DELETE' })
}
