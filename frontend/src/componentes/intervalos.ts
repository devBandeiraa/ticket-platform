/**
 * Atalhos de data da descoberta: hoje, amanha, este fim de semana.
 *
 * Moram no cliente, e nao no servidor, porque dependem do relogio de quem consulta. "Hoje" em
 * Sao Paulo comeca tres horas depois de "hoje" em UTC, e um servidor que decidisse isso mandaria
 * para todo mundo o dia dele. A API recebe um par de instantes absolutos, que nao tem essa
 * ambiguidade — ver `listarPublicados`.
 *
 * Funcoes puras recebendo `agora`, em vez de chamarem `new Date()` por dentro: assim o teste fixa
 * um instante e verifica o resultado, sem precisar congelar o relogio do processo.
 */

export type Atalho = 'hoje' | 'amanha' | 'fim-de-semana' | 'proximos'

/** Limites de uma consulta ao catalogo. Ausente significa "sem limite daquele lado". */
export interface Intervalo {
  de?: string
  ate?: string
}

const DIA = 24 * 60 * 60 * 1000

function inicioDoDia(data: Date): Date {
  const d = new Date(data)
  d.setHours(0, 0, 0, 0)
  return d
}

/** Fim do dia e o instante ANTES da meia-noite seguinte, e nao 23:59:59. */
function fimDoDia(data: Date): Date {
  const d = inicioDoDia(data)
  d.setDate(d.getDate() + 1)
  return new Date(d.getTime() - 1)
}

/**
 * O proximo sabado e domingo.
 *
 * <p>Se hoje ja for sabado ou domingo, o fim de semana e o que esta acontecendo — nao o da
 * semana que vem. Alguem procurando programa no sabado a tarde quer o de hoje a noite.
 */
function fimDeSemana(agora: Date): Intervalo {
  const diaDaSemana = agora.getDay() // 0 domingo, 6 sabado

  if (diaDaSemana === 6) {
    return { de: agora.toISOString(), ate: fimDoDia(soma(agora, 1)).toISOString() }
  }
  if (diaDaSemana === 0) {
    return { de: agora.toISOString(), ate: fimDoDia(agora).toISOString() }
  }

  const sabado = soma(agora, 6 - diaDaSemana)
  return { de: inicioDoDia(sabado).toISOString(), ate: fimDoDia(soma(sabado, 1)).toISOString() }
}

function soma(data: Date, dias: number): Date {
  return new Date(data.getTime() + dias * DIA)
}

/**
 * Traduz o atalho num par de instantes.
 *
 * <p>O limite inferior nunca e menor que `agora`: um evento que ja comecou nao esta a venda, e
 * mostrar a manha de hoje as dez da noite seria oferecer o que nao existe mais.
 */
export function intervaloDe(atalho: Atalho, agora: Date = new Date()): Intervalo {
  switch (atalho) {
    case 'hoje':
      return { de: agora.toISOString(), ate: fimDoDia(agora).toISOString() }

    case 'amanha': {
      const amanha = soma(agora, 1)
      return { de: inicioDoDia(amanha).toISOString(), ate: fimDoDia(amanha).toISOString() }
    }

    case 'fim-de-semana':
      return fimDeSemana(agora)

    case 'proximos':
      // Sem limite superior: e o padrao do catalogo, tudo daqui para a frente.
      return { de: agora.toISOString() }
  }
}

export const ROTULOS_DE_ATALHO: Record<Atalho, string> = {
  hoje: 'Hoje',
  amanha: 'Amanha',
  'fim-de-semana': 'Este fim de semana',
  proximos: 'Proximos eventos',
}
