import { describe, expect, it } from 'vitest'
import { intervaloDe, intervaloDoDia } from './intervalos'

/**
 * Os atalhos sao calculados no fuso do navegador, entao os testes fixam o instante e comparam
 * com limites tambem calculados localmente. Comparar com string ISO cravada faria a suite
 * passar na minha maquina e falhar no runner, que roda em UTC.
 */
function meiaNoiteLocal(data: Date): number {
  const d = new Date(data)
  d.setHours(0, 0, 0, 0)
  return d.getTime()
}

describe('intervaloDe', () => {
  it('hoje comeca AGORA, e nao a meia-noite', () => {
    const agora = new Date('2026-09-16T22:00:00')
    const { de, ate } = intervaloDe('hoje', agora)

    // Um evento das dez da manha nao esta a venda as dez da noite. Comecar o intervalo a
    // meia-noite ofereceria o que ja aconteceu.
    expect(new Date(de!).getTime()).toBe(agora.getTime())
    expect(new Date(ate!).getTime()).toBe(meiaNoiteLocal(agora) + 24 * 60 * 60 * 1000 - 1)
  })

  it('amanha cobre o dia inteiro seguinte', () => {
    const agora = new Date('2026-09-16T22:00:00')
    const { de, ate } = intervaloDe('amanha', agora)

    const inicioDeAmanha = meiaNoiteLocal(agora) + 24 * 60 * 60 * 1000
    expect(new Date(de!).getTime()).toBe(inicioDeAmanha)
    expect(new Date(ate!).getTime()).toBe(inicioDeAmanha + 24 * 60 * 60 * 1000 - 1)
  })

  it('numa quarta, o fim de semana vai da sexta ao domingo seguintes', () => {
    // 2026-09-16 e uma quarta-feira.
    const quarta = new Date('2026-09-16T12:00:00')
    expect(quarta.getDay()).toBe(3)

    const { de, ate } = intervaloDe('fim-de-semana', quarta)

    expect(new Date(de!).getDay()).toBe(5)
    expect(new Date(ate!).getDay()).toBe(0)
  })

  it('a sexta conta como fim de semana', () => {
    // Mesma definicao de `agenda.ts`, e de proposito: com o atalho comecando no sabado, a home
    // dizia "fim de semana" com dois sentidos — a agenda mostrava tres eventos e o botao logo
    // acima devolvia dois.
    const quinta = new Date('2026-09-17T12:00:00')
    const { de } = intervaloDe('fim-de-semana', quinta)

    expect(new Date(de!).getDay()).toBe(5)
    expect(new Date(de!).getDate()).toBe(18)
  })

  it('na propria sexta, o fim de semana e o que esta comecando', () => {
    const sexta = new Date('2026-09-18T19:00:00')
    expect(sexta.getDay()).toBe(5)

    const { de, ate } = intervaloDe('fim-de-semana', sexta)

    expect(new Date(de!).getTime()).toBe(sexta.getTime())
    expect(new Date(ate!).getDay()).toBe(0)
    expect(new Date(ate!).getDate()).toBe(20)
  })

  it('no proprio sabado, o fim de semana e o que esta acontecendo', () => {
    const sabado = new Date('2026-09-19T15:00:00')
    expect(sabado.getDay()).toBe(6)

    const { de, ate } = intervaloDe('fim-de-semana', sabado)

    // Quem procura programa no sabado a tarde quer o de hoje a noite, e nao o da semana que vem.
    expect(new Date(de!).getTime()).toBe(sabado.getTime())
    expect(new Date(ate!).getDay()).toBe(0)
  })

  it('no domingo, o fim de semana termina hoje', () => {
    const domingo = new Date('2026-09-20T10:00:00')
    expect(domingo.getDay()).toBe(0)

    const { de, ate } = intervaloDe('fim-de-semana', domingo)

    expect(new Date(de!).getTime()).toBe(domingo.getTime())
    expect(new Date(ate!).getDay()).toBe(0)
    expect(new Date(ate!).getTime()).toBeGreaterThan(domingo.getTime())
  })

  it('esta semana cobre os proximos sete dias a partir de agora', () => {
    const quinta = new Date('2026-09-17T18:00:00')
    const { de, ate } = intervaloDe('semana', quinta)

    // Ancorada em agora, e nao na segunda passada: numa quinta, uma semana ancorada no
    // calendario devolveria dois dias e os chamaria de semana.
    expect(new Date(de!).getTime()).toBe(quinta.getTime())
    expect(new Date(ate!).getTime()).toBe(meiaNoiteLocal(quinta) + 7 * 24 * 60 * 60 * 1000 - 1)
  })

  it('proximos eventos nao tem limite superior', () => {
    const agora = new Date('2026-09-16T12:00:00')
    const intervalo = intervaloDe('proximos', agora)

    expect(new Date(intervalo.de!).getTime()).toBe(agora.getTime())
    expect(intervalo.ate).toBeUndefined()
  })

  it('o limite inferior nunca fica no passado', () => {
    const agora = new Date('2026-09-16T12:00:00')

    for (const atalho of ['hoje', 'amanha', 'semana', 'fim-de-semana', 'proximos'] as const) {
      const { de } = intervaloDe(atalho, agora)
      expect(new Date(de!).getTime()).toBeGreaterThanOrEqual(agora.getTime())
    }
  })
})

describe('intervaloDoDia', () => {
  it('cobre o dia escolhido no fuso de quem escolheu', () => {
    const agora = new Date('2026-09-16T12:00:00')
    const { de, ate } = intervaloDoDia('2026-10-18', agora)

    // O ponto do teste: `new Date('2026-10-18')` seria meia-noite UTC, que em Sao Paulo cai no
    // dia 17. Quem escolhe 18 de outubro tem de receber o dia 18 inteiro, e so ele.
    expect(new Date(de!).getDate()).toBe(18)
    expect(new Date(de!).getHours()).toBe(0)
    expect(new Date(ate!).getDate()).toBe(18)
    expect(new Date(ate!).getHours()).toBe(23)
  })

  it('num dia que ja comecou, conta a partir de agora', () => {
    const agora = new Date('2026-10-18T20:00:00')
    const { de } = intervaloDoDia('2026-10-18', agora)

    expect(new Date(de!).getTime()).toBe(agora.getTime())
  })

  it('devolve o intervalo aberto enquanto a data esta incompleta', () => {
    // Estado real do campo: `input[type=date]` entrega string vazia ate a data fechar.
    const agora = new Date('2026-09-16T12:00:00')

    expect(intervaloDoDia('', agora)).toEqual(intervaloDe('proximos', agora))
    expect(intervaloDoDia('2026-10', agora)).toEqual(intervaloDe('proximos', agora))
  })
})
