package com.devbandeiraa.bookingservice.lock;

import java.util.function.Supplier;

/**
 * Exclusao mutua entre instancias do servico.
 *
 * <p>Existe como interface, e nao como classe concreta, por um motivo pratico: o servico de
 * reserva precisa poder ser testado sem Redis no ar, e a troca de implementacao e o que permite
 * isso. O ganho de desenho vem junto — quem chama depende de "executar isto sob exclusao mutua",
 * nao de "falar com o Redis".
 *
 * <p><strong>Nao confie nisto para correcao.</strong> Este lock e uma otimizacao: reduz a
 * contencao no banco serializando as tentativas antes que elas cheguem la. A garantia de que
 * nao se vende ingresso a mais e o {@code UPDATE} condicional em
 * {@code EventInventoryRepository}, e ela vale mesmo que este lock falhe.
 */
public interface DistributedLock {

    /**
     * Executa a operacao sob exclusao mutua para a chave informada.
     *
     * <p>Se o lock nao puder ser adquirido dentro das tentativas configuradas, a operacao nao e
     * executada. Se o proprio servico de lock estiver indisponivel, a operacao e executada
     * mesmo assim — ver a implementacao para o porque.
     *
     * @throws LockIndisponivelException quando as tentativas se esgotam sem adquirir o lock
     */
    <T> T executarComLock(String chave, Supplier<T> operacao);
}
