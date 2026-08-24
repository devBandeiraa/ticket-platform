package com.devbandeiraa.paymentsimulator.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * O quanto este gateway falso se comporta mal.
 *
 * <p>Tudo ajustavel em tempo de execucao por variavel de ambiente, porque os numeros uteis mudam
 * conforme o que se quer mostrar: para gravar uma demonstracao do retry, uma taxa alta de falha
 * transitoria; para rodar os testes de integracao da plataforma, zero — senao um teste de reserva
 * falharia por causa do dado de um simulador, o que seria um teste mentindo sobre o que verifica.
 *
 * @param falhaPercentual   chance de falha transitoria (503), que o cliente deve repetir
 * @param recusaPercentual  chance de recusa definitiva (402), que o cliente nao deve repetir
 * @param latencia          atraso fixo aplicado a toda resposta
 * @param latenciaExtra     atraso adicional sorteado entre zero e este valor
 */
@ConfigurationProperties(prefix = "simulacao")
public record SimulacaoProperties(
        int falhaPercentual,
        int recusaPercentual,
        Duration latencia,
        Duration latenciaExtra) {

    public SimulacaoProperties {
        // Validado na construcao, e nao no ponto de uso: uma configuracao errada deve impedir o
        // servico de subir, em vez de produzir um comportamento estranho no meio de uma
        // demonstracao e mandar quem assiste procurar defeito no lugar errado.
        if (falhaPercentual < 0 || falhaPercentual > 100) {
            throw new IllegalArgumentException("simulacao.falha-percentual deve ficar entre 0 e 100");
        }
        if (recusaPercentual < 0 || recusaPercentual > 100) {
            throw new IllegalArgumentException("simulacao.recusa-percentual deve ficar entre 0 e 100");
        }
        if (falhaPercentual + recusaPercentual > 100) {
            throw new IllegalArgumentException(
                    "simulacao.falha-percentual + simulacao.recusa-percentual nao pode passar de 100");
        }
    }
}
