import { describe, expect, it } from 'vitest'
import {
  dataDeCartaz,
  deCampoLocal,
  duracao,
  faixaDeDias,
  mesEAno,
  paraCampoLocal,
  partesDaData,
} from './formato'

/*
  As datas destes testes sao construidas pelo construtor de horario LOCAL — `new Date(2026, 9,
  18, 19)` —, e nao por string ISO com `Z`. Os formatadores exibem no fuso do navegador, e com
  `'2026-10-18T19:00:00Z'` o resultado esperado mudaria de maquina para maquina: 19H em Londres,
  16H em Sao Paulo. Com o construtor local, "dezenove horas do dia 18" e dezenove horas do dia
  18 em qualquer fuso que rode a suite.

  O mes vai com o indice do JavaScript, em que 9 e outubro.
*/

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

describe('partesDaData', () => {
  it('separa dia, mes, semana e hora do cartaz', () => {
    expect(partesDaData(new Date(2026, 9, 18, 19, 0))).toEqual({
      dia: '18',
      mes: 'OUT',
      semana: 'DOM',
      hora: '19H',
    })
  })

  it('omite o minuto na hora cheia e o mostra fora dela', () => {
    // `19H00` repetido em seis cartoes seguidos e ruido; `19H30` e informacao.
    expect(partesDaData(new Date(2026, 9, 17, 19, 30)).hora).toBe('19H30')
    expect(partesDaData(new Date(2026, 9, 17, 19, 0)).hora).toBe('19H')
  })

  it('tira o ponto da abreviacao que o Intl devolve', () => {
    // O Intl em pt-BR devolve `out.` e `sáb.`; num cartaz em caixa alta o ponto vira sujeira
    // entre dois separadores.
    const partes = partesDaData(new Date(2026, 9, 17, 20, 0))
    expect(partes.mes).not.toContain('.')
    expect(partes.semana).not.toContain('.')
  })

  it('mantem a acentuacao que o Intl gera', () => {
    // Sabado abreviado leva acento em pt-BR. O arquivo de origem nao tem literal acentuado —
    // quem acentua e o Intl, em tempo de execucao —, e apagar isso seria escrever errado.
    expect(partesDaData(new Date(2026, 9, 17, 20, 0)).semana).toBe('SÁB')
  })

  it('aceita string ISO alem de Date', () => {
    const instante = new Date(2026, 9, 18, 19, 0)
    expect(partesDaData(instante.toISOString())).toEqual(partesDaData(instante))
  })
})

describe('dataDeCartaz', () => {
  it('monta a linha do cartao do catalogo', () => {
    expect(dataDeCartaz(new Date(2026, 9, 18, 19, 0))).toBe('18 OUT · DOM · 19H')
  })
})

describe('mesEAno', () => {
  it('sobe o periodo para versalete', () => {
    expect(mesEAno(new Date(2026, 9, 1, 12, 0))).toBe('OUTUBRO 2026')
  })
})

describe('faixaDeDias', () => {
  it('nao repete o mes quando as duas pontas caem nele', () => {
    expect(faixaDeDias(new Date(2026, 9, 16), new Date(2026, 9, 18))).toBe('16 — 18 OUT')
  })

  it('mostra os dois meses quando o periodo vira o mes', () => {
    // Um fim de semana de 30 de outubro a 1 de novembro existe, e `30 — 01 OUT` seria uma
    // data que nao aconteceu.
    expect(faixaDeDias(new Date(2026, 9, 30), new Date(2026, 10, 1))).toBe('30 OUT — 01 NOV')
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
