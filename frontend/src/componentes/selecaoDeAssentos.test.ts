import { describe, expect, it } from 'vitest'
import type { AssentoDoMapa, StatusDoAssento } from '../api/tipos'
import { reconciliar, somar } from './selecaoDeAssentos'

function assento(seatId: string, status: StatusDoAssento, price = 100): AssentoDoMapa {
  return {
    seatId,
    sector: 'Plateia',
    row: 'A',
    number: Number(seatId.replace(/\D/g, '')) || 1,
    label: `Plateia A${seatId.replace(/\D/g, '')}`,
    price,
    status,
  }
}

describe('reconciliar', () => {
  it('mantem escolhido o que continua livre', () => {
    const mapa = [assento('1', 'FREE'), assento('2', 'FREE')]

    const { selecionados, perdidos } = reconciliar(mapa, new Set(['1', '2']))

    expect(selecionados.map((a) => a.seatId)).toEqual(['1', '2'])
    expect(perdidos).toHaveLength(0)
  })

  /**
   * O caso que este modulo existe para resolver.
   *
   * Sem ele, a tela seguiria exibindo como escolhido um lugar que o servidor ja recusaria — e
   * o usuario so descobriria ao clicar em "Reservar", depois de ter montado a escolha inteira.
   */
  it('tira da selecao o lugar vendido enquanto a pessoa decidia', () => {
    const mapa = [assento('1', 'FREE'), assento('2', 'SOLD')]

    const { selecionados, perdidos } = reconciliar(mapa, new Set(['1', '2']))

    expect(selecionados.map((a) => a.seatId)).toEqual(['1'])
    expect(perdidos.map((a) => a.label)).toEqual(['Plateia A2'])
  })

  it('trata reservado por outro como perdido, assim como vendido', () => {
    // Do ponto de vista de quem escolhe, "segurado por alguem" e "vendido" dao no mesmo: nao
    // da para levar. A distincao existe no mapa, para a tela poder explicar a diferenca.
    const { perdidos } = reconciliar([assento('1', 'RESERVED')], new Set(['1']))

    expect(perdidos).toHaveLength(1)
  })

  /**
   * A intencao do usuario nao se perde quando o lugar fica indisponivel — so fica
   * momentaneamente irrealizavel. Expirada a reserva de quem o segurava, ele volta.
   */
  it('reaproveita a escolha se o lugar voltar a ficar livre', () => {
    const intencao = new Set(['1'])

    expect(reconciliar([assento('1', 'RESERVED')], intencao).selecionados).toHaveLength(0)
    expect(reconciliar([assento('1', 'FREE')], intencao).selecionados).toHaveLength(1)
  })

  it('ignora lugar que nao foi escolhido', () => {
    const mapa = [assento('1', 'FREE'), assento('2', 'FREE')]

    const { selecionados } = reconciliar(mapa, new Set(['1']))

    expect(selecionados.map((a) => a.seatId)).toEqual(['1'])
  })

  it('devolve vazio quando o mapa ainda nao chegou', () => {
    expect(reconciliar([], new Set(['1']))).toEqual({ selecionados: [], perdidos: [] })
  })
})

describe('somar', () => {
  it('soma o preco de cada lugar, e nao multiplica um unitario', () => {
    // O caso que o preco unitario nao representava: setores diferentes na mesma reserva. A
    // media seria 125, valor que nenhum dos dois ingressos custou.
    const total = somar([assento('1', 'FREE', 180), assento('2', 'FREE', 70)])

    expect(total).toBe(250)
  })

  it('soma zero sem lugar algum', () => {
    expect(somar([])).toBe(0)
  })
})
