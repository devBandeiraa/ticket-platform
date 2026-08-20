package com.devbandeiraa.shared.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Testes da validacao de token.
 *
 * <p>Cada caso constroi o token diretamente com o jjwt, em vez de usar o emissor do
 * auth-service: o leitor precisa se defender de qualquer token que chegue pela rede, inclusive
 * de um forjado por terceiros, e nao apenas dos que a propria plataforma produz.
 */
class JwtTokenReaderTest {

    private static final String SEGREDO = "segredo-de-teste-com-mais-de-32-bytes-para-o-hs256";
    private static final String OUTRO_SEGREDO = "outro-segredo-de-teste-igualmente-longo-o-suficiente";
    private static final String EMISSOR = "ticket-platform-auth";

    private final JwtTokenReader reader = new JwtTokenReader(new JwtProperties(SEGREDO, EMISSOR));

    @Test
    @DisplayName("extrai id, e-mail e papel de um token valido")
    void deveExtrairIdentidade() {
        UUID id = UUID.randomUUID();
        String token = construirToken(SEGREDO, EMISSOR, id, "joao@email.com", "ADMIN", Duration.ofMinutes(15));

        AuthenticatedUser usuario = reader.extrairUsuario(token);

        assertThat(usuario.id()).isEqualTo(id);
        assertThat(usuario.email()).isEqualTo("joao@email.com");
        assertThat(usuario.role()).isEqualTo(Role.ADMIN);
        assertThat(usuario.isAdmin()).isTrue();
    }

    @Test
    @DisplayName("token expirado e recusado")
    void deveRecusarTokenExpirado() {
        String token = construirToken(SEGREDO, EMISSOR, UUID.randomUUID(), "joao@email.com", "USER",
                Duration.ofSeconds(-60));

        assertThatThrownBy(() -> reader.extrairUsuario(token)).isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    @DisplayName("token assinado com outra chave e recusado")
    void deveRecusarTokenDeOutraChave() {
        String token = construirToken(OUTRO_SEGREDO, EMISSOR, UUID.randomUUID(), "joao@email.com", "USER",
                Duration.ofMinutes(15));

        assertThatThrownBy(() -> reader.extrairUsuario(token)).isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("token emitido por outro sistema e recusado")
    void deveRecusarTokenDeOutroEmissor() {
        String token = construirToken(SEGREDO, "outro-sistema", UUID.randomUUID(), "joao@email.com", "USER",
                Duration.ofMinutes(15));

        assertThatThrownBy(() -> reader.extrairUsuario(token)).isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("token com papel adulterado para ADMIN e recusado")
    void deveRecusarEscalacaoDePrivilegio() {
        // Este e o ataque que mais importa barrar: pegar um token legitimo de USER e reescrever
        // o papel para ADMIN. Sem recalcular a assinatura, a validacao tem que rejeitar.
        String token = construirToken(SEGREDO, EMISSOR, UUID.randomUUID(), "joao@email.com", "USER",
                Duration.ofMinutes(15));

        String[] partes = token.split("\\.");
        String payloadOriginal = new String(
                java.util.Base64.getUrlDecoder().decode(partes[1]), StandardCharsets.UTF_8);
        String payloadAdulterado = payloadOriginal.replace("\"role\":\"USER\"", "\"role\":\"ADMIN\"");
        String tokenAdulterado = partes[0] + "."
                + java.util.Base64.getUrlEncoder().withoutPadding()
                        .encodeToString(payloadAdulterado.getBytes(StandardCharsets.UTF_8))
                + "." + partes[2];

        assertThatThrownBy(() -> reader.extrairUsuario(tokenAdulterado)).isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("segredo curto demais para HS256 e rejeitado na configuracao")
    void deveRejeitarSegredoCurto() {
        assertThatThrownBy(() -> new JwtProperties("curto", EMISSOR))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 bytes");
    }

    @Test
    @DisplayName("configuracao sem emissor e rejeitada na subida")
    void deveRejeitarEmissorAusente() {
        assertThatThrownBy(() -> new JwtProperties(SEGREDO, "  "))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("issuer");
    }

    private static String construirToken(
            String segredo, String emissor, UUID id, String email, String papel, Duration validade) {

        SecretKey chave = Keys.hmacShaKeyFor(segredo.getBytes(StandardCharsets.UTF_8));
        Instant agora = Instant.now();

        return Jwts.builder()
                .issuer(emissor)
                .subject(id.toString())
                .claim("email", email)
                .claim("role", papel)
                .issuedAt(Date.from(agora))
                .expiration(Date.from(agora.plus(validade)))
                .signWith(chave, Jwts.SIG.HS256)
                .compact();
    }
}
