import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it } from 'vitest'
import { CartaoDeEvento } from './CartaoDeEvento'
import type { EventoResumo } from '../api/tipos'

const EVENTO: EventoResumo = {
  id: '10000000-0000-0000-0000-000000000005',
  name: 'Quarteto Sonora',
  venue: 'Sala Sao Paulo',
  eventDate: '2026-09-23T21:00:00Z',
  price: 120,
  totalTickets: 800,
  imageUrl: null,
  category: 'SHOWS',
}

function montar(evento: EventoResumo = EVENTO) {
  return render(
    <MemoryRouter>
      <CartaoDeEvento evento={evento} />
    </MemoryRouter>,
  )
}

describe('CartaoDeEvento', () => {
  it('leva ao detalhe do evento', () => {
    montar()
    expect(screen.getByRole('link')).toHaveAttribute(
      'href',
      '/eventos/10000000-0000-0000-0000-000000000005',
    )
  })

  it('mostra nome, local e preco a partir de', () => {
    montar()

    expect(screen.getByRole('heading', { name: 'Quarteto Sonora' })).toBeInTheDocument()
    expect(screen.getByText('Sala Sao Paulo')).toBeInTheDocument()
    // "a partir de" e literal: o preco do evento e o MENOR entre os setores, e mostra-lo sem a
    // ressalva faria o comprador achar que a Plateia custa o mesmo que o Camarote.
    expect(screen.getByText('a partir de')).toBeInTheDocument()
    expect(screen.getByText(/120,00/)).toBeInTheDocument()
  })

  it('etiqueta a categoria', () => {
    montar()
    expect(screen.getByText('Shows')).toBeInTheDocument()
  })

  it('omite a etiqueta quando o evento nao tem categoria', () => {
    // Evento gravado antes da Fase 21. Sem a guarda, o cartao desenharia uma pilula preta
    // vazia — que parece defeito muito mais do que a ausencia da etiqueta.
    montar({ ...EVENTO, category: undefined as never })

    expect(screen.queryByText('Shows')).not.toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Quarteto Sonora' })).toBeInTheDocument()
  })

  it('nao mostra selo de disponibilidade sem o numero', () => {
    montar()

    expect(screen.queryByText('Disponivel')).not.toBeInTheDocument()
    expect(screen.queryByText('Esgotado')).not.toBeInTheDocument()
  })
})
