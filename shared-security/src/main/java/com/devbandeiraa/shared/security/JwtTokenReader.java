package com.devbandeiraa.shared.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import javax.crypto.SecretKey;

/**
 * Valida access tokens e extrai a identidade que eles carregam.
 *
 * <p>Lado da leitura do JWT, usado por todo servico que precisa autenticar requisicoes. A
 * emissao fica no auth-service: aqui nao ha como criar um token, apenas conferir um.
 */
public class JwtTokenReader {

    static final String CLAIM_EMAIL = "email";
    static final String CLAIM_ROLE = "role";

    /** Nome do padrao OpenID Connect. Ausente em token emitido antes da Fase 23. */
    static final String CLAIM_NAME = "name";

    private final SecretKey chave;
    private final String emissorEsperado;

    public JwtTokenReader(JwtProperties propriedades) {
        this.chave = Keys.hmacShaKeyFor(propriedades.secret().getBytes(StandardCharsets.UTF_8));
        this.emissorEsperado = propriedades.issuer();
    }

    /**
     * Valida assinatura, prazo e emissor, devolvendo quem o token representa.
     *
     * @throws JwtException se o token estiver expirado, adulterado, assinado com outra chave ou
     *                      emitido por outro sistema
     */
    public AuthenticatedUser extrairUsuario(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(chave)
                .requireIssuer(emissorEsperado)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        return new AuthenticatedUser(
                UUID.fromString(claims.getSubject()),
                claims.get(CLAIM_EMAIL, String.class),
                Role.valueOf(claims.get(CLAIM_ROLE, String.class)),
                // Sem exigir presenca: um token valido emitido ontem nao tem este claim, e
                // recusa-lo deslogaria todo mundo no momento do deploy por um campo que so
                // serve para escrever um nome na tela.
                claims.get(CLAIM_NAME, String.class));
    }
}
