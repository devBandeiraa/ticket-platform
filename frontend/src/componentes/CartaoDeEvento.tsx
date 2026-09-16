import { Link } from 'react-router-dom'
import type { Disponibilidade, EventoResumo } from '../api/tipos'
import { Capa } from './Capa'
import { SeloDeDisponibilidade } from './Disponibilidade'
import { ROTULOS_DE_CATEGORIA } from './categorias'
import { dataEHora, dinheiro } from './formato'

/**
 * Cartao de evento do catalogo.
 *
 * <p>Extraido para a home e a descoberta desenharem o mesmo cartao. Duplicado, bastaria alguem
 * acrescentar a categoria num lugar so para as duas telas divergirem — e a diferenca passaria
 * despercebida, porque ninguem abre as duas lado a lado.
 *
 * <p>Quem manda no cartao e a IMAGEM. O texto fica contido e o hover e discreto: um zoom leve
 * na capa e a cor do titulo mudando. O brief pede para o evento se destacar, e nao o cartao.
 */
export function CartaoDeEvento({
  evento,
  disponibilidade,
  atrasoDaAnimacao = 0,
}: {
  evento: EventoResumo
  disponibilidade?: Disponibilidade
  /** Escalonamento da entrada, em milissegundos. */
  atrasoDaAnimacao?: number
}) {
  return (
    <Link
      to={`/eventos/${evento.id}`}
      className="group block animate-subir rounded-cartao focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-marca"
      style={{ animationDelay: `${atrasoDaAnimacao}ms` }}
    >
      <article className="flex h-full flex-col overflow-hidden rounded-cartao border border-borda bg-superficie transition-shadow duration-200 group-hover:shadow-lg">
        {/* Proporcao fixa: o espaco da capa ja existe antes de a imagem chegar, entao a grade
            nao se reorganiza quando ela carrega. O `overflow-hidden` daqui e o que contem o
            zoom do hover — sem ele a capa cresceria por cima da borda do cartao. */}
        <div className="relative aspect-[16/9] w-full overflow-hidden">
          <Capa
            nome={evento.name}
            url={evento.imageUrl}
            className="size-full transition-transform duration-500 group-hover:scale-[1.04]"
          />

          {/* So desenha com rotulo. Um evento gravado antes da Fase 21 nao tem categoria, e
              sem esta guarda o cartao mostraria uma pilula preta vazia — que parece defeito
              muito mais do que a ausencia da etiqueta. */}
          {ROTULOS_DE_CATEGORIA[evento.category] && (
            <span className="absolute left-3 top-3 rounded-full bg-noite/75 px-2.5 py-1 text-[0.7rem] font-medium uppercase tracking-wide text-noite-texto backdrop-blur-sm">
              {ROTULOS_DE_CATEGORIA[evento.category]}
            </span>
          )}
        </div>

        <div className="flex flex-1 flex-col p-5">
          <h3 className="font-medium leading-snug transition-colors group-hover:text-marca">
            {evento.name}
          </h3>
          <p className="mt-1 text-sm text-suave">{evento.venue}</p>
          <p className="numerico mt-2 text-sm text-suave">{dataEHora(evento.eventDate)}</p>

          <div className="mt-auto flex flex-wrap items-center justify-between gap-2 border-t border-borda pt-4">
            <div>
              <span className="block text-[0.7rem] uppercase tracking-wide text-suave">
                a partir de
              </span>
              <span className="numerico text-lg font-semibold text-marca">
                {dinheiro(evento.price)}
              </span>
            </div>

            <SeloDeDisponibilidade disponibilidade={disponibilidade} />
          </div>
        </div>
      </article>
    </Link>
  )
}
