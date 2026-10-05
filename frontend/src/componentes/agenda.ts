import type { EventoResumo } from '../api/tipos'

/*
  Agenda de fim de semana.

  ----------------------------------------------------------------------------
   O proximo fim de semana COM EVENTO, e nao o proximo fim de semana
  ----------------------------------------------------------------------------
  O design mostra a secao com um periodo cravado — "16 — 18 OUT / 2026" — e tres dias
  preenchidos. Ler isso como "o fim de semana que vem" deixaria a secao vazia na maior parte do
  ano: o catalogo nao tem evento todo sabado, e uma faixa inteira da home anunciando "o fim de
  semana pede presenca" sem nada embaixo parece defeito, nao agenda.

  Entao a regra e outra: encontrar o PROXIMO fim de semana que tenha programacao e mostrar esse.
  A secao so desaparece quando nao houver nenhum — e nesse caso a tela diz isso em palavras, em
  vez de deixar o buraco.

  ----------------------------------------------------------------------------
   Fim de semana comeca na sexta
  ----------------------------------------------------------------------------
  Sexta, sabado e domingo. Nao e a definicao do calendario, e e a de quem procura programa: o
  show de sexta a noite e tao de fim de semana quanto o de sabado. O design confirma a leitura —
  a faixa dele vai de 16 (sexta) a 18 (domingo), e o painel de destaque fala de "sexta, sem
  pressa".

  As funcoes recebem `agora`, pelo mesmo motivo registrado em `intervalos.ts`: assim o teste fixa
  um instante em vez de congelar o relogio do processo.
*/

/** Indices de `getDay`: sexta, sabado, domingo. */
const SEXTA = 5
const SABADO = 6
const DOMINGO = 0

function inicioDoDia(data: Date): Date {
  const d = new Date(data)
  d.setHours(0, 0, 0, 0)
  return d
}

/** Fim do dia e o instante ANTES da meia-noite seguinte, como em `intervalos.ts`. */
function fimDoDia(data: Date): Date {
  const d = inicioDoDia(data)
  d.setDate(d.getDate() + 1)
  return new Date(d.getTime() - 1)
}

function ehFimDeSemana(data: Date): boolean {
  const dia = data.getDay()
  return dia === SEXTA || dia === SABADO || dia === DOMINGO
}

/**
 * A sexta-feira do bloco em que esta data cai.
 *
 * <p>Calculado por subtracao de dias no objeto `Date`, e nao em milissegundos: com aritmetica de
 * milissegundos, um bloco que atravessa a virada do horario de verao erraria em uma hora e a
 * sexta cairia na quinta a noite. `setDate` trabalha no calendario local, onde o dia pode ter
 * 23 ou 25 horas.
 */
function sextaDoBloco(data: Date): Date {
  const recuo = { [SEXTA]: 0, [SABADO]: 1, [DOMINGO]: 2 }[data.getDay()] ?? 0

  const sexta = inicioDoDia(data)
  sexta.setDate(sexta.getDate() - recuo)
  return sexta
}

/** O domingo dois dias depois da sexta, pelo calendario e pela mesma razao de `sextaDoBloco`. */
function domingoDoBloco(sexta: Date): Date {
  const domingo = new Date(sexta)
  domingo.setDate(domingo.getDate() + 2)
  return domingo
}

/** Um dia da agenda, com o que acontece nele. */
export interface DiaDaAgenda {
  /** Meia-noite local do dia. */
  data: Date
  /** Em ordem de horario. Nunca vazio: um dia sem evento nao vira um `DiaDaAgenda`. */
  eventos: EventoResumo[]
}

/** O fim de semana que a secao exibe. */
export interface FimDeSemana {
  /** Meia-noite da sexta. */
  inicio: Date
  /** Ultimo instante do domingo. */
  fim: Date
  /** Somente os dias que tem programacao, da sexta para o domingo. */
  dias: DiaDaAgenda[]
}

/**
 * O proximo fim de semana com programacao.
 *
 * <p>Devolve nulo quando nenhum evento futuro cai numa sexta, sabado ou domingo — e a secao
 * inteira sai da tela em vez de aparecer vazia.
 *
 * <p>Nao exige que a lista chegue ordenada: ela vem do catalogo em data crescente, e depender
 * disso silenciosamente faria a secao escolher o fim de semana errado no dia em que alguem
 * reordenasse a lista antes de passa-la por aqui — um defeito de leitura dificil, porque a
 * secao continuaria desenhando normalmente.
 */
export function proximoFimDeSemanaComEventos(
  eventos: EventoResumo[],
  agora: Date = new Date(),
): FimDeSemana | null {
  const candidatos = eventos
    .map((evento) => ({ evento, quando: new Date(evento.eventDate) }))
    .filter(({ quando }) => quando.getTime() >= agora.getTime() && ehFimDeSemana(quando))
    .sort((a, b) => a.quando.getTime() - b.quando.getTime())

  const primeiro = candidatos[0]
  if (!primeiro) {
    return null
  }

  const inicio = sextaDoBloco(primeiro.quando)
  const fim = fimDoDia(domingoDoBloco(inicio))

  // Agrupa por dia do mes. A chave e o dia local, e nao a data em ISO: em ISO, um evento de
  // domingo as 21h em Sao Paulo cai na segunda-feira UTC e abriria um quarto dia na agenda.
  const porDia = new Map<number, EventoResumo[]>()
  for (const { evento, quando } of candidatos) {
    if (quando.getTime() > fim.getTime()) continue

    const chave = inicioDoDia(quando).getTime()
    porDia.set(chave, [...(porDia.get(chave) ?? []), evento])
  }

  const dias = [...porDia.entries()]
    .sort(([a], [b]) => a - b)
    .map(([chave, eventosDoDia]) => ({ data: new Date(chave), eventos: eventosDoDia }))

  return { inicio, fim, dias }
}

/**
 * Chamada editorial de cada dia do fim de semana.
 *
 * <p>O design escreve "SEXTA, SEM PRESSA" sobre o painel de destaque. Um texto por evento seria
 * curadoria escrita a mao, que um catalogo gerado por seed nao tem como ter; um texto unico para
 * os tres dias perderia a graca da secao. O meio-termo e um por DIA da semana: sao tres, cabem
 * aqui, e cada um continua valendo para qualquer evento que caia naquele dia.
 */
const CHAMADAS: Record<number, string> = {
  [SEXTA]: 'Sexta, sem pressa',
  [SABADO]: 'Sábado, sem relógio',
  [DOMINGO]: 'Domingo, devagar',
}

export function chamadaDoDia(data: Date): string {
  return CHAMADAS[data.getDay()] ?? 'Fim de semana'
}
