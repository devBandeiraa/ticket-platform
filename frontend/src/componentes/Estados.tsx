import type { ReactNode } from 'react'
import { ErroDaApi } from '../api/cliente'

/**
 * Bloco cinza pulsante no lugar de um conteudo que ainda nao chegou.
 *
 * Melhor que a palavra "Carregando" por um motivo concreto: o esqueleto ocupa o espaco e o
 * formato do que vem depois, entao a tela nao pula quando os dados chegam. O texto centralizado
 * some e empurra tudo, e o resultado e um solavanco a cada requisicao.
 */
export function Esqueleto({ className = '' }: { className?: string }) {
  return <div className={`animate-pulse rounded-md bg-superficie ${className}`} />
}

/** Esqueleto no formato dos cartoes do catalogo. */
export function EsqueletoDeCartoes({ quantos = 6 }: { quantos?: number }) {
  return (
    <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
      {Array.from({ length: quantos }, (_, i) => (
        <div key={i} className="vidro rounded-xl border border-borda p-5">
          <Esqueleto className="h-5 w-3/4" />
          <Esqueleto className="mt-2.5 h-4 w-1/2" />
          <Esqueleto className="mt-5 h-4 w-2/3" />
          <Esqueleto className="mt-5 h-6 w-24" />
        </div>
      ))}
    </div>
  )
}

export function Carregando({ texto = 'Carregando...' }: { texto?: string }) {
  return (
    <div className="flex items-center justify-center gap-3 py-10 text-suave">
      <span
        aria-hidden="true"
        className="size-4 animate-spin rounded-full border-2 border-borda border-t-marca"
      />
      {/* role=status faz o leitor de tela anunciar a espera sem roubar o foco de onde ele esta. */}
      <span role="status" className="text-sm">
        {texto}
      </span>
    </div>
  )
}

export function Vazio({ children }: { children: ReactNode }) {
  return (
    <div className="animate-surgir rounded-xl border border-dashed border-borda py-14 text-center text-suave">
      {children}
    </div>
  )
}

/**
 * Traducoes dos codigos de erro do backend.
 *
 * A tela decide pelo codigo, nunca pelo texto — foi para isso que o backend passou a devolver
 * um codigo estavel. Aqui o texto e so a forma de dizer ao usuario o que aconteceu, e pode
 * mudar sem quebrar logica alguma.
 */
const MENSAGENS: Record<string, string> = {
  SOLD_OUT: 'Os ingressos acabaram enquanto voce decidia.',
  LOCK_TIMEOUT: 'Muita gente reservando este evento agora. Tente de novo em instantes.',
  BOOKING_EXPIRED: 'O prazo de pagamento desta reserva venceu.',
  BOOKING_CANCELLED: 'Esta reserva ja foi cancelada.',
  BOOKING_ALREADY_CONFIRMED: 'Esta reserva ja esta paga.',
  EVENT_NOT_AVAILABLE: 'Este evento nao esta disponivel para venda.',
  EVENT_NOT_PUBLISHED: 'Este evento ainda nao foi publicado.',
  INVALID_CREDENTIALS: 'E-mail ou senha incorretos.',
  EMAIL_ALREADY_REGISTERED: 'Ja existe uma conta com este e-mail.',
  RATE_LIMIT_EXCEEDED: 'Muitas tentativas seguidas. Aguarde alguns instantes.',
  INVALID_TOKEN: 'Sua sessao expirou. Entre novamente.',
  SESSION_EXPIRED: 'Sua sessao expirou. Entre novamente.',
  VALIDATION_ERROR: 'Confira os campos destacados.',
  FORBIDDEN: 'Voce nao tem permissao para isso.',
}

export function mensagemDe(erro: unknown): string {
  if (erro instanceof ErroDaApi) {
    return MENSAGENS[erro.codigo] ?? erro.message
  }
  // Sem resposta alguma: gateway fora do ar, rede caida, CORS barrando.
  return 'Nao foi possivel falar com o servidor. Verifique se a plataforma esta no ar.'
}

export function Erro({ erro }: { erro: unknown }) {
  const traceId = erro instanceof ErroDaApi ? erro.corpo?.traceId : undefined

  return (
    <div className="animate-surgir rounded-xl border border-erro/40 bg-erro/10 px-4 py-3 text-sm">
      <p className="text-erro">{mensagemDe(erro)}</p>
      {traceId && (
        // Mostrado de proposito: e o que liga a tela do usuario a linha exata no log.
        <p className="mt-1 text-xs text-suave">
          Codigo de rastreio: <span className="numerico">{traceId}</span>
        </p>
      )}
    </div>
  )
}
