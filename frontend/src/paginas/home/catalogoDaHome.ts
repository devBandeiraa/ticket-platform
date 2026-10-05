import { useMemo, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { listarPublicados } from '../../api/eventos'
import type { CategoriaDoEvento, Disponibilidade, EventoResumo } from '../../api/tipos'
import { useDisponibilidades } from '../../componentes/usarDisponibilidades'
import { filtrar, ordenar, type Ordenacao } from '../../componentes/catalogo'
import { intervaloDe, intervaloDoDia, type Atalho } from '../../componentes/intervalos'

/*
  O catalogo da home: uma consulta, muitas leituras.

  ----------------------------------------------------------------------------
   Por que uma janela, e nao uma consulta por secao
  ----------------------------------------------------------------------------
  A home do design le o catalogo cinco vezes: o evento em destaque do hero, as cidades do campo
  "onde", a contagem por cena, a lista filtravel e a agenda do fim de semana. Com uma consulta
  por secao seriam cinco requisicoes dizendo quase a mesma coisa — e, pior, podendo discordar:
  "6 eventos" num cabecalho e sete cartoes abaixo, porque alguem publicou um evento entre a
  terceira e a quarta chamada.

  Uma janela so, e todas as secoes falando do mesmo instante. As contas ficam em `catalogo.ts` e
  `agenda.ts`, em funcoes puras com teste proprio.

  ----------------------------------------------------------------------------
   O preco da janela, declarado
  ----------------------------------------------------------------------------
  Vinte e quatro eventos. A home nao e o catalogo completo — nunca foi —, e o design a apresenta
  como "uma selecao de encontros". Quem precisa do catalogo inteiro vai para `/explorar`, que
  pagina no servidor.

  Onde isso aparece: a busca por palavra-chave desta pagina procura DENTRO da janela. Entao a
  lista vazia nao diz apenas "nao encontrei" — ela oferece a busca no catalogo completo, que e o
  caminho para o que esta fora da janela. E o fim da lista distingue os dois casos: "voce viu
  todos os N eventos" so aparece quando a janela de fato cobre o catalogo, e a comparacao e com
  o `totalElements` que o servidor devolveu, nao com um palpite.

  ----------------------------------------------------------------------------
   Onde os filtros vivem
  ----------------------------------------------------------------------------
  Em estado local, e nao na URL — ao contrario de `/explorar`, e de proposito. A descoberta e a
  tela que alguem manda por mensagem ("olha os shows deste fim de semana"), e la os filtros
  precisam sobreviver ao link. A home e a porta de entrada: trocar de aba entre "esta semana" e
  "este fim de semana" e folhear, nao recortar uma consulta para compartilhar. Com a URL, cada
  clique numa aba entraria no historico, e o botao "voltar" deixaria de sair da pagina.
*/

const TAMANHO_DA_JANELA = 24

/*
  Lista vazia estavel, para o caso de a consulta ainda nao ter respondido.

  Um `?? []` literal cria um array NOVO a cada renderizacao, e a identidade e justamente o que o
  `useMemo` abaixo observa: com um literal, a lista filtrada seria recalculada sempre e a grade
  refaria a animacao de entrada de todos os cartoes a cada tecla digitada na busca. Com a
  constante, "sem dados" e sempre o mesmo objeto.
*/
const VAZIO: EventoResumo[] = []

/** `data` significa "um dia escolhido no calendario" — o quarto botao do design. */
export type Quando = Atalho | 'data'

export interface FiltrosDaHome {
  busca: string
  cidade: string
  quando: Quando
  /** `yyyy-mm-dd` do campo de calendario. Vale apenas quando `quando` e `data`. */
  dia: string
  categoria: CategoriaDoEvento | ''
  ordenacao: Ordenacao
}

export const FILTROS_INICIAIS: FiltrosDaHome = {
  busca: '',
  cidade: '',
  quando: 'proximos',
  dia: '',
  categoria: '',
  ordenacao: 'editorial',
}

export interface CatalogoDaHome {
  /*
    Carregamento e erro vem como dois campos, e nao como o objeto da consulta inteiro.

    As secoes precisam de duas respostas — "ainda nao chegou" e "nao deu" —, e passar o objeto do
    React Query adiante entregaria a elas trinta propriedades e a chance de cada uma inventar o
    proprio criterio de "pronto". Com dois campos, a regra e uma.
  */
  carregando: boolean
  erro: unknown
  /** A janela inteira, na ordem do servidor. Base do hero, das cenas e da agenda. */
  janela: EventoResumo[]
  /** A janela depois dos filtros e da ordenacao. O que a lista desenha. */
  lista: EventoResumo[]
  /** Quantos eventos publicados existem no total, segundo o servidor. */
  total: number
  /** Verdadeiro quando a janela cobre o catalogo inteiro. */
  janelaCobreTudo: boolean
  disponibilidades: Map<string, Disponibilidade>
  filtros: FiltrosDaHome
  ajustar: (mudanca: Partial<FiltrosDaHome>) => void
  limpar: () => void
  /** Verdadeiro quando algum filtro esta ativo — o que decide o texto da lista vazia. */
  temFiltro: boolean
}

export function useCatalogoDaHome(): CatalogoDaHome {
  const [filtros, setFiltros] = useState<FiltrosDaHome>(FILTROS_INICIAIS)

  const consulta = useQuery({
    queryKey: ['eventos', 'home'],
    // `de` fica fora da chave de cache de proposito: com o instante atual na chave, cada
    // renderizacao criaria uma chave nova e a consulta nunca aproveitaria o cache.
    queryFn: () =>
      listarPublicados({
        page: 0,
        size: TAMANHO_DA_JANELA,
        de: new Date().toISOString(),
      }),
  })

  const janela = consulta.data?.content ?? VAZIO
  const total = consulta.data?.totalElements ?? janela.length

  const intervalo =
    filtros.quando === 'data' ? intervaloDoDia(filtros.dia) : intervaloDe(filtros.quando)

  // `useMemo` pela identidade do resultado, e nao pelo custo: `lista` alimenta a grade de
  // cartoes, e um array novo a cada renderizacao refaria a animacao de entrada de todos eles
  // sempre que qualquer estado da pagina mudasse.
  const lista = useMemo(
    () =>
      ordenar(
        filtrar(janela, {
          busca: filtros.busca,
          categoria: filtros.categoria,
          cidade: filtros.cidade,
          de: intervalo.de,
          ate: intervalo.ate,
        }),
        filtros.ordenacao,
      ),
    [
      janela,
      filtros.busca,
      filtros.categoria,
      filtros.cidade,
      filtros.ordenacao,
      intervalo.de,
      intervalo.ate,
    ],
  )

  // Sobre a janela inteira, e nao sobre a lista filtrada: o selo de disponibilidade do hero e da
  // agenda precisa do numero mesmo quando a lista esta filtrada para outra cena. O hook cacheia
  // por evento, entao pedir a janela inteira nao multiplica requisicao ao trocar de filtro.
  const disponibilidades = useDisponibilidades(janela.map((evento) => evento.id))

  function ajustar(mudanca: Partial<FiltrosDaHome>) {
    setFiltros((atuais) => ({ ...atuais, ...mudanca }))
  }

  return {
    carregando: consulta.isPending,
    erro: consulta.isError ? consulta.error : undefined,
    janela,
    lista,
    total,
    janelaCobreTudo: janela.length >= total,
    disponibilidades,
    filtros,
    ajustar,
    limpar: () => setFiltros(FILTROS_INICIAIS),
    temFiltro:
      filtros.busca.trim() !== '' ||
      filtros.cidade !== '' ||
      filtros.categoria !== '' ||
      filtros.quando !== 'proximos',
  }
}
