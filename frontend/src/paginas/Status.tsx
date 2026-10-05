import { useQuery } from '@tanstack/react-query'
import { consultarStatus } from '../api/status'
import type { EstadoDoCircuito, ServicoNoStatus } from '../api/status'
import { Carregando, Erro } from '../componentes/Estados'
import { FaixaNoite } from '../componentes/Layout'
import { tempoNoAr } from '../componentes/formato'
import { Cartao, Secao } from '../componentes/Ui'

/** Intervalo entre coletas. */
const INTERVALO_MS = 5_000

/**
 * O estado da plataforma em tempo quase real.
 *
 * <p>Os numeros vem do Prometheus, mas a tela nao fala com ele: quem consulta e o gateway, que
 * traduz e devolve em /api/status. Publicar o Prometheus para o navegador entregaria junto o nome
 * de cada servico, cada endpoint e cada metrica interna a quem abrisse o endereco.
 *
 * <p>Nao substitui o Grafana — e nem tenta. Aqui cabe a pergunta "esta tudo no ar agora?"; a
 * pergunta "por que ficou lento as 14h" precisa de serie temporal, e essa mora la.
 */
export function Status() {
  const status = useQuery({
    queryKey: ['status'],
    queryFn: consultarStatus,
    refetchInterval: INTERVALO_MS,
    // Zero porque o padrao do app e 30s, pensado para catalogo de eventos. Aqui um dado de trinta
    // segundos atras nao serve: a pagina existe para mostrar o agora.
    staleTime: 0,
    // Manter o retrato anterior enquanto o proximo nao chega. Sem isto a tela piscaria em
    // esqueleto a cada cinco segundos, e o movimento constante esconderia a mudanca de verdade.
    placeholderData: (anterior) => anterior,
    // Um 503 aqui significa que o Prometheus nao respondeu, e insistir duas vezes so atrasa a
    // mensagem. A proxima coleta acontece em cinco segundos de qualquer forma.
    retry: false,
  })

  /*
    Carregando e erro tambem vivem na faixa escura.

    Sem isto, a pagina trocaria de familia de cor conforme o backend responde ou nao: o painel
    apareceria escuro quando tudo esta no ar e claro justamente quando algo caiu — que e o
    momento em que a pessoa mais precisa reconhecer a tela onde esta.
  */
  if (status.isPending) {
    return (
      <FaixaNoite className="min-h-screen">
        <Secao>
          <Carregando texto="Consultando a plataforma..." />
        </Secao>
      </FaixaNoite>
    )
  }
  if (status.error) {
    return (
      <FaixaNoite className="min-h-screen">
        <Secao>
          <ErroDeColeta erro={status.error} />
        </Secao>
      </FaixaNoite>
    )
  }

  const { servicos, circuitos, coletadoEm } = status.data
  const fora = servicos.filter((servico) => !servico.noAr)

  return (
    /* Mesma razao da demo de concorrencia: painel de estado se le melhor no escuro, e as duas
       sao as telas tecnicas que a Fase 24 deixou de fora do papel. Sem ceu aqui — a pagina ja
       tem pontos piscando de verdade, e estrela ao fundo competiria com eles. */
    <FaixaNoite className="min-h-screen">
      <Secao className="space-y-8">
        <header>
          <h1 className="text-2xl font-semibold">Status da plataforma</h1>
          <p className="mt-2 max-w-2xl text-sm text-suave">
            Os números vêm do Prometheus, que coleta cada serviço a cada dez segundos. Esta
            página pergunta ao gateway a cada cinco.
          </p>
        </header>

        <Resumo total={servicos.length} fora={fora.length} coletadoEm={coletadoEm} />

        <section>
          <h2 className="mb-3 text-sm tracking-wide text-suave uppercase">Serviços</h2>
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
            {servicos.map((servico) => (
              <CartaoDeServico key={servico.nome} servico={servico} />
            ))}
          </div>
        </section>

        <section>
          <h2 className="mb-3 text-sm tracking-wide text-suave uppercase">Circuit breakers</h2>
          {circuitos.length === 0 ? (
            <p className="text-sm text-suave">
              Nenhum circuito registrado ainda. Eles aparecem depois da primeira chamada entre
              serviços.
            </p>
          ) : (
            <div className="grid gap-4 sm:grid-cols-2">
              {circuitos.map((circuito) => (
                <CartaoDeCircuito
                  key={circuito.nome}
                  nome={circuito.nome}
                  estado={circuito.estado}
                />
              ))}
            </div>
          )}
        </section>
      </Secao>
    </FaixaNoite>
  )
}

function Resumo({
  total,
  fora,
  coletadoEm,
}: {
  total: number
  fora: number
  coletadoEm: string
}) {
  const tudoNoAr = fora === 0

  return (
    <Cartao
      className={`flex flex-wrap items-center gap-x-4 gap-y-2 ${
        tudoNoAr ? 'border-ok/40' : 'border-erro/40'
      }`}
    >
      <span
        aria-hidden="true"
        className={`size-3 rounded-full ${tudoNoAr ? 'bg-ok' : 'bg-erro'}`}
      />
      <span className="font-medium">
        {tudoNoAr
          ? `Todos os ${total} serviços no ar`
          : `${fora} de ${total} serviços fora do ar`}
      </span>
      {/* O horario da coleta e o que mostra que a pagina continua viva quando nenhum numero muda.
          Sem ele, uma plataforma estavel e uma pagina congelada sao indistinguiveis. */}
      <span className="numerico ml-auto text-xs text-suave">
        coletado às {new Date(coletadoEm).toLocaleTimeString('pt-BR')}
      </span>
    </Cartao>
  )
}

function CartaoDeServico({ servico }: { servico: ServicoNoStatus }) {
  return (
    <Cartao className={servico.noAr ? '' : 'border-erro/40 bg-erro/5'}>
      <div className="flex items-center justify-between gap-2">
        <h3 className="truncate font-medium">{servico.nome}</h3>
        <SeloNoAr noAr={servico.noAr} />
      </div>

      <dl className="mt-4 grid grid-cols-2 gap-3 text-sm">
        <div>
          <dt className="text-xs text-suave">Latência média</dt>
          <dd className="numerico mt-0.5 tabular-nums">
            {/* Tres coisas diferentes, tres exibicoes diferentes: um numero, "sem trafego" e o
                traco de quem esta fora. Colapsar em "0 ms" seria a unica saida errada. */}
            {servico.latenciaMediaMs === null ? (
              <span className="text-suave">sem tráfego</span>
            ) : (
              `${servico.latenciaMediaMs.toFixed(1)} ms`
            )}
          </dd>
        </div>
        <div>
          <dt className="text-xs text-suave">No ar há</dt>
          <dd className="numerico mt-0.5 tabular-nums">
            {servico.uptimeSegundos === null ? (
              <span className="text-suave">—</span>
            ) : (
              tempoNoAr(servico.uptimeSegundos)
            )}
          </dd>
        </div>
      </dl>
    </Cartao>
  )
}

function SeloNoAr({ noAr }: { noAr: boolean }) {
  return (
    <span
      className={`inline-flex shrink-0 items-center gap-1.5 rounded-full px-2.5 py-1 text-xs font-medium ring-1 ring-inset ${
        noAr ? 'bg-ok/15 text-ok ring-ok/25' : 'bg-erro/15 text-erro ring-erro/25'
      }`}
    >
      {/* O ponto reforca, mas quem carrega o significado e o texto: cor sozinha exclui quem nao
          distingue verde de vermelho. */}
      <span className="size-1.5 rounded-full bg-current" />
      {noAr ? 'no ar' : 'fora'}
    </span>
  )
}

const APARENCIA_DO_CIRCUITO: Record<
  EstadoDoCircuito,
  { rotulo: string; explicacao: string; classe: string; ponto: string }
> = {
  FECHADO: {
    rotulo: 'fechado',
    explicacao: 'As chamadas estão passando normalmente.',
    classe: 'border-ok/40',
    ponto: 'bg-ok',
  },
  MEIO_ABERTO: {
    rotulo: 'meio aberto',
    explicacao: 'Deixando passar algumas chamadas para descobrir se o serviço voltou.',
    classe: 'border-alerta/40 bg-alerta/5',
    ponto: 'bg-alerta',
  },
  ABERTO: {
    rotulo: 'aberto',
    explicacao:
      'As chamadas estão sendo recusadas sem sair pelo fio, para não insistir contra um serviço que já demonstrou estar fora.',
    classe: 'border-erro/40 bg-erro/5',
    ponto: 'bg-erro',
  },
}

function CartaoDeCircuito({ nome, estado }: { nome: string; estado: EstadoDoCircuito }) {
  const aparencia = APARENCIA_DO_CIRCUITO[estado]

  return (
    <Cartao className={aparencia.classe}>
      <div className="flex items-center gap-2.5">
        <span
          aria-hidden="true"
          className={`size-3 shrink-0 rounded-full ${aparencia.ponto} ${
            estado === 'FECHADO' ? '' : 'animate-pulse'
          }`}
        />
        <h3 className="font-medium">Chamadas ao {nome}</h3>
        <span className="numerico ml-auto text-xs tracking-wide uppercase">
          {aparencia.rotulo}
        </span>
      </div>
      <p className="mt-3 text-sm text-suave">{aparencia.explicacao}</p>
    </Cartao>
  )
}

function ErroDeColeta({ erro }: { erro: unknown }) {
  return (
    <div className="space-y-4">
      <h1 className="text-2xl font-semibold">Status da plataforma</h1>
      <Erro erro={erro} />
      {/* A distincao vale ser dita em voz alta: a pagina perdeu a fonte de metricas, o que nao
          significa que a plataforma caiu. Sem isto, quem le conclui o pior. */}
      <p className="text-sm text-suave">
        Isto significa que o painel não conseguiu ler as métricas — não que os serviços estejam
        fora. Confira se o Prometheus subiu junto com o resto.
      </p>
    </div>
  )
}
