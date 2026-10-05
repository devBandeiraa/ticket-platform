import { CabecalhoDeSecao, FaixaDeSecao } from '../../componentes/Editorial'
import { IconeInformacao } from '../../componentes/Icones'

/**
 * Os tres passos da compra, como o design os numera.
 *
 * <p>Descrevem o fluxo que existe de verdade: a pagina do evento, o mapa de assentos e o
 * ingresso digital. "Quando disponiveis" no segundo passo nao e hesitacao — e o estado real de
 * uma casa cheia, e prometer escolha de assento num evento esgotado seria vender o que nao tem.
 */
const PASSOS = [
  {
    titulo: 'Encontre seu evento',
    texto: 'Confira a programação, o local e todos os detalhes.',
  },
  {
    titulo: 'Escolha seu lugar',
    texto: 'Selecione o setor e o assento, quando disponíveis.',
  },
  {
    titulo: 'Leve seu ingresso',
    texto: 'Conclua a simulação e acesse seu ingresso digital.',
  },
]

/**
 * Marca de leitura do QR.
 *
 * <p>Quatro cantos, e nao um QR de verdade. O codigo real e gerado no ingresso da pessoa, a
 * partir da reserva dela — desenhar um aqui exigiria inventar um conteudo, e um QR falso e
 * escaneavel: alguem aponta a camera e cai num dado que nao existe. Os cantos dizem "aqui vai o
 * codigo" sem fingir ser um.
 */
function MarcaDeQr() {
  const canto = 'size-3.5 border-texto/70'

  return (
    <div aria-hidden="true" className="grid shrink-0 grid-cols-2 gap-1.5">
      <span className={`${canto} border-l-2 border-t-2`} />
      <span className={`${canto} border-r-2 border-t-2`} />
      <span className={`${canto} border-b-2 border-l-2`} />
      <span className={`${canto} border-b-2 border-r-2`} />
    </div>
  )
}

/**
 * Ingresso de exemplo.
 *
 * <h2>Por que os dados sao fixos</h2>
 *
 * <p>Esta e a peca que veio do hero, onde ilustrava a identidade da plataforma. Aqui ela ilustra
 * o RESULTADO da compra, que e um lugar bem melhor para ela: ao lado dos tres passos, um
 * ingresso de exemplo responde a pergunta que os passos levantam — o que eu recebo no fim.
 *
 * <p>Os dados continuam fixos, pelo motivo que ja valia no hero: e uma peca de identidade, e nao
 * a oferta de um evento. Um ingresso com dados reais aqui seria um ingresso que ninguem comprou,
 * com o codigo de alguma reserva de verdade impresso ao lado da palavra "exemplo".
 *
 * <p>E diz isso em palavras: "exemplo · sem validade para entrada", logo abaixo do codigo. Num
 * projeto que simula pagamento, a ressalva nao pode depender de quem leu o rodape.
 *
 * <h2>Claro, e nao vinho</h2>
 *
 * <p>No design o ingresso e papel claro com uma tarja escura no topo — a mesma inversao do
 * ingresso impresso de verdade, em que o cabecalho da casa e o unico bloco de tinta cheia. O
 * componente `Ticket`, usado na compra e em "meus ingressos", continua escuro: la ele aparece
 * sobre papel e precisa se destacar; aqui ele JA esta num bloco claro.
 */
function IngressoDeExemplo() {
  return (
    <div className="overflow-hidden rounded-cartao border border-borda bg-superficie shadow-lg">
      <div className="flex items-center justify-between gap-4 bg-noite px-6 py-4">
        <span className="font-semibold text-noite-texto">
          ticket<span className="text-marca-clara">.platform</span>
        </span>
        <span className="rotulo text-noite-suave">Ingresso digital</span>
      </div>

      <div className="p-6">
        <p className="text-xl font-semibold leading-snug">Orquestra de Câmara da Lapa</p>

        <p className="numerico mt-3 text-sm text-suave">18 OUT 2026 · 19H</p>
        <p className="text-sm text-suave">Casa da Lapa · Rio de Janeiro</p>

        <dl className="mt-6 flex gap-10">
          <div>
            <dt className="rotulo text-suave">Setor</dt>
            <dd className="mt-1 font-medium">Plateia</dd>
          </div>
          <div>
            <dt className="rotulo text-suave">Assento</dt>
            <dd className="numerico mt-1 font-medium">A · 08</dd>
          </div>
        </dl>

        {/* Regua tracejada no lugar do picote: e onde o canhoto se separaria. */}
        <div className="mt-6 flex items-center gap-5 border-t border-dashed border-borda pt-6">
          <MarcaDeQr />

          <div className="min-w-0">
            <p className="font-medium">Seu lugar, na palma da mão.</p>
            <p className="rotulo mt-1.5 text-suave">Exemplo · sem validade para entrada</p>
          </div>
        </div>
      </div>
    </div>
  )
}

/** Secao "do encontro ao ingresso": os tres passos ao lado do ingresso de exemplo. */
export function ComoFunciona() {
  return (
    <FaixaDeSecao id="como-funciona">
      <div className="grid items-start gap-14 lg:grid-cols-[1.45fr_1fr]">
        <div>
          <CabecalhoDeSecao
            sobretitulo="Do encontro ao ingresso"
            titulo="Menos etapas. Mais presença."
          />

          {/* Lista ordenada de verdade, em tres colunas como no design. Com `<div>` numerado a
              mao, quem usa leitor de tela ouviria tres paragrafos soltos em vez de "item 1 de
              3" — que e a informacao inteira desta secao. */}
          <ol className="mt-10 grid gap-8 sm:grid-cols-3">
            {PASSOS.map((passo, indice) => (
              <li key={passo.titulo}>
                <span aria-hidden="true" className="rotulo text-marca-forte">
                  {String(indice + 1).padStart(2, '0')} /
                </span>

                <h3 className="mt-3 font-semibold">{passo.titulo}</h3>
                <p className="mt-2 text-pretty text-sm text-suave">{passo.texto}</p>
              </li>
            ))}
          </ol>

          <p className="mt-10 flex items-center gap-2 text-xs text-suave">
            <IconeInformacao tamanho={15} className="shrink-0" />
            Ambiente de demonstração: nenhum pagamento real é realizado.
          </p>
        </div>

        <IngressoDeExemplo />
      </div>
    </FaixaDeSecao>
  )
}
