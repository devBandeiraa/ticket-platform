import { Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { listarParaAdmin } from '../../api/eventos'
import { consultarMetricas } from '../../api/metricas'
import { Carregando, Erro } from '../../componentes/Estados'
import { Cartao, Secao } from '../../componentes/Ui'
import { dinheiro } from '../../componentes/formato'

/**
 * Painel de quem organiza.
 *
 * <h2>De onde vem cada numero</h2>
 *
 * <p>Receita, ingressos, reservas e conversao vem de `GET /admin/metrics`, que agrega o banco do
 * booking-service numa consulta so. "Eventos publicados" vem do event-service, porque e ele o
 * dono do catalogo — o booking-service so conhece os eventos que alguem ja tentou reservar.
 *
 * <p>Nao confundir com a pagina de status, que le o Prometheus. Aquela conta o que os processos
 * viram desde que subiram e zera a cada reinicio; esta responde "quanto ja vendemos".
 */
export function Painel() {
  const metricas = useQuery({
    queryKey: ['metricas'],
    queryFn: consultarMetricas,
    // Um painel de vendas fica aberto. Meio minuto e frequente o bastante para acompanhar uma
    // abertura de vendas e raro o bastante para nao pesar sobre consultas que varrem tabela.
    refetchInterval: 30_000,
  })

  const publicados = useQuery({
    queryKey: ['admin-eventos', 'contagem-publicados'],
    // `size: 1` porque so interessa o total da pagina, e nao o conteudo dela.
    queryFn: () => listarParaAdmin({ page: 0, size: 1, status: 'PUBLISHED' }),
    refetchInterval: 30_000,
  })

  if (metricas.isPending) return <Carregando />
  if (metricas.isError) return <Erro erro={metricas.error} />

  const m = metricas.data
  const receitaTotal = m.receitaDosIngressos + m.taxaArrecadada

  return (
    <Secao>
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="text-2xl font-semibold">Painel</h1>
          <p className="mt-1 text-sm text-suave">
            Agregados do banco, no instante da consulta. Atualiza sozinho a cada meio minuto.
          </p>
        </div>

        <Link
          to="/admin/eventos"
          className="rounded-cartao bg-marca px-4 py-2 text-sm font-medium text-superficie transition-colors hover:bg-marca-forte"
        >
          Meus eventos
        </Link>
      </div>

      {/* Dinheiro em destaque, e as duas parcelas separadas logo abaixo. Somados num numero so,
          nao responderiam nem "quanto o evento rendeu" nem "quanto a plataforma reteve" — sao
          perguntas de donos diferentes. */}
      <div className="mt-8 grid gap-4 sm:grid-cols-2">
        <Cartao>
          <p className="text-xs uppercase tracking-wide text-suave">Receita dos ingressos</p>
          <p className="numerico mt-2 text-3xl font-semibold text-marca">
            {dinheiro(m.receitaDosIngressos)}
          </p>
          <p className="mt-2 text-xs text-suave">o que os ingressos renderam</p>
        </Cartao>

        <Cartao>
          <p className="text-xs uppercase tracking-wide text-suave">Taxa arrecadada</p>
          <p className="numerico mt-2 text-3xl font-semibold">{dinheiro(m.taxaArrecadada)}</p>
          <p className="numerico mt-2 text-xs text-suave">
            {dinheiro(receitaTotal)} cobrados no total
          </p>
        </Cartao>
      </div>

      <div className="mt-4 grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <Indicador rotulo="Ingressos vendidos" valor={m.ingressosVendidos} />
        <Indicador
          rotulo="Conversao"
          valor={`${m.conversao}%`}
          detalhe={`${m.reservasConfirmadas} de ${m.reservasCriadas} reservas`}
        />
        <Indicador
          rotulo="Eventos publicados"
          valor={publicados.data?.totalElements ?? '—'}
          detalhe="no catalogo"
        />
        <Indicador
          rotulo="Lugares disponiveis"
          valor={m.lugaresDisponiveis}
          // A imprecisao esta no proprio cartao, e nao so na documentacao: um numero menor que
          // a soma das casas publicadas parece defeito quando nao e.
          detalhe="so eventos ja com reserva"
        />
      </div>

      <section className="mt-10">
        <h2 className="text-lg font-semibold">Reservas por desfecho</h2>
        <p className="mt-1 text-sm text-suave">
          Expiracao e cancelamento sao contados separados: uma mede desistencia por inercia, a
          outra deliberada. Somadas, escondem a diferenca que diria se o prazo esta curto demais.
        </p>

        <dl className="mt-5 grid gap-4 sm:grid-cols-4">
          <Indicador rotulo="Criadas" valor={m.reservasCriadas} />
          <Indicador rotulo="Confirmadas" valor={m.reservasConfirmadas} cor="text-ok" />
          <Indicador rotulo="Expiradas" valor={m.reservasExpiradas} cor="text-alerta" />
          <Indicador rotulo="Canceladas" valor={m.reservasCanceladas} cor="text-suave" />
        </dl>
      </section>

      <p className="mt-10 text-xs text-suave">
        Estes numeros vem do banco. Latencia, disponibilidade e circuitos vivem em{' '}
        <Link to="/status" className="text-marca hover:underline">
          status
        </Link>
        , que le o Prometheus — sao perguntas diferentes.
      </p>
    </Secao>
  )
}

function Indicador({
  rotulo,
  valor,
  detalhe,
  cor = 'text-texto',
}: {
  rotulo: string
  valor: number | string
  detalhe?: string
  cor?: string
}) {
  return (
    <div className="rounded-cartao border border-borda bg-superficie p-5">
      <dt className="text-xs uppercase tracking-wide text-suave">{rotulo}</dt>
      <dd className={`numerico mt-2 text-2xl font-semibold ${cor}`}>{valor}</dd>
      {detalhe && <p className="mt-1 text-xs text-suave">{detalhe}</p>}
    </div>
  )
}
