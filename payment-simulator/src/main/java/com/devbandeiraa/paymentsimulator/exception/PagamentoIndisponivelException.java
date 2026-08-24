package com.devbandeiraa.paymentsimulator.exception;

/** Falha transitoria: a cobranca nao chegou a ser avaliada, e repetir faz sentido. */
public class PagamentoIndisponivelException extends RuntimeException {

    public PagamentoIndisponivelException(String mensagem) {
        super(mensagem);
    }
}
