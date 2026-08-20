import { memo, useEffect, useRef } from 'react'

/*
  Ceu de pixels desenhado em canvas: estrelas que piscam e, de vez em quando, uma estrela
  cadente. Fica atras de tudo, sem capturar clique.

  Canvas, e nao centenas de <div> animadas por CSS: cada estrela seria um no na arvore e uma
  camada para o compositor. Em um canvas so, o custo e um retangulo por estrela por quadro, a
  16 fps — barato o bastante para rodar durante a demo de concorrencia sem disputar CPU com ela.

  As funcoes de desenho vivem fora do componente de proposito. Elas nao dependem de nada do
  React, so de um contexto e de um array; deixa-las no modulo evita um `useCallback` por
  funcao e, com isso, a lista de dependencias que vinha junto.
*/

/*
  Paleta enviesada para a marca. As cores quentes do original sairam de proposito: vermelho e
  verde tem SIGNIFICADO nesta interface — erro e sucesso — e piscar uma estrela verde no fundo
  de um painel de reservas e ruido semantico, nao decoracao.
*/
const CORES = ['#FFFFFF', '#DCE7FF', '#AAC8FF', '#AAEEFF', '#FFF4CC', '#D7C4FF'] as const

const DENSIDADE_PADRAO = 0.00006
const CHANCE_DE_PISCAR = 0.7
const VELOCIDADE_MINIMA = 2
const VELOCIDADE_MAXIMA = 4
const LADO_DO_PIXEL = 5
const INTERVALO_DE_RENOVACAO = 5000
const PERCENTUAL_RENOVADO = 0.15

const LADO_DA_CADENTE = 2
const FPS_ALVO = 16 // baixo de proposito: o serrilhado do movimento e o efeito retro

interface Estrela {
  x: number
  y: number
  cor: string
  opacidadeBase: number
  opacidadeAtual: number
  pisca: boolean
  velocidade: number
  direcao: number
  relogio: number
}

interface PontoDoRastro {
  x: number
  y: number
  opacidade: number
}

interface Cadente {
  x: number
  y: number
  angulo: number
  velocidade: number
  distancia: number
  rastro: PontoDoRastro[]
}

function sortearEstrela(largura: number, altura: number): Estrela {
  const opacidadeBase = Math.random() * 0.5 + 0.5

  return {
    // Encaixada na grade do pixel: fora dela o canvas antisserrilha a borda e o quadrado vira
    // um borrao cinzento, que e o oposto de arte em pixel.
    x: Math.floor(Math.random() * (largura / LADO_DO_PIXEL)) * LADO_DO_PIXEL,
    y: Math.floor(Math.random() * (altura / LADO_DO_PIXEL)) * LADO_DO_PIXEL,
    cor: CORES[Math.floor(Math.random() * CORES.length)]!,
    opacidadeBase,
    opacidadeAtual: opacidadeBase,
    pisca: Math.random() < CHANCE_DE_PISCAR,
    velocidade: VELOCIDADE_MINIMA + Math.random() * (VELOCIDADE_MAXIMA - VELOCIDADE_MINIMA),
    direcao: -1,
    relogio: 0,
  }
}

/**
 * Desenha o ceu e avanca a piscada de cada estrela.
 *
 * Altera os objetos no lugar, e nao por copia. Sao dezenas deles redesenhados dezesseis vezes
 * por segundo: recriar o array a cada quadro produziria lixo para o coletor sem nenhum ganho,
 * ja que ninguem observa esse estado — ele nunca sai do canvas.
 */
function desenharEstrelas(ctx: CanvasRenderingContext2D, estrelas: Estrela[]): void {
  for (const estrela of estrelas) {
    ctx.fillStyle = estrela.cor
    ctx.globalAlpha = estrela.opacidadeAtual
    ctx.fillRect(estrela.x, estrela.y, LADO_DO_PIXEL, LADO_DO_PIXEL)

    if (!estrela.pisca) continue

    estrela.relogio += 1 / FPS_ALVO
    if (estrela.relogio >= estrela.velocidade) {
      estrela.relogio = 0
      estrela.direcao *= -1
    }

    // Dois niveis de brilho, e nao uma interpolacao suave: hardware de 16 bits nao tinha
    // opacidade continua, e a transicao em degrau e o que produz a piscada retro.
    const primeiraMetade = estrela.relogio / estrela.velocidade < 0.5
    const apagando = estrela.direcao < 0
    const forte = primeiraMetade === apagando

    estrela.opacidadeAtual = forte ? estrela.opacidadeBase : estrela.opacidadeBase * 0.3
  }
}

/** Move as cadentes, alonga o rastro e descarta as que sairam da tela. */
function avancarCadentes(cadentes: Cadente[]): Cadente[] {
  return cadentes
    .map((cadente) => {
      const radianos = (cadente.angulo * Math.PI) / 180
      const distancia = cadente.distancia + cadente.velocidade
      const rastro = [...cadente.rastro]

      // Um ponto a cada oito pixels percorridos. Marcar todo quadro daria um risco continuo;
      // o espacamento e o que faz o rastro parecer feito de pixels soltos.
      if (distancia % 8 < cadente.velocidade) {
        rastro.push({ x: cadente.x, y: cadente.y, opacidade: 1 })
      }

      return {
        ...cadente,
        x: cadente.x + cadente.velocidade * Math.cos(radianos),
        y: cadente.y + cadente.velocidade * Math.sin(radianos),
        distancia,
        rastro: rastro
          .map((ponto) => ({ ...ponto, opacidade: ponto.opacidade - 0.1 }))
          .filter((ponto) => ponto.opacidade > 0),
      }
    })
    .filter(
      (cadente) =>
        cadente.x >= -30 &&
        cadente.x <= window.innerWidth + 30 &&
        cadente.y >= -30 &&
        cadente.y <= window.innerHeight + 30,
    )
}

function desenharCadentes(ctx: CanvasRenderingContext2D, cadentes: Cadente[]): void {
  // Zera a opacidade herdada do laco das estrelas. Sem esta linha o rastro sai multiplicado
  // pela opacidade da ULTIMA estrela desenhada, e a cadente muda de brilho a cada quadro por
  // um motivo que nao tem nada a ver com ela.
  ctx.globalAlpha = 1

  for (const cadente of cadentes) {
    for (const ponto of cadente.rastro) {
      ctx.fillStyle = `rgba(180, 242, 255, ${ponto.opacidade})`
      ctx.fillRect(ponto.x, ponto.y, LADO_DA_CADENTE, LADO_DA_CADENTE)
    }

    ctx.save()
    ctx.translate(cadente.x, cadente.y)
    ctx.rotate((cadente.angulo * Math.PI) / 180)
    ctx.translate(-cadente.x, -cadente.y)
    ctx.fillStyle = '#FFFFFF'

    // Corpo de 4x2 pixels com dois cantos vazados, para nao virar um retangulo solido.
    for (let y = 0; y < 2; y++) {
      for (let x = 0; x < 4; x++) {
        if ((x === 0 && y === 1) || (x === 3 && y === 0)) continue
        ctx.fillRect(
          cadente.x + x * LADO_DA_CADENTE,
          cadente.y + y * LADO_DA_CADENTE,
          LADO_DA_CADENTE,
          LADO_DA_CADENTE,
        )
      }
    }

    ctx.restore()
  }
}

interface Props {
  /** Classes extras — e por aqui que cada tela controla opacidade e mascara. */
  className?: string
  /** Estrelas por pixel de area. Mais que isso vira poluicao visual, nao ceu. */
  densidade?: number
}

export const FundoEstrelado = memo(function FundoEstrelado({
  className = '',
  densidade = DENSIDADE_PADRAO,
}: Props) {
  const canvasRef = useRef<HTMLCanvasElement | null>(null)

  useEffect(() => {
    const canvas = canvasRef.current
    const ctx = canvas?.getContext('2d')
    if (!canvas || !ctx) return

    let estrelas: Estrela[] = []
    let cadentes: Cadente[] = []

    const dimensionar = (): void => {
      canvas.width = window.innerWidth
      canvas.height = window.innerHeight

      const quantas = Math.floor(canvas.width * canvas.height * densidade)
      estrelas = Array.from({ length: quantas }, () => sortearEstrela(canvas.width, canvas.height))
    }

    dimensionar()

    // Movimento reduzido: desenha o ceu uma vez e para. Some a piscada e somem as cadentes,
    // mas nao o fundo — apagar tudo trocaria um desconforto por uma tela sem identidade.
    if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
      desenharEstrelas(ctx, estrelas)
      return
    }

    let quadro = 0
    let ultimoQuadro = 0
    const intervaloEntreQuadros = 1000 / FPS_ALVO

    // Funcao nomeada declarada aqui dentro, e nao um `useCallback` que se referencia: o laco
    // pertence a este efeito e morre com ele, entao nao ha motivo para atravessar renders.
    const animar = (agora: number): void => {
      quadro = requestAnimationFrame(animar)

      // requestAnimationFrame roda a 60 fps ou mais; aqui so 16 interessam. Pular quadro e o
      // que da o serrilhado retro e, de quebra, corta tres quartos do desenho.
      if (agora - ultimoQuadro < intervaloEntreQuadros) return
      ultimoQuadro = agora

      ctx.clearRect(0, 0, canvas.width, canvas.height)
      desenharEstrelas(ctx, estrelas)

      if (cadentes.length > 0) {
        cadentes = avancarCadentes(cadentes)
        desenharCadentes(ctx, cadentes)
      }
    }

    quadro = requestAnimationFrame(animar)

    /*
      A cadente se reagenda sozinha. O `timeout` precisa ser guardado e cancelado na limpeza:
      sem isso o agendamento pendente dispara depois do unmount, cria outra estrela, agenda a
      proxima — e a corrente fica viva para sempre. Numa SPA, cada visita a uma pagina deixaria
      mais uma rodando em segundo plano.
    */
    let proximaCadente: ReturnType<typeof setTimeout>

    const lancarCadente = (): void => {
      cadentes = [
        ...cadentes,
        {
          x: Math.random() * window.innerWidth,
          y: 0,
          // 45 a 135 graus: sempre para baixo, com inclinacao variavel para os dois lados.
          angulo: 45 + Math.random() * 90,
          velocidade: Math.random() * 5 + 8,
          distancia: 0,
          rastro: [],
        },
      ]

      proximaCadente = setTimeout(lancarCadente, Math.random() * 6000 + 3000)
    }

    proximaCadente = setTimeout(lancarCadente, 1500)

    /*
      Troca uma fatia das estrelas de tempos em tempos. Sem isso o ceu e literalmente o mesmo
      desenho o tempo todo, e o olho percebe: o fundo passa a parecer uma imagem estatica com
      brilhos, em vez de um ceu.
    */
    const renovacao = setInterval(() => {
      if (estrelas.length === 0) return

      const quantas = Math.max(1, Math.floor(estrelas.length * PERCENTUAL_RENOVADO))
      for (let i = 0; i < quantas; i++) {
        estrelas[Math.floor(Math.random() * estrelas.length)] = sortearEstrela(
          canvas.width,
          canvas.height,
        )
      }
    }, INTERVALO_DE_RENOVACAO)

    // Redimensionar dispara dezenas de eventos por segundo enquanto se arrasta a janela, e
    // cada um refaria o ceu inteiro. Espera a mao parar.
    let redimensionamento: ReturnType<typeof setTimeout>
    const aoRedimensionar = (): void => {
      clearTimeout(redimensionamento)
      redimensionamento = setTimeout(dimensionar, 150)
    }

    window.addEventListener('resize', aoRedimensionar)

    return () => {
      cancelAnimationFrame(quadro)
      clearTimeout(proximaCadente)
      clearTimeout(redimensionamento)
      clearInterval(renovacao)
      window.removeEventListener('resize', aoRedimensionar)
    }
  }, [densidade])

  return (
    <canvas
      ref={canvasRef}
      aria-hidden="true"
      className={`pixelado pointer-events-none fixed inset-0 -z-10 ${className}`}
    />
  )
})
