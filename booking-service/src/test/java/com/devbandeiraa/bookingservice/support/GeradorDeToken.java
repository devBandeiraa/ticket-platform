package com.devbandeiraa.bookingservice.support;

import com.devbandeiraa.shared.security.Role;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;

/**
 * Emite tokens para os testes, assinando com o mesmo segredo do {@code application.yml}.
 *
 * <p>Os testes deste servico nao envolvem o auth-service, e essa independencia e o ponto: o
 * booking-service precisa autorizar com base apenas no que vem no token. Se um teste daqui
 * exigisse o auth-service no ar, isso ja indicaria acoplamento indevido.
 */
public final class GeradorDeToken {

    public static final String SEGREDO = "desenvolvimento-local-troque-este-segredo-antes-de-publicar";
    public static final String EMISSOR = "ticket-platform-auth";

    private GeradorDeToken() {
    }

    public static String deUsuario(UUID id) {
        return construir(id, "usuario@email.com", Role.USER);
    }

    public static String deUsuarioComum() {
        return construir(UUID.randomUUID(), "usuario@email.com", Role.USER);
    }

    public static String deAdmin() {
        return construir(UUID.randomUUID(), "admin@ticket.dev", Role.ADMIN);
    }

    private static String construir(UUID id, String email, Role papel) {
        SecretKey chave = Keys.hmacShaKeyFor(SEGREDO.getBytes(StandardCharsets.UTF_8));
        Instant agora = Instant.now();

        return Jwts.builder()
                .issuer(EMISSOR)
                .subject(id.toString())
                .claim("email", email)
                .claim("role", papel.name())
                .issuedAt(Date.from(agora))
                .expiration(Date.from(agora.plus(Duration.ofMinutes(15))))
                .signWith(chave, Jwts.SIG.HS256)
                .compact();
    }
}
