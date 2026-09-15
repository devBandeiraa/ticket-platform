import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { Ticket } from './Ticket'

describe('Ticket', () => {
  it('mostra o que o ingresso precisa carregar', () => {
    render(
      <Ticket
        titulo="Quarteto Sonora"
        local="Sala Sao Paulo"
        setor="Plateia"
        data="23 set 2026"
        hora="21:00"
        codigo="TP-4K7M2P-9XQ3RB"
        comprador="Joao Silva"
      />,
    )

    expect(screen.getByRole('heading', { name: 'Quarteto Sonora' })).toBeInTheDocument()
    expect(screen.getByText('Sala Sao Paulo · Plateia')).toBeInTheDocument()
    expect(screen.getByText('23 set 2026 · 21:00')).toBeInTheDocument()
    expect(screen.getByText('TP-4K7M2P-9XQ3RB')).toBeInTheDocument()
    expect(screen.getByText('Joao Silva')).toBeInTheDocument()
  })

  it('omite as linhas que nao tem dado, em vez de deixar separador solto', () => {
    // O ingresso do hero nao tem comprador nem codigo. Sem esta guarda, a linha de local
    // sairia como " · " — um separador sem nada dos dois lados.
    render(<Ticket titulo="Tomorrowland Experience" data="25 out 2026" />)

    expect(screen.getByText('25 out 2026')).toBeInTheDocument()
    expect(screen.queryByText('Titular')).not.toBeInTheDocument()
    expect(screen.queryByText(/·/)).not.toBeInTheDocument()
  })

  it('junta local e setor com separador so quando ha os dois', () => {
    const { rerender } = render(<Ticket titulo="Show" local="Teatro Rival" />)
    expect(screen.getByText('Teatro Rival')).toBeInTheDocument()

    rerender(<Ticket titulo="Show" setor="Camarote" />)
    expect(screen.getByText('Camarote')).toBeInTheDocument()
  })

  it('leva a chamada padrao quando nenhuma e informada', () => {
    render(<Ticket titulo="Show" />)
    expect(screen.getByText('Admit one')).toBeInTheDocument()
  })

  it('aplica o recorte e o picote, que sao a identidade da peca', () => {
    // Verifica as CLASSES, e nao o pixel: o recorte e o tracejado sao regras CSS, e jsdom nao
    // desenha. O que este teste impede e alguem remover a classe ao refatorar o markup — o
    // ingresso continuaria renderizando, so que como um retangulo qualquer.
    const { container } = render(<Ticket titulo="Show" />)

    expect(container.querySelector('.recorte-de-ingresso')).not.toBeNull()
    expect(container.querySelector('.picote-vertical')).not.toBeNull()
  })

  it('esconde de leitor de tela o que e so decoracao', () => {
    const { container } = render(<Ticket titulo="Show" />)

    // Textura e picote nao sao conteudo. Anunciados, viram ruido entre o titulo e a data.
    expect(container.querySelector('.picote-vertical')).toHaveAttribute('aria-hidden', 'true')
    expect(container.querySelector('svg')).toHaveAttribute('aria-hidden', 'true')
  })

  it('da ids distintos a textura de cada ingresso na mesma tela', () => {
    // Dois filtros SVG com o mesmo id fazem o segundo ingresso reaproveitar o primeiro. Com
    // um contador de modulo daria certo no navegador e erraria na hidratacao.
    const { container } = render(
      <>
        <Ticket titulo="Primeiro" />
        <Ticket titulo="Segundo" />
      </>,
    )

    const ids = [...container.querySelectorAll('filter')].map((f) => f.id)
    expect(ids).toHaveLength(2)
    expect(new Set(ids).size).toBe(2)
  })
})
