import { useEffect, useState } from 'react'
import { duracao } from './formato'

/**
 * Conta o tempo restante ate `expiraEm` e avisa quando chega a zero.
 *
 * Conta a partir do instante que o backend devolveu, e nao de um contador local iniciado no
 * clique. Se a aba ficar em segundo plano — quando o navegador estrangula os timers — o
 * numero exibido continua certo, porque cada tick recalcula a diferenca em vez de decrementar.
 *
 * O zero aqui e apenas visual. Quem de fato impede o pagamento de uma reserva vencida e o
 * `WHERE expires_at > now()` da transicao no banco; este relogio informa, nao decide.
 */
export function ContagemRegressiva({
  expiraEm,
  aoExpirar,
}: {
  expiraEm: string
  aoExpirar?: () => void
}) {
  const alvo = new Date(expiraEm).getTime()
  const [restante, setRestante] = useState(() => alvo - Date.now())

  useEffect(() => {
    setRestante(alvo - Date.now())

    const intervalo = setInterval(() => {
      const agora = alvo - Date.now()
      setRestante(agora)

      if (agora <= 0) {
        clearInterval(intervalo)
        // Deixa a tela reagir: recarregar a reserva e mostrar o status que o backend decidiu.
        aoExpirar?.()
      }
    }, 1000)

    return () => clearInterval(intervalo)
    // `aoExpirar` fica fora: uma funcao recriada a cada render reiniciaria o intervalo
    // constantemente, e a contagem nunca avancaria.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [alvo])

  if (restante <= 0) return <span className="text-erro">prazo vencido</span>

  // Menos de um minuto merece destaque: e quando ainda da tempo de agir.
  const urgente = restante < 60_000

  return (
    <span className={`numerico font-medium ${urgente ? 'text-erro' : 'text-alerta'}`}>
      {duracao(restante)}
    </span>
  )
}
