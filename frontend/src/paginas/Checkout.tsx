import { useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { buscarEvento } from '../api/eventos'
import { buscarReserva, pagar } from '../api/reservas'
import type { FormaDePagamento, Reserva } from '../api/tipos'
import { useSessao } from '../auth/SessaoContext'
import { ContagemRegressiva } from '../componentes/ContagemRegressiva'
import { Carregando, Erro, mensagemDe } from '../componentes/Estados'
import { Botao, Secao } from '../componentes/Ui'
import { dataEHora, dinheiro } from '../componentes/formato'

/**
 * Checkout em quatro etapas.
 *
 * <h2>Por que a etapa 02 nao e um formulario</h2>
 *
 * <p>O brief pede "02 Dados". Nao ha dado a coletar: quem chega aqui esta autenticado, e o
 * backend nao aceita nenhum campo de comprador alem do que ja veio no token. Um formulario com
 * campos que ninguem le seria um obstaculo disfarcado de etapa.
 *
 * <p>O que a etapa faz de real e conferir quem vai receber o ingresso. O nome impresso no
 * ingresso vem do claim `name`, e mostra-lo ANTES do pagamento e a unica chance de a pessoa
 * perceber que ele esta errado — depois, o codigo ja foi emitido.
 *
 * <h2>O prazo corre durante tudo isto</h2>
 *
 * <p>A reserva ja existe quando esta tela abre, e segura os lugares por um TTL. Por isso a
 * contagem regressiva fica no resumo, visivel nas quatro etapas, e nao escondida numa delas.
 */

const ETAPAS = ['Ingressos', 'Dados', 'Pagamento', 'Confirmacao'] as const

type Etapa = 0 | 1 | 2 | 3

const FORMAS: { valor: FormaDePagamento; rotulo: string; detalhe: string }[] = [
  { valor: 'CARD', rotulo: 'Cartao', detalhe: 'Aprovacao imediata' },
  { valor: 'PIX', rotulo: 'Pix', detalhe: 'Aprovacao imediata' },
]

function Indicador({ atual }: { atual: Etapa }) {
  return (
    <ol className="flex flex-wrap gap-x-6 gap-y-2">
      {ETAPAS.map((nome, i) => {
        const concluida = i < atual
        const ativa = i === atual

        return (
          <li
            key={nome}
            // `aria-current` e o que um leitor de tela usa para dizer onde a pessoa esta. Sem
            // ele, a lista soa como quatro rotulos sem estado.
            aria-current={ativa ? 'step' : undefined}
            className={`flex items-center gap-2 text-sm ${
              ativa ? 'text-texto' : concluida ? 'text-marca' : 'text-suave'
            }`}
          >
            <span
              className={`numerico flex size-6 items-center justify-center rounded-full border text-xs ${
                ativa
                  ? 'border-marca bg-marca text-superficie'
                  : concluida
                    ? 'border-marca text-marca'
                    : 'border-borda-forte'
              }`}
            >
              {/* O numero fica, mesmo concluida: um tique no lugar do numero apagaria a
                  referencia que o proprio rotulo da etapa usa. */}
              {String(i + 1).padStart(2, '0')}
            </span>
            {nome}
          </li>
        )
      })}
    </ol>
  )
}

function Resumo({
  reserva,
  nomeDoEvento,
  aoExpirar,
}: {
  reserva: Reserva
  nomeDoEvento?: string
  aoExpirar: () => void
}) {
  return (
    <aside className="h-fit rounded-cartao border border-borda bg-superficie p-5 lg:sticky lg:top-20">
      <h2 className="font-medium">Resumo do pedido</h2>

      {nomeDoEvento && <p className="mt-1 text-sm text-suave">{nomeDoEvento}</p>}

      <ul className="mt-4 space-y-1.5">
        {reserva.seats.map((assento) => (
          <li key={assento.seatId} className="flex items-baseline justify-between gap-2 text-sm">
            <span>{assento.label}</span>
            <span className="numerico text-suave">{dinheiro(assento.price)}</span>
          </li>
        ))}
      </ul>

      <dl className="mt-4 space-y-1.5 border-t border-borda pt-4 text-sm">
        <Valor rotulo="Subtotal" valor={reserva.subtotal} />
        {/* A taxa aparece SEMPRE, mesmo zerada. Escondendo quando e zero, o comprador que ve
            uma linha a mais no mes seguinte nao teria como saber quando ela passou a existir. */}
        <Valor rotulo="Taxa de servico" valor={reserva.fee} />
        <div className="flex items-baseline justify-between gap-2 border-t border-borda pt-2 text-base">
          <dt className="font-medium">Total</dt>
          <dd className="numerico font-semibold text-marca">{dinheiro(reserva.totalPrice)}</dd>
        </div>
      </dl>

      {reserva.status === 'PENDING' && reserva.expiresAt && (
        <p className="mt-4 rounded-cartao bg-alerta/10 px-3 py-2 text-xs text-alerta">
          Os lugares estao segurados. Expira em{' '}
          {/* Ao zerar, recarrega: quem decide o status final e o backend, nao este relogio. */}
          <ContagemRegressiva expiraEm={reserva.expiresAt} aoExpirar={aoExpirar} />
        </p>
      )}
    </aside>
  )
}

function Valor({ rotulo, valor }: { rotulo: string; valor: number }) {
  return (
    <div className="flex items-baseline justify-between gap-2">
      <dt className="text-suave">{rotulo}</dt>
      <dd className="numerico">{dinheiro(valor)}</dd>
    </div>
  )
}

export function Checkout() {
  const { id = '' } = useParams()
  const { usuario } = useSessao()
  const navegar = useNavigate()
  const queryClient = useQueryClient()

  const [etapa, setEtapa] = useState<Etapa>(0)
  const [forma, setForma] = useState<FormaDePagamento>('CARD')

  const reserva = useQuery({ queryKey: ['reserva', id], queryFn: () => buscarReserva(id) })

  const evento = useQuery({
    queryKey: ['evento', reserva.data?.eventId],
    queryFn: () => buscarEvento(reserva.data!.eventId),
    enabled: Boolean(reserva.data?.eventId),
  })

  const pagamento = useMutation({
    mutationFn: () => pagar(id, forma),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['reserva', id] })
      queryClient.invalidateQueries({ queryKey: ['minhas-reservas'] })
      setEtapa(3)
    },
  })

  function recarregar() {
    queryClient.invalidateQueries({ queryKey: ['reserva', id] })
  }

  if (reserva.isPending) return <Carregando />
  if (reserva.isError) return <Erro erro={reserva.error} />

  const dados = reserva.data

  // Reserva que nao esta mais pendente nem confirmada — expirou ou foi cancelada. Nao ha
  // checkout a fazer, e insistir levaria a um 409 depois de tres cliques.
  if (dados.status === 'EXPIRED' || dados.status === 'CANCELLED') {
    return (
      <Secao largura="estreita">
        <h1 className="text-2xl font-semibold">
          {dados.status === 'EXPIRED' ? 'O prazo desta reserva venceu' : 'Reserva cancelada'}
        </h1>
        <p className="mt-3 text-suave">
          Os lugares voltaram para o mapa. Escolha de novo para comprar.
        </p>
        <Link
          to={`/eventos/${dados.eventId}`}
          className="mt-6 inline-flex rounded-cartao bg-marca px-4 py-2 text-sm font-medium text-superficie transition-colors hover:bg-marca-forte"
        >
          Voltar ao evento
        </Link>
      </Secao>
    )
  }

  // Ja paga: cai direto na confirmacao, sem passar pelas etapas. Acontece ao recarregar a
  // pagina depois de pagar, ou ao abrir o link de uma reserva ja quitada.
  const jaPaga = dados.status === 'CONFIRMED'
  const etapaEfetiva: Etapa = jaPaga ? 3 : etapa

  return (
    <Secao>
      <Indicador atual={etapaEfetiva} />

      <div className="mt-8 grid gap-8 lg:grid-cols-[1fr_20rem]">
        <div className="min-w-0">
          {etapaEfetiva === 0 && (
            <section>
              <h1 className="text-2xl font-semibold">Confira os ingressos</h1>
              <p className="mt-2 text-suave">
                {dados.quantity} {dados.quantity === 1 ? 'lugar' : 'lugares'} em{' '}
                {evento.data?.name ?? 'carregando...'}.
              </p>

              <ul className="mt-6 divide-y divide-borda rounded-cartao border border-borda">
                {dados.seats.map((assento) => (
                  <li
                    key={assento.seatId}
                    className="flex items-baseline justify-between gap-3 px-4 py-3"
                  >
                    <div>
                      <p className="text-sm font-medium">{assento.label}</p>
                      <p className="text-xs text-suave">{assento.sector}</p>
                    </div>
                    <span className="numerico text-sm">{dinheiro(assento.price)}</span>
                  </li>
                ))}
              </ul>

              <Botao className="mt-6" onClick={() => setEtapa(1)}>
                Continuar
              </Botao>
            </section>
          )}

          {etapaEfetiva === 1 && (
            <section>
              <h1 className="text-2xl font-semibold">Quem vai receber</h1>
              <p className="mt-2 text-suave">
                Estes dados vao impressos no ingresso. Confira antes de pagar — depois o codigo
                ja foi emitido.
              </p>

              <dl className="mt-6 space-y-4 rounded-cartao border border-borda p-5">
                <div>
                  <dt className="text-xs uppercase tracking-wide text-suave">Titular</dt>
                  {/* Token emitido antes da Fase 23 nao traz o nome. O email serve, e a
                      mensagem diz o que fazer em vez de mostrar um campo vazio. */}
                  <dd className="mt-1">
                    {usuario?.fullName ?? (
                      <span className="text-suave">
                        nao informado — saia e entre de novo para atualizar
                      </span>
                    )}
                  </dd>
                </div>
                <div>
                  <dt className="text-xs uppercase tracking-wide text-suave">E-mail</dt>
                  <dd className="mt-1">{usuario?.email}</dd>
                </div>
              </dl>

              <div className="mt-6 flex gap-2">
                <Botao variante="neutro" onClick={() => setEtapa(0)}>
                  Voltar
                </Botao>
                <Botao onClick={() => setEtapa(2)}>Continuar</Botao>
              </div>
            </section>
          )}

          {etapaEfetiva === 2 && (
            <section>
              <h1 className="text-2xl font-semibold">Pagamento</h1>

              {/* `radiogroup` com `aria-label`: sem ele, um leitor de tela anuncia dois botoes
                  soltos sem dizer que sao alternativas da mesma escolha. */}
              <div role="radiogroup" aria-label="Forma de pagamento" className="mt-6 grid gap-3">
                {FORMAS.map((opcao) => (
                  <button
                    key={opcao.valor}
                    type="button"
                    role="radio"
                    aria-checked={forma === opcao.valor}
                    onClick={() => setForma(opcao.valor)}
                    className={`flex items-center justify-between rounded-cartao border px-4 py-3 text-left transition-colors ${
                      forma === opcao.valor
                        ? 'border-marca bg-marca/5'
                        : 'border-borda-forte hover:border-marca'
                    }`}
                  >
                    <span>
                      <span className="block text-sm font-medium">{opcao.rotulo}</span>
                      <span className="block text-xs text-suave">{opcao.detalhe}</span>
                    </span>

                    <span
                      aria-hidden="true"
                      className={`size-4 rounded-full border-2 ${
                        forma === opcao.valor ? 'border-marca bg-marca' : 'border-borda-forte'
                      }`}
                    />
                  </button>
                ))}
              </div>

              <p className="mt-4 text-xs text-suave">
                O provedor de pagamento e simulado. Nenhuma cobranca real acontece, e nenhum dado
                de cartao e pedido ou guardado.
              </p>

              <div className="mt-6 flex gap-2">
                <Botao variante="neutro" disabled={pagamento.isPending} onClick={() => setEtapa(1)}>
                  Voltar
                </Botao>
                <Botao disabled={pagamento.isPending} onClick={() => pagamento.mutate()}>
                  {pagamento.isPending ? 'Processando...' : 'Finalizar compra'}
                </Botao>
              </div>

              {pagamento.isError && (
                <p className="mt-4 rounded-cartao bg-erro/10 px-3 py-2 text-sm text-erro">
                  {mensagemDe(pagamento.error)}
                </p>
              )}
            </section>
          )}

          {etapaEfetiva === 3 && (
            <section>
              <h1 className="text-2xl font-semibold text-ok">Ingresso confirmado!</h1>
              <p className="mt-2 text-suave">
                O pagamento foi aprovado e os lugares sao seus.
              </p>

              {dados.ticketCode && (
                <p className="numerico mt-6 rounded-cartao border border-borda bg-superficie px-4 py-3 text-sm">
                  <span className="block text-xs uppercase tracking-wide text-suave">
                    Numero do ingresso
                  </span>
                  <span className="mt-1 block text-lg font-semibold tracking-wider">
                    {dados.ticketCode}
                  </span>
                </p>
              )}

              {/* O ingresso desenhado, com QR, e do estagio G. Ate la, o link leva a lista. */}
              <Link
                to="/minhas-reservas"
                className="mt-6 inline-flex rounded-cartao bg-marca px-4 py-2 text-sm font-medium text-superficie transition-colors hover:bg-marca-forte"
              >
                Ver meus ingressos
              </Link>
            </section>
          )}
        </div>

        <Resumo reserva={dados} nomeDoEvento={evento.data?.name} aoExpirar={recarregar} />
      </div>

      {evento.data && (
        <p className="mt-8 text-xs text-suave">
          {evento.data.venue} · {dataEHora(evento.data.eventDate)} ·{' '}
          <button
            type="button"
            onClick={() => navegar(`/eventos/${dados.eventId}`)}
            className="text-marca hover:underline"
          >
            ver o evento
          </button>
        </p>
      )}
    </Secao>
  )
}
