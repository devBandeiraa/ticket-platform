import { CabecalhoDeSecao, FaixaDeSecao, LinkDeSaida } from '../../componentes/Editorial'
import { IconeMais } from '../../componentes/Icones'

/** Onde o projeto mora. Mesmo endereco do rodape. */
const REPOSITORIO = 'https://github.com/devBandeiraa/ticket-platform'

/**
 * As quatro duvidas do design.
 *
 * <p>A primeira e a unica que importa de verdade: o site simula pagamento, e quem nao souber
 * disso vai descobrir no checkout. As outras tres explicam o fluxo — setor e assento, onde fica
 * o ingresso, o que acontece quando a reserva vence —, e cada resposta descreve o comportamento
 * que o sistema tem de fato, nao o que seria bom ter.
 *
 * <p>A resposta sobre a reserva vencida e a mais importante tecnicamente: e a regra que faz o
 * estoque voltar a ficar disponivel, e e o que a demonstracao de concorrencia exercita.
 */
const DUVIDAS = [
  {
    pergunta: 'Este site vende ingressos de verdade?',
    resposta:
      'Não. A ticket.platform é um projeto de portfólio. Os eventos, as reservas e os pagamentos são de demonstração, sem cobranças ou ingressos válidos para entrada.',
  },
  {
    pergunta: 'Como escolho meu setor e assento?',
    resposta:
      'Na página do evento você vê a planta da casa. Escolha o setor e depois o assento no mapa: os lugares já vendidos aparecem marcados, e a seleção vale para a reserva que você levar ao checkout.',
  },
  {
    pergunta: 'Onde encontro meu ingresso digital?',
    resposta:
      'Em "Meus ingressos", depois de concluir a simulação de pagamento. O ingresso traz o código, o setor, o assento e um QR Code — tudo gerado na hora, sem valor para entrada.',
  },
  {
    pergunta: 'O que acontece quando minha reserva expira?',
    resposta:
      'Os lugares voltam para o catálogo e ficam disponíveis para outra pessoa. A reserva aparece como expirada na sua lista, e nada é cobrado. É por isso que o checkout mostra uma contagem regressiva.',
  },
]

/**
 * Secao de duvidas.
 *
 * <h2>`details` nativo, e nao um acordeao de estado proprio</h2>
 *
 * <p>Abrir e fechar um painel e exatamente o que `<details>` faz, e o navegador ja traz o que um
 * acordeao escrito a mao precisa reimplementar: o papel de botao no resumo, o estado de expandido
 * anunciado ao leitor de tela, o teclado funcionando, e o conteudo encontravel pela busca da
 * propria pagina — um painel fechado por `display: none` em estado de React nao e achado pelo
 * Ctrl+F.
 *
 * <p>Nao usa `name` para fechar um painel quando outro abre. O atributo existe e e tentador, mas
 * aqui atrapalha: sao quatro respostas curtas, e quem esta comparando "onde fica o ingresso" com
 * "o que acontece se expirar" quer as duas abertas.
 */
export function Duvidas() {
  return (
    <FaixaDeSecao id="ajuda">
      <div className="grid gap-12 lg:grid-cols-[1fr_1.3fr]">
        <div>
          <CabecalhoDeSecao
            sobretitulo="Antes de ir"
            titulo="Uma dúvida no caminho?"
            apoio="O essencial para escolher seu lugar com tranquilidade."
          />

          {/*
            O destino desta chamada.

            O design escreve "ir para a central de ajuda", e nao ha central de ajuda: a propria
            secao e. Apontar para uma rota inventada daria um link para uma tela vazia, e
            apontar para esta secao daria um link para onde a pessoa ja esta. O que existe de
            documentacao do projeto e o repositorio — entao e para la, com o rotulo dizendo a
            verdade sobre o destino.
          */}
          <LinkDeSaida para={REPOSITORIO} className="mt-8 text-marca-forte">
            Ver a documentação do projeto
          </LinkDeSaida>
        </div>

        <div>
          <dl className="divide-y divide-borda border-y border-borda">
            {DUVIDAS.map((duvida) => (
              <div key={duvida.pergunta}>
                <details className="group">
                  {/* `list-none` nos dois dialetos: o Chrome desenha o triangulo por
                      `::-webkit-details-marker` e o Firefox por `list-style`. Sem os dois, um
                      dos navegadores mostra a seta padrao ao lado da nossa. */}
                  <summary className="flex cursor-pointer list-none items-center justify-between gap-4 py-5 text-left text-lg font-medium transition-colors hover:text-marca-forte [&::-webkit-details-marker]:hidden">
                    <dt>{duvida.pergunta}</dt>

                    {/* O sinal e desenhado, e nao um caractere: o "+" tipografico muda de
                        largura e de altura em cada fonte, e aqui ele precisa casar com o traco
                        dos demais icones. A haste some ao abrir — ver IconeMais. */}
                    <span className="shrink-0 text-suave group-open:text-marca-forte">
                      <IconeMais tamanho={18} className="group-open:[&>path:last-child]:scale-y-0" />
                    </span>
                  </summary>

                  <dd className="pb-6 pr-10 text-pretty text-sm leading-relaxed text-suave">
                    {duvida.resposta}
                  </dd>
                </details>
              </div>
            ))}
          </dl>
        </div>
      </div>
    </FaixaDeSecao>
  )
}
