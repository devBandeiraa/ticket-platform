package com.devbandeiraa.apigateway.web;

import com.devbandeiraa.shared.security.ApiError;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Escreve erros do gateway no mesmo formato {@link ApiError} dos demais servicos.
 *
 * <p>Existe porque o gateway responde por conta propria em dois casos — token invalido e limite
 * excedido — e nesses o cliente nunca chega a falar com o servico de destino. Sem isto, o frontend
 * teria que tratar um formato para erros de negocio e outro para erros da borda.
 *
 * <p>Nao reusa o {@code SecurityErrorResponder} do shared-security porque aquele escreve em
 * {@code HttpServletResponse}, que nao existe aqui. O que se compartilha e o contrato — o record
 * {@link ApiError} — e nao o mecanismo de escrita, que e necessariamente diferente entre servlet
 * e reativo.
 */
@Component
public class EscritorDeErro {

    private final ObjectMapper objectMapper;

    public EscritorDeErro(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** Encerra a requisicao no gateway, com status e corpo de erro. */
    public Mono<Void> escrever(
            ServerWebExchange troca, HttpStatus status, String codigo, String mensagem, String traceId) {

        ServerHttpResponse resposta = troca.getResponse();
        resposta.setStatusCode(status);
        resposta.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        // fromSupplier adia a alocacao do buffer para o momento da escrita: se o cliente
        // desistir antes disso, nenhum buffer chega a ser alocado e nao ha o que vazar.
        return resposta.writeWith(Mono.fromSupplier(
                () -> serializar(troca, status, codigo, mensagem, traceId, resposta.bufferFactory())));
    }

    /**
     * Serializa o corpo sem tocar na resposta, para quem ja esta dentro do fluxo de escrita e
     * precisa apenas dos bytes.
     */
    public DataBuffer serializar(
            ServerWebExchange troca, HttpStatus status, String codigo, String mensagem,
            String traceId, DataBufferFactory fabrica) {

        ApiError erro = ApiError.de(
                status.value(), codigo, mensagem, troca.getRequest().getPath().value(), traceId);

        try {
            return fabrica.wrap(objectMapper.writeValueAsBytes(erro));
        } catch (JsonProcessingException falha) {
            // ApiError e um record de tipos triviais; se isto falhar, o problema esta no
            // ObjectMapper. Ainda assim, devolver algo e melhor que estourar no meio da escrita.
            return fabrica.wrap(("{\"error\":\"" + codigo + "\"}").getBytes(StandardCharsets.UTF_8));
        }
    }

    /** Curto de proposito: serve para casar a resposta com a linha de log, nao para ser unico. */
    public static String gerarTraceId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
