import { useState } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { useSessao } from '../auth/SessaoContext'
import { mensagemDe } from '../componentes/Estados'
import { Botao, Campo, Cartao } from '../componentes/Ui'

export function Login() {
  const { entrar } = useSessao()
  const navegar = useNavigate()
  const local = useLocation()

  const [email, setEmail] = useState('')
  const [senha, setSenha] = useState('')
  const [erro, setErro] = useState<unknown>(null)
  const [enviando, setEnviando] = useState(false)

  async function enviar(evento: React.FormEvent) {
    evento.preventDefault()
    setErro(null)
    setEnviando(true)

    try {
      await entrar(email, senha)
      // Volta para onde a pessoa tentou ir antes de ser mandada para ca.
      const destino = (local.state as { de?: string } | null)?.de ?? '/'
      navegar(destino, { replace: true })
    } catch (falha) {
      setErro(falha)
    } finally {
      setEnviando(false)
    }
  }

  return (
    <div className="mx-auto max-w-sm">
      <h1 className="mb-6 text-2xl font-semibold">Entrar</h1>

      <Cartao>
        <form onSubmit={enviar} className="space-y-4">
          <Campo
            rotulo="E-mail"
            type="email"
            autoComplete="email"
            required
            value={email}
            onChange={(e) => setEmail(e.target.value)}
          />
          <Campo
            rotulo="Senha"
            type="password"
            autoComplete="current-password"
            required
            value={senha}
            onChange={(e) => setSenha(e.target.value)}
          />

          {erro != null && (
            <p className="rounded-md bg-erro/10 px-3 py-2 text-sm text-erro">{mensagemDe(erro)}</p>
          )}

          <Botao type="submit" className="w-full" disabled={enviando}>
            {enviando ? 'Entrando...' : 'Entrar'}
          </Botao>
        </form>
      </Cartao>

      <p className="mt-4 text-center text-sm text-suave">
        Ainda nao tem conta?{' '}
        <Link to="/cadastro" className="text-marca hover:underline">
          Criar agora
        </Link>
      </p>
    </div>
  )
}
