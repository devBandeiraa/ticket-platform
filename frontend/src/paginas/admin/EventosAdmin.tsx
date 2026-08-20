import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { cancelarEvento, listarParaAdmin, publicarEvento } from '../../api/eventos'
import type { StatusDoEvento } from '../../api/tipos'
import { Carregando, Erro, Vazio, mensagemDe } from '../../componentes/Estados'
import { Botao, Cartao, Paginacao } from '../../componentes/Ui'
import { dataEHora, dinheiro } from '../../componentes/formato'

const FILTROS: Array<{ valor: StatusDoEvento | ''; rotulo: string }> = [
  { valor: '', rotulo: 'Todos' },
  { valor: 'DRAFT', rotulo: 'Rascunhos' },
  { valor: 'PUBLISHED', rotulo: 'Publicados' },
  { valor: 'CANCELLED', rotulo: 'Cancelados' },
]

export function EventosAdmin() {
  const [pagina, setPagina] = useState(0)
  const [status, setStatus] = useState<StatusDoEvento | ''>('')
  const queryClient = useQueryClient()

  const consulta = useQuery({
    queryKey: ['admin-eventos', pagina, status],
    queryFn: () => listarParaAdmin({ page: pagina, size: 10, status }),
  })

  function recarregar() {
    queryClient.invalidateQueries({ queryKey: ['admin-eventos'] })
    // O catalogo publico muda junto: publicar faz o evento aparecer la.
    queryClient.invalidateQueries({ queryKey: ['eventos'] })
  }

  const publicacao = useMutation({ mutationFn: publicarEvento, onSuccess: recarregar })
  const cancelamento = useMutation({ mutationFn: cancelarEvento, onSuccess: recarregar })

  return (
    <>
      <div className="mb-6 flex flex-wrap items-center gap-3">
        <div>
          <h1 className="text-2xl font-semibold">Gerenciar eventos</h1>
          <p className="mt-1 text-sm text-suave">
            Um evento nasce como rascunho e so entra no catalogo por um ato deliberado de
            publicacao.
          </p>
        </div>
        <Link
          to="/admin/eventos/novo"
          className="ml-auto rounded-md bg-marca px-4 py-2 text-sm font-medium text-fundo hover:bg-marca-forte"
        >
          Novo evento
        </Link>
      </div>

      <div className="mb-6 flex flex-wrap gap-2">
        {FILTROS.map((filtro) => (
          <button
            key={filtro.valor}
            onClick={() => {
              setStatus(filtro.valor)
              setPagina(0)
            }}
            className={`rounded-md px-3 py-1.5 text-sm transition ${
              status === filtro.valor
                ? 'bg-marca text-fundo'
                : 'border border-borda text-suave hover:text-texto'
            }`}
          >
            {filtro.rotulo}
          </button>
        ))}
      </div>

      {consulta.isPending && <Carregando />}
      {consulta.isError && <Erro erro={consulta.error} />}

      {(publicacao.error ?? cancelamento.error) != null && (
        <p className="mb-4 rounded-md bg-erro/10 px-3 py-2 text-sm text-erro">
          {mensagemDe(publicacao.error ?? cancelamento.error)}
        </p>
      )}

      {consulta.data &&
        (consulta.data.content.length === 0 ? (
          <Vazio>Nenhum evento neste filtro.</Vazio>
        ) : (
          <div className="space-y-3">
            {/*
              A listagem administrativa devolve o resumo, sem o campo `status` — por isso os
              botoes nao dependem dele. Publicar ja e idempotente no backend, e cancelar um
              evento cancelado responde erro, que a tela mostra.
            */}
            {consulta.data.content.map((evento) => (
              <Cartao key={evento.id}>
                <div className="flex flex-wrap items-start gap-4">
                  <div className="min-w-0 flex-1">
                    <h2 className="font-medium">{evento.name}</h2>
                    <p className="mt-1 text-sm text-suave">{evento.venue}</p>
                    <p className="mt-1 text-sm">{dataEHora(evento.eventDate)}</p>
                  </div>

                  <div className="text-right">
                    <p className="numerico font-medium text-marca">{dinheiro(evento.price)}</p>
                    <p className="numerico mt-1 text-xs text-suave">
                      {evento.totalTickets} ingressos
                    </p>
                  </div>
                </div>

                <div className="mt-4 flex flex-wrap gap-2 border-t border-borda pt-4">
                  <Link
                    to={`/admin/eventos/${evento.id}`}
                    className="rounded-md border border-borda px-4 py-2 text-sm hover:bg-fundo"
                  >
                    Editar
                  </Link>
                  <Botao
                    variante="neutro"
                    disabled={publicacao.isPending}
                    onClick={() => publicacao.mutate(evento.id)}
                  >
                    Publicar
                  </Botao>
                  <Link
                    to={`/admin/reservas?eventId=${evento.id}`}
                    className="rounded-md border border-borda px-4 py-2 text-sm hover:bg-fundo"
                  >
                    Reservas
                  </Link>
                  <Botao
                    variante="perigo"
                    className="ml-auto"
                    disabled={cancelamento.isPending}
                    onClick={() => cancelamento.mutate(evento.id)}
                  >
                    Cancelar
                  </Botao>
                </div>
              </Cartao>
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
