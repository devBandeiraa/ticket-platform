package com.devbandeiraa.paymentsimulator.exception;

/** Recusa definitiva: a cobranca foi avaliada e negada. Repetir devolve exatamente isto. */
public class PagamentoRecusadoException extends RuntimeException {

    public PagamentoRecusadoException(String motivo) {
        super(motivo);
    }
}
