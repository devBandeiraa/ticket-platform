import type { AssentoDoMapa, Setor } from '../api/tipos'
import { dinheiro } from './formato'

/**
 * Escolha do setor, em cartoes com forma de ingresso.
 *
 * <h2>Por que isto nao substitui o mapa</h2>
 *
 * <p>O brief pedia um seletor com contador de quantidade, no lugar do formulario tradicional. A
 * plataforma, porem, vende LUGAR e nao quantidade — foi a troca da Fase 17, e o comentario em
 * `reservar` registra o motivo: havendo mapa, deixar o servidor escolher esconde do usuario a
 * unica decisao que ele veio tomar.
 *
 * <p>A conciliacao: o contador daqui e um ATALHO. Pedir tres lugares na Plateia pre-seleciona os
 * tres livres mais baratos daquele setor no mapa, e o mapa continua sendo onde a escolha se
 * confirma ou se ajusta. Quem quer decidir rapido usa o contador; quem se importa com a fila
 * ajusta embaixo. Nenhum dos dois perde.
 *
 * <p>O calculo e no cliente porque o mapa ja esta carregado — nao ha endpoint novo nem uma
 * segunda fonte de verdade sobre o que esta livre.
 */

function LinhaDeBeneficio({ texto }: { texto: string }) {
  return (
    <li className="flex items-start gap-2 text-sm text-suave">
      {/* Marcador decorativo: o texto do beneficio ja e a informacao, e anunciar um simbolo
          antes de cada item so somaria ruido a quem usa leitor de tela. */}
      <span aria-hidden="true" className="mt-1.5 size-1 shrink-0 rounded-full bg-marca" />
      {texto}
    </li>
  )
}

function BotaoDeContagem({
  rotulo,
  simbolo,
  desabilitado,
  aoClicar,
}: {
  rotulo: string
  simbolo: string
  desabilitado: boolean
  aoClicar: () => void
}) {
  return (
    <button
      type="button"
      // O simbolo e desenho; o nome acessivel diz o que o botao faz e em qual setor. Sem ele,
      // um leitor de tela anunciaria "menos, botao" seis vezes na mesma pagina.
      aria-label={rotulo}
      disabled={desabilitado}
      onClick={aoClicar}
      className="flex size-8 items-center justify-center rounded-cartao border border-borda-forte text-sm transition-colors hover:border-marca hover:text-marca disabled:cursor-not-allowed disabled:border-borda disabled:text-suave/50 disabled:hover:border-borda disabled:hover:text-suave/50"
    >
      <span aria-hidden="true">{simbolo}</span>
    </button>
  )
}

export function SeletorDeSetor({
  setores,
  assentos,
  selecionados,
  aoDefinirQuantidade,
  maximoRestante,
}: {
  setores: Setor[]
  assentos: AssentoDoMapa[]
  /** Ids ja escolhidos, para o cartao contar quantos deste setor estao na escolha. */
  selecionados: Set<string>
  /**
   * Pedido de quantidade para um setor.
   *
   * <p>Quem traduz a quantidade em lugares e a PAGINA, que e dona da intencao de compra. Fazer
   * isso aqui exigiria devolver a lista junto, e dois donos da mesma decisao acabam
   * divergindo quando o mapa chega atualizado no meio do caminho.
   */
  aoDefinirQuantidade: (setor: string, quantidade: number) => void
  /** Quantos lugares ainda cabem na reserva, contando os ja escolhidos em outros setores. */
  maximoRestante: number
}) {
  return (
    <ul className="grid gap-4 sm:grid-cols-2">
      {setores.map((setor) => {
        const livres = assentos.filter((a) => a.sector === setor.name && a.status === 'FREE')
        const nesteSetor = assentos.filter(
          (a) => a.sector === setor.name && selecionados.has(a.seatId),
        ).length

        const esgotado = livres.length === 0
        const vip = setor.tier === 'VIP'

        return (
          <li
            key={setor.id}
            className={`relative overflow-hidden rounded-cartao border bg-superficie p-5 ${
              vip ? 'border-alerta' : 'border-borda'
            }`}
          >
            {/* Picote vertical na lateral, como a margem de um ingresso destacavel. Decorativo,
                e a unica coisa que amarra este cartao a identidade da peca. */}
            <span
              aria-hidden="true"
              className="picote-vertical absolute inset-y-4 left-0 w-0.5 text-borda"
            />

            <div className="flex items-start justify-between gap-3">
              <div className="min-w-0">
                <h3 className="font-medium">{setor.name}</h3>
                {vip && (
                  <span className="mt-1 inline-flex rounded-full border border-alerta px-2 py-0.5 text-[0.65rem] font-semibold uppercase tracking-wider text-alerta">
                    VIP
                  </span>
                )}
              </div>

              <div className="shrink-0 text-right">
                <span className="numerico block text-lg font-semibold text-marca">
                  {dinheiro(setor.price)}
                </span>
                <span className="numerico block text-xs text-suave">
                  {esgotado ? 'esgotado' : `${livres.length} livres`}
                </span>
              </div>
            </div>

            {setor.description && <p className="mt-3 text-sm text-suave">{setor.description}</p>}

            {setor.benefits.length > 0 && (
              <ul className="mt-3 space-y-1.5">
                {setor.benefits.map((beneficio) => (
                  <LinhaDeBeneficio key={beneficio} texto={beneficio} />
                ))}
              </ul>
            )}

            <div className="mt-4 flex items-center justify-between gap-3 border-t border-borda pt-4">
              <span className="text-sm text-suave">
                {esgotado ? 'esgotado' : 'quantos lugares?'}
              </span>

              <div className="flex items-center gap-1">
                <BotaoDeContagem
                  rotulo={`Remover um lugar de ${setor.name}`}
                  simbolo="−"
                  desabilitado={nesteSetor === 0}
                  aoClicar={() => aoDefinirQuantidade(setor.name, nesteSetor - 1)}
                />

                {/* aria-live para o leitor de tela anunciar a mudanca: o numero muda sem que o
                    foco saia do botao, entao nada seria anunciado sem isto. */}
                <span
                  aria-live="polite"
                  className="numerico w-8 text-center text-sm font-medium tabular-nums"
                >
                  {nesteSetor}
                </span>

                <BotaoDeContagem
                  rotulo={`Adicionar um lugar de ${setor.name}`}
                  simbolo="+"
                  desabilitado={esgotado || nesteSetor >= livres.length || maximoRestante <= 0}
                  aoClicar={() => aoDefinirQuantidade(setor.name, nesteSetor + 1)}
                />
              </div>
            </div>
          </li>
        )
      })}
    </ul>
  )
}
