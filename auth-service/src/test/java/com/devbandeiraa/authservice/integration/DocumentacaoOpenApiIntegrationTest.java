package com.devbandeiraa.authservice.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.devbandeiraa.authservice.support.PostgresContainerConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

/** A especificacao OpenAPI do auth-service, verificada como o contrato publicado que ela e. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresContainerConfig.class)
class DocumentacaoOpenApiIntegrationTest {

    /**
     * Prefixado com o recurso para sobreviver ao {@code StripPrefix=1} do gateway. Trocado pelo
     * {@code /v3/api-docs} convencional, a documentacao some da interface agregada.
     */
    private static final String CAMINHO = "/auth/v3/api-docs";

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("a especificacao e servida sem token")
    void especificacaoEPublica() throws Exception {
        mockMvc.perform(get(CAMINHO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("auth-service"));
    }

    @Test
    @DisplayName("descreve os caminhos de autenticacao")
    void descreveOsCaminhos() throws Exception {
        mockMvc.perform(get(CAMINHO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/auth/register'].post").exists())
                .andExpect(jsonPath("$.paths['/auth/login'].post").exists())
                .andExpect(jsonPath("$.paths['/auth/refresh'].post").exists())
                .andExpect(jsonPath("$.paths['/auth/logout'].post").exists())
                .andExpect(jsonPath("$.paths['/auth/me'].get").exists());
    }

    /**
     * As quatro rotas de entrada precisam aparecer como abertas.
     *
     * <p>Aplicar o esquema de seguranca globalmente e o atalho comum, e produz uma documentacao
     * absurda: o login passaria a exigir o token que ele proprio emite. Quem lesse isso nao
     * teria como comecar.
     */
    @Test
    @DisplayName("nao exige token nas rotas de entrada, e exige em /auth/me")
    void distingueOPublicoDoPrivado() throws Exception {
        mockMvc.perform(get(CAMINHO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/auth/login'].post.security").doesNotExist())
                .andExpect(jsonPath("$.paths['/auth/register'].post.security").doesNotExist())
                .andExpect(jsonPath("$.paths['/auth/refresh'].post.security").doesNotExist())
                .andExpect(jsonPath("$.paths['/auth/me'].get.security[0]['bearer-jwt']").exists());
    }

    @Test
    @DisplayName("declara o esquema de token e o oferece pelo gateway")
    void declaraEsquemaESevidores() throws Exception {
        mockMvc.perform(get(CAMINHO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.securitySchemes['bearer-jwt'].scheme")
                        .value("bearer"))
                .andExpect(jsonPath("$.servers[0].url").value("/api"));
    }
}
