import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { listarPublicados } from '../api/eventos'
import { Erro, EsqueletoDeCartoes, Vazio } from '../componentes/Estados'
import { Cartao, Paginacao } from '../componentes/Ui'
import { dataEHora, dinheiro } from '../componentes/formato'

/**
 * Abertura da pagina inicial.
 *
 * Some na busca e nas paginas seguintes de proposito: quem digitou um termo quer a lista, e
 * um bloco de apresentacao entre a barra de busca e o resultado vira obstaculo.
 */
function Abertura() {
  return (
    <div className="mb-14 text-center">
      <span className="inline-flex items-center gap-2 rounded-full border border-borda bg-superficie/60 px-3 py-1 text-xs text-suave backdrop-blur">
        <span className="size-1.5 animate-pulse rounded-full bg-ok" />
        cinco microsservicos no ar
      </span>

      <h1 className="mt-6 text-4xl font-semibold tracking-tight text-balance sm:text-5xl">
        Mil pessoas clicam <span className="text-marca">comprar</span>
        <br />
        no mesmo segundo.
      </h1>

      <p className="mx-auto mt-5 max-w-xl text-pretty text-suave">
        Restam cinquenta ingressos. Quantos voce vende? A resposta ingenua — consultar, decidir,
        gravar — vende mais do que existe. Esta plataforma nao.
      </p>

      <Link
        to="/demo/concorrencia"
        className="group mt-7 inline-flex items-center gap-2 rounded-md border border-marca/40 bg-marca/10 px-5 py-2.5 text-sm font-medium text-marca transition-all duration-200 hover:border-marca hover:bg-marca/20 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-marca"
      >
        Ver o teste de concorrencia
        <span className="transition-transform duration-200 group-hover:translate-x-1">&rarr;</span>
      </Link>
    </div>
  )
}

export function Catalogo() {
  const [pagina, setPagina] = useState(0)
  const [busca, setBusca] = useState('')
  // Termo separado do que esta sendo digitado: consultar a cada tecla dispararia uma chamada
  // por letra, e o rate limiter do gateway acharia — com razao — que e abuso.
  const [termo, setTermo] = useState('')

  const consulta = useQuery({
    queryKey: ['eventos', pagina, termo],
    queryFn: () => listarPublicados({ page: pagina, size: 9, busca: termo }),
  })

  function pesquisar(evento: React.FormEvent) {
    evento.preventDefault()
    setTermo(busca)
    setPagina(0)
  }

  return (
    <>
      {pagina === 0 && !termo && <Abertura />}

      <div className="mb-6 flex flex-wrap items-end justify-between gap-4">
        <div>
          <h2 className="text-2xl font-semibold">Eventos</h2>
          <p className="mt-1 text-sm text-suave">
            Somente eventos publicados aparecem aqui — um rascunho nunca chega ao catalogo.
          </p>
        </div>

        <form onSubmit={pesquisar} className="flex gap-2">
          <input
            value={busca}
            onChange={(e) => setBusca(e.target.value)}
            placeholder="Buscar por nome ou local"
            className="w-56 rounded-md border border-borda bg-superficie/60 px-3 py-2 text-sm outline-none transition-colors hover:border-borda-clara focus:border-marca"
          />
          <button className="rounded-md bg-marca px-4 py-2 text-sm font-medium text-fundo transition-all duration-200 hover:bg-marca-forte active:scale-[0.97] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-marca">
            Buscar
          </button>
        </form>
      </div>

      {consulta.isPending && <EsqueletoDeCartoes />}
      {consulta.isError && <Erro erro={consulta.error} />}

      {consulta.data &&
        (consulta.data.content.length === 0 ? (
          <Vazio>
            {termo ? `Nenhum evento encontrado para "${termo}".` : 'Nenhum evento publicado ainda.'}
          </Vazio>
        ) : (
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
            {consulta.data.content.map((evento, indice) => (
              <Link
                key={evento.id}
                to={`/eventos/${evento.id}`}
                className="group animate-subir rounded-xl focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-marca"
                // Escalonado: os cartoes entram em cascata em vez de piscarem todos juntos.
                // Teto de seis para a ultima linha nao ficar esperando meio segundo.
                style={{ animationDelay: `${Math.min(indice, 6) * 60}ms` }}
              >
                <Cartao interativo className="flex h-full flex-col">
                  <h3 className="font-medium transition-colors group-hover:text-marca">
                    {evento.name}
                  </h3>
                  <p className="mt-1 text-sm text-suave">{evento.venue}</p>
                  <p className="mt-3 text-sm text-suave">{dataEHora(evento.eventDate)}</p>

                  <div className="mt-5 flex items-center justify-between border-t border-borda/60 pt-4">
                    <span className="numerico text-lg font-semibold text-marca">
                      {dinheiro(evento.price)}
                    </span>
                    <span className="text-xs text-suave transition-transform duration-200 group-hover:translate-x-1">
                      ver detalhes &rarr;
                    </span>
                  </div>
                </Cartao>
              </Link>
            ))}
          </div>
        ))}

      {consulta.data && (
        <Paginacao
          pagina={consulta.data.page}
          totalDePaginas={consulta.data.totalPages}
          aoMudar={setPagina}
        />
      )}
    </>
  )
}
