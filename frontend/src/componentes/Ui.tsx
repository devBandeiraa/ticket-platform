import type { ButtonHTMLAttributes, InputHTMLAttributes, ReactNode } from 'react'
import type { StatusDaReserva, StatusDoEvento } from '../api/tipos'

/*
  Pecas visuais repetidas. Existem para que a aparencia de um botao ou de um campo mude num
  lugar so, e nao em doze telas — e para que as telas fiquem legiveis, mostrando o que fazem
  em vez de uma parede de classes utilitarias.
*/

export function Botao({
  variante = 'primario',
  className = '',
  ...props
}: ButtonHTMLAttributes<HTMLButtonElement> & { variante?: 'primario' | 'neutro' | 'perigo' }) {
  const estilos = {
    primario: 'bg-marca text-fundo hover:bg-marca-forte',
    neutro: 'border border-borda text-texto hover:bg-superficie',
    perigo: 'border border-erro/50 text-erro hover:bg-erro/10',
  }[variante]

  return (
    <button
      {...props}
      className={`rounded-md px-4 py-2 text-sm font-medium transition disabled:cursor-not-allowed disabled:opacity-40 ${estilos} ${className}`}
    />
  )
}

export function Campo({
  rotulo,
  erro,
  ...props
}: InputHTMLAttributes<HTMLInputElement> & { rotulo: string; erro?: string }) {
  return (
    <label className="block">
      <span className="mb-1 block text-sm text-suave">{rotulo}</span>
      <input
        {...props}
        // aria-invalid deixa o erro perceptivel para leitor de tela, e nao so pela cor da borda.
        aria-invalid={erro ? true : undefined}
        className={`w-full rounded-md border bg-fundo px-3 py-2 text-sm outline-none focus:border-marca ${
          erro ? 'border-erro' : 'border-borda'
        }`}
      />
      {erro && <span className="mt-1 block text-xs text-erro">{erro}</span>}
    </label>
  )
}

export function Cartao({ children, className = '' }: { children: ReactNode; className?: string }) {
  return (
    <div className={`rounded-lg border border-borda bg-superficie p-5 ${className}`}>{children}</div>
  )
}

const CORES_DE_RESERVA: Record<StatusDaReserva, string> = {
  PENDING: 'bg-alerta/15 text-alerta',
  CONFIRMED: 'bg-ok/15 text-ok',
  CANCELLED: 'bg-suave/15 text-suave',
  EXPIRED: 'bg-erro/15 text-erro',
}

const ROTULOS_DE_RESERVA: Record<StatusDaReserva, string> = {
  PENDING: 'aguardando pagamento',
  CONFIRMED: 'paga',
  // A distincao importa e aparece: cancelada e desistencia do usuario, expirada e o
  // sistema recolhendo o ingresso por falta de pagamento.
  CANCELLED: 'cancelada',
  EXPIRED: 'expirada',
}

export function SeloDeReserva({ status }: { status: StatusDaReserva }) {
  return (
    <span className={`rounded-full px-2.5 py-1 text-xs font-medium ${CORES_DE_RESERVA[status]}`}>
      {ROTULOS_DE_RESERVA[status]}
    </span>
  )
}

const CORES_DE_EVENTO: Record<StatusDoEvento, string> = {
  DRAFT: 'bg-suave/15 text-suave',
  PUBLISHED: 'bg-ok/15 text-ok',
  CANCELLED: 'bg-erro/15 text-erro',
}

const ROTULOS_DE_EVENTO: Record<StatusDoEvento, string> = {
  DRAFT: 'rascunho',
  PUBLISHED: 'publicado',
  CANCELLED: 'cancelado',
}

export function SeloDeEvento({ status }: { status: StatusDoEvento }) {
  return (
    <span className={`rounded-full px-2.5 py-1 text-xs font-medium ${CORES_DE_EVENTO[status]}`}>
      {ROTULOS_DE_EVENTO[status]}
    </span>
  )
}

export function Paginacao({
  pagina,
  totalDePaginas,
  aoMudar,
}: {
  pagina: number
  totalDePaginas: number
  aoMudar: (pagina: number) => void
}) {
  if (totalDePaginas <= 1) return null

  return (
    <div className="flex items-center justify-center gap-3 py-6">
      <Botao variante="neutro" disabled={pagina === 0} onClick={() => aoMudar(pagina - 1)}>
        Anterior
      </Botao>
      <span className="numerico text-sm text-suave">
        {pagina + 1} de {totalDePaginas}
      </span>
      <Botao
        variante="neutro"
        disabled={pagina >= totalDePaginas - 1}
        onClick={() => aoMudar(pagina + 1)}
      >
        Proxima
      </Botao>
    </div>
  )
}
