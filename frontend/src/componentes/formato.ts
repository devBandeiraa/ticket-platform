/*
  Formatacao para leitura humana. Concentrada aqui porque data e dinheiro aparecem em quase
  toda tela, e cada copia solta seria uma chance de exibir moeda ou fuso diferente.

  As datas chegam da API em UTC e sao exibidas no fuso do navegador — a conversao acontece
  aqui, e so aqui.
*/

const DATA_E_HORA = new Intl.DateTimeFormat('pt-BR', {
  dateStyle: 'medium',
  timeStyle: 'short',
})

const DINHEIRO = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' })

export function dataEHora(iso: string): string {
  return DATA_E_HORA.format(new Date(iso))
}

export function dinheiro(valor: number): string {
  return DINHEIRO.format(valor)
}

/*
  --------------------------------------------------------------------------------------------
   Data em formato de cartaz
  --------------------------------------------------------------------------------------------
  O design apresenta a data como um cartaz de programacao, e nao como um campo de formulario:
  `18 OUT · DOM · 19H` na linha do cartao, e o dia e o mes empilhados em corpo grande no evento
  em destaque. `dataEHora` nao serve para isso — devolve `18 de out. de 2026 19:00`, que e a
  forma certa para um recibo e a errada para uma chamada editorial.

  As partes saem do `Intl`, e nao de uma tabela de meses escrita a mao. A tabela e tentadora
  porque sao doze palavras curtas, e custa o que toda duplicacao de locale custa: o dia da
  semana teria de vir junto, com as abreviacoes certas, e os dois conjuntos envelheceriam
  separados do resto da folha, que ja formata tudo por `Intl`.

  Daqui saem rotulos ACENTUADOS — `SÁB`, `MARÇO` —, porque e o `Intl` que os gera em tempo de
  execucao. Nao ha literal acentuado neste arquivo.
*/

const PARTES = new Intl.DateTimeFormat('pt-BR', {
  day: '2-digit',
  month: 'short',
  weekday: 'short',
  hour: '2-digit',
  minute: '2-digit',
})

const MES_E_ANO = new Intl.DateTimeFormat('pt-BR', { month: 'long', year: 'numeric' })

/**
 * Tira o ponto das abreviacoes e sobe para versalete.
 *
 * <p>O `Intl` devolve `out.` e `dom.`, com ponto. Num cartaz em caixa alta o ponto vira sujeira
 * visual entre dois separadores — `18 OUT. · DOM. · 19H` —, entao ele sai aqui.
 */
function versalete(parte: string): string {
  return parte.replace(/.$/, '').toUpperCase()
}

/** Partes de uma data, cada uma pronta para ir sozinha a tela. */
export interface PartesDaData {
  /** Dia do mes, com dois digitos: `18`. */
  dia: string
  /** Mes abreviado em versalete: `OUT`. */
  mes: string
  /** Dia da semana abreviado em versalete: `DOM`. */
  semana: string
  /** Hora no formato do cartaz: `19H` em hora cheia, `19H30` fora dela. */
  hora: string
}

/**
 * Quebra a data nas quatro partes que o design posiciona separadamente.
 *
 * <p>Separadas, e nao numa string so, porque o evento em destaque empilha `OUT` sobre `18` em
 * corpos diferentes — e um `split` no texto formatado para reconstruir isso seria desfazer na
 * tela o trabalho que o formatador acabou de fazer.
 */
export function partesDaData(iso: string | Date): PartesDaData {
  const data = typeof iso === 'string' ? new Date(iso) : iso
  const partes = PARTES.formatToParts(data)
  const pegar = (tipo: Intl.DateTimeFormatPartTypes) =>
    partes.find((parte) => parte.type === tipo)?.value ?? ''

  const minuto = pegar('minute')

  return {
    dia: pegar('day'),
    mes: versalete(pegar('month')),
    semana: versalete(pegar('weekday')),
    // `19H` e nao `19H00`: a hora cheia e a maioria dos eventos, e o `00` repetido em seis
    // cartoes seguidos vira ruido. O minuto aparece so quando existe de fato.
    hora: minuto === '00' ? `${pegar('hour')}H` : `${pegar('hour')}H${minuto}`,
  }
}

/** A linha de data do cartao do catalogo: `18 OUT · DOM · 19H`. */
export function dataDeCartaz(iso: string | Date): string {
  const { dia, mes, semana, hora } = partesDaData(iso)
  return `${dia} ${mes} · ${semana} · ${hora}`
}

/**
 * Cabecalho de periodo: `OUTUBRO 2026`.
 *
 * <p>Montado a partir das partes, e nao do texto formatado. Em pt-BR o `Intl` devolve
 * `outubro de 2026`, com a preposicao, e o design pede os dois campos justapostos — tirar o
 * ` de ` com `replace` funcionaria aqui e quebraria no dia em que alguem trocasse o locale,
 * porque a preposicao e a posicao dela mudam com o idioma.
 */
export function mesEAno(iso: string | Date): string {
  const data = typeof iso === 'string' ? new Date(iso) : iso
  const partes = MES_E_ANO.formatToParts(data)
  const pegar = (tipo: Intl.DateTimeFormatPartTypes) =>
    partes.find((parte) => parte.type === tipo)?.value ?? ''

  return `${pegar('month')} ${pegar('year')}`.toUpperCase()
}

/**
 * Faixa de dias de um fim de semana: `16 — 18 OUT`, ou `30 OUT — 01 NOV` quando ela vira o mes.
 *
 * <p>Repetir o mes nas duas pontas de `16 — 18 OUT` seria redundante; omiti-lo quando o periodo
 * atravessa a virada do mes seria errado. Dai o teste nas duas formas.
 */
export function faixaDeDias(inicio: Date, fim: Date): string {
  const a = partesDaData(inicio)
  const b = partesDaData(fim)
  return a.mes === b.mes
    ? `${a.dia} — ${b.dia} ${a.mes}`
    : `${a.dia} ${a.mes} — ${b.dia} ${b.mes}`
}

/** Formato de contagem regressiva: `mm:ss`, ou `h:mm:ss` quando passa de uma hora. */
export function duracao(milissegundos: number): string {
  const total = Math.max(0, Math.floor(milissegundos / 1000))
  const horas = Math.floor(total / 3600)
  const minutos = Math.floor((total % 3600) / 60)
  const segundos = total % 60

  const doisDigitos = (n: number) => String(n).padStart(2, '0')

  return horas > 0
    ? `${horas}:${doisDigitos(minutos)}:${doisDigitos(segundos)}`
    : `${doisDigitos(minutos)}:${doisDigitos(segundos)}`
}

/**
 * Ha quanto tempo algo esta de pe, em texto curto: `2d 4h`, `3h 12min`, `45s`.
 *
 * So as duas maiores unidades. "2d 4h 17min 3s" e preciso e ilegivel — quem olha um painel de
 * status quer saber se o servico subiu agora ou esta ha dias no ar, e a terceira casa nao muda
 * essa resposta.
 */
export function tempoNoAr(segundos: number): string {
  const total = Math.max(0, Math.floor(segundos))

  const dias = Math.floor(total / 86400)
  const horas = Math.floor((total % 86400) / 3600)
  const minutos = Math.floor((total % 3600) / 60)

  if (dias > 0) return `${dias}d ${horas}h`
  if (horas > 0) return `${horas}h ${minutos}min`
  if (minutos > 0) return `${minutos}min`
  return `${total}s`
}

/** Para o `datetime-local`, que nao aceita ISO com fuso e trabalha em horario local. */
export function paraCampoLocal(iso: string): string {
  const data = new Date(iso)
  const deslocado = new Date(data.getTime() - data.getTimezoneOffset() * 60000)
  return deslocado.toISOString().slice(0, 16)
}

/** Caminho inverso: o que o `datetime-local` devolve vira instante UTC para a API. */
export function deCampoLocal(valor: string): string {
  return new Date(valor).toISOString()
}
