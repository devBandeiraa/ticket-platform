package com.devbandeiraa.eventservice.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Documentacao OpenAPI do event-service.
 *
 * <p>O esquema de seguranca fica em {@code components} sem ser aplicado globalmente: o catalogo
 * e publico, e marcar toda a API como protegida faria a documentacao mentir sobre a parte que
 * qualquer visitante pode consultar. A exigencia de token esta declarada no controller
 * administrativo, onde ela de fato existe.
 */
@Configuration
public class OpenApiConfig {

    /** Nome do esquema, referenciado pelas anotacoes nos controllers. */
    public static final String ESQUEMA_JWT = "bearer-jwt";

    @Bean
    OpenAPI openApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("event-service")
                        .version("0.0.1")
                        .description("""
                                Catalogo de eventos: consulta publica e administracao.

                                Um evento nasce como rascunho e so aparece no catalogo depois de \
                                publicado. A capacidade declarada aqui e a fonte da verdade que o \
                                booking-service copia na primeira reserva de cada evento — este \
                                servico nao controla estoque, apenas o descreve."""))
                .servers(servidores())
                .components(new Components().addSecuritySchemes(ESQUEMA_JWT, esquemaJwt()));
    }

    /**
     * Os dois enderecos por onde este servico pode ser alcancado.
     *
     * <p>Sao relativos de proposito: o navegador resolve cada um contra a origem de onde a
     * pagina do Swagger veio. Aberta no gateway, {@code /api} aponta para o gateway; aberta
     * direto no servico, {@code /} aponta para o servico. Um endereco absoluto so funcionaria
     * em um dos dois casos, e quebraria no outro.
     */
    private static List<Server> servidores() {
        return List.of(
                new Server().url("/api").description("atraves do api-gateway (padrao)"),
                new Server().url("/").description("acesso direto ao servico"));
    }

    private static SecurityScheme esquemaJwt() {
        return new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")
                .description("Cole aqui o `accessToken` devolvido por `POST /auth/login`. "
                        + "Os caminhos sob `/admin` exigem um token com papel ADMIN.");
    }
}
