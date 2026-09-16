import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { SeloDeDisponibilidade } from './Disponibilidade'
import type { Disponibilidade } from '../api/tipos'

function disponibilidade(total: number, available: number): Disponibilidade {
  return { eventId: 'e1', total, reserved: total - available, available }
}

describe('SeloDeDisponibilidade', () => {
  it('nao desenha nada enquanto o numero nao chegou', () => {
    // Um esqueleto piscando no lugar chamaria mais atencao do que a informacao que ele
    // substitui — o selo e pequeno e divide a linha com o preco.
    const { container } = render(<SeloDeDisponibilidade disponibilidade={undefined} />)
    expect(container).toBeEmptyDOMElement()
  })

  it('casa cheia mostra Disponivel', () => {
    render(<SeloDeDisponibilidade disponibilidade={disponibilidade(100, 100)} />)
    expect(screen.getByText('Disponivel')).toBeInTheDocument()
  })

  it('zero lugares mostra Esgotado', () => {
    render(<SeloDeDisponibilidade disponibilidade={disponibilidade(100, 0)} />)
    expect(screen.getByText('Esgotado')).toBeInTheDocument()
  })

  it('a escassez e proporcional a casa, e nao um numero fixo de lugares', () => {
    // Vinte restando numa casa de cinquenta e quase esgotado; vinte numa de tres mil e o
    // comeco da venda. Um limiar absoluto acertaria um dos dois e erraria o outro.
    const { rerender } = render(<SeloDeDisponibilidade disponibilidade={disponibilidade(50, 4)} />)
    expect(screen.getByText('Ultimos ingressos')).toBeInTheDocument()

    rerender(<SeloDeDisponibilidade disponibilidade={disponibilidade(3000, 20)} />)
    expect(screen.getByText('Ultimos ingressos')).toBeInTheDocument()

    rerender(<SeloDeDisponibilidade disponibilidade={disponibilidade(3000, 900)} />)
    expect(screen.getByText('Disponivel')).toBeInTheDocument()
  })

  it('exatamente no limiar ainda conta como ultimos ingressos', () => {
    // 10 de 100 e exatamente 10%. O limite pertence ao lado do alerta: errar para o lado de
    // avisar custa menos do que deixar de avisar.
    render(<SeloDeDisponibilidade disponibilidade={disponibilidade(100, 10)} />)
    expect(screen.getByText('Ultimos ingressos')).toBeInTheDocument()
  })

  it('esgotado se distingue por forma, e nao so por cor', () => {
    // Mesmo principio de `.assento-ocupado`: quem nao separa as cores precisa de outro sinal.
    render(<SeloDeDisponibilidade disponibilidade={disponibilidade(100, 0)} />)
    expect(screen.getByText('Esgotado')).toHaveClass('line-through')
  })
})
