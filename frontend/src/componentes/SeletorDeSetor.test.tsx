import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { SeletorDeSetor } from './SeletorDeSetor'
import type { AssentoDoMapa, Setor } from '../api/tipos'

function setor(nome: string, preco: number, extras: Partial<Setor> = {}): Setor {
  return {
    id: `id-${nome}`,
    name: nome,
    price: preco,
    rowsCount: 2,
    seatsPerRow: 2,
    rowLabels: ['A', 'B'],
    capacity: 4,
    description: null,
    benefits: [],
    tier: 'STANDARD',
    ...extras,
  }
}

function lugar(id: string, setorNome: string, preco: number, livre = true): AssentoDoMapa {
  return {
    seatId: id,
    sector: setorNome,
    row: 'A',
    number: Number(id.slice(1)),
    label: `${setorNome} A${id.slice(1)}`,
    price: preco,
    status: livre ? 'FREE' : 'SOLD',
  }
}

const PLATEIA = setor('Plateia', 100)
const CAMAROTE = setor('Camarote', 300, {
  tier: 'VIP',
  description: 'Cabines laterais com mesa.',
  benefits: ['Entrada exclusiva', 'Servico de bar na mesa'],
})

const MAPA = [
  lugar('p1', 'Plateia', 100),
  lugar('p2', 'Plateia', 100),
  lugar('c1', 'Camarote', 300),
]

function montar(props: Partial<Parameters<typeof SeletorDeSetor>[0]> = {}) {
  const aoDefinirQuantidade = vi.fn()
  render(
    <SeletorDeSetor
      setores={[PLATEIA, CAMAROTE]}
      assentos={MAPA}
      selecionados={new Set()}
      aoDefinirQuantidade={aoDefinirQuantidade}
      maximoRestante={10}
      {...props}
    />,
  )
  return { aoDefinirQuantidade }
}

describe('SeletorDeSetor', () => {
  it('mostra descricao e beneficios do setor', () => {
    montar()

    expect(screen.getByText('Cabines laterais com mesa.')).toBeInTheDocument()
    expect(screen.getByText('Entrada exclusiva')).toBeInTheDocument()
    expect(screen.getByText('Servico de bar na mesa')).toBeInTheDocument()
  })

  it('marca a faixa VIP', () => {
    montar()
    expect(screen.getByText('VIP')).toBeInTheDocument()
  })

  it('conta os livres do proprio setor', () => {
    montar()

    expect(screen.getByText('2 livres')).toBeInTheDocument()
    expect(screen.getByText('1 livres')).toBeInTheDocument()
  })

  it('cada contador tem nome acessivel com o setor', () => {
    montar()

    // Sem o nome do setor, um leitor de tela anunciaria "mais, botao" duas vezes na mesma tela
    // e a pessoa nao saberia qual setor esta aumentando.
    expect(
      screen.getByRole('button', { name: 'Adicionar um lugar de Camarote' }),
    ).toBeInTheDocument()
    expect(
      screen.getByRole('button', { name: 'Remover um lugar de Plateia' }),
    ).toBeInTheDocument()
  })

  it('pede a quantidade seguinte ao clicar em mais', () => {
    const { aoDefinirQuantidade } = montar()

    fireEvent.click(screen.getByRole('button', { name: 'Adicionar um lugar de Plateia' }))

    expect(aoDefinirQuantidade).toHaveBeenCalledWith('Plateia', 1)
  })

  it('pede a quantidade anterior ao clicar em menos', () => {
    const { aoDefinirQuantidade } = montar({ selecionados: new Set(['p1', 'p2']) })

    fireEvent.click(screen.getByRole('button', { name: 'Remover um lugar de Plateia' }))

    expect(aoDefinirQuantidade).toHaveBeenCalledWith('Plateia', 1)
  })

  it('o menos fica desabilitado sem nada escolhido no setor', () => {
    montar()
    expect(screen.getByRole('button', { name: 'Remover um lugar de Plateia' })).toBeDisabled()
  })

  it('o mais para quando acaba o estoque do setor', () => {
    // Camarote so tem um lugar livre; com ele escolhido, nao ha o que somar.
    montar({ selecionados: new Set(['c1']) })

    expect(screen.getByRole('button', { name: 'Adicionar um lugar de Camarote' })).toBeDisabled()
  })

  it('o mais para quando a reserva atinge o teto, mesmo com estoque', () => {
    // Plateia tem dois livres, mas a reserva ja esta cheia por causa de outros setores.
    montar({ maximoRestante: 0 })

    expect(screen.getByRole('button', { name: 'Adicionar um lugar de Plateia' })).toBeDisabled()
  })

  it('setor sem lugar livre aparece como esgotado', () => {
    montar({ assentos: [lugar('p1', 'Plateia', 100, false)] })

    // Duas vezes: no lugar do numero de livres e no lugar da pergunta do contador.
    expect(screen.getAllByText('esgotado').length).toBeGreaterThan(0)
  })
})
