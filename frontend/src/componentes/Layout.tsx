import { Suspense, useEffect } from 'react'
import { Link, NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom'
import { useSessao } from '../auth/SessaoContext'
import { Carregando } from './Estados'
import { FundoEstrelado } from './FundoEstrelado'
import { LinkDeSaida } from './Editorial'
import { IconeIngresso, IconeSeta } from './Icones'
import { Botao } from './Ui'

/** Onde o projeto mora. No rodape e na secao de duvidas, que apontam para a documentacao. */
const REPOSITORIO = 'https://github.com/devBandeiraa/ticket-platform'

/*
  Aviso de que isto e demonstracao.

  Aparece tres vezes no design — na faixa acima do cabecalho, no rodape e dentro da secao de
  duvidas — e o texto e o mesmo nas tres. Numa constante porque e a frase que separa este
  projeto de uma loja de verdade: se ela divergir entre os lugares, a mais fraca das versoes e
  a que alguem vai acreditar.
*/
export const AVISO_DE_DEMONSTRACAO = 'Projeto de portfólio · eventos de demonstração'

/**
 * A marca: icone de ingresso mais wordmark.
 *
 * <p>O ponto de `.platform` nao e decoracao tipografica — e o que separa o produto do generico
 * no nome, e por isso ele e o unico pedaco que muda de cor. No design o icone e um retangulo
 * laranja arredondado com o picote vazado; aqui ele e o mesmo desenho, em `currentColor`, para
 * poder ficar laranja no papel e laranja-claro na faixa escura sem uma segunda copia.
 */
function Marca({ className = '', tamanho = 26 }: { className?: string; tamanho?: number }) {
  return (
    <span className={`inline-flex items-center gap-2.5 font-semibold tracking-tight ${className}`}>
      <IconeIngresso tamanho={tamanho} className="text-marca" />
      <span>
        ticket<span className="text-marca">.platform</span>
      </span>
    </span>
  )
}

function Item({ para, children }: { para: string; children: React.ReactNode }) {
  return (
    <NavLink
      to={para}
      className={({ isActive }) =>
        // Ativo em cor, e nao em sublinhado: o cabecalho do design nao tem regua sob o item
        // corrente. `marca-forte` e nao `marca` porque isto e TEXTO PEQUENO sobre papel, onde
        // o vermelhao cheio fica em 3.45:1 — ver a nota da marca no index.css.
        `rounded-md px-3 py-1.5 text-sm transition-colors ${
          isActive ? 'font-medium text-marca-forte' : 'text-suave hover:text-texto'
        }`
      }
    >
      {children}
    </NavLink>
  )
}

/**
 * Item que aponta para uma secao da home.
 *
 * <p>"Agenda", "Como funciona" e "Ajuda" sao secoes desta pagina, e nao rotas: o design as
 * coloca na home, e inventar uma rota para cada uma criaria tres telas com um bloco de conteudo
 * cada. Como ancora, o destino existe de verdade e o endereco continua compartilhavel.
 *
 * <p>Nao usa `NavLink`: o estado ativo dele compara o CAMINHO, e as tres ancoras tem o mesmo
 * caminho — as tres acenderiam juntas na home e nenhuma fora dela.
 */
function ItemDeAncora({ para, children }: { para: string; children: React.ReactNode }) {
  return (
    <Link
      to={para}
      className="rounded-md px-3 py-1.5 text-sm text-suave transition-colors hover:text-texto"
    >
      {children}
    </Link>
  )
}

/**
 * Rola ate a ancora depois de navegar.
 *
 * <p>O roteador nao faz isso sozinho: ele troca a URL e para ali, entao clicar em "Agenda" de
 * dentro de `/explorar` levaria para o topo da home, com `#agenda` no endereco e nada tendo
 * rolado. O comportamento quebrado e pior do que a ausencia do link, porque parece que o clique
 * nao funcionou.
 *
 * <p>Depende do `hash` E do `pathname`: vindo de outra rota, o elemento da secao so existe
 * depois que a home montou, e um efeito que observasse apenas o hash rodaria antes disso.
 *
 * <p>A rolagem suave vem do `scroll-behavior` no `index.css`, que a regra de
 * `prefers-reduced-motion` ja desliga para quem pediu menos movimento.
 */
function useRolarAteAncora() {
  const { pathname, hash } = useLocation()

  useEffect(() => {
    if (!hash) return

    // Quadro seguinte: com a rota recem-trocada, o alvo pode ainda nao estar no documento.
    const quadro = requestAnimationFrame(() => {
      document.querySelector(hash)?.scrollIntoView({ block: 'start' })
    })

    return () => cancelAnimationFrame(quadro)
  }, [pathname, hash])
}

/**
 * Faixa escura.
 *
 * O ceu estrelado vive aqui dentro, e nao mais atras da pagina inteira. A troca veio junto da
 * identidade nova: area de leitura passou a ser papel claro, e o ceu nao atravessa papel. Onde
 * o escuro permaneceu — topo, rodape, mapa de assentos, telas tecnicas — ele continua fazendo
 * o que fazia.
 *
 * `isolate` cria um contexto de empilhamento proprio, para o `-z-10` do ceu ficar atras do
 * conteudo DESTA faixa sem escapar para tras do resto da pagina.
 */
export function FaixaNoite({
  children,
  comCeu = false,
  className = '',
}: {
  children: React.ReactNode
  comCeu?: boolean
  className?: string
}) {
  return (
    <div className={`faixa-noite relative isolate overflow-hidden ${className}`}>
      {comCeu && <FundoEstrelado className="ceu-mascarado absolute inset-0 -z-10 opacity-60" />}
      {children}
    </div>
  )
}

/** Uma coluna do rodape. */
function ColunaDoRodape({ titulo, children }: { titulo: string; children: React.ReactNode }) {
  return (
    <div>
      <p className="rotulo text-noite-suave">{titulo}</p>
      <ul className="mt-4 space-y-2.5">{children}</ul>
    </div>
  )
}

function LinhaDoRodape({ para, children }: { para: string; children: React.ReactNode }) {
  return (
    <li>
      <Link to={para} className="text-sm text-noite-suave transition-colors hover:text-noite-texto">
        {children}
      </Link>
    </li>
  )
}

/**
 * Rodape editorial.
 *
 * <p>Tres colunas, como no design: o que se DESCOBRE, o que e SEU e o que e o PROJETO. A terceira
 * e onde as telas tecnicas passaram a morar — concorrencia e status sairam do cabecalho porque
 * nao sao o que um comprador vem fazer aqui, e e tambem onde o design as coloca. Quem visita
 * como portfolio chega ao rodape; quem visita como plataforma nao esbarra nelas no caminho da
 * compra.
 */
function Rodape() {
  return (
    <FaixaNoite>
      <footer className="mx-auto max-w-6xl px-4 py-14">
        <div className="grid gap-10 sm:grid-cols-2 lg:grid-cols-[1.4fr_repeat(3,1fr)]">
          <div>
            <Marca tamanho={30} className="text-2xl" />
            <p className="mt-5 max-w-xs text-pretty text-lg text-noite-suave">
              A cultura acontece. Você faz parte.
            </p>
            <p className="rotulo mt-6 text-noite-suave/80">{AVISO_DE_DEMONSTRACAO}</p>
          </div>

          <ColunaDoRodape titulo="Descubra">
            <LinhaDoRodape para="/explorar">Explorar eventos</LinhaDoRodape>
            <LinhaDoRodape para="/#agenda">Agenda cultural</LinhaDoRodape>
            <LinhaDoRodape para="/#cenas">Categorias</LinhaDoRodape>
          </ColunaDoRodape>

          <ColunaDoRodape titulo="Seu ingresso">
            <LinhaDoRodape para="/meus-ingressos">Meus ingressos</LinhaDoRodape>
            <LinhaDoRodape para="/meus-ingressos">Minhas reservas</LinhaDoRodape>
            <LinhaDoRodape para="/#ajuda">Central de ajuda</LinhaDoRodape>
          </ColunaDoRodape>

          <ColunaDoRodape titulo="O projeto">
            <LinhaDoRodape para="/#como-funciona">Sobre a plataforma</LinhaDoRodape>
            <LinhaDoRodape para="/demo/concorrencia">Concorrência</LinhaDoRodape>
            <LinhaDoRodape para="/status">Status</LinhaDoRodape>
            <li>
              {/* `font-normal` para casar com as outras linhas da coluna: o peso medio que
                  `LinkDeSaida` traz destaca bem num fim de secao e, numa lista de links, faz
                  este parecer o item selecionado. */}
              <LinkDeSaida para={REPOSITORIO} className="font-normal text-noite-suave">
                Código no GitHub
              </LinkDeSaida>
            </li>
          </ColunaDoRodape>
        </div>

        {/* Regua tracejada, como no rodape do design — o mesmo picote do ingresso virando
            divisoria. `border-dashed` basta aqui: a linha e fina e longa, e nao precisa do
            controle de tamanho que a classe `.picote-horizontal` existe para dar. */}
        <div className="mt-12 flex flex-wrap items-center justify-between gap-x-6 gap-y-3 border-t border-dashed border-noite-borda pt-6">
          <p className="rotulo text-noite-suave/80">
            &copy; 2026 ticket.platform · Feito para experimentar.
          </p>

          <p className="rotulo text-noite-suave/80">
            Eventos fictícios. Pagamento simulado. Nenhuma cobrança real.
          </p>

          {/*
            Ancora para o topo, e nao um botao com `scrollTo`.

            O design pede "VOLTAR AO TOPO ↑" no fim de uma pagina de quatro mil pixels. Como
            link, funciona com o teclado e no clique do meio sem nenhuma linha de JavaScript, e
            herda a rolagem suave da folha de estilo. Um `<button onClick>` precisaria de tudo
            isso escrito a mao — e a rolagem instantanea de volta nao daria a quem clicou
            nenhuma pista de para onde a pagina foi.
          */}
          <a
            href="#topo"
            className="group rotulo inline-flex items-center gap-2 text-noite-suave transition-colors hover:text-noite-texto"
          >
            Voltar ao topo
            <span
              aria-hidden="true"
              className="transition-transform duration-200 group-hover:-translate-y-0.5"
            >
              &uarr;
            </span>
          </a>
        </div>
      </footer>
    </FaixaNoite>
  )
}

export function Layout() {
  const { usuario, ehAdmin, sair } = useSessao()
  const navegar = useNavigate()
  const local = useLocation()

  useRolarAteAncora()

  async function sairEVoltar() {
    await sair()
    navegar('/')
  }

  return (
    <div id="topo" className="flex min-h-screen flex-col">
      {/*
        Faixa de aviso acima do cabecalho.

        Primeira coisa da pagina no design, e com razao: o site vende ingresso para evento que
        nao existe, e quem descobre isso no checkout descobre tarde. Em `noite` para nao
        competir com o cabecalho logo abaixo — e um rodape de pagina colocado no topo, nao uma
        barra de anuncio.
      */}
      <p className="faixa-noite rotulo px-4 py-2.5 text-center text-noite-suave">
        {AVISO_DE_DEMONSTRACAO}
      </p>

      {/* Opaco, e nao translucido. Sobre a faixa escura do hero, papel a 85% vira um cinza
          barrento que nao pertence a nenhuma das duas familias de cor — e o desfoque atras
          dele nao tem o que desfocar quando o fundo e chapado. */}
      <header className="sticky top-0 z-20 border-b border-borda bg-papel">
        <div className="mx-auto flex max-w-6xl flex-wrap items-center gap-3 px-4 py-4">
          <Link to="/" className="text-lg transition-opacity hover:opacity-80">
            <Marca />
          </Link>

          {/*
            No telefone a navegacao ocupa uma linha so e rola na horizontal; a partir de `sm`
            ela volta a se comportar como antes, em linha com a marca.

            Os rotulos do design sao longos — "Explorar eventos", "Como funciona", "Meus
            ingressos" —, e num aparelho de 390px eles quebram em tres ou quatro linhas. Como o
            cabecalho e FIXO, essas linhas nao passam: ficam comendo um terco da tela durante a
            rolagem inteira. Rolando na horizontal, o cabecalho fica com a altura de duas linhas
            e nenhum item se perde.

            `order-last` so no telefone, para a primeira linha ser marca e "entrar" — o par que
            se procura primeiro — e a navegacao vir abaixo.
          */}
          {/* Centralizada entre a marca e as acoes, como no design. `mx-auto` num filho de
              `flex` centra em relacao a LINHA; `justify-center` no pai faria a marca empurrar
              o menu para a direita. */}
          <nav className="order-last flex w-full items-center gap-1 overflow-x-auto sm:order-none sm:mx-auto sm:w-auto sm:flex-wrap sm:overflow-visible">
            <Item para="/explorar">Explorar eventos</Item>
            <ItemDeAncora para="/#agenda">Agenda</ItemDeAncora>
            <ItemDeAncora para="/#como-funciona">Como funciona</ItemDeAncora>
            <ItemDeAncora para="/#ajuda">Ajuda</ItemDeAncora>
            {ehAdmin && <Item para="/admin">Painel</Item>}
            {ehAdmin && <Item para="/admin/eventos">Meus eventos</Item>}
            {ehAdmin && <Item para="/admin/reservas">Reservas</Item>}
          </nav>

          <div className="flex items-center gap-5">
            {/* Com icone e fora do `nav`, como no design: e acesso a area da pessoa, nao
                navegacao do catalogo. Visivel mesmo sem sessao — quem ja comprou chega por
                aqui, e a rota protegida leva ao login antes de abrir a lista. */}
            <Link
              to="/meus-ingressos"
              className="hidden items-center gap-2 text-sm text-suave transition-colors hover:text-texto sm:inline-flex"
            >
              <IconeIngresso tamanho={20} />
              Meus ingressos
            </Link>
            {usuario ? (
              <>
                {/* O nome vem do claim `name`. Token emitido antes da Fase 23 nao o tem, e
                    nesse caso o email serve — ver AuthenticatedUser. */}
                <span className="hidden text-sm text-suave sm:inline">
                  {usuario.fullName ?? usuario.email}
                </span>
                <Botao variante="neutro" onClick={sairEVoltar}>
                  Sair
                </Botao>
              </>
            ) : (
              // So "Entrar", como no design. O caminho para criar conta nao se perde: a tela de
              // login tem o link, e e la que quem nao tem conta descobre que precisa de uma.
              <Link
                to="/login"
                className="group inline-flex items-center gap-1.5 text-sm font-medium text-marca-forte transition-opacity hover:opacity-80"
              >
                Entrar
                <IconeSeta
                  tamanho={14}
                  className="transition-transform duration-200 group-hover:-translate-y-0.5 group-hover:translate-x-0.5"
                />
              </Link>
            )}
          </div>
        </div>
      </header>

      {/* A chave force a animacao de entrada a repetir a cada troca de rota. Sem ela o React
          reaproveita o no e a transicao so aconteceria no primeiro carregamento. */}
      {/* Sem container: a faixa escura do hero sangra ate a borda da janela, e um `max-w`
          em volta a transformaria numa caixa centralizada. Cada pagina declara o proprio —
          `Secao` para as comuns, largura total para as que tem faixa. */}
      <main key={local.pathname} className="flex-1 animate-subir">
        {/* Um limite de Suspense so, aqui, e nao um por rota: o cabecalho e o rodape continuam
            desenhados enquanto o pedaco da rota chega, entao quem navega ve a moldura da
            aplicacao em vez de uma tela branca. */}
        <Suspense fallback={<Carregando />}>
          <Outlet />
        </Suspense>
      </main>

      <Rodape />
    </div>
  )
}
