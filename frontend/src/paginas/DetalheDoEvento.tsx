import { useEffect, useRef, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { buscarEvento, consultarDisponibilidade } from '../api/eventos'
import { buscarMapa } from '../api/assentos'
import { reservar } from '../api/reservas'
import { ErroDaApi } from '../api/cliente'
import type { AssentoDoMapa, FaixaDeSetor } from '../api/tipos'
import { useSessao } from '../auth/SessaoContext'
import { Capa } from '../componentes/Capa'
import { Carregando, Erro, mensagemDe } from '../componentes/Estados'
import { FaixaNoite } from '../componentes/Layout'
import { MapaDeAssentos } from '../componentes/MapaDeAssentos'
import { SeletorDeSetor } from '../componentes/SeletorDeSetor'
import { ROTULOS_DE_CATEGORIA } from '../componentes/categorias'
import { definirQuantidadeNoSetor, reconciliar, somar } from '../componentes/selecaoDeAssentos'
import { Botao, Cartao } from '../componentes/Ui'
import { dataEHora, dinheiro } from '../componentes/formato'

/** Teto por reserva. O mesmo do backend — passar disso recebe 400. */
const MAXIMO_DE_ASSENTOS = 10

export function DetalheDoEvento() {
  const { id = '' } = useParams()
  const { usuario } = useSessao()
  const navegar = useNavigate()
  const queryClient = useQueryClient()

  /*
    O que o usuario CLICOU, e nao o que esta reservavel.

    A distincao importa: um lugar escolhido pode ser vendido enquanto a pessoa decide. Guardar
    o objeto do assento deixaria a tela com uma copia que envelhece; guardando so a intencao, o
    que vale e sempre recalculado do mapa recem-chegado.
  */
  const [intencao, setIntencao] = useState<Set<string>>(new Set())
  const [limiteAtingido, setLimiteAtingido] = useState(false)

  const evento = useQuery({ queryKey: ['evento', id], queryFn: () => buscarEvento(id) })

  const mapa = useQuery({
    queryKey: ['mapa', id],
    queryFn: () => buscarMapa(id),
    // O mapa muda por ação de outras pessoas, e não por nada que este usuário faça. Sem
    // atualizar sozinho, a tela ofereceria lugares que já saíram.
    refetchInterval: 10_000,
  })

  const disponibilidade = useQuery({
    queryKey: ['disponibilidade', id],
    queryFn: () => consultarDisponibilidade(id),
    refetchInterval: 10_000,
  })

  /*
    Derivados do mapa, e nao guardados.

    `selecionados` sao os lugares que o usuario clicou E que continuam livres; `perdidos`, os
    que sairam enquanto ele decidia. Calcular a cada render, em vez de corrigir a selecao num
    efeito, elimina a renderizacao em cascata — e, mais importante, torna impossivel a tela
    exibir como escolhido um assento que o servidor ja recusaria.
  */
  const assentos = mapa.data?.seats ?? []
  const { selecionados, perdidos } = reconciliar(assentos, intencao)

  /*
    Uma chave de idempotencia por intencao de compra.

    Fixa enquanto a intencao nao muda: se a resposta se perder no caminho e o usuario clicar de
    novo, a mesma chave devolve a reserva que ja existe, em vez de criar a segunda.
  */
  const escolha = selecionados.map((a) => a.seatId).sort().join(',')
  const chave = useRef(crypto.randomUUID())
  useEffect(() => {
    chave.current = crypto.randomUUID()
  }, [escolha])

  const reserva = useMutation({
    mutationFn: () => reservar(id, selecionados.map((a) => a.seatId), chave.current),
    onSuccess: (criada) => {
      queryClient.invalidateQueries({ queryKey: ['disponibilidade', id] })
      queryClient.invalidateQueries({ queryKey: ['mapa', id] })
      queryClient.invalidateQueries({ queryKey: ['meus-ingressos'] })
      // Segue direto para o checkout. A reserva ja esta segurando os lugares com prazo, e
      // mandar a pessoa a uma lista para so entao pagar gasta parte desse prazo em navegacao.
      navegar(`/checkout/${criada.id}`)
    },
    onError: (erro) => {
      // O servidor recusou porque algum lugar saiu no instante do envio. Recarregar o mapa é
      // o que resolve: a seleção é derivada dele, então os perdidos somem sozinhos e os que
      // sobraram continuam marcados — a pessoa não recomeça a escolha por causa de um assento.
      if (erro instanceof ErroDaApi && erro.codigo === 'SEATS_TAKEN') {
        queryClient.invalidateQueries({ queryKey: ['mapa', id] })
      }
    },
  })

  const total = somar(selecionados)

  function alternar(assento: AssentoDoMapa) {
    setLimiteAtingido(false)
    setIntencao((atual) => {
      const proxima = new Set(atual)
      if (proxima.has(assento.seatId)) {
        proxima.delete(assento.seatId)
        return proxima
      }
      if (selecionados.length >= MAXIMO_DE_ASSENTOS) {
        setLimiteAtingido(true)
        return atual
      }
      proxima.add(assento.seatId)
      return proxima
    })
  }

  function definirQuantidade(setor: string, quantidade: number) {
    setLimiteAtingido(false)
    setIntencao(
      definirQuantidadeNoSetor(assentos, intencao, setor, quantidade, MAXIMO_DE_ASSENTOS),
    )
  }

  /** A mensagem vem do estado derivado: nao ha aviso guardado que possa contradizer o mapa. */
  const aviso = limiteAtingido
    ? `São no máximo ${MAXIMO_DE_ASSENTOS} lugares por reserva.`
    : perdidos.length === 1
      ? `O lugar ${perdidos[0].label} acabou de ser vendido. Escolha outro.`
      : perdidos.length > 1
        ? `Estes lugares foram vendidos: ${perdidos.map((a) => a.label).join(', ')}. Escolha outros.`
        : null

  if (evento.isPending) return <Carregando />
  if (evento.isError) return <Erro erro={evento.error} />

  const restam = disponibilidade.data?.available
  const esgotado = restam !== undefined && restam <= 0

  /*
    A faixa de cada setor vem do event-service; o estado de cada lugar, do booking-service. Os
    dois se encontram aqui, casados pelo NOME do setor — a chave natural do assento, conforme a
    migration `V3`.
  */
  const faixaPorSetor = new Map<string, FaixaDeSetor>(
    evento.data.sectors.map((setor) => [setor.name, setor.tier]),
  )

  return (
    <>
      {/* Hero: a capa ocupa a faixa inteira e o texto vive sobre ela. */}
      <FaixaNoite>
        <div className="relative isolate">
          {/* A capa vai DENTRO de um posicionador, e nao posicionada por className: `Capa` traz
              `relative` no proprio componente, e utilitario de posicao nao se sobrescreve pela
              ordem na string — quem vence depende da ordem no CSS gerado. Posicionada por fora,
              ela preenche a faixa; posicionada por dentro, entrava no fluxo com a altura
              natural da imagem e empurrava o hero para mil pixels. */}
          <div aria-hidden="true" className="absolute inset-0 -z-10">
            <Capa
              nome={evento.data.name}
              url={evento.data.imageUrl}
              prioridade
              className="size-full opacity-40"
            />
            {/* Véu que escurece a capa de baixo para cima. Sem ele, o texto cai sobre a parte
                clara de uma foto e o contraste vira sorte — depende da imagem que subiram. */}
            <div className="absolute inset-0 bg-gradient-to-t from-noite via-noite/85 to-noite/50" />
          </div>

          <div className="mx-auto max-w-6xl px-4 py-14 sm:py-20">
            {/* `block` porque o link e inline: sem ele a etiqueta de categoria sobe para a
                mesma linha e encosta no "voltar", e o `mt-6` nao tem efeito nenhum. */}
            <Link
              to="/explorar"
              className="block w-fit text-sm text-noite-suave hover:text-noite-texto"
            >
              &larr; voltar para a busca
            </Link>

            {ROTULOS_DE_CATEGORIA[evento.data.category] && (
              <span className="mt-6 flex w-fit rounded-full border border-noite-borda-forte px-3 py-1 text-xs font-medium uppercase tracking-wide text-noite-suave">
                {ROTULOS_DE_CATEGORIA[evento.data.category]}
              </span>
            )}

            <h1 className="mt-4 max-w-3xl text-balance text-3xl font-semibold leading-tight tracking-tight sm:text-5xl">
              {evento.data.name}
            </h1>

            <p className="numerico mt-4 text-noite-suave">
              {evento.data.venue} · {dataEHora(evento.data.eventDate)}
            </p>
          </div>
        </div>
      </FaixaNoite>

      <div className="mx-auto grid max-w-6xl gap-8 px-4 py-10 lg:grid-cols-[1fr_21rem]">
        <div className="min-w-0">
          {evento.data.description && (
            <p className="whitespace-pre-line leading-relaxed text-suave">
              {evento.data.description}
            </p>
          )}

          <dl className="mt-8 grid gap-4 border-y border-borda py-6 sm:grid-cols-3">
            <Informacao rotulo="Data e horario" valor={dataEHora(evento.data.eventDate)} />
            <Informacao rotulo="Local" valor={evento.data.venue} />
            <Informacao
              rotulo="Ingressos"
              valor={
                disponibilidade.data
                  ? `${disponibilidade.data.available} de ${disponibilidade.data.total}`
                  : '—'
              }
            />
          </dl>

          {mapa.data && evento.data.sectors.length > 0 && (
            <section className="mt-10">
              <h2 className="text-lg font-semibold">Escolha o setor</h2>
              <p className="mt-1 text-sm text-suave">
                O contador escolhe os lugares mais baratos do setor. Ajuste no mapa abaixo se
                quiser outros.
              </p>

              <div className="mt-5">
                <SeletorDeSetor
                  setores={evento.data.sectors}
                  assentos={assentos}
                  selecionados={new Set(selecionados.map((a) => a.seatId))}
                  aoDefinirQuantidade={definirQuantidade}
                  maximoRestante={MAXIMO_DE_ASSENTOS - selecionados.length}
                />
              </div>
            </section>
          )}

          <section className="mt-10">
            <h2 className="text-lg font-semibold">Escolha os lugares</h2>

            <div className="mt-5">
              {mapa.isPending && <Carregando texto="Carregando o mapa da casa..." />}
              {mapa.isError && <Erro erro={mapa.error} />}

              {mapa.data && (
                <MapaDeAssentos
                  assentos={assentos}
                  selecionados={new Set(selecionados.map((a) => a.seatId))}
                  aoAlternar={alternar}
                  faixaPorSetor={faixaPorSetor}
                />
              )}
            </div>
          </section>
        </div>

        <Cartao className="h-fit lg:sticky lg:top-20">
          {/* Com mais de um setor, `price` e o MENOR deles. Exibi-lo sem a ressalva faria a tela
              anunciar como preco do evento o valor do setor mais barato. */}
          {evento.data.sectors.length > 1 && <p className="text-xs text-suave">a partir de</p>}
          <p className="numerico text-2xl font-semibold text-marca">
            {dinheiro(evento.data.price)}
          </p>

          <p className="mt-2 text-sm text-suave">
            {disponibilidade.isPending ? (
              'consultando disponibilidade...'
            ) : disponibilidade.isError ? (
              'disponibilidade indisponivel no momento'
            ) : (
              <>
                <span className="numerico text-texto">{restam}</span> de{' '}
                <span className="numerico">{disponibilidade.data?.total}</span> lugares
              </>
            )}
          </p>

          {/* role=status faz o leitor de tela anunciar a perda de um lugar sem roubar o foco de
              quem está navegando o mapa pelo teclado. */}
          {aviso && (
            <p role="status" className="mt-4 rounded-cartao bg-erro/10 px-3 py-2 text-sm text-erro">
              {aviso}
            </p>
          )}

          {!usuario ? (
            <div className="mt-5">
              <p className="mb-3 text-sm text-suave">Entre na sua conta para reservar.</p>
              <Link
                to="/login"
                state={{ de: `/eventos/${id}` }}
                className="block rounded-cartao bg-marca px-4 py-2 text-center text-sm font-medium text-superficie transition-colors hover:bg-marca-forte"
              >
                Entrar
              </Link>
            </div>
          ) : esgotado ? (
            <p className="mt-5 rounded-cartao bg-erro/10 px-3 py-2 text-sm text-erro">
              Todos os lugares foram vendidos.
            </p>
          ) : (
            <div className="mt-5 space-y-3">
              <div>
                <h3 className="text-sm text-suave">Selecionados</h3>
                {selecionados.length === 0 ? (
                  <p className="mt-1 text-sm text-suave">
                    Nenhum ainda — use o contador do setor ou toque num lugar do mapa.
                  </p>
                ) : (
                  <ul className="mt-2 space-y-1">
                    {selecionados.map((assento) => (
                      <li
                        key={assento.seatId}
                        className="flex items-baseline justify-between gap-2 text-sm"
                      >
                        <span>{assento.label}</span>
                        <span className="numerico text-suave">{dinheiro(assento.price)}</span>
                      </li>
                    ))}
                  </ul>
                )}
              </div>

              <p className="numerico border-t border-borda pt-3 text-sm">
                Subtotal: <span className="font-semibold">{dinheiro(total)}</span>
              </p>

              <Botao
                className="w-full"
                disabled={reserva.isPending || selecionados.length === 0}
                onClick={() => reserva.mutate()}
              >
                {reserva.isPending ? 'Reservando...' : 'Continuar'}
              </Botao>

              {reserva.isError && (
                <p className="rounded-cartao bg-erro/10 px-3 py-2 text-sm text-erro">
                  {mensagemDe(reserva.error)}
                </p>
              )}

              {/* A taxa so entra no checkout, porque e o servidor quem a calcula e grava. Dizer
                  aqui que o subtotal e o total seria prometer um valor que a proxima tela
                  desmente. */}
              <p className="text-xs text-suave">
                A taxa de servico aparece no checkout. Os lugares ficam segurados por tempo
                limitado depois de continuar.
              </p>
            </div>
          )}
        </Cartao>
      </div>
    </>
  )
}

function Informacao({ rotulo, valor }: { rotulo: string; valor: string }) {
  return (
    <div>
      <dt className="text-xs uppercase tracking-wide text-suave">{rotulo}</dt>
      <dd className="mt-1 text-sm">{valor}</dd>
    </div>
  )
}
