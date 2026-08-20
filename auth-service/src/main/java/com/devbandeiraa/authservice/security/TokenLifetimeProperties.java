package com.devbandeiraa.authservice.security;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Tempo de vida dos tokens, sob o mesmo prefixo {@code jwt}.
 *
 * <p>Separado do {@code JwtProperties} compartilhado porque so faz sentido aqui: o auth-service e
 * o unico servico que emite tokens. Um servico que apenas valida nao tem o que fazer com o TTL,
 * ja que a expiracao vem gravada dentro do proprio token.
 *
 * <p>Dois {@code @ConfigurationProperties} sobre o mesmo prefixo convivem sem conflito: cada um
 * liga apenas os campos que declara.
 *
 * @param accessTokenTtl  curto de proposito: um access token nao pode ser revogado, entao a
 *                        janela de estrago de um token vazado e o proprio tempo de vida dele
 * @param refreshTokenTtl longo, mas revogavel — o token fica no banco e o logout o invalida
 */
@ConfigurationProperties(prefix = "jwt")
public record TokenLifetimeProperties(Duration accessTokenTtl, Duration refreshTokenTtl) {

    public TokenLifetimeProperties {
        if (accessTokenTtl == null || accessTokenTtl.isZero()) {
            throw new IllegalStateException("jwt.access-token-ttl nao foi configurado");
        }
        if (refreshTokenTtl == null || refreshTokenTtl.isZero() || refreshTokenTtl.isNegative()) {
            throw new IllegalStateException("jwt.refresh-token-ttl nao foi configurado");
        }
    }
}
