package com.devbandeiraa.authservice.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuracao da emissao de tokens, sob o prefixo {@code jwt} no application.yml.
 *
 * @param secret          segredo do HS256, compartilhado com o api-gateway, que valida os tokens
 *                        sem precisar chamar este servico
 * @param accessTokenTtl  curto de proposito: um access token nao pode ser revogado, entao a
 *                        janela de estrago de um token vazado e o proprio tempo de vida dele
 * @param refreshTokenTtl longo, mas revogavel — o token fica no banco e o logout o invalida
 */
@Validated
@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(

        @NotBlank String secret,
        @NotNull Duration accessTokenTtl,
        @NotNull Duration refreshTokenTtl,
        @NotBlank String issuer) {

    /** Minimo exigido pelo HS256: a chave precisa ter ao menos o tamanho do hash, 256 bits. */
    private static final int TAMANHO_MINIMO_DO_SEGREDO_EM_BYTES = 32;

    public JwtProperties {
        if (secret != null && secret.getBytes(StandardCharsets.UTF_8).length < TAMANHO_MINIMO_DO_SEGREDO_EM_BYTES) {
            throw new IllegalStateException(
                    "jwt.secret precisa ter no minimo " + TAMANHO_MINIMO_DO_SEGREDO_EM_BYTES
                            + " bytes para o HS256. Ajuste a variavel de ambiente JWT_SECRET.");
        }
    }
}
