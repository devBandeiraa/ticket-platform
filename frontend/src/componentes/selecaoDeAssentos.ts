import type { AssentoDoMapa } from '../api/tipos'

/**
 * Reconcilia o que o usuario escolheu com o que o mapa diz agora.
 *
 * Funcao pura, separada da tela, porque e aqui que mora a decisao que falha em silencio: um
 * lugar vendido enquanto a pessoa decide precisa sair da selecao sozinho. Se isso nao
 * acontecer, a tela oferece um assento que o servidor vai recusar, e o unico sintoma e um
 * `409` no momento mais caro — depois de a pessoa ter escolhido tudo.
 *
 * A `intencao` e o que foi clicado, e nao o que esta reservavel. Guardar o objeto do assento
 * deixaria a tela com uma copia que envelhece; guardando so os ids, o que vale e sempre
 * recalculado do mapa recem-chegado.
 *
 * @param assentos o mapa como o servidor acabou de devolve-lo
 * @param intencao ids dos lugares que o usuario clicou
 */
export function reconciliar(assentos: AssentoDoMapa[], intencao: Set<string>) {
  const escolhidos = assentos.filter((assento) => intencao.has(assento.seatId))

  return {
    /** Clicados e ainda livres: o que de fato sera enviado ao servidor. */
    selecionados: escolhidos.filter((assento) => assento.status === 'FREE'),
    /** Clicados que sairam no meio do caminho. Alimentam o aviso, e nada mais. */
    perdidos: escolhidos.filter((assento) => assento.status !== 'FREE'),
  }
}

/** Soma do que sera cobrado. Cada lugar tem o seu preco — nao ha preco unitario da reserva. */
export function somar(assentos: AssentoDoMapa[]): number {
  return assentos.reduce((soma, assento) => soma + assento.price, 0)
}

/**
 * Traduz "quero N lugares na Plateia" numa nova intencao de compra.
 *
 * <p>E o atalho do seletor de setores. Mantem intacto o que foi escolhido nos OUTROS setores e
 * reescreve apenas o setor pedido, com os livres mais baratos. Mexer em tudo faria o contador
 * de um setor apagar a escolha feita a mao em outro.
 *
 * <p>Ao aumentar, PRESERVA os lugares que a pessoa ja tinha escolhido neste setor e completa
 * com os mais baratos que faltam. Recomecar do zero moveria um lugar escolhido a dedo no mapa
 * so porque alguem clicou no `+` — e o mapa e justamente onde a escolha fina acontece.
 *
 * <p>Ao diminuir, remove primeiro os mais CAROS: quem esta tirando lugares quer gastar menos, e
 * tirar o mais barato seria o contrario do pedido.
 *
 * @param assentos o mapa como o servidor acabou de devolve-lo
 * @param intencao ids ja escolhidos, em todos os setores
 * @param setor    nome do setor que o contador mexeu
 * @param quantos  quantidade pedida para esse setor
 * @param teto     maximo de lugares na reserva inteira
 */
export function definirQuantidadeNoSetor(
  assentos: AssentoDoMapa[],
  intencao: Set<string>,
  setor: string,
  quantos: number,
  teto: number,
): Set<string> {
  const deOutrosSetores = assentos
    .filter((a) => intencao.has(a.seatId) && a.sector !== setor)
    .map((a) => a.seatId)

  const cabem = Math.max(0, teto - deOutrosSetores.length)
  const alvo = Math.max(0, Math.min(quantos, cabem))

  const jaEscolhidosAqui = assentos
    .filter((a) => a.sector === setor && intencao.has(a.seatId) && a.status === 'FREE')
    .sort((a, b) => b.price - a.price || b.row.localeCompare(a.row) || b.number - a.number)

  // Encolhendo: os mais caros saem primeiro, entao basta cortar do inicio da lista ordenada
  // por preco decrescente.
  if (alvo <= jaEscolhidosAqui.length) {
    const mantidos = jaEscolhidosAqui.slice(jaEscolhidosAqui.length - alvo)
    return new Set([...deOutrosSetores, ...mantidos.map((a) => a.seatId)])
  }

  const jaTenho = new Set(jaEscolhidosAqui.map((a) => a.seatId))
  const completar = assentos
    .filter((a) => a.sector === setor && a.status === 'FREE' && !jaTenho.has(a.seatId))
    .sort((a, b) => a.price - b.price || a.row.localeCompare(b.row) || a.number - b.number)
    .slice(0, alvo - jaEscolhidosAqui.length)

  return new Set([
    ...deOutrosSetores,
    ...jaEscolhidosAqui.map((a) => a.seatId),
    ...completar.map((a) => a.seatId),
  ])
}
