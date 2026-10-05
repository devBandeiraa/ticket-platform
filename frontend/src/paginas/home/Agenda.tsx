import { Link } from 'react-router-dom'
import type { EventoResumo } from '../../api/tipos'
import { Capa } from '../../componentes/Capa'
import { CabecalhoDeSecao, FaixaDeSecao, LinkDeSaida } from '../../componentes/Editorial'
import { IconeSeta } from '../../componentes/Icones'
import { chamadaDoDia, proximoFimDeSemanaComEventos } from '../../componentes/agenda'
import { faixaDeDias, partesDaData } from '../../componentes/formato'

/** Uma linha da agenda: a data a esquerda, o evento a direita. */
function LinhaDoDia({ data, evento }: { data: Date; evento: EventoResumo }) {
  // A data do DIA da a coluna da esquerda; a do evento da a hora. Sao a mesma data civil, mas
  // `data` e meia-noite — tirar a hora dela mostraria 00H em toda linha da agenda.
  const { dia, mes, semana } = partesDaData(data)
  const { hora } = partesDaData(evento.eventDate)

  return (
    <Link
      to={`/eventos/${evento.id}`}
      className="group flex items-center gap-6 border-b border-noite-borda py-6 transition-colors last:border-b-0 hover:border-marca-clara/40"
    >
      {/* Mesma solucao do hero: o bloco de data e decorativo, e a data legivel vai no `<time>`
          ao lado. Lido parte por parte, isto sairia como tres fragmentos sem pontuacao. */}
      <div aria-hidden="true" className="flex shrink-0 items-baseline gap-2">
        <span className="font-mono text-4xl font-semibold leading-none tracking-tight text-marca-clara">
          {dia}
        </span>
        <span className="flex flex-col gap-0.5">
          <span className="rotulo leading-none text-noite-suave">{mes}</span>
          <span className="rotulo leading-none text-noite-suave">{semana}</span>
        </span>
      </div>

      <div className="min-w-0 flex-1">
        <h3 className="text-pretty text-lg font-medium leading-snug transition-colors group-hover:text-marca-clara">
          {evento.name}
        </h3>
        <p className="mt-1.5 text-sm text-noite-suave">
          <time dateTime={evento.eventDate} className="font-mono">
            {hora}
          </time>{' '}
          · {evento.venue}
        </p>
      </div>

      <IconeSeta
        tamanho={18}
        className="shrink-0 text-noite-suave transition-all duration-200 group-hover:-translate-y-0.5 group-hover:translate-x-0.5 group-hover:text-marca-clara"
      />
    </Link>
  )
}

/**
 * Painel de destaque do fim de semana.
 *
 * <p>O design escreve "SEXTA, SEM PRESSA / Um quintal. Um trio. Uma noite inteira." sobre uma
 * foto — curadoria escrita a mao para um evento especifico. O sobretitulo aqui vem do dia da
 * semana, por `chamadaDoDia`: sao tres frases, uma por dia, e cada uma continua valendo para
 * qualquer evento que caia naquele dia. Inventar a segunda linha seria por na boca da casa um
 * texto que ela nao escreveu, entao ali vao o nome e o local — o que o catalogo de fato sabe.
 *
 * <p>A foto e a capa do proprio evento. O degrade de baixo para cima e o que garante o contraste
 * do texto: a capa muda a cada publicacao, e sem ele a legibilidade dependeria do que o
 * organizador subiu.
 */
function Destaque({ data, evento }: { data: Date; evento: EventoResumo }) {
  return (
    <Link
      to={`/eventos/${evento.id}`}
      className="group relative isolate flex min-h-80 flex-col justify-end overflow-hidden rounded-cartao p-7"
    >
      <Capa
        nome={evento.name}
        url={evento.imageUrl}
        className="absolute inset-0 -z-20 size-full transition-transform duration-700 group-hover:scale-105"
      />

      <div
        aria-hidden="true"
        className="absolute inset-0 -z-10"
        style={{
          background:
            'linear-gradient(0deg, var(--color-noite) 8%, color-mix(in oklch, var(--color-noite) 55%, transparent) 50%, transparent 85%)',
        }}
      />

      <p className="rotulo text-marca-clara">{chamadaDoDia(data)}</p>

      <p className="mt-4 text-balance text-2xl font-semibold leading-tight text-noite-texto">
        {evento.name}
      </p>

      <p className="mt-2 text-sm text-noite-suave">{evento.venue}</p>

      <span className="mt-5 inline-flex items-center gap-1.5 text-sm font-medium text-noite-texto">
        Conheça este encontro
        <IconeSeta
          tamanho={14}
          className="transition-transform duration-200 group-hover:-translate-y-0.5 group-hover:translate-x-0.5"
        />
      </span>
    </Link>
  )
}

/**
 * Agenda do fim de semana.
 *
 * <p>Mostra o PROXIMO fim de semana com programacao, e nao o fim de semana que vem — a razao
 * esta em `agenda.ts`. Por isso o periodo no cabecalho e calculado e nao escrito: ele diz de que
 * fim de semana a secao esta falando, que nem sempre e o mais proximo no calendario.
 *
 * <p>Sem nenhum evento de sexta a domingo no catalogo, a secao sai inteira. Diferente da lista
 * de eventos, aqui nao ha estado vazio a desenhar: uma faixa anunciando "o fim de semana pede
 * presenca" sobre um quadro em branco nao informa nada que a ausencia nao informe melhor.
 */
export function Agenda({ eventos }: { eventos: EventoResumo[] }) {
  const fds = proximoFimDeSemanaComEventos(eventos)

  if (!fds || fds.dias.length === 0) {
    return null
  }

  const ano = fds.inicio.getFullYear()

  // O destaque e o primeiro evento do bloco. A escolha e defensavel e barata: e o que acontece
  // primeiro, entao e o que exige decisao mais cedo de quem esta olhando.
  const primeiroDia = fds.dias[0]
  const destaque = primeiroDia.eventos[0]

  return (
    <div className="faixa-noite">
      <FaixaDeSecao id="agenda">
        <div className="flex flex-wrap items-end justify-between gap-x-8 gap-y-3">
          <CabecalhoDeSecao
            sobretitulo="Reserve um espaço na sua agenda"
            titulo="O fim de semana pede presença."
          />

          <p className="rotulo pb-2 text-noite-suave">
            {faixaDeDias(fds.inicio, fds.fim)} / {ano}
          </p>
        </div>

        <div className="mt-10 grid gap-10 lg:grid-cols-[1.4fr_1fr]">
          <div>
            {fds.dias.map((dia) =>
              dia.eventos.map((evento) => (
                <LinhaDoDia key={evento.id} data={dia.data} evento={evento} />
              )),
            )}

            <LinkDeSaida para="/explorar" className="mt-8 text-marca-clara">
              Ver programação completa
            </LinkDeSaida>
          </div>

          <Destaque data={primeiroDia.data} evento={destaque} />
        </div>
      </FaixaDeSecao>
    </div>
  )
}
