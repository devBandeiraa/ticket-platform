package com.devbandeiraa.eventservice.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.devbandeiraa.eventservice.support.PostgresContainerConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

/** A especificacao OpenAPI do event-service, verificada como o contrato publicado que ela e. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresContainerConfig.class)
class DocumentacaoOpenApiIntegrationTest {

    /**
     * Prefixado com o recurso para sobreviver ao {@code StripPrefix=1} do gateway. Trocado pelo
     * {@code /v3/api-docs} convencional, a documentacao some da interface agregada.
     */
    private static final String CAMINHO = "/events/v3/api-docs";

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("a especificacao e servida sem token")
    void especificacaoEPublica() throws Exception {
        mockMvc.perform(get(CAMINHO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("event-service"));
    }

    @Test
    @DisplayName("descreve o catalogo publico e a administracao")
    void descreveOsCaminhos() throws Exception {
        mockMvc.perform(get(CAMINHO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/events'].get").exists())
                .andExpect(jsonPath("$.paths['/events/{id}'].get").exists())
                .andExpect(jsonPath("$.paths['/admin/events'].post").exists())
                .andExpect(jsonPath("$.paths['/admin/events/{id}/publish'].post").exists());
    }

    /**
     * O catalogo e publico e a documentacao precisa dizer isso.
     *
     * <p>Marcar tudo como protegido faria a especificacao mentir sobre a parte que qualquer
     * visitante consulta sem conta — e um cliente gerado a partir dela exigiria um login que o
     * servidor nunca pediu.
     */
    @Test
    @DisplayName("nao exige token no catalogo, e exige na administracao")
    void distingueOPublicoDoPrivado() throws Exception {
        mockMvc.perform(get(CAMINHO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/events'].get.security").doesNotExist())
                .andExpect(jsonPath("$.paths['/events/{id}'].get.security").doesNotExist())
                .andExpect(jsonPath("$.paths['/admin/events'].post.security[0]['bearer-jwt']")
                        .exists());
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
