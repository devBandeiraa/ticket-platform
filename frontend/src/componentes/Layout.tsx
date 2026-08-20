import { Link, NavLink, Outlet, useNavigate } from 'react-router-dom'
import { useSessao } from '../auth/SessaoContext'
import { Botao } from './Ui'

function Item({ para, children }: { para: string; children: React.ReactNode }) {
  return (
    <NavLink
      to={para}
      className={({ isActive }) =>
        `rounded-md px-3 py-1.5 text-sm transition ${
          isActive ? 'bg-superficie text-texto' : 'text-suave hover:text-texto'
        }`
      }
    >
      {children}
    </NavLink>
  )
}

export function Layout() {
  const { usuario, ehAdmin, sair } = useSessao()
  const navegar = useNavigate()

  async function sairEVoltar() {
    await sair()
    navegar('/')
  }

  return (
    <div className="min-h-screen">
      <header className="border-b border-borda">
        <div className="mx-auto flex max-w-5xl flex-wrap items-center gap-3 px-4 py-3">
          <Link to="/" className="mr-2 font-semibold">
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
                <Link to="/login" className="text-sm text-suave hover:text-texto">
                  Entrar
                </Link>
                <Link
                  to="/cadastro"
                  className="rounded-md bg-marca px-4 py-2 text-sm font-medium text-fundo hover:bg-marca-forte"
                >
                  Criar conta
                </Link>
              </>
            )}
          </div>
        </div>
      </header>

      <main className="mx-auto max-w-5xl px-4 py-8">
        <Outlet />
      </main>
    </div>
  )
}
