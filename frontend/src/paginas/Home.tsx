import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { listarPublicados } from '../api/eventos'
import type { CategoriaDoEvento } from '../api/tipos'
import { CartaoDeEvento } from '../componentes/CartaoDeEvento'
import { useDisponibilidades } from '../componentes/usarDisponibilidades'
import { Erro, EsqueletoDeCartoes, Vazio } from '../componentes/Estados'
import { FaixaNoite } from '../componentes/Layout'
import { Ticket } from '../componentes/Ticket'
import { CATEGORIAS, ROTULOS_DE_CATEGORIA } from '../componentes/categorias'

/** Quantos eventos a home mostra. Seis, e nao nove: a home apresenta, a descoberta lista. */
const EM_DESTAQUE = 6

/**
 * Ingresso de vitrine do hero.
 *
 * <p>Os dados sao fixos e nao vem da API de proposito. E uma peca de identidade, e nao a oferta
 * de um evento: puxar o primeiro do catalogo faria o hero mudar de conteudo a cada publicacao, e
 * um evento esgotado ou cancelado apareceria como cartaz da plataforma.
 */
function IngressoDeVitrine() {
  return (
    <Ticket
      titulo="Tomorrowland Experience"
      local="Sao Paulo"
      data="25 out 2026"
      hora="22:00"
      setor="Camarote"
      codigo="TP-4K7M2P-9XQ3RB"
      canhoto={
        <span
          aria-hidden="true"
          className="text-[0.6rem] font-semibold uppercase leading-tight tracking-[0.2em] text-noite-suave [writing-mode:vertical-rl]"
        >
          Admit one
        </span>
      }
      className="w-full max-w-md rotate-[-2deg] transition-transform duration-500 hover:rotate-0"
    />
  )
}

function Hero() {
  const navegar = useNavigate()
  const [busca, setBusca] = useState('')

  function pesquisar(evento: React.FormEvent) {
    evento.preventDefault()
    // A busca vive na descoberta, e nao aqui: e la que estao os demais filtros, e um resultado
    // no meio do hero deixaria o usuario sem como refinar o que encontrou.
    navegar(`/explorar?busca=${encodeURIComponent(busca)}`)
  }

  function porCategoria(categoria: CategoriaDoEvento) {
    navegar(`/explorar?categoria=${categoria}`)
  }

  return (
    <FaixaNoite comCeu>
      <div className="mx-auto grid max-w-6xl items-center gap-12 px-4 py-16 lg:grid-cols-[1.1fr_1fr] lg:py-24">
        <div>
          <h1 className="text-balance text-4xl font-semibold leading-[1.1] tracking-tight sm:text-5xl lg:text-6xl">
            Viva experiencias que ficam na memoria.
          </h1>

          <p className="mt-5 max-w-lg text-pretty text-noite-suave">
            Encontre shows, festivais, eventos esportivos, conferencias e experiencias perto de
            voce.
          </p>

          <form onSubmit={pesquisar} className="mt-8 flex max-w-lg gap-2">
            {/* aria-label e nao so placeholder: o placeholder some ao digitar e nao e nome
                acessivel. Sem rotulo visivel, o nome vai no atributo. */}
            <input
              type="search"
              aria-label="Buscar eventos, artistas ou locais"
              value={busca}
              onChange={(e) => setBusca(e.target.value)}
              placeholder="Busque por eventos, artistas ou locais..."
              className="min-w-0 flex-1 rounded-cartao border border-noite-borda-forte bg-noite-superficie px-4 py-3 text-sm outline-none transition-colors placeholder:text-noite-suave focus:border-marca"
            />
            <button className="shrink-0 rounded-cartao bg-marca px-5 py-3 text-sm font-medium text-noite transition-colors hover:bg-marca-clara">
              Buscar
            </button>
          </form>

          <div className="mt-6 flex flex-wrap gap-2">
            {CATEGORIAS.map((categoria) => (
              <button
                key={categoria}
                type="button"
                onClick={() => porCategoria(categoria)}
                className="rounded-full border border-noite-borda-forte px-4 py-1.5 text-sm text-noite-suave transition-colors hover:border-marca hover:text-marca"
              >
                {ROTULOS_DE_CATEGORIA[categoria]}
              </button>
            ))}
          </div>
        </div>

        <div className="flex justify-center lg:justify-end">
          <IngressoDeVitrine />
        </div>
      </div>
    </FaixaNoite>
  )
}

function EmDestaque() {
  const consulta = useQuery({
    queryKey: ['eventos', 'destaque'],
    queryFn: () => listarPublicados({ page: 0, size: EM_DESTAQUE }),
  })

  const eventos = consulta.data?.content ?? []
  const disponibilidades = useDisponibilidades(eventos.map((e) => e.id))

  return (
    <section className="mx-auto max-w-6xl px-4 py-16">
      <div className="mb-8 flex flex-wrap items-end justify-between gap-4">
        <div>
          <h2 className="text-2xl font-semibold tracking-tight">Eventos que estao acontecendo</h2>
          <p className="mt-1 text-sm text-suave">
            Somente eventos publicados aparecem aqui — um rascunho nunca chega ao catalogo.
          </p>
        </div>

        <Link
          to="/explorar"
          className="group inline-flex items-center gap-2 text-sm font-medium text-marca hover:underline"
        >
          Explorar todos
          <span className="transition-transform duration-200 group-hover:translate-x-1">&rarr;</span>
        </Link>
      </div>

      {consulta.isPending && <EsqueletoDeCartoes quantos={EM_DESTAQUE} />}
      {consulta.isError && <Erro erro={consulta.error} />}

      {consulta.data &&
        (eventos.length === 0 ? (
          <Vazio>Nenhum evento publicado ainda.</Vazio>
        ) : (
          <div className="grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
            {eventos.map((evento, indice) => (
              <CartaoDeEvento
                key={evento.id}
                evento={evento}
                disponibilidade={disponibilidades.get(evento.id)}
                atrasoDaAnimacao={Math.min(indice, 6) * 60}
              />
            ))}
          </div>
        ))}
    </section>
  )
}

/**
 * Chamada para a demonstracao tecnica.
 *
 * <p>Fica no fim da home, e nao no hero, porque nao e o que um comprador vem fazer aqui. Quem
 * visita este projeto como portfolio chega ate o fim; quem visita como plataforma nao esbarra
 * nela no caminho da compra.
 */
function ConvitePelaConcorrencia() {
  return (
    <section className="mx-auto max-w-6xl px-4 pb-16">
      <div className="rounded-cartao border border-borda bg-superficie p-8 text-center">
        <p className="inline-flex items-center gap-2 text-xs text-suave">
          <span className="size-1.5 animate-pulse rounded-full bg-ok" />
          cinco microsservicos no ar
        </p>

        <h2 className="mt-4 text-balance text-2xl font-semibold tracking-tight">
          Mil pessoas clicam <span className="text-marca">comprar</span> no mesmo segundo.
        </h2>

        <p className="mx-auto mt-4 max-w-xl text-pretty text-sm text-suave">
          Restam cinquenta ingressos. Quantos voce vende? A resposta ingenua — consultar, decidir,
          gravar — vende mais do que existe. Esta plataforma nao.
        </p>

        <Link
          to="/demo/concorrencia"
          className="group mt-6 inline-flex items-center gap-2 rounded-cartao border border-marca px-5 py-2.5 text-sm font-medium text-marca transition-colors hover:bg-marca hover:text-superficie"
        >
          Ver o teste de concorrencia
          <span className="transition-transform duration-200 group-hover:translate-x-1">&rarr;</span>
        </Link>
      </div>
    </section>
  )
}

export function Home() {
  return (
    <>
      <Hero />
      <EmDestaque />
      <ConvitePelaConcorrencia />
    </>
  )
}
