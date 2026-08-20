import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { consultarDisponibilidade, listarPublicados } from '../api/eventos'
import { cancelarReserva, reservar } from '../api/reservas'
import { ErroDaApi } from '../api/cliente'
import { useSessao } from '../auth/SessaoContext'
import { Carregando, Erro, Vazio } from '../componentes/Estados'
import { NumeroAnimado } from '../componentes/NumeroAnimado'
import { Botao, Cartao, Estatistica } from '../componentes/Ui'

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
  const recusadas = resultado ? resultado.disparadas - resultado.confirmadas : 0

  return (
    <div className="mx-auto max-w-2xl">
      <div className="text-center">
        <h1 className="text-3xl font-semibold tracking-tight">Teste de concorrencia</h1>
        <p className="mx-auto mt-3 max-w-xl text-sm leading-relaxed text-pretty text-suave">
          Dispara varias reservas ao mesmo tempo contra o mesmo evento. A garantia de que nunca
          se vende mais que a capacidade nao vem do lock distribuido — ele e otimizacao — e sim
          de um <code className="rounded bg-superficie px-1 text-texto">UPDATE</code> condicional
          com <code className="rounded bg-superficie px-1 text-texto">CHECK constraint</code> no
          PostgreSQL. Quem perde a corrida recebe{' '}
          <code className="rounded bg-superficie px-1 text-texto">409 SOLD_OUT</code>.
        </p>
      </div>

      {!usuario ? (
        <Cartao className="mt-8">
          <p className="text-sm text-suave">
            Reservar exige uma conta.{' '}
            <Link
              to="/login"
              state={{ de: '/demo/concorrencia' }}
              className="text-marca hover:underline"
            >
              Entre
            </Link>{' '}
            para rodar o teste.
          </p>
        </Cartao>
      ) : (
        <>
          <Cartao className="mt-8 space-y-5">
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
                  <label className="group block">
                    <span className="mb-1.5 block text-sm text-suave">Evento</span>
                    <select
                      value={eventoId}
                      onChange={(e) => {
                        setEventoId(e.target.value)
                        setResultado(null)
                      }}
                      className="w-full rounded-md border border-borda bg-fundo/60 px-3 py-2 text-sm outline-none transition-colors hover:border-borda-clara focus:border-marca"
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
                    <span className="mb-1.5 flex items-baseline justify-between text-sm text-suave">
                      Reservas simultaneas
                      <span className="numerico text-2xl font-semibold text-marca">{quantas}</span>
                    </span>
                    <input
                      type="range"
                      min={2}
                      max={60}
                      value={quantas}
                      onChange={(e) => setQuantas(Number(e.target.value))}
                      className="w-full accent-[var(--color-marca)]"
                    />
                    <span className="mt-1.5 block text-xs text-suave">
                      Acima de 40 o rate limiter do gateway comeca a recusar antes de a
                      requisicao chegar ao booking-service — a rajada configurada e de 40.
                    </span>
                  </label>

                  {estoque.data && (
                    <div className="flex items-center justify-between rounded-lg border border-borda/60 bg-fundo/40 px-4 py-3 text-sm">
                      <span className="text-suave">Estoque agora</span>
                      <span className="numerico">
                        <span className="font-semibold text-texto">{estoque.data.available}</span>
                        <span className="text-suave"> de {estoque.data.total} disponiveis</span>
                      </span>
                    </div>
                  )}

                  <Botao className="w-full py-3" disabled={!eventoId || rodando} onClick={disparar}>
                    {rodando ? (
                      <span className="inline-flex items-center gap-2">
                        <span className="size-4 animate-spin rounded-full border-2 border-fundo/30 border-t-fundo" />
                        Disparando {quantas} reservas...
                      </span>
                    ) : (
                      `Disparar ${quantas} reservas simultaneas`
                    )}
                  </Botao>
                </>
              ))}
          </Cartao>

          {resultado && estoque.data && (
            <div className="mt-6 animate-subir space-y-4">
              {/*
                O veredito vem antes da tabela, e nao depois. Quem assiste a demo precisa da
                conclusao primeiro; o detalhamento e para quem quiser conferir como se chegou
                nela.
              */}
              <Cartao
                className={`text-center ${
                  vendidosAMais === 0 ? 'border-ok/40 bg-ok/5' : 'border-erro/40 bg-erro/5'
                }`}
              >
                <Estatistica
                  rotulo="vendidos a mais"
                  valor={<NumeroAnimado valor={vendidosAMais} />}
                  cor={vendidosAMais === 0 ? 'text-ok' : 'text-erro'}
                />
                <p className="mt-3 text-sm text-suave">
                  {vendidosAMais === 0 ? (
                    <>
                      <span className="numerico font-medium text-texto">
                        {resultado.disparadas}
                      </span>{' '}
                      requisicoes simultaneas,{' '}
                      <span className="numerico font-medium text-ok">
                        {estoque.data.reserved}
                      </span>{' '}
                      ingressos vendidos de{' '}
                      <span className="numerico font-medium text-texto">{estoque.data.total}</span>.
                      A invariante se manteve.
                    </>
                  ) : (
                    'A invariante foi violada. Isto nao deveria acontecer.'
                  )}
                </p>
              </Cartao>

              <Cartao>
                <div className="grid grid-cols-3 gap-4">
                  <Estatistica
                    rotulo="disparadas"
                    valor={<NumeroAnimado valor={resultado.disparadas} />}
                  />
                  <Estatistica
                    rotulo="confirmadas"
                    valor={<NumeroAnimado valor={resultado.confirmadas} />}
                    cor="text-ok"
                  />
                  <Estatistica
                    rotulo="recusadas"
                    valor={<NumeroAnimado valor={recusadas} />}
                    cor="text-suave"
                  />
                </div>

                {/* A barra torna a proporcao imediata: a fatia verde e exatamente o quanto
                    cabia no estoque, e o resto bateu na condicao do UPDATE. */}
                <div className="mt-6 flex h-2.5 overflow-hidden rounded-full bg-fundo">
                  <div
                    className="bg-ok transition-all duration-700 ease-out"
                    style={{ width: `${(resultado.confirmadas / resultado.disparadas) * 100}%` }}
                  />
                  <div
                    className="bg-suave/30 transition-all duration-700 ease-out"
                    style={{ width: `${(recusadas / resultado.disparadas) * 100}%` }}
                  />
                </div>

                <dl className="mt-6 space-y-2.5 border-t border-borda/60 pt-5 text-sm">
                  <Linha rotulo="201 confirmadas" valor={resultado.confirmadas} cor="text-ok" />
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
                  <Linha
                    rotulo="reservados / capacidade"
                    valor={`${estoque.data.reserved} / ${estoque.data.total}`}
                  />
                </dl>

                {resultado.limiteExcedido > 0 && (
                  <p className="mt-5 rounded-lg border border-alerta/30 bg-alerta/10 px-3 py-2.5 text-xs text-alerta">
                    Parte das requisicoes foi barrada pelo rate limiter antes de chegar ao
                    booking-service. Reduza a quantidade para que o teste meca a concorrencia no
                    estoque, e nao o limite da borda.
                  </p>
                )}

                {resultado.reservasCriadas.length > 0 && (
                  <Botao
                    variante="neutro"
                    className="mt-5 w-full"
                    disabled={limpando}
                    onClick={limparRodada}
                  >
                    {limpando
                      ? 'Cancelando...'
                      : `Cancelar as ${resultado.reservasCriadas.length} reservas desta rodada`}
                  </Botao>
                )}
              </Cartao>
            </div>
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
      <div className="flex-1 border-b border-dashed border-borda/60" />
      <dd className={`numerico font-medium ${cor}`}>{valor}</dd>
    </div>
  )
}
