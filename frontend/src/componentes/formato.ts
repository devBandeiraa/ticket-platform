/*
  Formatacao para leitura humana. Concentrada aqui porque data e dinheiro aparecem em quase
  toda tela, e cada copia solta seria uma chance de exibir moeda ou fuso diferente.

  As datas chegam da API em UTC e sao exibidas no fuso do navegador — a conversao acontece
  aqui, e so aqui.
*/

const DATA_E_HORA = new Intl.DateTimeFormat('pt-BR', {
  dateStyle: 'medium',
  timeStyle: 'short',
})

const DINHEIRO = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' })

export function dataEHora(iso: string): string {
  return DATA_E_HORA.format(new Date(iso))
}

export function dinheiro(valor: number): string {
  return DINHEIRO.format(valor)
}

/** Formato de contagem regressiva: `mm:ss`, ou `h:mm:ss` quando passa de uma hora. */
export function duracao(milissegundos: number): string {
  const total = Math.max(0, Math.floor(milissegundos / 1000))
  const horas = Math.floor(total / 3600)
  const minutos = Math.floor((total % 3600) / 60)
  const segundos = total % 60

  const doisDigitos = (n: number) => String(n).padStart(2, '0')

  return horas > 0
    ? `${horas}:${doisDigitos(minutos)}:${doisDigitos(segundos)}`
    : `${doisDigitos(minutos)}:${doisDigitos(segundos)}`
}

/**
 * Ha quanto tempo algo esta de pe, em texto curto: `2d 4h`, `3h 12min`, `45s`.
 *
 * So as duas maiores unidades. "2d 4h 17min 3s" e preciso e ilegivel — quem olha um painel de
 * status quer saber se o servico subiu agora ou esta ha dias no ar, e a terceira casa nao muda
 * essa resposta.
 */
export function tempoNoAr(segundos: number): string {
  const total = Math.max(0, Math.floor(segundos))

  const dias = Math.floor(total / 86400)
  const horas = Math.floor((total % 86400) / 3600)
  const minutos = Math.floor((total % 3600) / 60)

  if (dias > 0) return `${dias}d ${horas}h`
  if (horas > 0) return `${horas}h ${minutos}min`
  if (minutos > 0) return `${minutos}min`
  return `${total}s`
}

/** Para o `datetime-local`, que nao aceita ISO com fuso e trabalha em horario local. */
export function paraCampoLocal(iso: string): string {
  const data = new Date(iso)
  const deslocado = new Date(data.getTime() - data.getTimezoneOffset() * 60000)
  return deslocado.toISOString().slice(0, 16)
}

/** Caminho inverso: o que o `datetime-local` devolve vira instante UTC para a API. */
export function deCampoLocal(valor: string): string {
  return new Date(valor).toISOString()
}
