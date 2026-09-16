import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { cancelarReserva, listarMinhas } from '../api/reservas'
import type { Reserva } from '../api/tipos'
import { Carregando, Erro, Vazio, mensagemDe } from '../componentes/Estados'
import { Botao, Cartao, Paginacao, Secao, SeloDeReserva } from '../componentes/Ui'
import { ContagemRegressiva } from '../componentes/ContagemRegressiva'
import { dataEHora, dinheiro } from '../componentes/formato'

export function MinhasReservas() {
  const [pagina, setPagina] = useState(0)
  const queryClient = useQueryClient()

  const consulta = useQuery({
    queryKey: ['minhas-reservas', pagina],
    queryFn: () => listarMinhas({ page: pagina, size: 10 }),
  })

  function recarregar() {
    queryClient.invalidateQueries({ queryKey: ['minhas-reservas'] })
    // A disponibilidade tambem mudou: cancelar devolve ingressos ao estoque.
    queryClient.invalidateQueries({ queryKey: ['disponibilidade'] })
  }

  return (
    <Secao>
      <h1 className="mb-6 text-2xl font-semibold">Minhas reservas</h1>

      {consulta.isPending && <Carregando />}
      {consulta.isError && <Erro erro={consulta.error} />}

      {consulta.data &&
        (consulta.data.content.length === 0 ? (
          <Vazio>
            Voce ainda nao reservou nada.{' '}
            <Link to="/" className="text-marca hover:underline">
              Ver eventos
            </Link>
          </Vazio>
        ) : (
          <div className="space-y-4">
            {consulta.data.content.map((reserva) => (
              <LinhaDeReserva key={reserva.id} reserva={reserva} aoMudar={recarregar} />
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
    </Secao>
  )
}

function LinhaDeReserva({ reserva, aoMudar }: { reserva: Reserva; aoMudar: () => void }) {
  const cancelamento = useMutation({
    mutationFn: () => cancelarReserva(reserva.id),
    onSuccess: aoMudar,
  })

  const pendente = reserva.status === 'PENDING'
  const ocupado = cancelamento.isPending
  const falha = cancelamento.error

  return (
    <Cartao>
      <div className="flex flex-wrap items-start gap-4">
        <div className="min-w-0 flex-1">
          <div className="flex flex-wrap items-center gap-3">
            <SeloDeReserva status={reserva.status} />
            <span className="numerico text-sm text-suave">
              {reserva.quantity} {reserva.quantity === 1 ? 'ingresso' : 'ingressos'}
            </span>
          </div>

          {/* Os lugares vem vazios nas reservas anteriores a Fase 17, feitas quando o sistema
              contava ingressos sem saber quais eram. A tela simplesmente nao mostra a linha. */}
          {reserva.seats.length > 0 && (
            <ul className="mt-2 flex flex-wrap gap-1.5">
              {reserva.seats.map((assento) => (
                <li
                  key={assento.seatId}
                  className="rounded border border-borda bg-superficie/60 px-2 py-0.5 text-xs text-suave"
                  title={dinheiro(assento.price)}
                >
                  {assento.label}
                </li>
              ))}
            </ul>
          )}

          <Link
            to={`/eventos/${reserva.eventId}`}
            className="mt-2 block text-sm text-marca hover:underline"
          >
            ver evento
          </Link>

          <p className="mt-2 text-xs text-suave">
            reservada em {dataEHora(reserva.createdAt)}
            {reserva.paidAt && ` · paga em ${dataEHora(reserva.paidAt)}`}
          </p>
        </div>

        <div className="text-right">
          <p className="numerico text-lg font-semibold">{dinheiro(reserva.totalPrice)}</p>

          {pendente && reserva.expiresAt && (
            <p className="mt-1 text-xs text-suave">
              expira em{' '}
              {/* Ao zerar, recarrega: quem decide o status final e o backend, nao este relogio. */}
              <ContagemRegressiva expiraEm={reserva.expiresAt} aoExpirar={aoMudar} />
            </p>
          )}
        </div>
      </div>

      {pendente && (
        <div className="mt-4 flex flex-wrap items-center gap-2 border-t border-borda pt-4">
          {/* Leva ao checkout em vez de pagar aqui. O pagamento passou a exigir uma escolha —
              cartao ou Pix — e um botao que decide sozinho por quem clicou nao e um atalho, e
              sim uma escolha tomada no lugar da pessoa. */}
          <Link
            to={`/checkout/${reserva.id}`}
            className="rounded-cartao bg-marca px-4 py-2 text-sm font-medium text-superficie transition-colors hover:bg-marca-forte"
          >
            Finalizar compra
          </Link>
          <Botao variante="perigo" disabled={ocupado} onClick={() => cancelamento.mutate()}>
            Cancelar
          </Botao>
        </div>
      )}

      {falha != null && (
        <p className="mt-3 rounded-md bg-erro/10 px-3 py-2 text-sm text-erro">
          {mensagemDe(falha)}
        </p>
      )}
    </Cartao>
  )
}
