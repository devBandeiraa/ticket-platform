import { useState } from 'react'

/**
 * Capa do evento, com um fundo proprio para quando nao houver imagem.
 *
 * O fundo nao e um cinza generico: a matiz vem do nome do evento, entao cada card sem capa
 * ganha uma cor estavel e diferente da do vizinho. Um placeholder unico repetido pela grade
 * inteira parece defeito; cores distintas parecem escolha.
 *
 * O mesmo fundo cobre dois casos que o usuario nao distingue e nao deveria distinguir: evento
 * sem capa cadastrada e capa que nao carregou. O segundo importa mais do que parece — as capas
 * do seed vem de um CDN externo, e uma tela offline mostraria onze retangulos quebrados no
 * lugar do catalogo.
 */

/**
 * Matiz derivada do nome. Hash simples de proposito: nao ha nada a proteger aqui, so a
 * necessidade de que o mesmo nome caia sempre na mesma cor.
 */
function matizDe(nome: string): number {
  let acumulado = 0
  for (let i = 0; i < nome.length; i++) {
    acumulado = (acumulado * 31 + nome.charCodeAt(i)) % 360
  }
  return acumulado
}

export function Capa({
  nome,
  url,
  className = '',
  prioridade = false,
}: {
  nome: string
  url?: string | null
  className?: string
  /** Capa acima da dobra, como a do detalhe: carrega junto com a pagina em vez de sob demanda. */
  prioridade?: boolean
}) {
  // Guarda QUAL url falhou, e nao um booleano. Com um booleano, corrigir um endereco errado no
  // formulario do admin nao traria a imagem de volta: o estado continuaria marcado como falho e
  // a previa ficaria presa no fundo derivado do nome — justamente na tela onde se digita e se
  // corrige uma URL. Comparando com a url atual, trocar de endereco ja e a propria reposicao.
  const [urlQueFalhou, setUrlQueFalhou] = useState<string | null>(null)
  const temImagem = Boolean(url) && url !== urlQueFalhou

  const matiz = matizDe(nome)
  const fundo = `linear-gradient(135deg, oklch(0.42 0.12 ${matiz}), oklch(0.22 0.05 ${(matiz + 45) % 360}))`

  return (
    <div
      className={`relative overflow-hidden bg-superficie ${className}`}
      style={temImagem ? undefined : { background: fundo }}
    >
      {temImagem ? (
        <img
          src={url ?? ''}
          // Vazio de proposito: o nome do evento aparece como texto logo ao lado, e um leitor
          // de tela que lesse os dois ouviria a mesma coisa duas vezes. A capa e decoracao.
          alt=""
          loading={prioridade ? 'eager' : 'lazy'}
          onError={() => setUrlQueFalhou(url ?? null)}
          className="size-full object-cover transition-transform duration-500 group-hover:scale-105"
        />
      ) : (
        // Sem imagem, a inicial do evento ancora o bloco de cor e evita que ele pareca vazio.
        <span
          aria-hidden="true"
          className="absolute inset-0 flex items-center justify-center text-4xl font-semibold text-white/25"
        >
          {nome.trim().charAt(0).toUpperCase()}
        </span>
      )}
    </div>
  )
}
