package com.devbandeiraa.bookingservice.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.devbandeiraa.bookingservice.support.TestcontainersConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * A especificacao OpenAPI e um contrato publicado, e aqui ela e verificada como tal.
 *
 * <p>Documentacao que mente e pior que documentacao ausente: quem le uma API confia no que ela
 * declara, e uma promessa quebrada so aparece em producao. Estes testes prendem as afirmacoes
 * que custariam caro se deixassem de valer sem ninguem perceber.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
@ActiveProfiles("test")
class DocumentacaoOpenApiIntegrationTest {

    /**
     * Prefixado com o recurso, e nao o {@code /v3/api-docs} padrao, para sobreviver ao
     * {@code StripPrefix=1} do gateway. Se alguem "corrigir" isso para o caminho convencional,
     * a documentacao some da interface agregada — e este teste falha antes disso.
     */
    private static final String CAMINHO = "/bookings/v3/api-docs";

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("a especificacao e servida sem token")
    void especificacaoEPublica() throws Exception {
        // Nao e descuido de seguranca: sem isto o Swagger UI nao teria como carregar a
        // especificacao antes de existir um campo onde colar o token. O que a especificacao
        // expoe e o contrato, e cada caminho nela continua exigindo o que ja exigia.
        mockMvc.perform(get(CAMINHO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("booking-service"));
    }

    @Test
    @DisplayName("descreve os caminhos das reservas")
    void descreveOsCaminhos() throws Exception {
        mockMvc.perform(get(CAMINHO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/bookings'].post").exists())
                .andExpect(jsonPath("$.paths['/bookings/me'].get").exists())
                .andExpect(jsonPath("$.paths['/bookings/{id}/pay'].post").exists())
                .andExpect(jsonPath("$.paths['/bookings/{id}/cancel'].post").exists())
                .andExpect(jsonPath("$.paths['/events/{eventId}/availability'].get").exists());
    }

    @Test
    @DisplayName("declara o esquema de token e o oferece pelo gateway")
    void declaraEsquemaESevidores() throws Exception {
        mockMvc.perform(get(CAMINHO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.securitySchemes['bearer-jwt'].scheme")
                        .value("bearer"))
                // Relativo, e nao absoluto: e o que faz a mesma especificacao servir tanto ao
                // Swagger aberto no gateway quanto ao aberto direto no servico.
                .andExpect(jsonPath("$.servers[0].url").value("/api"));
    }

    /**
     * O teste que mais importa deste arquivo.
     *
     * <p>Aplicar o esquema de seguranca globalmente e o atalho comum, e faz a documentacao
     * declarar que a consulta de disponibilidade exige token — quando ela e publica de proposito.
     * Um consumidor que acreditasse nisso deixaria de mostrar o estoque a quem ainda nao tem
     * conta, que e exatamente a pessoa que precisa ver o numero para decidir criar uma.
     */
    @Test
    @DisplayName("nao exige token no que e publico, e exige no que e privado")
    void distingueOPublicoDoPrivado() throws Exception {
        mockMvc.perform(get(CAMINHO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/events/{eventId}/availability'].get.security")
                        .doesNotExist())
                .andExpect(jsonPath("$.paths['/bookings'].post.security[0]['bearer-jwt']")
                        .exists());
    }

    /**
     * O cabecalho e obrigatorio para quem consome, embora o binding use {@code required = false}
     * para que a ausencia vire o erro padrao da plataforma em vez de uma excecao do framework.
     * A documentacao precisa contar a verdade do consumidor, e nao o detalhe da implementacao.
     */
    @Test
    @DisplayName("marca Idempotency-Key como obrigatorio ao criar reserva")
    void documentaChaveDeIdempotencia() throws Exception {
        mockMvc.perform(get(CAMINHO))
                .andExpect(status().isOk())
                .andExpect(jsonPath(
                        "$.paths['/bookings'].post.parameters[?(@.name=='Idempotency-Key')]"
                                + ".required")
                        .value(true));
    }

    /**
     * {@code 409} e a resposta de quem perdeu a corrida pelo ultimo ingresso — a tese do projeto
     * inteiro. Se ela sumir da especificacao, um cliente gerado a partir dela tratara o caso
     * como erro inesperado, e provavelmente com retry, que e o pior desfecho possivel.
     */
    @Test
    @DisplayName("documenta o 409 de estoque esgotado")
    void documentaOConflitoDeEstoque() throws Exception {
        mockMvc.perform(get(CAMINHO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/bookings'].post.responses['409'].description")
                        .value(org.hamcrest.Matchers.containsString("SOLD_OUT")));
    }
}
