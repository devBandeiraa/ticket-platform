import { BrowserRouter, Route, Routes } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { ProvedorDeSessao } from './auth/SessaoContext'
import { RotaProtegida } from './auth/RotaProtegida'
import { Layout } from './componentes/Layout'
import { ErroDaApi } from './api/cliente'
import { Catalogo } from './paginas/Catalogo'
import { DetalheDoEvento } from './paginas/DetalheDoEvento'
import { Login } from './paginas/Login'
import { Cadastro } from './paginas/Cadastro'
import { MinhasReservas } from './paginas/MinhasReservas'
import { EventosAdmin } from './paginas/admin/EventosAdmin'
import { FormularioDeEvento } from './paginas/admin/FormularioDeEvento'
import { ReservasAdmin } from './paginas/admin/ReservasAdmin'
import { DemoConcorrencia } from './paginas/DemoConcorrencia'
import { NaoEncontrada } from './paginas/NaoEncontrada'

const clienteDeQueries = new QueryClient({
  defaultOptions: {
    queries: {
      // Dados de estoque envelhecem rapido: em 30s a disponibilidade ja pode ter mudado.
      staleTime: 30_000,
      retry: (tentativas, erro) => {
        // Nao insistir no que nao vai melhorar com insistencia. Repetir um 429 e o pior caso:
        // gasta mais fichas do balde e afunda o usuario mais fundo no limite.
        if (erro instanceof ErroDaApi && erro.status < 500) return false
        return tentativas < 2
      },
    },
  },
})

export default function App() {
  return (
    <QueryClientProvider client={clienteDeQueries}>
      <BrowserRouter>
        <ProvedorDeSessao>
          <Routes>
            <Route element={<Layout />}>
              {/* publicas */}
              <Route index element={<Catalogo />} />
              <Route path="eventos/:id" element={<DetalheDoEvento />} />
              <Route path="login" element={<Login />} />
              <Route path="cadastro" element={<Cadastro />} />
              <Route path="demo/concorrencia" element={<DemoConcorrencia />} />

              {/* exigem sessao */}
              <Route element={<RotaProtegida />}>
                <Route path="minhas-reservas" element={<MinhasReservas />} />
              </Route>

              {/* exigem ADMIN */}
              <Route element={<RotaProtegida exigeAdmin />}>
                <Route path="admin/eventos" element={<EventosAdmin />} />
                <Route path="admin/eventos/novo" element={<FormularioDeEvento />} />
                <Route path="admin/eventos/:id" element={<FormularioDeEvento />} />
                <Route path="admin/reservas" element={<ReservasAdmin />} />
              </Route>

              <Route path="*" element={<NaoEncontrada />} />
            </Route>
          </Routes>
        </ProvedorDeSessao>
      </BrowserRouter>
    </QueryClientProvider>
  )
}
