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
          {/* Sublinhado em vez de fundo preenchido: sobre o ceu estrelado, um retangulo solido
              atras do item ativo brigava com o fundo. A linha marca sem tapar. */}
          {isActive && (
            <span className="absolute inset-x-3 -bottom-px h-0.5 rounded-full bg-marca" />
          )}
        </>
      )}
    </NavLink>
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
    <div className="relative min-h-screen">
      <FundoEstrelado className="ceu-mascarado opacity-70" />

      {/* Halo da marca no topo. Da profundidade ao cabecalho e amarra o azul do tema ao ceu,
          que sem ele fica parecendo um papel de parede colado por cima de outro projeto. */}
      <div
        aria-hidden="true"
        className="pointer-events-none fixed inset-x-0 top-0 -z-10 h-96 bg-[radial-gradient(ellipse_60%_100%_at_50%_0%,color-mix(in_oklch,var(--color-marca)_18%,transparent),transparent)]"
      />

      <header className="sticky top-0 z-20 border-b border-borda/70 bg-fundo/70 backdrop-blur-xl">
        <div className="mx-auto flex max-w-5xl flex-wrap items-center gap-3 px-4 py-3">
          <Link
            to="/"
            className="mr-2 font-semibold transition-opacity hover:opacity-80"
          >
            ticket<span className="text-marca">.platform</span>
          </Link>

          <nav className="flex flex-wrap items-center gap-1">
            <Item para="/">Eventos</Item>
            {usuario && <Item para="/minhas-reservas">Minhas reservas</Item>}
            {ehAdmin && <Item para="/admin/eventos">Gerenciar</Item>}
            {ehAdmin && <Item para="/admin/reservas">Painel</Item>}
            <Item para="/demo/concorrencia">Concorrencia</Item>
          </nav>

          <div className="ml-auto flex items-center gap-3">
            {usuario ? (
              <>
                <span className="hidden text-sm text-suave sm:inline">{usuario.email}</span>
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
                  className="rounded-md bg-marca px-4 py-2 text-sm font-medium text-fundo shadow-lg shadow-marca/20 transition-all duration-200 hover:bg-marca-forte hover:shadow-xl hover:shadow-marca/30 active:scale-[0.97]"
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
      <main key={local.pathname} className="mx-auto max-w-5xl animate-subir px-4 py-10">
        <Outlet />
      </main>

      <footer className="mx-auto max-w-5xl px-4 pb-10 text-xs text-suave/70">
        <div className="border-t border-borda/60 pt-6">
          Projeto de estudo em microsservicos — o estoque nunca vende alem da capacidade, e a
          garantia mora num <code className="text-suave">UPDATE</code> condicional no PostgreSQL.
        </div>
      </footer>
    </div>
  )
}
