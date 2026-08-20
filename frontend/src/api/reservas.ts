import { query, requisitar } from './cliente'
import type { Pagina, Reserva, StatusDaReserva } from './tipos'

/**
 * Cria uma reserva.
 *
 * A `Idempotency-Key` e obrigatoria e vem de fora de proposito. Gerar uma aqui dentro faria
 * cada retentativa nascer com chave nova, e duas reservas sairiam do que o usuario entende
 * como um clique so — exatamente o que o cabecalho existe para impedir. Quem chama e dono da
 * tentativa, e portanto da chave.
 */
export function reservar(
  eventId: string,
  quantity: number,
  chaveDeIdempotencia: string,
): Promise<Reserva> {
  return requisitar('/bookings', {
    metodo: 'POST',
    corpo: { eventId, quantity },
    cabecalhos: { 'Idempotency-Key': chaveDeIdempotencia },
  })
}

export function pagar(id: string): Promise<Reserva> {
  return requisitar(`/bookings/${id}/pay`, { metodo: 'POST' })
}

export function cancelarReserva(id: string): Promise<void> {
  return requisitar(`/bookings/${id}/cancel`, { metodo: 'POST' })
}

export function buscarReserva(id: string): Promise<Reserva> {
  return requisitar(`/bookings/${id}`)
}

export function listarMinhas(parametros: { page?: number; size?: number }): Promise<Pagina<Reserva>> {
  return requisitar(`/bookings/me${query(parametros)}`)
}

export function listarParaAdmin(parametros: {
  page?: number
  size?: number
  eventId?: string
  status?: StatusDaReserva | ''
}): Promise<Pagina<Reserva>> {
  return requisitar(`/admin/bookings${query(parametros)}`)
}
