import { describe, expect, it } from 'vitest'
import { abaDe, eventosDistintos, linkDeCalendario } from './ingressos'
import type { EventoResumo, Reserva } from '../api/tipos'

const AGORA = new Date('2026-09-16T12:00:00Z')

function reserva(extras: Partial<Reserva> = {}): Reserva {
  return {
    id: 'r1',
    eventId: 'e1',
    userId: 'u1',
    quantity: 1,
    subtotal: 100,
    fee: 10,
    totalPrice: 110,
    seats: [],
    status: 'CONFIRMED',
    expiresAt: null,
    paidAt: '2026-09-10T10:00:00Z',
    createdAt: '2026-09-10T10:00:00Z',
    paymentMethod: 'CARD',
    ticketCode: 'TP-4K7M2P-9XQ3RB',
    ...extras,
  }
}

function evento(eventDate: string): EventoResumo {
  return {
    id: 'e1',
    name: 'Quarteto Sonora',
    venue: 'Sala Sao Paulo',
    eventDate,
    price: 120,
    totalTickets: 800,
    imageUrl: null,
    category: 'SHOWS',
  }
}

describe('abaDe', () => {
  it('confirmada com evento no futuro fica em proximos', () => {
    expect(abaDe(reserva(), evento('2026-12-01T21:00:00Z'), AGORA)).toBe('proximos')
  })

  it('confirmada com evento no passado vira utilizada', () => {
    expect(abaDe(reserva(), evento('2026-01-10T21:00:00Z'), AGORA)).toBe('utilizados')
  })

  it('cancelada e expirada caem em cancelados, independente da data', () => {
    expect(abaDe(reserva({ status: 'CANCELLED' }), evento('2026-12-01T21:00:00Z'), AGORA)).toBe(
      'cancelados',
    )
    expect(abaDe(reserva({ status: 'EXPIRED' }), evento('2026-01-10T21:00:00Z'), AGORA)).toBe(
      'cancelados',
    )
  })

  it('pendente fica em proximos, e nao numa quarta aba', () => {
    // Nao e ingresso ainda, mas e o item mais acionavel da lista. Escondido, a pessoa perderia
    // o prazo sem saber que ele estava correndo.
    expect(abaDe(reserva({ status: 'PENDING' }), evento('2026-12-01T21:00:00Z'), AGORA)).toBe(
      'proximos',
    )
  })

  it('sem o evento carregado, confirmada fica em proximos', () => {
    // Palpite menos danoso: manda-la para "utilizados" esconderia um ingresso que talvez seja
    // de hoje a noite. O contrario so atrasa a mudanca de aba ate o evento chegar.
    expect(abaDe(reserva(), undefined, AGORA)).toBe('proximos')
  })

  it('evento comecando exatamente agora ainda conta como proximo', () => {
    // O limite pertence ao lado de "ainda vai acontecer": quem esta chegando ao show as 21h em
    // ponto nao deveria ver o ingresso mudar de aba na mao.
    expect(abaDe(reserva(), evento(AGORA.toISOString()), AGORA)).toBe('proximos')
  })
})

describe('eventosDistintos', () => {
  it('nao repete o mesmo evento', () => {
    // Quatro ingressos do mesmo show sao uma consulta, e nao quatro.
    const lista = [
      reserva({ id: 'a', eventId: 'e1' }),
      reserva({ id: 'b', eventId: 'e1' }),
      reserva({ id: 'c', eventId: 'e2' }),
    ]

    expect(eventosDistintos(lista)).toEqual(['e1', 'e2'])
  })

  it('lista vazia devolve vazio', () => {
    expect(eventosDistintos([])).toEqual([])
  })
})

describe('linkDeCalendario', () => {
  it('monta o link com data em UTC compactado', () => {
    const link = linkDeCalendario(evento('2026-09-23T21:00:00Z'), 'TP-AAAAAA-BBBBBB')

    expect(link).toContain('dates=20260923T210000Z%2F20260924T000000Z')
    expect(link).toContain('text=Quarteto+Sonora')
    expect(link).toContain('location=Sala+Sao+Paulo')
  })

  it('leva o codigo do ingresso nos detalhes', () => {
    const link = linkDeCalendario(evento('2026-09-23T21:00:00Z'), 'TP-AAAAAA-BBBBBB')
    expect(decodeURIComponent(link)).toContain('TP-AAAAAA-BBBBBB')
  })

  it('sem codigo, nao escreve "null" nos detalhes', () => {
    const link = linkDeCalendario(evento('2026-09-23T21:00:00Z'), null)
    expect(decodeURIComponent(link)).not.toContain('null')
  })
})
