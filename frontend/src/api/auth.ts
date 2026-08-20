import { guardar, limpar, refreshTokenGuardado, requisitar } from './cliente'
import type { Tokens, UsuarioAutenticado, UsuarioCadastrado } from './tipos'

export function cadastrar(dados: {
  email: string
  password: string
  fullName: string
}): Promise<UsuarioCadastrado> {
  // Cadastro nao autentica: o backend devolve o usuario criado, e o login vem depois.
  return requisitar('/auth/register', { metodo: 'POST', corpo: dados, semRenovacao: true })
}

export async function entrar(email: string, password: string): Promise<Tokens> {
  const tokens = await requisitar<Tokens>('/auth/login', {
    metodo: 'POST',
    corpo: { email, password },
    semRenovacao: true,
  })
  guardar(tokens)
  return tokens
}

export function eu(): Promise<UsuarioAutenticado> {
  return requisitar('/auth/me')
}

/**
 * Renova a sessao a partir do refresh token guardado.
 *
 * Chamada na subida do app, quando o access token — que so vive em memoria — se perdeu no
 * recarregamento da pagina.
 */
export async function retomarSessao(): Promise<UsuarioAutenticado | null> {
  const refreshToken = refreshTokenGuardado()
  if (!refreshToken) return null

  try {
    const tokens = await requisitar<Tokens>('/auth/refresh', {
      metodo: 'POST',
      corpo: { refreshToken },
      semRenovacao: true,
    })
    guardar(tokens)
    return await eu()
  } catch {
    // Refresh token vencido, revogado ou de uma versao anterior do segredo. Nao ha o que
    // fazer alem de comecar do zero, e em silencio: o usuario nem pediu nada ainda.
    limpar()
    return null
  }
}

export async function sair(): Promise<void> {
  const refreshToken = refreshTokenGuardado()

  try {
    // Revoga do lado do servidor. Sem isto, o refresh token continuaria valido em qualquer
    // lugar onde tivesse vazado, mesmo depois de o usuario clicar em sair.
    if (refreshToken) {
      await requisitar('/auth/logout', {
        metodo: 'POST',
        corpo: { refreshToken },
        semRenovacao: true,
      })
    }
  } catch {
    // Falhar aqui nao pode impedir o usuario de sair da propria conta neste navegador.
  } finally {
    limpar()
  }
}
