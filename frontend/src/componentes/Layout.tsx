import { Link, NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom'
import { useSessao } from '../auth/SessaoContext'
import { FundoEstrelado } from './FundoEstrelado'
import { Botao } from './Ui'

function Item({ para, children }: { para: string; children: React.ReactNode }) {
  return (
    <NavLink
      to={para}
      className={({ isActive }) =>
        `relative rounded-md px-3 py-1.5 text-sm transition-colors ${
          isActive ? 'text-texto' : 'text-suave hover:text-texto'
        }`
      }
    >
      {({ isActive }) => (
        <>
          {children}
          {/* Sublinhado em vez de fundo preenchido: um retangulo solido atras do item ativo
              engrossa o cabecalho e briga com a faixa escura logo abaixo. A linha marca sem
              tapar, e continua legivel nas duas familias de cor. */}
          {isActive && (
            <span className="absolute inset-x-3 -bottom-px h-0.5 rounded-full bg-marca" />
          )}
        </>
      )}
    </NavLink>
  )
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

export function Layout() {
  const { usuario, ehAdmin, sair } = useSessao()
  const navegar = useNavigate()
  const local = useLocation()

  async function sairEVoltar() {
    await sair()
    navegar('/')
  }

  return (
    <div className="flex min-h-screen flex-col">
      <header className="sticky top-0 z-20 border-b border-borda bg-papel/85 backdrop-blur-xl">
        <div className="mx-auto flex max-w-5xl flex-wrap items-center gap-3 px-4 py-3">
          <Link to="/" className="mr-2 font-semibold transition-opacity hover:opacity-80">
            ticket<span className="text-marca">.platform</span>
          </Link>

          <nav className="flex flex-wrap items-center gap-1">
            <Item para="/">Eventos</Item>
            {usuario && <Item para="/minhas-reservas">Minhas reservas</Item>}
            {ehAdmin && <Item para="/admin/eventos">Gerenciar</Item>}
            {ehAdmin && <Item para="/admin/reservas">Painel</Item>}
            <Item para="/demo/concorrencia">Concorrencia</Item>
            <Item para="/status">Status</Item>
          </nav>

          <div className="ml-auto flex items-center gap-3">
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
              <>
                <Link
                  to="/login"
                  className="rounded-md px-2 py-1 text-sm text-suave transition-colors hover:text-texto"
                >
                  Entrar
                </Link>
                <Link
                  to="/cadastro"
                  className="rounded-md bg-marca px-4 py-2 text-sm font-medium text-superficie transition-colors hover:bg-marca-forte active:scale-[0.97]"
                >
                  Criar conta
                </Link>
              </>
            )}
          </div>
        </div>
      </header>

      {/* A chave force a animacao de entrada a repetir a cada troca de rota. Sem ela o React
          reaproveita o no e a transicao so aconteceria no primeiro carregamento. */}
      {/* O container fica aqui por enquanto. Quando a home ganhar o hero de largura total, ele
          desce para cada pagina — uma faixa escura que sangra ate a borda nao cabe dentro de um
          `max-w`, e tirar o container agora deixaria todas as telas sem margem antes de haver
          quem as redesenhe. */}
      <main key={local.pathname} className="mx-auto w-full max-w-5xl flex-1 animate-subir px-4 py-10">
        <Outlet />
      </main>

      <FaixaNoite className="mt-16">
        <footer className="mx-auto max-w-5xl px-4 py-10 text-xs text-suave">
          Projeto de estudo em microsservicos — o estoque nunca vende alem da capacidade, e a
          garantia mora num <code className="text-texto">UPDATE</code> condicional no PostgreSQL.
        </footer>
      </FaixaNoite>
    </div>
  )
}
