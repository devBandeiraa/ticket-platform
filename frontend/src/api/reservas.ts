import { query, requisitar } from './cliente'
import type { FormaDePagamento, Pagina, Reserva, StatusDaReserva } from './tipos'

/**
 * Cria uma reserva.
 *
 * A `Idempotency-Key` e obrigatoria e vem de fora de proposito. Gerar uma aqui dentro faria
 * cada retentativa nascer com chave nova, e duas reservas sairiam do que o usuario entende
 * como um clique so — exatamente o que o cabecalho existe para impedir. Quem chama e dono da
 * tentativa, e portanto da chave.
 */
/**
 * Reserva lugares escolhidos.
 *
 * O servidor aceita tambem um pedido so com `quantity`, escolhendo os mais baratos livres. A
 * tela nao usa esse caminho: havendo mapa, deixar o servidor escolher esconderia do usuario a
 * unica decisao que ele veio tomar.
 */
export function reservar(
  eventId: string,
  seatIds: string[],
  chaveDeIdempotencia: string,
): Promise<Reserva> {
  return requisitar('/bookings', {
    metodo: 'POST',
    corpo: { eventId, seatIds },
    cabecalhos: { 'Idempotency-Key': chaveDeIdempotencia },
  })
}

/**
 * Reserva N lugares quaisquer — o "melhor disponivel" que o servidor escolhe.
 *
 * Usado pela demo de concorrencia, e so por ela. Ali o ponto e justamente que N compradores
 * anonimos disputem o que houver: escolher lugares especificos transformaria a demo numa
 * disputa por um assento so, que e outro experimento — e um que o teste de 200 threads do
 * booking-service ja faz melhor.
 *
 * Na tela de compra este caminho nao aparece: havendo mapa, deixar o servidor escolher
 * esconderia do usuario a unica decisao que ele veio tomar.
 */
export function reservarMelhorDisponivel(
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

/**
 * Paga a reserva.
 *
 * O valor NAO vai daqui: ja esta gravado na reserva desde a criacao, taxa inclusa. Envia-lo
 * permitiria pagar mil reais de ingresso mandando dez.
 *
 * A forma e opcional no servidor — ate a Fase 22 este endpoint nao recebia corpo algum — mas o
 * checkout sempre informa, porque e uma escolha que o comprador fez e que o ingresso mostra.
 */
export function pagar(id: string, forma: FormaDePagamento): Promise<Reserva> {
  return requisitar(`/bookings/${id}/pay`, { metodo: 'POST', corpo: { method: forma } })
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
