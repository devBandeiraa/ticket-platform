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
