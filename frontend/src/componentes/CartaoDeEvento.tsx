import { Link } from 'react-router-dom'
import type { Disponibilidade, EventoResumo } from '../api/tipos'
import { Capa } from './Capa'
import { TextoDeDisponibilidade } from './Disponibilidade'
import { IconeCalendario, IconeLocal, IconeMarcador, IconeSeta } from './Icones'
import { ROTULOS_DE_CATEGORIA } from './categorias'
import { dataDeCartaz, dinheiro } from './formato'

/**
 * Cartao de evento do catalogo.
 *
 * <p>Extraido para a home e a descoberta desenharem o mesmo cartao. Duplicado, bastaria alguem
 * acrescentar a categoria num lugar so para as duas telas divergirem — e a diferenca passaria
 * despercebida, porque ninguem abre as duas lado a lado.
 *
 * <h2>A data mora na imagem</h2>
 *
 * <p>Uma barra escura atravessa o pe da capa e leva a data. Parece detalhe e resolve dois
 * problemas de uma vez: a data e o primeiro dado que alguem procura ao folhear uma programacao,
 * e sobre a foto ela ganha a posicao mais alta do cartao sem roubar a linha do titulo. A barra e
 * opaca, e nao translucida, porque o que esta atras e uma foto diferente a cada evento — com
 * transparencia, a legibilidade da data passaria a depender do que o organizador subiu.
 *
 * <h2>A seta e decoracao, nao um segundo destino</h2>
 *
 * <p>O cartao inteiro ja e um link. O quadrado laranja no canto repete esse destino para o olho,
 * e por isso ele e um `<span>` dentro do mesmo `<a>` — como botao, seriam dois alvos de teclado
 * para o mesmo lugar, e quem navega por Tab pararia duas vezes em cada cartao.
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
        <div className="relative">
          {/* Proporcao fixa: o espaco da capa ja existe antes de a imagem chegar, entao a grade
              nao se reorganiza quando ela carrega. O `overflow-hidden` daqui e o que contem o
              zoom do hover — sem ele a capa cresceria por cima da borda do cartao. */}
          <div className="relative aspect-[16/10] w-full overflow-hidden">
            <Capa
              nome={evento.name}
              url={evento.imageUrl}
              className="size-full transition-transform duration-500 group-hover:scale-[1.04]"
            />
          </div>

          {/* So desenha com rotulo. Um evento gravado antes da Fase 21 nao tem categoria, e
              sem esta guarda o cartao mostraria uma pilula vazia — que parece defeito muito
              mais do que a ausencia da etiqueta. */}
          {ROTULOS_DE_CATEGORIA[evento.category] && (
            <span className="rotulo absolute left-3 top-3 rounded bg-superficie px-2.5 py-1.5 text-texto shadow-sm">
              {ROTULOS_DE_CATEGORIA[evento.category]}
            </span>
          )}

          {/* Decorativo: o projeto nao tem lista de salvos, e um marcador que nao salva nada
              seria uma promessa falsa. Fica como sinal visual do design, fora da ordem de
              teclado e invisivel para leitor de tela. */}
          <span
            aria-hidden="true"
            className="absolute right-3 top-3 rounded bg-superficie/90 p-1.5 text-suave shadow-sm backdrop-blur-sm"
          >
            <IconeMarcador tamanho={16} />
          </span>

          <p className="numerico absolute inset-x-0 bottom-0 flex items-center gap-2 bg-noite px-4 py-2.5 text-noite-texto">
            <IconeCalendario tamanho={14} className="shrink-0 text-marca-clara" />
            <span className="rotulo">{dataDeCartaz(evento.eventDate)}</span>
          </p>
        </div>

        <div className="flex flex-1 flex-col p-5">
          <h3 className="text-pretty text-lg font-semibold leading-snug transition-colors group-hover:text-marca-forte">
            {evento.name}
          </h3>

          <p className="mb-6 mt-2.5 flex items-center gap-1.5 text-sm text-suave">
            <IconeLocal tamanho={15} className="shrink-0" />
            {evento.venue}
          </p>

          {/* Regua tracejada, como no cartao do design — o picote do ingresso aparecendo de novo
              como divisoria entre o evento e o que ele custa. */}
          <div className="mt-auto flex items-end justify-between gap-3 border-t border-dashed border-borda pt-4">
            <div className="min-w-0">
              {/* O design mostra so o numero. "A partir de" fica, em corpo minimo: o `price` do
                  catalogo e o MENOR preco entre os setores, e sem a ressalva quem le acha que a
                  Plateia custa o mesmo que o Camarote. E meia linha de texto contra um erro de
                  expectativa que so aparece no checkout. */}
              <p className="rotulo text-suave">a partir de</p>
              <p className="numerico mt-1 text-2xl font-semibold leading-none">
                {dinheiro(evento.price)}
              </p>
              <TextoDeDisponibilidade disponibilidade={disponibilidade} className="mt-2" />
            </div>

            <span
              aria-hidden="true"
              className="inline-flex shrink-0 items-center justify-center rounded bg-marca p-2.5 text-white transition-colors group-hover:bg-marca-forte"
            >
              <IconeSeta tamanho={16} />
            </span>
          </div>
        </div>
      </article>
    </Link>
  )
}
