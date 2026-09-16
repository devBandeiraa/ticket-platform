import type { Disponibilidade } from '../api/tipos'

/**
 * Fracao da capacidade abaixo da qual o evento entra em "ultimos ingressos".
 *
 * <p>Proporcional, e nao um numero fixo de lugares: vinte restando numa casa de cinquenta e
 * quase esgotado, e vinte numa de tres mil e o comeco da venda. Um limiar absoluto acertaria um
 * dos dois casos e erraria o outro.
 */
const FRACAO_DE_ESCASSEZ = 0.1

type Estado = 'disponivel' | 'ultimos' | 'esgotado'

function estadoDe(disponibilidade: Disponibilidade): Estado {
  if (disponibilidade.available <= 0) {
    return 'esgotado'
  }
  if (disponibilidade.available <= disponibilidade.total * FRACAO_DE_ESCASSEZ) {
    return 'ultimos'
  }
  return 'disponivel'
}

const APARENCIA: Record<Estado, { rotulo: string; classe: string }> = {
  // Disponivel usa `suave` e nao verde: e o estado normal, e pintar o comum de verde faz o
  // olho parar de enxergar a cor quando ela de fato importa.
  disponivel: { rotulo: 'Disponivel', classe: 'border-borda-forte text-suave' },
  ultimos: { rotulo: 'Ultimos ingressos', classe: 'border-alerta text-alerta' },
  esgotado: { rotulo: 'Esgotado', classe: 'border-borda-forte text-suave line-through' },
}

/**
 * Selo de disponibilidade.
 *
 * <p>Devolve nulo enquanto o numero nao chegou, em vez de um esqueleto: o selo e pequeno e fica
 * numa linha com outros elementos, e um bloco cinza piscando ali chama mais atencao do que a
 * informacao que ele substitui.
 *
 * <p>"Esgotado" leva risco no texto alem da cor. As tres aparencias precisam se distinguir em
 * escala de cinza, pela mesma razao registrada em `.assento-ocupado`.
 */
export function SeloDeDisponibilidade({
  disponibilidade,
}: {
  disponibilidade: Disponibilidade | undefined
}) {
  if (!disponibilidade) {
    return null
  }

  const { rotulo, classe } = APARENCIA[estadoDe(disponibilidade)]

  return (
    <span
      className={`inline-flex items-center rounded-full border px-2 py-0.5 text-[0.7rem] font-medium ${classe}`}
    >
      {rotulo}
    </span>
  )
}
