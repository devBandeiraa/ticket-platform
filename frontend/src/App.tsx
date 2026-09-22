import { lazy } from 'react'
import { BrowserRouter, Route, Routes } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { ProvedorDeSessao } from './auth/SessaoContext'
import { RotaProtegida } from './auth/RotaProtegida'
import { Layout } from './componentes/Layout'
import { ErroDaApi } from './api/cliente'
import { Home } from './paginas/Home'

/*
  Divisao do pacote por rota.

  A Home fica ESTATICA: e a primeira tela de quase todo mundo, e adiar o codigo dela trocaria um
  pacote inicial menor por uma espera bem na abertura — o oposto do que se quer.

  O resto entra sob demanda. O peso concentra-se em dois grupos que a maioria nunca abre: a area
  administrativa, que so o organizador ve, e o ingresso digital, que carrega junto a biblioteca
  de QR — sozinha, 42 KB dos quais ninguem precisa antes de comprar.
*/
const Explorar = lazy(() => import('./paginas/Explorar').then((m) => ({ default: m.Explorar })))
const DetalheDoEvento = lazy(() =>
  import('./paginas/DetalheDoEvento').then((m) => ({ default: m.DetalheDoEvento })),
)
const Checkout = lazy(() => import('./paginas/Checkout').then((m) => ({ default: m.Checkout })))
const MeusIngressos = lazy(() =>
  import('./paginas/MeusIngressos').then((m) => ({ default: m.MeusIngressos })),
)
const Login = lazy(() => import('./paginas/Login').then((m) => ({ default: m.Login })))
const Cadastro = lazy(() => import('./paginas/Cadastro').then((m) => ({ default: m.Cadastro })))
const DemoConcorrencia = lazy(() =>
  import('./paginas/DemoConcorrencia').then((m) => ({ default: m.DemoConcorrencia })),
)
const Status = lazy(() => import('./paginas/Status').then((m) => ({ default: m.Status })))
const NaoEncontrada = lazy(() =>
  import('./paginas/NaoEncontrada').then((m) => ({ default: m.NaoEncontrada })),
)
const Painel = lazy(() => import('./paginas/admin/Painel').then((m) => ({ default: m.Painel })))
const EventosAdmin = lazy(() =>
  import('./paginas/admin/EventosAdmin').then((m) => ({ default: m.EventosAdmin })),
)
const FormularioDeEvento = lazy(() =>
  import('./paginas/admin/FormularioDeEvento').then((m) => ({ default: m.FormularioDeEvento })),
)
const ReservasAdmin = lazy(() =>
  import('./paginas/admin/ReservasAdmin').then((m) => ({ default: m.ReservasAdmin })),
)

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
              <Route index element={<Home />} />
              <Route path="explorar" element={<Explorar />} />
              <Route path="eventos/:id" element={<DetalheDoEvento />} />
              <Route path="login" element={<Login />} />
              <Route path="cadastro" element={<Cadastro />} />
              <Route path="demo/concorrencia" element={<DemoConcorrencia />} />
              {/* Publica de proposito: num projeto de portfolio, quem visita precisa conseguir
                  abrir. Num ambiente real ela exigiria sessao de administrador — a pagina revela
                  quais servicos existem e quais estao fora. */}
              <Route path="status" element={<Status />} />

              {/* exigem sessao */}
              <Route element={<RotaProtegida />}>
                <Route path="meus-ingressos" element={<MeusIngressos />} />
                {/* Protegida: a reserva pertence a alguem, e o backend devolve 403 para o
                    token de outro usuario. Sem a guarda, quem nao esta logado veria um erro
                    de API no lugar da tela de entrar. */}
                <Route path="checkout/:id" element={<Checkout />} />
              </Route>

              {/* exigem ADMIN */}
              <Route element={<RotaProtegida exigeAdmin />}>
                <Route path="admin" element={<Painel />} />
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
