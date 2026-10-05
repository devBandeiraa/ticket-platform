import type { CategoriaDoEvento, EventoResumo } from '../api/tipos'
import { ROTULOS_DE_CATEGORIA } from './categorias'

/*
  Leituras do catalogo que a home faz sobre a janela de eventos que ela ja buscou.

  ----------------------------------------------------------------------------
   Por que aqui, e em funcoes puras
  ----------------------------------------------------------------------------
  A home do design nao e uma vitrine com seis cartoes: ela conta quantos eventos existem por
  categoria, lista as cidades, filtra por cena e reordena a lista — tudo sobre o MESMO conjunto.
  Pedir cada uma dessas respostas ao servidor seria uma requisicao por secao, e as respostas
  poderiam discordar entre si: "6 eventos" no cabecalho e sete cartoes abaixo, porque um evento
  foi publicado entre as duas chamadas.

  Com uma consulta so e as contas feitas aqui, a pagina inteira fala do mesmo instante. E como
  sao funcoes puras sobre uma lista, cada regra — qual e a cidade de um local, o que "ordenar
  por data" significa — tem teste proprio, sem React e sem servidor no meio.

  A autoridade sobre o catalogo COMPLETO continua sendo a descoberta, em /explorar, que pagina
  no servidor. O que esta aqui opera sobre a selecao da home, e e assim que o design a
  apresenta: "uma selecao de encontros".
*/

/** Uma cena do catalogo: a categoria, o rotulo que vai a tela e quantos eventos ela tem. */
export interface Cena {
  categoria: CategoriaDoEvento
  rotulo: string
  quantos: number
}

/**
 * As cenas presentes na janela, da mais cheia para a mais vazia.
 *
 * <p>Somente as que TEM evento. Uma categoria vazia na tela e um convite para um clique que
 * leva a uma lista vazia — e o design conta as cenas no sobretitulo da secao ("4 jeitos de
 * viver a cidade"), numero que ficaria errado se as seis categorias do enum entrassem sempre.
 *
 * <p>O desempate e pelo rotulo, e nao pela ordem de chegada: com dois empates em um evento, a
 * ordem passaria a depender de qual deles o servidor devolveu primeiro, e a secao trocaria de
 * arranjo a cada publicacao sem nada ter mudado para quem olha.
 */
export function cenasDe(eventos: EventoResumo[]): Cena[] {
  const contagem = new Map<CategoriaDoEvento, number>()

  for (const evento of eventos) {
    // Evento gravado antes da Fase 21 nao tem categoria, e sem esta guarda ele viraria uma
    // cena sem nome — a mesma ausencia que o cartao do catalogo ja trata.
    if (!ROTULOS_DE_CATEGORIA[evento.category]) continue
    contagem.set(evento.category, (contagem.get(evento.category) ?? 0) + 1)
  }

  return [...contagem.entries()]
    .map(([categoria, quantos]) => ({
      categoria,
      rotulo: ROTULOS_DE_CATEGORIA[categoria],
      quantos,
    }))
    .sort((a, b) => b.quantos - a.quantos || a.rotulo.localeCompare(b.rotulo, 'pt-BR'))
}

/**
 * A cidade de um local.
 *
 * <p>O `venue` do catalogo e escrito como "Casa, Cidade" — "Teatro Bradesco, Sao Paulo". A
 * cidade e o ULTIMO trecho, e nao o segundo: "Teatro Municipal, Centro, Rio de Janeiro" tem
 * tres, e pegar o segundo devolveria o bairro.
 *
 * <p>Sem virgula, o local inteiro e a resposta. E o caso de um nome que ja carrega a cidade
 * ("Arena Carioca"), e devolver vazio ali tiraria o evento do seletor de cidade em vez de
 * apenas nomea-lo de um jeito menos preciso.
 */
export function cidadeDe(local: string): string {
  const partes = local
    .split(',')
    .map((parte) => parte.trim())
    .filter(Boolean)

  return partes.at(-1) ?? local.trim()
}

/** As cidades da janela, em ordem alfabetica e sem repeticao. */
export function cidadesDe(eventos: EventoResumo[]): string[] {
  const cidades = new Set(eventos.map((evento) => cidadeDe(evento.venue)))
  return [...cidades].sort((a, b) => a.localeCompare(b, 'pt-BR'))
}

/**
 * Criterio de ordenacao da lista da home.
 *
 * <p>`editorial` e a ordem em que o servidor devolveu — data crescente, o proximo evento
 * primeiro. Tem nome proprio em vez de se chamar "padrao" porque e o que o design exibe no
 * seletor, e porque "selecao editorial" descreve a intencao: e a ordem em que a casa sugere
 * que se leia a programacao.
 */
export type Ordenacao = 'editorial' | 'data' | 'preco'

export const ROTULOS_DE_ORDENACAO: Record<Ordenacao, string> = {
  editorial: 'Seleção editorial',
  data: 'Data mais próxima',
  preco: 'Menor preço',
}

export const ORDENACOES = Object.keys(ROTULOS_DE_ORDENACAO) as Ordenacao[]

/**
 * Reordena uma copia da lista.
 *
 * <p>Copia, e nao `sort` no lugar: a lista vem do cache do React Query, e ordenar o proprio
 * array mutaria o dado em cache. O efeito seria a secao de cenas acima mudando de ordem quando
 * alguem trocasse a ordenacao da lista abaixo — duas secoes acopladas por um detalhe de
 * implementacao, e um defeito que so aparece na segunda interacao.
 *
 * <p>`editorial` devolve a copia sem tocar na ordem: ja e a do servidor.
 */
export function ordenar(eventos: EventoResumo[], criterio: Ordenacao): EventoResumo[] {
  const copia = [...eventos]

  switch (criterio) {
    case 'data':
      return copia.sort((a, b) => new Date(a.eventDate).getTime() - new Date(b.eventDate).getTime())

    case 'preco':
      // Desempate pela data: dois eventos de R$ 40 na mesma lista ficariam em ordem indefinida,
      // e a lista trocaria de arranjo entre renderizacoes sem motivo visivel.
      return copia.sort(
        (a, b) =>
          a.price - b.price || new Date(a.eventDate).getTime() - new Date(b.eventDate).getTime(),
      )

    case 'editorial':
      return copia
  }
}

/**
 * Compara ignorando caixa e acento.
 *
 * <p>`localeCompare` com sensibilidade de base e o que faz "sao paulo" encontrar "São Paulo".
 * Normalizar a mao — tirar acento por regex, baixar a caixa — erraria nos casos que a tabela do
 * locale conhece e uma substituicao de caracteres nao, e e exatamente o tipo de regra que nao
 * se quer reescrever.
 */
function contem(texto: string, trecho: string): boolean {
  const alvo = trecho.trim()
  if (!alvo) return true

  // `localeCompare` compara strings inteiras, e aqui a pergunta e de SUBSTRING. Dai a janela
  // deslizante: cada trecho do tamanho do alvo e comparado com ele sob as mesmas regras de
  // locale. O texto e curto — nome de evento e de casa —, entao o custo nao aparece.
  for (let i = 0; i + alvo.length <= texto.length; i++) {
    const pedaco = texto.slice(i, i + alvo.length)
    if (pedaco.localeCompare(alvo, 'pt-BR', { sensitivity: 'base' }) === 0) return true
  }

  return false
}

/** O que a lista da home filtra sem voltar ao servidor. Campo ausente nao filtra. */
export interface Filtros {
  /**
   * Trecho do nome ou do local.
   *
   * <p>Mais amplo do que a busca do servidor, que compara so o nome — ver
   * `EventSpecifications.comNomeContendo`. A diferenca e deliberada: o campo da home esta ao
   * lado de um seletor de cidade, e quem digita "Lapa" ali espera encontrar a Casa da Lapa.
   */
  busca?: string
  categoria?: CategoriaDoEvento | ''
  cidade?: string
  /** Limites em ISO 8601, como os de `intervalos.ts`. */
  de?: string
  ate?: string
}

/**
 * Aplica os filtros da home sobre a janela.
 *
 * <p>A data e filtrada aqui, e nao numa consulta nova por clique: trocar de aba — "esta
 * semana", "este fim de semana" — responde na hora e sem esqueleto, porque o conjunto ja esta
 * na mao. O preco e que a janela e finita; o catalogo inteiro continua em /explorar.
 */
export function filtrar(eventos: EventoResumo[], filtros: Filtros): EventoResumo[] {
  const de = filtros.de ? new Date(filtros.de).getTime() : undefined
  const ate = filtros.ate ? new Date(filtros.ate).getTime() : undefined

  return eventos.filter((evento) => {
    if (filtros.categoria && evento.category !== filtros.categoria) return false
    if (filtros.cidade && cidadeDe(evento.venue) !== filtros.cidade) return false

    if (filtros.busca && !contem(`${evento.name} ${evento.venue}`, filtros.busca)) {
      return false
    }

    const quando = new Date(evento.eventDate).getTime()
    if (de !== undefined && quando < de) return false
    if (ate !== undefined && quando > ate) return false

    return true
  })
}
