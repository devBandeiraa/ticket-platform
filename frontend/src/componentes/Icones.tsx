import type { CategoriaDoEvento } from '../api/tipos'

/*
  Os icones do design.

  ----------------------------------------------------------------------------
   Por que desenhados aqui, e nao por biblioteca
  ----------------------------------------------------------------------------
  Sao doze formas simples. Uma biblioteca de icones resolveria em um import e
  traria junto um pacote inteiro para usar um por cento dele, mais uma dependencia
  para manter em dia — e ainda assim nenhuma delas tem a nota musical, a mascara e
  a claquete no mesmo peso de traco que o resto do desenho.

  ----------------------------------------------------------------------------
   Todos no mesmo contrato
  ----------------------------------------------------------------------------
  Caixa de 24, traco de 1.75, pontas e juntas arredondadas, `currentColor`. Herdar
  a cor do texto e o que permite o mesmo icone aparecer laranja no azulejo de cena,
  branco sobre a foto do cartao e cinza na linha de local, sem variante nenhuma.

  `aria-hidden` em todos. Nenhum destes icones carrega informacao que o texto ao
  lado nao carregue — o pin acompanha o nome do local, o calendario acompanha a
  data. Anunciados, seriam ruido duplicado.
*/

interface PropsDoIcone {
  /** Lado da caixa, em pixels. O traco nao engrossa junto: e sempre 1.75 no espaco de 24. */
  tamanho?: number
  className?: string
}

function Svg({
  tamanho = 24,
  className = '',
  children,
}: PropsDoIcone & { children: React.ReactNode }) {
  return (
    <svg
      aria-hidden="true"
      width={tamanho}
      height={tamanho}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={1.75}
      strokeLinecap="round"
      strokeLinejoin="round"
      className={className}
    >
      {children}
    </svg>
  )
}

/** Ingresso — a marca. O unico preenchido, porque funciona como logotipo e nao como icone. */
export function IconeIngresso({ tamanho = 24, className = '' }: PropsDoIcone) {
  return (
    <svg
      aria-hidden="true"
      width={tamanho}
      height={tamanho}
      viewBox="0 0 24 24"
      fill="none"
      className={className}
    >
      <path
        d="M3 8.5A2.5 2.5 0 0 1 5.5 6h13A2.5 2.5 0 0 1 21 8.5v1.75a2 2 0 0 0 0 3.5v1.75A2.5 2.5 0 0 1 18.5 18h-13A2.5 2.5 0 0 1 3 15.5v-1.75a2 2 0 0 0 0-3.5V8.5Z"
        fill="currentColor"
      />
      {/* O picote, vazado na cor de tras. `stroke-dasharray` em vez de linha cheia: o furo de
          ingresso tem tamanho fisico, e um tracejado do navegador nao se controla. */}
      <path
        d="M14 7.5v9"
        stroke="var(--color-papel)"
        strokeWidth={1.5}
        strokeDasharray="2 2.5"
        strokeLinecap="round"
      />
    </svg>
  )
}

export function IconeBusca(props: PropsDoIcone) {
  return (
    <Svg {...props}>
      <circle cx="11" cy="11" r="6.5" />
      <path d="m20 20-3.6-3.6" />
    </Svg>
  )
}

export function IconeLocal(props: PropsDoIcone) {
  return (
    <Svg {...props}>
      <path d="M12 21s7-5.2 7-11a7 7 0 1 0-14 0c0 5.8 7 11 7 11Z" />
      <circle cx="12" cy="10" r="2.5" />
    </Svg>
  )
}

export function IconeCalendario(props: PropsDoIcone) {
  return (
    <Svg {...props}>
      <rect x="3.5" y="5" width="17" height="15.5" rx="2.5" />
      <path d="M3.5 10h17M8 3v4M16 3v4" />
    </Svg>
  )
}

export function IconeMarcador(props: PropsDoIcone) {
  return (
    <Svg {...props}>
      <path d="M6.5 4h11a1 1 0 0 1 1 1v15.2a.5.5 0 0 1-.78.41L12 16.5l-5.72 4.11a.5.5 0 0 1-.78-.41V5a1 1 0 0 1 1-1Z" />
    </Svg>
  )
}

export function IconeSeta(props: PropsDoIcone) {
  return (
    <Svg {...props}>
      <path d="M7 17 17 7M9 7h8v8" />
    </Svg>
  )
}

export function IconeInformacao(props: PropsDoIcone) {
  return (
    <Svg {...props}>
      <circle cx="12" cy="12" r="8.5" />
      <path d="M12 11.5v5M12 8h.01" />
    </Svg>
  )
}

/** Mais e menos do acordeao. O menos e o mais sem a haste vertical — abrir gira, nao troca. */
export function IconeMais({ aberto = false, ...props }: PropsDoIcone & { aberto?: boolean }) {
  return (
    <Svg {...props}>
      <path d="M4.5 12h15" />
      {/* A haste some ao abrir em vez de o icone ser trocado: assim a transicao e continua, e o
          `+` virando `-` conta a propria historia. */}
      <path
        d="M12 4.5v15"
        className="origin-center transition-transform duration-200"
        style={{ transform: aberto ? 'scaleY(0)' : 'scaleY(1)' }}
      />
    </Svg>
  )
}

export function IconeChevron(props: PropsDoIcone) {
  return (
    <Svg {...props}>
      <path d="m6 9.5 6 6 6-6" />
    </Svg>
  )
}

export function IconeConfere(props: PropsDoIcone) {
  return (
    <Svg {...props}>
      <path d="m5 12.5 4.5 4.5L19 7.5" />
    </Svg>
  )
}

// --- as cenas ---

function IconeMusica(props: PropsDoIcone) {
  return (
    <Svg {...props}>
      <path d="M9 18V6.5l10-2V16" />
      <circle cx="6.5" cy="18" r="2.5" />
      <circle cx="16.5" cy="16" r="2.5" />
    </Svg>
  )
}

function IconeTeatro(props: PropsDoIcone) {
  return (
    <Svg {...props}>
      <path d="M3.5 6.5h9v6a4.5 4.5 0 0 1-9 0v-6Z" />
      <path d="M6 10h.01M10 10h.01M6.5 14c.8.7 2.2.7 3 0" />
      <path d="M11.5 6.5h9v6a4.5 4.5 0 0 1-7.6 3.2" />
      <path d="M14 10h.01M18 10h.01" />
    </Svg>
  )
}

function IconeFestas(props: PropsDoIcone) {
  return (
    <Svg {...props}>
      <circle cx="12" cy="11" r="7" />
      <path d="M12 4v14M5 11h14M7.3 6.6l9.4 8.8M16.7 6.6l-9.4 8.8" />
    </Svg>
  )
}

function IconeCinema(props: PropsDoIcone) {
  return (
    <Svg {...props}>
      <rect x="2.5" y="9" width="19" height="11.5" rx="2" />
      <path d="M2.5 9 4 4.2l16 2.1L18.9 9" />
      <path d="m8.4 5 1 3.2M13.6 5.7l1 3.2" />
      <path d="M2.5 13h19" />
    </Svg>
  )
}

function IconeEsportes(props: PropsDoIcone) {
  return (
    <Svg {...props}>
      <circle cx="12" cy="12" r="8.5" />
      <path d="M12 3.5c2.5 2.3 2.5 14.7 0 17M3.6 9.5c3.8 1.6 13 1.6 16.8 0" />
    </Svg>
  )
}

function IconeTecnologia(props: PropsDoIcone) {
  return (
    <Svg {...props}>
      <rect x="2.5" y="4.5" width="19" height="12.5" rx="2" />
      <path d="M8 20.5h8M12 17v3.5" />
    </Svg>
  )
}

function IconeFestivais(props: PropsDoIcone) {
  return (
    <Svg {...props}>
      <path d="M3 20.5V11l9-6.5 9 6.5v9.5" />
      <path d="M3 20.5h18M8.5 20.5v-5.5h7v5.5" />
    </Svg>
  )
}

/**
 * O icone de cada cena.
 *
 * <p>Um `Record` completo, como os rotulos em `categorias.ts` e pela mesma razao: acrescentar
 * categoria no backend e esquecer o icone aqui vira erro de compilacao, e nao um azulejo com um
 * buraco onde deveria haver um simbolo.
 */
export const ICONE_DE_CATEGORIA: Record<
  CategoriaDoEvento,
  (props: PropsDoIcone) => React.ReactElement
> = {
  MUSICA: IconeMusica,
  SHOWS: IconeMusica,
  FESTIVAIS: IconeFestivais,
  ESPORTES: IconeEsportes,
  TECNOLOGIA: IconeTecnologia,
  TEATRO: IconeTeatro,
  CINEMA: IconeCinema,
  FESTAS: IconeFestas,
}
