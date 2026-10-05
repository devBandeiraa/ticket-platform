import type { CategoriaDoEvento } from '../../api/tipos'
import { CartaoDeEvento } from '../../componentes/CartaoDeEvento'
import { Erro, EsqueletoDeCartoes } from '../../componentes/Estados'
import { CabecalhoDeSecao, FaixaDeSecao, LinkDeSaida } from '../../componentes/Editorial'
import { IconeChevron, IconeConfere } from '../../componentes/Icones'
import { CATEGORIAS, ROTULOS_DE_CATEGORIA } from '../../componentes/categorias'
import { ORDENACOES, ROTULOS_DE_ORDENACAO, type Ordenacao } from '../../componentes/catalogo'
import type { CatalogoDaHome, Quando } from './catalogoDaHome'

/**
 * As quatro abas de data do design.
 *
 * <p>Um subconjunto dos atalhos de `intervalos.ts`, com os rotulos do design: "todos os eventos"
 * e `proximos`, e "escolher data" abre o calendario em vez de aplicar um intervalo pronto. As
 * opcoes que sobraram — hoje, amanha — continuam no seletor "quando" da secao de busca, onde
 * cabem sem alongar a fileira de abas.
 */
const ABAS: { valor: Quando; rotulo: string }[] = [
  { valor: 'proximos', rotulo: 'Todos os eventos' },
  { valor: 'semana', rotulo: 'Esta semana' },
  { valor: 'fim-de-semana', rotulo: 'Este fim de semana' },
  { valor: 'data', rotulo: 'Escolher data' },
]

function Aba({
  ativa,
  children,
  onClick,
}: {
  ativa: boolean
  children: React.ReactNode
  onClick: () => void
}) {
  return (
    <button
      type="button"
      aria-pressed={ativa}
      onClick={onClick}
      // Ativa em NOITE, e nao em laranja: o design reserva o vermelhao para o que leva a algum
      // lugar — botao, seta, preco. A aba so diz onde voce esta.
      className={`rounded-full border px-4 py-2 text-sm transition-colors ${
        ativa
          ? 'border-noite bg-noite font-medium text-noite-texto'
          : 'border-borda text-suave hover:border-borda-forte hover:text-texto'
      }`}
    >
      {children}
    </button>
  )
}

/*
  Os dois seletores viram chip.

  No design, "Categoria: todas" fica na MESMA fileira das abas de data e com a mesma forma, e
  "Ordenar por" vai para a ponta direita. A seta e desenhada por nos — `appearance-none` tira a
  do sistema, que muda de desenho em cada navegador e nao acompanharia a forma arredondada.
*/
const SELETOR =
  'cursor-pointer appearance-none rounded-full border border-borda bg-superficie py-2 pl-4 pr-9 text-sm outline-none transition-colors hover:border-borda-forte focus:border-marca'

/** "6 eventos encontrados", no singular quando for um. */
function encontrados(quantos: number): string {
  return quantos === 1 ? '1 evento encontrado' : `${quantos} eventos encontrados`
}

/**
 * Lista de eventos da home.
 *
 * <p>A secao que o design chama de "escolha o seu proximo ingresso": as abas de data, o seletor
 * de cena, a ordenacao, a grade e o rodape que fecha a contagem.
 *
 * <h2>O rodape da lista diz uma de duas coisas</h2>
 *
 * <p>"Voce viu todos os N eventos" so aparece quando a janela da home cobre o catalogo inteiro —
 * `janelaCobreTudo`, comparado com o `totalElements` do servidor. Fora disso, a frase seria
 * falsa, e no lugar dela vai o caminho para o catalogo completo. O design mostra a primeira
 * versao porque, no desenho dele, seis eventos eram tudo o que existia.
 */
export function Lista({ catalogo, cidades }: { catalogo: CatalogoDaHome; cidades: string[] }) {
  const { lista, total, janelaCobreTudo, disponibilidades, filtros, ajustar, limpar, temFiltro } =
    catalogo

  // "Uma selecao de encontros no Rio" e a frase do design, e ela so e verdade com uma cidade no
  // catalogo. Com duas, nomear uma delas excluiria a outra; contar as duas continua verdadeiro
  // nos dois casos.
  const ondeEstamos =
    cidades.length === 1
      ? `em ${cidades[0]}`
      : cidades.length > 1
        ? `em ${cidades.length} cidades`
        : 'pela cidade'

  return (
    <FaixaDeSecao id="eventos">
      <CabecalhoDeSecao
        sobretitulo="Escolha o seu próximo ingresso"
        titulo="Sua próxima boa história começa aqui."
        apoio={`Uma seleção de encontros ${ondeEstamos}. Escolha a data, encontre sua cena e chegue mais perto.`}
      />

      <div
        role="group"
        aria-label="Filtrar a programação"
        className="mt-10 flex flex-wrap items-center gap-2"
      >
        {ABAS.map((aba) => (
          <Aba
            key={aba.valor}
            ativa={filtros.quando === aba.valor}
            onClick={() => ajustar({ quando: aba.valor })}
          >
            {aba.rotulo}
          </Aba>
        ))}

        {/* A categoria entra na MESMA fileira das abas, como no design. */}
        <label className="relative inline-flex items-center">
          <span className="sr-only">Categoria</span>
          <select
            value={filtros.categoria}
            onChange={(e) => ajustar({ categoria: e.target.value as CategoriaDoEvento | '' })}
            className={SELETOR}
          >
            <option value="">Categoria: todas</option>
            {CATEGORIAS.map((opcao) => (
              <option key={opcao} value={opcao}>
                {ROTULOS_DE_CATEGORIA[opcao]}
              </option>
            ))}
          </select>
          <IconeChevron tamanho={14} className="pointer-events-none absolute right-3 text-suave" />
        </label>

        <label className="relative ml-auto hidden items-center gap-2 sm:inline-flex">
          <span className="text-sm text-suave">Ordenar por:</span>
          <select
            value={filtros.ordenacao}
            onChange={(e) => ajustar({ ordenacao: e.target.value as Ordenacao })}
            className={`${SELETOR} border-transparent bg-transparent font-medium`}
          >
            {ORDENACOES.map((opcao) => (
              <option key={opcao} value={opcao}>
                {ROTULOS_DE_ORDENACAO[opcao]}
              </option>
            ))}
          </select>
          <IconeChevron tamanho={14} className="pointer-events-none absolute right-3 text-suave" />
        </label>
      </div>

      {/* O calendario aparece so quando a aba dele esta ativa. Visivel sempre, seria um campo
          que nao faz nada nas outras tres abas. */}
      {filtros.quando === 'data' && (
        <label className="mt-4 flex flex-wrap items-center gap-3">
          <span className="text-sm text-suave">Data</span>
          <input
            type="date"
            value={filtros.dia}
            onChange={(e) => ajustar({ dia: e.target.value })}
            className="rounded-full border border-borda bg-superficie px-4 py-2 text-sm outline-none focus:border-marca"
          />
        </label>
      )}

      {/* A contagem e a cidade, em mono nas duas pontas — como no design. */}
      <div className="mt-6 flex flex-wrap items-center justify-between gap-3">
        {/* `aria-live` porque o numero muda sem que a pagina recarregue: quem nao ve a grade se
            reorganizar precisa ouvir que o filtro surtiu efeito. */}
        <p aria-live="polite" className="rotulo text-suave">
          {encontrados(lista.length)}
        </p>

        {cidades.length === 1 && <p className="rotulo text-suave">{cidades[0]}</p>}
      </div>

      <div className="mt-5">
        {catalogo.carregando && <EsqueletoDeCartoes quantos={6} />}
        {catalogo.erro !== undefined && <Erro erro={catalogo.erro} />}

        {!catalogo.carregando && catalogo.erro === undefined && lista.length === 0 && (
          <div className="animate-surgir rounded-cartao border border-dashed border-borda py-14 text-center">
            <p className="text-suave">
              {temFiltro
                ? 'Nenhum evento desta seleção combina com os filtros.'
                : 'Nenhum evento publicado ainda.'}
            </p>

            {temFiltro && (
              <div className="mt-5 flex flex-wrap items-center justify-center gap-x-6 gap-y-3">
                <button
                  type="button"
                  onClick={limpar}
                  className="text-sm font-medium text-marca-forte hover:underline"
                >
                  Limpar os filtros
                </button>

                {/*
                  A saida para fora da janela.

                  A busca desta pagina procura entre os eventos que a home carregou — ver
                  `catalogoDaHome`. Quando ela nao acha, o catalogo completo e o lugar certo
                  para perguntar, e a busca de `/explorar` roda no servidor. Sem esta ponte, uma
                  lista vazia aqui pareceria um catalogo vazio.
                */}
                <LinkDeSaida
                  para={`/explorar?busca=${encodeURIComponent(filtros.busca)}`}
                  className="text-marca-forte"
                >
                  Procurar no catálogo completo
                </LinkDeSaida>
              </div>
            )}
          </div>
        )}

        {lista.length > 0 && (
          <div className="grid gap-6 sm:grid-cols-2 lg:grid-cols-3">
            {lista.map((evento, indice) => (
              <CartaoDeEvento
                key={evento.id}
                evento={evento}
                disponibilidade={disponibilidades.get(evento.id)}
                atrasoDaAnimacao={Math.min(indice, 6) * 60}
              />
            ))}
          </div>
        )}
      </div>

      {lista.length > 0 && (
        <div className="mt-10 flex flex-wrap items-center justify-between gap-4 border-t border-borda pt-6">
          <p className="text-xs text-suave">
            Valores ilustrativos de ingresso inteiro. Pagamento apenas simulado.
          </p>

          {janelaCobreTudo && !temFiltro ? (
            <p className="rotulo inline-flex items-center gap-2 text-suave">
              Você viu todos os {total} eventos
              <IconeConfere tamanho={14} className="text-ok" />
            </p>
          ) : (
            <LinkDeSaida para="/explorar" className="rotulo text-marca-forte">
              Ver a programação completa
            </LinkDeSaida>
          )}
        </div>
      )}
    </FaixaDeSecao>
  )
}
