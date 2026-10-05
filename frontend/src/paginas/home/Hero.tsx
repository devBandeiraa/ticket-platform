import { Link } from 'react-router-dom'
import type { Disponibilidade, EventoResumo } from '../../api/tipos'
import { Capa } from '../../componentes/Capa'
import { Esqueleto } from '../../componentes/Estados'
import { IconeSeta } from '../../componentes/Icones'
import { mesEAno, partesDaData } from '../../componentes/formato'

/**
 * Cartao do evento em destaque — a "escolha da edicao".
 *
 * <h2>Por que o proximo evento, e nao um cartaz fixo</h2>
 *
 * <p>O hero mostrava um ingresso de vitrine com dados inventados, e a razao registrada era boa:
 * puxar o primeiro do catalogo faria um evento esgotado aparecer como cartaz da plataforma.
 *
 * <p>O design decidiu a outra ponta, e com um argumento melhor: o bloco tem data, casa, numero de
 * lugares e um "ver evento" que leva a algum lugar. Um cartaz com dados falsos e um botao que
 * nao abre nada e pior do que um evento esgotado — e o risco original encolheu, porque o catalogo
 * publico so devolve evento PUBLICADO e a lista chega em data crescente. O que aparece aqui e,
 * literalmente, a proxima coisa que acontece na cidade.
 *
 * <p>O ingresso de vitrine nao se perdeu: foi para a secao "do encontro ao ingresso", onde ele
 * ilustra o que se recebe no fim da compra.
 *
 * <h2>Claro sobre a foto</h2>
 *
 * <p>O cartao e papel, e nao vidro escuro, e por isso ele cavalga a borda da imagem: e a peca que
 * o olho precisa encontrar primeiro depois do titulo. Sobre foto, um bloco translucido depende do
 * que estiver atras — e o que esta atras muda a cada evento publicado.
 */
function EventoEmDestaque({
  evento,
  disponibilidade,
}: {
  evento: EventoResumo
  disponibilidade?: Disponibilidade
}) {
  const { dia, mes, semana, hora } = partesDaData(evento.eventDate)

  return (
    <article className="rounded-cartao bg-papel shadow-2xl">
      <div className="flex items-stretch">
        {/*
          Bloco de data. `aria-hidden` no conjunto, e nao em cada parte: lido em voz alta, isto
          sairia como "OUT dezoito DOM 19H" — quatro fragmentos sem pontuacao. A data legivel
          esta no `<time>` ao lado, e e aquela que o leitor de tela anuncia.
        */}
        <div
          aria-hidden="true"
          className="flex shrink-0 flex-col items-center justify-center border-r border-borda px-5 py-6 text-center"
        >
          <span className="rotulo text-marca-forte">{mes}</span>
          <span className="font-mono text-4xl font-semibold leading-none tracking-tight">
            {dia}
          </span>
          <span className="rotulo mt-1 text-suave">
            {semana} · {hora}
          </span>
        </div>

        <div className="min-w-0 flex-1 px-6 py-6">
          <h2 className="text-pretty text-xl font-semibold leading-snug">{evento.name}</h2>

          <p className="mt-2 text-sm text-suave">{evento.venue}</p>

          <time dateTime={evento.eventDate} className="sr-only">
            {dia} de {mesEAno(evento.eventDate).toLowerCase()}, {hora}
          </time>

          {disponibilidade && (
            <p className="rotulo mt-3 text-marca-forte">
              {disponibilidade.total} lugares · {disponibilidade.available} disponíveis
            </p>
          )}
        </div>

        <div className="flex shrink-0 items-center pr-6">
          <Link
            to={`/eventos/${evento.id}`}
            className="group inline-flex items-center gap-1.5 whitespace-nowrap text-sm font-medium transition-colors hover:text-marca-forte"
          >
            Ver evento
            <IconeSeta
              tamanho={14}
              className="transition-transform duration-200 group-hover:-translate-y-0.5 group-hover:translate-x-0.5"
            />
          </Link>
        </div>
      </div>
    </article>
  )
}

/** Esqueleto no formato do cartao, para a faixa nao nascer e depois empurrar o hero. */
function EsqueletoDoDestaque() {
  return (
    <div className="rounded-cartao bg-papel p-6 shadow-2xl">
      <div className="flex items-center gap-6">
        <Esqueleto className="h-16 w-14 shrink-0" />
        <div className="min-w-0 flex-1">
          <Esqueleto className="h-5 w-3/4" />
          <Esqueleto className="mt-2.5 h-4 w-1/2" />
          <Esqueleto className="mt-3 h-3 w-2/5" />
        </div>
      </div>
    </div>
  )
}

/**
 * Hero da home.
 *
 * <h2>A foto e do evento em destaque</h2>
 *
 * <p>Nao e uma imagem de banco presa no codigo: e a capa do proprio evento que o cartao anuncia.
 * As duas pecas falam da mesma noite, e o hero deixa de ser decoracao para virar a primeira
 * oferta do catalogo. Sem capa — ou sem evento —, o degrade escuro ocupa a faixa inteira e o
 * titulo continua de pe.
 *
 * <h2>O degrade existe para o texto, nao para a foto</h2>
 *
 * <p>O titulo e branco e cai sobre a borda esquerda da imagem. Qualquer foto clara ali o apagaria,
 * e as capas vem de um catalogo que muda. O degrade de `noite` opaco ate transparente garante
 * contraste na coluna do texto seja qual for a imagem — e e por isso que ele vai ate 70%, bem
 * depois de onde o texto termina.
 */
export function Hero({
  evento,
  disponibilidade,
  carregando,
}: {
  evento?: EventoResumo
  disponibilidade?: Disponibilidade
  carregando: boolean
}) {
  return (
    <>
      <section className="faixa-noite relative isolate overflow-hidden pb-24 lg:pb-32">
      {/* A foto, sangrando a direita. */}
      {evento && (
        <div className="absolute inset-y-0 right-0 -z-20 w-full lg:w-[58%]">
          <Capa nome={evento.name} url={evento.imageUrl} prioridade className="size-full" />
        </div>
      )}

      {/*
        Dois degrades, e nao um. O horizontal protege a coluna do texto; o vertical escurece o pe
        da imagem, que e onde o cartao claro encosta — sem ele, uma foto clara embaixo faz o
        cartao sumir dentro dela.
      */}
      <div
        aria-hidden="true"
        className="absolute inset-0 -z-10"
        style={{
          background:
            'linear-gradient(90deg, var(--color-noite) 0%, var(--color-noite) 38%, color-mix(in oklch, var(--color-noite) 55%, transparent) 58%, transparent 78%),' +
            'linear-gradient(0deg, color-mix(in oklch, var(--color-noite) 70%, transparent) 0%, transparent 45%)',
        }}
      />

      <div className="mx-auto max-w-6xl px-4 pb-14 pt-20 lg:pb-20 lg:pt-28">
        <div className="max-w-xl">
          <p className="rotulo text-noite-suave">Para sair do óbvio. E de casa.</p>

          <h1 className="mt-6 text-balance text-5xl font-semibold leading-[1.04] tracking-tight sm:text-6xl">
            A cidade tem muito a dizer. Vá ouvir.
          </h1>

          <p className="mt-7 max-w-md text-pretty text-noite-suave">
            Do palco intimista à pista cheia. Encontre seu próximo encontro com a cultura.
          </p>

          <Link
            to="/explorar"
            className="group mt-9 inline-flex items-center gap-2.5 rounded-cartao bg-marca px-6 py-3.5 text-sm font-semibold text-white transition-colors hover:bg-marca-forte active:scale-[0.98]"
          >
            Explorar eventos
            <IconeSeta
              tamanho={16}
              className="transition-transform duration-200 group-hover:-translate-y-0.5 group-hover:translate-x-0.5"
            />
          </Link>

          {/* A regua laranja antes do periodo, como no design: ela ancora a linha no rodape do
              bloco de texto e repete a marca sem precisar de mais uma palavra colorida. */}
          <p className="mt-12 flex items-center gap-4">
            <span aria-hidden="true" className="h-px w-10 shrink-0 bg-marca-clara" />
            <span className="rotulo text-noite-suave">Em cartaz / {mesEAno(new Date())}</span>
          </p>
        </div>
      </div>

      </section>

      {/*
        O cartao sobreposto, FORA da faixa escura.

        Dentro dela, ele nasceria escuro sem ninguem pedir: `.faixa-noite` reatribui
        `--color-papel` e `--color-texto` para a familia da noite em todo o seu interior, entao
        um `bg-papel` ali dentro resolve para a cor da noite. O design quer o oposto — papel
        claro cavalgando a borda da imagem.

        Tirado da faixa, ele volta a enxergar a familia clara, e a margem negativa e o que o faz
        subir por cima dela. Fica no fluxo, e nao em `absolute`: assim ele EMPURRA a secao
        seguinte quando o nome do evento ocupa duas linhas — em `absolute` cobriria a busca
        justamente nos eventos de titulo longo.
      */}
      {(carregando || evento) && (
        <div className="relative z-10 mx-auto -mt-16 max-w-6xl px-4 lg:-mt-24 lg:flex lg:justify-end">
          <div className="lg:w-[62%]">
            {evento ? (
              <EventoEmDestaque evento={evento} disponibilidade={disponibilidade} />
            ) : (
              <EsqueletoDoDestaque />
            )}
          </div>
        </div>
      )}
    </>
  )
}
