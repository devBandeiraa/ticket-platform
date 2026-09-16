import { useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { listarPublicados } from '../api/eventos'
import type { CategoriaDoEvento } from '../api/tipos'
import { CartaoDeEvento } from '../componentes/CartaoDeEvento'
import { useDisponibilidades } from '../componentes/usarDisponibilidades'
import { Erro, EsqueletoDeCartoes, Vazio } from '../componentes/Estados'
import { Paginacao } from '../componentes/Ui'
import { CATEGORIAS, ROTULOS_DE_CATEGORIA } from '../componentes/categorias'
import { ROTULOS_DE_ATALHO, intervaloDe, type Atalho } from '../componentes/intervalos'

const POR_PAGINA = 9

const ATALHOS = Object.keys(ROTULOS_DE_ATALHO) as Atalho[]

/**
 * Descoberta de eventos.
 *
 * <h2>Por que os filtros vivem na URL</h2>
 *
 * <p>Estado em `useState` se perde ao recarregar, nao volta no botao "voltar" do navegador e nao
 * pode ser compartilhado. Um filtro de catalogo e exatamente o tipo de coisa que alguem manda
 * por mensagem — "olha os shows deste fim de semana" — e com estado local o link chegaria sem
 * filtro nenhum do outro lado.
 *
 * <p>A busca textual e a excecao parcial: o que esta sendo DIGITADO fica em estado local, e so
 * vai para a URL ao submeter. Sincronizar a cada tecla encheria o historico de navegacao de uma
 * entrada por letra, e o botao "voltar" precisaria de vinte cliques para sair da tela.
 */
export function Explorar() {
  const [parametros, definirParametros] = useSearchParams()

  const busca = parametros.get('busca') ?? ''
  const categoria = (parametros.get('categoria') ?? '') as CategoriaDoEvento | ''
  const atalho = (parametros.get('quando') ?? 'proximos') as Atalho
  const pagina = Number(parametros.get('pagina') ?? '0')

  const [digitando, setDigitando] = useState(busca)

  const intervalo = intervaloDe(ATALHOS.includes(atalho) ? atalho : 'proximos')

  const consulta = useQuery({
    queryKey: ['eventos', 'explorar', pagina, busca, categoria, atalho],
    queryFn: () =>
      listarPublicados({
        page: pagina,
        size: POR_PAGINA,
        busca: busca || undefined,
        categoria: categoria || undefined,
        de: intervalo.de,
        ate: intervalo.ate,
      }),
  })

  const eventos = consulta.data?.content ?? []
  const disponibilidades = useDisponibilidades(eventos.map((e) => e.id))

  /**
   * Reescreve a URL preservando o que nao mudou.
   *
   * <p>Zera a pagina em qualquer troca de filtro. Sem isso, quem estivesse na pagina tres e
   * filtrasse por uma categoria com dois eventos cairia numa pagina vazia, achando que o filtro
   * nao encontrou nada.
   */
  function ajustar(mudanca: Record<string, string>) {
    const proximos = new URLSearchParams(parametros)

    for (const [chave, valor] of Object.entries(mudanca)) {
      if (valor) {
        proximos.set(chave, valor)
      } else {
        proximos.delete(chave)
      }
    }
    if (!('pagina' in mudanca)) {
      proximos.delete('pagina')
    }

    definirParametros(proximos)
  }

  return (
    <div className="mx-auto max-w-6xl px-4 py-10">
      <h1 className="text-3xl font-semibold tracking-tight">Explorar eventos</h1>

      <form
        onSubmit={(e) => {
          e.preventDefault()
          ajustar({ busca: digitando })
        }}
        className="mt-6 flex max-w-xl gap-2"
      >
        <input
          type="search"
          aria-label="Buscar eventos, artistas ou locais"
          value={digitando}
          onChange={(e) => setDigitando(e.target.value)}
          placeholder="Busque por eventos, artistas ou locais..."
          className="min-w-0 flex-1 rounded-cartao border border-borda-forte bg-superficie px-4 py-2.5 text-sm outline-none transition-colors focus:border-marca"
        />
        <button className="shrink-0 rounded-cartao bg-marca px-5 py-2.5 text-sm font-medium text-superficie transition-colors hover:bg-marca-forte">
          Buscar
        </button>
      </form>

      {/* `role=group` com nome: sem ele, um leitor de tela anuncia sete botoes soltos sem dizer
          do que sao as opcoes. */}
      <div role="group" aria-label="Filtrar por data" className="mt-8 flex flex-wrap gap-2">
        {ATALHOS.map((opcao) => (
          <button
            key={opcao}
            type="button"
            aria-pressed={atalho === opcao}
            onClick={() => ajustar({ quando: opcao === 'proximos' ? '' : opcao })}
            className={`rounded-full border px-4 py-1.5 text-sm transition-colors ${
              atalho === opcao
                ? 'border-marca bg-marca text-superficie'
                : 'border-borda-forte text-suave hover:border-marca hover:text-marca'
            }`}
          >
            {ROTULOS_DE_ATALHO[opcao]}
          </button>
        ))}
      </div>

      <div role="group" aria-label="Filtrar por categoria" className="mt-3 flex flex-wrap gap-2">
        <button
          type="button"
          aria-pressed={categoria === ''}
          onClick={() => ajustar({ categoria: '' })}
          className={`rounded-full border px-4 py-1.5 text-sm transition-colors ${
            categoria === ''
              ? 'border-marca bg-marca text-superficie'
              : 'border-borda-forte text-suave hover:border-marca hover:text-marca'
          }`}
        >
          Todas
        </button>

        {CATEGORIAS.map((opcao) => (
          <button
            key={opcao}
            type="button"
            aria-pressed={categoria === opcao}
            onClick={() => ajustar({ categoria: opcao })}
            className={`rounded-full border px-4 py-1.5 text-sm transition-colors ${
              categoria === opcao
                ? 'border-marca bg-marca text-superficie'
                : 'border-borda-forte text-suave hover:border-marca hover:text-marca'
            }`}
          >
            {ROTULOS_DE_CATEGORIA[opcao]}
          </button>
        ))}
      </div>

      <div className="mt-10">
        {consulta.isPending && <EsqueletoDeCartoes quantos={POR_PAGINA} />}
        {consulta.isError && <Erro erro={consulta.error} />}

        {consulta.data &&
          (eventos.length === 0 ? (
            <Vazio>
              {busca
                ? `Nenhum evento encontrado para "${busca}".`
                : 'Nenhum evento para estes filtros.'}
            </Vazio>
          ) : (
            <div className="grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
              {eventos.map((evento, indice) => (
                <CartaoDeEvento
                  key={evento.id}
                  evento={evento}
                  disponibilidade={disponibilidades.get(evento.id)}
                  atrasoDaAnimacao={Math.min(indice, 6) * 60}
                />
              ))}
            </div>
          ))}

        {consulta.data && (
          <Paginacao
            pagina={consulta.data.page}
            totalDePaginas={consulta.data.totalPages}
            aoMudar={(nova) => ajustar({ pagina: String(nova) })}
          />
        )}
      </div>
    </div>
  )
}
