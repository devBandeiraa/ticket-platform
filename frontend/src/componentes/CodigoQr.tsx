import { useEffect, useState } from 'react'
import { toString as qrParaSvg } from 'qrcode'

/**
 * QR Code do ingresso.
 *
 * <h2>Por que SVG, e nao canvas</h2>
 *
 * <p>O ingresso e impresso e fotografado. Um canvas tem resolucao fixa em pixels e vira um
 * borrao quando alguem amplia para a camera da portaria ler; o SVG e vetorial e continua nitido
 * em qualquer tamanho. Tambem sobrevive a um "salvar pagina" e a impressao em PDF, que e como
 * boa parte das pessoas guarda ingresso.
 *
 * <h2>Sobre o `dangerouslySetInnerHTML`</h2>
 *
 * <p>A biblioteca devolve o SVG como string, e nao como arvore React. Injetar HTML merece
 * justificativa, e aqui ela e: a string NAO vem do usuario nem da rede. E gerada localmente por
 * `qrcode` a partir de um codigo que o proprio servidor emitiu no formato `TP-XXXXXX-XXXXXX`,
 * cujo alfabeto tem trinta e um simbolos alfanumericos — nao ha como um `<script>` atravessar
 * um codificador de QR e sair do outro lado como marcacao.
 *
 * <p>A alternativa seria uma segunda dependencia que devolvesse componentes React. Uma
 * dependencia a mais para evitar uma linha comentada nao se paga.
 *
 * <h2>Correcao de erro</h2>
 *
 * <p>Nivel M: recupera cerca de 15% do simbolo danificado. Ingresso amassa, dobra e pega
 * reflexo de luz na portaria. O nivel L geraria um QR menor e falharia justamente no caso em
 * que ele mais precisa funcionar; H desperdicaria area para um codigo de dezesseis caracteres.
 */
export function CodigoQr({
  valor,
  tamanho = 128,
  className = '',
}: {
  valor: string
  tamanho?: number
  className?: string
}) {
  const [svg, setSvg] = useState<string | null>(null)
  const [falhou, setFalhou] = useState(false)

  useEffect(() => {
    let vigente = true

    qrParaSvg(valor, {
      type: 'svg',
      errorCorrectionLevel: 'M',
      // Sem margem da biblioteca: o espaco em volta e dado pelo layout do ingresso, e somar os
      // dois deixaria o QR pequeno dentro de uma moldura branca larga.
      margin: 0,
      width: tamanho,
      // Preto sobre branco, sempre. O QR fica sobre o gradiente escuro do ingresso, e um leitor
      // precisa de contraste maximo entre modulo e fundo — tingir de vinho seria decorar uma
      // coisa cuja unica funcao e ser lida por uma camera.
      color: { dark: '#000000', light: '#FFFFFF' },
    })
      .then((gerado) => {
        if (vigente) setSvg(gerado)
      })
      .catch(() => {
        if (vigente) setFalhou(true)
      })

    // A geracao e assincrona; sem esta guarda, trocar de ingresso antes de ela terminar
    // escreveria o QR do anterior por cima do atual.
    return () => {
      vigente = false
    }
  }, [valor, tamanho])

  if (falhou) {
    // O codigo em texto e o recurso: a portaria digita, e digitar funciona sem camera.
    return (
      <p className="numerico text-xs text-noite-suave">
        QR indisponivel — informe o codigo {valor}
      </p>
    )
  }

  return (
    <div
      // O codigo ja aparece como texto ao lado. Anunciar o QR tambem faria um leitor de tela
      // repetir a mesma informacao duas vezes, uma delas como "imagem".
      aria-hidden="true"
      className={`overflow-hidden rounded bg-white p-2 ${className}`}
      style={{ width: tamanho + 16, height: tamanho + 16 }}
    >
      {svg ? (
        <div dangerouslySetInnerHTML={{ __html: svg }} />
      ) : (
        // Reserva o espaco enquanto gera, para o ingresso nao pular de altura.
        <div className="size-full animate-pulse rounded bg-esqueleto" />
      )}
    </div>
  )
}
