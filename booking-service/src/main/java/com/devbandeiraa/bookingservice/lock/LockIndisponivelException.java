package com.devbandeiraa.bookingservice.lock;

/**
 * Nao foi possivel adquirir o lock dentro das tentativas configuradas.
 *
 * <p>Traduzida em {@code 409 LOCK_TIMEOUT}. Nao e erro do servidor: significa que o evento esta
 * sob disputa intensa neste instante e vale a pena tentar de novo — situacao normal nos segundos
 * de abertura de venda de um show concorrido.
 */
public class LockIndisponivelException extends RuntimeException {

    private final String chave;

    public LockIndisponivelException(String chave, int tentativas) {
        super("nao foi possivel adquirir o lock '%s' apos %d tentativas".formatted(chave, tentativas));
        this.chave = chave;
    }

    public String getChave() {
        return chave;
    }
}
