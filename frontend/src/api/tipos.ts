/**
 * Contratos da API, espelhados dos records do backend.
 *
 * Copia deliberada, e nao tipo gerado: o contrato entre frontend e backend e o JSON que
 * atravessa a rede, e ele so muda numa versao nova da API. Gerar estes tipos a partir do
 * codigo Java acoplaria o build do frontend ao do backend, que e justamente o acoplamento
 * que uma API HTTP existe para desfazer.
 *
 * Datas chegam como string ISO 8601 e assim permanecem. Converter para `Date` na borda
 * espalharia fuso horario por toda parte; a conversao acontece so onde algo e exibido.
 */

export type Papel = 'USER' | 'ADMIN'

export type StatusDoEvento = 'DRAFT' | 'PUBLISHED' | 'CANCELLED'

export type StatusDaReserva = 'PENDING' | 'CONFIRMED' | 'CANCELLED' | 'EXPIRED'

export interface Tokens {
  accessToken: string
  refreshToken: string
  tokenType: string
  /** Segundos ate o access token expirar. */
  expiresIn: number
}

/** O que `/auth/me` devolve: vem inteiro do token, sem consulta a banco. */
export interface UsuarioAutenticado {
  id: string
  email: string
  role: Papel
}

export interface UsuarioCadastrado {
  id: string
  email: string
  fullName: string
  role: Papel
  createdAt: string
}

export interface EventoResumo {
  id: string
  name: string
  venue: string
  eventDate: string
  price: number
  totalTickets: number
}

export interface EventoDetalhe extends EventoResumo {
  description: string | null
  status: StatusDoEvento
  createdBy: string
  createdAt: string
  updatedAt: string
}

/** Corpo de criacao e de alteracao — os campos editaveis sao os mesmos nos dois casos. */
export interface EventoFormulario {
  name: string
  description?: string | null
  venue: string
  eventDate: string
  totalTickets: number
  price: number
}

export interface Disponibilidade {
  eventId: string
  total: number
  reserved: number
  available: number
}

export interface Reserva {
  id: string
  eventId: string
  userId: string
  quantity: number
  unitPrice: number
  totalPrice: number
  status: StatusDaReserva
  expiresAt: string | null
  paidAt: string | null
  createdAt: string
}

export interface Pagina<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  first: boolean
  last: boolean
}

/** Formato unico de erro da plataforma, venha do servico ou do proprio gateway. */
export interface CorpoDeErro {
  timestamp: string
  status: number
  error: string
  message: string
  path: string
  fields?: Record<string, string>
  traceId: string
}
