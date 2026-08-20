import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { cadastrar } from '../api/auth'
import { ErroDaApi } from '../api/cliente'
import { useSessao } from '../auth/SessaoContext'
import { mensagemDe } from '../componentes/Estados'
import { Botao, Campo, Cartao } from '../componentes/Ui'

export function Cadastro() {
  const { entrar } = useSessao()
  const navegar = useNavigate()

  const [nome, setNome] = useState('')
  const [email, setEmail] = useState('')
  const [senha, setSenha] = useState('')
  const [erro, setErro] = useState<unknown>(null)
  const [enviando, setEnviando] = useState(false)

  // O backend devolve erros por campo em `fields`; mostrar cada um no seu lugar poupa o
  // usuario de adivinhar qual dos tres campos ele errou.
  const campos = erro instanceof ErroDaApi ? erro.campos : undefined

  async function enviar(evento: React.FormEvent) {
    evento.preventDefault()
    setErro(null)
    setEnviando(true)

    try {
      await cadastrar({ email, password: senha, fullName: nome })
      // Cadastro nao autentica — o backend devolve o usuario criado, sem token. Entrar em
      // seguida poupa o usuario de digitar a mesma senha duas vezes.
      await entrar(email, senha)
      navegar('/', { replace: true })
    } catch (falha) {
      setErro(falha)
    } finally {
      setEnviando(false)
    }
  }

  return (
    <div className="mx-auto max-w-sm">
      <h1 className="mb-6 text-2xl font-semibold">Criar conta</h1>

      <Cartao>
        <form onSubmit={enviar} className="space-y-4">
          <Campo
            rotulo="Nome completo"
            autoComplete="name"
            required
            value={nome}
            onChange={(e) => setNome(e.target.value)}
            erro={campos?.fullName}
          />
          <Campo
            rotulo="E-mail"
            type="email"
            autoComplete="email"
            required
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            erro={campos?.email}
          />
          <Campo
            rotulo="Senha"
            type="password"
            autoComplete="new-password"
            required
            value={senha}
            onChange={(e) => setSenha(e.target.value)}
            erro={campos?.password}
          />

          {erro != null && (
            <p className="rounded-md bg-erro/10 px-3 py-2 text-sm text-erro">{mensagemDe(erro)}</p>
          )}

          <Botao type="submit" className="w-full" disabled={enviando}>
            {enviando ? 'Criando...' : 'Criar conta'}
          </Botao>
        </form>
      </Cartao>

      <p className="mt-4 text-center text-sm text-suave">
        Ja tem conta?{' '}
        <Link to="/login" className="text-marca hover:underline">
          Entrar
        </Link>
      </p>
    </div>
  )
}
