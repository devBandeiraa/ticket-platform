import { requisitar } from './cliente'
import type { MetricasDeVenda } from './tipos'

/**
 * Agregados de venda do painel administrativo.
 *
 * <p>Vem de `COUNT` e `SUM` sobre o banco, e nao das metricas do Prometheus que a pagina de
 * status consulta. Sao perguntas diferentes: aquelas contam o que o processo viu desde que
 * subiu e zeram a cada reinicio; esta responde "quanto ja vendemos", que so o banco sabe.
 */
export function consultarMetricas(): Promise<MetricasDeVenda> {
  return requisitar('/admin/metrics')
}
