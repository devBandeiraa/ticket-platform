import { useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { listarParaAdmin } from '../../api/reservas'
import { consultarDisponibilidade } from '../../api/eventos'
import type { StatusDaReserva } from '../../api/tipos'
import { Carregando, Erro, Vazio } from '../../componentes/Estados'
import { Cartao, Paginacao, SeloDeReserva } from '../../componentes/Ui'
import { dataEHora, dinheiro } from '../../componentes/formato'

const FILTROS: Array<{ valor: StatusDaReserva | ''; rotulo: string }> = [
  { valor: '', rotulo: 'Todas' },
  { valor: 'PENDING', rotulo: 'Aguardando' },
  { valor: 'CONFIRMED', rotulo: 'Pagas' },
  { valor: 'CANCELLED', rotulo: 'Canceladas' },
  { valor: 'EXPIRED', rotulo: 'Expiradas' },
]

export function ReservasAdmin() {
  // O eventId vem da URL para que o link "Reservas" da listagem de eventos ja chegue filtrado,
  // e para que o filtro sobreviva a um recarregamento da pagina.
  const [parametros, setParametros] = useSearchParams()
  const eventId = parametros.get('eventId') ?? ''

  const [pagina, setPagina] = useState(0)
  const [status, setStatus] = useState<StatusDaReserva | ''>('')

  const consulta = useQuery({
    queryKey: ['admin-reservas', pagina, status, eventId],
    queryFn: () => listarParaAdmin({ page: pagina, size: 15, status, eventId }),
  })

  // Com um evento escolhido, vale mostrar o estoque ao lado das reservas: e o numero que
  // responde a pergunta que traz alguem a esta tela.
  const estoque = useQuery({
    queryKey: ['disponibilidade', eventId],
    queryFn: () => consultarDisponibilidade(eventId),
    enabled: Boolean(eventId),
  })

  return (
    <>
      <h1 className="mb-1 text-2xl font-semibold">Painel de reservas</h1>
      <p className="mb-6 text-sm text-suave">
        {eventId ? 'Filtrado por evento.' : 'Todas as reservas da plataforma.'}
      </p>

      {eventId && (
        <div className="mb-6 flex flex-wrap items-center gap-4">
          {estoque.data && (
            <Cartao className="flex-1">
              <div className="flex flex-wrap gap-8">
                <Numero rotulo="capacidade" valor={estoque.data.total} />
                <Numero rotulo="reservados" valor={estoque.data.reserved} />
                <Numero rotulo="disponiveis" valor={estoque.data.available} destaque />
              </div>
            </Cartao>
          )}
          <button
            onClick={() => {
              setParametros({})
              setPagina(0)
            }}
            className="rounded-md border border-borda px-3 py-1.5 text-sm text-suave hover:text-texto"
          >
            limpar filtro de evento
          </button>
        </div>
      )}

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

      {consulta.data &&
        (consulta.data.content.length === 0 ? (
          <Vazio>Nenhuma reserva neste filtro.</Vazio>
        ) : (
          <div className="overflow-x-auto rounded-lg border border-borda">
            <table className="w-full text-sm">
              <thead className="border-b border-borda bg-superficie text-left text-suave">
                <tr>
                  <th className="px-4 py-3 font-medium">Status</th>
                  <th className="px-4 py-3 font-medium">Qtd</th>
                  <th className="px-4 py-3 font-medium">Total</th>
                  <th className="px-4 py-3 font-medium">Criada</th>
                  <th className="px-4 py-3 font-medium">Usuario</th>
                </tr>
              </thead>
              <tbody>
                {consulta.data.content.map((reserva) => (
                  <tr key={reserva.id} className="border-b border-borda last:border-0">
                    <td className="px-4 py-3">
                      <SeloDeReserva status={reserva.status} />
                    </td>
                    <td className="numerico px-4 py-3">{reserva.quantity}</td>
                    <td className="numerico px-4 py-3">{dinheiro(reserva.totalPrice)}</td>
                    <td className="px-4 py-3 text-suave">{dataEHora(reserva.createdAt)}</td>
                    <td className="numerico px-4 py-3 text-xs text-suave">
                      {/* O painel identifica por id: o booking-service nao conhece e-mail,
                          e busca-lo no auth-service so para preencher uma coluna colocaria
                          uma dependencia entre servicos onde nao havia nenhuma. */}
                      {reserva.userId.slice(0, 8)}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
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

function Numero({
  rotulo,
  valor,
  destaque = false,
}: {
  rotulo: string
  valor: number
  destaque?: boolean
}) {
  return (
    <div>
      <p className={`numerico text-2xl font-semibold ${destaque ? 'text-marca' : ''}`}>{valor}</p>
      <p className="text-xs text-suave">{rotulo}</p>
    </div>
  )
}
