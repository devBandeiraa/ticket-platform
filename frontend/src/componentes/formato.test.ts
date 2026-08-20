import { describe, expect, it } from 'vitest'
import { deCampoLocal, duracao, paraCampoLocal } from './formato'

describe('duracao', () => {
  it('formata minutos e segundos', () => {
    expect(duracao(90_000)).toBe('01:30')
  })

  it('acrescenta a hora quando passa de sessenta minutos', () => {
    expect(duracao(3_661_000)).toBe('1:01:01')
  })

  it('nao mostra tempo negativo', () => {
    // A contagem regressiva chega ao fim e continua rodando por um tick. Um "-00:01" na tela
    // pareceria defeito.
    expect(duracao(-5000)).toBe('00:00')
  })

  it('mantem dois digitos, para o numero nao dancar a cada segundo', () => {
    expect(duracao(9000)).toBe('00:09')
  })
})

describe('campo de data local', () => {
  it('vai e volta sem perder o instante', () => {
    const original = '2026-08-20T15:30:00.000Z'

    // O `datetime-local` trabalha em horario local e nao aceita fuso; a ida e a volta
    // precisam se cancelar, ou editar um evento sem tocar na data mudaria a data.
    expect(deCampoLocal(paraCampoLocal(original))).toBe(original)
  })
})
