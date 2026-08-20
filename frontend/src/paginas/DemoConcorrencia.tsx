import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { consultarDisponibilidade, listarPublicados } from '../api/eventos'
import { cancelarReserva, reservar } from '../api/reservas'
import { ErroDaApi } from '../api/cliente'
import { useSessao } from '../auth/SessaoContext'
import { Carregando, Erro, Vazio } from '../componentes/Estados'
import { Botao, Cartao } from '../componentes/Ui'

interface Resultado {
  confirmadas: number
  esgotado: number
  lockTimeout: number
  limiteExcedido: number
  outros: Array<{ codigo: string; quantidade: number }>
  reservasCriadas: string[]
  disparadas: number
}

/**
 * Dispara N reservas simultaneas contra o mesmo evento e mostra o que aconteceu.
 *
 * E a tese do projeto na tela. A garantia contra overselling nao esta no lock distribuido —
 * o lock e otimizacao — e sim no `UPDATE` condicional com `CHECK constraint` no PostgreSQL.
 * O que se ve aqui e o resultado disso: por mais requisicoes que cheguem juntas, o total
 * vendido nunca passa da capacidade.
 *
 * O mesmo ja e verificado por teste automatizado no booking-service, com 200 threads. Esta
 * pagina existe porque quem visita o repositorio nao vai rodar um teste JUnit.
 */
export function DemoConcorrencia() {
  const { usuario } = useSessao()

  const [eventoId, setEventoId] = useState('')
  const [quantas, setQuantas] = useState(30)
  const [rodando, setRodando] = useState(false)
  const [resultado, setResultado] = useState<Resultado | null>(null)
  const [limpando, setLimpando] = useState(false)

  const eventos = useQuery({
    queryKey: ['eventos', 0, ''],
    queryFn: () => listarPublicados({ page: 0, size: 50 }),
  })

  const estoque = useQuery({
    queryKey: ['disponibilidade', eventoId],
    queryFn: () => consultarDisponibilidade(eventoId),
    enabled: Boolean(eventoId),
  })

  async function disparar() {
    setRodando(true)
    setResultado(null)

    // Promise.allSettled, e nao um laco: o objetivo e que saiam juntas. Uma de cada vez nao
    // produziria concorrencia alguma, e o teste nao provaria nada.
    const respostas = await Promise.allSettled(
      Array.from({ length: quantas }, () => reservar(eventoId, 1, crypto.randomUUID())),
    )

    const contagem: Record<string, number> = {}
    const reservasCriadas: string[] = []

    for (const resposta of respostas) {
      if (resposta.status === 'fulfilled') {
        reservasCriadas.push(resposta.value.id)
      } else {
        const codigo =
          resposta.reason instanceof ErroDaApi ? resposta.reason.codigo : 'FALHA_DE_REDE'
        contagem[codigo] = (contagem[codigo] ?? 0) + 1
      }
    }

    const conhecidos = ['SOLD_OUT', 'LOCK_TIMEOUT', 'RATE_LIMIT_EXCEEDED']

    setResultado({
      confirmadas: reservasCriadas.length,
      esgotado: contagem.SOLD_OUT ?? 0,
      lockTimeout: contagem.LOCK_TIMEOUT ?? 0,
      limiteExcedido: contagem.RATE_LIMIT_EXCEEDED ?? 0,
      outros: Object.entries(contagem)
        .filter(([codigo]) => !conhecidos.includes(codigo))
        .map(([codigo, quantidade]) => ({ codigo, quantidade })),
      reservasCriadas,
      disparadas: quantas,
    })

    setRodando(false)
    await estoque.refetch()
  }

  /** Devolve os ingressos ao estoque, para a demo poder rodar de novo no mesmo evento. */
  async function limparRodada() {
    if (!resultado) return
    setLimpando(true)
    await Promise.allSettled(resultado.reservasCriadas.map(cancelarReserva))
    setResultado(null)
    setLimpando(false)
    await estoque.refetch()
  }

  const vendidosAMais = estoque.data ? Math.max(0, estoque.data.reserved - estoque.data.total) : 0

  return (
    <div className="mx-auto max-w-2xl">
      <h1 className="text-2xl font-semibold">Teste de concorrencia</h1>
      <p className="mt-2 text-sm leading-relaxed text-suave">
        Dispara varias reservas ao mesmo tempo contra o mesmo evento. A garantia de que nunca se
        vende mais que a capacidade nao vem do lock distribuido — ele e otimizacao — e sim de um{' '}
        <code className="text-texto">UPDATE</code> condicional com{' '}
        <code className="text-texto">CHECK constraint</code> no PostgreSQL. Quem perde a corrida
        recebe <code className="text-texto">409 SOLD_OUT</code>.
      </p>

      {!usuario ? (
        <Cartao className="mt-6">
          <p className="text-sm text-suave">
            Reservar exige uma conta.{' '}
            <Link to="/login" state={{ de: '/demo/concorrencia' }} className="text-marca hover:underline">
              Entre
            </Link>{' '}
            para rodar o teste.
          </p>
        </Cartao>
      ) : (
        <>
          <Cartao className="mt-6 space-y-4">
            {eventos.isPending && <Carregando texto="Carregando eventos..." />}
            {eventos.isError && <Erro erro={eventos.error} />}

            {eventos.data &&
              (eventos.data.content.length === 0 ? (
                <Vazio>
                  Nenhum evento publicado. Crie um em{' '}
                  <Link to="/admin/eventos/novo" className="text-marca hover:underline">
                    gerenciar eventos
                  </Link>
                  .
                </Vazio>
              ) : (
                <>
                  <label className="block">
                    <span className="mb-1 block text-sm text-suave">Evento</span>
                    <select
                      value={eventoId}
                      onChange={(e) => {
                        setEventoId(e.target.value)
                        setResultado(null)
                      }}
                      className="w-full rounded-md border border-borda bg-fundo px-3 py-2 text-sm outline-none focus:border-marca"
                    >
                      <option value="">selecione...</option>
                      {eventos.data.content.map((evento) => (
                        <option key={evento.id} value={evento.id}>
                          {evento.name} ({evento.totalTickets} ingressos)
                        </option>
                      ))}
                    </select>
                  </label>

                  <label className="block">
                    <span className="mb-1 block text-sm text-suave">
                      Reservas simultaneas: <span className="numerico text-texto">{quantas}</span>
                    </span>
                    <input
                      type="range"
                      min={2}
                      max={60}
                      value={quantas}
                      onChange={(e) => setQuantas(Number(e.target.value))}
                      className="w-full accent-[var(--color-marca)]"
                    />
                    <span className="mt-1 block text-xs text-suave">
                      Acima de 40 o rate limiter do gateway comeca a recusar antes de a
                      requisicao chegar ao booking-service — a rajada configurada e de 40.
                    </span>
                  </label>

                  {estoque.data && (
                    <p className="numerico text-sm text-suave">
                      Estoque agora: {estoque.data.available} disponiveis de {estoque.data.total}
                    </p>
                  )}

                  <Botao
                    className="w-full"
                    disabled={!eventoId || rodando}
                    onClick={disparar}
                  >
                    {rodando ? `Disparando ${quantas} reservas...` : 'Disparar'}
                  </Botao>
                </>
              ))}
          </Cartao>

          {resultado && estoque.data && (
            <Cartao className="mt-4">
              <h2 className="mb-4 font-medium">
                Resultado de <span className="numerico">{resultado.disparadas}</span> requisicoes
                simultaneas
              </h2>

              <dl className="space-y-2 text-sm">
                <Linha rotulo="confirmadas" valor={resultado.confirmadas} cor="text-ok" />
                <Linha rotulo="409 SOLD_OUT" valor={resultado.esgotado} />
                {resultado.lockTimeout > 0 && (
                  <Linha rotulo="409 LOCK_TIMEOUT" valor={resultado.lockTimeout} />
                )}
                {resultado.limiteExcedido > 0 && (
                  <Linha
                    rotulo="429 RATE_LIMIT_EXCEEDED"
                    valor={resultado.limiteExcedido}
                    cor="text-alerta"
                  />
                )}
                {resultado.outros.map((outro) => (
                  <Linha key={outro.codigo} rotulo={outro.codigo} valor={outro.quantidade} />
                ))}
              </dl>

              <div className="mt-4 border-t border-borda pt-4">
                <dl className="space-y-2 text-sm">
                  <Linha
                    rotulo="reservados / capacidade"
                    valor={`${estoque.data.reserved} / ${estoque.data.total}`}
                  />
                  <Linha
                    rotulo="vendidos a mais"
                    valor={vendidosAMais}
                    cor={vendidosAMais === 0 ? 'text-ok' : 'text-erro'}
                  />
                </dl>
              </div>

              {resultado.limiteExcedido > 0 && (
                <p className="mt-4 rounded-md bg-alerta/10 px-3 py-2 text-xs text-alerta">
                  Parte das requisicoes foi barrada pelo rate limiter antes de chegar ao
                  booking-service. Reduza a quantidade para que o teste meca a concorrencia no
                  estoque, e nao o limite da borda.
                </p>
              )}

              {resultado.reservasCriadas.length > 0 && (
                <Botao
                  variante="neutro"
                  className="mt-4 w-full"
                  disabled={limpando}
                  onClick={limparRodada}
                >
                  {limpando
                    ? 'Cancelando...'
                    : `Cancelar as ${resultado.reservasCriadas.length} reservas desta rodada`}
                </Botao>
              )}
            </Cartao>
          )}
        </>
      )}
    </div>
  )
}

function Linha({
  rotulo,
  valor,
  cor = '',
}: {
  rotulo: string
  valor: number | string
  cor?: string
}) {
  return (
    <div className="flex items-baseline justify-between gap-4">
      <dt className="text-suave">{rotulo}</dt>
      <div className="flex-1 border-b border-dashed border-borda" />
      <dd className={`numerico font-medium ${cor}`}>{valor}</dd>
    </div>
  )
}
