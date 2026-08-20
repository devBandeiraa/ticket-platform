import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { useSessao } from './SessaoContext'
import { Carregando } from '../componentes/Estados'

/**
 * Esconde rotas de quem nao esta autenticado — ou nao e admin, quando exigido.
 *
 * Vale dizer o que isto **nao** e: seguranca. Qualquer pessoa edita o JavaScript da propria
 * aba e alcanca a tela. A garantia esta no backend, onde cada servico valida o token e
 * confere o papel. Aqui o objetivo e outro: nao mostrar ao usuario um caminho que vai
 * terminar em 403.
 */
export function RotaProtegida({ exigeAdmin = false }: { exigeAdmin?: boolean }) {
  const { usuario, carregando, ehAdmin } = useSessao()
  const local = useLocation()

  // Sem esta espera, um recarregamento em /minhas-reservas jogaria para o login antes de a
  // sessao ser retomada, e o usuario perderia a pagina em que estava.
  if (carregando) return <Carregando />

  if (!usuario) {
    // `state` guarda onde a pessoa queria chegar, para o login devolve-la ao lugar certo.
    return <Navigate to="/login" replace state={{ de: local.pathname + local.search }} />
  }

  if (exigeAdmin && !ehAdmin) return <Navigate to="/" replace />

  return <Outlet />
}
