import { useEffect, useRef, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { buscarEvento, consultarDisponibilidade } from '../api/eventos'
import { buscarMapa } from '../api/assentos'
import { reservar } from '../api/reservas'
import { ErroDaApi } from '../api/cliente'
import type { AssentoDoMapa } from '../api/tipos'
import { useSessao } from '../auth/SessaoContext'
import { Capa } from '../componentes/Capa'
import { Carregando, Erro, mensagemDe } from '../componentes/Estados'
import { MapaDeAssentos } from '../componentes/MapaDeAssentos'
import { reconciliar, somar } from '../componentes/selecaoDeAssentos'
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

    Efeito colateral bem-vindo: se a reserva de outra pessoa expirar e o lugar voltar a ficar
    livre, ele reaparece selecionado. A intencao do usuario nunca se perdeu; so estava
    momentaneamente irrealizavel.
  */
  const { selecionados, perdidos } = reconciliar(mapa.data?.seats ?? [], intencao)

  /*
    Uma chave de idempotencia por intencao de compra.

    Fixa enquanto a intencao nao muda: se a resposta se perder no caminho e o usuario clicar de
    novo, a mesma chave devolve a reserva que ja existe, em vez de criar a segunda. Trocar os
    lugares escolhidos e outra intencao, e por isso ganha chave nova.
  */
  const escolha = selecionados.map((a) => a.seatId).sort().join(',')
  const chave = useRef(crypto.randomUUID())
  useEffect(() => {
    chave.current = crypto.randomUUID()
  }, [escolha])

  const reserva = useMutation({
    mutationFn: () => reservar(id, selecionados.map((a) => a.seatId), chave.current),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['disponibilidade', id] })
      queryClient.invalidateQueries({ queryKey: ['mapa', id] })
      queryClient.invalidateQueries({ queryKey: ['minhas-reservas'] })
      navegar('/minhas-reservas')
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

  return (
    <div className="grid gap-6 lg:grid-cols-[1fr_20rem]">
      <div>
        <Link to="/" className="text-sm text-suave hover:text-texto">
          &larr; voltar ao catalogo
        </Link>

        {/* Prioridade: esta capa esta acima da dobra, e adiar o carregamento dela deixaria o
            topo da pagina cinza no primeiro instante — justo o oposto do que uma capa faz. */}
        <Capa
          nome={evento.data.name}
          url={evento.data.imageUrl}
          prioridade
          className="mt-3 aspect-[21/9] w-full rounded-xl border border-borda"
        />

        <h1 className="mt-5 text-2xl font-semibold">{evento.data.name}</h1>
        <p className="mt-1 text-suave">{evento.data.venue}</p>
        <p className="mt-1 text-sm">{dataEHora(evento.data.eventDate)}</p>

        {evento.data.description && (
          <p className="mt-6 whitespace-pre-line text-sm leading-relaxed text-suave">
            {evento.data.description}
          </p>
        )}

        <div className="mt-8">
          <h2 className="mb-4 text-sm font-medium text-suave">Escolha os lugares</h2>

          {mapa.isPending && <Carregando texto="Carregando o mapa da casa..." />}
          {mapa.isError && <Erro erro={mapa.error} />}

          {mapa.data && (
            <MapaDeAssentos
              assentos={mapa.data.seats}
              selecionados={new Set(selecionados.map((a) => a.seatId))}
              aoAlternar={alternar}
            />
          )}
        </div>
      </div>

      <Cartao className="h-fit lg:sticky lg:top-6">
        {/* Com mais de um setor, `price` e o MENOR deles. Exibi-lo sem a ressalva faria a tela
            anunciar como preco do evento o valor do setor mais barato. */}
        {evento.data.sectors.length > 1 && <p className="text-xs text-suave">a partir de</p>}
        <p className="numerico text-2xl font-semibold text-marca">{dinheiro(evento.data.price)}</p>

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
          <p role="status" className="mt-4 rounded-md bg-erro/10 px-3 py-2 text-sm text-erro">
            {aviso}
          </p>
        )}

        {!usuario ? (
          <div className="mt-5">
            <p className="mb-3 text-sm text-suave">Entre na sua conta para reservar.</p>
            <Link
              to="/login"
              state={{ de: `/eventos/${id}` }}
              className="block rounded-md bg-marca px-4 py-2 text-center text-sm font-medium text-fundo transition-colors hover:bg-marca-forte focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-marca"
            >
              Entrar
            </Link>
          </div>
        ) : esgotado ? (
          <p className="mt-5 rounded-md bg-erro/10 px-3 py-2 text-sm text-erro">
            Todos os lugares foram vendidos.
          </p>
        ) : (
          <div className="mt-5 space-y-3">
            <div>
              <h3 className="text-sm text-suave">Selecionados</h3>
              {selecionados.length === 0 ? (
                <p className="mt-1 text-sm text-suave">
                  Nenhum ainda — toque num lugar livre do mapa.
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

            <p className="numerico border-t border-borda/60 pt-3 text-sm">
              Total: <span className="font-semibold">{dinheiro(total)}</span>
            </p>

            <Botao
              className="w-full"
              disabled={reserva.isPending || selecionados.length === 0}
              onClick={() => reserva.mutate()}
            >
              {reserva.isPending ? 'Reservando...' : 'Reservar'}
            </Botao>

            {reserva.isError && (
              <p className="rounded-md bg-erro/10 px-3 py-2 text-sm text-erro">
                {mensagemDe(reserva.error)}
              </p>
            )}

            <p className="text-xs text-suave">
              A reserva segura os lugares por tempo limitado. O pagamento e feito na tela de
              minhas reservas.
            </p>
          </div>
        )}
      </Cartao>
    </div>
  )
}
