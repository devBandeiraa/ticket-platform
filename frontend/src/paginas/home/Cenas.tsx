import type { CategoriaDoEvento } from '../../api/tipos'
import { FaixaDeSecao } from '../../componentes/Editorial'
import { ICONE_DE_CATEGORIA, IconeSeta } from '../../componentes/Icones'
import type { Cena } from '../../componentes/catalogo'

/** "1 evento", "3 eventos" — o singular importa num azulejo que costuma mostrar o numero 1. */
function contagem(quantos: number): string {
  return quantos === 1 ? '1 evento' : `${quantos} eventos`
}

/**
 * Secao de categorias.
 *
 * <h2>O cabecalho e uma conta, nao um texto</h2>
 *
 * <p>O design escreve "6 EVENTOS · 4 JEITOS DE VIVER A CIDADE" alinhado a direita do titulo. Os
 * dois numeros saem do catalogo — e e justamente por isso que eles valem: um numero cravado seria
 * uma promessa que envelhece na primeira publicacao, e o tipo de detalhe que ninguem volta para
 * conferir.
 *
 * <p>So entram as cenas que tem evento, por decisao registrada em `cenasDe`. Entao o segundo
 * numero conta azulejos que existem na tela, e cada um leva a uma lista com conteudo.
 *
 * <p>A secao inteira sai da tela quando nao ha nenhuma cena. Um "0 jeitos de viver a cidade"
 * sobre um espaco vazio e pior do que a ausencia da secao.
 */
export function Cenas({
  cenas,
  quantosEventos,
  categoriaAtiva,
  aoEscolher,
}: {
  cenas: Cena[]
  quantosEventos: number
  categoriaAtiva: CategoriaDoEvento | ''
  aoEscolher: (categoria: CategoriaDoEvento | '') => void
}) {
  if (cenas.length === 0) {
    return null
  }

  return (
    <FaixaDeSecao id="cenas" className="py-10 sm:py-12">
      <div className="flex flex-wrap items-baseline justify-between gap-x-6 gap-y-2">
        <h2 className="text-lg font-semibold">Encontre a sua cena</h2>

        <p className="rotulo text-suave">
          {contagem(quantosEventos)} · {cenas.length}{' '}
          {cenas.length === 1 ? 'jeito' : 'jeitos'} de viver a cidade
        </p>
      </div>

      {/*
        Botoes, e nao links.

        Escolher uma cena filtra a lista NESTA pagina — nao navega. Como link, o teclado
        anunciaria "link" e prometeria uma troca de pagina que nao acontece; e o estado de
        selecionado nao teria como ser dito, porque link nao tem `aria-pressed`.

        `auto-fit` com minimo de 15rem: quatro cenas cabem numa fileira, e cinco nao viram uma
        orfa sozinha na linha de baixo — elas se redistribuem.
      */}
      <div
        role="group"
        aria-label="Filtrar por cena"
        className="mt-6 grid gap-4"
        style={{ gridTemplateColumns: 'repeat(auto-fit, minmax(15rem, 1fr))' }}
      >
        {cenas.map((cena) => {
          const Icone = ICONE_DE_CATEGORIA[cena.categoria]
          const ativa = categoriaAtiva === cena.categoria

          return (
            <button
              key={cena.categoria}
              type="button"
              aria-pressed={ativa}
              // Clicar na cena ativa a desliga. Sem isso, voltar para "todas" exigiria procurar
              // o seletor de categoria no meio da lista, mais abaixo.
              onClick={() => aoEscolher(ativa ? '' : cena.categoria)}
              className={`group flex items-center gap-4 rounded-cartao border bg-superficie px-5 py-4 text-left transition-all duration-200 hover:-translate-y-0.5 hover:shadow-md ${
                ativa ? 'border-marca ring-1 ring-marca' : 'border-borda hover:border-marca/50'
              }`}
            >
              <span className="shrink-0 text-marca">
                <Icone tamanho={26} />
              </span>

              <span className="min-w-0 flex-1">
                <span className="block text-lg font-semibold leading-tight">{cena.rotulo}</span>
                <span className="rotulo mt-1 block text-suave">{contagem(cena.quantos)}</span>
              </span>

              <IconeSeta
                tamanho={16}
                className="shrink-0 text-suave transition-all duration-200 group-hover:-translate-y-0.5 group-hover:translate-x-0.5 group-hover:text-marca-forte"
              />
            </button>
          )
        })}
      </div>
    </FaixaDeSecao>
  )
}
