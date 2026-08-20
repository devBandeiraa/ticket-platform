package com.devbandeiraa.eventservice.support;

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
 * Emite tokens para os testes, assinando com o mesmo segredo configurado no
 * {@code application.yml}.
 *
 * <p>Os testes deste servico nao envolvem o auth-service, e essa independencia e o ponto: o
 * event-service precisa autorizar com base apenas no que vem no token, sem consultar ninguem.
 * Se um teste daqui precisasse do auth-service no ar, isso ja indicaria acoplamento indevido.
 *
 * <p>Permite ainda produzir tokens que o auth-service jamais emitiria — assinados com outra
 * chave, ou vencidos — que sao justamente os casos que a autorizacao precisa recusar.
 */
public final class GeradorDeToken {

    /** Mesmo valor padrao do application.yml. */
    public static final String SEGREDO = "desenvolvimento-local-troque-este-segredo-antes-de-publicar";
    public static final String EMISSOR = "ticket-platform-auth";

    private GeradorDeToken() {
    }

    public static String deAdmin() {
        return de(UUID.randomUUID(), "admin@ticket.dev", Role.ADMIN);
    }

    public static String deAdmin(UUID id) {
        return de(id, "admin@ticket.dev", Role.ADMIN);
    }

    public static String deUsuarioComum() {
        return de(UUID.randomUUID(), "usuario@email.com", Role.USER);
    }

    public static String de(UUID id, String email, Role papel) {
        return construir(SEGREDO, EMISSOR, id, email, papel, Duration.ofMinutes(15));
    }

    /** Token assinado com outra chave: simula um token forjado por terceiros. */
    public static String assinadoComOutraChave() {
        return construir("outro-segredo-completamente-diferente-e-longo-o-bastante",
                EMISSOR, UUID.randomUUID(), "invasor@email.com", Role.ADMIN, Duration.ofMinutes(15));
    }

    public static String expirado() {
        return construir(SEGREDO, EMISSOR, UUID.randomUUID(), "admin@ticket.dev", Role.ADMIN,
                Duration.ofSeconds(-60));
    }

    private static String construir(String segredo, String emissor, UUID id, String email,
                                    Role papel, Duration validade) {
        SecretKey chave = Keys.hmacShaKeyFor(segredo.getBytes(StandardCharsets.UTF_8));
        Instant agora = Instant.now();

        return Jwts.builder()
                .issuer(emissor)
                .subject(id.toString())
                .claim("email", email)
                .claim("role", papel.name())
                .issuedAt(Date.from(agora))
                .expiration(Date.from(agora.plus(validade)))
                .signWith(chave, Jwts.SIG.HS256)
                .compact();
    }
}
