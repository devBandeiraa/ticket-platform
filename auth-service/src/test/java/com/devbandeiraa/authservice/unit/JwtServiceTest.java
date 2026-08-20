package com.devbandeiraa.authservice.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.devbandeiraa.authservice.domain.User;
import com.devbandeiraa.authservice.security.JwtService;
import com.devbandeiraa.authservice.security.TokenLifetimeProperties;
import com.devbandeiraa.authservice.support.UsuarioDeTeste;
import com.devbandeiraa.shared.security.AuthenticatedUser;
import com.devbandeiraa.shared.security.JwtProperties;
import com.devbandeiraa.shared.security.JwtTokenReader;
import com.devbandeiraa.shared.security.Role;
import io.jsonwebtoken.ExpiredJwtException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Testes da emissao de access tokens.
 *
 * <p>Os casos de recusa — token expirado, adulterado, de outra chave ou de outro emissor — ficam
 * no {@code JwtTokenReaderTest} do modulo compartilhado, junto da classe que os implementa. Aqui
 * so se verifica o que o auth-service produz.
 */
class JwtServiceTest {

    private static final String SEGREDO = "segredo-de-teste-com-mais-de-32-bytes-para-o-hs256";
    private static final String EMISSOR = "ticket-platform-auth";

    private final JwtService jwtService = criarEmissor(SEGREDO, Duration.ofMinutes(15));
    private final JwtTokenReader reader = new JwtTokenReader(new JwtProperties(SEGREDO, EMISSOR));

    @Test
    @DisplayName("token emitido carrega id, e-mail e papel do usuario")
    void deveCarregarIdentidadeDoUsuario() {
        UUID id = UUID.randomUUID();
        User usuario = UsuarioDeTeste.comIdEPapel(id, "joao@email.com", Role.ADMIN);

        AuthenticatedUser extraido = reader.extrairUsuario(jwtService.gerarAccessToken(usuario));

        assertThat(extraido.id()).isEqualTo(id);
        assertThat(extraido.email()).isEqualTo("joao@email.com");
        assertThat(extraido.role()).isEqualTo(Role.ADMIN);
    }

    @Test
    @DisplayName("respeita o tempo de vida configurado")
    void deveRespeitarOTempoDeVidaConfigurado() {
        // TTL negativo produz um token que ja nasce vencido, evitando um sleep no teste.
        JwtService emissorDeTokenVencido = criarEmissor(SEGREDO, Duration.ofSeconds(-60));
        String token = emissorDeTokenVencido.gerarAccessToken(UsuarioDeTeste.comum("joao@email.com"));

        assertThatThrownBy(() -> reader.extrairUsuario(token)).isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    @DisplayName("assina sempre em HS256, independente do tamanho do segredo")
    void deveAssinarSempreEmHs256() {
        // Um segredo bem mais longo faria o jjwt inferir HS384 ou HS512 se o algoritmo nao
        // estivesse fixado — e os demais servicos, esperando HS256, recusariam todos os tokens.
        String segredoLongo = "segredo-muito-mais-longo-do-que-o-necessario-para-o-hs256-e-ate-para-o-hs512-tambem";
        JwtService emissorComSegredoLongo = criarEmissor(segredoLongo, Duration.ofMinutes(15));

        String token = emissorComSegredoLongo.gerarAccessToken(UsuarioDeTeste.comum("joao@email.com"));
        String cabecalho = new String(
                Base64.getUrlDecoder().decode(token.split("\\.")[0]), StandardCharsets.UTF_8);

        assertThat(cabecalho).contains("\"alg\":\"HS256\"");
    }

    @Test
    @DisplayName("informa ao cliente os segundos de validade do access token")
    void deveInformarValidadeEmSegundos() {
        assertThat(jwtService.segundosDeValidadeDoAccessToken()).isEqualTo(900);
    }

    @Test
    @DisplayName("configuracao sem tempo de vida e rejeitada na subida")
    void deveRejeitarValidadeAusente() {
        assertThatThrownBy(() -> new TokenLifetimeProperties(null, Duration.ofDays(7)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("access-token-ttl");
    }

    private static JwtService criarEmissor(String segredo, Duration validadeDoAccessToken) {
        return new JwtService(
                new JwtProperties(segredo, EMISSOR),
                new TokenLifetimeProperties(validadeDoAccessToken, Duration.ofDays(7)));
    }
}
