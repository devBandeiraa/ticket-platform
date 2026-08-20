import type { CorpoDeErro, Tokens } from './tipos'

const BASE = import.meta.env.VITE_API_URL ?? 'http://localhost:8080/api'

/**
 * Chave do refresh token no localStorage. O access token *nao* mora aqui — ver `guardar`.
 */
const CHAVE_DO_REFRESH = 'ticket.refreshToken'

/**
 * Erro de qualquer chamada a API, ja com o codigo estavel que o backend devolve.
 *
 * A tela decide o que fazer olhando `codigo` (`SOLD_OUT`, `BOOKING_EXPIRED`), e nunca o texto
 * da mensagem — foi para isso que o backend passou a devolver um codigo legivel por maquina.
 */
export class ErroDaApi extends Error {
  // Campos declarados e atribuidos no corpo, e nao como parametros-propriedade: o projeto
  // compila com `erasableSyntaxOnly`, que so aceita sintaxe que some ao apagar os tipos.
  readonly status: number
  readonly codigo: string
  readonly corpo?: CorpoDeErro

  constructor(status: number, codigo: string, corpo?: CorpoDeErro) {
    super(corpo?.message ?? `Falha na requisicao (${status})`)
    this.name = 'ErroDaApi'
    this.status = status
    this.codigo = codigo
    this.corpo = corpo
  }

  /** Erros por campo, presentes so em falha de validacao. */
  get campos(): Record<string, string> | undefined {
    return this.corpo?.fields
  }
}

// ---------------------------------------------------------------------------
// Sessao
// ---------------------------------------------------------------------------

/**
 * O access token vive apenas em memoria, e o refresh token no localStorage.
 *
 * Nao e teatro de seguranca: um XSS alcanca o localStorage, entao guardar ali o token de
 * acesso entregaria de imediato uma credencial pronta para uso. Mantendo-o so em memoria, o
 * que sobra no disco e um refresh token — que o `/auth/logout` revoga de verdade, porque o
 * auth-service o persiste. O access token, esse ninguem consegue revogar, e e justamente por
 * isso que ele nao deve ficar guardado.
 *
 * O custo e recarregar a pagina perder o access token. Resolve-se renovando na subida do app,
 * com o refresh token guardado — que e o fluxo que o `/auth/refresh` existe para atender.
 */
let accessToken: string | null = null

let aoPerderSessao: (() => void) | null = null

/** Avisa o app que a sessao caiu de vez. O cliente nao navega: ele nao conhece rotas. */
export function observarPerdaDeSessao(callback: () => void) {
  aoPerderSessao = callback
}

export function guardar(tokens: Tokens) {
  accessToken = tokens.accessToken
  localStorage.setItem(CHAVE_DO_REFRESH, tokens.refreshToken)
}

export function limpar() {
  accessToken = null
  localStorage.removeItem(CHAVE_DO_REFRESH)
}

export function refreshTokenGuardado(): string | null {
  return localStorage.getItem(CHAVE_DO_REFRESH)
}

export function temAccessToken(): boolean {
  return accessToken !== null
}

// ---------------------------------------------------------------------------
// Renovacao
// ---------------------------------------------------------------------------

/**
 * Renovacao em andamento, compartilhada.
 *
 * Sem isto, uma tela que dispara tres chamadas em paralelo com o token vencido faria tres
 * renovacoes simultaneas. Como o auth-service **rotaciona** o refresh token, a primeira
 * invalidaria o que as outras duas ja tinham em maos, e duas das tres derrubariam a sessao de
 * um usuario que nao fez nada de errado.
 */
let renovacaoEmAndamento: Promise<string> | null = null

async function renovar(): Promise<string> {
  if (renovacaoEmAndamento) return renovacaoEmAndamento

  renovacaoEmAndamento = (async () => {
    const refreshToken = refreshTokenGuardado()
    if (!refreshToken) throw new ErroDaApi(401, 'NO_REFRESH_TOKEN')

    const resposta = await fetch(`${BASE}/auth/refresh`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ refreshToken }),
    })

    if (!resposta.ok) throw await construirErro(resposta)

    const tokens: Tokens = await resposta.json()
    guardar(tokens)
    return tokens.accessToken
  })()

  try {
    return await renovacaoEmAndamento
  } finally {
    renovacaoEmAndamento = null
  }
}

// ---------------------------------------------------------------------------
// Requisicao
// ---------------------------------------------------------------------------

interface Opcoes {
  metodo?: 'GET' | 'POST' | 'PUT' | 'DELETE'
  corpo?: unknown
  cabecalhos?: Record<string, string>
  /** Rotas de credencial nao devem tentar renovar: o 401 delas significa senha errada. */
  semRenovacao?: boolean
}

export async function requisitar<T>(caminho: string, opcoes: Opcoes = {}): Promise<T> {
  const resposta = await enviar(caminho, opcoes)

  // Uma unica retentativa, e so ela: se a chamada falhar de novo depois de um token novo em
  // folha, o problema nao e o token, e insistir viraria laco.
  if (resposta.status === 401 && !opcoes.semRenovacao && refreshTokenGuardado()) {
    try {
      await renovar()
    } catch {
      limpar()
      aoPerderSessao?.()
      throw new ErroDaApi(401, 'SESSION_EXPIRED')
    }
    return interpretar<T>(await enviar(caminho, opcoes))
  }

  return interpretar<T>(resposta)
}

function enviar(caminho: string, opcoes: Opcoes): Promise<Response> {
  const cabecalhos: Record<string, string> = { ...opcoes.cabecalhos }

  if (opcoes.corpo !== undefined) cabecalhos['Content-Type'] = 'application/json'
  if (accessToken) cabecalhos.Authorization = `Bearer ${accessToken}`

  return fetch(`${BASE}${caminho}`, {
    method: opcoes.metodo ?? 'GET',
    headers: cabecalhos,
    body: opcoes.corpo === undefined ? undefined : JSON.stringify(opcoes.corpo),
  })
}

async function interpretar<T>(resposta: Response): Promise<T> {
  if (!resposta.ok) throw await construirErro(resposta)

  // 204 e o `cancelar`, que nao devolve corpo. `json()` num corpo vazio estoura.
  if (resposta.status === 204) return undefined as T

  return (await resposta.json()) as T
}

async function construirErro(resposta: Response): Promise<ErroDaApi> {
  try {
    const corpo: CorpoDeErro = await resposta.json()
    return new ErroDaApi(resposta.status, corpo.error, corpo)
  } catch {
    // Resposta sem JSON: gateway fora do ar, proxy no caminho, corpo truncado. Vale ter um
    // codigo proprio para a tela nao confundir isso com um erro de negocio.
    return new ErroDaApi(resposta.status, 'RESPOSTA_ILEGIVEL')
  }
}

/** Monta a query string ignorando o que estiver vazio, para nao mandar `status=` a toa. */
export function query(parametros: Record<string, string | number | undefined | null>): string {
  const partes = Object.entries(parametros)
    .filter(([, valor]) => valor !== undefined && valor !== null && valor !== '')
    .map(([chave, valor]) => `${encodeURIComponent(chave)}=${encodeURIComponent(String(valor))}`)

  return partes.length ? `?${partes.join('&')}` : ''
}
