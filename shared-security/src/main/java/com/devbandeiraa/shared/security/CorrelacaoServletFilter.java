package com.devbandeiraa.shared.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Poe o {@code X-Request-Id} da requisicao no MDC, para que toda linha de log do servico o carregue.
 *
 * <p>Sem o MDC, imprimir o id exigiria passa-lo como parametro de metodo em metodo ate o ponto do
 * log — poluindo assinaturas inteiras com um argumento que nada tem a ver com o negocio, e ainda
 * assim faltando nas linhas emitidas pelo proprio Spring, que nao conhece esse parametro.
 *
 * <p>Complementa o filtro reativo do gateway. Este le o cabecalho que aquele injetou; quando o
 * servico e chamado diretamente, sem gateway no caminho — o que acontece o tempo todo em
 * desenvolvimento e nos testes —, gera um id proprio, de modo que os logs nunca ficam sem
 * identificacao.
 */
public class CorrelacaoServletFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            HttpServletRequest requisicao, HttpServletResponse resposta, FilterChain cadeia)
            throws ServletException, IOException {

        String id = CorrelacaoDeRequisicao.normalizar(
                requisicao.getHeader(CorrelacaoDeRequisicao.CABECALHO));

        MDC.put(CorrelacaoDeRequisicao.CHAVE_MDC, id);

        // Devolvido ao chamador para que quem recebeu um erro possa informar o id, e a linha de
        // log correspondente seja encontrada sem adivinhacao.
        resposta.setHeader(CorrelacaoDeRequisicao.CABECALHO, id);

        try {
            cadeia.doFilter(requisicao, resposta);
        } finally {
            // O finally e obrigatorio, nao defensivo. O MDC vive numa ThreadLocal e as threads sao
            // reaproveitadas pelo pool: sem a limpeza, a proxima requisicao atendida por esta
            // mesma thread herdaria o id da anterior e seus logs apontariam para a requisicao
            // errada — pior que nao ter id nenhum, porque parece correto.
            MDC.remove(CorrelacaoDeRequisicao.CHAVE_MDC);
        }
    }
}
