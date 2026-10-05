import { describe, expect, it } from 'vitest'
import type { EventoResumo } from '../api/tipos'
import { chamadaDoDia, proximoFimDeSemanaComEventos } from './agenda'

/*
  Datas construidas pelo construtor local, e nao por string ISO com `Z`: o agrupamento e por dia
  LOCAL, e com ISO em UTC um evento de domingo a noite em Sao Paulo cairia na segunda e a suite
  passaria a depender do fuso do runner.

  Outubro de 2026: 16 e sexta, 17 sabado, 18 domingo — a mesma faixa do design.
*/
function evento(id: string, data: Date): EventoResumo {
  return {
    id,
    name: `Evento ${id}`,
    venue: 'Casa da Lapa, Rio de Janeiro',
    eventDate: data.toISOString(),
    price: 80,
    totalTickets: 50,
    imageUrl: null,
    category: 'SHOWS',
  }
}

const quinta = new Date(2026, 9, 15, 12, 0)

describe('proximoFimDeSemanaComEventos', () => {
  it('agrupa sexta, sabado e domingo num bloco', () => {
    const fds = proximoFimDeSemanaComEventos(
      [
        evento('sexta', new Date(2026, 9, 16, 20, 0)),
        evento('sabado', new Date(2026, 9, 17, 19, 30)),
        evento('domingo', new Date(2026, 9, 18, 19, 0)),
      ],
      quinta,
    )

    expect(fds).not.toBeNull()
    expect(fds!.inicio.getDate()).toBe(16)
    expect(fds!.fim.getDate()).toBe(18)
    expect(fds!.dias.map((dia) => dia.data.getDate())).toEqual([16, 17, 18])
  })

  it('a sexta conta como fim de semana', () => {
    // Nao e a definicao do calendario, e e a de quem procura programa: o show de sexta a noite
    // e tao de fim de semana quanto o de sabado.
    const fds = proximoFimDeSemanaComEventos([evento('sexta', new Date(2026, 9, 16, 20, 0))], quinta)

    expect(fds!.dias).toHaveLength(1)
    expect(fds!.dias[0].data.getDay()).toBe(5)
  })

  it('pula o fim de semana sem evento e acha o seguinte', () => {
    // O ponto da secao: "o proximo fim de semana COM evento", e nao "o proximo fim de semana".
    // Com a segunda leitura, a faixa ficaria vazia na maior parte do ano.
    const fds = proximoFimDeSemanaComEventos(
      [evento('tarde', new Date(2026, 9, 24, 18, 30))],
      quinta,
    )

    expect(fds!.inicio.getDate()).toBe(23)
    expect(fds!.dias.map((dia) => dia.data.getDate())).toEqual([24])
  })

  it('ignora evento de meio de semana', () => {
    const fds = proximoFimDeSemanaComEventos(
      [
        evento('quarta', new Date(2026, 9, 21, 20, 0)),
        evento('sabado', new Date(2026, 9, 17, 19, 0)),
      ],
      quinta,
    )

    expect(fds!.dias.flatMap((dia) => dia.eventos).map((e) => e.id)).toEqual(['sabado'])
  })

  it('nao mistura o fim de semana seguinte no mesmo bloco', () => {
    const fds = proximoFimDeSemanaComEventos(
      [
        evento('deste', new Date(2026, 9, 17, 19, 0)),
        evento('doProximo', new Date(2026, 9, 24, 19, 0)),
      ],
      quinta,
    )

    expect(fds!.dias.flatMap((dia) => dia.eventos).map((e) => e.id)).toEqual(['deste'])
    expect(fds!.fim.getDate()).toBe(18)
  })

  it('agrupa dois eventos do mesmo dia em ordem de horario', () => {
    const fds = proximoFimDeSemanaComEventos(
      [
        evento('noite', new Date(2026, 9, 17, 21, 0)),
        evento('tarde', new Date(2026, 9, 17, 16, 0)),
      ],
      quinta,
    )

    expect(fds!.dias).toHaveLength(1)
    expect(fds!.dias[0].eventos.map((e) => e.id)).toEqual(['tarde', 'noite'])
  })

  it('nao depende da lista chegar ordenada', () => {
    const emOrdem = proximoFimDeSemanaComEventos(
      [
        evento('sexta', new Date(2026, 9, 16, 20, 0)),
        evento('domingo', new Date(2026, 9, 18, 19, 0)),
      ],
      quinta,
    )
    const fora = proximoFimDeSemanaComEventos(
      [
        evento('domingo', new Date(2026, 9, 18, 19, 0)),
        evento('sexta', new Date(2026, 9, 16, 20, 0)),
      ],
      quinta,
    )

    expect(fora).toEqual(emOrdem)
  })

  it('ignora o que ja passou', () => {
    // Sabado as 19h, visto no domingo: o evento aconteceu, e anuncia-lo seria oferecer o que
    // nao existe mais.
    const domingo = new Date(2026, 9, 18, 10, 0)
    const fds = proximoFimDeSemanaComEventos(
      [
        evento('sabadoPassado', new Date(2026, 9, 17, 19, 0)),
        evento('hojeANoite', new Date(2026, 9, 18, 19, 0)),
      ],
      domingo,
    )

    expect(fds!.dias.flatMap((dia) => dia.eventos).map((e) => e.id)).toEqual(['hojeANoite'])
  })

  it('no proprio domingo, o bloco ainda e o que esta acontecendo', () => {
    const domingo = new Date(2026, 9, 18, 10, 0)
    const fds = proximoFimDeSemanaComEventos([evento('hoje', new Date(2026, 9, 18, 19, 0))], domingo)

    expect(fds!.inicio.getDate()).toBe(16)
    expect(fds!.fim.getDate()).toBe(18)
  })

  it('devolve nulo quando nenhum evento cai num fim de semana', () => {
    // A secao inteira sai da tela, em vez de aparecer vazia.
    const fds = proximoFimDeSemanaComEventos([evento('quarta', new Date(2026, 9, 21, 20, 0))], quinta)

    expect(fds).toBeNull()
  })

  it('devolve nulo para catalogo vazio', () => {
    expect(proximoFimDeSemanaComEventos([], quinta)).toBeNull()
  })

  it('cobre um fim de semana que vira o mes', () => {
    // 30 de outubro de 2026 e sexta; o bloco termina em 1 de novembro.
    const fds = proximoFimDeSemanaComEventos(
      [evento('virada', new Date(2026, 10, 1, 18, 0))],
      new Date(2026, 9, 28, 12, 0),
    )

    expect(fds!.inicio.getMonth()).toBe(9)
    expect(fds!.inicio.getDate()).toBe(30)
    expect(fds!.fim.getMonth()).toBe(10)
    expect(fds!.fim.getDate()).toBe(1)
  })
})

describe('chamadaDoDia', () => {
  it('tem uma chamada por dia do fim de semana', () => {
    expect(chamadaDoDia(new Date(2026, 9, 16))).toBe('Sexta, sem pressa')
    expect(chamadaDoDia(new Date(2026, 9, 17))).toBe('Sábado, sem relógio')
    expect(chamadaDoDia(new Date(2026, 9, 18))).toBe('Domingo, devagar')
  })

  it('tem saida para um dia fora do fim de semana', () => {
    expect(chamadaDoDia(new Date(2026, 9, 21))).toBe('Fim de semana')
  })
})
