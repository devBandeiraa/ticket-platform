import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it } from 'vitest'
import { CartaoDeEvento } from './CartaoDeEvento'
import type { Disponibilidade, EventoResumo } from '../api/tipos'

// Construtor local, e nao string ISO com `Z`: a linha de data e exibida no fuso do navegador, e
// com `'2026-09-23T21:00:00Z'` o dia esperado mudaria de maquina para maquina.
const QUANDO = new Date(2026, 8, 23, 21, 0)

const EVENTO: EventoResumo = {
  id: '10000000-0000-0000-0000-000000000005',
  name: 'Quarteto Sonora',
  venue: 'Sala Sao Paulo, Sao Paulo',
  eventDate: QUANDO.toISOString(),
  price: 120,
  totalTickets: 800,
  imageUrl: null,
  category: 'SHOWS',
}

function montar(evento: EventoResumo = EVENTO, disponibilidade?: Disponibilidade) {
  return render(
    <MemoryRouter>
      <CartaoDeEvento evento={evento} disponibilidade={disponibilidade} />
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
    expect(screen.getByText('Sala Sao Paulo, Sao Paulo')).toBeInTheDocument()
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

  it('mostra a data no formato de cartaz', () => {
    // `18 OUT · DOM · 19H`, e nao `18 de out. de 2026 19:00`: a segunda forma e a certa para um
    // recibo e a errada para uma vitrine.
    montar()
    expect(screen.getByText('23 SET · QUA · 21H')).toBeInTheDocument()
  })

  it('nao mostra disponibilidade sem o numero', () => {
    montar()

    expect(screen.queryByText(/lugares/)).not.toBeInTheDocument()
    expect(screen.queryByText('Esgotado')).not.toBeInTheDocument()
  })

  it('mostra a disponibilidade quando o numero chega', () => {
    montar(EVENTO, { eventId: EVENTO.id, total: 800, reserved: 760, available: 40 })
    expect(screen.getByText('40 de 800 lugares disponíveis')).toBeInTheDocument()
  })
})
