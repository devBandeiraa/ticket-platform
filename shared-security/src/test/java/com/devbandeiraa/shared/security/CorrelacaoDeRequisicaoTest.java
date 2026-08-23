package com.devbandeiraa.shared.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * A regra que decide se um {@code X-Request-Id} vindo de fora pode ser aproveitado.
 *
 * <p>Vale um teste proprio porque este e o unico ponto do sistema em que um texto controlado pelo
 * cliente vai parar em toda linha de log de uma requisicao.
 */
class CorrelacaoDeRequisicaoTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "abcdef0123456789",
            "cliente-abc-123",
            "COM_UNDERSCORE_E_MAIUSCULAS",
            "12345678"
    })
    @DisplayName("aceita identificadores alfanumericos com hifen e underscore")
    void deveAceitarFormatosLegitimos(String valor) {
        assertThat(CorrelacaoDeRequisicao.aceitavel(valor)).isTrue();
        assertThat(CorrelacaoDeRequisicao.normalizar(valor)).isEqualTo(valor);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {
            "",
            "curto",
            "com espaco no meio",
            "quebra\nde linha injetada no log",
            "retorno\rde carro",
            "ponto.e.virgula;",
            "<script>alert(1)</script>"
    })
    @DisplayName("recusa o que nao serve como identificador de log")
    void deveRecusarFormatosPerigososOuInuteis(String valor) {
        assertThat(CorrelacaoDeRequisicao.aceitavel(valor)).isFalse();

        // Recusado nao significa requisicao rejeitada: a chamada e legitima, so o cabecalho e que
        // nao serve. Trocar o valor mantem a observabilidade sem transformar um problema de log
        // num problema de disponibilidade.
        assertThat(CorrelacaoDeRequisicao.normalizar(valor)).matches("[a-f0-9]{16}");
    }

    @Test
    @DisplayName("recusa caractere fora do ASCII")
    void deveRecusarCaractereNaoAscii() {
        // Montado em codigo, e nao escrito literalmente, para o arquivo seguir em ASCII puro como
        // o resto do projeto. Um cabecalho HTTP so carrega ASCII com seguranca: bytes acima de 127
        // dependem da interpretacao de cada intermediario, e o valor pode chegar diferente do que
        // saiu — quebrando justamente a igualdade da qual a correlacao depende.
        String comAcento = "acentua" + (char) 0xE7 + (char) 0xE3 + "o";

        assertThat(CorrelacaoDeRequisicao.aceitavel(comAcento)).isFalse();
        assertThat(CorrelacaoDeRequisicao.normalizar(comAcento)).matches("[a-f0-9]{16}");
    }

    @Test
    @DisplayName("recusa valor longo demais")
    void deveRecusarValorLongoDemais() {
        // O teto limita quanto um cliente consegue fazer o servico gravar em disco por requisicao.
        String longo = "a".repeat(65);

        assertThat(CorrelacaoDeRequisicao.aceitavel(longo)).isFalse();
        assertThat(CorrelacaoDeRequisicao.aceitavel("a".repeat(64))).isTrue();
    }

    @Test
    @DisplayName("ids gerados nao se repetem dentro de um volume plausivel de investigacao")
    void deveGerarIdsDistintos() {
        Set<String> gerados = new HashSet<>();
        for (int i = 0; i < 10_000; i++) {
            gerados.add(CorrelacaoDeRequisicao.gerar());
        }

        // Com 64 bits, a chance de colisao em dez mil sorteios e desprezivel. O teste protege
        // contra o erro real, que seria alguem encurtar o id e nao perceber o efeito.
        assertThat(gerados).hasSize(10_000);
    }
}
