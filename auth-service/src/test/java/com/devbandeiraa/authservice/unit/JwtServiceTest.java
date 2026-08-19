package com.devbandeiraa.authservice.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.devbandeiraa.authservice.domain.Role;
import com.devbandeiraa.authservice.domain.User;
import com.devbandeiraa.authservice.security.AuthenticatedUser;
import com.devbandeiraa.authservice.security.JwtProperties;
import com.devbandeiraa.authservice.security.JwtService;
import com.devbandeiraa.authservice.support.UsuarioDeTeste;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Testes de unidade da emissao e validacao de access tokens. */
class JwtServiceTest {

    private static final String SEGREDO = "segredo-de-teste-com-mais-de-32-bytes-para-o-hs256";
    private static final String OUTRO_SEGREDO = "outro-segredo-de-teste-igualmente-longo-o-suficiente";
    private static final String EMISSOR = "ticket-platform-auth";

    private final JwtService jwtService = criarServico(SEGREDO, EMISSOR, Duration.ofMinutes(15));

    @Test
    @DisplayName("token emitido carrega id, e-mail e papel do usuario")
    void deveCarregarIdentidadeDoUsuario() {
        UUID id = UUID.randomUUID();
        User usuario = UsuarioDeTeste.comIdEPapel(id, "joao@email.com", Role.ADMIN);

        AuthenticatedUser extraido = jwtService.extrairUsuario(jwtService.gerarAccessToken(usuario));

        assertThat(extraido.id()).isEqualTo(id);
        assertThat(extraido.email()).isEqualTo("joao@email.com");
        assertThat(extraido.role()).isEqualTo(Role.ADMIN);
    }

    @Test
    @DisplayName("token expirado e recusado")
    void deveRecusarTokenExpirado() {
        // TTL negativo produz um token que ja nasce vencido, evitando um sleep no teste.
        JwtService servicoComTokenVencido = criarServico(SEGREDO, EMISSOR, Duration.ofSeconds(-60));
        String token = servicoComTokenVencido.gerarAccessToken(UsuarioDeTeste.comum("joao@email.com"));

        assertThatThrownBy(() -> jwtService.extrairUsuario(token))
                .isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    @DisplayName("token assinado com outra chave e recusado")
    void deveRecusarTokenDeOutraChave() {
        JwtService servicoIntruso = criarServico(OUTRO_SEGREDO, EMISSOR, Duration.ofMinutes(15));
        String token = servicoIntruso.gerarAccessToken(UsuarioDeTeste.comum("joao@email.com"));

        assertThatThrownBy(() -> jwtService.extrairUsuario(token))
                .isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("token adulterado e recusado")
    void deveRecusarTokenAdulterado() {
        String token = jwtService.gerarAccessToken(UsuarioDeTeste.comum("joao@email.com"));

        // Altera um caractere do payload sem recalcular a assinatura, que e exatamente o que
        // um atacante tentaria para trocar o proprio papel para ADMIN.
        String[] partes = token.split("\\.");
        String payloadAdulterado = partes[1].substring(0, partes[1].length() - 2)
                + (partes[1].endsWith("A") ? "BB" : "AA");
        String adulterado = partes[0] + "." + payloadAdulterado + "." + partes[2];

        assertThatThrownBy(() -> jwtService.extrairUsuario(adulterado))
                .isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("token de outro emissor e recusado")
    void deveRecusarTokenDeOutroEmissor() {
        JwtService servicoDeOutroSistema = criarServico(SEGREDO, "outro-sistema", Duration.ofMinutes(15));
        String token = servicoDeOutroSistema.gerarAccessToken(UsuarioDeTeste.comum("joao@email.com"));

        assertThatThrownBy(() -> jwtService.extrairUsuario(token))
                .isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("segredo curto demais para HS256 e rejeitado na configuracao")
    void deveRejeitarSegredoCurto() {
        assertThatThrownBy(() -> new JwtProperties("curto", Duration.ofMinutes(15), Duration.ofDays(7), EMISSOR))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 bytes");
    }

    @Test
    @DisplayName("informa ao cliente os segundos de validade do access token")
    void deveInformarValidadeEmSegundos() {
        assertThat(jwtService.segundosDeValidadeDoAccessToken()).isEqualTo(900);
    }

    @Test
    @DisplayName("assina sempre em HS256, independente do tamanho do segredo")
    void deveAssinarSempreEmHs256() {
        // Um segredo bem mais longo faria o jjwt inferir HS384 ou HS512 se o algoritmo nao
        // estivesse fixado — e o gateway, esperando HS256, recusaria todos os tokens.
        String segredoLongo = "segredo-muito-mais-longo-do-que-o-necessario-para-o-hs256-e-ate-para-o-hs512-tambem";
        JwtService servicoComSegredoLongo = criarServico(segredoLongo, EMISSOR, Duration.ofMinutes(15));

        String token = servicoComSegredoLongo.gerarAccessToken(UsuarioDeTeste.comum("joao@email.com"));
        String cabecalho = new String(
                java.util.Base64.getUrlDecoder().decode(token.split("\\.")[0]),
                java.nio.charset.StandardCharsets.UTF_8);

        assertThat(cabecalho).contains("\"alg\":\"HS256\"");
    }

    private static JwtService criarServico(String segredo, String emissor, Duration validade) {
        return new JwtService(new JwtProperties(segredo, validade, Duration.ofDays(7), emissor));
    }
}
