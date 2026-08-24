package com.devbandeiraa.paymentsimulator;

import com.devbandeiraa.paymentsimulator.config.SimulacaoProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * Gateway de pagamento falso.
 *
 * <p>Nao existe para simular pagamento — a plataforma ja fazia isso trocando o status da reserva
 * direto no banco. Existe para simular <em>a rede</em>: um servico fora do nosso controle, que
 * demora, falha sem avisar e volta sozinho. Sem um terceiro assim, retry e circuit breaker viram
 * configuracao que ninguem nunca viu agir.
 *
 * <p>Por isso ele e propositalmente instavel, e o quanto de instabilidade se ajusta por variavel
 * de ambiente. Ver {@link SimulacaoProperties}.
 */
@SpringBootApplication
@EnableConfigurationProperties(SimulacaoProperties.class)
public class PaymentSimulatorApplication {

    public static void main(String[] args) {
        SpringApplication.run(PaymentSimulatorApplication.class, args);
    }
}
