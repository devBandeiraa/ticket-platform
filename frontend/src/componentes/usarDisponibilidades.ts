import { useQueries } from '@tanstack/react-query'
import { consultarDisponibilidade } from '../api/eventos'
import type { Disponibilidade } from '../api/tipos'

/**
 * Quanto resta de cada evento, para o cartao do catalogo.
 *
 * <h2>O custo, declarado</h2>
 *
 * <p>Isto e um N+1 sobre HTTP: nove cartoes na tela viram nove requisicoes. A API de
 * disponibilidade e por evento — {@code GET /events/:id/availability} — e nao ha versao em lote.
 *
 * <p>Foi aceito, e nao ignorado. Nove chamadas paralelas de um {@code COUNT} cabem folgadamente
 * no limite do gateway, que repoe vinte fichas por segundo, e o React Query as mantem em cache
 * entre paginas. O consumo que isso evita e maior do que parece: sem o numero, o cartao teria de
 * mandar todo mundo abrir o evento para descobrir que esgotou.
 *
 * <p>A correcao de verdade e um endpoint em lote no booking-service, aceitando uma lista de ids.
 * E trabalho de backend, fora do que este estagio combinou fazer, e esta registrado no
 * mapeamento para ser decidido.
 *
 * <p>{@code staleTime} de trinta segundos: o numero envelhece rapido sob concorrencia, e ao
 * mesmo tempo nao precisa ser exato num cartao de vitrine. Quem esta comprando ve o valor
 * atualizado no detalhe do evento, que consulta de novo.
 */
export function useDisponibilidades(eventIds: string[]) {
  const consultas = useQueries({
    queries: eventIds.map((id) => ({
      queryKey: ['disponibilidade', id],
      queryFn: () => consultarDisponibilidade(id),
      staleTime: 30_000,
      // Sem retry: o cartao funciona sem o selo, e insistir multiplicaria por tres um N+1 que
      // ja e o ponto sensivel daqui.
      retry: false,
    })),
  })

  const porEvento = new Map<string, Disponibilidade>()
  consultas.forEach((consulta, i) => {
    if (consulta.data) {
      porEvento.set(eventIds[i], consulta.data)
    }
  })

  return porEvento
}
