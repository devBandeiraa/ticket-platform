import { useState, type ReactNode } from 'react'
import { FaixaDeSecao, TituloDeSecao } from '../../componentes/Editorial'
import { IconeBusca, IconeCalendario, IconeLocal } from '../../componentes/Icones'
import { ROTULOS_DE_ATALHO, type Atalho } from '../../componentes/intervalos'
import type { FiltrosDaHome, Quando } from './catalogoDaHome'

/** Na ordem do seletor. `proximos` vem primeiro, com o rotulo que o design usa. */
const QUANDO: Atalho[] = ['proximos', 'hoje', 'amanha', 'semana', 'fim-de-semana']

/**
 * Um campo da barra de busca.
 *
 * <p>O rotulo e um `<label>` de verdade, visivel, e nao um `placeholder`: o design o desenha
 * acima do campo em versalete, e o placeholder desaparece quando alguem comeca a digitar —
 * justamente quando conferir o que se esta preenchendo importa mais.
 *
 * <p>O icone fica dentro do `<label>` e `aria-hidden`: ele repete o que o rotulo ja diz.
 */
function Campo({
  rotulo,
  icone,
  children,
}: {
  rotulo: string
  icone: ReactNode
  children: ReactNode
}) {
  return (
    <label className="group flex min-w-0 flex-1 items-center gap-3.5 px-5 py-4">
      <span className="shrink-0 text-suave transition-colors group-focus-within:text-marca-forte">
        {icone}
      </span>

      <span className="min-w-0 flex-1">
        <span className="rotulo block text-suave transition-colors group-focus-within:text-marca-forte">
          {rotulo}
        </span>
        {children}
      </span>
    </label>
  )
}

/*
  Os controles perdem a moldura propria.

  A borda e o fundo pertencem ao CARTAO inteiro, e nao a cada campo: o design desenha uma barra
  unica dividida por reguas, e tres caixas dentro de uma caixa dariam quatro contornos
  concorrentes. O foco continua visivel — ele acende o rotulo e o icone, e o anel padrao do
  `:focus-visible` segue valendo para quem chega por teclado.
*/
const CONTROLE =
  'mt-1 w-full border-0 bg-transparent p-0 text-sm text-texto outline-none placeholder:text-suave/70'

/**
 * Secao de busca da home.
 *
 * <h2>Por que a busca filtra aqui, e nao manda para a descoberta</h2>
 *
 * <p>Ate agora a busca da home empurrava para `/explorar`, com um motivo registrado: era la que
 * estavam os outros filtros, e um resultado solto no meio do hero deixaria quem buscou sem como
 * refinar. O design mudou o terreno — esta pagina ganhou abas de data, seletor de cena e
 * ordenacao —, e o motivo antigo passou a apontar para o outro lado: os filtros estao aqui.
 *
 * <p>Ha um motivo mais concreto. O campo "onde" nao tem equivalente no servidor: a busca de
 * `/explorar` compara o NOME do evento e nada mais — ver `EventSpecifications.comNomeContendo`.
 * Mandar a cidade para la seria mandar um filtro que ninguem aplica, e o resultado voltaria com
 * eventos de outras cidades sob um cabecalho dizendo o contrario.
 *
 * <p>O limite da escolha e a janela: esta busca procura entre os eventos que a home carregou. E
 * por isso que a lista vazia oferece a busca no catalogo completo — ver `Lista`.
 *
 * <h2>Rascunho ate o envio</h2>
 *
 * <p>Os tres campos guardam estado proprio e so o entregam no `submit`. Aplicar a cada tecla
 * faria a lista se reorganizar embaixo de quem ainda esta escrevendo, e o botao "buscar eventos"
 * que o design desenha nao teria o que fazer.
 */
export function Busca({
  filtros,
  cidades,
  aoBuscar,
}: {
  filtros: FiltrosDaHome
  /** Derivadas do catalogo em `cidadesDe`. */
  cidades: string[]
  aoBuscar: (mudanca: Partial<FiltrosDaHome>) => void
}) {
  const [busca, setBusca] = useState(filtros.busca)
  const [cidade, setCidade] = useState(filtros.cidade)
  const [quando, setQuando] = useState<Quando>(filtros.quando)

  function enviar(evento: React.FormEvent) {
    evento.preventDefault()
    aoBuscar({ busca, cidade, quando })
  }

  return (
    <FaixaDeSecao className="pt-14 sm:pt-16">
      <TituloDeSecao className="text-2xl sm:text-3xl">
        Qual vai ser o seu próximo programa?
      </TituloDeSecao>

      {/* `divide-*` desenha as reguas ENTRE os campos sem uma borda solta no primeiro e no
          ultimo — e troca de eixo junto com o layout, que empilha no telefone. */}
      <form
        onSubmit={enviar}
        className="mt-8 flex flex-col divide-y divide-borda rounded-cartao border border-borda bg-superficie shadow-sm lg:flex-row lg:items-stretch lg:divide-x lg:divide-y-0"
      >
        <Campo rotulo="O que você quer ver?" icone={<IconeBusca tamanho={20} />}>
          <input
            type="search"
            value={busca}
            onChange={(e) => setBusca(e.target.value)}
            placeholder="Evento, artista ou palavra-chave"
            className={CONTROLE}
          />
        </Campo>

        <Campo rotulo="Onde?" icone={<IconeLocal tamanho={20} />}>
          {/* As cidades vem do proprio catalogo, e nao de uma lista escrita a mao: uma lista
              fixa ofereceria cidade sem evento, e deixaria de fora a cidade do evento
              cadastrado ontem. */}
          <select value={cidade} onChange={(e) => setCidade(e.target.value)} className={CONTROLE}>
            <option value="">Todas as cidades</option>
            {cidades.map((nome) => (
              <option key={nome} value={nome}>
                {nome}
              </option>
            ))}
          </select>
        </Campo>

        <Campo rotulo="Quando?" icone={<IconeCalendario tamanho={20} />}>
          <select
            value={quando}
            onChange={(e) => setQuando(e.target.value as Quando)}
            className={CONTROLE}
          >
            {QUANDO.map((opcao) => (
              <option key={opcao} value={opcao}>
                {opcao === 'proximos' ? 'Todas as datas' : ROTULOS_DE_ATALHO[opcao]}
              </option>
            ))}
          </select>
        </Campo>

        <div className="flex items-center p-3">
          <button className="inline-flex w-full items-center justify-center gap-2.5 rounded-cartao bg-marca px-6 py-3.5 text-sm font-semibold text-white transition-colors hover:bg-marca-forte active:scale-[0.98]">
            Buscar eventos
            <IconeBusca tamanho={16} />
          </button>
        </div>
      </form>
    </FaixaDeSecao>
  )
}
