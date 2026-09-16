import { useId } from 'react'

/**
 * O ingresso.
 *
 * <p>E a peca de identidade da plataforma: aparece no hero, na confirmacao da compra e em
 * "meus ingressos". Tudo aqui e CSS e SVG — sem WebGL, sem canvas, sem dependencia nova.
 *
 * <h2>Como o recorte e feito</h2>
 *
 * <p>Os dois semicirculos laterais saem por `mask-image`, na classe `.recorte-de-ingresso`. A
 * alternativa comum — dois pseudo-elementos redondos pintados da cor do fundo — parece igual
 * sobre cor chapada e falha em tudo mais: sobre a faixa escura, sobre uma capa de evento, sobre
 * qualquer gradiente. A mascara recorta de verdade, entao o que estiver atras aparece.
 *
 * <h2>A textura</h2>
 *
 * <p>`feTurbulence` gera o granulado no proprio SVG, sem imagem para baixar. Fica em opacidade
 * baixa e com `mix-blend-mode: overlay`, porque papel impresso tem grao e nao chuvisco de
 * televisao — passando disso, o texto perde nitidez, que e o que a secao 10 do brief pede para
 * nao acontecer.
 *
 * <h2>Movimento</h2>
 *
 * <p>Nao ha nenhum aqui dentro. A entrada e o parallax pertencem a quem posiciona o ingresso na
 * tela, e a regra global de `prefers-reduced-motion` no `index.css` ja alcanca os dois.
 */

export interface TicketProps {
  /** Linha superior, em versalete. O "ADMIT ONE" do ingresso classico. */
  chamada?: string
  titulo: string
  local?: string
  /** Ja formatada para leitura: este componente nao sabe de fuso. */
  data?: string
  hora?: string
  /** Numero do ingresso, `TP-XXXXXX-XXXXXX`. Fica no canhoto, que e onde se confere. */
  codigo?: string
  /** Quem comprou. Ausente no ingresso de vitrine do hero. */
  comprador?: string
  setor?: string
  /** Conteudo do canhoto — o QR Code, na confirmacao da compra. */
  canhoto?: React.ReactNode
  className?: string
}

/** Granulado do papel, gerado no proprio SVG. */
function Textura({ id }: { id: string }) {
  return (
    <svg
      aria-hidden="true"
      className="pointer-events-none absolute inset-0 size-full opacity-[0.18] mix-blend-overlay"
      preserveAspectRatio="none"
    >
      <filter id={id}>
        {/* baseFrequency alto da grao fino; baixo daria manchas. numOctaves 2 basta — cada
            oitava a mais custa tempo de composicao e some sob a opacidade que usamos. */}
        <feTurbulence type="fractalNoise" baseFrequency="0.9" numOctaves="2" stitchTiles="stitch" />
        <feColorMatrix type="saturate" values="0" />
      </filter>
      <rect width="100%" height="100%" filter={`url(#${id})`} />
    </svg>
  )
}

export function Ticket({
  chamada = 'Admit one',
  titulo,
  local,
  data,
  hora,
  codigo,
  comprador,
  setor,
  canhoto,
  className = '',
}: TicketProps) {
  // `useId` e nao um contador de modulo: dois ingressos na mesma tela precisam de ids de filtro
  // distintos, ou o segundo reaproveita a textura do primeiro.
  const idTextura = `${useId()}-textura`

  return (
    <article
      className={`recorte-de-ingresso relative isolate overflow-hidden rounded-cartao text-noite-texto shadow-xl ${className}`}
      style={{
        // Gradiente de cortina: mais quente na quina superior esquerda, onde bateria a luz.
        background:
          'linear-gradient(135deg, oklch(0.26 0.09 25) 0%, oklch(0.19 0.05 20) 55%, oklch(0.22 0.07 35) 100%)',
      }}
    >
      <Textura id={idTextura} />

      <div className="flex items-stretch">
        {/* Corpo. A largura casa com `--posicao-do-picote` da classe de recorte: o furo lateral
            precisa cair exatamente sobre a linha do picote, ou o ingresso parece rasgado fora
            do lugar. Os dois numeros vivem juntos aqui e no CSS, e mexer num pede o outro. */}
        <div className="min-w-0 flex-1 basis-[68%] p-5 sm:p-6">
          <p className="text-[0.65rem] font-semibold uppercase tracking-[0.2em] text-marca-clara">
            {chamada}
          </p>

          <h3 className="mt-3 text-balance text-xl font-semibold leading-tight sm:text-2xl">
            {titulo}
          </h3>

          {(local || setor) && (
            <p className="mt-1.5 text-sm text-noite-suave">
              {[local, setor].filter(Boolean).join(' · ')}
            </p>
          )}

          {(data || hora) && (
            <p className="numerico mt-4 text-sm font-medium">
              {[data, hora].filter(Boolean).join(' · ')}
            </p>
          )}

          {comprador && (
            <div className="mt-4 text-xs text-noite-suave">
              <span className="uppercase tracking-wider">Titular</span>
              <span className="mt-0.5 block text-sm text-noite-texto">{comprador}</span>
            </div>
          )}
        </div>

        {/* Picote. `currentColor` herda daqui, entao a linha acompanha o tema do ingresso sem
            uma cor propria para manter em sincronia. */}
        <div
          aria-hidden="true"
          className="picote-vertical w-0.5 shrink-0 text-noite-borda-forte"
        />

        {/* Canhoto */}
        <div className="flex shrink-0 basis-[32%] flex-col items-center justify-center gap-3 p-4 text-center sm:p-5">
          {canhoto}
          {codigo && (
            <p className="numerico text-[0.7rem] tracking-wider text-noite-suave">{codigo}</p>
          )}
        </div>
      </div>
    </article>
  )
}
