import { requisitar } from './cliente'

/*
  Retrato da plataforma, servido pelo gateway em /api/status.

  O gateway consulta o Prometheus e traduz o resultado antes de responder. A tela nunca ve
  PromQL, nem o formato de serie temporal — se a origem das metricas mudar, nada aqui muda.
*/

export type EstadoDoCircuito = 'FECHADO' | 'MEIO_ABERTO' | 'ABERTO'

export interface ServicoNoStatus {
  nome: string
  /**
   * Vem da serie `up` do Prometheus: ele constatando que a coleta respondeu.
   *
   * Nao e o servico se declarando saudavel — e um terceiro verificando. A diferenca aparece
   * justamente no caso que importa: um processo travado continua achando que esta bem.
   */
  noAr: boolean
  /** Nulo quando nao houve trafego na janela. Nulo nao e zero: "sem dados" nao e "instantaneo". */
  latenciaMediaMs: number | null
  /** Nulo quando o servico esta fora — nao ha processo de quem perguntar. */
  uptimeSegundos: number | null
}

export interface CircuitoNoStatus {
  nome: string
  estado: EstadoDoCircuito
}

export interface StatusDaPlataforma {
  /** Quando o gateway perguntou, e nao quando o Prometheus mediu. */
  coletadoEm: string
  servicos: ServicoNoStatus[]
  circuitos: CircuitoNoStatus[]
}

export function consultarStatus(): Promise<StatusDaPlataforma> {
  return requisitar('/status')
}
