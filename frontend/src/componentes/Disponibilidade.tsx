import type { Disponibilidade } from '../api/tipos'

/**
 * Fracao da capacidade abaixo da qual o evento entra em "ultimos ingressos".
 *
 * <p>Proporcional, e nao um numero fixo de lugares: vinte restando numa casa de cinquenta e
 * quase esgotado, e vinte numa de tres mil e o comeco da venda. Um limiar absoluto acertaria um
 * dos dois casos e erraria o outro.
 */
const FRACAO_DE_ESCASSEZ = 0.1

/**
 * Fracao abaixo da qual o texto passa a mostrar o TOTAL junto do que resta.
 *
 * <p>Separada do limiar de escassez de proposito. Sao duas perguntas diferentes: "isto merece cor
 * de alerta?" e "este numero se entende sozinho?". O design responde a segunda com "12 de 50
 * lugares disponiveis" num cartao e "28 lugares disponiveis" no vizinho — e 12 de 50 e um quarto
 * da casa, longe dos 10% que acendem o alerta.
 *
 * <p>Meia casa e o ponto em que o denominador comeca a informar: com mais da metade livre, o
 * total so rouba a atencao de que ha lugar de sobra.
 */
const FRACAO_COM_TOTAL = 0.5

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
  disponivel: { rotulo: 'Disponível', classe: 'border-borda-forte text-suave' },
  ultimos: { rotulo: 'Últimos ingressos', classe: 'border-alerta text-alerta' },
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

/**
 * A mesma disponibilidade em palavras: "12 de 50 lugares disponiveis".
 *
 * <h2>Por que o total aparece so na escassez</h2>
 *
 * <p>O design escreve "12 de 50 lugares disponiveis" num cartao e "28 lugares disponiveis" no
 * vizinho, e a diferenca nao e descuido: e o denominador que transforma um numero em aviso.
 * "12 disponiveis" nao diz nada — pode ser uma casa de quinze ou de mil. "12 de 50" diz que a
 * casa esta quase cheia.
 *
 * <p>Na outra ponta, "820 de 1200 lugares disponiveis" e pior do que "820 lugares disponiveis":
 * o total rouba a atencao do que importa, que e haver lugar de sobra. Entao o denominador entra
 * quando muda a leitura — da metade da casa para baixo, por `FRACAO_COM_TOTAL`. Repare que esse
 * limiar NAO e o do alerta: o cartao pode dizer "12 de 50" sem que o numero esteja em laranja,
 * que e exatamente o que o design desenha.
 *
 * <p>"Lugares", e nunca "ingressos": toda casa deste catalogo tem lugar marcado, e a escolha
 * esta registrada no seed do event-service. Chamar de ingresso o que e assento numerado
 * descreveria um evento de pista, que o dominio nao modela.
 */
export function TextoDeDisponibilidade({
  disponibilidade,
  className = '',
}: {
  disponibilidade: Disponibilidade | undefined
  className?: string
}) {
  if (!disponibilidade) {
    return null
  }

  const estado = estadoDe(disponibilidade)

  const comTotal = disponibilidade.available < disponibilidade.total * FRACAO_COM_TOTAL

  const texto =
    estado === 'esgotado'
      ? 'Esgotado'
      : comTotal
        ? `${disponibilidade.available} de ${disponibilidade.total} lugares disponíveis`
        : `${disponibilidade.available} lugares disponíveis`

  // `alerta` so na escassez de verdade. O estado normal fica em `suave` pelo mesmo motivo do
  // selo: cor de aviso usada no comum deixa de ser lida como aviso.
  const cor = estado === 'ultimos' ? 'text-alerta' : 'text-suave'

  return <p className={`numerico text-xs ${cor} ${className}`}>{texto}</p>
}
