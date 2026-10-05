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

export type Atalho = 'hoje' | 'amanha' | 'semana' | 'fim-de-semana' | 'proximos'

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
 * O proximo fim de semana: sexta, sabado e domingo.
 *
 * <p>Se hoje ja for um dos tres, o fim de semana e o que esta acontecendo — nao o da semana que
 * vem. Alguem procurando programa no sabado a tarde quer o de hoje a noite.
 *
 * <h2>Por que a sexta entra</h2>
 *
 * <p>Nao e a definicao do calendario, e e a de quem procura programa: o show de sexta a noite e
 * tao de fim de semana quanto o de sabado.
 *
 * <p>Havia um motivo mais concreto para mudar. A agenda da home — `agenda.ts` — agrupa de sexta
 * a domingo, porque e o que o design desenha. Com este atalho comecando no sabado, a mesma
 * pagina dizia "fim de semana" com dois sentidos: a faixa da agenda anunciava tres eventos e o
 * botao "este fim de semana", logo acima, devolvia dois. As duas definicoes agora sao a mesma.
 */
function fimDeSemana(agora: Date): Intervalo {
  const diaDaSemana = agora.getDay() // 0 domingo, 5 sexta, 6 sabado

  // Ja dentro do bloco: vale de agora ate o domingo que o fecha.
  if (diaDaSemana === 5 || diaDaSemana === 6 || diaDaSemana === 0) {
    const ateODomingo = { 5: 2, 6: 1, 0: 0 }[diaDaSemana] ?? 0
    return { de: agora.toISOString(), ate: fimDoDia(soma(agora, ateODomingo)).toISOString() }
  }

  // Segunda a quinta: o bloco e a proxima sexta ate o domingo seguinte.
  const sexta = soma(agora, 5 - diaDaSemana)
  return { de: inicioDoDia(sexta).toISOString(), ate: fimDoDia(soma(sexta, 2)).toISOString() }
}

function soma(data: Date, dias: number): Date {
  return new Date(data.getTime() + dias * DIA)
}

/**
 * Os proximos sete dias.
 *
 * <p>"Esta semana" conta a partir de AGORA, e nao da segunda-feira passada: quem abre a home
 * numa quinta e filtra por esta semana quer saber o que da para fazer ainda, e um intervalo
 * ancorado no calendario devolveria dois dias de programacao e chamaria isso de semana.
 */
function semana(agora: Date): Intervalo {
  return { de: agora.toISOString(), ate: fimDoDia(soma(agora, 6)).toISOString() }
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

    case 'semana':
      return semana(agora)

    case 'fim-de-semana':
      return fimDeSemana(agora)

    case 'proximos':
      // Sem limite superior: e o padrao do catalogo, tudo daqui para a frente.
      return { de: agora.toISOString() }
  }
}

export const ROTULOS_DE_ATALHO: Record<Atalho, string> = {
  hoje: 'Hoje',
  amanha: 'Amanhã',
  semana: 'Esta semana',
  'fim-de-semana': 'Este fim de semana',
  proximos: 'Próximos eventos',
}

/**
 * Um dia escolhido no calendario, a partir do `yyyy-mm-dd` que o `input[type=date]` devolve.
 *
 * <p>A string e dividida a mao em vez de ir para `new Date(valor)`. O construtor interpreta
 * `'2026-10-18'` como meia-noite UTC — pela especificacao, a forma so-data e tratada como UTC —,
 * e em Sao Paulo isso cai as 21h do dia 17: quem escolhesse 18 de outubro receberia a
 * programacao da noite anterior.
 *
 * <p>Devolve o intervalo aberto quando a string nao esta completa, que e o estado do campo
 * enquanto alguem ainda digita a data.
 */
export function intervaloDoDia(valor: string, agora: Date = new Date()): Intervalo {
  const [ano, mes, dia] = valor.split('-').map(Number)
  if (!ano || !mes || !dia) {
    return intervaloDe('proximos', agora)
  }

  const escolhido = new Date(ano, mes - 1, dia)

  // Um dia que ja comecou vale do instante atual em diante, pela mesma razao registrada em
  // `intervaloDe`: um evento das dez da manha nao esta a venda as dez da noite.
  const inicio = escolhido.getTime() > agora.getTime() ? escolhido : agora

  return { de: inicio.toISOString(), ate: fimDoDia(escolhido).toISOString() }
}
