import type { ReactNode } from 'react'
import { Link } from 'react-router-dom'

/*
  Pecas da linguagem editorial do design.

  ----------------------------------------------------------------------------
   O padrao que se repete
  ----------------------------------------------------------------------------
  Toda secao da home segue a mesma estrutura de tres partes: um sobretitulo curto em versalete
  espacado ("EM CARTAZ / OUTUBRO 2026"), um titulo grande em corpo de display ("Qual vai ser o
  seu proximo programa?") e, as vezes, uma linha de apoio. Sao oito secoes com a mesma anatomia.

  Escritas como utilitarios repetidos, oito vezes `text-[0.7rem] uppercase tracking-[0.18em]`, a
  primeira que divergisse por um caractere desalinharia a coluna inteira — e ninguem abre oito
  secoes lado a lado para conferir. Aqui a escala tipografica da pagina e uma decisao, num lugar.

  ----------------------------------------------------------------------------
   A seta diagonal
  ----------------------------------------------------------------------------
  O design marca com uma seta diagonal os links que LEVAM a outro lugar — "Ver evento",
  "Conheca o Quintal do jazz", "Codigo no GitHub" — e a deixa de fora dos botoes de acao. E um
  sinal de saida, nao um enfeite: por isso vem de um componente que tambem sabe a diferenca
  entre rota interna e endereco externo, em vez de um "↗" digitado no fim de cada texto.
*/

/**
 * Sobretitulo de secao: versalete curto, espacado, acima do titulo.
 *
 * <p>Nao e um titulo de documento, e por isso sai num `p` e nao num `h*`. Um leitor de tela que
 * anunciasse "EM CARTAZ / OUTUBRO 2026" como cabecalho de nivel 3, seguido do `h2` de verdade,
 * entregaria dois titulos para uma secao — e o sumario da pagina ficaria com o dobro de linhas,
 * metade delas sem conteudo proprio.
 */
export function Sobretitulo({
  children,
  className = '',
}: {
  children: ReactNode
  className?: string
}) {
  return (
    <p
      className={`text-[0.7rem] font-semibold uppercase tracking-[0.18em] text-suave ${className}`}
    >
      {children}
    </p>
  )
}

/**
 * Titulo de secao em corpo de display.
 *
 * <p>`text-balance` para o titulo nao quebrar com uma palavra orfa na ultima linha — os titulos
 * do design tem tres a seis palavras, que e exatamente a faixa em que o orfao acontece.
 */
export function TituloDeSecao({
  children,
  nivel = 'h2',
  className = '',
}: {
  children: ReactNode
  /** `h1` so na primeira secao da pagina. */
  nivel?: 'h1' | 'h2'
  className?: string
}) {
  const Titulo = nivel

  return (
    <Titulo
      className={`text-balance text-3xl font-semibold leading-[1.1] tracking-tight sm:text-4xl ${className}`}
    >
      {children}
    </Titulo>
  )
}

/** Linha de apoio sob o titulo. Largura limitada: linha longa demais atrapalha a leitura. */
export function LinhaDeApoio({
  children,
  className = '',
}: {
  children: ReactNode
  className?: string
}) {
  return <p className={`max-w-2xl text-pretty text-suave ${className}`}>{children}</p>
}

/**
 * Cabecalho de secao — as tres partes juntas, que e como o design as usa.
 *
 * <p>Existe alem das tres pecas soltas porque a relacao ENTRE elas tambem e uma decisao: o
 * espaco entre sobretitulo e titulo e menor que o entre titulo e apoio, e com as pecas soltas
 * cada secao escolheria o seu e a pagina perderia o ritmo vertical.
 */
export function CabecalhoDeSecao({
  sobretitulo,
  titulo,
  apoio,
  nivel = 'h2',
  className = '',
}: {
  sobretitulo?: ReactNode
  titulo: ReactNode
  apoio?: ReactNode
  nivel?: 'h1' | 'h2'
  className?: string
}) {
  return (
    <div className={className}>
      {sobretitulo && <Sobretitulo className="mb-3">{sobretitulo}</Sobretitulo>}
      <TituloDeSecao nivel={nivel}>{titulo}</TituloDeSecao>
      {apoio && <LinhaDeApoio className="mt-4">{apoio}</LinhaDeApoio>}
    </div>
  )
}

/**
 * Seta de saida.
 *
 * <p>`aria-hidden` porque ela nao se le: um leitor de tela anunciaria "seta para nordeste" no
 * fim de cada link, e o texto do link ja diz para onde vai. Quem precisa saber que o destino e
 * externo recebe isso pelo `rel`/`target`, nao pelo glifo.
 */
function Seta({ className = '' }: { className?: string }) {
  return (
    <span
      aria-hidden="true"
      className={`transition-transform duration-200 group-hover:-translate-y-0.5 group-hover:translate-x-0.5 ${className}`}
    >
      &#8599;
    </span>
  )
}

/**
 * Link com seta de saida, interno ou externo.
 *
 * <p>Decide o elemento pelo endereco: `http` vira `<a>` com `target` e `rel`, qualquer outra
 * coisa vira `<Link>` do roteador. Sem isso, uma rota interna passada por engano a um `<a>`
 * recarregaria a aplicacao inteira — defeito que nao aparece em teste nenhum e so se nota pelo
 * piscar da tela.
 *
 * <p>`noopener` junto de `target="_blank"`: sem ele a pagina aberta recebe uma referencia a
 * esta pela `window.opener`.
 */
export function LinkDeSaida({
  para,
  children,
  className = '',
}: {
  para: string
  children: ReactNode
  className?: string
}) {
  const estilo = `group inline-flex items-center gap-1.5 text-sm font-medium transition-colors hover:text-marca ${className}`
  const externo = para.startsWith('http')

  if (externo) {
    return (
      <a href={para} target="_blank" rel="noopener noreferrer" className={estilo}>
        {children}
        <Seta />
      </a>
    )
  }

  return (
    <Link to={para} className={estilo}>
      {children}
      <Seta />
    </Link>
  )
}

/**
 * Faixa de conteudo de uma secao da home.
 *
 * <p>Diferente de `Secao` do `Ui.tsx`, que e o container das paginas comuns: esta nasce para a
 * home, onde as secoes se alternam entre papel e faixa escura e o respiro vertical e maior — o
 * design trabalha com blocos que ocupam a tela, nao com uma lista de cartoes.
 *
 * <p>`scroll-mt` existe por causa do cabecalho fixo: a navegacao da home aponta para ancoras
 * nesta pagina, e sem a margem de rolagem o titulo da secao para DEBAIXO do cabecalho — a
 * pessoa clica em "Agenda", a pagina rola, e o que ela ve e o meio da secao.
 */
export function FaixaDeSecao({
  children,
  id,
  className = '',
}: {
  children: ReactNode
  id?: string
  className?: string
}) {
  return (
    <section id={id} className={`scroll-mt-24 py-16 sm:py-20 ${className}`}>
      <div className="mx-auto max-w-6xl px-4">{children}</div>
    </section>
  )
}
