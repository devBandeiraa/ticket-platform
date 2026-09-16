import { describe, expect, it } from 'vitest'
import type { AssentoDoMapa, StatusDoAssento } from '../api/tipos'
import { definirQuantidadeNoSetor, reconciliar, somar } from './selecaoDeAssentos'

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

/** Helper proprio: o de cima fixa setor e fila, e estes casos precisam variar os dois. */
function lugar(
  seatId: string,
  sector: string,
  row: string,
  number: number,
  price: number,
  status: StatusDoAssento,
): AssentoDoMapa {
  return { seatId, sector, row, number, label: `${sector} ${row}${number}`, price, status }
}

describe('definirQuantidadeNoSetor', () => {
  const mapa: AssentoDoMapa[] = [
    lugar('p1', 'Plateia', 'A', 1, 100, 'FREE'),
    lugar('p2', 'Plateia', 'A', 2, 100, 'FREE'),
    lugar('p3', 'Plateia', 'B', 1, 80, 'FREE'),
    lugar('p4', 'Plateia', 'B', 2, 80, 'SOLD'),
    lugar('c1', 'Camarote', 'A', 1, 300, 'FREE'),
    lugar('c2', 'Camarote', 'A', 2, 300, 'FREE'),
  ]

  it('escolhe os mais baratos livres do setor', () => {
    const escolha = definirQuantidadeNoSetor(mapa, new Set(), 'Plateia', 2, 10)
    // p3 custa 80 e entra primeiro; depois p1, a 100. p4 esta vendido e nao entra.
    expect([...escolha].sort()).toEqual(['p1', 'p3'])
  })

  it('nao toca na escolha feita em outro setor', () => {
    const escolha = definirQuantidadeNoSetor(mapa, new Set(['c1']), 'Plateia', 1, 10)
    expect(escolha.has('c1')).toBe(true)
    expect(escolha.has('p3')).toBe(true)
  })

  it('ao aumentar, preserva o que ja estava escolhido a dedo', () => {
    // p1 foi clicado no mapa, e nao e o mais barato. Recomecar do zero o trocaria por p3 —
    // movendo um lugar que a pessoa escolheu de proposito.
    const escolha = definirQuantidadeNoSetor(mapa, new Set(['p1']), 'Plateia', 2, 10)
    expect(escolha.has('p1')).toBe(true)
    expect(escolha.has('p3')).toBe(true)
  })

  it('ao diminuir, tira primeiro o mais caro', () => {
    // Quem esta removendo lugares quer gastar menos; tirar o mais barato seria o contrario.
    const escolha = definirQuantidadeNoSetor(mapa, new Set(['p1', 'p3']), 'Plateia', 1, 10)
    expect([...escolha]).toEqual(['p3'])
  })

  it('zero limpa apenas o setor pedido', () => {
    const escolha = definirQuantidadeNoSetor(mapa, new Set(['p1', 'c1']), 'Plateia', 0, 10)
    expect([...escolha]).toEqual(['c1'])
  })

  it('respeita o teto da reserva contando os outros setores', () => {
    // Teto de 3 com dois lugares ja no Camarote: sobra um para a Plateia, mesmo pedindo tres.
    const escolha = definirQuantidadeNoSetor(mapa, new Set(['c1', 'c2']), 'Plateia', 3, 3)
    expect(escolha.size).toBe(3)
    expect(escolha.has('c1')).toBe(true)
    expect(escolha.has('c2')).toBe(true)
  })

  it('pedir mais do que ha livre devolve o que ha', () => {
    const escolha = definirQuantidadeNoSetor(mapa, new Set(), 'Plateia', 99, 10)
    // So tres livres na Plateia; p4 esta vendido.
    expect(escolha.size).toBe(3)
    expect(escolha.has('p4')).toBe(false)
  })

  it('quantidade negativa vira zero em vez de erro', () => {
    const escolha = definirQuantidadeNoSetor(mapa, new Set(['p1']), 'Plateia', -1, 10)
    expect(escolha.size).toBe(0)
  })
})
