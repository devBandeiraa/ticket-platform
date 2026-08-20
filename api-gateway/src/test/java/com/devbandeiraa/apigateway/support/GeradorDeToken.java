package com.devbandeiraa.apigateway.support;

import com.devbandeiraa.shared.security.Role;
import io.jsonwebtoken.Jwts;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import io.jsonwebtoken.security.Keys;

/**
 * Emite tokens para os testes, com a mesma chave e o mesmo emissor da configuracao local.
 *
 * <p>Assinar de verdade, em vez de simular o {@code JwtTokenReader}, e o ponto: um leitor
 * simulado devolveria a identidade que o teste mandasse e passaria igual se o gateway nem
 * conferisse a assinatura. Aqui, um token adulterado precisa ser recusado pela criptografia.
 */
public final class GeradorDeToken {

    /** O mesmo default do application.yml — trocar la sem trocar aqui quebra os testes, de proposito. */
    public static final String SEGREDO = "desenvolvimento-local-troque-este-segredo-antes-de-publicar";
    public static final String EMISSOR = "ticket-platform-auth";

    private static final SecretKey CHAVE = Keys.hmacShaKeyFor(SEGREDO.getBytes(StandardCharsets.UTF_8));

    private GeradorDeToken() {
    }

    public static String valido(UUID usuario, String email, Role papel) {
        return construir(usuario, email, papel, EMISSOR, Instant.now().plus(Duration.ofMinutes(15)), CHAVE);
    }

    public static String expirado(UUID usuario) {
        return construir(usuario, "expirado@teste.com", Role.USER, EMISSOR,
                Instant.now().minus(Duration.ofMinutes(1)), CHAVE);
    }

    /** Bem formado e no prazo, mas assinado com outra chave: o caso de token forjado. */
    public static String assinadoPorOutraChave(UUID usuario) {
        SecretKey intrusa = Keys.hmacShaKeyFor(
                "outra-chave-completamente-diferente-com-32-bytes".getBytes(StandardCharsets.UTF_8));

        return construir(usuario, "intruso@teste.com", Role.ADMIN, EMISSOR,
                Instant.now().plus(Duration.ofMinutes(15)), intrusa);
    }

    /** Assinatura certa, emissor errado: token de outro sistema que compartilhe o segredo. */
    public static String deOutroEmissor(UUID usuario) {
        return construir(usuario, "alheio@teste.com", Role.USER, "outro-sistema",
                Instant.now().plus(Duration.ofMinutes(15)), CHAVE);
    }

    private static String construir(
            UUID usuario, String email, Role papel, String emissor, Instant expiracao, SecretKey chave) {

        return Jwts.builder()
                .subject(usuario.toString())
                .claim("email", email)
                .claim("role", papel.name())
                .issuer(emissor)
                .issuedAt(Date.from(Instant.now().minusSeconds(1)))
                .expiration(Date.from(expiracao))
                .signWith(chave)
                .compact();
    }
}
