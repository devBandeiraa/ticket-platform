import type { ButtonHTMLAttributes, InputHTMLAttributes, ReactNode } from 'react'
import type { StatusDaReserva, StatusDoEvento } from '../api/tipos'

/*
  Pecas visuais repetidas. Existem para que a aparencia de um botao ou de um campo mude num
  lugar so, e nao em doze telas — e para que as telas fiquem legiveis, mostrando o que fazem
  em vez de uma parede de classes utilitarias.
*/

// Anel de foco unico para tudo que recebe teclado. Some no clique de mouse e aparece no Tab,
// que e o unico momento em que ele importa: quem navega sem ver o cursor precisa saber onde
// esta. `outline` em vez de `ring` para nao ser cortado por `overflow-hidden` do pai.
const FOCO =
  'outline-none focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-marca'

export function Botao({
  variante = 'primario',
  className = '',
  ...props
}: ButtonHTMLAttributes<HTMLButtonElement> & { variante?: 'primario' | 'neutro' | 'perigo' }) {
  const estilos = {
    // O brilho so existe no estado normal: no hover ele cresce, e no disabled a regra de
    // opacidade ja apaga o botao inteiro.
    primario:
      'bg-marca text-fundo shadow-lg shadow-marca/20 hover:bg-marca-forte hover:shadow-xl hover:shadow-marca/30',
    neutro: 'border border-borda text-texto hover:border-borda-clara hover:bg-superficie',
    perigo: 'border border-erro/50 text-erro hover:border-erro hover:bg-erro/10',
  }[variante]

  return (
    <button
      {...props}
      className={`rounded-md px-4 py-2 text-sm font-medium transition-all duration-200 active:scale-[0.97] disabled:cursor-not-allowed disabled:opacity-40 disabled:shadow-none disabled:active:scale-100 ${FOCO} ${estilos} ${className}`}
    />
  )
}

export function Campo({
  rotulo,
  erro,
  className = '',
  ...props
}: InputHTMLAttributes<HTMLInputElement> & { rotulo: string; erro?: string }) {
  return (
    <label className="group block">
      <span className="mb-1.5 block text-sm text-suave transition-colors group-focus-within:text-marca">
        {rotulo}
      </span>
      <input
        {...props}
        // aria-invalid deixa o erro perceptivel para leitor de tela, e nao so pela cor da borda.
        aria-invalid={erro ? true : undefined}
        className={`w-full rounded-md border bg-fundo/60 px-3 py-2 text-sm outline-none transition-colors focus:border-marca focus:bg-fundo ${
          erro ? 'border-erro' : 'border-borda hover:border-borda-clara'
        } ${className}`}
      />
      {erro && <span className="mt-1 block animate-surgir text-xs text-erro">{erro}</span>}
    </label>
  )
}

export function Cartao({
  children,
  className = '',
  interativo = false,
}: {
  children: ReactNode
  className?: string
  /** Cartao que e um link ou um botao: ganha resposta ao passar o mouse. */
  interativo?: boolean
}) {
  const resposta = interativo
    ? 'transition-all duration-200 hover:-translate-y-1 hover:border-marca/60 hover:shadow-xl hover:shadow-marca/10'
    : ''

  return (
    <div className={`vidro rounded-xl border border-borda p-5 ${resposta} ${className}`}>
      {children}
    </div>
  )
}

/**
 * Numero grande com rotulo — o formato em que um resultado vira manchete.
 *
 * Existe porque a demo de concorrencia precisa que "zero vendidos a mais" seja a primeira
 * coisa que a pessoa le, e nao mais uma linha numa lista de definicoes.
 */
export function Estatistica({
  rotulo,
  valor,
  cor = 'text-texto',
  detalhe,
}: {
  rotulo: string
  valor: ReactNode
  cor?: string
  detalhe?: string
}) {
  return (
    <div className="text-center">
      <div className={`numerico text-4xl font-semibold tabular-nums sm:text-5xl ${cor}`}>
        {valor}
      </div>
      <div className="mt-1 text-xs tracking-wide text-suave uppercase">{rotulo}</div>
      {detalhe && <div className="mt-0.5 text-xs text-suave/70">{detalhe}</div>}
    </div>
  )
}

const CORES_DE_RESERVA: Record<StatusDaReserva, string> = {
  PENDING: 'bg-alerta/15 text-alerta ring-alerta/25',
  CONFIRMED: 'bg-ok/15 text-ok ring-ok/25',
  CANCELLED: 'bg-suave/15 text-suave ring-suave/25',
  EXPIRED: 'bg-erro/15 text-erro ring-erro/25',
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
    <span
      className={`inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 text-xs font-medium ring-1 ring-inset ${CORES_DE_RESERVA[status]}`}
    >
      {/* O ponto carrega o status junto com a cor: quem nao distingue verde de vermelho ainda
          ve o texto, e o ponto so reforca o agrupamento visual. */}
      <span className="size-1.5 rounded-full bg-current" />
      {ROTULOS_DE_RESERVA[status]}
    </span>
  )
}

const CORES_DE_EVENTO: Record<StatusDoEvento, string> = {
  DRAFT: 'bg-suave/15 text-suave ring-suave/25',
  PUBLISHED: 'bg-ok/15 text-ok ring-ok/25',
  CANCELLED: 'bg-erro/15 text-erro ring-erro/25',
}

const ROTULOS_DE_EVENTO: Record<StatusDoEvento, string> = {
  DRAFT: 'rascunho',
  PUBLISHED: 'publicado',
  CANCELLED: 'cancelado',
}

export function SeloDeEvento({ status }: { status: StatusDoEvento }) {
  return (
    <span
      className={`inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 text-xs font-medium ring-1 ring-inset ${CORES_DE_EVENTO[status]}`}
    >
      <span className="size-1.5 rounded-full bg-current" />
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
    <div className="flex items-center justify-center gap-3 py-8">
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
