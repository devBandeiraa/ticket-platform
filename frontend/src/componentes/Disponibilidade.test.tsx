import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { SeloDeDisponibilidade, TextoDeDisponibilidade } from './Disponibilidade'
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
    expect(screen.getByText('Disponível')).toBeInTheDocument()
  })

  it('zero lugares mostra Esgotado', () => {
    render(<SeloDeDisponibilidade disponibilidade={disponibilidade(100, 0)} />)
    expect(screen.getByText('Esgotado')).toBeInTheDocument()
  })

  it('a escassez e proporcional a casa, e nao um numero fixo de lugares', () => {
    // Vinte restando numa casa de cinquenta e quase esgotado; vinte numa de tres mil e o
    // comeco da venda. Um limiar absoluto acertaria um dos dois e erraria o outro.
    const { rerender } = render(<SeloDeDisponibilidade disponibilidade={disponibilidade(50, 4)} />)
    expect(screen.getByText('Últimos ingressos')).toBeInTheDocument()

    rerender(<SeloDeDisponibilidade disponibilidade={disponibilidade(3000, 20)} />)
    expect(screen.getByText('Últimos ingressos')).toBeInTheDocument()

    rerender(<SeloDeDisponibilidade disponibilidade={disponibilidade(3000, 900)} />)
    expect(screen.getByText('Disponível')).toBeInTheDocument()
  })

  it('exatamente no limiar ainda conta como ultimos ingressos', () => {
    // 10 de 100 e exatamente 10%. O limite pertence ao lado do alerta: errar para o lado de
    // avisar custa menos do que deixar de avisar.
    render(<SeloDeDisponibilidade disponibilidade={disponibilidade(100, 10)} />)
    expect(screen.getByText('Últimos ingressos')).toBeInTheDocument()
  })

  it('esgotado se distingue por forma, e nao so por cor', () => {
    // Mesmo principio de `.assento-ocupado`: quem nao separa as cores precisa de outro sinal.
    render(<SeloDeDisponibilidade disponibilidade={disponibilidade(100, 0)} />)
    expect(screen.getByText('Esgotado')).toHaveClass('line-through')
  })
})

describe('TextoDeDisponibilidade', () => {
  it('nao desenha nada enquanto o numero nao chegou', () => {
    const { container } = render(<TextoDeDisponibilidade disponibilidade={undefined} />)
    expect(container).toBeEmptyDOMElement()
  })

  it('na folga, mostra so quantos restam', () => {
    // "820 de 1200 lugares disponiveis" poe o total na frente do que importa, que e haver
    // lugar de sobra.
    render(<TextoDeDisponibilidade disponibilidade={disponibilidade(1200, 820)} />)
    expect(screen.getByText('820 lugares disponíveis')).toBeInTheDocument()
  })

  it('da metade da casa para baixo, mostra o total junto', () => {
    // E o denominador que transforma o numero em aviso: "12 disponiveis" pode ser uma casa de
    // quinze ou de mil; "12 de 50" diz que a casa esta quase cheia.
    render(<TextoDeDisponibilidade disponibilidade={disponibilidade(50, 12)} />)
    expect(screen.getByText('12 de 50 lugares disponíveis')).toBeInTheDocument()
  })

  it('o total aparece antes de o alerta acender', () => {
    // Sao duas perguntas diferentes: "este numero se entende sozinho?" e "isto merece cor de
    // alerta?". 12 de 50 e um quarto da casa — longe dos 10% da escassez —, e o design mostra o
    // denominador assim mesmo, em texto neutro.
    render(<TextoDeDisponibilidade disponibilidade={disponibilidade(50, 12)} />)

    expect(screen.getByText('12 de 50 lugares disponíveis')).toHaveClass('text-suave')
  })

  it('na escassez de verdade, o texto ganha cor de alerta', () => {
    render(<TextoDeDisponibilidade disponibilidade={disponibilidade(50, 4)} />)

    expect(screen.getByText('4 de 50 lugares disponíveis')).toHaveClass('text-alerta')
  })

  it('exatamente na metade ainda conta como folga', () => {
    render(<TextoDeDisponibilidade disponibilidade={disponibilidade(50, 25)} />)
    expect(screen.getByText('25 lugares disponíveis')).toBeInTheDocument()
  })

  it('esgotado nao anuncia quantidade', () => {
    render(<TextoDeDisponibilidade disponibilidade={disponibilidade(50, 0)} />)
    expect(screen.getByText('Esgotado')).toBeInTheDocument()
  })

  it('fala de lugares, e nao de ingressos', () => {
    // Toda casa deste catalogo tem lugar marcado — a escolha esta registrada no seed do
    // event-service. "Ingressos" descreveria um evento de pista, que o dominio nao modela.
    render(<TextoDeDisponibilidade disponibilidade={disponibilidade(100, 40)} />)
    expect(screen.queryByText(/ingressos/)).not.toBeInTheDocument()
  })
})
