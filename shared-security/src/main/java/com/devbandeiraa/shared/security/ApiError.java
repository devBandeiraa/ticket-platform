package com.devbandeiraa.shared.security;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.Map;

/**
 * Corpo unico de erro da API, no formato acordado em {@code docs/00-mapeamento.md}.
 *
 * <p>Compartilhado entre os servicos por um motivo pratico: o frontend trata uma unica forma de
 * erro, venha ela do auth-service, do event-service ou do gateway. Se cada servico definisse o
 * proprio formato, o cliente precisaria de um tratamento por origem.
 *
 * @param error   codigo estavel e legivel por maquina (ex: {@code EMAIL_ALREADY_REGISTERED}),
 *                para o frontend decidir o que fazer sem depender do texto da mensagem
 * @param fields  erros por campo, presentes apenas em falha de validacao
 * @param traceId correlaciona a resposta com a linha de log correspondente
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        Map<String, String> fields,
        String traceId) {

    public static ApiError de(int status, String error, String message, String path, String traceId) {
        return new ApiError(Instant.now(), status, error, message, path, null, traceId);
    }

    public static ApiError deValidacao(String path, Map<String, String> fields, String traceId) {
        return new ApiError(
                Instant.now(),
                400,
                "VALIDATION_ERROR",
                "A requisicao contem campos invalidos",
                path,
                fields,
                traceId);
    }
}
