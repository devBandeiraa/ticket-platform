import { describe, expect, it } from 'vitest'
import { intervaloDe } from './intervalos'

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

  it('numa quarta, o fim de semana e o sabado e domingo seguintes', () => {
    // 2026-09-16 e uma quarta-feira.
    const quarta = new Date('2026-09-16T12:00:00')
    expect(quarta.getDay()).toBe(3)

    const { de, ate } = intervaloDe('fim-de-semana', quarta)

    expect(new Date(de!).getDay()).toBe(6)
    expect(new Date(ate!).getDay()).toBe(0)
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

  it('proximos eventos nao tem limite superior', () => {
    const agora = new Date('2026-09-16T12:00:00')
    const intervalo = intervaloDe('proximos', agora)

    expect(new Date(intervalo.de!).getTime()).toBe(agora.getTime())
    expect(intervalo.ate).toBeUndefined()
  })

  it('o limite inferior nunca fica no passado', () => {
    const agora = new Date('2026-09-16T12:00:00')

    for (const atalho of ['hoje', 'amanha', 'fim-de-semana', 'proximos'] as const) {
      const { de } = intervaloDe(atalho, agora)
      expect(new Date(de!).getTime()).toBeGreaterThanOrEqual(agora.getTime())
    }
  })
})
