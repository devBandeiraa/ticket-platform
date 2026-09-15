import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { alterarEvento, buscarParaAdmin, criarEvento } from '../../api/eventos'
import { ErroDaApi } from '../../api/cliente'
import type { CategoriaDoEvento, EventoFormulario, SetorFormulario } from '../../api/tipos'
import { Capa } from '../../componentes/Capa'
import { Carregando, Erro, mensagemDe } from '../../componentes/Estados'
import { Botao, Campo, Cartao, Selecao, SeloDeEvento } from '../../componentes/Ui'
import { deCampoLocal, dinheiro, paraCampoLocal } from '../../componentes/formato'

const SETOR_NOVO: SetorFormulario = { name: '', price: 0, rowsCount: 10, seatsPerRow: 20 }

/*
  Rotulos das categorias.

  O valor e o mesmo enum do backend; o texto e so apresentacao. Ficam juntos aqui, e nao numa
  lista derivada do tipo, porque TypeScript apaga o tipo na compilacao — nao ha como percorrer
  `CategoriaDoEvento` em tempo de execucao. O `Record` garante o que importa: acrescentar uma
  categoria no backend e esquecer o rotulo vira erro de compilacao, e nao um seletor incompleto.
*/
const CATEGORIAS: Record<CategoriaDoEvento, string> = {
  SHOWS: 'Shows',
  FESTIVAIS: 'Festivais',
  ESPORTES: 'Esportes',
  TECNOLOGIA: 'Tecnologia',
  TEATRO: 'Teatro',
  FESTAS: 'Festas',
}

const VAZIO: EventoFormulario = {
  name: '',
  description: '',
  venue: '',
  eventDate: '',
  sectors: [{ ...SETOR_NOVO, name: 'Plateia' }],
  imageUrl: '',
  category: 'SHOWS',
}

/** Capacidade e "a partir de" como o servidor vai derivar — a tela so antecipa a conta. */
function derivados(setores: SetorFormulario[]) {
  const capacidade = setores.reduce((soma, s) => soma + s.rowsCount * s.seatsPerRow, 0)
  const menorPreco = setores.length ? Math.min(...setores.map((s) => s.price)) : 0
  return { capacidade, menorPreco }
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
      sectors: existente.data.sectors.map((setor) => ({
        name: setor.name,
        price: setor.price,
        rowsCount: setor.rowsCount,
        seatsPerRow: setor.seatsPerRow,
        // Reenviados inalterados. Omiti-los faria o PUT chegar sem eles, e o servidor
        // gravaria nulo por cima do que o setor ja dizia de si.
        description: setor.description,
        benefits: setor.benefits,
        tier: setor.tier,
      })),
      // O input e controlado e nao aceita null; o backend devolve o vazio como null de volta.
      imageUrl: existente.data.imageUrl ?? '',
      category: existente.data.category,
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

  function alterarSetor<C extends keyof SetorFormulario>(
    indice: number,
    campo: C,
    valor: SetorFormulario[C],
  ) {
    setDados((atual) => ({
      ...atual,
      sectors: atual.sectors.map((setor, i) => (i === indice ? { ...setor, [campo]: valor } : setor)),
    }))
  }

  function adicionarSetor() {
    setDados((atual) => ({ ...atual, sectors: [...atual.sectors, { ...SETOR_NOVO }] }))
  }

  function removerSetor(indice: number) {
    setDados((atual) => ({ ...atual, sectors: atual.sectors.filter((_, i) => i !== indice) }))
  }

  /*
    Publicado, o evento pode ter reservas, e o booking-service ja copiou a capacidade. O
    servidor recusa com 409 EVENT_LAYOUT_LOCKED; aqui os campos ficam desabilitados para o
    admin nao descobrir isso depois de digitar. A guarda de verdade continua sendo a do servidor
    — desabilitar um input nao impede ninguem de mandar a requisicao na mao.
  */
  const bloqueado = existente.data?.status !== undefined && existente.data.status !== 'DRAFT'

  const { capacidade, menorPreco } = derivados(dados.sectors)

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

          <Selecao
            rotulo="Categoria"
            required
            value={dados.category}
            onChange={(e) => alterar('category', e.target.value as CategoriaDoEvento)}
            erro={campos?.category}
          >
            {Object.entries(CATEGORIAS).map(([valor, rotulo]) => (
              <option key={valor} value={valor}>
                {rotulo}
              </option>
            ))}
          </Selecao>

          <div>
            <Campo
              rotulo="Capa (URL)"
              type="url"
              maxLength={500}
              placeholder="https://..."
              value={dados.imageUrl ?? ''}
              onChange={(e) => alterar('imageUrl', e.target.value)}
              erro={campos?.imageUrl}
            />
            {/* Previa imediata: uma URL errada aparece aqui, e nao depois de publicar o evento. */}
            {dados.imageUrl ? (
              <Capa
                nome={dados.name || 'Evento'}
                url={dados.imageUrl}
                prioridade
                className="mt-2 aspect-[16/9] w-full rounded-md border border-borda"
              />
            ) : (
              <p className="mt-1 text-xs text-suave">
                Sem capa, o catalogo desenha um fundo a partir do nome do evento.
              </p>
            )}
          </div>

          <fieldset className="rounded-md border border-borda p-4">
            <legend className="px-1 text-sm text-suave">Setores</legend>

            {/* Capacidade e preco do evento nao sao mais campos: derivam daqui. A tela mostra
                a conta enquanto o admin digita, para ele nao descobrir o resultado so ao salvar. */}
            <p className="mb-3 text-xs text-suave">
              {capacidade} lugares no total, a partir de {dinheiro(menorPreco)}
              {bloqueado && ' — o evento ja foi publicado, e a planta nao pode mais mudar'}
            </p>

            <div className="space-y-3">
              {dados.sectors.map((setor, indice) => (
                <div key={indice} className="grid grid-cols-2 gap-2 sm:grid-cols-9">
                  <div className="col-span-2 sm:col-span-3">
                    <Campo
                      rotulo="Nome"
                      required
                      maxLength={60}
                      disabled={bloqueado}
                      value={setor.name}
                      onChange={(e) => alterarSetor(indice, 'name', e.target.value)}
                      erro={campos?.[`sectors[${indice}].name`]}
                    />
                  </div>
                  <Campo
                    rotulo="Preco"
                    type="number"
                    min={0}
                    step="0.01"
                    required
                    disabled={bloqueado}
                    value={setor.price}
                    onChange={(e) => alterarSetor(indice, 'price', Number(e.target.value))}
                    erro={campos?.[`sectors[${indice}].price`]}
                  />
                  <Campo
                    rotulo="Filas"
                    type="number"
                    min={1}
                    max={200}
                    required
                    disabled={bloqueado}
                    value={setor.rowsCount}
                    onChange={(e) => alterarSetor(indice, 'rowsCount', Number(e.target.value))}
                    erro={campos?.[`sectors[${indice}].rowsCount`]}
                  />
                  <Campo
                    rotulo="Por fila"
                    type="number"
                    min={1}
                    max={100}
                    required
                    disabled={bloqueado}
                    value={setor.seatsPerRow}
                    onChange={(e) => alterarSetor(indice, 'seatsPerRow', Number(e.target.value))}
                    erro={campos?.[`sectors[${indice}].seatsPerRow`]}
                  />
                  <div className="flex items-end sm:col-span-2">
                    <span className="mb-2 text-xs text-suave">
                      = {setor.rowsCount * setor.seatsPerRow}
                    </span>
                    {/* O ultimo setor nao pode sair: um evento sem setor nao tem casa. */}
                    {dados.sectors.length > 1 && !bloqueado && (
                      <button
                        type="button"
                        onClick={() => removerSetor(indice)}
                        aria-label={`Remover o setor ${setor.name || indice + 1}`}
                        className="mb-1 ml-auto rounded-md px-2 py-1 text-xs text-suave transition-colors hover:text-erro focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-marca"
                      >
                        remover
                      </button>
                    )}
                  </div>
                </div>
              ))}
            </div>

            {!bloqueado && (
              <button
                type="button"
                onClick={adicionarSetor}
                className="mt-3 rounded-md border border-borda px-3 py-1.5 text-xs text-suave transition-colors hover:border-marca hover:text-marca focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-marca"
              >
                + setor
              </button>
            )}

            {campos?.sectors && (
              <span className="mt-2 block text-xs text-erro">{campos.sectors}</span>
            )}
          </fieldset>

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
