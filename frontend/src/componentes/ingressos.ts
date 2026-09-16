import type { EventoResumo, Reserva } from '../api/tipos'

/**
 * Classificacao dos ingressos nas tres abas.
 *
 * <h2>"Utilizado" e derivado, e nao um estado no banco</h2>
 *
 * <p>O brief pede a aba "Utilizados". Um estado `USED` no `BookingStatus` exigiria quem o
 * disparasse, e nao ha catraca nem leitor na portaria — seria uma transicao que nenhum codigo
 * chama, ou seja, codigo morto. Decisao registrada no mapeamento, Fase 23.
 *
 * <p>No lugar dele: reserva confirmada cujo evento ja aconteceu. Responde a mesma pergunta que
 * a pessoa faz ao abrir a aba — "o que eu ja usei?" — sem inventar dominio.
 *
 * <p>O custo: um ingresso que a pessoa comprou e nao usou aparece como utilizado. Nao ha como
 * distinguir sem alguem registrar a entrada, e afirmar "nao compareceu" seria afirmar mais do
 * que o sistema sabe.
 */

export type Aba = 'proximos' | 'utilizados' | 'cancelados'

export const ROTULOS_DE_ABA: Record<Aba, string> = {
  proximos: 'Proximos',
  utilizados: 'Utilizados',
  cancelados: 'Cancelados',
}

/**
 * Em que aba esta reserva cai.
 *
 * <p>Sem a data do evento, uma reserva confirmada fica em "proximos". E o palpite menos
 * danoso: manda-la para "utilizados" esconderia um ingresso que talvez seja de hoje a noite,
 * enquanto o contrario apenas atrasa a mudanca de aba ate o evento carregar.
 */
export function abaDe(reserva: Reserva, evento: EventoResumo | undefined, agora: Date): Aba {
  if (reserva.status === 'CANCELLED' || reserva.status === 'EXPIRED') {
    return 'cancelados'
  }

  if (reserva.status === 'CONFIRMED' && evento && new Date(evento.eventDate) < agora) {
    return 'utilizados'
  }

  // PENDING tambem cai aqui: nao e ingresso ainda, mas e o item mais acionavel da lista, e
  // esconde-lo numa quarta aba faria a pessoa perder o prazo sem saber que ele estava correndo.
  return 'proximos'
}

/** Ids de evento distintos de uma pagina de reservas, para consultar cada um uma vez so. */
export function eventosDistintos(reservas: Reserva[]): string[] {
  return [...new Set(reservas.map((r) => r.eventId))]
}

/**
 * Link de "adicionar ao calendario", no formato do Google Agenda.
 *
 * <p>Um link, e nao um arquivo `.ics` gerado no cliente. O `.ics` cobre mais aplicativos e, no
 * celular — que e onde a pessoa guarda ingresso —, costuma cair na pasta de downloads sem abrir
 * agenda nenhuma. O link abre direto.
 *
 * <p>A duracao e de tres horas, arbitrada: o evento nao informa quando termina. Um bloco de
 * duracao zero some na visualizacao de mes da maioria das agendas.
 */
export function linkDeCalendario(evento: EventoResumo, codigo: string | null): string {
  const inicio = new Date(evento.eventDate)
  const fim = new Date(inicio.getTime() + 3 * 60 * 60 * 1000)

  // O formato do Google e UTC compactado: 20260923T210000Z.
  const formatar = (data: Date) => data.toISOString().replace(/[-:]|\.\d{3}/g, '')

  const parametros = new URLSearchParams({
    action: 'TEMPLATE',
    text: evento.name,
    dates: `${formatar(inicio)}/${formatar(fim)}`,
    location: evento.venue,
    details: codigo ? `Ingresso ${codigo} — ticket.platform` : 'ticket.platform',
  })

  return `https://calendar.google.com/calendar/render?${parametros}`
}
