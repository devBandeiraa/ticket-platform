import { useEffect, useRef } from 'react'
import type { EventoResumo, Reserva } from '../api/tipos'
import { CodigoQr } from './CodigoQr'
import { Ticket } from './Ticket'
import { dataEHora } from './formato'

/**
 * O ingresso de uma reserva paga, desenhado.
 *
 * <p>E aqui que o `Ticket` da Fase 24 finalmente encontra o uso para o qual foi construido: ate
 * agora ele so aparecia como vitrine no hero, com dados fixos.
 *
 * <p>O QR carrega o codigo emitido pelo servidor, e nao uma URL. Um endereco exigiria rede na
 * portaria e viraria um ingresso invalido quando o sinal cai justamente na fila da entrada; o
 * codigo e conferivel contra a lista, e a lista pode estar em cache.
 */
export function IngressoDigital({
  reserva,
  evento,
  comprador,
  compacto = false,
}: {
  reserva: Reserva
  evento?: EventoResumo
  comprador?: string | null
  /** Miniatura da lista: sem QR e sem titular, porque a 220px nada disso se le. */
  compacto?: boolean
}) {
  const primeiro = reserva.seats[0]

  // Um ingresso por RESERVA, e nao por lugar. A reserva e a unidade que foi paga e a que tem
  // codigo; separar em quatro ingressos daria a impressao de quatro codigos, e ha um so.
  const lugares =
    reserva.seats.length === 1
      ? primeiro?.label
      : `${reserva.seats.length} lugares · ${primeiro?.sector ?? ''}`

  return (
    <Ticket
      titulo={evento?.name ?? 'Evento'}
      local={evento?.venue}
      setor={lugares}
      data={evento ? dataEHora(evento.eventDate) : undefined}
      codigo={reserva.ticketCode ?? undefined}
      comprador={compacto ? undefined : (comprador ?? undefined)}
      canhoto={
        compacto ? (
          <span
            aria-hidden="true"
            className="text-[0.55rem] font-semibold uppercase leading-tight tracking-[0.2em] text-noite-suave [writing-mode:vertical-rl]"
          >
            Admit one
          </span>
        ) : reserva.ticketCode ? (
          <CodigoQr valor={reserva.ticketCode} tamanho={110} />
        ) : undefined
      }
    />
  )
}

/**
 * Ingresso em tamanho cheio, num dialogo.
 *
 * <p>`<dialog>` nativo, e nao uma biblioteca de modal. O elemento ja traz o que e dificil de
 * acertar a mao: prende o foco dentro dele, fecha no `Esc`, marca o resto da pagina como inerte
 * e empilha acima de qualquer `z-index` por viver na camada de topo do navegador.
 *
 * <p>Era aqui que o plano previa Radix. Com o `<dialog>` resolvendo o caso, a dependencia
 * deixaria de se pagar — ver a decisao registrada no mapeamento.
 */
export function DialogoDoIngresso({
  reserva,
  evento,
  comprador,
  aberto,
  aoFechar,
}: {
  reserva: Reserva
  evento?: EventoResumo
  comprador?: string | null
  aberto: boolean
  aoFechar: () => void
}) {
  const dialogo = useRef<HTMLDialogElement>(null)

  useEffect(() => {
    const elemento = dialogo.current
    if (!elemento) return

    // `showModal` e nao o atributo `open`: so ele ativa a camada de topo, o `::backdrop` e a
    // prisao de foco. Com `open`, o dialogo vira uma div comum no meio da pagina.
    if (aberto && !elemento.open) {
      elemento.showModal()
    } else if (!aberto && elemento.open) {
      elemento.close()
    }
  }, [aberto])

  return (
    <dialog
      ref={dialogo}
      // O `Esc` fecha por conta do navegador, e nao passa pelo React — sem avisar o estado, o
      // dialogo fecharia visualmente e a tela continuaria achando que ele esta aberto.
      onClose={aoFechar}
      // Clique no fundo fecha. O alvo so e o proprio dialogo quando o clique cai no
      // `::backdrop`; caindo no conteudo, o alvo e algum filho.
      onClick={(evento) => {
        if (evento.target === dialogo.current) aoFechar()
      }}
      className="m-auto w-[min(32rem,92vw)] bg-transparent p-0 backdrop:bg-noite/70"
    >
      <div className="rounded-cartao bg-papel p-4">
        <div className="mb-3 flex items-center justify-between">
          <h2 className="text-sm font-medium">Seu ingresso</h2>
          <button
            type="button"
            onClick={aoFechar}
            className="rounded-cartao border border-borda-forte px-3 py-1 text-sm text-suave transition-colors hover:border-marca hover:text-marca"
          >
            Fechar
          </button>
        </div>

        <IngressoDigital reserva={reserva} evento={evento} comprador={comprador} />

        {reserva.ticketCode && (
          <p className="numerico mt-3 text-center text-xs text-suave">
            Apresente o QR ou informe o codigo {reserva.ticketCode}
          </p>
        )}
      </div>
    </dialog>
  )
}
