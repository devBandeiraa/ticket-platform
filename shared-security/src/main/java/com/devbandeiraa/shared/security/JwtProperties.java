package com.devbandeiraa.shared.security;

import java.nio.charset.StandardCharsets;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuracao necessaria para <em>validar</em> um token, sob o prefixo {@code jwt}.
 *
 * <p>Contem so o que todo servico precisa. O tempo de vida dos tokens fica no auth-service, que
 * e o unico que os emite — um servico que apenas valida nao tem o que fazer com essa informacao.
 *
 * <p>A verificacao e feita no construtor, e nao por anotacoes de Bean Validation, para que este
 * modulo nao arraste uma dependencia de validacao para servicos que talvez nem a usem. O efeito
 * pratico e o mesmo: configuracao invalida derruba a aplicacao na subida, com mensagem dizendo
 * exatamente o que corrigir.
 *
 * @param secret segredo do HS256, o mesmo em todos os servicos: e o que permite a cada um
 *               validar o token sem chamar o auth-service
 */
@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(String secret, String issuer) {

    /** Minimo exigido pelo HS256: a chave precisa ter ao menos o tamanho do hash, 256 bits. */
    private static final int TAMANHO_MINIMO_DO_SEGREDO_EM_BYTES = 32;

    public JwtProperties {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("jwt.secret nao foi configurado");
        }
        if (secret.getBytes(StandardCharsets.UTF_8).length < TAMANHO_MINIMO_DO_SEGREDO_EM_BYTES) {
            throw new IllegalStateException(
                    "jwt.secret precisa ter no minimo " + TAMANHO_MINIMO_DO_SEGREDO_EM_BYTES
                            + " bytes para o HS256. Ajuste a variavel de ambiente JWT_SECRET.");
        }
        if (issuer == null || issuer.isBlank()) {
            throw new IllegalStateException("jwt.issuer nao foi configurado");
        }
    }
}
