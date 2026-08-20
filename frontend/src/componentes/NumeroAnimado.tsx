import { useEffect, useState } from 'react'

/** O usuario pediu menos movimento no sistema operacional. */
function prefereMenosMovimento(): boolean {
  return window.matchMedia('(prefers-reduced-motion: reduce)').matches
}

/**
 * Numero que sobe de zero ate o valor final.
 *
 * Existe para o resultado do teste de concorrencia. O numero que interessa ali e o zero de
 * "vendidos a mais", e um zero que simplesmente aparece na tela nao se distingue de um campo
 * que nao carregou. Contando ate ele, fica claro que o valor foi medido.
 *
 * Anima com requestAnimationFrame, e nao com setInterval: o intervalo agenda por tempo de
 * relogio e acumula atraso, enquanto o rAF entrega o instante real do quadro — a contagem
 * termina na duracao pedida mesmo que a aba engasgue no meio.
 */
export function NumeroAnimado({
  valor,
  duracao = 700,
  className = '',
}: {
  valor: number
  duracao?: number
  className?: string
}) {
  // Quem pediu menos movimento ja comeca no valor final: iniciar em zero e corrigir no
  // primeiro quadro seria um salto visivel, que e exatamente o que a preferencia evita.
  // A regra CSS global nao alcanca animacao feita em JavaScript, entao a decisao vem para ca.
  const [exibido, setExibido] = useState(() => (prefereMenosMovimento() ? valor : 0))

  useEffect(() => {
    const duracaoEfetiva = prefereMenosMovimento() ? 0 : duracao
    const inicio = performance.now()
    let quadro = 0

    const passo = (agora: number): void => {
      const progresso =
        duracaoEfetiva <= 0 ? 1 : Math.min(1, (agora - inicio) / duracaoEfetiva)

      // Desaceleracao cubica: rapida no comeco e lenta no fim, que e como o olho espera que
      // um contador pare. Linear parece travar de repente no ultimo numero.
      const suavizado = 1 - Math.pow(1 - progresso, 3)

      setExibido(Math.round(valor * suavizado))

      if (progresso < 1) quadro = requestAnimationFrame(passo)
    }

    quadro = requestAnimationFrame(passo)

    return () => cancelAnimationFrame(quadro)
  }, [valor, duracao])

  return <span className={`numerico ${className}`}>{exibido}</span>
}
