package com.devbandeiraa.shared.security;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.ServletException;
import java.io.IOException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * O filtro de correlacao do lado servlet: MDC preenchido durante a requisicao e limpo ao final.
 */
class CorrelacaoServletFilterTest {

    private final CorrelacaoServletFilter filtro = new CorrelacaoServletFilter();

    @AfterEach
    void limparContexto() {
        MDC.clear();
    }

    @Test
    @DisplayName("o id recebido fica disponivel no MDC durante a requisicao")
    void devePublicarOIdRecebidoNoMdc() throws Exception {
        MockHttpServletRequest requisicao = new MockHttpServletRequest();
        requisicao.addHeader(CorrelacaoDeRequisicao.CABECALHO, "vindo-do-gateway");

        String[] visto = new String[1];
        filtro.doFilter(requisicao, new MockHttpServletResponse(),
                capturarDoMdc(visto));

        assertThat(visto[0]).isEqualTo("vindo-do-gateway");
    }

    @Test
    @DisplayName("sem cabecalho, o servico gera o proprio id")
    void deveGerarQuandoChamadoDiretamente() throws Exception {
        String[] visto = new String[1];

        // Acontece o tempo todo: servico aberto pela IDE, teste de integracao, chamada por dentro
        // da rede. Ficar sem id nesses casos deixaria justamente o desenvolvimento sem correlacao.
        filtro.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(),
                capturarDoMdc(visto));

        assertThat(visto[0]).matches("[a-f0-9]{16}");
    }

    @Test
    @DisplayName("o id volta ao chamador no cabecalho da resposta")
    void deveDevolverOIdNaResposta() throws Exception {
        MockHttpServletRequest requisicao = new MockHttpServletRequest();
        requisicao.addHeader(CorrelacaoDeRequisicao.CABECALHO, "para-o-suporte");
        MockHttpServletResponse resposta = new MockHttpServletResponse();

        filtro.doFilter(requisicao, resposta, new MockFilterChain());

        assertThat(resposta.getHeader(CorrelacaoDeRequisicao.CABECALHO)).isEqualTo("para-o-suporte");
    }

    @Test
    @DisplayName("o MDC e limpo ao final, mesmo quando a requisicao falha")
    void deveLimparOMdcAposFalha() {
        MockHttpServletRequest requisicao = new MockHttpServletRequest();
        requisicao.addHeader(CorrelacaoDeRequisicao.CABECALHO, "vai-explodir");

        try {
            filtro.doFilter(requisicao, new MockHttpServletResponse(),
                    (req, res) -> {
                        throw new IllegalStateException("falha no meio da requisicao");
                    });
        } catch (IllegalStateException | IOException | ServletException esperado) {
            // A excecao nao e o objeto do teste; o que importa e o estado deixado para tras.
        }

        // Threads sao reaproveitadas pelo pool. Sem a limpeza, a proxima requisicao atendida por
        // esta mesma thread herdaria o id e seus logs apontariam para a requisicao errada — pior
        // que nao ter id nenhum, porque parece correto.
        assertThat(MDC.get(CorrelacaoDeRequisicao.CHAVE_MDC)).isNull();
    }

    /** Cadeia que apenas anota o que estava no MDC no momento em que foi chamada. */
    private MockFilterChain capturarDoMdc(String[] destino) {
        return new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest requisicao,
                                 jakarta.servlet.ServletResponse resposta)
                    throws IOException, ServletException {

                destino[0] = MDC.get(CorrelacaoDeRequisicao.CHAVE_MDC);
                super.doFilter(requisicao, resposta);
            }
        };
    }
}
