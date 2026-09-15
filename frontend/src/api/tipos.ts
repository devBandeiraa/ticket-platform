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

/**
 * Porta de entrada do catalogo.
 *
 * Conjunto fechado, e nao texto livre: com texto, em poucos meses conviveriam 'Show', 'show' e
 * 'Musica', e o filtro deixaria de filtrar. Um valor fora do conjunto nao chega ate aqui — o
 * servidor devolve 400 na desserializacao.
 */
export type CategoriaDoEvento =
  | 'SHOWS'
  | 'FESTIVAIS'
  | 'ESPORTES'
  | 'TECNOLOGIA'
  | 'TEATRO'
  | 'FESTAS'

/** Faixa de um setor. Nao e derivada do preco: o setor mais caro nem sempre e o VIP. */
export type FaixaDeSetor = 'STANDARD' | 'VIP'

export type StatusDaReserva = 'PENDING' | 'CONFIRMED' | 'CANCELLED' | 'EXPIRED'

/** Forma escolhida no checkout. O simulador trata as duas iguais; a distincao e do comprador. */
export type FormaDePagamento = 'CARD' | 'PIX'

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
  /** Capa do evento. Nulo quando ainda nao ha arte — a tela desenha um fundo derivado do nome. */
  imageUrl: string | null
  /** Vem tambem no resumo porque o cartao do catalogo a exibe como etiqueta. */
  category: CategoriaDoEvento
}

/**
 * Um setor da casa, como o servidor o descreve.
 *
 * Vem com as dimensoes, e nao com a lista de lugares: com filas e lugares por fila, a tela
 * desenha a grade inteira. A disponibilidade de cada lugar nao esta aqui — quem sabe o que ja
 * foi vendido e o booking-service.
 */
export interface Setor {
  id: string
  name: string
  price: number
  rowsCount: number
  seatsPerRow: number
  /** Rotulos prontos: 'A', 'B', ... 'AA'. Vem do servidor para as duas pontas nao divergirem. */
  rowLabels: string[]
  capacity: number
  /** Texto corrido. Nulo quando o nome e o preco ja se explicam. */
  description: string | null
  /** Sempre presente, possivelmente vazio. Nunca nulo: a tela percorre sem testar nulidade. */
  benefits: string[]
  tier: FaixaDeSetor
}

export interface EventoDetalhe extends EventoResumo {
  description: string | null
  sectors: Setor[]
  status: StatusDoEvento
  createdBy: string
  createdAt: string
  updatedAt: string
}

/** Um setor como o admin o envia: sem id, sem rotulos e sem capacidade — o servidor os deriva. */
export interface SetorFormulario {
  name: string
  price: number
  rowsCount: number
  seatsPerRow: number
  description?: string | null
  benefits?: string[]
  tier?: FaixaDeSetor
}

/**
 * Corpo de criacao e de alteracao — os campos editaveis sao os mesmos nos dois casos.
 *
 * Sem `totalTickets` e sem `price`: os dois passaram a ser derivados dos setores. Envia-los
 * permitiria que discordassem da planta, e o catalogo anunciaria uma casa que nao existe.
 */
export interface EventoFormulario {
  name: string
  description?: string | null
  venue: string
  eventDate: string
  sectors: SetorFormulario[]
  imageUrl?: string | null
  category: CategoriaDoEvento
}

export interface Disponibilidade {
  eventId: string
  total: number
  reserved: number
  available: number
}

/** Um lugar de uma reserva. `label` vem pronto do servidor: "Plateia A12". */
export interface AssentoDaReserva {
  seatId: string
  sector: string
  row: string
  number: number
  label: string
  price: number
}

/** Estado de um lugar no mapa. RESERVED pode voltar a ficar livre; SOLD, nao. */
export type StatusDoAssento = 'FREE' | 'RESERVED' | 'SOLD'

/** Um lugar no mapa do evento, com o estado que o booking-service conhece. */
export interface AssentoDoMapa extends AssentoDaReserva {
  status: StatusDoAssento
}

export interface MapaDeAssentos {
  eventId: string
  seats: AssentoDoMapa[]
}

export interface Reserva {
  id: string
  eventId: string
  userId: string
  quantity: number
  /** Soma dos precos dos lugares, sem a taxa. */
  subtotal: number
  /** Taxa de servico sobre o subtotal. Zero quando a plataforma nao cobra taxa. */
  fee: number
  /**
   * O que foi cobrado: `subtotal + fee`.
   *
   * Mudou de significado na Fase 22. Ate ela era a soma dos lugares, que hoje e `subtotal`. Os
   * tres vem na resposta em vez de a tela recalcular: a regra de arredondamento mora no
   * servidor, e refaze-la aqui a duplicaria em outra linguagem.
   */
  totalPrice: number
  /**
   * Os lugares da reserva, cada um com o que custou no ato da compra.
   *
   * Substitui o antigo `unitPrice`: uma reserva de Plateia a 180 e Galeria a 70 nao tem preco
   * unitario, e a media seria um valor que nenhum ingresso custou.
   *
   * Vem vazia nas reservas anteriores a Fase 17, feitas quando o sistema contava ingressos sem
   * saber quais eram.
   */
  seats: AssentoDaReserva[]
  status: StatusDaReserva
  expiresAt: string | null
  paidAt: string | null
  createdAt: string
  /** Nulos enquanto a reserva nao foi paga, e nas reservas confirmadas antes da Fase 22. */
  paymentMethod: FormaDePagamento | null
  /** Numero do ingresso, no formato `TP-XXXXXX-XXXXXX`. Sem ele, nao ha ingresso a desenhar. */
  ticketCode: string | null
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
