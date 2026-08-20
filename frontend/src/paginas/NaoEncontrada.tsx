import { Link } from 'react-router-dom'

export function NaoEncontrada() {
  return (
    <div className="py-20 text-center">
      <p className="numerico text-5xl font-semibold text-suave">404</p>
      <p className="mt-3 text-suave">Esta pagina nao existe.</p>
      <Link to="/" className="mt-6 inline-block text-marca hover:underline">
        Voltar ao catalogo
      </Link>
    </div>
  )
}
