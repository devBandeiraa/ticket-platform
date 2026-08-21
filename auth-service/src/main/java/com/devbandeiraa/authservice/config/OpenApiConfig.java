package com.devbandeiraa.authservice.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Documentacao OpenAPI do auth-service.
 *
 * <p>O esquema de seguranca e declarado em {@code components}, mas <strong>nao</strong> aplicado
 * globalmente. Quase tudo aqui e publico de proposito — registrar, entrar, renovar e sair sao
 * justamente os caminhos por onde o token e obtido ou descartado, e marca-los como protegidos
 * seria documentar um impasse. So {@code /auth/me} exige token, e a exigencia esta anotada la.
 */
@Configuration
public class OpenApiConfig {

    /** Nome do esquema, referenciado pelas anotacoes nos controllers. */
    public static final String ESQUEMA_JWT = "bearer-jwt";

    @Bean
    OpenAPI openApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("auth-service")
                        .version("0.0.1")
                        .description("""
                                Cadastro, autenticacao e emissao de tokens.

                                O login devolve um par de tokens: um `accessToken` de vida curta, \
                                usado no cabecalho `Authorization`, e um `refreshToken` de vida \
                                longa, usado apenas para obter um novo par. Nenhum dos demais \
                                servicos chama este aqui para validar um token — todos verificam \
                                a assinatura por conta propria."""))
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
     *
     * <p>O caminho pelo gateway vem primeiro por ser o padrao do Swagger UI, e por ser tambem
     * o modo como a API e de fato consumida — o frontend nunca fala com o servico diretamente.
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
                        + "O prefixo `Bearer` e adicionado automaticamente.");
    }
}
