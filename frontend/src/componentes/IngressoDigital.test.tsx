import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { IngressoDigital } from './IngressoDigital'
import type { EventoResumo, Reserva } from '../api/tipos'

function reserva(extras: Partial<Reserva> = {}): Reserva {
  return {
    id: 'r1',
    eventId: 'e1',
    userId: 'u1',
    quantity: 1,
    subtotal: 120,
    fee: 12,
    totalPrice: 132,
    seats: [
      { seatId: 's1', sector: 'Plateia', row: 'C', number: 11, label: 'Plateia C11', price: 120 },
    ],
    status: 'CONFIRMED',
    expiresAt: null,
    paidAt: '2026-09-16T10:00:00Z',
    createdAt: '2026-09-16T10:00:00Z',
    paymentMethod: 'PIX',
    ticketCode: 'TP-4K7M2P-9XQ3RB',
    ...extras,
  }
}

const EVENTO: EventoResumo = {
  id: 'e1',
  name: 'Quarteto Sonora',
  venue: 'Sala Sao Paulo',
  eventDate: '2026-09-23T21:00:00Z',
  price: 120,
  totalTickets: 800,
  imageUrl: null,
  category: 'SHOWS',
}

describe('IngressoDigital', () => {
  it('leva evento, local, lugar e codigo', () => {
    render(<IngressoDigital reserva={reserva()} evento={EVENTO} comprador="Joao Silva" />)

    expect(screen.getByRole('heading', { name: 'Quarteto Sonora' })).toBeInTheDocument()
    expect(screen.getByText(/Sala Sao Paulo/)).toBeInTheDocument()
    expect(screen.getByText(/Plateia C11/)).toBeInTheDocument()
    expect(screen.getByText('TP-4K7M2P-9XQ3RB')).toBeInTheDocument()
    expect(screen.getByText('Joao Silva')).toBeInTheDocument()
  })

  it('com varios lugares, resume em vez de listar', () => {
    // Um ingresso por RESERVA, e nao por lugar: a reserva e a unidade que foi paga e a que tem
    // codigo. Listar quatro etiquetas daria a impressao de quatro codigos, e ha um so.
    const quatro = reserva({
      quantity: 4,
      seats: Array.from({ length: 4 }, (_, i) => ({
        seatId: `s${i}`,
        sector: 'Plateia',
        row: 'C',
        number: 10 + i,
        label: `Plateia C${10 + i}`,
        price: 120,
      })),
    })

    render(<IngressoDigital reserva={quatro} evento={EVENTO} />)

    expect(screen.getByText(/4 lugares/)).toBeInTheDocument()
    expect(screen.queryByText(/Plateia C13/)).not.toBeInTheDocument()
  })

  it('a miniatura nao mostra titular', () => {
    // A 220px de largura, nome de titular nao se le — so rouba espaco do que importa na lista,
    // que e reconhecer de qual evento e o ingresso.
    render(<IngressoDigital reserva={reserva()} evento={EVENTO} comprador="Joao Silva" compacto />)

    expect(screen.queryByText('Joao Silva')).not.toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Quarteto Sonora' })).toBeInTheDocument()
  })

  it('sem o evento carregado, nao inventa nome', () => {
    render(<IngressoDigital reserva={reserva()} />)

    expect(screen.getByRole('heading', { name: 'Evento' })).toBeInTheDocument()
    // O codigo continua aparecendo: e o que a portaria confere, e ele nao depende do evento.
    expect(screen.getByText('TP-4K7M2P-9XQ3RB')).toBeInTheDocument()
  })

  it('reserva sem codigo nao desenha linha de codigo vazia', () => {
    render(<IngressoDigital reserva={reserva({ ticketCode: null })} evento={EVENTO} />)

    expect(screen.queryByText(/^TP-/)).not.toBeInTheDocument()
  })
})
