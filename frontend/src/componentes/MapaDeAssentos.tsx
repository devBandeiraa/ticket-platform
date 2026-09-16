import { useMemo, useRef, useState } from 'react'
import type { AssentoDoMapa, FaixaDeSetor } from '../api/tipos'
import { dinheiro } from './formato'

/**
 * Mapa de lugares da casa.
 *
 * Direcao visual: palco no topo e setores empilhados do mais proximo ao mais distante, com
 * corredor central. A metafora espacial existe para que a pessoa entenda ONDE vai sentar antes
 * de olhar o preco — num mapa puramente tabular, escolher lugar vira preencher formulario.
 *
 * ## Acessibilidade
 *
 * Um mapa grande tem milhares de botoes, e um `Tab` por assento tornaria a tela intransitavel
 * por teclado — passar da Plateia ao Balcao levaria mil e quinhentas tabulacoes. Por isso cada
 * setor e uma grade com *roving tabindex*: um unico assento participa da ordem de tabulacao, e
 * as setas movem o foco dentro da grade. E o padrao de widget de grade, e o mesmo que uma
 * planilha usa.
 *
 * Cor nunca e o unico diferenciador: cada estado tem forma e rotulo proprios, e o `aria-label`
 * de cada assento diz setor, fila, numero, preco e situacao por extenso.
 */

/** Quantos assentos por bloco antes do corredor central. */
const ASSENTOS_POR_BLOCO = 5

type Posicao = { fila: number; coluna: number }

function classesDoAssento(
  assento: AssentoDoMapa,
  selecionado: boolean,
  vip: boolean,
): string {
  if (selecionado) {
    // Selecionado e o unico estado preenchido com a cor de marca, e ganha anel para nao
    // depender so da cor.
    return 'bg-marca text-superficie ring-2 ring-marca ring-offset-2 ring-offset-papel'
  }
  if (assento.status === 'FREE') {
    // VIP livre se distingue por BORDA mais grossa e cor de alerta, e nao por um quarto tom de
    // cinza: a diferenca entre "livre" e "livre VIP" precisa sobreviver a escala de cinza, e
    // dois cinzas proximos nao sobrevivem.
    return vip
      ? 'border-2 border-alerta bg-superficie text-alerta hover:border-marca hover:text-marca'
      : 'border border-borda-forte bg-superficie/70 text-suave hover:border-marca hover:text-marca'
  }
  // Ocupado: risco diagonal, cursor de bloqueio e contraste baixo de proposito.
  return 'cursor-not-allowed border border-borda bg-borda/40 text-suave/40 assento-ocupado'
}

function descrever(assento: AssentoDoMapa, selecionado: boolean, vip: boolean): string {
  const situacao = selecionado
    ? 'selecionado por voce'
    : assento.status === 'FREE'
      ? 'disponivel'
      : assento.status === 'SOLD'
        ? 'vendido'
        : 'reservado por outra pessoa'

  // A faixa entra no texto, e nao so na borda: quem navega por leitor de tela nao ve a borda.
  const faixa = vip ? ', setor VIP' : ''

  return `${assento.sector}${faixa}, fila ${assento.row}, lugar ${assento.number}, ${dinheiro(assento.price)}, ${situacao}`
}

export function MapaDeAssentos({
  assentos,
  selecionados,
  aoAlternar,
  faixaPorSetor,
}: {
  assentos: AssentoDoMapa[]
  selecionados: Set<string>
  aoAlternar: (assento: AssentoDoMapa) => void
  /**
   * Faixa de cada setor, por NOME.
   *
   * <p>O mapa vem do booking-service, que conhece o estado de cada lugar e nao a oferta
   * comercial; a faixa vem do event-service, junto do detalhe do evento. A tela ja carrega os
   * dois e os casa aqui pelo nome do setor, que e a chave natural — ver a migration `V3`.
   *
   * <p>Opcional: sem o mapa, todo setor e tratado como comum. Um evento antigo, ou o detalhe
   * ainda carregando, nao pode deixar o mapa sem desenhar.
   */
  faixaPorSetor?: Map<string, FaixaDeSetor>
}) {
  // Agrupa por setor e por fila, preservando a ordem em que o servidor devolveu — ela ja vem
  // por setor, fila e numero.
  const setores = useMemo(() => {
    const porSetor = new Map<string, Map<string, AssentoDoMapa[]>>()

    for (const assento of assentos) {
      const filas = porSetor.get(assento.sector) ?? new Map<string, AssentoDoMapa[]>()
      const fila = filas.get(assento.row) ?? []
      fila.push(assento)
      filas.set(assento.row, fila)
      porSetor.set(assento.sector, filas)
    }

    return [...porSetor.entries()].map(([nome, filas]) => ({
      nome,
      preco: filas.values().next().value?.[0]?.price ?? 0,
      livres: [...filas.values()].flat().filter((a) => a.status === 'FREE').length,
      filas: [...filas.entries()].map(([rotulo, lugares]) => ({ rotulo, lugares })),
    }))
  }, [assentos])

  return (
    <div className="space-y-8">
      {/* O palco e decorativo: nao acrescenta informacao que o aria-label dos assentos ja nao
          carregue, e anuncia-lo so somaria ruido a quem usa leitor de tela. */}
      <div aria-hidden="true" className="relative flex justify-center">
        <div className="h-8 w-4/5 rounded-t-[100%] border-t-2 border-marca/40 bg-gradient-to-b from-marca/15 to-transparent" />
        <span className="absolute top-2 text-xs font-medium tracking-[0.3em] text-suave">
          PALCO
        </span>
      </div>

      {setores.map((setor) => (
        <SetorDoMapa
          key={setor.nome}
          setor={setor}
          selecionados={selecionados}
          aoAlternar={aoAlternar}
          vip={faixaPorSetor?.get(setor.nome) === 'VIP'}
        />
      ))}

      <Legenda temVip={[...(faixaPorSetor?.values() ?? [])].includes('VIP')} />
    </div>
  )
}

type SetorAgrupado = {
  nome: string
  preco: number
  livres: number
  filas: { rotulo: string; lugares: AssentoDoMapa[] }[]
}

function SetorDoMapa({
  setor,
  selecionados,
  aoAlternar,
  vip,
}: {
  setor: SetorAgrupado
  selecionados: Set<string>
  aoAlternar: (assento: AssentoDoMapa) => void
  vip: boolean
}) {
  // O assento que participa da ordem de tabulacao. As setas movem esta posicao, e o foco vai
  // junto — e o roving tabindex.
  const [ativo, setAtivo] = useState<Posicao>({ fila: 0, coluna: 0 })
  const grade = useRef<HTMLDivElement>(null)

  function mover(de: Posicao, teclaDe: number, teclaColuna: number) {
    const fila = Math.min(Math.max(de.fila + teclaDe, 0), setor.filas.length - 1)
    const colunas = setor.filas[fila].lugares.length
    const coluna = Math.min(Math.max(de.coluna + teclaColuna, 0), colunas - 1)

    setAtivo({ fila, coluna })

    // O foco precisa seguir a posicao ativa, ou a seta moveria o tabindex sem mover o cursor
    // de quem navega por teclado.
    grade.current
      ?.querySelector<HTMLButtonElement>(`[data-pos="${fila}-${coluna}"]`)
      ?.focus()
  }

  function aoTeclar(evento: React.KeyboardEvent, posicao: Posicao) {
    const movimentos: Record<string, [number, number]> = {
      ArrowUp: [-1, 0],
      ArrowDown: [1, 0],
      ArrowLeft: [0, -1],
      ArrowRight: [0, 1],
    }

    const movimento = movimentos[evento.key]
    if (!movimento) return

    evento.preventDefault()
    mover(posicao, movimento[0], movimento[1])
  }

  return (
    <section>
      <header className="mb-3 flex flex-wrap items-baseline justify-between gap-2 border-b border-borda/60 pb-2">
        <h3 className="text-sm font-medium">{setor.nome}</h3>
        <p className="text-xs text-suave">
          <span className="numerico text-marca">{dinheiro(setor.preco)}</span>
          {' · '}
          <span className="numerico">{setor.livres}</span> {setor.livres === 1 ? 'livre' : 'livres'}
        </p>
      </header>

      {/* Rolagem horizontal PROPRIA, e nao da pagina: a casa tem largura fixa em lugares, e
          encolher o assento ate caber deixaria o alvo de toque menor do que um dedo. Sem isto a
          pagina inteira ganharia barra horizontal, arrastando cabecalho e rodape junto para ver
          o assento 25.

          `tabIndex=0` porque um container rolavel precisa ser alcancavel pelo teclado. As setas
          do roving tabindex movem o foco DENTRO da grade, entao quem chega ao ultimo assento
          visivel e rolado ate ele pelo proprio navegador — mas quem so quer olhar o mapa, sem
          escolher, precisa de um jeito de rolar sem entrar na grade. */}
      <div
        tabIndex={0}
        role="region"
        aria-label={`Rolagem do setor ${setor.nome}`}
        className="overflow-x-auto pb-2"
      >
        <div
          ref={grade}
          role="grid"
          aria-label={`Mapa de assentos — ${setor.nome}`}
          className="inline-block min-w-full space-y-1.5"
        >
          {setor.filas.map((fila, indiceDaFila) => (
            <div key={fila.rotulo} role="row" className="flex items-center gap-1.5">
              <span
                aria-hidden="true"
                className="numerico w-6 shrink-0 text-right text-xs text-suave"
              >
                {fila.rotulo}
              </span>

              {fila.lugares.map((assento, indiceDaColuna) => {
                const selecionado = selecionados.has(assento.seatId)
                const ocupado = assento.status !== 'FREE'
                const ehAtivo =
                  ativo.fila === indiceDaFila && ativo.coluna === indiceDaColuna

                return (
                  <span key={assento.seatId} className="contents">
                    <button
                      type="button"
                      role="gridcell"
                      data-pos={`${indiceDaFila}-${indiceDaColuna}`}
                      // Roving tabindex: so um assento do setor entra na ordem de tabulacao.
                      tabIndex={ehAtivo ? 0 : -1}
                      disabled={ocupado}
                      aria-label={descrever(assento, selecionado, vip)}
                      aria-pressed={selecionado}
                      onFocus={() => setAtivo({ fila: indiceDaFila, coluna: indiceDaColuna })}
                      onKeyDown={(e) => aoTeclar(e, { fila: indiceDaFila, coluna: indiceDaColuna })}
                      onClick={() => aoAlternar(assento)}
                      className={`size-6 shrink-0 rounded text-[10px] transition-colors focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-marca ${classesDoAssento(assento, selecionado, vip)}`}
                    >
                      <span aria-hidden="true">{assento.number}</span>
                    </button>

                    {/* Corredor central. Puramente visual — a numeracao dos lugares nao muda. */}
                    {(indiceDaColuna + 1) % ASSENTOS_POR_BLOCO === 0
                      && indiceDaColuna < fila.lugares.length - 1 && (
                      <span aria-hidden="true" className="w-4 shrink-0" />
                    )}
                  </span>
                )
              })}
            </div>
          ))}
        </div>
      </div>
    </section>
  )
}

function Legenda({ temVip }: { temVip: boolean }) {
  const estados = [
    { rotulo: 'livre', classe: 'border border-borda-forte bg-superficie/70' },
    // So aparece quando a casa tem setor VIP. Uma legenda explicando um estado que nao existe
    // no mapa faz a pessoa procurar na tela o que ela nao vai achar.
    ...(temVip ? [{ rotulo: 'VIP livre', classe: 'border-2 border-alerta bg-superficie' }] : []),
    { rotulo: 'seu', classe: 'bg-marca' },
    { rotulo: 'ocupado', classe: 'border border-borda bg-borda/40 assento-ocupado' },
  ]

  return (
    <ul className="flex flex-wrap items-center gap-4 text-xs text-suave">
      {estados.map((estado) => (
        <li key={estado.rotulo} className="flex items-center gap-1.5">
          <span aria-hidden="true" className={`size-4 rounded ${estado.classe}`} />
          {estado.rotulo}
        </li>
      ))}
    </ul>
  )
}
