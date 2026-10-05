import { Link } from 'react-router-dom'
import { cenasDe, cidadesDe } from '../componentes/catalogo'
import { FaixaDeSecao } from '../componentes/Editorial'
import { useCatalogoDaHome, type FiltrosDaHome } from './home/catalogoDaHome'
import { Hero } from './home/Hero'
import { Busca } from './home/Busca'
import { Cenas } from './home/Cenas'
import { Lista } from './home/Lista'
import { Agenda } from './home/Agenda'
import { ComoFunciona } from './home/ComoFunciona'
import { Duvidas } from './home/Duvidas'

/**
 * Chamada para a demonstracao tecnica.
 *
 * <p>Fica no fim da home, e nao no hero, porque nao e o que um comprador vem fazer aqui. Quem
 * visita este projeto como portfolio chega ate o fim; quem visita como plataforma nao esbarra
 * nela no caminho da compra.
 *
 * <p>Sobreviveu a reforma do design, que nao previa a secao. O motivo e que ela e a unica porta
 * visivel para o que este projeto tem de mais proprio — o estoque que nao vende alem da
 * capacidade sob concorrencia. O rodape tambem aponta para la, e um link de rodape e facil de
 * nao ver.
 */
function ConvitePelaConcorrencia() {
  return (
    <FaixaDeSecao>
      <div className="rounded-cartao border border-borda bg-superficie p-8 text-center sm:p-12">
        <p className="inline-flex items-center gap-2 text-xs text-suave">
          <span className="size-1.5 animate-pulse rounded-full bg-ok" />
          cinco microsserviços no ar
        </p>

        <h2 className="mt-4 text-balance text-2xl font-semibold tracking-tight sm:text-3xl">
          Mil pessoas clicam <span className="text-marca">comprar</span> no mesmo segundo.
        </h2>

        <p className="mx-auto mt-4 max-w-xl text-pretty text-sm text-suave">
          Restam cinquenta ingressos. Quantos você vende? A resposta ingênua — consultar, decidir,
          gravar — vende mais do que existe. Esta plataforma não.
        </p>

        <Link
          to="/demo/concorrencia"
          className="group mt-8 inline-flex items-center gap-2 rounded-cartao border border-marca px-5 py-2.5 text-sm font-medium text-marca transition-colors hover:bg-marca hover:text-superficie"
        >
          Ver o teste de concorrência
          <span className="transition-transform duration-200 group-hover:translate-x-1">&rarr;</span>
        </Link>
      </div>
    </FaixaDeSecao>
  )
}

/**
 * Descoberta de eventos — a home.
 *
 * <h2>A pagina e uma consulta so</h2>
 *
 * <p>Oito secoes, e uma janela de eventos que as alimenta todas: o destaque do hero, as cidades
 * do campo "onde", a contagem por cena, a lista filtravel e a agenda do fim de semana saem do
 * mesmo conjunto. O motivo esta em `catalogoDaHome`, e o resumo e que secoes que contam a mesma
 * coisa precisam contar o mesmo numero.
 *
 * <p>Daqui para baixo, cada secao e so apresentacao: recebe o que precisa por propriedade e nao
 * consulta nada. As que podem ficar sem conteudo — a agenda, as cenas — decidem elas mesmas
 * desaparecer, porque quem sabe se uma secao tem o que mostrar e ela.
 *
 * <h2>A rolagem depois de filtrar</h2>
 *
 * <p>Buscar na secao de busca e escolher uma cena nos azulejos mudam a lista, que esta mais
 * abaixo na pagina — fora da tela, nos dois casos. Sem a rolagem, o clique nao teria efeito
 * VISIVEL: a pessoa escolheria "Teatro", nada mudaria na altura em que ela esta, e a conclusao
 * razoavel seria que o azulejo nao funciona.
 */
export function Home() {
  const catalogo = useCatalogoDaHome()

  const cenas = cenasDe(catalogo.janela)
  const cidades = cidadesDe(catalogo.janela)

  function filtrarERolar(mudanca: Partial<FiltrosDaHome>) {
    catalogo.ajustar(mudanca)
    document.getElementById('eventos')?.scrollIntoView({ block: 'start' })
  }

  return (
    <>
      <Hero
        evento={catalogo.janela[0]}
        disponibilidade={
          catalogo.janela[0] && catalogo.disponibilidades.get(catalogo.janela[0].id)
        }
        carregando={catalogo.carregando}
      />

      <Busca filtros={catalogo.filtros} cidades={cidades} aoBuscar={filtrarERolar} />

      <Cenas
        cenas={cenas}
        quantosEventos={catalogo.janela.length}
        categoriaAtiva={catalogo.filtros.categoria}
        aoEscolher={(categoria) => filtrarERolar({ categoria })}
      />

      <Lista catalogo={catalogo} cidades={cidades} />

      <Agenda eventos={catalogo.janela} />

      <ComoFunciona />

      <Duvidas />

      <ConvitePelaConcorrencia />
    </>
  )
}
