import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useMutation, useQueries, useQuery, useQueryClient } from '@tanstack/react-query'
import { buscarEvento } from '../api/eventos'
import { cancelarReserva, listarMinhas } from '../api/reservas'
import type { EventoResumo, Reserva } from '../api/tipos'
import { useSessao } from '../auth/SessaoContext'
import { ContagemRegressiva } from '../componentes/ContagemRegressiva'
import { Carregando, Erro, Vazio, mensagemDe } from '../componentes/Estados'
import { DialogoDoIngresso, IngressoDigital } from '../componentes/IngressoDigital'
import { Botao, Paginacao, Secao, SeloDeReserva } from '../componentes/Ui'
import {
  ROTULOS_DE_ABA,
  abaDe,
  eventosDistintos,
  linkDeCalendario,
  type Aba,
} from '../componentes/ingressos'
import { dataEHora, dinheiro } from '../componentes/formato'

const ABAS = Object.keys(ROTULOS_DE_ABA) as Aba[]

export function MeusIngressos() {
  const [pagina, setPagina] = useState(0)
  const [aba, setAba] = useState<Aba>('proximos')
  const [aberto, setAberto] = useState<string | null>(null)
  const { usuario } = useSessao()
  const queryClient = useQueryClient()

  const consulta = useQuery({
    queryKey: ['meus-ingressos', pagina],
    queryFn: () => listarMinhas({ page: pagina, size: 10 }),
  })

  const reservas = consulta.data?.content ?? []

  /*
    Um evento por id DISTINTO, e nao um por reserva: quatro ingressos do mesmo show sao uma
    consulta, e nao quatro. E a mesma familia de N+1 do selo de disponibilidade (risco #91), e o
    mesmo endpoint em lote resolveria os dois.

    Aqui a informacao decide em qual ABA o ingresso cai, entao a alternativa de nao buscar nao
    existe: sem a data do evento, "utilizados" nunca teria conteudo.
  */
  const eventos = useQueries({
    queries: eventosDistintos(reservas).map((id) => ({
      queryKey: ['evento', id],
      queryFn: () => buscarEvento(id),
      staleTime: 5 * 60_000,
    })),
  })

  const porEvento = new Map<string, EventoResumo>()
  eventos.forEach((e) => {
    if (e.data) porEvento.set(e.data.id, e.data)
  })

  const agora = new Date()
  const porAba = (alvo: Aba) => reservas.filter((r) => abaDe(r, porEvento.get(r.eventId), agora) === alvo)
  const visiveis = porAba(aba)

  function recarregar() {
    queryClient.invalidateQueries({ queryKey: ['meus-ingressos'] })
    // A disponibilidade tambem mudou: cancelar devolve ingressos ao estoque.
    queryClient.invalidateQueries({ queryKey: ['disponibilidade'] })
  }

  return (
    <Secao>
      <h1 className="text-2xl font-semibold">Meus ingressos</h1>

      {/* `role=tablist` com `aria-selected`: sem isso, um leitor de tela anuncia tres botoes
          soltos e nao diz qual esta ativo nem que sao alternativas. */}
      <div role="tablist" aria-label="Filtrar ingressos" className="mt-6 flex flex-wrap gap-2">
        {ABAS.map((opcao) => (
          <button
            key={opcao}
            type="button"
            role="tab"
            aria-selected={aba === opcao}
            onClick={() => setAba(opcao)}
            className={`rounded-full border px-4 py-1.5 text-sm transition-colors ${
              aba === opcao
                ? 'border-marca bg-marca text-superficie'
                : 'border-borda-forte text-suave hover:border-marca hover:text-marca'
            }`}
          >
            {ROTULOS_DE_ABA[opcao]}
            <span className="numerico ml-2 opacity-70">{porAba(opcao).length}</span>
          </button>
        ))}
      </div>

      <div className="mt-8">
        {consulta.isPending && <Carregando />}
        {consulta.isError && <Erro erro={consulta.error} />}

        {consulta.data &&
          (reservas.length === 0 ? (
            <Vazio>
              Voce ainda nao comprou nada.{' '}
              <Link to="/explorar" className="text-marca hover:underline">
                Ver eventos
              </Link>
            </Vazio>
          ) : visiveis.length === 0 ? (
            <Vazio>Nenhum ingresso em {ROTULOS_DE_ABA[aba].toLowerCase()}.</Vazio>
          ) : (
            <div className="grid gap-5 sm:grid-cols-2">
              {visiveis.map((reserva) => (
                <CartaoDeIngresso
                  key={reserva.id}
                  reserva={reserva}
                  evento={porEvento.get(reserva.eventId)}
                  comprador={usuario?.fullName}
                  aberto={aberto === reserva.id}
                  aoAbrir={() => setAberto(reserva.id)}
                  aoFechar={() => setAberto(null)}
                  aoMudar={recarregar}
                />
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
      </div>
    </Secao>
  )
}

function CartaoDeIngresso({
  reserva,
  evento,
  comprador,
  aberto,
  aoAbrir,
  aoFechar,
  aoMudar,
}: {
  reserva: Reserva
  evento?: EventoResumo
  comprador?: string | null
  aberto: boolean
  aoAbrir: () => void
  aoFechar: () => void
  aoMudar: () => void
}) {
  const cancelamento = useMutation({
    mutationFn: () => cancelarReserva(reserva.id),
    onSuccess: aoMudar,
  })

  const pendente = reserva.status === 'PENDING'
  const temIngresso = reserva.status === 'CONFIRMED' && reserva.ticketCode

  return (
    <article className="rounded-cartao border border-borda bg-superficie p-4">
      <div className="mb-3 flex flex-wrap items-center justify-between gap-2">
        <SeloDeReserva status={reserva.status} />
        <span className="numerico text-sm font-semibold">{dinheiro(reserva.totalPrice)}</span>
      </div>

      {temIngresso ? (
        // A miniatura e um botao: o ingresso inteiro e o alvo, porque um "ver" pequeno ao lado
        // seria um alvo menor do que a propria figura que convida a clicar.
        <button
          type="button"
          onClick={aoAbrir}
          className="block w-full rounded-cartao text-left transition-transform hover:scale-[1.01] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-marca"
          aria-label={`Abrir ingresso ${reserva.ticketCode}`}
        >
          <IngressoDigital reserva={reserva} evento={evento} compacto />
        </button>
      ) : (
        <div className="rounded-cartao border border-borda p-4">
          <p className="font-medium">{evento?.name ?? 'Carregando evento...'}</p>
          {evento && <p className="numerico mt-1 text-sm text-suave">{dataEHora(evento.eventDate)}</p>}

          <ul className="mt-3 flex flex-wrap gap-1.5">
            {reserva.seats.map((assento) => (
              <li
                key={assento.seatId}
                className="rounded border border-borda px-2 py-0.5 text-xs text-suave"
              >
                {assento.label}
              </li>
            ))}
          </ul>
        </div>
      )}

      {pendente && reserva.expiresAt && (
        <p className="mt-3 rounded-cartao bg-alerta/10 px-3 py-2 text-xs text-alerta">
          Pagamento pendente. Expira em{' '}
          {/* Ao zerar, recarrega: quem decide o status final e o backend, nao este relogio. */}
          <ContagemRegressiva expiraEm={reserva.expiresAt} aoExpirar={aoMudar} />
        </p>
      )}

      <div className="mt-3 flex flex-wrap items-center gap-2">
        {pendente && (
          <Link
            to={`/checkout/${reserva.id}`}
            className="rounded-cartao bg-marca px-3 py-1.5 text-sm font-medium text-superficie transition-colors hover:bg-marca-forte"
          >
            Finalizar compra
          </Link>
        )}

        {temIngresso && (
          <>
            <button
              type="button"
              onClick={aoAbrir}
              className="rounded-cartao border border-borda-forte px-3 py-1.5 text-sm transition-colors hover:border-marca hover:text-marca"
            >
              Ver QR Code
            </button>

            {evento && (
              <a
                href={linkDeCalendario(evento, reserva.ticketCode)}
                target="_blank"
                // `noreferrer` junto de `noopener`: sem ele, a pagina de destino recebe de qual
                // endereco o usuario veio, e a agenda dele nao tem o que fazer com isso.
                rel="noopener noreferrer"
                className="rounded-cartao border border-borda-forte px-3 py-1.5 text-sm transition-colors hover:border-marca hover:text-marca"
              >
                Adicionar ao calendario
              </a>
            )}
          </>
        )}

        {pendente && (
          <Botao
            variante="perigo"
            disabled={cancelamento.isPending}
            onClick={() => cancelamento.mutate()}
          >
            Cancelar
          </Botao>
        )}
      </div>

      {cancelamento.error != null && (
        <p className="mt-3 rounded-cartao bg-erro/10 px-3 py-2 text-sm text-erro">
          {mensagemDe(cancelamento.error)}
        </p>
      )}

      {temIngresso && (
        <DialogoDoIngresso
          reserva={reserva}
          evento={evento}
          comprador={comprador}
          aberto={aberto}
          aoFechar={aoFechar}
        />
      )}
    </article>
  )
}
