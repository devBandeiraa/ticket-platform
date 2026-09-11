import { describe, expect, it, vi } from 'vitest'
import { fireEvent, render, screen } from '@testing-library/react'
import type { AssentoDoMapa, StatusDoAssento } from '../api/tipos'
import { MapaDeAssentos } from './MapaDeAssentos'

function assento(
  numero: number,
  status: StatusDoAssento,
  sector = 'Plateia',
  row = 'A',
): AssentoDoMapa {
  return {
    seatId: `${sector}-${row}-${numero}`,
    sector,
    row,
    number: numero,
    label: `${sector} ${row}${numero}`,
    price: 180,
    status,
  }
}

/**
 * Testes do mapa de assentos.
 *
 * O foco e acessibilidade, e nao aparencia. A aparencia um humano confere abrindo a tela; o
 * que ninguem percebe olhando e um mapa que um leitor de tela nao consegue descrever, ou que
 * exige mil e quinhentas tabulacoes para atravessar.
 */
describe('MapaDeAssentos', () => {
  it('descreve cada lugar por extenso, com setor, fila, numero, preco e situacao', () => {
    render(
      <MapaDeAssentos
        assentos={[assento(1, 'FREE'), assento(2, 'SOLD')]}
        selecionados={new Set()}
        aoAlternar={vi.fn()}
      />,
    )

    // Sem isto, o leitor de tela anunciaria apenas "1" e "2" — a numeracao visivel dentro do
    // botao —, sem dizer de que fila, a que preco, nem se da para escolher.
    expect(screen.getByRole('gridcell', { name: /Plateia, fila A, lugar 1, .* disponivel/ }))
      .toBeInTheDocument()
    expect(screen.getByRole('gridcell', { name: /lugar 2, .* vendido/ })).toBeInTheDocument()
  })

  it('marca o lugar escolhido com aria-pressed', () => {
    render(
      <MapaDeAssentos
        assentos={[assento(1, 'FREE')]}
        selecionados={new Set(['Plateia-A-1'])}
        aoAlternar={vi.fn()}
      />,
    )

    const lugar = screen.getByRole('gridcell', { name: /lugar 1/ })
    expect(lugar).toHaveAttribute('aria-pressed', 'true')
    // A cor nao e o unico sinal: o estado tambem chega a quem nao a enxerga.
    expect(lugar).toHaveAccessibleName(/selecionado por voce/)
  })

  it('desabilita o lugar ocupado, em vez de so pinta-lo diferente', () => {
    const aoAlternar = vi.fn()
    render(
      <MapaDeAssentos
        assentos={[assento(1, 'RESERVED')]}
        selecionados={new Set()}
        aoAlternar={aoAlternar}
      />,
    )

    fireEvent.click(screen.getByRole('gridcell', { name: /lugar 1/ }))

    // Um lugar apenas "pintado de cinza" continuaria clicavel, e o clique viraria um 409 que
    // a pessoa nao tinha como prever.
    expect(aoAlternar).not.toHaveBeenCalled()
  })

  it('avisa o lugar reservado como podendo voltar, e o vendido como definitivo', () => {
    render(
      <MapaDeAssentos
        assentos={[assento(1, 'RESERVED'), assento(2, 'SOLD')]}
        selecionados={new Set()}
        aoAlternar={vi.fn()}
      />,
    )

    expect(screen.getByRole('gridcell', { name: /lugar 1, .* reservado por outra pessoa/ }))
      .toBeInTheDocument()
    expect(screen.getByRole('gridcell', { name: /lugar 2, .* vendido/ })).toBeInTheDocument()
  })

  /**
   * Roving tabindex.
   *
   * Uma casa grande tem milhares de botoes. Se todos entrassem na ordem de tabulacao, passar
   * da Plateia ao Balcao pelo teclado levaria mil e quinhentas tabulacoes — a tela seria
   * intransitavel sem mouse. Apenas um assento por grade e tabulavel; as setas movem o foco.
   */
  it('deixa so um lugar por setor na ordem de tabulacao', () => {
    render(
      <MapaDeAssentos
        assentos={[assento(1, 'FREE'), assento(2, 'FREE'), assento(3, 'FREE')]}
        selecionados={new Set()}
        aoAlternar={vi.fn()}
      />,
    )

    const tabulaveis = screen
      .getAllByRole('gridcell')
      .filter((lugar) => lugar.getAttribute('tabindex') === '0')

    expect(tabulaveis).toHaveLength(1)
  })

  it('move o foco com as setas, sem sair da fila', () => {
    render(
      <MapaDeAssentos
        assentos={[assento(1, 'FREE'), assento(2, 'FREE')]}
        selecionados={new Set()}
        aoAlternar={vi.fn()}
      />,
    )

    const primeiro = screen.getByRole('gridcell', { name: /lugar 1/ })
    primeiro.focus()

    fireEvent.keyDown(primeiro, { key: 'ArrowRight' })
    const segundo = screen.getByRole('gridcell', { name: /lugar 2/ })
    expect(segundo).toHaveFocus()

    fireEvent.keyDown(segundo, { key: 'ArrowLeft' })
    expect(primeiro).toHaveFocus()
  })

  it('nao passa da borda da grade', () => {
    render(
      <MapaDeAssentos
        assentos={[assento(1, 'FREE'), assento(2, 'FREE')]}
        selecionados={new Set()}
        aoAlternar={vi.fn()}
      />,
    )

    const primeiro = screen.getByRole('gridcell', { name: /lugar 1/ })
    primeiro.focus()

    // Ir para a esquerda no primeiro lugar nao pode saltar para outro setor nem perder o foco.
    fireEvent.keyDown(primeiro, { key: 'ArrowLeft' })
    expect(primeiro).toHaveFocus()
  })

  it('cada setor e uma grade com nome proprio', () => {
    render(
      <MapaDeAssentos
        assentos={[assento(1, 'FREE'), assento(1, 'FREE', 'Balcao', 'A')]}
        selecionados={new Set()}
        aoAlternar={vi.fn()}
      />,
    )

    expect(screen.getByRole('grid', { name: /Plateia/ })).toBeInTheDocument()
    expect(screen.getByRole('grid', { name: /Balcao/ })).toBeInTheDocument()
  })

  it('conta os livres de cada setor', () => {
    render(
      <MapaDeAssentos
        assentos={[assento(1, 'FREE'), assento(2, 'SOLD'), assento(3, 'FREE')]}
        selecionados={new Set()}
        aoAlternar={vi.fn()}
      />,
    )

    // Pelo cabecalho do setor, e nao por /2/ solto: o numero 2 tambem e o rotulo de um
    // assento, e a busca casaria os dois.
    const cabecalho = screen.getByRole('heading', { name: 'Plateia' }).parentElement
    expect(cabecalho).toHaveTextContent('2 livres')
  })
})
