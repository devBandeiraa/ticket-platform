package com.devbandeiraa.bookingservice.exception;

/**
 * O cabecalho {@code Idempotency-Key} veio ausente, vazio ou longo demais.
 *
 * <p>Traduzida em {@code 400 INVALID_IDEMPOTENCY_KEY}. Exigir a chave em vez de gerar uma
 * quando falta e proposital: uma chave gerada pelo servidor seria diferente a cada tentativa,
 * de modo que o retry de um cliente que perdeu a resposta criaria uma segunda reserva. A
 * idempotencia so funciona se quem repete o pedido repetir tambem a chave.
 */
public class ChaveDeIdempotenciaInvalidaException extends RuntimeException {

    public ChaveDeIdempotenciaInvalidaException(String motivo) {
        super("Idempotency-Key invalido: " + motivo);
    }
}
