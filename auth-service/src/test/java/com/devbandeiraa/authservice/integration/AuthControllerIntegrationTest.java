package com.devbandeiraa.authservice.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.devbandeiraa.authservice.domain.User;
import com.devbandeiraa.authservice.repository.UserRepository;
import com.devbandeiraa.authservice.support.PostgresContainerConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

/**
 * Testes de integracao do cadastro, contra um PostgreSQL real em container.
 *
 * <p>Anotado com {@code @Transactional} para que cada teste faca rollback ao final e nao
 * interfira nos demais.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresContainerConfig.class)
@Transactional
class AuthControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    @DisplayName("cadastra usuario e devolve 201 com os dados publicos")
    void deveCadastrarUsuario() throws Exception {
        mockMvc.perform(requisicaoDeCadastro("maria@email.com", "senhaSegura123", "Maria Souza"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.email").value("maria@email.com"))
                .andExpect(jsonPath("$.fullName").value("Maria Souza"))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty());
    }

    @Test
    @DisplayName("a resposta nunca expoe a senha nem o hash")
    void naoDeveVazarSenhaNaResposta() throws Exception {
        MvcResult resultado = mockMvc
                .perform(requisicaoDeCadastro("sigilo@email.com", "senhaSegura123", "Fulano"))
                .andExpect(status().isCreated())
                .andReturn();

        String corpo = resultado.getResponse().getContentAsString();
        assertThat(corpo).doesNotContain("senhaSegura123");
        assertThat(corpo.toLowerCase()).doesNotContain("password");
        assertThat(corpo).doesNotContain("$2a$");
    }

    @Test
    @DisplayName("grava a senha como hash BCrypt verificavel, e nao em claro")
    void devePersistirSenhaComoHashBCrypt() throws Exception {
        mockMvc.perform(requisicaoDeCadastro("hash@email.com", "senhaSegura123", "Fulano"))
                .andExpect(status().isCreated());

        User persistido = userRepository.findByEmail("hash@email.com").orElseThrow();

        assertThat(persistido.getPasswordHash()).isNotEqualTo("senhaSegura123");
        assertThat(persistido.getPasswordHash()).startsWith("$2a$");
        assertThat(passwordEncoder.matches("senhaSegura123", persistido.getPasswordHash())).isTrue();
    }

    @Test
    @DisplayName("normaliza o e-mail, impedindo contas duplicadas por diferenca de caixa")
    void deveNormalizarEmail() throws Exception {
        mockMvc.perform(requisicaoDeCadastro("  Pedro@Email.COM ", "senhaSegura123", "Pedro"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("pedro@email.com"));

        assertThat(userRepository.existsByEmail("pedro@email.com")).isTrue();
    }

    @Test
    @DisplayName("recusa e-mail ja cadastrado com 409 EMAIL_ALREADY_REGISTERED")
    void deveRecusarEmailDuplicado() throws Exception {
        mockMvc.perform(requisicaoDeCadastro("repetido@email.com", "senhaSegura123", "Primeiro"))
                .andExpect(status().isCreated());

        mockMvc.perform(requisicaoDeCadastro("repetido@email.com", "outraSenha456", "Segundo"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("EMAIL_ALREADY_REGISTERED"))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.path").value("/auth/register"))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    @DisplayName("recusa e-mail em formato invalido com 400 e detalhe por campo")
    void deveRecusarEmailInvalido() throws Exception {
        mockMvc.perform(requisicaoDeCadastro("nao-e-um-email", "senhaSegura123", "Fulano"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fields.email").isNotEmpty());
    }

    @Test
    @DisplayName("recusa senha menor que o minimo com 400")
    void deveRecusarSenhaCurta() throws Exception {
        mockMvc.perform(requisicaoDeCadastro("curta@email.com", "1234", "Fulano"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fields.password").isNotEmpty());

        assertThat(userRepository.existsByEmail("curta@email.com")).isFalse();
    }

    @Test
    @DisplayName("recusa corpo sem os campos obrigatorios com 400")
    void deveRecusarCorpoIncompleto() throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
            requisicaoDeCadastro(String email, String senha, String nome) throws Exception {

        String corpo = objectMapper.writeValueAsString(
                Map.of("email", email, "password", senha, "fullName", nome));

        return post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(corpo);
    }
}
