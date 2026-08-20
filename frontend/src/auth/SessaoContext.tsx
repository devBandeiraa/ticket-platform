import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'
import type { ReactNode } from 'react'
import * as auth from '../api/auth'
import { observarPerdaDeSessao } from '../api/cliente'
import type { UsuarioAutenticado } from '../api/tipos'

interface Sessao {
  usuario: UsuarioAutenticado | null
  /** Verdadeiro so durante a tentativa de retomar a sessao na subida do app. */
  carregando: boolean
  entrar: (email: string, senha: string) => Promise<void>
  sair: () => Promise<void>
  ehAdmin: boolean
}

const SessaoContext = createContext<Sessao | null>(null)

export function ProvedorDeSessao({ children }: { children: ReactNode }) {
  const [usuario, setUsuario] = useState<UsuarioAutenticado | null>(null)
  const [carregando, setCarregando] = useState(true)

  useEffect(() => {
    // O access token so vive em memoria, entao recarregar a pagina o perde. O refresh token
    // guardado permite recuperar a sessao sem pedir a senha de novo.
    auth
      .retomarSessao()
      .then(setUsuario)
      .finally(() => setCarregando(false))
  }, [])

  useEffect(() => {
    // O cliente HTTP avisa quando a renovacao falhou de vez. Ele nao navega nem conhece
    // React; apenas informa, e quem decide o que fazer e esta camada.
    observarPerdaDeSessao(() => setUsuario(null))
  }, [])

  const entrar = useCallback(async (email: string, senha: string) => {
    await auth.entrar(email, senha)
    setUsuario(await auth.eu())
  }, [])

  const sair = useCallback(async () => {
    await auth.sair()
    setUsuario(null)
  }, [])

  const valor = useMemo<Sessao>(
    () => ({ usuario, carregando, entrar, sair, ehAdmin: usuario?.role === 'ADMIN' }),
    [usuario, carregando, entrar, sair],
  )

  return <SessaoContext value={valor}>{children}</SessaoContext>
}

export function useSessao(): Sessao {
  const sessao = useContext(SessaoContext)
  if (!sessao) throw new Error('useSessao precisa estar dentro de ProvedorDeSessao')
  return sessao
}
