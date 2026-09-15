package com.devbandeiraa.bookingservice.domain;

/**
 * Forma de pagamento escolhida no checkout.
 *
 * <p>Registrada no ato da confirmacao, e nao na reserva: quem reserva ainda nao escolheu. Por
 * isso a coluna aceita nulo, e reserva pendente nao tem forma de pagamento alguma.
 *
 * <p>Persistido como texto, e nao pelo ordinal, pelo mesmo motivo dos demais enums do projeto:
 * inserir um meio novo no meio da lista nao pode reescrever o significado das linhas gravadas.
 *
 * <p>O simulador de pagamento trata as duas iguais. A distincao existe no dado porque e o que o
 * comprador informou e o que o ingresso mostra; fazer o simulador se comportar diferente seria
 * simular uma diferenca que este projeto nao tem como exercer de verdade.
 */
public enum PaymentMethod {

    CARD,

    PIX
}
