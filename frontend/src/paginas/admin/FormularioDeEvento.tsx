import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { alterarEvento, buscarParaAdmin, criarEvento } from '../../api/eventos'
import { ErroDaApi } from '../../api/cliente'
import type { EventoFormulario } from '../../api/tipos'
import { Carregando, Erro, mensagemDe } from '../../componentes/Estados'
import { Botao, Campo, Cartao, SeloDeEvento } from '../../componentes/Ui'
import { deCampoLocal, paraCampoLocal } from '../../componentes/formato'

const VAZIO: EventoFormulario = {
  name: '',
  description: '',
  venue: '',
  eventDate: '',
  totalTickets: 100,
  price: 0,
}

/** Cria e edita. Os campos editaveis sao os mesmos nos dois casos, como no backend. */
export function FormularioDeEvento() {
  const { id } = useParams()
  const editando = Boolean(id)
  const navegar = useNavigate()
  const queryClient = useQueryClient()

  const [dados, setDados] = useState<EventoFormulario>(VAZIO)

  const existente = useQuery({
    queryKey: ['admin-evento', id],
    queryFn: () => buscarParaAdmin(id!),
    enabled: editando,
  })

  useEffect(() => {
    if (!existente.data) return
    setDados({
      name: existente.data.name,
      description: existente.data.description ?? '',
      venue: existente.data.venue,
      eventDate: paraCampoLocal(existente.data.eventDate),
      totalTickets: existente.data.totalTickets,
      price: existente.data.price,
    })
  }, [existente.data])

  const salvamento = useMutation({
    mutationFn: (formulario: EventoFormulario) => {
      // O campo de data trabalha em horario local; a API espera instante UTC.
      const corpo = { ...formulario, eventDate: deCampoLocal(formulario.eventDate) }
      return editando ? alterarEvento(id!, corpo) : criarEvento(corpo)
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin-eventos'] })
      queryClient.invalidateQueries({ queryKey: ['eventos'] })
      navegar('/admin/eventos')
    },
  })

  const campos = salvamento.error instanceof ErroDaApi ? salvamento.error.campos : undefined

  function alterar<C extends keyof EventoFormulario>(campo: C, valor: EventoFormulario[C]) {
    setDados((atual) => ({ ...atual, [campo]: valor }))
  }

  if (editando && existente.isPending) return <Carregando />
  if (editando && existente.isError) return <Erro erro={existente.error} />

  return (
    <div className="mx-auto max-w-xl">
      <Link to="/admin/eventos" className="text-sm text-suave hover:text-texto">
        &larr; voltar
      </Link>

      <div className="mb-6 mt-3 flex items-center gap-3">
        <h1 className="text-2xl font-semibold">{editando ? 'Editar evento' : 'Novo evento'}</h1>
        {existente.data && <SeloDeEvento status={existente.data.status} />}
      </div>

      <Cartao>
        <form
          onSubmit={(e) => {
            e.preventDefault()
            salvamento.mutate(dados)
          }}
          className="space-y-4"
        >
          <Campo
            rotulo="Nome"
            required
            maxLength={150}
            value={dados.name}
            onChange={(e) => alterar('name', e.target.value)}
            erro={campos?.name}
          />

          <label className="block">
            <span className="mb-1 block text-sm text-suave">Descricao</span>
            <textarea
              rows={4}
              maxLength={2000}
              value={dados.description ?? ''}
              onChange={(e) => alterar('description', e.target.value)}
              className="w-full rounded-md border border-borda bg-fundo px-3 py-2 text-sm outline-none focus:border-marca"
            />
            {campos?.description && (
              <span className="mt-1 block text-xs text-erro">{campos.description}</span>
            )}
          </label>

          <Campo
            rotulo="Local"
            required
            maxLength={200}
            value={dados.venue}
            onChange={(e) => alterar('venue', e.target.value)}
            erro={campos?.venue}
          />

          <Campo
            rotulo="Data e hora"
            type="datetime-local"
            required
            value={dados.eventDate}
            onChange={(e) => alterar('eventDate', e.target.value)}
            erro={campos?.eventDate}
          />

          <div className="grid gap-4 sm:grid-cols-2">
            <Campo
              rotulo="Ingressos"
              type="number"
              min={1}
              required
              value={dados.totalTickets}
              onChange={(e) => alterar('totalTickets', Number(e.target.value))}
              erro={campos?.totalTickets}
            />
            <Campo
              rotulo="Preco (R$)"
              type="number"
              min={0}
              step="0.01"
              required
              value={dados.price}
              onChange={(e) => alterar('price', Number(e.target.value))}
              erro={campos?.price}
            />
          </div>

          {salvamento.error != null && (
            <p className="rounded-md bg-erro/10 px-3 py-2 text-sm text-erro">
              {mensagemDe(salvamento.error)}
            </p>
          )}

          <div className="flex gap-2">
            <Botao type="submit" disabled={salvamento.isPending}>
              {salvamento.isPending ? 'Salvando...' : 'Salvar'}
            </Botao>
            <Botao type="button" variante="neutro" onClick={() => navegar('/admin/eventos')}>
              Cancelar
            </Botao>
          </div>

          {!editando && (
            <p className="text-xs text-suave">
              O evento sera criado como rascunho. Publicar e uma acao separada, na listagem.
            </p>
          )}
        </form>
      </Cartao>
    </div>
  )
}
