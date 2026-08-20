import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { listarPublicados } from '../api/eventos'
import { Carregando, Erro, Vazio } from '../componentes/Estados'
import { Cartao, Paginacao } from '../componentes/Ui'
import { dataEHora, dinheiro } from '../componentes/formato'

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
      <div className="mb-8">
        <h1 className="text-2xl font-semibold">Eventos</h1>
        <p className="mt-1 text-sm text-suave">
          Somente eventos publicados aparecem aqui — um rascunho nunca chega ao catalogo.
        </p>
      </div>

      <form onSubmit={pesquisar} className="mb-6 flex gap-2">
        <input
          value={busca}
          onChange={(e) => setBusca(e.target.value)}
          placeholder="Buscar por nome ou local"
          className="flex-1 rounded-md border border-borda bg-superficie px-3 py-2 text-sm outline-none focus:border-marca"
        />
        <button className="rounded-md bg-marca px-4 py-2 text-sm font-medium text-fundo hover:bg-marca-forte">
          Buscar
        </button>
      </form>

      {consulta.isPending && <Carregando />}
      {consulta.isError && <Erro erro={consulta.error} />}

      {consulta.data &&
        (consulta.data.content.length === 0 ? (
          <Vazio>
            {termo ? `Nenhum evento encontrado para "${termo}".` : 'Nenhum evento publicado ainda.'}
          </Vazio>
        ) : (
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
            {consulta.data.content.map((evento) => (
              <Link key={evento.id} to={`/eventos/${evento.id}`}>
                <Cartao className="h-full transition hover:border-marca">
                  <h2 className="font-medium">{evento.name}</h2>
                  <p className="mt-1 text-sm text-suave">{evento.venue}</p>
                  <p className="mt-3 text-sm">{dataEHora(evento.eventDate)}</p>
                  <p className="mt-3 numerico font-medium text-marca">{dinheiro(evento.price)}</p>
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
