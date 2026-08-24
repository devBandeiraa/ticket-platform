import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { Status } from './Status'
import type { StatusDaPlataforma } from '../api/status'

/*
  A tela de status.

  O que se verifica aqui e a traducao de dados em significado, que e onde uma pagina destas erra
  de um jeito caro: mostrar "0 ms" para um servico sem trafego, ou pintar tudo de verde porque o
  campo veio nulo. Nenhum desses erros quebra a tela — ela so passa a mentir.
*/

vi.mock('../api/status', async (original) => ({
  ...(await original<typeof import('../api/status')>()),
  consultarStatus: vi.fn(),
}))

const { consultarStatus } = await import('../api/status')
const consultaSimulada = vi.mocked(consultarStatus)

afterEach(() => {
  vi.clearAllMocks()
})

function renderizar() {
  // retry desligado e um cliente novo por teste: sem isso o cache de um teste vazaria para o
  // seguinte e a assercao passaria pelo motivo errado.
  const cliente = new QueryClient({ defaultOptions: { queries: { retry: false } } })

  return render(
    <QueryClientProvider client={cliente}>
      <Status />
    </QueryClientProvider>,
  )
}

function retrato(parcial: Partial<StatusDaPlataforma> = {}): StatusDaPlataforma {
  return {
    coletadoEm: '2026-08-24T12:00:00Z',
    servicos: [],
    circuitos: [],
    ...parcial,
  }
}

describe('pagina de status', () => {
  it('anuncia quando esta tudo no ar', async () => {
    consultaSimulada.mockResolvedValue(
      retrato({
        servicos: [
          { nome: 'api-gateway', noAr: true, latenciaMediaMs: 12.5, uptimeSegundos: 3601 },
          { nome: 'booking-service', noAr: true, latenciaMediaMs: 42, uptimeSegundos: 900 },
        ],
      }),
    )

    renderizar()

    expect(await screen.findByText('Todos os 2 servicos no ar')).toBeDefined()
    expect(screen.getByText('12.5 ms')).toBeDefined()
    // 3601 segundos: uma hora e um minuto. So as duas maiores unidades aparecem.
    expect(screen.getByText('1h 0min')).toBeDefined()
  })

  it('conta quantos estao fora, em vez de so mudar de cor', async () => {
    consultaSimulada.mockResolvedValue(
      retrato({
        servicos: [
          { nome: 'api-gateway', noAr: true, latenciaMediaMs: 10, uptimeSegundos: 60 },
          { nome: 'event-service', noAr: false, latenciaMediaMs: null, uptimeSegundos: null },
        ],
      }),
    )

    renderizar()

    expect(await screen.findByText('1 de 2 servicos fora do ar')).toBeDefined()
    // O texto ao lado do ponto colorido: quem nao distingue verde de vermelho precisa ler.
    expect(screen.getByText('fora')).toBeDefined()
  })

  it('distingue servico sem trafego de servico instantaneo', async () => {
    consultaSimulada.mockResolvedValue(
      retrato({
        servicos: [
          {
            nome: 'notification-service',
            noAr: true,
            latenciaMediaMs: null,
            uptimeSegundos: 120,
          },
        ],
      }),
    )

    renderizar()

    // Este e o teste que justifica o nulo atravessar a API inteira sem virar zero no caminho.
    // "0.0 ms" leria como "responde instantaneamente", que e o oposto de "nao respondeu nada".
    expect(await screen.findByText('sem trafego')).toBeDefined()
    expect(screen.queryByText('0.0 ms')).toBeNull()
  })

  it('explica o que um circuito aberto significa, e nao so o rotulo', async () => {
    consultaSimulada.mockResolvedValue(
      retrato({
        servicos: [
          { nome: 'booking-service', noAr: true, latenciaMediaMs: 30, uptimeSegundos: 300 },
        ],
        circuitos: [{ nome: 'event-service', estado: 'ABERTO' }],
      }),
    )

    renderizar()

    expect(await screen.findByText('Chamadas ao event-service')).toBeDefined()
    expect(screen.getByText('aberto')).toBeDefined()
    expect(screen.getByText(/recusadas sem sair pelo fio/)).toBeDefined()
  })

  it('sem metricas, diz que perdeu a fonte — e nao que a plataforma caiu', async () => {
    consultaSimulada.mockRejectedValue(new Error('sem Prometheus'))

    renderizar()

    // A frase importa mais que o estado tecnico: sem ela, quem le conclui que os seis servicos
    // cairam quando o que caiu foi o painel.
    await waitFor(() => {
      expect(screen.getByText(/nao que os servicos estejam/i)).toBeDefined()
    })
  })
})
