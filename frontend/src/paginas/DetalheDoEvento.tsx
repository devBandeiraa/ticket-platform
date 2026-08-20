import { useEffect, useRef, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { buscarEvento, consultarDisponibilidade } from '../api/eventos'
import { reservar } from '../api/reservas'
import { useSessao } from '../auth/SessaoContext'
import { Carregando, Erro, mensagemDe } from '../componentes/Estados'
import { Botao, Cartao } from '../componentes/Ui'
import { dataEHora, dinheiro } from '../componentes/formato'

export function DetalheDoEvento() {
  const { id = '' } = useParams()
  const { usuario } = useSessao()
  const navegar = useNavigate()
  const queryClient = useQueryClient()

  const [quantidade, setQuantidade] = useState(1)

  const evento = useQuery({ queryKey: ['evento', id], queryFn: () => buscarEvento(id) })

  const disponibilidade = useQuery({
    queryKey: ['disponibilidade', id],
    queryFn: () => consultarDisponibilidade(id),
    // Estoque muda por acao de outras pessoas, e nao por nada que este usuario faca. Sem
    // atualizar sozinho, a tela mostraria "restam 12" muito depois de terem acabado.
    refetchInterval: 10_000,
  })

  /*
    Uma chave de idempotencia por intencao de compra.

    Fixa enquanto a intencao nao muda: se a resposta se perder no caminho e o usuario clicar de
    novo, a mesma chave devolve a reserva que ja existe, em vez de criar a segunda. Trocar a
    quantidade e outra intencao, e por isso ganha chave nova.
  */
  const chave = useRef(crypto.randomUUID())
  useEffect(() => {
    chave.current = crypto.randomUUID()
  }, [quantidade])

  const reserva = useMutation({
    mutationFn: () => reservar(id, quantidade, chave.current),
    onSuccess: () => {
      // O estoque acabou de mudar por conta desta reserva.
      queryClient.invalidateQueries({ queryKey: ['disponibilidade', id] })
      queryClient.invalidateQueries({ queryKey: ['minhas-reservas'] })
      navegar('/minhas-reservas')
    },
  })

  if (evento.isPending) return <Carregando />
  if (evento.isError) return <Erro erro={evento.error} />

  const restam = disponibilidade.data?.available
  const esgotado = restam !== undefined && restam <= 0
  const maximo = Math.min(10, restam ?? 10)

  return (
    <div className="grid gap-6 lg:grid-cols-[1fr_20rem]">
      <div>
        <Link to="/" className="text-sm text-suave hover:text-texto">
          &larr; voltar ao catalogo
        </Link>

        <h1 className="mt-3 text-2xl font-semibold">{evento.data.name}</h1>
        <p className="mt-1 text-suave">{evento.data.venue}</p>
        <p className="mt-1 text-sm">{dataEHora(evento.data.eventDate)}</p>

        {evento.data.description && (
          <p className="mt-6 whitespace-pre-line text-sm leading-relaxed text-suave">
            {evento.data.description}
          </p>
        )}
      </div>

      <Cartao className="h-fit">
        <p className="numerico text-2xl font-semibold text-marca">{dinheiro(evento.data.price)}</p>

        <p className="mt-2 text-sm text-suave">
          {disponibilidade.isPending ? (
            'consultando disponibilidade...'
          ) : disponibilidade.isError ? (
            'disponibilidade indisponivel no momento'
          ) : (
            <>
              <span className="numerico text-texto">{restam}</span> de{' '}
              <span className="numerico">{disponibilidade.data?.total}</span> ingressos
            </>
          )}
        </p>

        {!usuario ? (
          <div className="mt-5">
            <p className="mb-3 text-sm text-suave">Entre na sua conta para reservar.</p>
            <Link
              to="/login"
              state={{ de: `/eventos/${id}` }}
              className="block rounded-md bg-marca px-4 py-2 text-center text-sm font-medium text-fundo hover:bg-marca-forte"
            >
              Entrar
            </Link>
          </div>
        ) : esgotado ? (
          <p className="mt-5 rounded-md bg-erro/10 px-3 py-2 text-sm text-erro">
            Ingressos esgotados.
          </p>
        ) : (
          <div className="mt-5 space-y-3">
            <label className="block">
              <span className="mb-1 block text-sm text-suave">Quantidade</span>
              <select
                value={quantidade}
                onChange={(e) => setQuantidade(Number(e.target.value))}
                className="w-full rounded-md border border-borda bg-fundo px-3 py-2 text-sm outline-none focus:border-marca"
              >
                {Array.from({ length: Math.max(1, maximo) }, (_, i) => i + 1).map((n) => (
                  <option key={n} value={n}>
                    {n}
                  </option>
                ))}
              </select>
            </label>

            <p className="numerico text-sm text-suave">
              Total: {dinheiro(evento.data.price * quantidade)}
            </p>

            <Botao
              className="w-full"
              disabled={reserva.isPending}
              onClick={() => reserva.mutate()}
            >
              {reserva.isPending ? 'Reservando...' : 'Reservar'}
            </Botao>

            {reserva.isError && (
              <p className="rounded-md bg-erro/10 px-3 py-2 text-sm text-erro">
                {mensagemDe(reserva.error)}
              </p>
            )}

            <p className="text-xs text-suave">
              A reserva segura os ingressos por tempo limitado. O pagamento e feito na tela de
              minhas reservas.
            </p>
          </div>
        )}
      </Cartao>
    </div>
  )
}
