package com.devbandeiraa.paymentsimulator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.devbandeiraa.paymentsimulator.config.SimulacaoProperties;
import com.devbandeiraa.paymentsimulator.dto.PaymentRequest;
import com.devbandeiraa.paymentsimulator.dto.PaymentResponse;
import com.devbandeiraa.paymentsimulator.exception.PagamentoIndisponivelException;
import com.devbandeiraa.paymentsimulator.exception.PagamentoRecusadoException;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A idempotencia do provedor falso.
 *
 * <p>E o comportamento que torna seguro o retry do booking-service. Se este contrato quebrar, o
 * retry do outro lado deixa de ser resiliencia e vira um gerador de cobranca em duplicidade — por
 * isso vale teste proprio, e nao apenas a verificacao de ponta a ponta.
 *
 * <p>As instancias sao montadas com percentuais fixos, e nao com os padroes: um teste que depende
 * de sorteio ora passa ora nao, e um teste assim e pior que teste nenhum, porque ensina a equipe a
 * reexecutar ate passar.
 */
class AutorizadorSimuladoTest {

    private static final Duration SEM_ATRASO = Duration.ZERO;

    private final PaymentRequest cobranca =
            new PaymentRequest(UUID.randomUUID(), new BigDecimal("150.00"));

    /** Sempre autoriza. */
    private AutorizadorSimulado sempreAutoriza() {
        return new AutorizadorSimulado(new SimulacaoProperties(0, 0, SEM_ATRASO, SEM_ATRASO));
    }

    /** Sempre falha de forma transitoria. */
    private AutorizadorSimulado sempreFalha() {
        return new AutorizadorSimulado(new SimulacaoProperties(100, 0, SEM_ATRASO, SEM_ATRASO));
    }

    /** Sempre recusa em definitivo. */
    private AutorizadorSimulado sempreRecusa() {
        return new AutorizadorSimulado(new SimulacaoProperties(0, 100, SEM_ATRASO, SEM_ATRASO));
    }

    // ---------- idempotencia ----------

    @Test
    @DisplayName("a mesma chave devolve o mesmo comprovante, sem cobrar de novo")
    void deveDevolverAMesmaAutorizacaoParaAMesmaChave() {
        AutorizadorSimulado autorizador = sempreAutoriza();

        PaymentResponse primeira = autorizador.cobrar(cobranca, "booking-1");
        PaymentResponse segunda = autorizador.cobrar(cobranca, "booking-1");

        assertThat(segunda.authorizationCode()).isEqualTo(primeira.authorizationCode());
        assertThat(primeira.repetida()).isFalse();
        // A marca e o que permite ao cliente registrar em log que houve reaproveitamento, em vez
        // de acreditar que fez duas cobrancas.
        assertThat(segunda.repetida()).isTrue();
    }

    @Test
    @DisplayName("chaves diferentes produzem cobrancas diferentes")
    void deveCobrarDeNovoComChaveDiferente() {
        AutorizadorSimulado autorizador = sempreAutoriza();

        PaymentResponse primeira = autorizador.cobrar(cobranca, "booking-1");
        PaymentResponse segunda = autorizador.cobrar(cobranca, "booking-2");

        assertThat(segunda.authorizationCode()).isNotEqualTo(primeira.authorizationCode());
        assertThat(segunda.repetida()).isFalse();
    }

    // ---------- desfechos ----------

    @Test
    @DisplayName("falha transitoria nao fica registrada: a proxima tentativa tem chance nova")
    void deveDeixarARetentativaPassarAposFalhaTransitoria() {
        // Guardar a falha condenaria a chave a falhar para sempre, tornando o retry inutil
        // exatamente no caso em que ele deveria funcionar.
        AutorizadorSimulado instavel = sempreFalha();

        assertThatThrownBy(() -> instavel.cobrar(cobranca, "booking-1"))
                .isInstanceOf(PagamentoIndisponivelException.class);

        // Um autorizador saudavel com a MESMA chave: se a falha tivesse sido registrada, nem este
        // conseguiria autorizar.
        PaymentResponse aposRecuperar = sempreAutoriza().cobrar(cobranca, "booking-1");
        assertThat(aposRecuperar.authorizationCode()).isNotBlank();
        assertThat(aposRecuperar.repetida()).isFalse();
    }

    @Test
    @DisplayName("recusa e definitiva: repetir devolve a mesma recusa")
    void deveManterARecusaNaRepeticao() {
        AutorizadorSimulado autorizador = sempreRecusa();

        assertThatThrownBy(() -> autorizador.cobrar(cobranca, "booking-1"))
                .isInstanceOf(PagamentoRecusadoException.class);

        // Deixar a repeticao sortear de novo faria o mesmo cartao ora passar ora nao, e quem
        // integra concluiria que retry "as vezes resolve" uma recusa — a licao oposta a que este
        // simulador existe para ensinar.
        assertThatThrownBy(() -> autorizador.cobrar(cobranca, "booking-1"))
                .isInstanceOf(PagamentoRecusadoException.class);
    }

    // ---------- estorno ----------

    @Test
    @DisplayName("estorno de comprovante conhecido e aceito, e repetir tambem")
    void deveEstornarDeFormaIdempotente() {
        AutorizadorSimulado autorizador = sempreAutoriza();
        PaymentResponse autorizada = autorizador.cobrar(cobranca, "booking-1");

        assertThat(autorizador.estornar(autorizada.authorizationCode())).isTrue();
        // Quem compensa uma falha ja esta num caminho que deu errado, e nao pode receber um erro
        // novo por tentar consertar.
        assertThat(autorizador.estornar(autorizada.authorizationCode())).isTrue();
    }

    @Test
    @DisplayName("estorno de comprovante desconhecido e recusado")
    void deveRecusarEstornoDesconhecido() {
        assertThat(sempreAutoriza().estornar("AUT-NAOEXISTE")).isFalse();
    }

    // ---------- configuracao ----------

    @Test
    @DisplayName("percentuais somando mais de 100 impedem o servico de subir")
    void deveRecusarConfiguracaoImpossivel() {
        // Falhar na subida e melhor do que produzir um comportamento estranho no meio de uma
        // demonstracao e mandar quem assiste procurar defeito no lugar errado.
        assertThatThrownBy(() -> new SimulacaoProperties(70, 40, SEM_ATRASO, SEM_ATRASO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nao pode passar de 100");

        assertThatCode(() -> new SimulacaoProperties(70, 30, SEM_ATRASO, SEM_ATRASO))
                .doesNotThrowAnyException();
    }
}
