package com.devbandeiraa.authservice.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.devbandeiraa.authservice.repository.RefreshTokenRepository;
import com.devbandeiraa.authservice.repository.UserRepository;
import com.devbandeiraa.authservice.support.PostgresContainerConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Testes de integracao do fluxo de autenticacao completo, contra um PostgreSQL real.
 *
 * <p>Sem {@code @Transactional} de proposito: varios cenarios exercitam falhas que marcam a
 * transacao como rollback-only, o que derrubaria as chamadas seguintes do mesmo teste. O estado
 * e limpo antes de cada teste.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresContainerConfig.class)
class AuthenticationFlowIntegrationTest {

    private static final String EMAIL = "joao@email.com";
    private static final String SENHA = "senhaSegura123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @BeforeEach
    void limparEstado() {
        refreshTokenRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("login devolve access token, refresh token e a validade")
    void deveAutenticarUsuarioCadastrado() throws Exception {
        cadastrar(EMAIL, SENHA);

        mockMvc.perform(login(EMAIL, SENHA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900));
    }

    @Test
    @DisplayName("login e insensivel a caixa do e-mail")
    void deveAutenticarIgnorandoCaixaDoEmail() throws Exception {
        cadastrar(EMAIL, SENHA);

        mockMvc.perform(login("  JOAO@Email.COM  ", SENHA))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("senha errada e e-mail inexistente devolvem exatamente a mesma resposta")
    void naoDeveRevelarSeOEmailExiste() throws Exception {
        cadastrar(EMAIL, SENHA);

        String corpoSenhaErrada = mockMvc.perform(login(EMAIL, "senhaErrada999"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("INVALID_CREDENTIALS"))
                .andReturn().getResponse().getContentAsString();

        String corpoEmailInexistente = mockMvc.perform(login("ninguem@email.com", SENHA))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("INVALID_CREDENTIALS"))
                .andReturn().getResponse().getContentAsString();

        // Comparar as mensagens: se diferirem, um atacante descobre quais e-mails existem.
        assertThat(extrairCampo(corpoSenhaErrada, "message"))
                .isEqualTo(extrairCampo(corpoEmailInexistente, "message"));
    }

    @Test
    @DisplayName("rota protegida sem token devolve 401, e nao 403")
    void deveExigirAutenticacaoCom401() throws Exception {
        mockMvc.perform(get("/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    @DisplayName("rota protegida com token valido devolve a identidade do token")
    void deveAceitarTokenValido() throws Exception {
        cadastrar(EMAIL, SENHA);
        String accessToken = autenticarERetornar(EMAIL, SENHA).get("accessToken").asText();

        mockMvc.perform(get("/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(EMAIL))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.id").isNotEmpty());
    }

    @Test
    @DisplayName("token adulterado e recusado com 401")
    void deveRecusarTokenAdulterado() throws Exception {
        cadastrar(EMAIL, SENHA);
        String accessToken = autenticarERetornar(EMAIL, SENHA).get("accessToken").asText();

        mockMvc.perform(get("/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken + "adulterado"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("refresh devolve novo par e invalida o token usado (rotacao)")
    void deveRotacionarRefreshToken() throws Exception {
        cadastrar(EMAIL, SENHA);
        String refreshOriginal = autenticarERetornar(EMAIL, SENHA).get("refreshToken").asText();

        String novoRefresh = mockMvc.perform(refresh(refreshOriginal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        assertThat(extrairCampo(novoRefresh, "refreshToken")).isNotEqualTo(refreshOriginal);

        // O token ja usado nao pode servir de novo: e o que limita o estrago de uma
        // interceptacao a um unico uso.
        mockMvc.perform(refresh(refreshOriginal))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("INVALID_REFRESH_TOKEN"));
    }

    @Test
    @DisplayName("reapresentar um refresh ja usado derruba todas as sessoes do usuario")
    void deveDerrubarSessoesAoDetectarReuso() throws Exception {
        cadastrar(EMAIL, SENHA);
        String refreshOriginal = autenticarERetornar(EMAIL, SENHA).get("refreshToken").asText();

        String refreshAtual = extrairCampo(
                mockMvc.perform(refresh(refreshOriginal)).andReturn().getResponse().getContentAsString(),
                "refreshToken");

        // Alguem tenta usar a copia antiga: sinal de que o token vazou.
        mockMvc.perform(refresh(refreshOriginal))
                .andExpect(status().isUnauthorized());

        // Como nao ha como saber quem e o dono legitimo, ate o token atual e invalidado.
        mockMvc.perform(refresh(refreshAtual))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("refresh token desconhecido devolve 401")
    void deveRecusarRefreshTokenDesconhecido() throws Exception {
        mockMvc.perform(refresh("token-que-nunca-existiu"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("INVALID_REFRESH_TOKEN"));
    }

    @Test
    @DisplayName("logout invalida o refresh token")
    void deveInvalidarRefreshTokenNoLogout() throws Exception {
        cadastrar(EMAIL, SENHA);
        String refreshToken = autenticarERetornar(EMAIL, SENHA).get("refreshToken").asText();

        mockMvc.perform(logout(refreshToken)).andExpect(status().isNoContent());

        mockMvc.perform(refresh(refreshToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("INVALID_REFRESH_TOKEN"));
    }

    @Test
    @DisplayName("logout e idempotente, mesmo com token desconhecido")
    void deveTratarLogoutComoIdempotente() throws Exception {
        cadastrar(EMAIL, SENHA);
        String refreshToken = autenticarERetornar(EMAIL, SENHA).get("refreshToken").asText();

        mockMvc.perform(logout(refreshToken)).andExpect(status().isNoContent());
        mockMvc.perform(logout(refreshToken)).andExpect(status().isNoContent());
        mockMvc.perform(logout("token-inexistente")).andExpect(status().isNoContent());
    }

    // ---------- auxiliares ----------

    private void cadastrar(String email, String senha) throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("email", email, "password", senha, "fullName", "Joao Silva"))))
                .andExpect(status().isCreated());
    }

    private JsonNode autenticarERetornar(String email, String senha) throws Exception {
        String corpo = mockMvc.perform(login(email, senha))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(corpo);
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
            login(String email, String senha) throws Exception {
        return post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("email", email, "password", senha)));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
            refresh(String refreshToken) throws Exception {
        return post("/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("refreshToken", refreshToken)));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
            logout(String refreshToken) throws Exception {
        return post("/auth/logout")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("refreshToken", refreshToken)));
    }

    private String extrairCampo(String json, String campo) throws Exception {
        return objectMapper.readTree(json).get(campo).asText();
    }
}
