import { beforeEach, describe, expect, it, vi } from 'vitest'

type Cliente = typeof import('./cliente')
type Falha = import('./cliente').ErroDaApi

/** Captura a rejeicao ja tipada, para os testes lerem `codigo` sem repetir um cast a cada uso. */
function capturar(promessa: Promise<unknown>): Promise<Falha> {
  return promessa.then(
    () => {
      throw new Error('esperava rejeicao, mas a chamada teve sucesso')
    },
    (falha: Falha) => falha,
  )
}

/**
 * Testes do cliente HTTP — em especial da renovacao silenciosa.
 *
 * E a unica parte do frontend com logica que pode falhar de um jeito silencioso e caro:
 * derrubar a sessao de quem nao fez nada de errado, ou entrar em laco de retentativa. As
 * telas em volta sao composicao, e um teste de renderizacao delas verificaria sobretudo o
 * proprio React.
 *
 * O modulo guarda estado (o access token em memoria, a renovacao em andamento), entao cada
 * teste o reimporta do zero.
 */
describe('cliente', () => {
  let cliente: Cliente
  let fetchSimulado: ReturnType<typeof vi.fn>

  beforeEach(async () => {
    vi.resetModules()
    localStorage.clear()

    fetchSimulado = vi.fn()
    vi.stubGlobal('fetch', fetchSimulado)

    cliente = await import('./cliente')
  })

  function resposta(status: number, corpo?: unknown): Response {
    return {
      ok: status >= 200 && status < 300,
      status,
      json: async () => {
        if (corpo === undefined) throw new Error('sem corpo')
        return corpo
      },
    } as Response
  }

  function erroDoBackend(status: number, codigo: string) {
    return resposta(status, {
      timestamp: '2026-08-20T12:00:00Z',
      status,
      error: codigo,
      message: 'mensagem qualquer',
      path: '/api/qualquer',
      traceId: 'abc12345',
    })
  }

  const TOKENS = {
    accessToken: 'novo-access',
    refreshToken: 'novo-refresh',
    tokenType: 'Bearer',
    expiresIn: 900,
  }

  // ---------- basico ----------

  it('devolve o corpo em caso de sucesso', async () => {
    fetchSimulado.mockResolvedValueOnce(resposta(200, { id: 7 }))

    await expect(cliente.requisitar('/qualquer')).resolves.toEqual({ id: 7 })
  })

  it('devolve undefined no 204, em vez de estourar ao ler o corpo', async () => {
    // O `cancelar` responde 204. `json()` num corpo vazio lanca, e o erro apareceria como
    // falha de cancelamento para um cancelamento que deu certo.
    fetchSimulado.mockResolvedValueOnce(resposta(204))

    await expect(cliente.requisitar('/bookings/1/cancel', { metodo: 'POST' })).resolves.toBeUndefined()
  })

  it('traduz o corpo de erro do backend em ErroDaApi', async () => {
    fetchSimulado.mockResolvedValueOnce(erroDoBackend(409, 'SOLD_OUT'))

    const falha = await capturar(cliente.requisitar('/bookings'))

    expect(falha).toBeInstanceOf(cliente.ErroDaApi)
    expect(falha.codigo).toBe('SOLD_OUT')
    expect(falha.status).toBe(409)
    expect(falha.corpo?.traceId).toBe('abc12345')
  })

  it('nao confunde resposta sem JSON com erro de negocio', async () => {
    fetchSimulado.mockResolvedValueOnce(resposta(502))

    const falha = await capturar(cliente.requisitar('/qualquer'))

    expect(falha.codigo).toBe('RESPOSTA_ILEGIVEL')
  })

  it('manda o access token guardado no cabecalho', async () => {
    cliente.guardar(TOKENS)
    fetchSimulado.mockResolvedValueOnce(resposta(200, {}))

    await cliente.requisitar('/auth/me')

    const [, opcoes] = fetchSimulado.mock.calls[0]
    expect(opcoes.headers.Authorization).toBe('Bearer novo-access')
  })

  // ---------- renovacao ----------

  it('renova e repete a chamada quando recebe 401', async () => {
    localStorage.setItem('ticket.refreshToken', 'refresh-valido')

    fetchSimulado
      .mockResolvedValueOnce(erroDoBackend(401, 'INVALID_TOKEN')) // chamada original
      .mockResolvedValueOnce(resposta(200, TOKENS)) // /auth/refresh
      .mockResolvedValueOnce(resposta(200, { id: 7 })) // repeticao

    await expect(cliente.requisitar('/bookings/me')).resolves.toEqual({ id: 7 })

    expect(fetchSimulado.mock.calls[1][0]).toContain('/auth/refresh')
    // A repeticao ja vai com o token novo; sem isso ela receberia 401 de novo.
    expect(fetchSimulado.mock.calls[2][1].headers.Authorization).toBe('Bearer novo-access')
  })

  it('nao repete mais de uma vez', async () => {
    localStorage.setItem('ticket.refreshToken', 'refresh-valido')

    fetchSimulado
      .mockResolvedValueOnce(erroDoBackend(401, 'INVALID_TOKEN'))
      .mockResolvedValueOnce(resposta(200, TOKENS))
      .mockResolvedValueOnce(erroDoBackend(401, 'INVALID_TOKEN')) // falha de novo

    // Se falha com um token recem-emitido, o problema nao e o token. Insistir viraria laco.
    await expect(cliente.requisitar('/bookings/me')).rejects.toBeInstanceOf(cliente.ErroDaApi)
    expect(fetchSimulado).toHaveBeenCalledTimes(3)
  })

  it('renova uma unica vez para varias chamadas simultaneas', async () => {
    localStorage.setItem('ticket.refreshToken', 'refresh-valido')

    fetchSimulado.mockImplementation(async (url: string) => {
      if (url.includes('/auth/refresh')) return resposta(200, TOKENS)
      if (!cliente.temAccessToken()) return erroDoBackend(401, 'INVALID_TOKEN')
      return resposta(200, { ok: true })
    })

    await Promise.all([
      cliente.requisitar('/a'),
      cliente.requisitar('/b'),
      cliente.requisitar('/c'),
    ])

    // O auth-service rotaciona o refresh token: tres renovacoes em paralelo fariam a primeira
    // invalidar o que as outras duas tinham em maos, e a sessao cairia sem motivo.
    const renovacoes = fetchSimulado.mock.calls.filter(([url]) => url.includes('/auth/refresh'))
    expect(renovacoes).toHaveLength(1)
  })

  it('limpa a sessao e avisa quando a renovacao falha', async () => {
    localStorage.setItem('ticket.refreshToken', 'refresh-vencido')

    const avisou = vi.fn()
    cliente.observarPerdaDeSessao(avisou)

    fetchSimulado
      .mockResolvedValueOnce(erroDoBackend(401, 'INVALID_TOKEN'))
      .mockResolvedValueOnce(erroDoBackend(401, 'INVALID_REFRESH_TOKEN'))

    const falha = await capturar(cliente.requisitar('/bookings/me'))

    expect(falha.codigo).toBe('SESSION_EXPIRED')
    expect(avisou).toHaveBeenCalledOnce()
    // Guardar um refresh token que o servidor ja recusou faria toda subida seguinte do app
    // gastar uma chamada para ouvir o mesmo nao.
    expect(localStorage.getItem('ticket.refreshToken')).toBeNull()
  })

  it('nao tenta renovar em rota de credencial', async () => {
    localStorage.setItem('ticket.refreshToken', 'refresh-valido')

    fetchSimulado.mockResolvedValueOnce(erroDoBackend(401, 'INVALID_CREDENTIALS'))

    // O 401 do login significa senha errada. Renovar aqui trocaria uma mensagem util por uma
    // sessao derrubada, e ainda gastaria uma chamada.
    const falha = await capturar(
      cliente.requisitar('/auth/login', { metodo: 'POST', corpo: {}, semRenovacao: true }),
    )

    expect(falha.codigo).toBe('INVALID_CREDENTIALS')
    expect(fetchSimulado).toHaveBeenCalledOnce()
  })

  it('nao tenta renovar sem refresh token guardado', async () => {
    fetchSimulado.mockResolvedValueOnce(erroDoBackend(401, 'INVALID_TOKEN'))

    // Visitante anonimo esbarrando numa rota protegida: nao ha o que renovar.
    await expect(cliente.requisitar('/bookings/me')).rejects.toBeInstanceOf(cliente.ErroDaApi)
    expect(fetchSimulado).toHaveBeenCalledOnce()
  })

  // ---------- query ----------

  it('omite parametros vazios da query string', () => {
    // Sem isso, um filtro nao preenchido viraria `status=` e o backend tentaria converter
    // string vazia em enum.
    expect(cliente.query({ page: 0, status: '', eventId: undefined })).toBe('?page=0')
    expect(cliente.query({})).toBe('')
  })

  it('escapa valores na query string', () => {
    expect(cliente.query({ busca: 'rock & roll' })).toBe('?busca=rock%20%26%20roll')
  })
})
