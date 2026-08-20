import type { ReactNode } from 'react'
import { ErroDaApi } from '../api/cliente'

export function Carregando({ texto = 'Carregando...' }: { texto?: string }) {
  return <p className="py-10 text-center text-suave">{texto}</p>
}

export function Vazio({ children }: { children: ReactNode }) {
  return (
    <div className="rounded-lg border border-dashed border-borda py-12 text-center text-suave">
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
    <div className="rounded-lg border border-erro/40 bg-erro/10 px-4 py-3 text-sm">
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
