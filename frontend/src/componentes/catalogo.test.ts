import { describe, expect, it } from 'vitest'
import type { CategoriaDoEvento, EventoResumo } from '../api/tipos'
import { cenasDe, cidadeDe, cidadesDe, filtrar, ordenar } from './catalogo'

/** Evento minimo: cada teste declara so os campos de que a regra sob teste depende. */
function evento(partes: Partial<EventoResumo> & { id: string }): EventoResumo {
  return {
    name: `Evento ${partes.id}`,
    venue: 'Casa da Lapa, Rio de Janeiro',
    eventDate: new Date(2026, 9, 18, 19, 0).toISOString(),
    price: 80,
    totalTickets: 50,
    imageUrl: null,
    category: 'SHOWS',
    ...partes,
  }
}

describe('cenasDe', () => {
  it('conta por categoria e ordena da cena mais cheia para a mais vazia', () => {
    const cenas = cenasDe([
      evento({ id: '1', category: 'SHOWS' }),
      evento({ id: '2', category: 'TEATRO' }),
      evento({ id: '3', category: 'SHOWS' }),
      evento({ id: '4', category: 'SHOWS' }),
      evento({ id: '5', category: 'FESTAS' }),
    ])

    expect(cenas.map((cena) => [cena.categoria, cena.quantos])).toEqual([
      ['SHOWS', 3],
      ['FESTAS', 1],
      ['TEATRO', 1],
    ])
  })

  it('omite categoria sem evento', () => {
    // O sobretitulo do design conta as cenas ("4 jeitos de viver a cidade"). Com as seis do
    // enum entrando sempre, o numero estaria errado e dois dos azulejos levariam a uma
    // lista vazia.
    const cenas = cenasDe([evento({ id: '1', category: 'TEATRO' })])

    expect(cenas).toHaveLength(1)
    expect(cenas[0].rotulo).toBe('Teatro')
  })

  it('ignora evento sem categoria conhecida', () => {
    // Evento gravado antes da Fase 21. Sem a guarda, viraria uma cena sem nome na tela.
    const cenas = cenasDe([
      evento({ id: '1', category: 'SHOWS' }),
      evento({ id: '2', category: undefined as unknown as CategoriaDoEvento }),
    ])

    expect(cenas).toEqual([{ categoria: 'SHOWS', rotulo: 'Shows', quantos: 1 }])
  })

  it('desempata pelo rotulo, para a ordem nao depender do servidor', () => {
    const umaOrdem = cenasDe([
      evento({ id: '1', category: 'TEATRO' }),
      evento({ id: '2', category: 'FESTAS' }),
    ])
    const outraOrdem = cenasDe([
      evento({ id: '1', category: 'FESTAS' }),
      evento({ id: '2', category: 'TEATRO' }),
    ])

    expect(umaOrdem).toEqual(outraOrdem)
  })

  it('devolve lista vazia para catalogo vazio', () => {
    expect(cenasDe([])).toEqual([])
  })
})

describe('cidadeDe', () => {
  it('pega o trecho depois da virgula', () => {
    expect(cidadeDe('Teatro Bradesco, Sao Paulo')).toBe('Sao Paulo')
  })

  it('pega o ULTIMO trecho quando o local tem bairro', () => {
    // Pegar o segundo devolveria "Centro", que e bairro e nao cidade.
    expect(cidadeDe('Teatro Municipal, Centro, Rio de Janeiro')).toBe('Rio de Janeiro')
  })

  it('devolve o local inteiro quando nao ha virgula', () => {
    // Devolver vazio aqui tiraria o evento do seletor de cidade; nomea-lo pelo local apenas o
    // nomeia de um jeito menos preciso.
    expect(cidadeDe('Arena Carioca')).toBe('Arena Carioca')
  })

  it('aguenta espaco sobrando e virgula no fim', () => {
    expect(cidadeDe('  Casa da Lapa ,  Rio de Janeiro , ')).toBe('Rio de Janeiro')
  })
})

describe('cidadesDe', () => {
  it('nao repete cidade e ordena em pt-BR', () => {
    const cidades = cidadesDe([
      evento({ id: '1', venue: 'Teatro Rival, Rio de Janeiro' }),
      evento({ id: '2', venue: 'Teatro Bradesco, Sao Paulo' }),
      evento({ id: '3', venue: 'Casa da Lapa, Rio de Janeiro' }),
    ])

    expect(cidades).toEqual(['Rio de Janeiro', 'Sao Paulo'])
  })
})

describe('ordenar', () => {
  const caro = evento({ id: 'caro', price: 140, eventDate: new Date(2026, 9, 16).toISOString() })
  const barato = evento({ id: 'barato', price: 25, eventDate: new Date(2026, 9, 24).toISOString() })
  const medio = evento({ id: 'medio', price: 80, eventDate: new Date(2026, 9, 18).toISOString() })

  it('editorial preserva a ordem do servidor', () => {
    expect(ordenar([caro, barato, medio], 'editorial').map((e) => e.id)).toEqual([
      'caro',
      'barato',
      'medio',
    ])
  })

  it('data poe o evento mais proximo primeiro', () => {
    expect(ordenar([barato, medio, caro], 'data').map((e) => e.id)).toEqual([
      'caro',
      'medio',
      'barato',
    ])
  })

  it('preco poe o mais barato primeiro', () => {
    expect(ordenar([caro, medio, barato], 'preco').map((e) => e.id)).toEqual([
      'barato',
      'medio',
      'caro',
    ])
  })

  it('desempata preco igual pela data', () => {
    const tarde = evento({ id: 'tarde', price: 40, eventDate: new Date(2026, 9, 25).toISOString() })
    const cedo = evento({ id: 'cedo', price: 40, eventDate: new Date(2026, 9, 17).toISOString() })

    expect(ordenar([tarde, cedo], 'preco').map((e) => e.id)).toEqual(['cedo', 'tarde'])
  })

  it('nao mexe na lista recebida', () => {
    // A lista vem do cache do React Query. Ordenar no lugar mudaria a ordem das cenas acima
    // quando alguem trocasse a ordenacao da lista abaixo.
    const original = [caro, barato, medio]
    ordenar(original, 'preco')

    expect(original.map((e) => e.id)).toEqual(['caro', 'barato', 'medio'])
  })
})

describe('filtrar', () => {
  const show = evento({
    id: 'show',
    category: 'SHOWS',
    venue: 'Teatro Rival, Rio de Janeiro',
    eventDate: new Date(2026, 9, 16, 20, 0).toISOString(),
  })
  const teatro = evento({
    id: 'teatro',
    category: 'TEATRO',
    venue: 'Teatro Bradesco, Sao Paulo',
    eventDate: new Date(2026, 9, 24, 19, 0).toISOString(),
  })

  it('sem filtro, devolve tudo', () => {
    expect(filtrar([show, teatro], {}).map((e) => e.id)).toEqual(['show', 'teatro'])
  })

  it('categoria vazia nao filtra', () => {
    // E o valor do seletor em "Categoria: todas". Tratado como filtro, a lista zeraria.
    expect(filtrar([show, teatro], { categoria: '' })).toHaveLength(2)
  })

  it('filtra por categoria', () => {
    expect(filtrar([show, teatro], { categoria: 'TEATRO' }).map((e) => e.id)).toEqual(['teatro'])
  })

  it('filtra por cidade', () => {
    expect(filtrar([show, teatro], { cidade: 'Sao Paulo' }).map((e) => e.id)).toEqual(['teatro'])
  })

  it('filtra pelo intervalo de datas, limites inclusive', () => {
    const intervalo = {
      de: new Date(2026, 9, 16, 20, 0).toISOString(),
      ate: new Date(2026, 9, 20, 0, 0).toISOString(),
    }

    expect(filtrar([show, teatro], intervalo).map((e) => e.id)).toEqual(['show'])
  })

  it('combina filtros', () => {
    expect(filtrar([show, teatro], { categoria: 'TEATRO', cidade: 'Rio de Janeiro' })).toEqual([])
  })

  it('busca vazia nao filtra', () => {
    // E o estado do campo antes de alguem digitar.
    expect(filtrar([show, teatro], { busca: '' })).toHaveLength(2)
    expect(filtrar([show, teatro], { busca: '   ' })).toHaveLength(2)
  })

  it('busca pelo nome do evento', () => {
    const jazz = evento({ id: 'jazz', name: 'Quintal do jazz: encontros ao anoitecer' })
    expect(filtrar([jazz, show], { busca: 'jazz' }).map((e) => e.id)).toEqual(['jazz'])
  })

  it('busca tambem pelo local, diferente do servidor', () => {
    // O servidor compara so o nome. Aqui o campo esta ao lado de um seletor de cidade, e quem
    // digita "Lapa" espera encontrar a Casa da Lapa.
    const lapa = evento({ id: 'lapa', name: 'Roda de domingo', venue: 'Casa da Lapa, Rio de Janeiro' })
    expect(filtrar([lapa, teatro], { busca: 'Lapa' }).map((e) => e.id)).toEqual(['lapa'])
  })

  it('ignora caixa e acento', () => {
    const orquestra = evento({ id: 'orq', name: 'Orquestra de Câmara da Lapa' })

    expect(filtrar([orquestra], { busca: 'camara' })).toHaveLength(1)
    expect(filtrar([orquestra], { busca: 'CÂMARA' })).toHaveLength(1)
    expect(filtrar([orquestra], { busca: 'orquestra' })).toHaveLength(1)
  })

  it('nao encontra o que nao esta no catalogo', () => {
    expect(filtrar([show, teatro], { busca: 'opera chinesa' })).toEqual([])
  })
})
